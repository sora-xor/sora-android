#!/usr/bin/env node

import { resolve } from "node:path";
import { validateDownloadedAndroidQualifiedCandidatePackageV1 } from "./lib/android-qualified-candidate-package-v1.mjs";

const required = (name) => {
  const value = process.env[name] ?? "";
  if (value.length === 0) throw new Error(`${name}_MISSING`);
  return value;
};

const result = validateDownloadedAndroidQualifiedCandidatePackageV1({
  root: resolve(new URL("..", import.meta.url).pathname),
  packageRoot: required("PRODUCTION_DOWNLOADED_CANDIDATE_PACKAGE_ROOT"),
  expectedSourceRevision: required("PRODUCTION_CANDIDATE_SOURCE_REVISION"),
  expectedTairaDeploymentOperatorKeySha256: required(
    "TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256",
  ),
  expectedTairaDeploymentReviewerKeySha256: required(
    "TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256",
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
});
process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
