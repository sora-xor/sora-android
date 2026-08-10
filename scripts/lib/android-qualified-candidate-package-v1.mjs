import { createHash } from "node:crypto";
import { lstatSync, readFileSync, readdirSync, realpathSync } from "node:fs";
import { isAbsolute, join, relative, resolve } from "node:path";
import {
  hashStableRegularFile,
  readStrictJsonFile,
} from "./strict-evidence.mjs";
import {
  PRODUCTION_ADMISSION_IDENTITY_KEYS,
  QUALIFICATION_WORKFLOW_RELATIVE_PATH,
} from "./qualified-candidate-provenance.mjs";
import {
  isProductionAdmissionV3Envelope,
  productionAdmissionV3ProjectionPrefix,
} from "./production-rollout-v3-contract.mjs";
import { validateAndroidCandidateSigningVerificationV1 } from "./android-candidate-signing-verification-v1.mjs";
import {
  FUNDED_CANARY_CONTROLLER_BUNDLE_FILES,
  FUNDED_CANARY_CONTROLLER_BUNDLE_V1,
} from "./funded-canary-controller-bundle-v1.mjs";
import {
  ANDROID_MIGRATION_CONTROLLER_ENVELOPE_V1,
  ANDROID_MIGRATION_CONTROLLER_FILES,
  ANDROID_MIGRATION_CONTROLLER_MAXIMUM_BYTES,
  inspectAndroidMigrationControllerEnvelopeV1,
} from "./android-migration-controller-envelope-v1.mjs";
import { verifyTairaDeploymentManifestV1 } from "./taira-deployment-manifest-v1.mjs";
import {
  androidDependencySigningReviewContractSha256V1,
  verifyAndroidDependencySigningReviewV1,
} from "./android-dependency-signing-review-v1.mjs";

export const ANDROID_QUALIFIED_CANDIDATE_PACKAGE_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-android-qualified-candidate-package-v1",
});

const SHA256 = /^[0-9a-f]{64}$/;
const REVISION = /^[0-9a-f]{40}$/;
const MAXIMUM_JSON_BYTES = 2 * 1024 * 1024;
const MAXIMUM_AAB_BYTES = 512 * 1024 * 1024;
const MAXIMUM_APK_BYTES = 512 * 1024 * 1024;
const MAXIMUM_MAPPING_BYTES = 256 * 1024 * 1024;
const MAXIMUM_BUILD_LOG_BYTES = 128 * 1024 * 1024;
const MAXIMUM_EVIDENCE_BYTES = 16 * 1024 * 1024;
const MAXIMUM_CANARY_BUNDLE_BYTES = 5 * 1024 * 1024 * 1024;

const fail = (code) => {
  throw new Error(code);
};

const hasExactKeys = (value, keys) =>
  value !== null &&
  typeof value === "object" &&
  !Array.isArray(value) &&
  Object.keys(value).sort().join("\0") === [...keys].sort().join("\0");

const canonicalSha256 = (lines) =>
  createHash("sha256").update(lines.join("\n"), "utf8").digest("hex");

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

const canonicalJsonSha256 = (value) =>
  createHash("sha256")
    .update(JSON.stringify(canonicalJsonValue(value)), "utf8")
    .digest("hex");

const canonicalAbsoluteRoot = (value) =>
  typeof value === "string" &&
  value.length > 0 &&
  isAbsolute(value) &&
  resolve(value) === value &&
  realpathSync(value) === value;

const regularInput = (path, maximumBytes, code) => {
  const record = hashStableRegularFile(path, maximumBytes);
  if (record === null) fail(code);
  let stat;
  try {
    stat = lstatSync(path);
  } catch {
    fail(code);
  }
  if (
    !stat.isFile() ||
    stat.isSymbolicLink() ||
    stat.nlink !== 1 ||
    realpathSync(path) !== path
  ) {
    fail(code);
  }
  return { ...record, device: stat.dev, inode: stat.ino };
};

const strictJsonInput = (path, maximumBytes, code) => {
  const record = readStrictJsonFile(path, maximumBytes);
  if (record === null) fail(code);
  const regular = regularInput(path, maximumBytes, code);
  if (regular.sha256 !== record.sha256 || regular.bytes !== record.byteCount) {
    fail(code);
  }
  return { ...record, device: regular.device, inode: regular.inode };
};

const distinctPhysicalFiles = (records, code) => {
  const identities = records.map((record) => `${record.device}:${record.inode}`);
  if (new Set(identities).size !== identities.length) fail(code);
};

const relativeEvidencePath = (root, value, code) => {
  const components = typeof value === "string" ? value.split("/") : [];
  if (
    typeof value !== "string" ||
    value.length < 1 ||
    isAbsolute(value) ||
    components.length < 1 ||
    components.some(
      (component) =>
        component.length < 1 ||
        component === "." ||
        component === ".." ||
        !/^[A-Za-z0-9._-]+$/.test(component),
    ) ||
    resolve(root, value) === root ||
    relative(root, resolve(root, value)).startsWith("..") ||
    relative(root, resolve(root, value)).includes("\\")
  ) {
    fail(code);
  }
  return resolve(root, value);
};

const evidenceFile = (root, path, expectedSha256, maximumBytes, code) => {
  if (!SHA256.test(expectedSha256 ?? "")) fail(code);
  const absolutePath = relativeEvidencePath(root, path, code);
  const record = regularInput(absolutePath, maximumBytes, code);
  if (record.sha256 !== expectedSha256) fail(code);
  return { sha256: record.sha256, bytes: record.bytes };
};

const artifactProjection = (fileName, record) => ({
  fileName,
  sha256: record.sha256,
  bytes: record.bytes,
});

const artifactProjectionValid = (value, expectedFileName) =>
  hasExactKeys(value, ["fileName", "sha256", "bytes"]) &&
  value.fileName === expectedFileName &&
  SHA256.test(value.sha256 ?? "") &&
  Number.isSafeInteger(value.bytes) &&
  value.bytes > 0;

const fundedCanaryExtractionReceiptValid = (value) =>
  hasExactKeys(value, [
    "schemaVersion",
    "contractId",
    "status",
    "platform",
    "tarSha256",
    "tarBytes",
    "fileCount",
    "files",
    "authorization",
  ]) &&
  value.schemaVersion === FUNDED_CANARY_CONTROLLER_BUNDLE_V1.schemaVersion &&
  value.contractId === FUNDED_CANARY_CONTROLLER_BUNDLE_V1.contractId &&
  value.status === "extracted-unreviewed" &&
  value.platform === "android" &&
  SHA256.test(value.tarSha256 ?? "") &&
  Number.isSafeInteger(value.tarBytes) &&
  value.tarBytes > 0 &&
  value.fileCount === Object.keys(FUNDED_CANARY_CONTROLLER_BUNDLE_FILES).length &&
  hasExactKeys(value.files, Object.keys(FUNDED_CANARY_CONTROLLER_BUNDLE_FILES)) &&
  Object.entries(value.files).every(
    ([name, record]) =>
      hasExactKeys(record, ["sha256", "bytes"]) &&
      SHA256.test(record.sha256 ?? "") &&
      Number.isSafeInteger(record.bytes) &&
      record.bytes > 0 &&
      record.bytes <= FUNDED_CANARY_CONTROLLER_BUNDLE_FILES[name],
  ) &&
  hasExactKeys(value.authorization, [
    "authorizesRelease",
    "authorizesProductionMutation",
  ]) &&
  value.authorization.authorizesRelease === false &&
  value.authorization.authorizesProductionMutation === false;

const migrationControllerExtractionReceiptValid = (value) =>
  hasExactKeys(value, [
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
    "envelopeSha256",
    "envelopeBytes",
    "fileCount",
    "files",
    "authorization",
  ]) &&
  value.schemaVersion === ANDROID_MIGRATION_CONTROLLER_ENVELOPE_V1.schemaVersion &&
  value.contractId === ANDROID_MIGRATION_CONTROLLER_ENVELOPE_V1.contractId &&
  value.status === "extracted-unreviewed" &&
  value.platform === "android" &&
  REVISION.test(value.sourceRevision ?? "") &&
  !/^0+$/.test(value.sourceRevision) &&
  SHA256.test(value.candidateAabSha256 ?? "") &&
  Number.isSafeInteger(value.candidateAabBytes) &&
  value.candidateAabBytes > 0 &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(
    value.runId ?? "",
  ) &&
  value.runId !== "00000000-0000-0000-0000-000000000000" &&
  Number.isSafeInteger(value.qualificationSequenceNumber) &&
  value.qualificationSequenceNumber > 0 &&
  SHA256.test(value.appBuildIdentitySha256 ?? "") &&
  SHA256.test(value.envelopeSha256 ?? "") &&
  Number.isSafeInteger(value.envelopeBytes) &&
  value.envelopeBytes > 0 &&
  value.fileCount === Object.keys(ANDROID_MIGRATION_CONTROLLER_FILES).length &&
  hasExactKeys(value.files, Object.keys(ANDROID_MIGRATION_CONTROLLER_FILES)) &&
  Object.entries(value.files).every(
    ([name, record]) =>
      hasExactKeys(record, ["sha256", "bytes"]) &&
      SHA256.test(record.sha256 ?? "") &&
      Number.isSafeInteger(record.bytes) &&
      record.bytes > 0 &&
      record.bytes <= ANDROID_MIGRATION_CONTROLLER_FILES[name],
  ) &&
  hasExactKeys(value.authorization, [
    "authorizesRelease",
    "authorizesProductionMutation",
  ]) &&
  value.authorization.authorizesRelease === false &&
  value.authorization.authorizesProductionMutation === false;

