#!/usr/bin/env node

import { resolve } from "node:path";
import {
  createAndroidQualifiedCandidatePackageV1,
  validateAndroidQualifiedCandidatePackageV1,
} from "./lib/android-qualified-candidate-package-v1.mjs";

const required = (name) => {
  const value = process.env[name] ?? "";
  if (value.length === 0) throw new Error(`${name}_MISSING`);
  return value;
};

const epochRaw = required("PRODUCTION_RELEASE_EVALUATED_AT_EPOCH_SECONDS");
if (!/^[1-9][0-9]{0,9}$/.test(epochRaw)) {
  throw new Error("PRODUCTION_RELEASE_EVALUATED_AT_EPOCH_SECONDS_INVALID");
}

const manifest = createAndroidQualifiedCandidatePackageV1({
  root: resolve(new URL("..", import.meta.url).pathname),
  sourceRevision: required("PRODUCTION_CANDIDATE_SOURCE_REVISION"),
  evaluatedAtEpochSeconds: Number(epochRaw),
  observedUploadCertificateSha256: required(
    "PRODUCTION_OBSERVED_UPLOAD_CERTIFICATE_SHA256",
  ),
  primaryAabPath: required("PRODUCTION_CANDIDATE_AAB_PATH"),
  reproducedAabPath: required("PRODUCTION_REPRODUCED_AAB_PATH"),
  primaryApkPath: required("PRODUCTION_CANDIDATE_APK_PATH"),
  reproducedApkPath: required("PRODUCTION_REPRODUCED_APK_PATH"),
  primaryMappingPath: required("PRODUCTION_CANDIDATE_MAPPING_PATH"),
  reproducedMappingPath: required("PRODUCTION_REPRODUCED_MAPPING_PATH"),
  primaryBuildLogPath: required("PRODUCTION_PRIMARY_BUILD_LOG_PATH"),
  reproducedBuildLogPath: required("PRODUCTION_REPRODUCED_BUILD_LOG_PATH"),
  primarySigningVerificationPath: required(
    "PRODUCTION_PRIMARY_SIGNING_VERIFICATION_PATH",
  ),
  reproducedSigningVerificationPath: required(
    "PRODUCTION_REPRODUCED_SIGNING_VERIFICATION_PATH",
  ),
  migrationControllerEnvelopePath: required(
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PATH",
  ),
  migrationControllerExtractionReceiptPath: required(
    "ANDROID_MIGRATION_CONTROLLER_EXTRACTION_RECEIPT_PATH",
  ),
  tairaDeploymentManifestPath: required("TAIRA_DEPLOYMENT_MANIFEST_PATH"),
  tairaDeploymentOperatorSignaturePath: required(
    "TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_PATH",
  ),
  tairaDeploymentReviewerSignaturePath: required(
    "TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_PATH",
  ),
  tairaDeploymentOperatorPublicKeyPath: required(
    "TAIRA_DEPLOYMENT_OPERATOR_PUBLIC_KEY_PATH",
  ),
  tairaDeploymentReviewerPublicKeyPath: required(
    "TAIRA_DEPLOYMENT_REVIEWER_PUBLIC_KEY_PATH",
  ),
  tairaDeploymentAdmissionReceiptPath: required(
    "TAIRA_DEPLOYMENT_ADMISSION_RECEIPT_PATH",
  ),
  expectedTairaDeploymentOperatorKeySha256: required(
    "TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256",
  ),
  expectedTairaDeploymentReviewerKeySha256: required(
    "TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256",
  ),
  dependencySigningReviewManifestPath: required(
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_MANIFEST_PATH",
  ),
  dependencySigningReviewProducerSignaturePath: required(
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_SIGNATURE_PATH",
  ),
  dependencySigningReviewReviewerSignaturePath: required(
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_SIGNATURE_PATH",
  ),
  dependencySigningReviewProducerPublicKeyPath: required(
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_PUBLIC_KEY_PATH",
  ),
  dependencySigningReviewReviewerPublicKeyPath: required(
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_PUBLIC_KEY_PATH",
  ),
  dependencySigningReviewAdmissionReceiptPath: required(
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_ADMISSION_PATH",
  ),
  expectedDependencySigningReviewProducerKeySha256: required(
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_KEY_SHA256",
  ),
  expectedDependencySigningReviewReviewerKeySha256: required(
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_KEY_SHA256",
  ),
  expectedDependencySigningReviewSequenceNumber: Number(
    required("ANDROID_DEPENDENCY_SIGNING_REVIEW_SEQUENCE_NUMBER"),
  ),
  expectedDependencySigningReviewContractSha256: required(
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_SHA256",
  ),
  fundedCanaryControllerBundlePath: required(
    "FUNDED_CANARY_CONTROLLER_BUNDLE_PATH",
  ),
  fundedCanaryControllerExtractionReceiptPath: required(
    "FUNDED_CANARY_CONTROLLER_EXTRACTION_RECEIPT_PATH",
  ),
  productionAdmissionPath: required("PRODUCTION_QUALIFICATION_RECEIPT_PATH"),
  candidatePiReceiptPath: required("PRODUCTION_CANDIDATE_PI_RECEIPT_PATH"),
});
if (!validateAndroidQualifiedCandidatePackageV1(manifest)) {
  throw new Error("ANDROID_QUALIFIED_CANDIDATE_PACKAGE_V1_SELF_CHECK_FAILED");
}
process.stdout.write(`${JSON.stringify(manifest, null, 2)}\n`);
