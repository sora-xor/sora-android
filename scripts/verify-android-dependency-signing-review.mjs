#!/usr/bin/env node

import { resolve } from "node:path";
import {
  androidDependencySigningReviewContractSha256V1,
  verifyAndroidDependencySigningReviewV1,
} from "./lib/android-dependency-signing-review-v1.mjs";
import { readStrictJsonFile } from "./lib/strict-evidence.mjs";

const fail = (code) => {
  process.stderr.write(`${code}\n`);
  process.exit(1);
};

const printContractMode =
  process.argv.length === 3 &&
  process.argv[2] === "--print-contract-sha256";
if (!printContractMode && process.argv.length !== 2) {
  fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_ARGUMENTS_INVALID");
}
const required = (name) => {
  const value = process.env[name] ?? "";
  if (value.length === 0) fail(`ANDROID_DEPENDENCY_SIGNING_REVIEW_INPUT_MISSING:${name}`);
  return value;
};
const positiveInteger = (name) => {
  const value = required(name);
  if (!/^[1-9][0-9]{0,11}$/.test(value)) fail(`${name}_INVALID`);
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) fail(`${name}_INVALID`);
  return parsed;
};
const root = resolve(new URL("..", import.meta.url).pathname);
const dependency = readStrictJsonFile(
  resolve(root, "config/gradle-dependency-provenance.json"),
  512 * 1024,
);
const signing = readStrictJsonFile(
  resolve(root, "config/android-production-signing-identity.json"),
  64 * 1024,
);
if (dependency === null || signing === null) fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_INPUT_INVALID");
const reviewedInputs = {
  gradleDependencyProvenanceSha256: dependency.sha256,
  verificationMetadataSha256: dependency.value.dependencyVerification.metadataSha256,
  verificationMetadataDigestSha256: dependency.value.dependencyVerification.metadataDigestSha256,
  vendorSourceProvenanceSha256:
    dependency.value.repositoryPolicy.sourceQualifiedVendorRepository.sourceProvenanceSha256,
  vendorContentsManifestSha256:
    dependency.value.repositoryPolicy.sourceQualifiedVendorRepository.contentsManifestSha256,
  lockFileSetSha256:
    dependency.value.dependencyLocking.materializedInventory.lockFileSetSha256,
  lockConfigurationInventorySha256:
    dependency.value.dependencyLocking.materializedInventory.configurationInventorySha256,
  androidProductionSigningIdentitySha256: signing.sha256,
  productionAppSigningCertificateSha256:
    signing.value.productionAppSigningCertificateSha256,
  productionUploadCertificateSha256:
    signing.value.productionUploadCertificateSha256,
};
const sourceRevision = required("ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_REVISION");
let computedContract;
try {
  computedContract = androidDependencySigningReviewContractSha256V1({
    sourceRevision,
    reviewedInputs,
  });
} catch (error) {
  fail(
    error instanceof Error
      ? error.message
      : "ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_INPUT_INVALID",
  );
}
if (printContractMode) {
  process.stdout.write(`contractSha256=${computedContract}\n`);
  process.exit(0);
}
const expectedContract = required("ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_SHA256");
if (computedContract !== expectedContract) fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_DIVERGED");
if (required("PRODUCTION_CANDIDATE_SOURCE_REVISION") !== sourceRevision) {
  fail("ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_REVISION_DIVERGED");
}
try {
  const receipt = verifyAndroidDependencySigningReviewV1({
    manifestPath: required("ANDROID_DEPENDENCY_SIGNING_REVIEW_MANIFEST_PATH"),
    producerSignaturePath: required("ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_SIGNATURE_PATH"),
    reviewerSignaturePath: required("ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_SIGNATURE_PATH"),
    producerPublicKeyPath: required("ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_PUBLIC_KEY_PATH"),
    reviewerPublicKeyPath: required("ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_PUBLIC_KEY_PATH"),
    expectedProducerKeySha256: required("ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_KEY_SHA256"),
    expectedReviewerKeySha256: required("ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_KEY_SHA256"),
    expectedReviewSequenceNumber: positiveInteger("ANDROID_DEPENDENCY_SIGNING_REVIEW_SEQUENCE_NUMBER"),
    expectedSourceRevision: sourceRevision,
    expectedReviewContractSha256: expectedContract,
    expectedReviewedInputs: reviewedInputs,
    evaluationEpochSeconds: positiveInteger("ANDROID_DEPENDENCY_SIGNING_REVIEW_EVALUATION_EPOCH_SECONDS"),
  });
  process.stdout.write(`${JSON.stringify(receipt, null, 2)}\n`);
} catch (error) {
  fail(error instanceof Error ? error.message : "ANDROID_DEPENDENCY_SIGNING_REVIEW_VERIFICATION_FAILED");
}
