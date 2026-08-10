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
  readdirSync,
  realpathSync,
  writeSync,
} from "node:fs";
import { dirname, isAbsolute, join, resolve } from "node:path";
import {
  parseStrictJsonBytes,
  readStrictJsonFile,
} from "./strict-evidence.mjs";

export const ANDROID_MIGRATION_CONTROLLER_ENVELOPE_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-android-migration-controller-envelope-v1",
});

const KIB = 1024;
const MIB = 1024 * KIB;
export const ANDROID_MIGRATION_CONTROLLER_MAXIMUM_BYTES = 32 * MIB;
export const ANDROID_MIGRATION_CONTROLLER_FILES = Object.freeze({
  "android-migration-device-execution-evidence.json": 2 * MIB,
  "android-migration-encrypted-storage-evidence.json": 2 * MIB,
  "android-migration-evidence-producer-signature.der": 16 * KIB,
  "android-migration-evidence-reviewer-signature.der": 16 * KIB,
  "android-migration-evidence.json": 2 * MIB,
  "android-migration-matrix.json": 2 * MIB,
  "android-migration-raw-execution-evidence.json": 2 * MIB,
  "android-migration-receipt-signature.der": 16 * KIB,
  "android-migration-retained-snapshot-manifest.json": 2 * MIB,
  "android-migration-test-results.json": 2 * MIB,
  "android-migration-trust.json": 2 * MIB,
  "device-producer-public-key.pem": 16 * KIB,
  "reviewer-public-key.pem": 16 * KIB,
});

const EXPECTED_NAMES = Object.keys(ANDROID_MIGRATION_CONTROLLER_FILES).sort();
const JSON_NAMES = EXPECTED_NAMES.filter((name) => name.endsWith(".json"));
const SHA256 = /^[0-9a-f]{64}$/;
const REVISION = /^[0-9a-f]{40}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const BASE64 = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;

const ARTIFACT_FILE_BY_KEY = Object.freeze({
  retainedReleaseSnapshotManifest:
    "android-migration-retained-snapshot-manifest.json",
  testResult: "android-migration-test-results.json",
  encryptedStorageEvidence:
    "android-migration-encrypted-storage-evidence.json",
  deviceExecutionEvidence: "android-migration-device-execution-evidence.json",
  rawExecutionEvidence: "android-migration-raw-execution-evidence.json",
});

const fail = (code) => {
  throw new Error(code);
};

const hasExactKeys = (value, keys) =>
  value !== null &&
  typeof value === "object" &&
  !Array.isArray(value) &&
  Object.keys(value).sort().join("\0") === [...keys].sort().join("\0");

const nonzeroSha256 = (value) =>
  typeof value === "string" && SHA256.test(value) && !/^0+$/.test(value);

const validRevision = (value) =>
  typeof value === "string" && REVISION.test(value) && !/^0+$/.test(value);

const validUuid = (value) =>
  typeof value === "string" && UUID.test(value) && value !== "00000000-0000-0000-0000-000000000000";

const canonicalAbsolutePath = (value) =>
  typeof value === "string" &&
  value.length > 0 &&
  isAbsolute(value) &&
  resolve(value) === value;

const sha256Bytes = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

const parseJsonPayload = (bytes) => {
  try {
    const value = parseStrictJsonBytes(bytes);
    return value !== null && typeof value === "object" && !Array.isArray(value)
      ? value
      : null;
  } catch {
    return null;
  }
};

const decodePayloads = (files) => {
  if (!hasExactKeys(files, EXPECTED_NAMES)) {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_INVENTORY_INVALID");
  }
  const payloads = Object.create(null);
  const records = Object.create(null);
  for (const name of EXPECTED_NAMES) {
    const encoded = files[name];
    if (
      typeof encoded !== "string" ||
      encoded.length < 4 ||
      !BASE64.test(encoded)
    ) {
      fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_BASE64_INVALID");
    }
    const bytes = Buffer.from(encoded, "base64");
    if (
      bytes.length < 1 ||
      bytes.length > ANDROID_MIGRATION_CONTROLLER_FILES[name] ||
      bytes.toString("base64") !== encoded
    ) {
      fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PAYLOAD_INVALID");
    }
    if (name.endsWith(".json") && parseJsonPayload(bytes) === null) {
      fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_JSON_INVALID");
    }
    payloads[name] = bytes;
    records[name] = { sha256: sha256Bytes(bytes), bytes: bytes.length };
  }
  return { payloads, records };
};

