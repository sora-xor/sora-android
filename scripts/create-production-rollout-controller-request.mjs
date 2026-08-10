#!/usr/bin/env node

import { createHash } from "node:crypto";
import { isAbsolute, resolve } from "node:path";
import {
  hashStableRegularFile,
  readStrictJsonFile,
} from "./lib/strict-evidence.mjs";
import {
  PRODUCTION_ADMISSION_IDENTITY_KEYS,
  validateQualifiedCandidateProvenance,
} from "./lib/qualified-candidate-provenance.mjs";
import {
  containsPrivacySensitiveField,
  validateProductionPiRawLiveReceipt,
  verifyProductionPiCandidateReceiptV3,
} from "./lib/production-pi-receipt.mjs";
import {
  ANDROID_PRODUCTION_ROLLOUT_CONTROLLER_REQUEST_V3,
  isProductionAdmissionV3Envelope,
  isProductionRolloutControllerRequestV3Envelope,
  productionAdmissionV3ProjectionPrefix,
} from "./lib/production-rollout-v3-contract.mjs";

const root = resolve(new URL("..", import.meta.url).pathname);
const candidatePath = process.env.PRODUCTION_CANDIDATE_AAB_PATH ?? "";
const admissionPath =
  process.env.PRODUCTION_QUALIFICATION_RECEIPT_PATH ?? "";
const piPath = process.env.PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH ?? "";
const candidatePiPath =
  process.env.PRODUCTION_CANDIDATE_PI_RECEIPT_PATH ?? "";
const candidatePiSignaturePath =
  process.env.PRODUCTION_CANDIDATE_PI_RECEIPT_SIGNATURE_PATH ?? "";
const piControllerId = process.env.PRODUCTION_PI_CONTROLLER_ID ?? "";
const targetRaw = process.env.PRODUCTION_ROLLOUT_TARGET_PERCENT ?? "";
const evaluatedAtRaw =
  process.env.PRODUCTION_ROLLOUT_EVALUATED_AT_EPOCH_SECONDS ?? "";
const sourceRevision =
  process.env.PRODUCTION_CANDIDATE_SOURCE_REVISION ?? "";
const candidateRepository =
  process.env.PRODUCTION_CANDIDATE_REPOSITORY ?? "";
const candidateRunIdRaw = process.env.PRODUCTION_CANDIDATE_RUN_ID ?? "";
const candidateRunReceiptPath =
  process.env.PRODUCTION_CANDIDATE_RUN_RECEIPT_PATH ?? "";
const candidateArtifactReceiptPath =
  process.env.PRODUCTION_CANDIDATE_ARTIFACT_RECEIPT_PATH ?? "";
const trustPin = process.env.PRODUCTION_ROLLOUT_TRUST_SHA256 ?? "";
const trustPath = resolve(root, "config/production-rollout-trust.json");
const tairaDeploymentAdmissionPath =
  process.env.TAIRA_DEPLOYMENT_ADMISSION_RECEIPT_PATH ?? "";
const MAXIMUM_RECEIPT_BYTES = 64 * 1024;
const MAXIMUM_CANDIDATE_BYTES = 512 * 1024 * 1024;
const MAXIMUM_CAPABILITY_AGE_SECONDS = 5 * 60;
const MAXIMUM_FUTURE_SKEW_SECONDS = 30;
const SHA256 = /^[0-9a-f]{64}$/;
const SORA2_REVISION = "411dcdb70c5c00b21482a44d02334840d5f338c6";
const admissionIdentityKeys = [
  ...PRODUCTION_ADMISSION_IDENTITY_KEYS,
  "bindingSha256",
];
const admissionProjectionKeys = admissionIdentityKeys.filter(
  (key) => key !== "bindingSha256",
);

const exactKeys = (value, expected) => {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return false;
  }
  const actual = Object.keys(value).sort();
  const required = [...expected].sort();
  return (
    actual.length === required.length &&
    actual.every((key, index) => key === required[index])
  );
};
const canonicalPath = (value) =>
  value.length > 0 && isAbsolute(value) && resolve(value) === value;
const containsSensitiveField = containsPrivacySensitiveField;
const fail = (code) => {
  process.stderr.write(`${code}\n`);
  process.exit(1);
};

if (!/^(1|5|25|100)$/.test(targetRaw)) {
  fail("ROLLOUT_CONTROLLER_REQUEST_TARGET_INVALID");
}
if (!/^[1-9][0-9]{0,9}$/.test(evaluatedAtRaw)) {
  fail("ROLLOUT_CONTROLLER_REQUEST_EVALUATION_EPOCH_INVALID");
}
if (!/^[0-9a-f]{40}$/.test(sourceRevision)) {
  fail("ROLLOUT_CONTROLLER_REQUEST_SOURCE_REVISION_INVALID");
}
if (!SHA256.test(trustPin)) {
  fail("ROLLOUT_CONTROLLER_REQUEST_TRUST_PIN_INVALID");
}
if (
  !canonicalPath(candidatePath) ||
  !canonicalPath(admissionPath) ||
  !canonicalPath(piPath) ||
  !canonicalPath(candidatePiPath) ||
  !canonicalPath(candidatePiSignaturePath) ||
  !canonicalPath(tairaDeploymentAdmissionPath) ||
  piPath === candidatePiPath ||
  !canonicalPath(candidateRunReceiptPath) ||
  !canonicalPath(candidateArtifactReceiptPath)
) {
  fail("ROLLOUT_CONTROLLER_REQUEST_INPUT_PATH_INVALID");
}

