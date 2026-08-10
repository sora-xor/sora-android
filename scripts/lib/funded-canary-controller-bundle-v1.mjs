import { createHash } from "node:crypto";
import {
  chmodSync,
  closeSync,
  constants as fsConstants,
  fstatSync,
  fsyncSync,
  lstatSync,
  mkdirSync,
  openSync,
  readSync,
  readdirSync,
  realpathSync,
  writeSync,
} from "node:fs";
import { dirname, isAbsolute, join, resolve } from "node:path";
import { readStrictJsonFile } from "./strict-evidence.mjs";

export const FUNDED_CANARY_CONTROLLER_BUNDLE_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-android-funded-canary-controller-bundle-v1",
});

const KIB = 1024;
const MIB = 1024 * KIB;
const GIB = 1024 * MIB;
const TAR_BLOCK_BYTES = 512;
const MAXIMUM_TAR_BYTES = 5 * GIB;
const JSON_MAXIMUM_BYTES = 256 * KIB;

export const FUNDED_CANARY_CONTROLLER_BUNDLE_FILES = Object.freeze({
  "bundletool.jar": 512 * MIB,
  "candidate.apks": GIB,
  "iroha-finality-platform.artifact": GIB,
  "iroha-finality-verifier.artifact": GIB,
  "iroha-native-finality-canary.json": 64 * KIB,
  "minamoto-approval.json": 64 * KIB,
  "minamoto-artifact-identity.json": 64 * KIB,
  "minamoto-canary.json": 64 * KIB,
  "minamoto-consumption-receipt.json": 64 * KIB,
  "minamoto-evidence-bundle.json": JSON_MAXIMUM_BYTES,
  "minamoto-finality-trust-context.json": 64 * KIB,
  "minamoto-low-value-policy.json": 64 * KIB,
  "minamoto-pi-receipt.json": 64 * KIB,
  "production-finality-trust-manifest.json": 64 * KIB,
  "taira-approval.json": 64 * KIB,
  "taira-artifact-identity.json": 64 * KIB,
  "taira-canary.json": 64 * KIB,
  "taira-consumption-receipt.json": 64 * KIB,
  "taira-evidence-bundle.json": JSON_MAXIMUM_BYTES,
  "taira-finality-trust-context.json": 64 * KIB,
  "taira-low-value-policy.json": 64 * KIB,
  "taira-pi-receipt.json": 64 * KIB,
});

const EXPECTED_NAMES = Object.keys(FUNDED_CANARY_CONTROLLER_BUNDLE_FILES).sort();
const JSON_NAMES = EXPECTED_NAMES.filter((name) => name.endsWith(".json"));

const fail = (code) => {
  throw new Error(code);
};

const canonicalAbsolutePath = (value) =>
  typeof value === "string" &&
  value.length > 0 &&
  isAbsolute(value) &&
  resolve(value) === value;

const cString = (bytes, code) => {
  const nul = bytes.indexOf(0);
  const end = nul < 0 ? bytes.length : nul;
  if (
    (nul >= 0 && bytes.subarray(nul).some((value) => value !== 0)) ||
    bytes.subarray(0, end).some((value) => value < 0x20 || value > 0x7e)
  ) {
    fail(code);
  }
  return bytes.subarray(0, end).toString("ascii");
};

const octalField = (bytes, code) => {
  const source = bytes.toString("ascii");
  const match = /^ *([0-7]+)(?:\0| )+$/.exec(source);
  if (match === null) fail(code);
  const value = Number.parseInt(match[1], 8);
  if (!Number.isSafeInteger(value) || value < 0) fail(code);
  return value;
};

const writeAll = (descriptor, buffer) => {
  let offset = 0;
  while (offset < buffer.length) {
    const count = writeSync(
      descriptor,
      buffer,
      offset,
      buffer.length - offset,
      null,
    );
    if (count < 1) fail("FUNDED_CANARY_BUNDLE_OUTPUT_WRITE_FAILED");
    offset += count;
  }
};

const readExact = (descriptor, length, position, code) => {
  const buffer = Buffer.allocUnsafe(length);
  let offset = 0;
  while (offset < length) {
    const count = readSync(
      descriptor,
      buffer,
      offset,
      length - offset,
      position + offset,
    );
    if (count < 1) fail(code);
    offset += count;
  }
  return buffer;
};

const checksumForHeader = (header) => {
  let checksum = 0;
  for (let index = 0; index < header.length; index += 1) {
    checksum += index >= 148 && index < 156 ? 0x20 : header[index];
  }
  return checksum;
};

