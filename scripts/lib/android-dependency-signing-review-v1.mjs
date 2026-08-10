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

export const ANDROID_DEPENDENCY_SIGNING_REVIEW_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-android-dependency-signing-review-v1",
  admissionContractId:
    "sora-android-dependency-signing-review-admission-v1",
});

export const ANDROID_DEPENDENCY_SIGNING_REVIEWED_INPUT_KEYS = Object.freeze([
  "gradleDependencyProvenanceSha256",
  "verificationMetadataSha256",
  "verificationMetadataDigestSha256",
  "vendorSourceProvenanceSha256",
  "vendorContentsManifestSha256",
  "lockFileSetSha256",
  "lockConfigurationInventorySha256",
  "androidProductionSigningIdentitySha256",
  "productionAppSigningCertificateSha256",
  "productionUploadCertificateSha256",
]);

export const ANDROID_DEPENDENCY_SIGNING_REVIEW_QUALIFICATION_KEYS =
  Object.freeze([
    "vendoredCoordinateCountReviewed",
    "lockFileCountReviewed",
    "lockConfigurationCountReviewed",
    "sourceToBinaryCorrespondenceReviewed",
    "licensesAndNoticesReviewed",
    "sbomReviewed",
    "provenanceReviewed",
    "buildAttestationsReviewed",
    "repositoryAllowlistReviewed",
    "verificationMetadataReviewed",
    "productionReleaseLocksReviewed",
    "idensicTensorflowWorkaroundReviewed",
    "credentialRevocationAndRotationReviewed",
    "productionAppSigningIdentityReviewed",
    "productionUploadCertificateReviewed",
    "playAppSigningContinuityReviewed",
  ]);

export const ANDROID_DEPENDENCY_SIGNING_REVIEW_EXTERNAL_EVIDENCE_KEYS =
  Object.freeze([
    "vendorSourceToBinaryReviewReceiptSha256",
    "licensesAndNoticesReviewReceiptSha256",
    "sbomReviewReceiptSha256",
    "dependencyBuildAttestationReviewReceiptSha256",
    "repositoryAllowlistReviewReceiptSha256",
    "verificationMetadataReviewReceiptSha256",
    "productionReleaseLockReviewReceiptSha256",
    "idensicTensorflowWorkaroundReviewReceiptSha256",
    "credentialIncidentClosureReceiptSha256",
    "playSigningContinuityReceiptSha256",
  ]);

const MAXIMUM_MANIFEST_BYTES = 128 * 1024;
const MAXIMUM_KEY_BYTES = 8 * 1024;
const MAXIMUM_SIGNATURE_BYTES = 1024;
const MAXIMUM_REVIEW_AGE_SECONDS = 7 * 24 * 60 * 60;
const MAXIMUM_CLOCK_SKEW_SECONDS = 60;
const SHA256 = /^[0-9a-f]{64}$/;
const REVISION = /^[0-9a-f]{40}$/;
const KEY_ID = /^[a-z0-9][a-z0-9._:-]{2,127}$/;
const P256_ORDER = BigInt(
  "0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",
);
const P256_HALF_ORDER = P256_ORDER / 2n;

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

const nonzeroRevision = (value) =>
  typeof value === "string" && REVISION.test(value) && !/^0+$/.test(value);

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

const canonicalP256Signature = (bytes) => {
  if (!Buffer.isBuffer(bytes) || bytes.length !== 64) return false;
  const r = BigInt(`0x${bytes.subarray(0, 32).toString("hex")}`);
  const s = BigInt(`0x${bytes.subarray(32).toString("hex")}`);
  return r > 0n && r < P256_ORDER && s > 0n && s <= P256_HALF_ORDER;
};

const canonicalJsonValue = (value) => {
  if (Array.isArray(value)) return value.map(canonicalJsonValue);
  if (value !== null && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, canonicalJsonValue(value[key])]),
    );
  }
  return value;
};