export const createAndroidQualifiedCandidatePackageV1 = ({
  root,
  sourceRevision,
  evaluatedAtEpochSeconds,
  observedUploadCertificateSha256,
  primaryAabPath,
  reproducedAabPath,
  primaryApkPath,
  reproducedApkPath,
  primaryMappingPath,
  reproducedMappingPath,
  primaryBuildLogPath,
  reproducedBuildLogPath,
  primarySigningVerificationPath,
  reproducedSigningVerificationPath,
  migrationControllerEnvelopePath,
  migrationControllerExtractionReceiptPath,
  tairaDeploymentManifestPath,
  tairaDeploymentOperatorSignaturePath,
  tairaDeploymentReviewerSignaturePath,
  tairaDeploymentOperatorPublicKeyPath,
  tairaDeploymentReviewerPublicKeyPath,
  tairaDeploymentAdmissionReceiptPath,
  expectedTairaDeploymentOperatorKeySha256,
  expectedTairaDeploymentReviewerKeySha256,
  dependencySigningReviewManifestPath,
  dependencySigningReviewProducerSignaturePath,
  dependencySigningReviewReviewerSignaturePath,
  dependencySigningReviewProducerPublicKeyPath,
  dependencySigningReviewReviewerPublicKeyPath,
  dependencySigningReviewAdmissionReceiptPath,
  expectedDependencySigningReviewProducerKeySha256,
  expectedDependencySigningReviewReviewerKeySha256,
  expectedDependencySigningReviewSequenceNumber,
  expectedDependencySigningReviewContractSha256,
  fundedCanaryControllerBundlePath,
  fundedCanaryControllerExtractionReceiptPath,
  productionAdmissionPath,
  candidatePiReceiptPath,
  candidatePiReceiptSignaturePath,
}) => {
  if (!canonicalAbsoluteRoot(root)) fail("CANDIDATE_PACKAGE_ROOT_INVALID");
  if (!REVISION.test(sourceRevision ?? "") || /^0+$/.test(sourceRevision)) {
    fail("CANDIDATE_PACKAGE_SOURCE_REVISION_INVALID");
  }
  if (
    !Number.isSafeInteger(evaluatedAtEpochSeconds) ||
    evaluatedAtEpochSeconds < 1 ||
    evaluatedAtEpochSeconds > Math.floor(Date.now() / 1000)
  ) {
    fail("CANDIDATE_PACKAGE_EVALUATION_EPOCH_INVALID");
  }
  if (
    !SHA256.test(observedUploadCertificateSha256 ?? "") ||
    /^0+$/.test(observedUploadCertificateSha256)
  ) {
    fail("CANDIDATE_PACKAGE_OBSERVED_UPLOAD_CERTIFICATE_INVALID");
  }

  const primaryAab = regularInput(
    primaryAabPath,
    MAXIMUM_AAB_BYTES,
    "CANDIDATE_PACKAGE_PRIMARY_AAB_INVALID",
  );
  const reproducedAab = regularInput(
    reproducedAabPath,
    MAXIMUM_AAB_BYTES,
    "CANDIDATE_PACKAGE_REPRODUCED_AAB_INVALID",
  );
  const primaryApk = regularInput(
    primaryApkPath,
    MAXIMUM_APK_BYTES,
    "CANDIDATE_PACKAGE_PRIMARY_APK_INVALID",
  );
  const reproducedApk = regularInput(
    reproducedApkPath,
    MAXIMUM_APK_BYTES,
    "CANDIDATE_PACKAGE_REPRODUCED_APK_INVALID",
  );
  const primaryMapping = regularInput(
    primaryMappingPath,
    MAXIMUM_MAPPING_BYTES,
    "CANDIDATE_PACKAGE_PRIMARY_MAPPING_INVALID",
  );
  const reproducedMapping = regularInput(
    reproducedMappingPath,
    MAXIMUM_MAPPING_BYTES,
    "CANDIDATE_PACKAGE_REPRODUCED_MAPPING_INVALID",
  );
  const primaryBuildLog = regularInput(
    primaryBuildLogPath,
    MAXIMUM_BUILD_LOG_BYTES,
    "CANDIDATE_PACKAGE_PRIMARY_BUILD_LOG_INVALID",
  );
  const reproducedBuildLog = regularInput(
    reproducedBuildLogPath,
    MAXIMUM_BUILD_LOG_BYTES,
    "CANDIDATE_PACKAGE_REPRODUCED_BUILD_LOG_INVALID",
  );
  distinctPhysicalFiles(
    [
      primaryAab,
      reproducedAab,
      primaryApk,
      reproducedApk,
      primaryMapping,
      reproducedMapping,
      primaryBuildLog,
      reproducedBuildLog,
    ],
    "CANDIDATE_PACKAGE_BUILD_INPUTS_NOT_PHYSICALLY_DISTINCT",
  );
  if (
    primaryAab.sha256 !== reproducedAab.sha256 ||
    primaryAab.bytes !== reproducedAab.bytes
  ) {
    fail("CANDIDATE_PACKAGE_AAB_NOT_REPRODUCIBLE");
  }
  if (
    primaryApk.sha256 !== reproducedApk.sha256 ||
    primaryApk.bytes !== reproducedApk.bytes
  ) {
    fail("CANDIDATE_PACKAGE_APK_NOT_REPRODUCIBLE");
  }
  if (
    primaryMapping.sha256 !== reproducedMapping.sha256 ||
    primaryMapping.bytes !== reproducedMapping.bytes
  ) {
    fail("CANDIDATE_PACKAGE_R8_MAPPING_NOT_REPRODUCIBLE");
  }

  const admission = strictJsonInput(
    productionAdmissionPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_ADMISSION_INVALID",
  );
  const candidatePi = strictJsonInput(
    candidatePiReceiptPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_PI_RECEIPT_INVALID",
  );
  const candidatePiSignature = strictJsonInput(
    candidatePiReceiptSignaturePath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_PI_SIGNATURE_INVALID",
  );
  const primarySigningVerification = strictJsonInput(
    primarySigningVerificationPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_PRIMARY_SIGNING_VERIFICATION_INVALID",
  );
  const reproducedSigningVerification = strictJsonInput(
    reproducedSigningVerificationPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_REPRODUCED_SIGNING_VERIFICATION_INVALID",
  );
  const migrationControllerEnvelope = strictJsonInput(
    migrationControllerEnvelopePath,
    ANDROID_MIGRATION_CONTROLLER_MAXIMUM_BYTES,
    "CANDIDATE_PACKAGE_MIGRATION_CONTROLLER_ENVELOPE_INVALID",
  );
  const migrationControllerExtraction = strictJsonInput(
    migrationControllerExtractionReceiptPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_MIGRATION_CONTROLLER_EXTRACTION_INVALID",
  );
  const tairaDeploymentManifest = strictJsonInput(
    tairaDeploymentManifestPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_MANIFEST_INVALID",
  );
  const tairaDeploymentOperatorSignature = regularInput(
    tairaDeploymentOperatorSignaturePath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_INVALID",
  );
  const tairaDeploymentReviewerSignature = regularInput(
    tairaDeploymentReviewerSignaturePath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_INVALID",
  );
  const tairaDeploymentOperatorPublicKey = regularInput(
    tairaDeploymentOperatorPublicKeyPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_OPERATOR_KEY_INVALID",
  );
  const tairaDeploymentReviewerPublicKey = regularInput(
    tairaDeploymentReviewerPublicKeyPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_REVIEWER_KEY_INVALID",
  );
  const tairaDeploymentAdmission = strictJsonInput(
    tairaDeploymentAdmissionReceiptPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_ADMISSION_INVALID",
  );
  const dependencySigningReviewManifest = strictJsonInput(
    dependencySigningReviewManifestPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_MANIFEST_INVALID",
  );
  const dependencySigningReviewProducerSignature = regularInput(
    dependencySigningReviewProducerSignaturePath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_PRODUCER_SIGNATURE_INVALID",
  );
  const dependencySigningReviewReviewerSignature = regularInput(
    dependencySigningReviewReviewerSignaturePath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_REVIEWER_SIGNATURE_INVALID",
  );
  const dependencySigningReviewProducerPublicKey = regularInput(
    dependencySigningReviewProducerPublicKeyPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_PRODUCER_KEY_INVALID",
  );
  const dependencySigningReviewReviewerPublicKey = regularInput(
    dependencySigningReviewReviewerPublicKeyPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_REVIEWER_KEY_INVALID",
  );
  const dependencySigningReviewAdmission = strictJsonInput(
    dependencySigningReviewAdmissionReceiptPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_ADMISSION_INVALID",
  );
  const fundedCanaryControllerBundle = regularInput(
    fundedCanaryControllerBundlePath,
    MAXIMUM_CANARY_BUNDLE_BYTES,
    "CANDIDATE_PACKAGE_FUNDED_CANARY_BUNDLE_INVALID",
  );
  const fundedCanaryControllerExtraction = strictJsonInput(
    fundedCanaryControllerExtractionReceiptPath,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_FUNDED_CANARY_EXTRACTION_INVALID",
  );
  distinctPhysicalFiles(
    [
      admission,
      candidatePi,
      candidatePiSignature,
      primarySigningVerification,
      reproducedSigningVerification,
      migrationControllerEnvelope,
      migrationControllerExtraction,
      tairaDeploymentManifest,
      tairaDeploymentOperatorSignature,
      tairaDeploymentReviewerSignature,
      tairaDeploymentOperatorPublicKey,
      tairaDeploymentReviewerPublicKey,
      tairaDeploymentAdmission,
      dependencySigningReviewManifest,
      dependencySigningReviewProducerSignature,
      dependencySigningReviewReviewerSignature,
      dependencySigningReviewProducerPublicKey,
      dependencySigningReviewReviewerPublicKey,
      dependencySigningReviewAdmission,
      fundedCanaryControllerBundle,
      fundedCanaryControllerExtraction,
    ],
    "CANDIDATE_PACKAGE_RECEIPTS_NOT_PHYSICALLY_DISTINCT",
  );
  if (
    !validateAndroidCandidateSigningVerificationV1(
      primarySigningVerification.value,
    ) ||
    !validateAndroidCandidateSigningVerificationV1(
      reproducedSigningVerification.value,
    )
  ) {
    fail("CANDIDATE_PACKAGE_SIGNING_VERIFICATION_SHAPE_INVALID");
  }
  let inspectedMigrationEnvelope;
  try {
    inspectedMigrationEnvelope = inspectAndroidMigrationControllerEnvelopeV1({
      value: migrationControllerEnvelope.value,
      expectedSourceRevision: sourceRevision,
      expectedCandidateAabSha256: primaryAab.sha256,
      expectedCandidateAabBytes: primaryAab.bytes,
    });
  } catch {
    fail("CANDIDATE_PACKAGE_MIGRATION_CONTROLLER_ENVELOPE_DIVERGED");
  }
  if (
    !migrationControllerExtractionReceiptValid(
      migrationControllerExtraction.value,
    ) ||
    migrationControllerExtraction.value.sourceRevision !== sourceRevision ||
    migrationControllerExtraction.value.candidateAabSha256 !== primaryAab.sha256 ||
    migrationControllerExtraction.value.candidateAabBytes !== primaryAab.bytes ||
    migrationControllerExtraction.value.envelopeSha256 !==
      migrationControllerEnvelope.sha256 ||
    migrationControllerExtraction.value.envelopeBytes !==
      migrationControllerEnvelope.byteCount ||
    Object.entries(inspectedMigrationEnvelope.records).some(
      ([name, record]) =>
        migrationControllerExtraction.value.files[name].sha256 !== record.sha256 ||
        migrationControllerExtraction.value.files[name].bytes !== record.bytes,
    )
  ) {
    fail("CANDIDATE_PACKAGE_MIGRATION_CONTROLLER_EXTRACTION_DIVERGED");
  }
  let verifiedTairaDeployment;
  try {
    verifiedTairaDeployment = verifyTairaDeploymentManifestV1({
      manifestPath: tairaDeploymentManifestPath,
      operatorSignaturePath: tairaDeploymentOperatorSignaturePath,
      reviewerSignaturePath: tairaDeploymentReviewerSignaturePath,
      operatorPublicKeyPath: tairaDeploymentOperatorPublicKeyPath,
      reviewerPublicKeyPath: tairaDeploymentReviewerPublicKeyPath,
      expectedOperatorKeySha256:
        expectedTairaDeploymentOperatorKeySha256,
      expectedReviewerKeySha256:
        expectedTairaDeploymentReviewerKeySha256,
      evaluationEpochSeconds:
        tairaDeploymentAdmission.value?.evaluationEpochSeconds,
    });
  } catch {
    fail("CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_VERIFICATION_FAILED");
  }
  if (
    JSON.stringify(verifiedTairaDeployment) !==
      JSON.stringify(tairaDeploymentAdmission.value) ||
    verifiedTairaDeployment.manifestSha256 !==
      tairaDeploymentManifest.sha256
  ) {
    fail("CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_ADMISSION_DIVERGED");
  }
  if (
    !fundedCanaryExtractionReceiptValid(
      fundedCanaryControllerExtraction.value,
    ) ||
    fundedCanaryControllerExtraction.value.tarSha256 !==
      fundedCanaryControllerBundle.sha256 ||
    fundedCanaryControllerExtraction.value.tarBytes !==
      fundedCanaryControllerBundle.bytes
  ) {
    fail("CANDIDATE_PACKAGE_FUNDED_CANARY_EXTRACTION_DIVERGED");
  }
  if (
    !isProductionAdmissionV3Envelope(admission.value) ||
    !hasExactKeys(admission.value, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "identity",
    ]) ||
    admission.value.status !== "qualified" ||
    admission.value.platform !== "android" ||
    !hasExactKeys(admission.value.identity, [
      ...PRODUCTION_ADMISSION_IDENTITY_KEYS,
      "bindingSha256",
    ])
  ) {
    fail("CANDIDATE_PACKAGE_ADMISSION_SHAPE_INVALID");
  }
  const identity = admission.value.identity;
  const expectedAdmissionBinding = canonicalSha256([
    ...productionAdmissionV3ProjectionPrefix(),
    ...PRODUCTION_ADMISSION_IDENTITY_KEYS.map(
      (key) => `${key}=${identity[key]}`,
    ),
  ]);
  if (
    identity.bindingSha256 !== expectedAdmissionBinding ||
    identity.candidateAabSha256 !== primaryAab.sha256 ||
    identity.candidateAabBytes !== primaryAab.bytes ||
    identity.sourceRevision !== sourceRevision ||
    identity.qualifiedAtEpochSeconds !== evaluatedAtEpochSeconds ||
    identity.candidatePiProbeReceiptSha256 !== candidatePi.sha256 ||
    identity.migrationQualificationSha256 !==
      inspectedMigrationEnvelope.records["android-migration-matrix.json"].sha256 ||
    identity.tairaDeploymentManifestSha256 !==
      verifiedTairaDeployment.manifestSha256
  ) {
    fail("CANDIDATE_PACKAGE_ADMISSION_IDENTITY_DIVERGED");
  }

  const dependencyConfigPath = join(
    root,
    "config/gradle-dependency-provenance.json",
  );
  const signingConfigPath = join(
    root,
    "config/android-production-signing-identity.json",
  );
  const irohaConfigPath = join(root, "config/iroha-mobile-sdk-pin.json");
  const workflowPath = join(root, QUALIFICATION_WORKFLOW_RELATIVE_PATH);
  const dependencyConfig = strictJsonInput(
    dependencyConfigPath,
    MAXIMUM_JSON_BYTES,
    "CANDIDATE_PACKAGE_DEPENDENCY_CONFIG_INVALID",
  );
  const signingConfig = strictJsonInput(
    signingConfigPath,
    MAXIMUM_JSON_BYTES,
    "CANDIDATE_PACKAGE_SIGNING_CONFIG_INVALID",
  );
  const irohaConfig = strictJsonInput(
    irohaConfigPath,
    MAXIMUM_JSON_BYTES,
    "CANDIDATE_PACKAGE_IROHA_CONFIG_INVALID",
  );
  const workflow = regularInput(
    workflowPath,
    MAXIMUM_JSON_BYTES,
    "CANDIDATE_PACKAGE_WORKFLOW_INVALID",
  );

  if (
    dependencyConfig.value?.schemaVersion !== 3 ||
    dependencyConfig.value?.platform !== "android" ||
    dependencyConfig.value?.status !== "qualified" ||
    dependencyConfig.value?.releaseEnabled !== true
  ) {
    fail("CANDIDATE_PACKAGE_DEPENDENCIES_NOT_QUALIFIED");
  }
  if (
    signingConfig.value?.schemaVersion !== 1 ||
    signingConfig.value?.platform !== "android" ||
    signingConfig.value?.applicationId !== "jp.co.soramitsu.sora" ||
    signingConfig.value?.status !== "qualified" ||
    signingConfig.value?.releaseEnabled !== true ||
    signingConfig.value?.debugFallbackAllowedForRelease !== false ||
    signingConfig.value?.productionUploadCertificateSha256 !==
      observedUploadCertificateSha256 ||
    !SHA256.test(
      signingConfig.value?.productionAppSigningCertificateSha256 ?? "",
    ) ||
    signingConfig.value?.retainedProductionCertificateMatched !== true ||
    signingConfig.value?.signedBundleCertificateMatched !== true ||
    signingConfig.value?.playAppSigningContinuityReviewed !== true ||
    signingConfig.value?.secretsRecordedInEvidence !== false
  ) {
    fail("CANDIDATE_PACKAGE_SIGNING_IDENTITY_NOT_QUALIFIED");
  }
  const signingReceiptMatches = (receipt, aab, apk) =>
    receipt.artifacts.aabSha256 === aab.sha256 &&
    receipt.artifacts.aabBytes === aab.bytes &&
    receipt.artifacts.apkSha256 === apk.sha256 &&
    receipt.artifacts.apkBytes === apk.bytes &&
    receipt.signingIdentity.configSha256 === signingConfig.sha256 &&
    receipt.signingIdentity.productionAppSigningCertificateSha256 ===
      signingConfig.value.productionAppSigningCertificateSha256 &&
    receipt.signingIdentity.expectedUploadCertificateSha256 ===
      signingConfig.value.productionUploadCertificateSha256 &&
    receipt.signingIdentity.observedAabUploadCertificateSha256 ===
      observedUploadCertificateSha256 &&
    receipt.signingIdentity.observedUploadCertificateSha256 ===
      observedUploadCertificateSha256;
  if (
    !signingReceiptMatches(
      primarySigningVerification.value,
      primaryAab,
      primaryApk,
    ) ||
    !signingReceiptMatches(
      reproducedSigningVerification.value,
      reproducedAab,
      reproducedApk,
    )
  ) {
    fail("CANDIDATE_PACKAGE_SIGNING_VERIFICATION_DIVERGED");
  }
  if (
    irohaConfig.value?.schemaVersion !== 4 ||
    irohaConfig.value?.platform !== "android" ||
    irohaConfig.value?.status !== "qualified" ||
    irohaConfig.value?.releaseEnabled !== true
  ) {
    fail("CANDIDATE_PACKAGE_IROHA_SDK_NOT_QUALIFIED");
  }
  if (
    identity.productionSigningIdentitySha256 !== signingConfig.sha256 ||
    identity.irohaMobileSdkPinSha256 !== irohaConfig.sha256 ||
    identity.qualificationWorkflowPath !==
      QUALIFICATION_WORKFLOW_RELATIVE_PATH ||
    identity.qualificationWorkflowSha256 !== workflow.sha256
  ) {
    fail("CANDIDATE_PACKAGE_SOURCE_EVIDENCE_DIVERGED");
  }

  const dependencyVerification = dependencyConfig.value.dependencyVerification;
  const lockInventory =
    dependencyConfig.value.dependencyLocking?.materializedInventory;
  const vendor =
    dependencyConfig.value.repositoryPolicy?.sourceQualifiedVendorRepository;
  if (
    dependencyVerification?.status !== "qualified" ||
    dependencyVerification?.independentlyReviewed !== true ||
    lockInventory === null ||
    typeof lockInventory !== "object" ||
    !Array.isArray(lockInventory.lockFilePaths) ||
    lockInventory.lockFilePaths.length !== 31 ||
    !SHA256.test(lockInventory.lockFileSetSha256 ?? "") ||
    dependencyConfig.value.dependencyLocking?.independentlyReviewed !== true ||
    vendor?.status !== "qualified" ||
    vendor?.productionAllowed !== true
  ) {
    fail("CANDIDATE_PACKAGE_DEPENDENCY_INVENTORY_NOT_QUALIFIED");
  }
  const verificationMetadata = evidenceFile(
    root,
    dependencyVerification.metadataPath,
    dependencyVerification.metadataSha256,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_VERIFICATION_METADATA_DIVERGED",
  );
  const verificationMetadataDigest = evidenceFile(
    root,
    dependencyVerification.metadataDigestPath,
    dependencyVerification.metadataDigestSha256,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_VERIFICATION_DIGEST_DIVERGED",
  );
  const vendorSourceProvenance = evidenceFile(
    root,
    vendor.sourceProvenancePath,
    vendor.sourceProvenanceSha256,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_VENDOR_PROVENANCE_DIVERGED",
  );
  const vendorContentsManifest = evidenceFile(
    root,
    vendor.contentsManifestPath,
    vendor.contentsManifestSha256,
    MAXIMUM_EVIDENCE_BYTES,
    "CANDIDATE_PACKAGE_VENDOR_MANIFEST_DIVERGED",
  );
  const dependencySigningReviewedInputs = {
    gradleDependencyProvenanceSha256: dependencyConfig.sha256,
    verificationMetadataSha256: verificationMetadata.sha256,
    verificationMetadataDigestSha256: verificationMetadataDigest.sha256,
    vendorSourceProvenanceSha256: vendorSourceProvenance.sha256,
    vendorContentsManifestSha256: vendorContentsManifest.sha256,
    lockFileSetSha256: lockInventory.lockFileSetSha256,
    lockConfigurationInventorySha256:
      lockInventory.configurationInventorySha256,
    androidProductionSigningIdentitySha256: signingConfig.sha256,
    productionAppSigningCertificateSha256:
      signingConfig.value.productionAppSigningCertificateSha256,
    productionUploadCertificateSha256:
      signingConfig.value.productionUploadCertificateSha256,
  };
  let verifiedDependencySigningReview;
  try {
    if (
      androidDependencySigningReviewContractSha256V1({
        sourceRevision,
        reviewedInputs: dependencySigningReviewedInputs,
      }) !== expectedDependencySigningReviewContractSha256
    ) {
      fail("CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_CONTRACT_DIVERGED");
    }
    verifiedDependencySigningReview = verifyAndroidDependencySigningReviewV1({
      manifestPath: dependencySigningReviewManifestPath,
      producerSignaturePath: dependencySigningReviewProducerSignaturePath,
      reviewerSignaturePath: dependencySigningReviewReviewerSignaturePath,
      producerPublicKeyPath: dependencySigningReviewProducerPublicKeyPath,
      reviewerPublicKeyPath: dependencySigningReviewReviewerPublicKeyPath,
      expectedProducerKeySha256:
        expectedDependencySigningReviewProducerKeySha256,
      expectedReviewerKeySha256:
        expectedDependencySigningReviewReviewerKeySha256,
      expectedReviewSequenceNumber:
        expectedDependencySigningReviewSequenceNumber,
      expectedSourceRevision: sourceRevision,
      expectedReviewContractSha256:
        expectedDependencySigningReviewContractSha256,
      expectedReviewedInputs: dependencySigningReviewedInputs,
      evaluationEpochSeconds:
        dependencySigningReviewAdmission.value?.evaluationEpochSeconds,
    });
  } catch (error) {
    if (
      error?.message ===
      "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_CONTRACT_DIVERGED"
    ) {
      throw error;
    }
    fail("CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_VERIFICATION_FAILED");
  }
  if (
    JSON.stringify(verifiedDependencySigningReview) !==
      JSON.stringify(dependencySigningReviewAdmission.value) ||
    verifiedDependencySigningReview.manifestSha256 !==
      dependencySigningReviewManifest.sha256 ||
    identity.dependencySigningReviewManifestSha256 !==
      verifiedDependencySigningReview.manifestSha256
  ) {
    fail("CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_ADMISSION_DIVERGED");
  }

  const payload = {
    schemaVersion: ANDROID_QUALIFIED_CANDIDATE_PACKAGE_V1.schemaVersion,
    contractId: ANDROID_QUALIFIED_CANDIDATE_PACKAGE_V1.contractId,
    status: "sealed-candidate",
    platform: "android",
    sourceRevision,
    evaluatedAtEpochSeconds,
    reproducibility: {
      buildCount: 2,
      aabByteForByteEqual: true,
      apkByteForByteEqual: true,
      r8MappingByteForByteEqual: true,
      primary: {
        aab: artifactProjection("candidate.aab", primaryAab),
        apk: artifactProjection("candidate.apk", primaryApk),
        r8Mapping: artifactProjection("r8-mapping.txt", primaryMapping),
        buildLog: artifactProjection("primary-build.log", primaryBuildLog),
      },
      independentRebuild: {
        aab: artifactProjection("reproduced-candidate.aab", reproducedAab),
        apk: artifactProjection("reproduced-candidate.apk", reproducedApk),
        r8Mapping: artifactProjection(
          "reproduced-r8-mapping.txt",
          reproducedMapping,
        ),
        buildLog: artifactProjection(
          "reproduction-build.log",
          reproducedBuildLog,
        ),
      },
    },
    evidence: {
      productionAdmission: artifactProjection(
        "production-admission.json",
        { sha256: admission.sha256, bytes: admission.byteCount },
      ),
      candidatePiReceipt: artifactProjection(
        "candidate-pi-receipt.json",
        { sha256: candidatePi.sha256, bytes: candidatePi.byteCount },
      ),
      candidatePiReceiptSignature: artifactProjection(
        "candidate-pi-receipt-signature.json",
        {
          sha256: candidatePiSignature.sha256,
          bytes: candidatePiSignature.byteCount,
        },
      ),
      primarySigningVerification: artifactProjection(
        "primary-signing-verification.json",
        {
          sha256: primarySigningVerification.sha256,
          bytes: primarySigningVerification.byteCount,
        },
      ),
      reproducedSigningVerification: artifactProjection(
        "reproduction-signing-verification.json",
        {
          sha256: reproducedSigningVerification.sha256,
          bytes: reproducedSigningVerification.byteCount,
        },
      ),
      migrationControllerEnvelope: artifactProjection(
        "android-migration-controller-envelope-v1.json",
        {
          sha256: migrationControllerEnvelope.sha256,
          bytes: migrationControllerEnvelope.byteCount,
        },
      ),
      migrationControllerExtraction: artifactProjection(
        "android-migration-controller-extraction-v1.json",
        {
          sha256: migrationControllerExtraction.sha256,
          bytes: migrationControllerExtraction.byteCount,
        },
      ),
      tairaDeploymentManifest: artifactProjection(
        "taira-deployment-manifest.json",
        {
          sha256: tairaDeploymentManifest.sha256,
          bytes: tairaDeploymentManifest.byteCount,
        },
      ),
      tairaDeploymentOperatorSignature: artifactProjection(
        "taira-deployment-operator.sig",
        tairaDeploymentOperatorSignature,
      ),
      tairaDeploymentReviewerSignature: artifactProjection(
        "taira-deployment-reviewer.sig",
        tairaDeploymentReviewerSignature,
      ),
      tairaDeploymentOperatorPublicKey: artifactProjection(
        "taira-deployment-operator.pem",
        tairaDeploymentOperatorPublicKey,
      ),
      tairaDeploymentReviewerPublicKey: artifactProjection(
        "taira-deployment-reviewer.pem",
        tairaDeploymentReviewerPublicKey,
      ),
      tairaDeploymentAdmission: artifactProjection(
        "taira-deployment-admission.json",
        {
          sha256: tairaDeploymentAdmission.sha256,
          bytes: tairaDeploymentAdmission.byteCount,
        },
      ),
      dependencySigningReviewManifest: artifactProjection(
        "android-dependency-signing-review-manifest.json",
        {
          sha256: dependencySigningReviewManifest.sha256,
          bytes: dependencySigningReviewManifest.byteCount,
        },
      ),
      dependencySigningReviewProducerSignature: artifactProjection(
        "android-dependency-signing-review-producer.sig",
        dependencySigningReviewProducerSignature,
      ),
      dependencySigningReviewReviewerSignature: artifactProjection(
        "android-dependency-signing-review-reviewer.sig",
        dependencySigningReviewReviewerSignature,
      ),
      dependencySigningReviewProducerPublicKey: artifactProjection(
        "android-dependency-signing-review-producer.pem",
        dependencySigningReviewProducerPublicKey,
      ),
      dependencySigningReviewReviewerPublicKey: artifactProjection(
        "android-dependency-signing-review-reviewer.pem",
        dependencySigningReviewReviewerPublicKey,
      ),
      dependencySigningReviewAdmission: artifactProjection(
        "android-dependency-signing-review-admission.json",
        {
          sha256: dependencySigningReviewAdmission.sha256,
          bytes: dependencySigningReviewAdmission.byteCount,
        },
      ),
      fundedCanaryControllerBundle: artifactProjection(
        "funded-canary-controller-bundle-v1.tar",
        fundedCanaryControllerBundle,
      ),
      fundedCanaryControllerExtraction: artifactProjection(
        "funded-canary-controller-extraction-v1.json",
        {
          sha256: fundedCanaryControllerExtraction.sha256,
          bytes: fundedCanaryControllerExtraction.byteCount,
        },
      ),
      qualificationWorkflow: {
        path: QUALIFICATION_WORKFLOW_RELATIVE_PATH,
        sha256: workflow.sha256,
        bytes: workflow.bytes,
      },
      gradleDependencyProvenance: {
        sha256: dependencyConfig.sha256,
        bytes: dependencyConfig.byteCount,
        schemaVersion: dependencyConfig.value.schemaVersion,
        status: dependencyConfig.value.status,
      },
      verificationMetadata,
      verificationMetadataDigest,
      lockInventory: {
        fileCount: lockInventory.lockFilePaths.length,
        lockFileSetSha256: lockInventory.lockFileSetSha256,
      },
      vendorSourceProvenance,
      vendorContentsManifest,
      signingIdentity: {
        configSha256: signingConfig.sha256,
        productionAppSigningCertificateSha256:
          signingConfig.value.productionAppSigningCertificateSha256,
        productionUploadCertificateSha256:
          signingConfig.value.productionUploadCertificateSha256,
        observedAabUploadCertificateSha256:
          observedUploadCertificateSha256,
        observedUploadCertificateSha256,
      },
      irohaMobileSdkPin: {
        sha256: irohaConfig.sha256,
        bytes: irohaConfig.byteCount,
        schemaVersion: irohaConfig.value.schemaVersion,
        status: irohaConfig.value.status,
      },
    },
    privacy: {
      aggregateOnly: true,
      secretsIncluded: false,
      privateKeysIncluded: false,
      keyPasswordsIncluded: false,
      rawWalletPayloadsIncluded: false,
    },
    authorization: {
      authorizesRelease: false,
      authorizesProductionMutation: false,
      authority: "production-admission.json",
    },
  };
  return {
    ...payload,
    packageBindingSha256: canonicalJsonSha256(payload),
  };
};

