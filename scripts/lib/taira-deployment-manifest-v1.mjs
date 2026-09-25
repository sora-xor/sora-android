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
export const TAIRA_CURRENT_CHAIN_ID =
  "fc56984b-2be7-431d-840e-21514d1883f0";
export const TAIRA_PUBLIC_TORII_ROOT = "https://taira.sora.org";
export const TAIRA_PUBLIC_MCP_ENDPOINT = `${TAIRA_PUBLIC_TORII_ROOT}/v1/mcp`;

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

export const parseExpectedTairaDeploymentManifestSequenceNumberV1 = (value) => {
  if (typeof value !== "string" || !/^[1-9][0-9]{0,15}$/.test(value)) {
    fail("TAIRA_DEPLOYMENT_EXPECTED_MANIFEST_SEQUENCE_INVALID");
  }
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) {
    fail("TAIRA_DEPLOYMENT_EXPECTED_MANIFEST_SEQUENCE_INVALID");
  }
  return parsed;
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
  if (value !== TAIRA_PUBLIC_TORII_ROOT) return false;
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
      parsed.hostname === "taira.sora.org"
    );
  } catch {
    return false;
  }
};

const canonicalPublicMcpEndpoint = (value, toriiBaseUrl) => {
  if (
    toriiBaseUrl !== TAIRA_PUBLIC_TORII_ROOT ||
    value !== TAIRA_PUBLIC_MCP_ENDPOINT
  ) {
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
      parsed.hostname === "taira.sora.org"
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
    key.asymmetricKeyType !== "ec" ||
    !["prime256v1", "P-256"].includes(key.asymmetricKeyDetails?.namedCurve) ||
    der.length !== 91 ||
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
      epoch.chainId !== TAIRA_CURRENT_CHAIN_ID ||
      !canonicalOrigin(epoch.toriiBaseUrl) ||
      !canonicalPublicMcpEndpoint(epoch.publicNodeMcpEndpoint, epoch.toriiBaseUrl) ||
      !canonicalOrigin(epoch.explorerBaseUrl)
    ) {
      fail(`${label}_ROUTING_INVALID`);
    }
  } else {
    if (
      epoch.chainId === TAIRA_CURRENT_CHAIN_ID ||
      epoch.toriiBaseUrl !== null ||
      epoch.publicNodeMcpEndpoint !== null ||
      epoch.explorerBaseUrl !== null
    ) {
      fail(`${label}_RETIRED_ROUTE_PRESENT`);
    }
  }
};

export const inspectTairaDeploymentManifestV1 = ({
  manifestRecord,
  evaluationEpochSeconds,
  expectedManifestSequenceNumber,
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
      "pendingRowPolicy",
      "epochs",
      "authorization",
    ]) ||
    manifest.schemaVersion !== TAIRA_DEPLOYMENT_MANIFEST_V1.schemaVersion ||
    manifest.contractId !== TAIRA_DEPLOYMENT_MANIFEST_V1.contractId ||
    manifest.status !== "qualified" ||
    manifest.networkId !== "taira" ||
    !Number.isSafeInteger(manifest.manifestSequenceNumber) ||
    manifest.manifestSequenceNumber < 1 ||
    !Number.isSafeInteger(expectedManifestSequenceNumber) ||
    expectedManifestSequenceNumber < 1 ||
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
  if (manifest.manifestSequenceNumber !== expectedManifestSequenceNumber) {
    fail("TAIRA_DEPLOYMENT_MANIFEST_SEQUENCE_MISMATCH");
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
  if (
    !exactKeys(manifest.pendingRowPolicy, [
      "schemaVersion",
      "preserveExactChainUuid",
      "mismatchedCurrentDisposition",
      "reinterpretationAllowed",
    ]) ||
    manifest.pendingRowPolicy.schemaVersion !== 77 ||
    manifest.pendingRowPolicy.preserveExactChainUuid !== true ||
    manifest.pendingRowPolicy.mismatchedCurrentDisposition !==
      "quarantine-recovery-only" ||
    manifest.pendingRowPolicy.reinterpretationAllowed !== false
  ) {
    fail("TAIRA_DEPLOYMENT_PENDING_ROW_POLICY_INVALID");
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
  expectedManifestSequenceNumber,
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
    operatorSignature.bytes.length < 68 ||
    operatorSignature.bytes.length > 72 ||
    !verifySignature(
      "sha256",
      manifestRecord.bytes,
      operator.key,
      operatorSignature.bytes,
    )
  ) {
    fail("TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_INVALID");
  }
  if (
    reviewerSignature.bytes.length < 68 ||
    reviewerSignature.bytes.length > 72 ||
    !verifySignature(
      "sha256",
      manifestRecord.bytes,
      reviewer.key,
      reviewerSignature.bytes,
    )
  ) {
    fail("TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_INVALID");
  }
  const inspected = inspectTairaDeploymentManifestV1({
    manifestRecord,
    evaluationEpochSeconds,
    expectedManifestSequenceNumber,
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
    operatorSignatureSha256: operatorSignature.sha256,
    operatorSignatureBytes: operatorSignature.bytesCount,
    reviewerSignatureSha256: reviewerSignature.sha256,
    reviewerSignatureBytes: reviewerSignature.bytesCount,
    operatorPublicKeyFileSha256: operatorKeyRecord.sha256,
    operatorPublicKeyFileBytes: operatorKeyRecord.bytesCount,
    reviewerPublicKeyFileSha256: reviewerKeyRecord.sha256,
    reviewerPublicKeyFileBytes: reviewerKeyRecord.bytesCount,
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