export const androidDependencySigningReviewContractSha256V1 = ({
  sourceRevision,
  reviewedInputs,
}) => {
  if (
    !nonzeroRevision(sourceRevision) ||
    !exactKeys(
      reviewedInputs,
      ANDROID_DEPENDENCY_SIGNING_REVIEWED_INPUT_KEYS,
    ) ||
    Object.values(reviewedInputs).some((value) => !nonzeroSha256(value))
  ) {
    fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_INPUT_INVALID");
  }
  const contract = {
    schemaVersion: ANDROID_DEPENDENCY_SIGNING_REVIEW_V1.schemaVersion,
    contractId: ANDROID_DEPENDENCY_SIGNING_REVIEW_V1.contractId,
    platform: "android",
    applicationId: "jp.co.soramitsu.sora",
    sourceRevision,
    reviewedInputs,
  };
  return sha256(
    Buffer.from(JSON.stringify(canonicalJsonValue(contract)), "utf8"),
  );
};

const canonicalAbsolutePath = (value) =>
  typeof value === "string" &&
  value.length > 0 &&
  isAbsolute(value) &&
  resolve(value) === value;

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
      const count = readSync(
        descriptor,
        bytes,
        offset,
        bytes.length - offset,
        null,
      );
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
    return Object.freeze({
      bytes,
      bytesCount: bytes.length,
      sha256: sha256(bytes),
    });
  } catch (error) {
    if (error?.message === code) throw error;
    fail(code);
  } finally {
    if (descriptor !== null) {
      try {
        closeSync(descriptor);
      } catch {
        // A close failure cannot make rejected protected input admissible.
      }
    }
  }
};

const parseP256Authority = (record, expectedPin, label) => {
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
    key.asymmetricKeyDetails?.namedCurve !== "prime256v1" ||
    sha256(der) !== expectedPin
  ) {
    fail(`${label}_KEY_INVALID`);
  }
  return Object.freeze({ key, sha256: expectedPin });
};

const qualificationIsComplete = (value) =>
  exactKeys(value, ANDROID_DEPENDENCY_SIGNING_REVIEW_QUALIFICATION_KEYS) &&
  value.vendoredCoordinateCountReviewed === 11 &&
  value.lockFileCountReviewed === 31 &&
  value.lockConfigurationCountReviewed === 271 &&
  ANDROID_DEPENDENCY_SIGNING_REVIEW_QUALIFICATION_KEYS.slice(3).every(
    (key) => value[key] === true,
  );

const authorizationIsQualified = (value) =>
  exactKeys(value, [
    "authorizesDependencySigningQualification",
    "authorizesArtifactSigning",
    "authorizesRelease",
    "authorizesProductionMutation",
  ]) &&
  value.authorizesDependencySigningQualification === true &&
  value.authorizesArtifactSigning === false &&
  value.authorizesRelease === false &&
  value.authorizesProductionMutation === false;

const privacyIsQualified = (value) =>
  exactKeys(value, [
    "aggregateOnly",
    "secretsIncluded",
    "privateKeysIncluded",
    "keyPasswordsIncluded",
  ]) &&
  value.aggregateOnly === true &&
  value.secretsIncluded === false &&
  value.privateKeysIncluded === false &&
  value.keyPasswordsIncluded === false;