export const validateAndroidQualifiedCandidatePackageV1 = (value) => {
  if (
    !hasExactKeys(value, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "sourceRevision",
      "evaluatedAtEpochSeconds",
      "reproducibility",
      "evidence",
      "privacy",
      "authorization",
      "packageBindingSha256",
    ]) ||
    value.schemaVersion !== ANDROID_QUALIFIED_CANDIDATE_PACKAGE_V1.schemaVersion ||
    value.contractId !== ANDROID_QUALIFIED_CANDIDATE_PACKAGE_V1.contractId ||
    value.status !== "sealed-candidate" ||
    value.platform !== "android" ||
    !SHA256.test(value.packageBindingSha256 ?? "")
  ) {
    return false;
  }
  const { packageBindingSha256, ...payload } = value;
  return (
    REVISION.test(value.sourceRevision ?? "") &&
    !/^0+$/.test(value.sourceRevision) &&
    Number.isSafeInteger(value.evaluatedAtEpochSeconds) &&
    value.evaluatedAtEpochSeconds > 0 &&
    hasExactKeys(value.reproducibility, [
      "buildCount",
      "aabByteForByteEqual",
      "apkByteForByteEqual",
      "r8MappingByteForByteEqual",
      "primary",
      "independentRebuild",
    ]) &&
    value.reproducibility?.buildCount === 2 &&
    value.reproducibility?.aabByteForByteEqual === true &&
    value.reproducibility?.apkByteForByteEqual === true &&
    value.reproducibility?.r8MappingByteForByteEqual === true &&
    hasExactKeys(value.reproducibility.primary, [
      "aab",
      "apk",
      "r8Mapping",
      "buildLog",
    ]) &&
    artifactProjectionValid(
      value.reproducibility.primary.aab,
      "candidate.aab",
    ) &&
    artifactProjectionValid(
      value.reproducibility.primary.apk,
      "candidate.apk",
    ) &&
    artifactProjectionValid(
      value.reproducibility.primary.r8Mapping,
      "r8-mapping.txt",
    ) &&
    artifactProjectionValid(
      value.reproducibility.primary.buildLog,
      "primary-build.log",
    ) &&
    hasExactKeys(value.reproducibility.independentRebuild, [
      "aab",
      "apk",
      "r8Mapping",
      "buildLog",
    ]) &&
    artifactProjectionValid(
      value.reproducibility.independentRebuild.aab,
      "reproduced-candidate.aab",
    ) &&
    artifactProjectionValid(
      value.reproducibility.independentRebuild.apk,
      "reproduced-candidate.apk",
    ) &&
    artifactProjectionValid(
      value.reproducibility.independentRebuild.r8Mapping,
      "reproduced-r8-mapping.txt",
    ) &&
    artifactProjectionValid(
      value.reproducibility.independentRebuild.buildLog,
      "reproduction-build.log",
    ) &&
    hasExactKeys(value.evidence, [
      "productionAdmission",
      "candidatePiReceipt",
      "candidatePiReceiptSignature",
      "primarySigningVerification",
      "reproducedSigningVerification",
      "migrationControllerEnvelope",
      "migrationControllerExtraction",
      "tairaDeploymentManifest",
      "tairaDeploymentOperatorSignature",
      "tairaDeploymentReviewerSignature",
      "tairaDeploymentOperatorPublicKey",
      "tairaDeploymentReviewerPublicKey",
      "tairaDeploymentAdmission",
      "dependencySigningReviewManifest",
      "dependencySigningReviewProducerSignature",
      "dependencySigningReviewReviewerSignature",
      "dependencySigningReviewProducerPublicKey",
      "dependencySigningReviewReviewerPublicKey",
      "dependencySigningReviewAdmission",
      "fundedCanaryControllerBundle",
      "fundedCanaryControllerExtraction",
      "qualificationWorkflow",
      "gradleDependencyProvenance",
      "verificationMetadata",
      "verificationMetadataDigest",
      "lockInventory",
      "vendorSourceProvenance",
      "vendorContentsManifest",
      "signingIdentity",
      "irohaMobileSdkPin",
    ]) &&
    artifactProjectionValid(
      value.evidence.productionAdmission,
      "production-admission.json",
    ) &&
    artifactProjectionValid(
      value.evidence.candidatePiReceipt,
      "candidate-pi-receipt.json",
    ) &&
    artifactProjectionValid(
      value.evidence.candidatePiReceiptSignature,
      "candidate-pi-receipt-signature.json",
    ) &&
    artifactProjectionValid(
      value.evidence.primarySigningVerification,
      "primary-signing-verification.json",
    ) &&
    artifactProjectionValid(
      value.evidence.reproducedSigningVerification,
      "reproduction-signing-verification.json",
    ) &&
    artifactProjectionValid(
      value.evidence.migrationControllerEnvelope,
      "android-migration-controller-envelope-v1.json",
    ) &&
    artifactProjectionValid(
      value.evidence.migrationControllerExtraction,
      "android-migration-controller-extraction-v1.json",
    ) &&
    artifactProjectionValid(
      value.evidence.tairaDeploymentManifest,
      "taira-deployment-manifest.json",
    ) &&
    artifactProjectionValid(
      value.evidence.tairaDeploymentOperatorSignature,
      "taira-deployment-operator.sig",
    ) &&
    artifactProjectionValid(
      value.evidence.tairaDeploymentReviewerSignature,
      "taira-deployment-reviewer.sig",
    ) &&
    artifactProjectionValid(
      value.evidence.tairaDeploymentOperatorPublicKey,
      "taira-deployment-operator.pem",
    ) &&
    artifactProjectionValid(
      value.evidence.tairaDeploymentReviewerPublicKey,
      "taira-deployment-reviewer.pem",
    ) &&
    artifactProjectionValid(
      value.evidence.tairaDeploymentAdmission,
      "taira-deployment-admission.json",
    ) &&
    artifactProjectionValid(
      value.evidence.dependencySigningReviewManifest,
      "android-dependency-signing-review-manifest.json",
    ) &&
    artifactProjectionValid(
      value.evidence.dependencySigningReviewProducerSignature,
      "android-dependency-signing-review-producer.sig",
    ) &&
    artifactProjectionValid(
      value.evidence.dependencySigningReviewReviewerSignature,
      "android-dependency-signing-review-reviewer.sig",
    ) &&
    artifactProjectionValid(
      value.evidence.dependencySigningReviewProducerPublicKey,
      "android-dependency-signing-review-producer.pem",
    ) &&
    artifactProjectionValid(
      value.evidence.dependencySigningReviewReviewerPublicKey,
      "android-dependency-signing-review-reviewer.pem",
    ) &&
    artifactProjectionValid(
      value.evidence.dependencySigningReviewAdmission,
      "android-dependency-signing-review-admission.json",
    ) &&
    artifactProjectionValid(
      value.evidence.fundedCanaryControllerBundle,
      "funded-canary-controller-bundle-v1.tar",
    ) &&
    artifactProjectionValid(
      value.evidence.fundedCanaryControllerExtraction,
      "funded-canary-controller-extraction-v1.json",
    ) &&
    hasExactKeys(value.evidence.qualificationWorkflow, [
      "path",
      "sha256",
      "bytes",
    ]) &&
    value.evidence.qualificationWorkflow.path ===
      QUALIFICATION_WORKFLOW_RELATIVE_PATH &&
    SHA256.test(value.evidence.qualificationWorkflow.sha256 ?? "") &&
    Number.isSafeInteger(value.evidence.qualificationWorkflow.bytes) &&
    value.evidence.qualificationWorkflow.bytes > 0 &&
    hasExactKeys(value.evidence.gradleDependencyProvenance, [
      "sha256",
      "bytes",
      "schemaVersion",
      "status",
    ]) &&
    SHA256.test(value.evidence.gradleDependencyProvenance.sha256 ?? "") &&
    Number.isSafeInteger(value.evidence.gradleDependencyProvenance.bytes) &&
    value.evidence.gradleDependencyProvenance.bytes > 0 &&
    value.evidence.gradleDependencyProvenance.schemaVersion === 3 &&
    value.evidence.gradleDependencyProvenance.status === "qualified" &&
    ["verificationMetadata", "verificationMetadataDigest", "vendorSourceProvenance", "vendorContentsManifest"].every(
      (key) =>
        hasExactKeys(value.evidence[key], ["sha256", "bytes"]) &&
        SHA256.test(value.evidence[key].sha256 ?? "") &&
        Number.isSafeInteger(value.evidence[key].bytes) &&
        value.evidence[key].bytes > 0,
    ) &&
    hasExactKeys(value.evidence.lockInventory, [
      "fileCount",
      "lockFileSetSha256",
    ]) &&
    value.evidence.lockInventory.fileCount === 31 &&
    SHA256.test(value.evidence.lockInventory.lockFileSetSha256 ?? "") &&
    hasExactKeys(value.evidence.signingIdentity, [
      "configSha256",
      "productionAppSigningCertificateSha256",
      "productionUploadCertificateSha256",
      "observedAabUploadCertificateSha256",
      "observedUploadCertificateSha256",
    ]) &&
    Object.values(value.evidence.signingIdentity).every((fieldValue) =>
      SHA256.test(fieldValue),
    ) &&
    value.evidence.signingIdentity.productionUploadCertificateSha256 ===
      value.evidence.signingIdentity.observedAabUploadCertificateSha256 &&
    value.evidence.signingIdentity.productionUploadCertificateSha256 ===
      value.evidence.signingIdentity.observedUploadCertificateSha256 &&
    hasExactKeys(value.evidence.irohaMobileSdkPin, [
      "sha256",
      "bytes",
      "schemaVersion",
      "status",
    ]) &&
    SHA256.test(value.evidence.irohaMobileSdkPin.sha256 ?? "") &&
    Number.isSafeInteger(value.evidence.irohaMobileSdkPin.bytes) &&
    value.evidence.irohaMobileSdkPin.bytes > 0 &&
    value.evidence.irohaMobileSdkPin.schemaVersion === 4 &&
    value.evidence.irohaMobileSdkPin.status === "qualified" &&
    hasExactKeys(value.privacy, [
      "aggregateOnly",
      "secretsIncluded",
      "privateKeysIncluded",
      "keyPasswordsIncluded",
      "rawWalletPayloadsIncluded",
    ]) &&
    value.authorization?.authorizesRelease === false &&
    value.authorization?.authorizesProductionMutation === false &&
    value.authorization?.authority === "production-admission.json" &&
    hasExactKeys(value.authorization, [
      "authorizesRelease",
      "authorizesProductionMutation",
      "authority",
    ]) &&
    value.privacy?.aggregateOnly === true &&
    Object.entries(value.privacy).every(
      ([key, fieldValue]) => key === "aggregateOnly" || fieldValue === false,
    ) &&
    packageBindingSha256 === canonicalJsonSha256(payload)
  );
};

