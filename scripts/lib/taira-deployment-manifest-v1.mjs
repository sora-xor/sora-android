import {
  createHash,
  createPublicKey,
  verify as verifySignature,
} from "node:crypto";
import {
  closeSync,
  constants as fsConstants,
  fstatSync,
  lstatSync,
  openSync,
  readSync,
  realpathSync,
} from "node:fs";
import { isAbsolute, resolve } from "node:path";
import { parseStrictJsonBytes } from "./strict-evidence.mjs";

export const TAIRA_DEPLOYMENT_MANIFEST_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-taira-deployment-epoch-manifest-v1",
});

export const TAIRA_KNOWN_CHAIN_IDS = Object.freeze([
  "809574f5-fee7-5e69-bfcf-52451e42d50f",
  "fc56984b-2be7-431d-840e-21514d1883f0",
]);

const MAXIMUM_MANIFEST_BYTES = 64 * 1024;
const MAXIMUM_KEY_BYTES = 4 * 1024;
const MAXIMUM_SIGNATURE_BYTES = 1024;
const MAXIMUM_MANIFEST_AGE_SECONDS = 7 * 24 * 60 * 60;
const MAXIMUM_CLOCK_SKEW_SECONDS = 60;
const SHA256 = /^[0-9a-f]{64}$/;
const KEY_ID = /^[a-z0-9][a-z0-9._:-]{2,127}$/;

const fail = (code) => {
  throw new Error(code);
};

const exactKeys = (value, keys) =>
  value !== null &&
  typeof value === "object" &&
  !Array.isArray(value) &&
  Object.keys(value).sort().join("\0") === [...keys].sort().join("\0");

const nonzeroSha256 = (value) =>
  typeof value === "string" && SHA256.test(value) && !/^0+$/.test(value);

const canonicalAbsolutePath = (value) =>
  typeof value === "string" &&
  value.length > 0 &&
  isAbsolute(value) &&
  resolve(value) === value;

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

const readProtectedFile = (path, maximumBytes, code) => {
  if (!canonicalAbsolutePath(path)) fail(code);
  let descriptor = null;
  try {
    descriptor = openSync(path, fsConstants.O_RDONLY | fsConstants.O_NOFOLLOW);
    const before = fstatSync(descriptor);
    const pathBefore = lstatSync(path);
    if (
      !before.isFile() ||
      before.size < 1 ||
      before.size > maximumBytes ||
      before.nlink !== 1 ||
      (before.mode & 0o077) !== 0 ||
      (typeof process.getuid === "function" && before.uid !== process.getuid()) ||
      !pathBefore.isFile() ||
      pathBefore.isSymbolicLink() ||
      pathBefore.dev !== before.dev ||
      pathBefore.ino !== before.ino ||
      realpathSync(path) !== path
    ) {
      fail(code);
    }
    const bytes = Buffer.allocUnsafe(before.size);
    let offset = 0;
    while (offset < bytes.length) {
      const count = readSync(descriptor, bytes, offset, bytes.length - offset, null);
      if (count === 0) fail(code);
      offset += count;
    }
    const after = fstatSync(descriptor);
    const pathAfter = lstatSync(path);
    if (
      after.dev !== before.dev ||
      after.ino !== before.ino ||
      after.size !== before.size ||
      after.mtimeMs !== before.mtimeMs ||
      after.ctimeMs !== before.ctimeMs ||
      pathAfter.dev !== before.dev ||
      pathAfter.ino !== before.ino ||
      realpathSync(path) !== path
    ) {
      fail(code);
    }
    return Object.freeze({ bytes, bytesCount: bytes.length, sha256: sha256(bytes) });
  } catch (error) {
    if (error?.message === code) throw error;
    fail(code);
  } finally {
    if (descriptor !== null) {
      try {
        closeSync(descriptor);
      } catch {
        // Closing failure cannot make rejected protected input admissible.
      }
    }
  }
};