export const inspectAndroidDependencySigningReviewV1 = ({
  manifestRecord,
  evaluationEpochSeconds,
  expectedProducerKeySha256,
  expectedReviewerKeySha256,
  expectedReviewSequenceNumber,
  expectedSourceRevision,
  expectedReviewContractSha256,
  expectedReviewedInputs,
}) => {
  let manifest;
  try {
    manifest = parseStrictJsonBytes(manifestRecord.bytes);
  } catch {
    fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_MANIFEST_JSON_INVALID");
  }
  const canonicalBytes = Buffer.from(
    `${JSON.stringify(manifest, null, 2)}\n`,
    "utf8",
  );
  if (!canonicalBytes.equals(manifestRecord.bytes)) {
    fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_MANIFEST_NOT_CANONICAL");
  }
  if (
    !exactKeys(manifest, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "applicationId",
      "reviewSequenceNumber",
      "sourceRevision",
      "issuedAtEpochSeconds",
      "reviewedAtEpochSeconds",
      "reviewContractSha256",
      "authorities",
      "reviewedInputs",
      "externalEvidence",
      "qualification",
      "privacy",
      "authorization",
      "blockingReasons",
    ]) ||
    manifest.schemaVersion !==
      ANDROID_DEPENDENCY_SIGNING_REVIEW_V1.schemaVersion ||
    manifest.contractId !== ANDROID_DEPENDENCY_SIGNING_REVIEW_V1.contractId ||
    manifest.status !== "qualified" ||
    manifest.platform !== "android" ||
    manifest.applicationId !== "jp.co.soramitsu.sora" ||
    manifest.reviewSequenceNumber !== expectedReviewSequenceNumber ||
    !Number.isSafeInteger(expectedReviewSequenceNumber) ||
    expectedReviewSequenceNumber < 1 ||
    manifest.sourceRevision !== expectedSourceRevision ||
    !nonzeroRevision(expectedSourceRevision) ||
    manifest.reviewContractSha256 !== expectedReviewContractSha256 ||
    !nonzeroSha256(expectedReviewContractSha256) ||
    !Number.isSafeInteger(manifest.issuedAtEpochSeconds) ||
    !Number.isSafeInteger(manifest.reviewedAtEpochSeconds) ||
    manifest.issuedAtEpochSeconds < 1 ||
    manifest.reviewedAtEpochSeconds < manifest.issuedAtEpochSeconds ||
    !Number.isSafeInteger(evaluationEpochSeconds) ||
    evaluationEpochSeconds < 1 ||
    manifest.reviewedAtEpochSeconds >
      evaluationEpochSeconds + MAXIMUM_CLOCK_SKEW_SECONDS ||
    evaluationEpochSeconds - manifest.reviewedAtEpochSeconds >
      MAXIMUM_REVIEW_AGE_SECONDS ||
    !Array.isArray(manifest.blockingReasons) ||
    manifest.blockingReasons.length !== 0 ||
    !qualificationIsComplete(manifest.qualification) ||
    !privacyIsQualified(manifest.privacy) ||
    !authorizationIsQualified(manifest.authorization)
  ) {
    fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_MANIFEST_SHAPE_INVALID");
  }
  if (
    !exactKeys(
      manifest.externalEvidence,
      ANDROID_DEPENDENCY_SIGNING_REVIEW_EXTERNAL_EVIDENCE_KEYS,
    ) ||
    Object.values(manifest.externalEvidence).some(
      (value) => !nonzeroSha256(value),
    )
  ) {
    fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_EXTERNAL_EVIDENCE_INVALID");
  }
  if (
    !exactKeys(
      manifest.reviewedInputs,
      ANDROID_DEPENDENCY_SIGNING_REVIEWED_INPUT_KEYS,
    ) ||
    !exactKeys(
      expectedReviewedInputs,
      ANDROID_DEPENDENCY_SIGNING_REVIEWED_INPUT_KEYS,
    ) ||
    ANDROID_DEPENDENCY_SIGNING_REVIEWED_INPUT_KEYS.some(
      (key) =>
        !nonzeroSha256(manifest.reviewedInputs[key]) ||
        manifest.reviewedInputs[key] !== expectedReviewedInputs[key],
    ) ||
    androidDependencySigningReviewContractSha256V1({
      sourceRevision: expectedSourceRevision,
      reviewedInputs: expectedReviewedInputs,
    }) !== expectedReviewContractSha256
  ) {
    fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_INPUTS_DIVERGED");
  }
  if (
    !exactKeys(manifest.authorities, ["producer", "independentReviewer"]) ||
    !exactKeys(manifest.authorities.producer, ["keyId", "publicKeySha256"]) ||
    !exactKeys(manifest.authorities.independentReviewer, [
      "keyId",
      "publicKeySha256",
    ]) ||
    !KEY_ID.test(manifest.authorities.producer.keyId ?? "") ||
    !KEY_ID.test(manifest.authorities.independentReviewer.keyId ?? "") ||
    manifest.authorities.producer.keyId ===
      manifest.authorities.independentReviewer.keyId ||
    manifest.authorities.producer.publicKeySha256 !==
      expectedProducerKeySha256 ||
    manifest.authorities.independentReviewer.publicKeySha256 !==
      expectedReviewerKeySha256 ||
    expectedProducerKeySha256 === expectedReviewerKeySha256
  ) {
    fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_AUTHORITIES_INVALID");
  }
  return Object.freeze({ manifest });
};

