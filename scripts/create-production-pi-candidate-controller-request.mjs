#!/usr/bin/env node

import { isAbsolute, resolve } from "node:path";
import {
  hashStableRegularFile,
  readStrictJsonFile,
} from "./lib/strict-evidence.mjs";
import {
  PRODUCTION_PI_CANDIDATE_V3,
  containsPrivacySensitiveField,
  validateProductionPiRawLiveReceipt,
} from "./lib/production-pi-receipt.mjs";
import { verifyTairaDeploymentManifestV1 } from "./lib/taira-deployment-manifest-v1.mjs";

const root = resolve(new URL("..", import.meta.url).pathname);
const MAXIMUM_AAB_BYTES = 512 * 1024 * 1024;
const MAXIMUM_RECEIPT_BYTES = 64 * 1024;
const MAXIMUM_RUNTIME_METADATA_BYTES = 2 * 1024 * 1024;
const SHA256 = /^[0-9a-f]{64}$/;

const fail = (code) => {
  process.stderr.write(`${code}\n`);
  process.exit(1);
};
const required = (name) => {
  const value = process.env[name] ?? "";
  if (value.length === 0) fail(`PI_CANDIDATE_REQUEST_${name}_MISSING`);
  return value;
};
const canonicalPath = (value) =>
  isAbsolute(value) && resolve(value) === value;

const candidatePath = required("PRODUCTION_CANDIDATE_AAB_PATH");
const rawLivePath = required("PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH");
const sourceRevision = required("PRODUCTION_CANDIDATE_SOURCE_REVISION");
const controllerId = required("PRODUCTION_PI_CONTROLLER_ID");
const requestedAtRaw = required(
  "PRODUCTION_PI_CANDIDATE_REQUESTED_AT_EPOCH_SECONDS",
);
if (
  !canonicalPath(candidatePath) ||
  !canonicalPath(rawLivePath) ||
  !/^[0-9a-f]{40}$/.test(sourceRevision) ||
  !/^[A-Za-z0-9][A-Za-z0-9._-]{2,255}$/.test(controllerId) ||
  !/^[1-9][0-9]{0,9}$/.test(requestedAtRaw)
) {
  fail("PI_CANDIDATE_REQUEST_IDENTITY_INVALID");
}
const requestedAtEpochSeconds = Number(requestedAtRaw);
const candidate = hashStableRegularFile(candidatePath, MAXIMUM_AAB_BYTES);
const rawLive = readStrictJsonFile(rawLivePath, MAXIMUM_RECEIPT_BYTES);
const runtimeMetadata = hashStableRegularFile(
  resolve(root, "common/src/production/assets/sora2_metadata"),
  MAXIMUM_RUNTIME_METADATA_BYTES,
);
if (
  candidate === null ||
  candidate.bytes === 0 ||
  rawLive === null ||
  runtimeMetadata === null ||
  containsPrivacySensitiveField(rawLive.value) ||
  validateProductionPiRawLiveReceipt(
    rawLive.value,
    requestedAtEpochSeconds,
  ) === null
) {
  fail("PI_CANDIDATE_REQUEST_RAW_INPUT_NOT_QUALIFIED");
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
    evaluationEpochSeconds: requestedAtEpochSeconds,
  });
} catch {
  fail("PI_CANDIDATE_REQUEST_TAIRA_DEPLOYMENT_NOT_QUALIFIED");
}
const tairaAdmissionPath = required(
  "TAIRA_DEPLOYMENT_ADMISSION_RECEIPT_PATH",
);
const tairaAdmission = canonicalPath(tairaAdmissionPath)
  ? readStrictJsonFile(tairaAdmissionPath, MAXIMUM_RECEIPT_BYTES)
  : null;
if (
  tairaAdmission === null ||
  JSON.stringify(tairaAdmission.value) !== JSON.stringify(tairaDeployment) ||
  !SHA256.test(tairaDeployment.current?.genesisSha256 ?? "")
) {
  fail("PI_CANDIDATE_REQUEST_TAIRA_ADMISSION_DIVERGED");
}

const request = {
  schemaVersion: 1,
  contractId: "sora-android-production-pi-candidate-controller-request-v1",
  platform: "android",
  requestedAtEpochSeconds,
  controllerId,
  expectedReceipt: {
    schemaVersion: PRODUCTION_PI_CANDIDATE_V3.schemaVersion,
    contractId: PRODUCTION_PI_CANDIDATE_V3.contractId,
    status: PRODUCTION_PI_CANDIDATE_V3.status,
  },
  candidate: {
    applicationId: "jp.co.soramitsu.sora",
    artifactSha256: candidate.sha256,
    artifactBytes: candidate.bytes,
    sourceRevision,
  },
  runtime: {
    sora2GenesisHash: rawLive.value.sora2GenesisHash.slice(2),
    specVersion: 130,
    transactionVersion: 130,
    metadataSha256: runtimeMetadata.sha256,
  },
  rawLiveProbe: {
    receiptSha256: rawLive.sha256,
    receipt: rawLive.value,
  },
  tairaDeployment: {
    admissionSha256: tairaAdmission.sha256,
    manifestSha256: tairaDeployment.manifestSha256,
    chainId: tairaDeployment.current.chainId,
    genesisHash: tairaDeployment.current.genesisSha256,
    toriiEndpoint: tairaDeployment.current.toriiBaseUrl,
    i105Discriminant: tairaDeployment.current.i105Discriminant,
  },
  privacy: {
    aggregateOnly: true,
    accountIdentifiersIncluded: false,
    addressesIncluded: false,
    transactionIdentifiersIncluded: false,
    phrasesOrSeedsIncluded: false,
    privateKeysIncluded: false,
    publicKeysIncluded: false,
    rawSignedPayloadsIncluded: false,
    perWalletRecordsIncluded: false,
    deviceIdentifiersIncluded: false,
    ipAddressesIncluded: false,
    rawResponsesIncluded: false,
    rawErrorsIncluded: false,
  },
};
process.stdout.write(`${JSON.stringify(request, null, 2)}\n`);