const validateHeader = (header) => {
  const storedChecksum = octalField(
    header.subarray(148, 156),
    "FUNDED_CANARY_BUNDLE_TAR_CHECKSUM_INVALID",
  );
  if (storedChecksum !== checksumForHeader(header)) {
    fail("FUNDED_CANARY_BUNDLE_TAR_CHECKSUM_INVALID");
  }
  const name = cString(
    header.subarray(0, 100),
    "FUNDED_CANARY_BUNDLE_TAR_NAME_INVALID",
  );
  const prefix = cString(
    header.subarray(345, 500),
    "FUNDED_CANARY_BUNDLE_TAR_PREFIX_INVALID",
  );
  const linkName = cString(
    header.subarray(157, 257),
    "FUNDED_CANARY_BUNDLE_TAR_LINK_INVALID",
  );
  const type = header[156];
  if (
    prefix !== "" ||
    linkName !== "" ||
    (type !== 0 && type !== 0x30) ||
    header.subarray(257, 263).toString("binary") !== "ustar\0" ||
    header.subarray(263, 265).toString("ascii") !== "00" ||
    !Object.hasOwn(FUNDED_CANARY_CONTROLLER_BUNDLE_FILES, name)
  ) {
    fail("FUNDED_CANARY_BUNDLE_TAR_ENTRY_INVALID");
  }
  const mode = octalField(
    header.subarray(100, 108),
    "FUNDED_CANARY_BUNDLE_TAR_MODE_INVALID",
  );
  const size = octalField(
    header.subarray(124, 136),
    "FUNDED_CANARY_BUNDLE_TAR_SIZE_INVALID",
  );
  if (
    (mode & 0o077) !== 0 ||
    (mode & 0o600) !== 0o600 ||
    size < 1 ||
    size > FUNDED_CANARY_CONTROLLER_BUNDLE_FILES[name]
  ) {
    fail("FUNDED_CANARY_BUNDLE_TAR_ENTRY_BOUNDS_INVALID");
  }
  return { name, size };
};

const assertProtectedTar = (path) => {
  if (!canonicalAbsolutePath(path)) {
    fail("FUNDED_CANARY_BUNDLE_PATH_INVALID");
  }
  let stat;
  try {
    stat = lstatSync(path);
  } catch {
    fail("FUNDED_CANARY_BUNDLE_PATH_INVALID");
  }
  if (
    !stat.isFile() ||
    stat.isSymbolicLink() ||
    stat.nlink !== 1 ||
    stat.size < TAR_BLOCK_BYTES * 2 ||
    stat.size > MAXIMUM_TAR_BYTES ||
    stat.size % TAR_BLOCK_BYTES !== 0 ||
    (stat.mode & 0o077) !== 0 ||
    (typeof process.getuid === "function" && stat.uid !== process.getuid()) ||
    realpathSync(path) !== path
  ) {
    fail("FUNDED_CANARY_BUNDLE_PATH_INVALID");
  }
  return stat;
};

const createProtectedOutputRoot = (outputRoot) => {
  if (!canonicalAbsolutePath(outputRoot)) {
    fail("FUNDED_CANARY_BUNDLE_OUTPUT_ROOT_INVALID");
  }
  const parent = dirname(outputRoot);
  if (realpathSync(parent) !== parent) {
    fail("FUNDED_CANARY_BUNDLE_OUTPUT_ROOT_INVALID");
  }
  try {
    lstatSync(outputRoot);
    fail("FUNDED_CANARY_BUNDLE_OUTPUT_ROOT_ALREADY_EXISTS");
  } catch (error) {
    if (error instanceof Error && error.message === "FUNDED_CANARY_BUNDLE_OUTPUT_ROOT_ALREADY_EXISTS") {
      throw error;
    }
    if (error?.code !== "ENOENT") {
      fail("FUNDED_CANARY_BUNDLE_OUTPUT_ROOT_INVALID");
    }
  }
  mkdirSync(outputRoot, { mode: 0o700 });
  chmodSync(outputRoot, 0o700);
  if (realpathSync(outputRoot) !== outputRoot) {
    fail("FUNDED_CANARY_BUNDLE_OUTPUT_ROOT_INVALID");
  }
};

