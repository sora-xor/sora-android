#!/usr/bin/env node

import { verifyTairaDeploymentManifestV1 } from "./lib/taira-deployment-manifest-v1.mjs";

const fail = (code) => {
  process.stderr.write(`${code}\n`);
  process.exit(1);
};

if (process.argv.length !== 2) {
  fail("TAIRA_DEPLOYMENT_VERIFIER_ARGUMENTS_INVALID");
}

const required = (name) => {
  const value = process.env[name];
  if (typeof value !== "string" || value.length === 0) {
    fail(`TAIRA_DEPLOYMENT_INPUT_MISSING:${name}`);
  }
  return value;
};

const evaluationRaw = required("RELEASE_EVALUATION_EPOCH_SECONDS");
if (!/^[1-9][0-9]{0,11}$/.test(evaluationRaw)) {
  fail("TAIRA_DEPLOYMENT_EVALUATION_EPOCH_INVALID");
}
const evaluationEpochSeconds = Number(evaluationRaw);
if (!Number.isSafeInteger(evaluationEpochSeconds)) {
  fail("TAIRA_DEPLOYMENT_EVALUATION_EPOCH_INVALID");
}

try {
  const receipt = verifyTairaDeploymentManifestV1({
    manifestPath: required("TAIRA_DEPLOYMENT_MANIFEST_PATH"),
    operatorSignaturePath: required("TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_PATH"),
    reviewerSignaturePath: required("TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_PATH"),
    operatorPublicKeyPath: required("TAIRA_DEPLOYMENT_OPERATOR_PUBLIC_KEY_PATH"),
    reviewerPublicKeyPath: required("TAIRA_DEPLOYMENT_REVIEWER_PUBLIC_KEY_PATH"),
    expectedOperatorKeySha256: required("TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256"),
    expectedReviewerKeySha256: required("TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256"),
    evaluationEpochSeconds,
  });
  process.stdout.write(`${JSON.stringify(receipt, null, 2)}\n`);
} catch (error) {
  fail(error instanceof Error ? error.message : "TAIRA_DEPLOYMENT_VERIFICATION_FAILED");
}