const requireExpected = (actual, expected, code) => {
  if (expected !== undefined && actual !== expected) fail(code);
};

const validateInternalBindings = ({ envelope, payloads, records }) => {
  const receipt = parseJsonPayload(payloads["android-migration-matrix.json"]);
  const evidence = parseJsonPayload(payloads["android-migration-evidence.json"]);
  const trust = parseJsonPayload(payloads["android-migration-trust.json"]);
  if (
    receipt?.schemaVersion !== 7 ||
    receipt?.contractId !== "sora-android-wallet-migration-qualification-v7" ||
    receipt?.platform !== "android" ||
    receipt?.status !== "qualified" ||
    evidence?.schemaVersion !== 2 ||
    evidence?.contractId !== "sora-android-wallet-migration-evidence-v2" ||
    evidence?.platform !== "android" ||
    evidence?.status !== "qualified" ||
    trust?.schemaVersion !== 1 ||
    trust?.contractId !== "sora-android-wallet-migration-qualification-trust-v1" ||
    trust?.platform !== "android" ||
    trust?.status !== "qualified"
  ) {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_QUALIFICATION_SHAPE_INVALID");
  }
  if (
    receipt.runId !== envelope.runId ||
    evidence.runId !== envelope.runId ||
    receipt.qualificationSequenceNumber !== envelope.qualificationSequenceNumber ||
    evidence.qualificationSequenceNumber !== envelope.qualificationSequenceNumber ||
    receipt.sourceRevision !== envelope.sourceRevision ||
    evidence.sourceRevision !== envelope.sourceRevision ||
    receipt.identity?.appBuildIdentitySha256 !== envelope.appBuildIdentitySha256 ||
    evidence.identity?.appBuildIdentitySha256 !== envelope.appBuildIdentitySha256 ||
    receipt.evidenceManifestSha256 !== records["android-migration-evidence.json"].sha256 ||
    receipt.trustRootSha256 !== records["android-migration-trust.json"].sha256 ||
    evidence.trustRootSha256 !== records["android-migration-trust.json"].sha256
  ) {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_IDENTITY_DIVERGED");
  }
  if (!hasExactKeys(evidence.artifacts, Object.keys(ARTIFACT_FILE_BY_KEY))) {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_ARTIFACTS_INVALID");
  }
  for (const [key, fileName] of Object.entries(ARTIFACT_FILE_BY_KEY)) {
    const artifact = evidence.artifacts[key];
    if (
      !hasExactKeys(artifact, ["relativePath", "sha256"]) ||
      artifact.relativePath !== `docs/modernization/qualification/${fileName}` ||
      artifact.sha256 !== records[fileName].sha256
    ) {
      fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_ARTIFACTS_DIVERGED");
    }
  }
  return { receipt, evidence, trust };
};