export const extractFundedCanaryControllerBundleV1 = ({
  tarPath,
  outputRoot,
}) => {
  const pathStat = assertProtectedTar(tarPath);
  createProtectedOutputRoot(outputRoot);
  let descriptor = null;
  const tarDigest = createHash("sha256");
  const extracted = Object.create(null);
  let position = 0;
  let terminalObserved = false;
  try {
    descriptor = openSync(tarPath, fsConstants.O_RDONLY | fsConstants.O_NOFOLLOW);
    const before = fstatSync(descriptor);
    if (
      !before.isFile() ||
      before.dev !== pathStat.dev ||
      before.ino !== pathStat.ino ||
      before.size !== pathStat.size ||
      before.mtimeMs !== pathStat.mtimeMs ||
      before.ctimeMs !== pathStat.ctimeMs
    ) {
      fail("FUNDED_CANARY_BUNDLE_CHANGED_DURING_OPEN");
    }

    while (position < before.size) {
      const header = readExact(
        descriptor,
        TAR_BLOCK_BYTES,
        position,
        "FUNDED_CANARY_BUNDLE_TAR_TRUNCATED",
      );
      tarDigest.update(header);
      position += TAR_BLOCK_BYTES;
      if (header.every((value) => value === 0)) {
        const second = readExact(
          descriptor,
          TAR_BLOCK_BYTES,
          position,
          "FUNDED_CANARY_BUNDLE_TAR_TRUNCATED",
        );
        tarDigest.update(second);
        position += TAR_BLOCK_BYTES;
        if (!second.every((value) => value === 0)) {
          fail("FUNDED_CANARY_BUNDLE_TAR_TERMINATOR_INVALID");
        }
        while (position < before.size) {
          const trailing = readExact(
            descriptor,
            Math.min(1024 * 1024, before.size - position),
            position,
            "FUNDED_CANARY_BUNDLE_TAR_TRUNCATED",
          );
          tarDigest.update(trailing);
          position += trailing.length;
          if (!trailing.every((value) => value === 0)) {
            fail("FUNDED_CANARY_BUNDLE_TAR_TRAILING_DATA");
          }
        }
        terminalObserved = true;
        break;
      }

      const entry = validateHeader(header);
      if (Object.hasOwn(extracted, entry.name)) {
        fail("FUNDED_CANARY_BUNDLE_TAR_DUPLICATE_ENTRY");
      }
      const outputPath = join(outputRoot, entry.name);
      let outputDescriptor = null;
      const entryDigest = createHash("sha256");
      try {
        outputDescriptor = openSync(
          outputPath,
          fsConstants.O_WRONLY |
            fsConstants.O_CREAT |
            fsConstants.O_EXCL |
            fsConstants.O_NOFOLLOW,
          0o600,
        );
        let remaining = entry.size;
        while (remaining > 0) {
          const chunk = readExact(
            descriptor,
            Math.min(1024 * 1024, remaining),
            position,
            "FUNDED_CANARY_BUNDLE_TAR_TRUNCATED",
          );
          tarDigest.update(chunk);
          entryDigest.update(chunk);
          writeAll(outputDescriptor, chunk);
          position += chunk.length;
          remaining -= chunk.length;
        }
        fsyncSync(outputDescriptor);
      } finally {
        if (outputDescriptor !== null) closeSync(outputDescriptor);
      }
      chmodSync(outputPath, 0o600);
      const paddingBytes =
        (TAR_BLOCK_BYTES - (entry.size % TAR_BLOCK_BYTES)) % TAR_BLOCK_BYTES;
      if (paddingBytes > 0) {
        const padding = readExact(
          descriptor,
          paddingBytes,
          position,
          "FUNDED_CANARY_BUNDLE_TAR_TRUNCATED",
        );
        tarDigest.update(padding);
        position += paddingBytes;
        if (!padding.every((value) => value === 0)) {
          fail("FUNDED_CANARY_BUNDLE_TAR_PADDING_INVALID");
        }
      }
      const outputStat = lstatSync(outputPath);
      if (
        !outputStat.isFile() ||
        outputStat.isSymbolicLink() ||
        outputStat.nlink !== 1 ||
        outputStat.size !== entry.size ||
        (outputStat.mode & 0o077) !== 0 ||
        realpathSync(outputPath) !== outputPath
      ) {
        fail("FUNDED_CANARY_BUNDLE_OUTPUT_FILE_INVALID");
      }
      extracted[entry.name] = {
        sha256: entryDigest.digest("hex"),
        bytes: entry.size,
      };
    }

    const after = fstatSync(descriptor);
    const pathAfter = lstatSync(tarPath);
    if (
      !terminalObserved ||
      after.dev !== before.dev ||
      after.ino !== before.ino ||
      after.size !== before.size ||
      after.mtimeMs !== before.mtimeMs ||
      after.ctimeMs !== before.ctimeMs ||
      pathAfter.dev !== before.dev ||
      pathAfter.ino !== before.ino ||
      pathAfter.size !== before.size ||
      realpathSync(tarPath) !== tarPath
    ) {
      fail("FUNDED_CANARY_BUNDLE_CHANGED_DURING_EXTRACTION");
    }
  } finally {
    if (descriptor !== null) closeSync(descriptor);
  }

  const observedNames = readdirSync(outputRoot).sort();
  if (
    observedNames.join("\0") !== EXPECTED_NAMES.join("\0") ||
    Object.keys(extracted).sort().join("\0") !== EXPECTED_NAMES.join("\0")
  ) {
    fail("FUNDED_CANARY_BUNDLE_INVENTORY_INVALID");
  }
  for (const name of JSON_NAMES) {
    const record = readStrictJsonFile(
      join(outputRoot, name),
      FUNDED_CANARY_CONTROLLER_BUNDLE_FILES[name],
    );
    if (
      record === null ||
      record.sha256 !== extracted[name].sha256 ||
      record.byteCount !== extracted[name].bytes
    ) {
      fail("FUNDED_CANARY_BUNDLE_JSON_INVALID");
    }
  }
  return {
    schemaVersion: FUNDED_CANARY_CONTROLLER_BUNDLE_V1.schemaVersion,
    contractId: FUNDED_CANARY_CONTROLLER_BUNDLE_V1.contractId,
    status: "extracted-unreviewed",
    platform: "android",
    tarSha256: tarDigest.digest("hex"),
    tarBytes: pathStat.size,
    fileCount: EXPECTED_NAMES.length,
    files: Object.fromEntries(
      EXPECTED_NAMES.map((name) => [name, extracted[name]]),
    ),
    authorization: {
      authorizesRelease: false,
      authorizesProductionMutation: false,
    },
  };
};
