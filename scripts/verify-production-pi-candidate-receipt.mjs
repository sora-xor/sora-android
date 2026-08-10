#!/usr/bin/env node

import { isAbsolute, resolve } from "node:path";
import {
  hashStableRegularFile,
  readStrictJsonFile,
} from "./lib/strict-evidence.mjs";
import {
  validateProductionPiRawLiveReceipt,
  verifyProductionPiCandidateReceiptV3,
} from "./lib/production-pi-receipt.mjs";
import { verifyTairaDeploymentManifestV1 } from "./lib/taira-deployment-manifest-v1.mjs";

const root = resolve(new URL("..", import.meta.url).pathname);
const MAXIMUM_AAB_BYTES = 512 * 1024 * 1024;
const MAXIMUM_RECEIPT_BYTES = 64 * 1024;
const MAXIMUM_RUNTIME_METADATA_BYTES = 2 * 1024 * 1024;

const fail = (code) => {
  process.stderr.write(`${code}\n`);
  process.exit(1);
};
const required = (name) => {
  const value = process.env[name] ?? "";
  if (value.length === 0) fail(`PI_CANDIDATE_VERIFY_${name}_MISSING`);
  return value;
};
const canonicalPath = (value) =>
  isAbsolute(value) && resolve(value) === value;
const strictRecord = (name) => {
  const path = required(name);
  const record = canonicalPath(path)
    ? readStrictJsonFile(path, MAXIMUM_RECEIPT_BYTES)
    : null;
  if (record === null) fail(`PI_CANDIDATE_VERIFY_${name}_INVALID`);
  return record;
};

const requireRawBinding = process.argv.slice(2).includes(
  "--require-raw-live-binding",
);
if (
  process.argv.length !== (requireRawBinding ? 3 : 2) ||
  process.argv.slice(2).some(
    (argument) => argument !== "--require-raw-live-binding",
  )
) {
  fail("PI_CANDIDATE_VERIFY_ARGUMENT_INVALID");
}
const candidatePath = required("PRODUCTION_CANDIDATE_AAB_PATH");
const sourceRevision = required("PRODUCTION_CANDIDATE_SOURCE_REVISION");
const evaluationRaw = required(
  "PRODUCTION_PI_CANDIDATE_EVALUATED_AT_EPOCH_SECONDS",
);
const expectedControllerId = required("PRODUCTION_PI_CONTROLLER_ID");
const expectedTrustSha256 = required("PRODUCTION_ROLLOUT_TRUST_SHA256");
if (
  !canonicalPath(candidatePath) ||
  !/^[0-9a-f]{40}$/.test(sourceRevision) ||
  !/^[1-9][0-9]{0,9}$/.test(evaluationRaw)
) {
  fail("PI_CANDIDATE_VERIFY_CONTEXT_INVALID");
}
const evaluationEpoch = Number(evaluationRaw);
const candidate = hashStableRegularFile(candidatePath, MAXIMUM_AAB_BYTES);
const runtimeMetadata = hashStableRegularFile(
  resolve(root, "common/src/production/assets/sora2_metadata"),
  MAXIMUM_RUNTIME_METADATA_BYTES,
);
const receiptRecord = strictRecord("PRODUCTION_CANDIDATE_PI_RECEIPT_PATH");
const signatureRecord = strictRecord(
  "PRODUCTION_CANDIDATE_PI_RECEIPT_SIGNATURE_PATH",
);
const trustRecord = readStrictJsonFile(
  resolve(root, "config/production-rollout-trust.json"),
  MAXIMUM_RECEIPT_BYTES,
);
if (candidate === null || runtimeMetadata === null || trustRecord === null) {
  fail("PI_CANDIDATE_VERIFY_LOCAL_INPUT_INVALID");
}

let tairaDeployment;
try {
  tairaDeployment = verifyTairaDeploymentManifestV1({
    manifestPath: required("TAIRA_DEPLOYMENT_MANIFEST_PATH"),
    operatorSignaturePath: required(
      "TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_PATH",
    ),
    reviewerSignaturePath: required(
      "TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_PATH",
    ),
    operatorPublicKeyPath: required(
      "TAIRA_DEPLOYMENT_OPERATOR_PUBLIC_KEY_PATH",
    ),
    reviewerPublicKeyPath: required(
      "TAIRA_DEPLOYMENT_REVIEWER_PUBLIC_KEY_PATH",
    ),
    expectedOperatorKeySha256: required(
      "TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256",
    ),
    expectedReviewerKeySha256: required(
      "TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256",
    ),
    evaluationEpochSeconds: evaluationEpoch,
  });
} catch {
  fail("PI_CANDIDATE_VERIFY_TAIRA_DEPLOYMENT_NOT_QUALIFIED");
}

let expectedRawLiveReceiptSha256;
if (requireRawBinding) {
  const rawRecord = strictRecord("PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH");
  if (
    validateProductionPiRawLiveReceipt(rawRecord.value, evaluationEpoch) ===
    null
  ) {
    fail("PI_CANDIDATE_VERIFY_RAW_LIVE_NOT_QUALIFIED");
  }
  expectedRawLiveReceiptSha256 = rawRecord.sha256;
}
const result = verifyProductionPiCandidateReceiptV3({
  receiptRecord,
  signatureRecord,
  trustRecord,
  expectedTrustSha256,
  evaluationEpoch,
  validationContext: {
    expectedControllerId,
    expectedCandidate: {
      artifactSha256: candidate.sha256,
      artifactBytes: candidate.bytes,
      sourceRevision,
    },
    expectedRawLiveReceiptSha256,
    expectedRuntimeMetadataSha256: runtimeMetadata.sha256,
    expectedTairaDeployment: {
      chainId: tairaDeployment.current.chainId,
      toriiEndpoint: tairaDeployment.current.toriiBaseUrl,
      genesisHash: tairaDeployment.current.genesisSha256,
    },
  },
});
if (result === null) fail("PI_CANDIDATE_RECEIPT_NOT_QUALIFIED");
process.stdout.write(
  `${JSON.stringify(
    {
      schemaVersion: 1,
      contractId: "sora-android-production-pi-candidate-admission-v1",
      status: "qualified",
      platform: "android",
      controllerId: expectedControllerId,
      receiptSha256: result.receiptSha256,
      signatureReceiptSha256: result.signatureReceiptSha256,
      candidateBindingSha256: result.bindingSha256,
      capabilityBindingSha256: result.capabilityBindingSha256,
      capturedAtEpochSeconds: result.capturedAtEpochSeconds,
    },
    null,
    2,
  )}\n`,
);