const canonicalOrigin = (value) => {
  if (typeof value !== "string" || value.length > 512) return false;
  try {
    const parsed = new URL(value);
    return (
      parsed.protocol === "https:" &&
      parsed.username === "" &&
      parsed.password === "" &&
      parsed.port === "" &&
      parsed.pathname === "/" &&
      parsed.search === "" &&
      parsed.hash === "" &&
      parsed.origin === value &&
      parsed.hostname === parsed.hostname.toLowerCase()
    );
  } catch {
    return false;
  }
};

const canonicalPublicMcpEndpoint = (value, toriiBaseUrl) => {
  if (typeof value !== "string" || value !== `${toriiBaseUrl}/v1/mcp`) {
    return false;
  }
  try {
    const parsed = new URL(value);
    return (
      parsed.protocol === "https:" &&
      parsed.username === "" &&
      parsed.password === "" &&
      parsed.port === "" &&
      parsed.pathname === "/v1/mcp" &&
      parsed.search === "" &&
      parsed.hash === "" &&
      parsed.hostname !== "taira.sora.org"
    );
  } catch {
    return false;
  }
};

const parseAuthorityKey = (record, expectedPin, label) => {
  if (!nonzeroSha256(expectedPin)) fail(`${label}_PIN_INVALID`);
  let key;
  try {
    key = createPublicKey(record.bytes);
  } catch {
    fail(`${label}_KEY_INVALID`);
  }
  const der = key.export({ type: "spki", format: "der" });
  if (
    key.asymmetricKeyType !== "ed25519" ||
    der.length !== 44 ||
    sha256(der) !== expectedPin
  ) {
    fail(`${label}_KEY_INVALID`);
  }
  return Object.freeze({ key, sha256: expectedPin });
};

const validateEpoch = (epoch, label) => {
  if (
    !exactKeys(epoch, [
      "epoch",
      "chainId",
      "genesisSha256",
      "i105Discriminant",
      "status",
      "toriiBaseUrl",
      "publicNodeMcpEndpoint",
      "explorerBaseUrl",
    ]) ||
    !Number.isSafeInteger(epoch.epoch) ||
    epoch.epoch < 1 ||
    !TAIRA_KNOWN_CHAIN_IDS.includes(epoch.chainId) ||
    !nonzeroSha256(epoch.genesisSha256) ||
    epoch.i105Discriminant !== 369 ||
    !["current", "retired"].includes(epoch.status)
  ) {
    fail(`${label}_INVALID`);
  }
  if (epoch.status === "current") {
    if (
      !canonicalOrigin(epoch.toriiBaseUrl) ||
      !canonicalPublicMcpEndpoint(epoch.publicNodeMcpEndpoint, epoch.toriiBaseUrl) ||
      !canonicalOrigin(epoch.explorerBaseUrl)
    ) {
      fail(`${label}_ROUTING_INVALID`);
    }
  } else if (
    epoch.toriiBaseUrl !== null ||
    epoch.publicNodeMcpEndpoint !== null ||
    epoch.explorerBaseUrl !== null
  ) {
    fail(`${label}_RETIRED_ROUTE_PRESENT`);
  }
};