export const verifyAndroidDependencySigningReviewV1 = ({
  manifestPath,
  producerSignaturePath,
  reviewerSignaturePath,
  producerPublicKeyPath,
  reviewerPublicKeyPath,
  expectedProducerKeySha256,
  expectedReviewerKeySha256,
  expectedReviewSequenceNumber,
  expectedSourceRevision,
  expectedReviewContractSha256,
  expectedReviewedInputs,
  evaluationEpochSeconds,
}) => {
  const manifestRecord = readProtectedFile(
    manifestPath,
    MAXIMUM_MANIFEST_BYTES,
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_MANIFEST_FILE_INVALID",
  );
  const producerSignature = readProtectedFile(
    producerSignaturePath,
    MAXIMUM_SIGNATURE_BYTES,
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_SIGNATURE_FILE_INVALID",
  );
  const reviewerSignature = readProtectedFile(
    reviewerSignaturePath,
    MAXIMUM_SIGNATURE_BYTES,
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_SIGNATURE_FILE_INVALID",
  );
  const producerKeyRecord = readProtectedFile(
    producerPublicKeyPath,
    MAXIMUM_KEY_BYTES,
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_KEY_FILE_INVALID",
  );
  const reviewerKeyRecord = readProtectedFile(
    reviewerPublicKeyPath,
    MAXIMUM_KEY_BYTES,
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_KEY_FILE_INVALID",
  );
  const producer = parseP256Authority(
    producerKeyRecord,
    expectedProducerKeySha256,
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER",
  );
  const reviewer = parseP256Authority(
    reviewerKeyRecord,
    expectedReviewerKeySha256,
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER",
  );
  if (
    !canonicalP256Signature(producerSignature.bytes) ||
    !verifySignature(
      "sha256",
      manifestRecord.bytes,
      { key: producer.key, dsaEncoding: "ieee-p1363" },
      producerSignature.bytes,
    )
  ) {
    fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_SIGNATURE_INVALID");
  }
  if (
    !canonicalP256Signature(reviewerSignature.bytes) ||
    !verifySignature(
      "sha256",
      manifestRecord.bytes,
      { key: reviewer.key, dsaEncoding: "ieee-p1363" },
      reviewerSignature.bytes,
    )
  ) {
    fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_SIGNATURE_INVALID");
  }
  const inspected = inspectAndroidDependencySigningReviewV1({
    manifestRecord,
    evaluationEpochSeconds,
    expectedProducerKeySha256: producer.sha256,
    expectedReviewerKeySha256: reviewer.sha256,
    expectedReviewSequenceNumber,
    expectedSourceRevision,
    expectedReviewContractSha256,
    expectedReviewedInputs,
  });
  return Object.freeze({
    schemaVersion: 1,
    contractId:
      ANDROID_DEPENDENCY_SIGNING_REVIEW_V1.admissionContractId,
    status: "admitted-for-dependency-signing-gate",
    platform: "android",
    applicationId: "jp.co.soramitsu.sora",
    manifestSha256: manifestRecord.sha256,
    manifestBytes: manifestRecord.bytesCount,
    reviewSequenceNumber: inspected.manifest.reviewSequenceNumber,
    sourceRevision: inspected.manifest.sourceRevision,
    reviewContractSha256: inspected.manifest.reviewContractSha256,
    issuedAtEpochSeconds: inspected.manifest.issuedAtEpochSeconds,
    reviewedAtEpochSeconds: inspected.manifest.reviewedAtEpochSeconds,
    evaluationEpochSeconds,
    producerKeyId: inspected.manifest.authorities.producer.keyId,
    producerKeySha256: producer.sha256,
    reviewerKeyId:
      inspected.manifest.authorities.independentReviewer.keyId,
    reviewerKeySha256: reviewer.sha256,
    reviewedInputs: inspected.manifest.reviewedInputs,
    externalEvidence: inspected.manifest.externalEvidence,
    qualification: inspected.manifest.qualification,
    privacy: inspected.manifest.privacy,
    authorization: {
      authorizesDependencySigningQualification: true,
      authorizesArtifactSigning: false,
      authorizesRelease: false,
      authorizesProductionMutation: false,
    },
  });
};