export const inspectAndroidMigrationControllerEnvelopeV1 = ({
  value,
  expectedSourceRevision,
  expectedCandidateAabSha256,
  expectedCandidateAabBytes,
  expectedRunId,
  expectedQualificationSequenceNumber,
  expectedAppBuildIdentitySha256,
}) => {
  if (
    !hasExactKeys(value, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "sourceRevision",
      "candidateAabSha256",
      "candidateAabBytes",
      "runId",
      "qualificationSequenceNumber",
      "appBuildIdentitySha256",
      "files",
      "authorization",
    ]) ||
    value.schemaVersion !== ANDROID_MIGRATION_CONTROLLER_ENVELOPE_V1.schemaVersion ||
    value.contractId !== ANDROID_MIGRATION_CONTROLLER_ENVELOPE_V1.contractId ||
    value.status !== "delivered-unreviewed" ||
    value.platform !== "android" ||
    !validRevision(value.sourceRevision) ||
    !nonzeroSha256(value.candidateAabSha256) ||
    !Number.isSafeInteger(value.candidateAabBytes) ||
    value.candidateAabBytes < 1 ||
    !validUuid(value.runId) ||
    !Number.isSafeInteger(value.qualificationSequenceNumber) ||
    value.qualificationSequenceNumber < 1 ||
    !nonzeroSha256(value.appBuildIdentitySha256) ||
    !hasExactKeys(value.authorization, [
      "authorizesRelease",
      "authorizesProductionMutation",
    ]) ||
    value.authorization.authorizesRelease !== false ||
    value.authorization.authorizesProductionMutation !== false
  ) {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_SHAPE_INVALID");
  }
  requireExpected(
    value.sourceRevision,
    expectedSourceRevision,
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_SOURCE_DIVERGED",
  );
  requireExpected(
    value.candidateAabSha256,
    expectedCandidateAabSha256,
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_CANDIDATE_DIVERGED",
  );
  requireExpected(
    value.candidateAabBytes,
    expectedCandidateAabBytes,
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_CANDIDATE_DIVERGED",
  );
  requireExpected(
    value.runId,
    expectedRunId,
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_RUN_DIVERGED",
  );
  requireExpected(
    value.qualificationSequenceNumber,
    expectedQualificationSequenceNumber,
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_SEQUENCE_DIVERGED",
  );
  requireExpected(
    value.appBuildIdentitySha256,
    expectedAppBuildIdentitySha256,
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_APP_IDENTITY_DIVERGED",
  );
  const decoded = decodePayloads(value.files);
  const internal = validateInternalBindings({
    envelope: value,
    payloads: decoded.payloads,
    records: decoded.records,
  });
  return { ...decoded, ...internal };
};

const protectedEnvelope = (path) => {
  if (!canonicalAbsolutePath(path)) {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PATH_INVALID");
  }
  let stat;
  try {
    stat = lstatSync(path);
  } catch {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PATH_INVALID");
  }
  if (
    !stat.isFile() ||
    stat.isSymbolicLink() ||
    stat.nlink !== 1 ||
    stat.size < 1 ||
    stat.size > ANDROID_MIGRATION_CONTROLLER_MAXIMUM_BYTES ||
    (stat.mode & 0o077) !== 0 ||
    (typeof process.getuid === "function" && stat.uid !== process.getuid()) ||
    realpathSync(path) !== path
  ) {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PATH_INVALID");
  }
  const record = readStrictJsonFile(
    path,
    ANDROID_MIGRATION_CONTROLLER_MAXIMUM_BYTES,
  );
  if (record === null) {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_JSON_INVALID");
  }
  const after = lstatSync(path);
  if (
    after.dev !== stat.dev ||
    after.ino !== stat.ino ||
    after.size !== stat.size ||
    after.mtimeMs !== stat.mtimeMs ||
    after.ctimeMs !== stat.ctimeMs ||
    after.nlink !== 1
  ) {
    fail("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_CHANGED");
  }
  return record;
};

const createProtectedOutputRoot = (outputRoot) => {
  if (!canonicalAbsolutePath(outputRoot)) {
    fail("ANDROID_MIGRATION_CONTROLLER_OUTPUT_ROOT_INVALID");
  }
  const parent = dirname(outputRoot);
  let parentStat;
  try {
    parentStat = lstatSync(parent);
  } catch {
    fail("ANDROID_MIGRATION_CONTROLLER_OUTPUT_ROOT_INVALID");
  }
  if (
    !parentStat.isDirectory() ||
    parentStat.isSymbolicLink() ||
    realpathSync(parent) !== parent
  ) {
    fail("ANDROID_MIGRATION_CONTROLLER_OUTPUT_ROOT_INVALID");
  }
  try {
    lstatSync(outputRoot);
    fail("ANDROID_MIGRATION_CONTROLLER_OUTPUT_ROOT_EXISTS");
  } catch (error) {
    if (error instanceof Error && error.message === "ANDROID_MIGRATION_CONTROLLER_OUTPUT_ROOT_EXISTS") {
      throw error;
    }
    if (error?.code !== "ENOENT") {
      fail("ANDROID_MIGRATION_CONTROLLER_OUTPUT_ROOT_INVALID");
    }
  }
  mkdirSync(outputRoot, { mode: 0o700 });
  chmodSync(outputRoot, 0o700);
  if (realpathSync(outputRoot) !== outputRoot) {
    fail("ANDROID_MIGRATION_CONTROLLER_OUTPUT_ROOT_INVALID");
  }
};