export const inspectTairaDeploymentManifestV1 = ({
  manifestRecord,
  evaluationEpochSeconds,
  operatorKeySha256,
  reviewerKeySha256,
}) => {
  let manifest;
  try {
    manifest = parseStrictJsonBytes(manifestRecord.bytes);
  } catch {
    fail("TAIRA_DEPLOYMENT_MANIFEST_JSON_INVALID");
  }
  const canonicalBytes = Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  if (!canonicalBytes.equals(manifestRecord.bytes)) {
    fail("TAIRA_DEPLOYMENT_MANIFEST_NOT_CANONICAL");
  }
  if (
    !exactKeys(manifest, [
      "schemaVersion",
      "contractId",
      "status",
      "networkId",
      "manifestSequenceNumber",
      "issuedAtEpochSeconds",
      "reviewedAtEpochSeconds",
      "currentEpoch",
      "authorities",
      "epochs",
      "authorization",
    ]) ||
    manifest.schemaVersion !== TAIRA_DEPLOYMENT_MANIFEST_V1.schemaVersion ||
    manifest.contractId !== TAIRA_DEPLOYMENT_MANIFEST_V1.contractId ||
    manifest.status !== "qualified" ||
    manifest.networkId !== "taira" ||
    !Number.isSafeInteger(manifest.manifestSequenceNumber) ||
    manifest.manifestSequenceNumber < 1 ||
    !Number.isSafeInteger(manifest.issuedAtEpochSeconds) ||
    !Number.isSafeInteger(manifest.reviewedAtEpochSeconds) ||
    manifest.issuedAtEpochSeconds < 1 ||
    manifest.reviewedAtEpochSeconds < manifest.issuedAtEpochSeconds ||
    !Number.isSafeInteger(manifest.currentEpoch) ||
    manifest.currentEpoch < 1 ||
    !Number.isSafeInteger(evaluationEpochSeconds) ||
    evaluationEpochSeconds < 1 ||
    manifest.reviewedAtEpochSeconds > evaluationEpochSeconds + MAXIMUM_CLOCK_SKEW_SECONDS ||
    evaluationEpochSeconds - manifest.reviewedAtEpochSeconds > MAXIMUM_MANIFEST_AGE_SECONDS ||
    !exactKeys(manifest.authorization, [
      "authorizesDeploymentIdentity",
      "authorizesFundedCanary",
      "authorizesRelease",
    ]) ||
    manifest.authorization.authorizesDeploymentIdentity !== true ||
    manifest.authorization.authorizesFundedCanary !== false ||
    manifest.authorization.authorizesRelease !== false
  ) {
    fail("TAIRA_DEPLOYMENT_MANIFEST_SHAPE_INVALID");
  }
  if (
    !exactKeys(manifest.authorities, ["operator", "independentReviewer"]) ||
    !exactKeys(manifest.authorities.operator, ["keyId", "publicKeySha256"]) ||
    !exactKeys(manifest.authorities.independentReviewer, ["keyId", "publicKeySha256"]) ||
    !KEY_ID.test(manifest.authorities.operator.keyId ?? "") ||
    !KEY_ID.test(manifest.authorities.independentReviewer.keyId ?? "") ||
    manifest.authorities.operator.keyId === manifest.authorities.independentReviewer.keyId ||
    manifest.authorities.operator.publicKeySha256 !== operatorKeySha256 ||
    manifest.authorities.independentReviewer.publicKeySha256 !== reviewerKeySha256 ||
    operatorKeySha256 === reviewerKeySha256
  ) {
    fail("TAIRA_DEPLOYMENT_MANIFEST_AUTHORITIES_INVALID");
  }
  if (!Array.isArray(manifest.epochs) || manifest.epochs.length !== 2) {
    fail("TAIRA_DEPLOYMENT_MANIFEST_EPOCHS_INVALID");
  }
  manifest.epochs.forEach((epoch, index) => validateEpoch(epoch, `TAIRA_DEPLOYMENT_EPOCH_${index}`));
  const chainIds = manifest.epochs.map((epoch) => epoch.chainId).sort();
  const knownIds = [...TAIRA_KNOWN_CHAIN_IDS].sort();
  const epochs = manifest.epochs.map((epoch) => epoch.epoch);
  const genesis = manifest.epochs.map((epoch) => epoch.genesisSha256);
  const current = manifest.epochs.filter((epoch) => epoch.status === "current");
  const retired = manifest.epochs.filter((epoch) => epoch.status === "retired");
  if (
    chainIds.join("\0") !== knownIds.join("\0") ||
    new Set(epochs).size !== 2 ||
    new Set(genesis).size !== 2 ||
    current.length !== 1 ||
    retired.length !== 1 ||
    current[0].epoch !== manifest.currentEpoch ||
    current[0].epoch <= retired[0].epoch
  ) {
    fail("TAIRA_DEPLOYMENT_MANIFEST_EPOCH_MAPPING_INVALID");
  }
  return Object.freeze({ manifest, current: current[0], retired: retired[0] });
};