const candidate = hashStableRegularFile(
  candidatePath,
  MAXIMUM_CANDIDATE_BYTES,
);
const admission = readStrictJsonFile(
  admissionPath,
  MAXIMUM_RECEIPT_BYTES,
);
const pi = readStrictJsonFile(piPath, MAXIMUM_RECEIPT_BYTES);
const candidatePi = readStrictJsonFile(
  candidatePiPath,
  MAXIMUM_RECEIPT_BYTES,
);
const candidatePiSignature = readStrictJsonFile(
  candidatePiSignaturePath,
  MAXIMUM_RECEIPT_BYTES,
);
const trust = readStrictJsonFile(trustPath, MAXIMUM_RECEIPT_BYTES);
const tairaDeploymentAdmission = readStrictJsonFile(
  tairaDeploymentAdmissionPath,
  MAXIMUM_RECEIPT_BYTES,
);
if (
  candidate === null ||
  admission === null ||
  pi === null ||
  candidatePi === null ||
  candidatePiSignature === null ||
  trust === null ||
  tairaDeploymentAdmission === null
) {
  fail("ROLLOUT_CONTROLLER_REQUEST_INPUT_MISSING_OR_UNSTABLE");
}
if (
  containsSensitiveField(admission.value) ||
  containsSensitiveField(pi.value) ||
  containsSensitiveField(candidatePi.value)
) {
  fail("ROLLOUT_CONTROLLER_REQUEST_PI_RECEIPT_CONTAINS_SENSITIVE_FIELD");
}
const admissionEnvelope = admission.value;
const admissionValue = admissionEnvelope?.admission;
const admissionIdentity = admissionValue?.identity;
const trustValue = trust.value;
const admissionBinding =
  admissionIdentity === null || typeof admissionIdentity !== "object"
    ? "INVALID"
    : createHash("sha256")
        .update(
          [
            ...productionAdmissionV3ProjectionPrefix(),
            ...admissionProjectionKeys.map(
              (key) => `${key}=${admissionIdentity[key]}`,
            ),
          ].join("\n"),
          "utf8",
        )
        .digest("hex");