const DOWNLOADED_PACKAGE_FILES = [
  "android-dependency-signing-review-admission.json",
  "android-dependency-signing-review-manifest.json",
  "android-dependency-signing-review-producer.pem",
  "android-dependency-signing-review-producer.sig",
  "android-dependency-signing-review-reviewer.pem",
  "android-dependency-signing-review-reviewer.sig",
  "android-migration-controller-envelope-v1.json",
  "android-migration-controller-extraction-v1.json",
  "candidate-package-manifest.json",
  "candidate-pi-receipt.json",
  "candidate-pi-receipt-signature.json",
  "candidate.aab",
  "candidate.apk",
  "funded-canary-controller-bundle-v1.tar",
  "funded-canary-controller-extraction-v1.json",
  "primary-build.log",
  "primary-signing-verification.json",
  "production-admission.json",
  "r8-mapping.txt",
  "reproduced-candidate.aab",
  "reproduced-candidate.apk",
  "reproduced-r8-mapping.txt",
  "reproduction-build.log",
  "reproduction-signing-verification.json",
  "source-revision.txt",
  "taira-deployment-admission.json",
  "taira-deployment-manifest.json",
  "taira-deployment-operator.pem",
  "taira-deployment-operator.sig",
  "taira-deployment-reviewer.pem",
  "taira-deployment-reviewer.sig",
].sort();