export const lintAndroidDependencySigningReviewBlockedTemplatesV1 = ({
  manifest,
  admission,
}) => {
  const blockedAuthorization = (value) =>
    exactKeys(value, [
      "authorizesDependencySigningQualification",
      "authorizesArtifactSigning",
      "authorizesRelease",
      "authorizesProductionMutation",
    ]) && Object.values(value).every((item) => item === false);
  return (
    exactKeys(manifest, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "applicationId",
      "reviewSequenceNumber",
      "sourceRevision",
      "issuedAtEpochSeconds",
      "reviewedAtEpochSeconds",
      "reviewContractSha256",
      "authorities",
      "reviewedInputs",
      "externalEvidence",
      "qualification",
      "privacy",
      "authorization",
      "blockingReasons",
    ]) &&
    manifest.schemaVersion === 1 &&
    manifest.contractId === ANDROID_DEPENDENCY_SIGNING_REVIEW_V1.contractId &&
    manifest.status === "blocked" &&
    manifest.platform === "android" &&
    manifest.applicationId === "jp.co.soramitsu.sora" &&
    manifest.reviewSequenceNumber === null &&
    manifest.sourceRevision === null &&
    manifest.issuedAtEpochSeconds === null &&
    manifest.reviewedAtEpochSeconds === null &&
    manifest.reviewContractSha256 === null &&
    exactKeys(manifest.authorities, ["producer", "independentReviewer"]) &&
    [manifest.authorities.producer, manifest.authorities.independentReviewer].every(
      (authority) =>
        exactKeys(authority, ["keyId", "publicKeySha256"]) &&
        authority.keyId === null &&
        authority.publicKeySha256 === null,
    ) &&
    exactKeys(
      manifest.reviewedInputs,
      ANDROID_DEPENDENCY_SIGNING_REVIEWED_INPUT_KEYS,
    ) &&
    Object.values(manifest.reviewedInputs).every((value) => value === null) &&
    exactKeys(
      manifest.externalEvidence,
      ANDROID_DEPENDENCY_SIGNING_REVIEW_EXTERNAL_EVIDENCE_KEYS,
    ) &&
    Object.values(manifest.externalEvidence).every((value) => value === null) &&
    exactKeys(
      manifest.qualification,
      ANDROID_DEPENDENCY_SIGNING_REVIEW_QUALIFICATION_KEYS,
    ) &&
    manifest.qualification.vendoredCoordinateCountReviewed === 0 &&
    manifest.qualification.lockFileCountReviewed === 0 &&
    manifest.qualification.lockConfigurationCountReviewed === 0 &&
    ANDROID_DEPENDENCY_SIGNING_REVIEW_QUALIFICATION_KEYS.slice(3).every(
      (key) => manifest.qualification[key] === false,
    ) &&
    exactKeys(manifest.privacy, [
      "aggregateOnly",
      "secretsIncluded",
      "privateKeysIncluded",
      "keyPasswordsIncluded",
    ]) &&
    manifest.privacy.aggregateOnly === true &&
    Object.entries(manifest.privacy).every(
      ([key, value]) => key === "aggregateOnly" || value === false,
    ) &&
    Array.isArray(manifest.blockingReasons) &&
    manifest.blockingReasons.length > 0 &&
    blockedAuthorization(manifest.authorization) &&
    exactKeys(admission, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "applicationId",
      "manifestSha256",
      "reviewSequenceNumber",
      "sourceRevision",
      "reviewContractSha256",
      "producerKeySha256",
      "reviewerKeySha256",
      "authorization",
      "blockingReasons",
    ]) &&
    admission.schemaVersion === 1 &&
    admission.contractId ===
      ANDROID_DEPENDENCY_SIGNING_REVIEW_V1.admissionContractId &&
    admission.status === "blocked" &&
    admission.platform === "android" &&
    admission.applicationId === "jp.co.soramitsu.sora" &&
    admission.manifestSha256 === null &&
    admission.reviewSequenceNumber === null &&
    admission.sourceRevision === null &&
    admission.reviewContractSha256 === null &&
    admission.producerKeySha256 === null &&
    admission.reviewerKeySha256 === null &&
    blockedAuthorization(admission.authorization) &&
    Array.isArray(admission.blockingReasons) &&
    admission.blockingReasons.length > 0
  );
};