const candidateProvenance = validateQualifiedCandidateProvenance({
  root,
  admissionIdentity,
  expectedRepository: candidateRepository,
  expectedSourceRevision: sourceRevision,
  candidateRunIdRaw,
  candidateRunReceiptPath,
  candidateArtifactReceiptPath,
});
const currentPiSnapshot = validateProductionPiRawLiveReceipt(
  pi.value,
  Number(evaluatedAtRaw),
);
const candidatePiSnapshot = verifyProductionPiCandidateReceiptV3({
  receiptRecord: candidatePi,
  signatureRecord: candidatePiSignature,
  trustRecord: trust,
  expectedTrustSha256: trustPin,
  evaluationEpoch: admissionIdentity?.qualifiedAtEpochSeconds,
  validationContext: {
    expectedControllerId: piControllerId,
    expectedCandidate: {
      artifactSha256: candidate.sha256,
      artifactBytes: candidate.bytes,
      sourceRevision,
    },
    expectedRuntimeMetadataSha256:
      admissionIdentity?.runtimeMetadataSha256,
    expectedTairaDeployment: {
      chainId: tairaDeploymentAdmission.value?.current?.chainId,
      toriiEndpoint:
        tairaDeploymentAdmission.value?.current?.toriiBaseUrl,
      genesisHash:
        tairaDeploymentAdmission.value?.current?.genesisSha256,
    },
  },
});
if (
  !exactKeys(admissionEnvelope, [
    "mode",
    "failures",
    "releaseBlockers",
    "admission",
  ]) ||
  admissionEnvelope.mode !== "release" ||
  !Array.isArray(admissionEnvelope.failures) ||
  admissionEnvelope.failures.length !== 0 ||
  !Array.isArray(admissionEnvelope.releaseBlockers) ||
  admissionEnvelope.releaseBlockers.length !== 0 ||
  !exactKeys(admissionValue, [
    "schemaVersion",
    "contractId",
    "status",
    "platform",
    "identity",
  ]) ||
  !isProductionAdmissionV3Envelope(admissionValue) ||
  admissionValue?.status !== "qualified" ||
  admissionValue?.platform !== "android" ||
  !exactKeys(admissionIdentity, admissionIdentityKeys) ||
  admissionIdentity?.candidateAabSha256 !== candidate.sha256 ||
  admissionIdentity?.candidateAabBytes !== candidate.bytes ||
  admissionIdentity?.sourceRevision !== sourceRevision ||
  !Number.isSafeInteger(admissionIdentity?.qualifiedAtEpochSeconds) ||
  admissionIdentity.qualifiedAtEpochSeconds <= 0 ||
  admissionIdentity.qualifiedAtEpochSeconds > Number(evaluatedAtRaw) ||
  admissionIdentity?.candidatePiProbeReceiptSha256 !== candidatePi.sha256 ||
  tairaDeploymentAdmission.value?.manifestSha256 !==
    admissionIdentity?.tairaDeploymentManifestSha256 ||
  !Number.isSafeInteger(candidatePi.value?.capturedAtEpochSeconds) ||
  candidatePi.value.capturedAtEpochSeconds <= 0 ||
  candidatePi.value.capturedAtEpochSeconds >
    admissionIdentity.qualifiedAtEpochSeconds + MAXIMUM_FUTURE_SKEW_SECONDS ||
  admissionIdentity.qualifiedAtEpochSeconds -
    candidatePi.value.capturedAtEpochSeconds >
    MAXIMUM_CAPABILITY_AGE_SECONDS ||
  admissionIdentityKeys
    .filter((key) => key.endsWith("Sha256"))
    .some((key) => !SHA256.test(admissionIdentity?.[key] ?? "")) ||
  admissionIdentity?.tairaCanaryReceiptSha256 ===
    admissionIdentity?.minamotoCanaryReceiptSha256 ||
  admissionIdentity?.sora2NetworkRevision !== SORA2_REVISION ||
  admissionIdentity?.runtimeSpecVersion !== 130 ||
  admissionIdentity?.runtimeTransactionVersion !== 130 ||
  admissionIdentity?.bindingSha256 !== admissionBinding ||
  !SHA256.test(admissionIdentity?.bindingSha256 ?? "") ||
  admissionIdentity?.productionRolloutTrustSha256 !== trust.sha256 ||
  candidateProvenance === null ||
  currentPiSnapshot === null ||
  candidatePiSnapshot === null ||
  trust.sha256 !== trustPin ||
  !exactKeys(trustValue, [
    "schemaVersion",
    "contractId",
    "platform",
    "assessedAt",
    "status",
    "signatureAlgorithm",
    "telemetrySourceId",
    "completenessPolicySha256",
    "authorities",
    "blockingReasons",
  ]) ||
  trustValue.status !== "qualified" ||
  !Array.isArray(trustValue.blockingReasons) ||
  trustValue.blockingReasons.length !== 0 ||
  trustValue.authorities?.telemetryCollector?.enabled !== true ||
  trustValue.authorities?.releaseAuthorizer?.enabled !== true
) {
  fail("ROLLOUT_CONTROLLER_REQUEST_ADMISSION_NOT_BOUND");
}

const request = {
  schemaVersion:
    ANDROID_PRODUCTION_ROLLOUT_CONTROLLER_REQUEST_V3.schemaVersion,
  contractId: ANDROID_PRODUCTION_ROLLOUT_CONTROLLER_REQUEST_V3.contractId,
  platform: "android",
  targetCohortPercent: Number(targetRaw),
  evaluatedAtEpochSeconds: Number(evaluatedAtRaw),
  sourceRevision,
  candidate: {
    sha256: candidate.sha256,
    bytes: candidate.bytes,
  },
  candidateQualification: candidateProvenance,
  productionAdmission: {
    receiptSha256: admission.sha256,
    bindingSha256: admissionIdentity.bindingSha256,
    tairaCanaryReceiptSha256: admissionIdentity.tairaCanaryReceiptSha256,
    minamotoCanaryReceiptSha256: admissionIdentity.minamotoCanaryReceiptSha256,
    receipt: admission.value,
  },
  candidatePiProbe: {
    receiptSha256: candidatePi.sha256,
    signatureReceiptSha256: candidatePiSignature.sha256,
    capabilityBindingSha256:
      candidatePiSnapshot.capabilityBindingSha256,
    candidateBindingSha256: candidatePiSnapshot.bindingSha256,
    receipt: candidatePi.value,
    signatureReceipt: candidatePiSignature.value,
  },
  piProbe: {
    receiptSha256: pi.sha256,
    capabilityBindingSha256: currentPiSnapshot.bindingSha256,
    receipt: pi.value,
  },
  rolloutTrustSha256: trust.sha256,
  privacy: {
    accountIdentifiersIncluded: false,
    addressesIncluded: false,
    transactionIdentifiersIncluded: false,
    secretsIncluded: false,
  },
};
if (!isProductionRolloutControllerRequestV3Envelope(request)) {
  fail("ROLLOUT_CONTROLLER_REQUEST_ENVELOPE_INVALID");
}
process.stdout.write(`${JSON.stringify(request, null, 2)}\n`);