export const verifyTairaDeploymentManifestV1 = ({
  manifestPath,
  operatorSignaturePath,
  reviewerSignaturePath,
  operatorPublicKeyPath,
  reviewerPublicKeyPath,
  expectedOperatorKeySha256,
  expectedReviewerKeySha256,
  evaluationEpochSeconds,
}) => {
  const manifestRecord = readProtectedFile(
    manifestPath,
    MAXIMUM_MANIFEST_BYTES,
    "TAIRA_DEPLOYMENT_MANIFEST_FILE_INVALID",
  );
  const operatorSignature = readProtectedFile(
    operatorSignaturePath,
    MAXIMUM_SIGNATURE_BYTES,
    "TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_FILE_INVALID",
  );
  const reviewerSignature = readProtectedFile(
    reviewerSignaturePath,
    MAXIMUM_SIGNATURE_BYTES,
    "TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_FILE_INVALID",
  );
  const operatorKeyRecord = readProtectedFile(
    operatorPublicKeyPath,
    MAXIMUM_KEY_BYTES,
    "TAIRA_DEPLOYMENT_OPERATOR_KEY_FILE_INVALID",
  );
  const reviewerKeyRecord = readProtectedFile(
    reviewerPublicKeyPath,
    MAXIMUM_KEY_BYTES,
    "TAIRA_DEPLOYMENT_REVIEWER_KEY_FILE_INVALID",
  );
  const operator = parseAuthorityKey(
    operatorKeyRecord,
    expectedOperatorKeySha256,
    "TAIRA_DEPLOYMENT_OPERATOR",
  );
  const reviewer = parseAuthorityKey(
    reviewerKeyRecord,
    expectedReviewerKeySha256,
    "TAIRA_DEPLOYMENT_REVIEWER",
  );
  if (
    operatorSignature.bytes.length !== 64 ||
    !verifySignature(null, manifestRecord.bytes, operator.key, operatorSignature.bytes)
  ) {
    fail("TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_INVALID");
  }
  if (
    reviewerSignature.bytes.length !== 64 ||
    !verifySignature(null, manifestRecord.bytes, reviewer.key, reviewerSignature.bytes)
  ) {
    fail("TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_INVALID");
  }
  const inspected = inspectTairaDeploymentManifestV1({
    manifestRecord,
    evaluationEpochSeconds,
    operatorKeySha256: operator.sha256,
    reviewerKeySha256: reviewer.sha256,
  });
  return Object.freeze({
    schemaVersion: 1,
    contractId: "sora-taira-deployment-manifest-admission-v1",
    status: "admitted-for-build-binding",
    networkId: "taira",
    manifestSha256: manifestRecord.sha256,
    manifestBytes: manifestRecord.bytesCount,
    manifestSequenceNumber: inspected.manifest.manifestSequenceNumber,
    issuedAtEpochSeconds: inspected.manifest.issuedAtEpochSeconds,
    reviewedAtEpochSeconds: inspected.manifest.reviewedAtEpochSeconds,
    evaluationEpochSeconds,
    operatorKeyId: inspected.manifest.authorities.operator.keyId,
    operatorKeySha256: operator.sha256,
    reviewerKeyId: inspected.manifest.authorities.independentReviewer.keyId,
    reviewerKeySha256: reviewer.sha256,
    current: inspected.current,
    retired: inspected.retired,
    authorization: {
      authorizesBuildBinding: true,
      authorizesFundedCanary: false,
      authorizesRelease: false,
    },
  });
};