const writeAll = (descriptor, bytes) => {
  let offset = 0;
  while (offset < bytes.length) {
    const count = writeSync(
      descriptor,
      bytes,
      offset,
      bytes.length - offset,
      null,
    );
    if (count < 1) fail("ANDROID_MIGRATION_CONTROLLER_OUTPUT_WRITE_FAILED");
    offset += count;
  }
};

export const extractAndroidMigrationControllerEnvelopeV1 = ({
  envelopePath,
  outputRoot,
  expectedSourceRevision,
  expectedCandidateAabSha256,
  expectedCandidateAabBytes,
  expectedRunId,
  expectedQualificationSequenceNumber,
  expectedAppBuildIdentitySha256,
}) => {
  const envelopeRecord = protectedEnvelope(envelopePath);
  const inspected = inspectAndroidMigrationControllerEnvelopeV1({
    value: envelopeRecord.value,
    expectedSourceRevision,
    expectedCandidateAabSha256,
    expectedCandidateAabBytes,
    expectedRunId,
    expectedQualificationSequenceNumber,
    expectedAppBuildIdentitySha256,
  });
  createProtectedOutputRoot(outputRoot);
  for (const name of EXPECTED_NAMES) {
    const outputPath = join(outputRoot, name);
    let descriptor = null;
    try {
      descriptor = openSync(
        outputPath,
        fsConstants.O_WRONLY |
          fsConstants.O_CREAT |
          fsConstants.O_EXCL |
          fsConstants.O_NOFOLLOW,
        0o600,
      );
      writeAll(descriptor, inspected.payloads[name]);
      fsyncSync(descriptor);
    } finally {
      if (descriptor !== null) closeSync(descriptor);
    }
    chmodSync(outputPath, 0o600);
    const stat = lstatSync(outputPath);
    if (
      !stat.isFile() ||
      stat.isSymbolicLink() ||
      stat.nlink !== 1 ||
      stat.size !== inspected.records[name].bytes ||
      (stat.mode & 0o077) !== 0 ||
      realpathSync(outputPath) !== outputPath
    ) {
      fail("ANDROID_MIGRATION_CONTROLLER_OUTPUT_FILE_INVALID");
    }
  }
  if (readdirSync(outputRoot).sort().join("\0") !== EXPECTED_NAMES.join("\0")) {
    fail("ANDROID_MIGRATION_CONTROLLER_OUTPUT_INVENTORY_INVALID");
  }
  return {
    schemaVersion: ANDROID_MIGRATION_CONTROLLER_ENVELOPE_V1.schemaVersion,
    contractId: ANDROID_MIGRATION_CONTROLLER_ENVELOPE_V1.contractId,
    status: "extracted-unreviewed",
    platform: "android",
    sourceRevision: envelopeRecord.value.sourceRevision,
    candidateAabSha256: envelopeRecord.value.candidateAabSha256,
    candidateAabBytes: envelopeRecord.value.candidateAabBytes,
    runId: envelopeRecord.value.runId,
    qualificationSequenceNumber:
      envelopeRecord.value.qualificationSequenceNumber,
    appBuildIdentitySha256: envelopeRecord.value.appBuildIdentitySha256,
    envelopeSha256: envelopeRecord.sha256,
    envelopeBytes: envelopeRecord.byteCount,
    fileCount: EXPECTED_NAMES.length,
    files: Object.fromEntries(
      EXPECTED_NAMES.map((name) => [name, inspected.records[name]]),
    ),
    authorization: {
      authorizesRelease: false,
      authorizesProductionMutation: false,
    },
  };
};