const projectionMatchesRecord = (projection, record) =>
  projection.sha256 === record.sha256 && projection.bytes === record.bytes;

export const validateDownloadedAndroidQualifiedCandidatePackageV1 = ({
  root,
  packageRoot,
  expectedSourceRevision,
  expectedTairaDeploymentOperatorKeySha256,
  expectedTairaDeploymentReviewerKeySha256,
  expectedDependencySigningReviewProducerKeySha256,
  expectedDependencySigningReviewReviewerKeySha256,
  expectedDependencySigningReviewSequenceNumber,
  expectedDependencySigningReviewContractSha256,
}) => {
  if (!canonicalAbsoluteRoot(root) || !canonicalAbsoluteRoot(packageRoot)) {
    fail("DOWNLOADED_CANDIDATE_PACKAGE_ROOT_INVALID");
  }
  if (
    !REVISION.test(expectedSourceRevision ?? "") ||
    /^0+$/.test(expectedSourceRevision)
  ) {
    fail("DOWNLOADED_CANDIDATE_SOURCE_REVISION_INVALID");
  }
  let inventory;
  try {
    inventory = readdirSync(packageRoot).sort();
  } catch {
    fail("DOWNLOADED_CANDIDATE_PACKAGE_INVENTORY_INVALID");
  }
  if (inventory.join("\0") !== DOWNLOADED_PACKAGE_FILES.join("\0")) {
    fail("DOWNLOADED_CANDIDATE_PACKAGE_INVENTORY_INVALID");
  }

  const path = (name) => join(packageRoot, name);
  const manifestRecord = strictJsonInput(
    path("candidate-package-manifest.json"),
    MAXIMUM_JSON_BYTES,
    "DOWNLOADED_CANDIDATE_MANIFEST_INVALID",
  );
  const manifest = manifestRecord.value;
  if (
    !validateAndroidQualifiedCandidatePackageV1(manifest) ||
    manifest.sourceRevision !== expectedSourceRevision
  ) {
    fail("DOWNLOADED_CANDIDATE_MANIFEST_INVALID");
  }

  const files = {
    candidateAab: regularInput(
      path("candidate.aab"),
      MAXIMUM_AAB_BYTES,
      "DOWNLOADED_CANDIDATE_PRIMARY_AAB_INVALID",
    ),
    candidateApk: regularInput(
      path("candidate.apk"),
      MAXIMUM_APK_BYTES,
      "DOWNLOADED_CANDIDATE_PRIMARY_APK_INVALID",
    ),
    primaryMapping: regularInput(
      path("r8-mapping.txt"),
      MAXIMUM_MAPPING_BYTES,
      "DOWNLOADED_CANDIDATE_PRIMARY_MAPPING_INVALID",
    ),
    primaryLog: regularInput(
      path("primary-build.log"),
      MAXIMUM_BUILD_LOG_BYTES,
      "DOWNLOADED_CANDIDATE_PRIMARY_LOG_INVALID",
    ),
    reproducedAab: regularInput(
      path("reproduced-candidate.aab"),
      MAXIMUM_AAB_BYTES,
      "DOWNLOADED_CANDIDATE_REPRODUCED_AAB_INVALID",
    ),
    reproducedApk: regularInput(
      path("reproduced-candidate.apk"),
      MAXIMUM_APK_BYTES,
      "DOWNLOADED_CANDIDATE_REPRODUCED_APK_INVALID",
    ),
    reproducedMapping: regularInput(
      path("reproduced-r8-mapping.txt"),
      MAXIMUM_MAPPING_BYTES,
      "DOWNLOADED_CANDIDATE_REPRODUCED_MAPPING_INVALID",
    ),
    reproducedLog: regularInput(
      path("reproduction-build.log"),
      MAXIMUM_BUILD_LOG_BYTES,
      "DOWNLOADED_CANDIDATE_REPRODUCED_LOG_INVALID",
    ),
  };
  const admission = strictJsonInput(
    path("production-admission.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_ADMISSION_INVALID",
  );
  const candidatePi = strictJsonInput(
    path("candidate-pi-receipt.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_PI_RECEIPT_INVALID",
  );
  const candidatePiSignature = strictJsonInput(
    path("candidate-pi-receipt-signature.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_PI_SIGNATURE_INVALID",
  );
  const primarySigning = strictJsonInput(
    path("primary-signing-verification.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_PRIMARY_SIGNING_INVALID",
  );
  const reproducedSigning = strictJsonInput(
    path("reproduction-signing-verification.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_REPRODUCED_SIGNING_INVALID",
  );
  const migrationControllerEnvelope = strictJsonInput(
    path("android-migration-controller-envelope-v1.json"),
    ANDROID_MIGRATION_CONTROLLER_MAXIMUM_BYTES,
    "DOWNLOADED_CANDIDATE_MIGRATION_CONTROLLER_ENVELOPE_INVALID",
  );
  const migrationControllerExtraction = strictJsonInput(
    path("android-migration-controller-extraction-v1.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_MIGRATION_CONTROLLER_EXTRACTION_INVALID",
  );
  const tairaDeploymentManifest = strictJsonInput(
    path("taira-deployment-manifest.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_TAIRA_DEPLOYMENT_MANIFEST_INVALID",
  );
  const tairaDeploymentOperatorSignature = regularInput(
    path("taira-deployment-operator.sig"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_INVALID",
  );
  const tairaDeploymentReviewerSignature = regularInput(
    path("taira-deployment-reviewer.sig"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_INVALID",
  );
  const tairaDeploymentOperatorPublicKey = regularInput(
    path("taira-deployment-operator.pem"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_TAIRA_DEPLOYMENT_OPERATOR_KEY_INVALID",
  );
  const tairaDeploymentReviewerPublicKey = regularInput(
    path("taira-deployment-reviewer.pem"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_TAIRA_DEPLOYMENT_REVIEWER_KEY_INVALID",
  );
  const tairaDeploymentAdmission = strictJsonInput(
    path("taira-deployment-admission.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_TAIRA_DEPLOYMENT_ADMISSION_INVALID",
  );
  const dependencySigningReviewManifest = strictJsonInput(
    path("android-dependency-signing-review-manifest.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_MANIFEST_INVALID",
  );
  const dependencySigningReviewProducerSignature = regularInput(
    path("android-dependency-signing-review-producer.sig"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_PRODUCER_SIGNATURE_INVALID",
  );
  const dependencySigningReviewReviewerSignature = regularInput(
    path("android-dependency-signing-review-reviewer.sig"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_REVIEWER_SIGNATURE_INVALID",
  );
  const dependencySigningReviewProducerPublicKey = regularInput(
    path("android-dependency-signing-review-producer.pem"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_PRODUCER_KEY_INVALID",
  );
  const dependencySigningReviewReviewerPublicKey = regularInput(
    path("android-dependency-signing-review-reviewer.pem"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_REVIEWER_KEY_INVALID",
  );
  const dependencySigningReviewAdmission = strictJsonInput(
    path("android-dependency-signing-review-admission.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_ADMISSION_INVALID",
  );
  const fundedCanaryControllerBundle = regularInput(
    path("funded-canary-controller-bundle-v1.tar"),
    MAXIMUM_CANARY_BUNDLE_BYTES,
    "DOWNLOADED_CANDIDATE_FUNDED_CANARY_BUNDLE_INVALID",
  );
  const fundedCanaryControllerExtraction = strictJsonInput(
    path("funded-canary-controller-extraction-v1.json"),
    MAXIMUM_EVIDENCE_BYTES,
    "DOWNLOADED_CANDIDATE_FUNDED_CANARY_EXTRACTION_INVALID",
  );
  const sourceRevisionRecord = regularInput(
    path("source-revision.txt"),
    128,
    "DOWNLOADED_CANDIDATE_SOURCE_REVISION_FILE_INVALID",
  );
  let sourceRevisionBytes;
  try {
    sourceRevisionBytes = readFileSync(path("source-revision.txt"));
  } catch {
    fail("DOWNLOADED_CANDIDATE_SOURCE_REVISION_FILE_INVALID");
  }
  const sourceRevisionAfter = regularInput(
    path("source-revision.txt"),
    128,
    "DOWNLOADED_CANDIDATE_SOURCE_REVISION_FILE_INVALID",
  );
  if (
    sourceRevisionRecord.sha256 !== sourceRevisionAfter.sha256 ||
    sourceRevisionRecord.bytes !== sourceRevisionAfter.bytes ||
    sourceRevisionBytes.toString("ascii") !== `${expectedSourceRevision}\n`
  ) {
    fail("DOWNLOADED_CANDIDATE_SOURCE_REVISION_FILE_INVALID");
  }
  distinctPhysicalFiles(
    [
      manifestRecord,
      ...Object.values(files),
      admission,
      candidatePi,
      candidatePiSignature,
      primarySigning,
      reproducedSigning,
      migrationControllerEnvelope,
      migrationControllerExtraction,
      tairaDeploymentManifest,
      tairaDeploymentOperatorSignature,
      tairaDeploymentReviewerSignature,
      tairaDeploymentOperatorPublicKey,
      tairaDeploymentReviewerPublicKey,
      tairaDeploymentAdmission,
      dependencySigningReviewManifest,
      dependencySigningReviewProducerSignature,
      dependencySigningReviewReviewerSignature,
      dependencySigningReviewProducerPublicKey,
      dependencySigningReviewReviewerPublicKey,
      dependencySigningReviewAdmission,
      fundedCanaryControllerBundle,
      fundedCanaryControllerExtraction,
      sourceRevisionRecord,
    ],
    "DOWNLOADED_CANDIDATE_PACKAGE_FILES_NOT_DISTINCT",
  );

  const primary = manifest.reproducibility.primary;
  const reproduced = manifest.reproducibility.independentRebuild;
  if (
    !projectionMatchesRecord(primary.aab, files.candidateAab) ||
    !projectionMatchesRecord(primary.apk, files.candidateApk) ||
    !projectionMatchesRecord(primary.r8Mapping, files.primaryMapping) ||
    !projectionMatchesRecord(primary.buildLog, files.primaryLog) ||
    !projectionMatchesRecord(reproduced.aab, files.reproducedAab) ||
    !projectionMatchesRecord(reproduced.apk, files.reproducedApk) ||
    !projectionMatchesRecord(reproduced.r8Mapping, files.reproducedMapping) ||
    !projectionMatchesRecord(reproduced.buildLog, files.reproducedLog) ||
    files.candidateAab.sha256 !== files.reproducedAab.sha256 ||
    files.candidateAab.bytes !== files.reproducedAab.bytes ||
    files.candidateApk.sha256 !== files.reproducedApk.sha256 ||
    files.candidateApk.bytes !== files.reproducedApk.bytes ||
    files.primaryMapping.sha256 !== files.reproducedMapping.sha256 ||
    files.primaryMapping.bytes !== files.reproducedMapping.bytes
  ) {
    fail("DOWNLOADED_CANDIDATE_REPRODUCIBILITY_DIVERGED");
  }
  if (
    !projectionMatchesRecord(manifest.evidence.productionAdmission, {
      sha256: admission.sha256,
      bytes: admission.byteCount,
    }) ||
    !projectionMatchesRecord(manifest.evidence.candidatePiReceipt, {
      sha256: candidatePi.sha256,
      bytes: candidatePi.byteCount,
    }) ||
    !projectionMatchesRecord(manifest.evidence.candidatePiReceiptSignature, {
      sha256: candidatePiSignature.sha256,
      bytes: candidatePiSignature.byteCount,
    }) ||
    !projectionMatchesRecord(manifest.evidence.primarySigningVerification, {
      sha256: primarySigning.sha256,
      bytes: primarySigning.byteCount,
    }) ||
    !projectionMatchesRecord(
      manifest.evidence.reproducedSigningVerification,
      {
        sha256: reproducedSigning.sha256,
        bytes: reproducedSigning.byteCount,
      },
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.migrationControllerEnvelope,
      {
        sha256: migrationControllerEnvelope.sha256,
        bytes: migrationControllerEnvelope.byteCount,
      },
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.migrationControllerExtraction,
      {
        sha256: migrationControllerExtraction.sha256,
        bytes: migrationControllerExtraction.byteCount,
      },
    ) ||
    !projectionMatchesRecord(manifest.evidence.tairaDeploymentManifest, {
      sha256: tairaDeploymentManifest.sha256,
      bytes: tairaDeploymentManifest.byteCount,
    }) ||
    !projectionMatchesRecord(
      manifest.evidence.tairaDeploymentOperatorSignature,
      tairaDeploymentOperatorSignature,
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.tairaDeploymentReviewerSignature,
      tairaDeploymentReviewerSignature,
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.tairaDeploymentOperatorPublicKey,
      tairaDeploymentOperatorPublicKey,
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.tairaDeploymentReviewerPublicKey,
      tairaDeploymentReviewerPublicKey,
    ) ||
    !projectionMatchesRecord(manifest.evidence.tairaDeploymentAdmission, {
      sha256: tairaDeploymentAdmission.sha256,
      bytes: tairaDeploymentAdmission.byteCount,
    }) ||
    !projectionMatchesRecord(
      manifest.evidence.dependencySigningReviewManifest,
      {
        sha256: dependencySigningReviewManifest.sha256,
        bytes: dependencySigningReviewManifest.byteCount,
      },
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.dependencySigningReviewProducerSignature,
      dependencySigningReviewProducerSignature,
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.dependencySigningReviewReviewerSignature,
      dependencySigningReviewReviewerSignature,
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.dependencySigningReviewProducerPublicKey,
      dependencySigningReviewProducerPublicKey,
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.dependencySigningReviewReviewerPublicKey,
      dependencySigningReviewReviewerPublicKey,
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.dependencySigningReviewAdmission,
      {
        sha256: dependencySigningReviewAdmission.sha256,
        bytes: dependencySigningReviewAdmission.byteCount,
      },
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.fundedCanaryControllerBundle,
      fundedCanaryControllerBundle,
    ) ||
    !projectionMatchesRecord(
      manifest.evidence.fundedCanaryControllerExtraction,
      {
        sha256: fundedCanaryControllerExtraction.sha256,
        bytes: fundedCanaryControllerExtraction.byteCount,
      },
    )
  ) {
    fail("DOWNLOADED_CANDIDATE_EVIDENCE_DIVERGED");
  }
  let inspectedMigrationEnvelope;
  try {
    inspectedMigrationEnvelope = inspectAndroidMigrationControllerEnvelopeV1({
      value: migrationControllerEnvelope.value,
      expectedSourceRevision,
      expectedCandidateAabSha256: files.candidateAab.sha256,
      expectedCandidateAabBytes: files.candidateAab.bytes,
    });
  } catch {
    fail("DOWNLOADED_CANDIDATE_MIGRATION_CONTROLLER_ENVELOPE_DIVERGED");
  }
  if (
    !migrationControllerExtractionReceiptValid(
      migrationControllerExtraction.value,
    ) ||
    migrationControllerExtraction.value.sourceRevision !== expectedSourceRevision ||
    migrationControllerExtraction.value.candidateAabSha256 !==
      files.candidateAab.sha256 ||
    migrationControllerExtraction.value.candidateAabBytes !==
      files.candidateAab.bytes ||
    migrationControllerExtraction.value.envelopeSha256 !==
      migrationControllerEnvelope.sha256 ||
    migrationControllerExtraction.value.envelopeBytes !==
      migrationControllerEnvelope.byteCount ||
    Object.entries(inspectedMigrationEnvelope.records).some(
      ([name, record]) =>
        migrationControllerExtraction.value.files[name].sha256 !== record.sha256 ||
        migrationControllerExtraction.value.files[name].bytes !== record.bytes,
    )
  ) {
    fail("DOWNLOADED_CANDIDATE_MIGRATION_CONTROLLER_EXTRACTION_DIVERGED");
  }
  let verifiedTairaDeployment;
  try {
    verifiedTairaDeployment = verifyTairaDeploymentManifestV1({
      manifestPath: path("taira-deployment-manifest.json"),
      operatorSignaturePath: path("taira-deployment-operator.sig"),
      reviewerSignaturePath: path("taira-deployment-reviewer.sig"),
      operatorPublicKeyPath: path("taira-deployment-operator.pem"),
      reviewerPublicKeyPath: path("taira-deployment-reviewer.pem"),
      expectedOperatorKeySha256:
        expectedTairaDeploymentOperatorKeySha256,
      expectedReviewerKeySha256:
        expectedTairaDeploymentReviewerKeySha256,
      evaluationEpochSeconds:
        tairaDeploymentAdmission.value?.evaluationEpochSeconds,
    });
  } catch {
    fail("DOWNLOADED_CANDIDATE_TAIRA_DEPLOYMENT_VERIFICATION_FAILED");
  }
  if (
    JSON.stringify(verifiedTairaDeployment) !==
      JSON.stringify(tairaDeploymentAdmission.value) ||
    verifiedTairaDeployment.manifestSha256 !==
      tairaDeploymentManifest.sha256
  ) {
    fail("DOWNLOADED_CANDIDATE_TAIRA_DEPLOYMENT_ADMISSION_DIVERGED");
  }
  if (
    !fundedCanaryExtractionReceiptValid(
      fundedCanaryControllerExtraction.value,
    ) ||
    fundedCanaryControllerExtraction.value.tarSha256 !==
      fundedCanaryControllerBundle.sha256 ||
    fundedCanaryControllerExtraction.value.tarBytes !==
      fundedCanaryControllerBundle.bytes
  ) {
    fail("DOWNLOADED_CANDIDATE_FUNDED_CANARY_EXTRACTION_DIVERGED");
  }
  if (
    !validateAndroidCandidateSigningVerificationV1(primarySigning.value) ||
    !validateAndroidCandidateSigningVerificationV1(reproducedSigning.value)
  ) {
    fail("DOWNLOADED_CANDIDATE_SIGNING_RECEIPT_INVALID");
  }
  const signingMatches = (receipt, aab, apk) =>
    receipt.artifacts.aabSha256 === aab.sha256 &&
    receipt.artifacts.aabBytes === aab.bytes &&
    receipt.artifacts.apkSha256 === apk.sha256 &&
    receipt.artifacts.apkBytes === apk.bytes &&
    receipt.signingIdentity.configSha256 ===
      manifest.evidence.signingIdentity.configSha256 &&
    receipt.signingIdentity.productionAppSigningCertificateSha256 ===
      manifest.evidence.signingIdentity.productionAppSigningCertificateSha256 &&
    receipt.signingIdentity.expectedUploadCertificateSha256 ===
      manifest.evidence.signingIdentity.productionUploadCertificateSha256 &&
    receipt.signingIdentity.observedAabUploadCertificateSha256 ===
      manifest.evidence.signingIdentity.observedAabUploadCertificateSha256 &&
    receipt.signingIdentity.observedUploadCertificateSha256 ===
      manifest.evidence.signingIdentity.observedUploadCertificateSha256;
  if (
    !signingMatches(primarySigning.value, files.candidateAab, files.candidateApk) ||
    !signingMatches(
      reproducedSigning.value,
      files.reproducedAab,
      files.reproducedApk,
    )
  ) {
    fail("DOWNLOADED_CANDIDATE_SIGNING_RECEIPT_DIVERGED");
  }

  if (
    !isProductionAdmissionV3Envelope(admission.value) ||
    admission.value?.status !== "qualified" ||
    admission.value?.platform !== "android" ||
    admission.value?.identity?.candidateAabSha256 !== files.candidateAab.sha256 ||
    admission.value?.identity?.candidateAabBytes !== files.candidateAab.bytes ||
    admission.value?.identity?.sourceRevision !== expectedSourceRevision ||
    admission.value?.identity?.candidatePiProbeReceiptSha256 !== candidatePi.sha256 ||
    admission.value?.identity?.migrationQualificationSha256 !==
      inspectedMigrationEnvelope.records["android-migration-matrix.json"].sha256 ||
    admission.value?.identity?.tairaDeploymentManifestSha256 !==
      verifiedTairaDeployment.manifestSha256
  ) {
    fail("DOWNLOADED_CANDIDATE_ADMISSION_DIVERGED");
  }

  const dependencyConfig = strictJsonInput(
    join(root, "config/gradle-dependency-provenance.json"),
    MAXIMUM_JSON_BYTES,
    "DOWNLOADED_CANDIDATE_SOURCE_DEPENDENCY_CONFIG_INVALID",
  );
  const signingConfig = strictJsonInput(
    join(root, "config/android-production-signing-identity.json"),
    MAXIMUM_JSON_BYTES,
    "DOWNLOADED_CANDIDATE_SOURCE_SIGNING_CONFIG_INVALID",
  );
  const irohaConfig = strictJsonInput(
    join(root, "config/iroha-mobile-sdk-pin.json"),
    MAXIMUM_JSON_BYTES,
    "DOWNLOADED_CANDIDATE_SOURCE_IROHA_CONFIG_INVALID",
  );
  const workflow = regularInput(
    join(root, QUALIFICATION_WORKFLOW_RELATIVE_PATH),
    MAXIMUM_JSON_BYTES,
    "DOWNLOADED_CANDIDATE_SOURCE_WORKFLOW_INVALID",
  );
  if (
    manifest.evidence.gradleDependencyProvenance.sha256 !==
      dependencyConfig.sha256 ||
    manifest.evidence.gradleDependencyProvenance.bytes !==
      dependencyConfig.byteCount ||
    manifest.evidence.signingIdentity.configSha256 !== signingConfig.sha256 ||
    manifest.evidence.irohaMobileSdkPin.sha256 !== irohaConfig.sha256 ||
    manifest.evidence.irohaMobileSdkPin.bytes !== irohaConfig.byteCount ||
    manifest.evidence.qualificationWorkflow.sha256 !== workflow.sha256 ||
    manifest.evidence.qualificationWorkflow.bytes !== workflow.bytes ||
    admission.value.identity.productionSigningIdentitySha256 !==
      signingConfig.sha256 ||
    admission.value.identity.irohaMobileSdkPinSha256 !== irohaConfig.sha256 ||
    admission.value.identity.qualificationWorkflowSha256 !== workflow.sha256
  ) {
    fail("DOWNLOADED_CANDIDATE_SOURCE_IDENTITY_DIVERGED");
  }
  const dependencyVerification = dependencyConfig.value?.dependencyVerification;
  const vendor =
    dependencyConfig.value?.repositoryPolicy?.sourceQualifiedVendorRepository;
  const lockInventory =
    dependencyConfig.value?.dependencyLocking?.materializedInventory;
  const currentEvidence = {
    verificationMetadata: evidenceFile(
      root,
      dependencyVerification?.metadataPath,
      dependencyVerification?.metadataSha256,
      MAXIMUM_EVIDENCE_BYTES,
      "DOWNLOADED_CANDIDATE_SOURCE_VERIFICATION_METADATA_DIVERGED",
    ),
    verificationMetadataDigest: evidenceFile(
      root,
      dependencyVerification?.metadataDigestPath,
      dependencyVerification?.metadataDigestSha256,
      MAXIMUM_EVIDENCE_BYTES,
      "DOWNLOADED_CANDIDATE_SOURCE_VERIFICATION_DIGEST_DIVERGED",
    ),
    vendorSourceProvenance: evidenceFile(
      root,
      vendor?.sourceProvenancePath,
      vendor?.sourceProvenanceSha256,
      MAXIMUM_EVIDENCE_BYTES,
      "DOWNLOADED_CANDIDATE_SOURCE_VENDOR_PROVENANCE_DIVERGED",
    ),
    vendorContentsManifest: evidenceFile(
      root,
      vendor?.contentsManifestPath,
      vendor?.contentsManifestSha256,
      MAXIMUM_EVIDENCE_BYTES,
      "DOWNLOADED_CANDIDATE_SOURCE_VENDOR_MANIFEST_DIVERGED",
    ),
  };
  const downloadedReviewInputs = {
    gradleDependencyProvenanceSha256: dependencyConfig.sha256,
    verificationMetadataSha256: currentEvidence.verificationMetadata.sha256,
    verificationMetadataDigestSha256:
      currentEvidence.verificationMetadataDigest.sha256,
    vendorSourceProvenanceSha256:
      currentEvidence.vendorSourceProvenance.sha256,
    vendorContentsManifestSha256:
      currentEvidence.vendorContentsManifest.sha256,
    lockFileSetSha256: lockInventory?.lockFileSetSha256,
    lockConfigurationInventorySha256:
      lockInventory?.configurationInventorySha256,
    androidProductionSigningIdentitySha256: signingConfig.sha256,
    productionAppSigningCertificateSha256:
      signingConfig.value?.productionAppSigningCertificateSha256,
    productionUploadCertificateSha256:
      signingConfig.value?.productionUploadCertificateSha256,
  };
  let verifiedDependencySigningReview;
  try {
    if (
      androidDependencySigningReviewContractSha256V1({
        sourceRevision: expectedSourceRevision,
        reviewedInputs: downloadedReviewInputs,
      }) !== expectedDependencySigningReviewContractSha256
    ) {
      fail("DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_CONTRACT_DIVERGED");
    }
    verifiedDependencySigningReview = verifyAndroidDependencySigningReviewV1({
      manifestPath: path("android-dependency-signing-review-manifest.json"),
      producerSignaturePath: path(
        "android-dependency-signing-review-producer.sig",
      ),
      reviewerSignaturePath: path(
        "android-dependency-signing-review-reviewer.sig",
      ),
      producerPublicKeyPath: path(
        "android-dependency-signing-review-producer.pem",
      ),
      reviewerPublicKeyPath: path(
        "android-dependency-signing-review-reviewer.pem",
      ),
      expectedProducerKeySha256:
        expectedDependencySigningReviewProducerKeySha256,
      expectedReviewerKeySha256:
        expectedDependencySigningReviewReviewerKeySha256,
      expectedReviewSequenceNumber:
        expectedDependencySigningReviewSequenceNumber,
      expectedSourceRevision,
      expectedReviewContractSha256:
        expectedDependencySigningReviewContractSha256,
      expectedReviewedInputs: downloadedReviewInputs,
      evaluationEpochSeconds:
        dependencySigningReviewAdmission.value?.evaluationEpochSeconds,
    });
  } catch (error) {
    if (
      error?.message ===
      "DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_CONTRACT_DIVERGED"
    ) {
      throw error;
    }
    fail("DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_VERIFICATION_FAILED");
  }
  if (
    Object.entries(currentEvidence).some(
      ([key, record]) =>
        manifest.evidence[key].sha256 !== record.sha256 ||
        manifest.evidence[key].bytes !== record.bytes,
    ) ||
    manifest.evidence.lockInventory.fileCount !==
      lockInventory?.lockFilePaths?.length ||
    manifest.evidence.lockInventory.lockFileSetSha256 !==
      lockInventory?.lockFileSetSha256 ||
    JSON.stringify(verifiedDependencySigningReview) !==
      JSON.stringify(dependencySigningReviewAdmission.value) ||
    verifiedDependencySigningReview.manifestSha256 !==
      dependencySigningReviewManifest.sha256 ||
    admission.value.identity.dependencySigningReviewManifestSha256 !==
      verifiedDependencySigningReview.manifestSha256
  ) {
    fail("DOWNLOADED_CANDIDATE_DEPENDENCY_IDENTITY_DIVERGED");
  }

  return {
    schemaVersion: 1,
    contractId: "sora-android-downloaded-qualified-candidate-validation-v1",
    status: "validated",
    platform: "android",
    sourceRevision: expectedSourceRevision,
    candidateAabSha256: files.candidateAab.sha256,
    candidateAabBytes: files.candidateAab.bytes,
    tairaDeploymentManifestSha256:
      verifiedTairaDeployment.manifestSha256,
    dependencySigningReviewManifestSha256:
      verifiedDependencySigningReview.manifestSha256,
    packageManifestSha256: manifestRecord.sha256,
    authorization: {
      authorizesRelease: false,
      authorizesProductionMutation: false,
    },
  };
};
