#!/usr/bin/env node

import {
  createHash,
  createPublicKey,
  verify as verifySignature,
} from "node:crypto";
import { isAbsolute, resolve } from "node:path";
import { isDeepStrictEqual } from "node:util";
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
  validateProductionPiReceipt,
} from "./lib/production-pi-receipt.mjs";
import {
  isProductionAdmissionV3Envelope,
  isProductionRolloutCurrentV3Envelope,
  isProductionRolloutPriorV3Envelope,
  productionAdmissionV3ProjectionPrefix,
} from "./lib/production-rollout-v3-contract.mjs";
import { verifyTairaDeploymentManifestV1 } from "./lib/taira-deployment-manifest-v1.mjs";

const root = resolve(new URL("..", import.meta.url).pathname);
const templatePath = resolve(
  root,
  "docs/modernization/qualification/production-rollout-advancement.blocked.json",
);
const candidateTemplatePath = resolve(
  root,
  "docs/modernization/qualification/production-rollout-candidate.blocked.json",
);
const trustPath = resolve(root, "config/production-rollout-trust.json");
const trustSha256PinRaw =
  process.env.PRODUCTION_ROLLOUT_TRUST_SHA256 ?? "";
const evidencePathRaw = process.env.PRODUCTION_ROLLOUT_EVIDENCE_PATH ?? "";
const targetRaw = process.env.PRODUCTION_ROLLOUT_TARGET_PERCENT ?? "";
const candidateArtifactPathRaw =
  process.env.PRODUCTION_CANDIDATE_AAB_PATH ?? "";
const capabilitySnapshotPathRaw = process.env.PI_PRODUCTION_PROBE_RECEIPT ?? "";
const productionQualificationPathRaw =
  process.env.PRODUCTION_QUALIFICATION_RECEIPT_PATH ?? "";
const candidatePiReceiptPathRaw =
  process.env.PRODUCTION_CANDIDATE_PI_RECEIPT_PATH ?? "";
const priorRolloutReceiptPaths = new Map([
  [1, process.env.PRODUCTION_ROLLOUT_RECEIPT_1_PATH ?? ""],
  [5, process.env.PRODUCTION_ROLLOUT_RECEIPT_5_PATH ?? ""],
  [25, process.env.PRODUCTION_ROLLOUT_RECEIPT_25_PATH ?? ""],
]);
const candidateSourceRevisionRaw =
  process.env.PRODUCTION_CANDIDATE_SOURCE_REVISION ?? "";
const candidateRepositoryRaw =
  process.env.PRODUCTION_CANDIDATE_REPOSITORY ?? "";
const candidateRunIdRaw = process.env.PRODUCTION_CANDIDATE_RUN_ID ?? "";
const candidateRunReceiptPathRaw =
  process.env.PRODUCTION_CANDIDATE_RUN_RECEIPT_PATH ?? "";
const candidateArtifactReceiptPathRaw =
  process.env.PRODUCTION_CANDIDATE_ARTIFACT_RECEIPT_PATH ?? "";
const evaluationEpochRaw =
  process.env.PRODUCTION_ROLLOUT_EVALUATED_AT_EPOCH_SECONDS ?? "";
const evaluationEpoch = /^[1-9][0-9]{0,9}$/.test(evaluationEpochRaw)
  ? Number(evaluationEpochRaw)
  : null;
const tairaDeploymentAdmissionPathRaw =
  process.env.TAIRA_DEPLOYMENT_ADMISSION_RECEIPT_PATH ?? "";
const runtimeMetadataPath = resolve(
  root,
  "common/src/production/assets/sora2_metadata",
);
const MAXIMUM_RECEIPT_BYTES = 64 * 1024;
const MAXIMUM_CANDIDATE_AAB_BYTES = 512 * 1024 * 1024;
const MAXIMUM_RUNTIME_METADATA_BYTES = 2 * 1024 * 1024;
const MAXIMUM_CAPABILITY_AGE_SECONDS = 5 * 60;
const MAXIMUM_FUTURE_SKEW_SECONDS = 30;
const MAXIMUM_CHECKPOINT_LAG_BLOCKS = 32;
// Keeps the later `failures * 100` comparison inside Number.MAX_SAFE_INTEGER.
const MAXIMUM_AGGREGATE_COUNT = 90_000_000_000_000;
const MINIMUM_DWELL_SECONDS = 48 * 60 * 60;
const COHORT_SEQUENCE = [1, 5, 25, 100];
const SORA2_REVISION = "411dcdb70c5c00b21482a44d02334840d5f338c6";
const SHA256 = /^[0-9a-f]{64}$/;
const MAXIMUM_TAIRA_DEPLOYMENT_AGE_SECONDS = 7 * 24 * 60 * 60;

const failures = [];
const fail = (code) => failures.push(code);
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
const isBoundedInteger = (value, maximum = MAXIMUM_AGGREGATE_COUNT) =>
  Number.isSafeInteger(value) && value >= 0 && value <= maximum;
const containsSensitiveField = containsPrivacySensitiveField;
const privacyIsAggregateOnly = (privacy) =>
  exactKeys(privacy, [
    "aggregateOnly",
    "accountIdentifiersIncluded",
    "addressesIncluded",
    "transactionIdentifiersIncluded",
    "phrasesOrSeedsIncluded",
    "privateKeysIncluded",
    "rawSignedPayloadsIncluded",
    "perWalletRecordsIncluded",
  ]) &&
  privacy.aggregateOnly === true &&
  privacy.accountIdentifiersIncluded === false &&
  privacy.addressesIncluded === false &&
  privacy.transactionIdentifiersIncluded === false &&
  privacy.phrasesOrSeedsIncluded === false &&
  privacy.privateKeysIncluded === false &&
  privacy.rawSignedPayloadsIncluded === false &&
  privacy.perWalletRecordsIncluded === false;
const rolloutCheckpointKeys = [
  "piReceiptSha256",
  "capabilitySnapshotSha256",
  "sora2GenesisHash",
  "finalizedCheckpoint",
  "finalizedCheckpointBlockHash",
  "indexedCheckpoint",
  "capabilityFinalizedCheckpoint",
  "capabilityFinalizedCheckpointBlockHash",
  "capabilityIndexedCheckpoint",
  "minamotoChainId",
  "minamotoGenesisHash",
  "minamotoFinalizedCheckpoint",
  "minamotoFinalizedBlockHash",
  "tairaChainId",
  "tairaGenesisHash",
  "tairaFinalizedCheckpoint",
  "tairaFinalizedBlockHash",
  "nexusObservedAtEpochSeconds",
];

const canonicalDigest = (lines) =>
  createHash("sha256").update(lines.join("\n"), "utf8").digest("hex");
const canonicalSignature = (value) => {
  if (
    typeof value !== "string" ||
    !/^[A-Za-z0-9+/]{86}==$/.test(value)
  ) {
    return null;
  }
  const bytes = Buffer.from(value, "base64");
  return bytes.length === 64 && bytes.toString("base64") === value
    ? bytes
    : null;
};
const canonicalSignatureSha256 = (value) => {
  const bytes = canonicalSignature(value);
  return bytes === null
    ? "INVALID"
    : createHash("sha256").update(bytes).digest("hex");
};
const trustRecord = readStrictJsonFile(trustPath, MAXIMUM_RECEIPT_BYTES);
const trust = trustRecord?.value;
const trustAuthorityNames = ["telemetryCollector", "releaseAuthorizer"];
const trustAuthorityRoles = {
  telemetryCollector: "privacy-aggregate-telemetry-collector",
  releaseAuthorizer: "production-rollout-authorizer",
};
const trustKeys = {};
let trustContractValid = false;
let trustEnabled = false;
const trustSchemaValid =
  exactKeys(trust, [
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
  ]) &&
  trust.schemaVersion === 1 &&
  trust.contractId === "sora-android-production-rollout-trust-v1" &&
  trust.platform === "android" &&
  /^\d{4}-\d{2}-\d{2}$/.test(trust.assessedAt ?? "") &&
  ["blocked", "qualified"].includes(trust.status) &&
  trust.signatureAlgorithm === "Ed25519" &&
  exactKeys(trust.authorities, trustAuthorityNames) &&
  trustAuthorityNames.every((name) => {
    const authority = trust.authorities[name];
    return (
      exactKeys(authority, [
        "role",
        "keyId",
        "publicKeyPem",
        "publicKeySha256",
        "enabled",
      ]) &&
      authority.role === trustAuthorityRoles[name] &&
      typeof authority.enabled === "boolean"
    );
  }) &&
  Array.isArray(trust.blockingReasons) &&
  trust.blockingReasons.length <= 16 &&
  trust.blockingReasons.every(
    (reason) =>
      typeof reason === "string" &&
      [...reason].length > 0 &&
      [...reason].length <= 240 &&
      !/[\u0000-\u001f\u007f-\u009f]/u.test(reason),
  );
if (trustSchemaValid) {
  if (trust.status === "blocked") {
    trustContractValid =
      trust.telemetrySourceId === null &&
      trust.completenessPolicySha256 === null &&
      trust.blockingReasons.length > 0 &&
      trustAuthorityNames.every((name) => {
        const authority = trust.authorities[name];
        return (
          authority.enabled === false &&
          authority.keyId === null &&
          authority.publicKeyPem === null &&
          authority.publicKeySha256 === null
        );
      });
  } else {
    try {
      trustEnabled =
        typeof trust.telemetrySourceId === "string" &&
        /^[a-z0-9][a-z0-9._-]{2,127}$/.test(trust.telemetrySourceId) &&
        SHA256.test(trust.completenessPolicySha256 ?? "") &&
        trust.blockingReasons.length === 0 &&
        trustAuthorityNames.every((name) => {
          const authority = trust.authorities[name];
          if (
            authority.enabled !== true ||
            typeof authority.keyId !== "string" ||
            !/^[a-z0-9][a-z0-9._-]{2,63}$/.test(authority.keyId) ||
            typeof authority.publicKeyPem !== "string" ||
            authority.publicKeyPem.length > 2_048 ||
            !SHA256.test(authority.publicKeySha256 ?? "")
          ) {
            return false;
          }
          const publicKey = createPublicKey(authority.publicKeyPem);
          const spki = publicKey.export({ type: "spki", format: "der" });
          if (
            publicKey.asymmetricKeyType !== "ed25519" ||
            createHash("sha256").update(spki).digest("hex") !==
              authority.publicKeySha256
          ) {
            return false;
          }
          trustKeys[name] = publicKey;
          return true;
        }) &&
        new Set(
          trustAuthorityNames.map((name) => trust.authorities[name].keyId),
        ).size === trustAuthorityNames.length &&
        new Set(
          trustAuthorityNames.map(
            (name) => trust.authorities[name].publicKeySha256,
          ),
        ).size === trustAuthorityNames.length;
      trustContractValid = trustEnabled;
    } catch {
      trustEnabled = false;
      trustContractValid = false;
    }
  }
}
if (!trustSchemaValid || !trustContractValid) {
  fail("ROLLOUT_TRUST_CONTRACT_INVALID");
}
if (!trustEnabled) {
  fail("ROLLOUT_TRUST_ROOT_NOT_QUALIFIED");
}
if (
  !SHA256.test(trustSha256PinRaw) ||
  trustSha256PinRaw !== trustRecord?.sha256
) {
  fail("ROLLOUT_TRUST_ROOT_PROTECTED_PIN_MISSING_OR_MISMATCHED");
}
const signatureQualified = (authorityName, projection, signature) => {
  const signatureBytes = canonicalSignature(signature);
  if (!trustEnabled || signatureBytes === null) return false;
  try {
    return verifySignature(
      null,
      Buffer.from(projection.join("\n"), "utf8"),
      trustKeys[authorityName],
      signatureBytes,
    );
  } catch {
    return false;
  }
};

const validateLiveCapabilitySnapshot = () => {
  if (
    capabilitySnapshotPathRaw.length === 0 ||
    !isAbsolute(capabilitySnapshotPathRaw) ||
    resolve(capabilitySnapshotPathRaw) !== capabilitySnapshotPathRaw
  ) {
    fail("ROLLOUT_LIVE_CAPABILITY_SNAPSHOT_PATH_MISSING_OR_INVALID");
    return null;
  }
  const record = readStrictJsonFile(
    capabilitySnapshotPathRaw,
    MAXIMUM_RECEIPT_BYTES,
  );
  if (record === null) {
    fail("ROLLOUT_LIVE_CAPABILITY_SNAPSHOT_MISSING_OR_INVALID");
    return null;
  }
  const snapshot = validateProductionPiReceipt(record.value, evaluationEpoch);
  if (snapshot === null) {
    fail("ROLLOUT_LIVE_CAPABILITY_SNAPSHOT_NOT_QUALIFIED");
    return null;
  }
  return {
    ...snapshot,
    receiptSha256: record.sha256,
  };
};

const hashReviewedRuntimeMetadata = () => {
  const artifact = hashStableRegularFile(
    runtimeMetadataPath,
    MAXIMUM_RUNTIME_METADATA_BYTES,
  );
  if (artifact === null) {
    fail("ROLLOUT_RUNTIME_METADATA_MISSING_OR_UNBOUNDED");
    return null;
  }
  return artifact;
};

const validateBlockedTemplate = ({
  path,
  expectedFrom,
  expectedTarget,
  expectedWarning,
  failurePrefix,
}) => {
  const templateRecord = readStrictJsonFile(path, MAXIMUM_RECEIPT_BYTES);
  if (templateRecord === null) {
    fail(`${failurePrefix}_MISSING_OR_INVALID`);
    return;
  }
  const template = templateRecord.value;
  if (
    !exactKeys(template, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "fromCohortPercent",
      "targetCohortPercent",
      "evaluatedAtEpochSeconds",
      "priorGateReceiptSha256",
      "identity",
      "checkpoint",
      "privacy",
      "cohorts",
      "telemetryAttestation",
      "authorization",
      "blockingReasons",
    ]) ||
    !isProductionRolloutCurrentV3Envelope(template) ||
    template.status !== "blocked-template" ||
    template.platform !== "android" ||
    template.fromCohortPercent !== expectedFrom ||
    template.targetCohortPercent !== expectedTarget ||
    template.evaluatedAtEpochSeconds !== 0 ||
    template.priorGateReceiptSha256 !== null ||
    !Array.isArray(template.cohorts) ||
    template.cohorts.length !== 0 ||
    !Array.isArray(template.blockingReasons) ||
    template.blockingReasons.length !== 1 ||
    template.blockingReasons[0] !== expectedWarning ||
    !privacyIsAggregateOnly(template.privacy) ||
    !exactKeys(template.identity, [
      "bindingSha256",
      "candidateArtifactSha256",
      "sora2NetworkRevision",
      "runtimeSpecVersion",
      "runtimeTransactionVersion",
      "runtimeMetadataSha256",
      "productionQualificationReceiptSha256",
      "productionAdmissionBindingSha256",
      "tairaCanaryReceiptSha256",
      "minamotoCanaryReceiptSha256",
      "productionRolloutTrustSha256",
    ]) ||
    template.identity.bindingSha256 !== "UNAVAILABLE" ||
    template.identity.candidateArtifactSha256 !== "UNAVAILABLE" ||
    template.identity.runtimeMetadataSha256 !== "UNAVAILABLE" ||
    template.identity.productionQualificationReceiptSha256 !==
      "UNAVAILABLE" ||
    template.identity.productionAdmissionBindingSha256 !== "UNAVAILABLE" ||
    template.identity.tairaCanaryReceiptSha256 !== "UNAVAILABLE" ||
    template.identity.minamotoCanaryReceiptSha256 !== "UNAVAILABLE" ||
    template.identity.productionRolloutTrustSha256 !== "UNAVAILABLE" ||
    !exactKeys(template.checkpoint, rolloutCheckpointKeys) ||
    template.checkpoint.sora2GenesisHash !==
      "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5" ||
    template.checkpoint.minamotoChainId !==
      "00000000-0000-0000-0000-000000000753" ||
    template.checkpoint.tairaChainId !==
      "fc56984b-2be7-431d-840e-21514d1883f0" ||
    rolloutCheckpointKeys
      .filter(
        (key) =>
          !["sora2GenesisHash", "minamotoChainId", "tairaChainId"].includes(
            key,
          ),
      )
      .some((key) => template.checkpoint[key] !== null) ||
    !exactKeys(template.telemetryAttestation, [
      "telemetrySourceId",
      "completenessPolicySha256",
      "observedThroughEpochSeconds",
      "collectorKeyId",
      "collectorSignatureBase64",
    ]) ||
    Object.values(template.telemetryAttestation).some(
      (value) => value !== null,
    ) ||
    !exactKeys(template.authorization, [
      "telemetryBindingSha256",
      "collectorSignatureSha256",
      "authorizedTargetPercent",
      "authorizedAtEpochSeconds",
      "releaseAuthorizerKeyId",
      "releaseAuthorizerSignatureBase64",
    ]) ||
    Object.values(template.authorization).some((value) => value !== null) ||
    template.identity.sora2NetworkRevision !== SORA2_REVISION ||
    template.identity.runtimeSpecVersion !== 130 ||
    template.identity.runtimeTransactionVersion !== 130
  ) {
    fail(`${failurePrefix}_NOT_FAIL_CLOSED`);
  }
};

const hashCandidateArtifact = () => {
  if (
    candidateArtifactPathRaw.length === 0 ||
    !isAbsolute(candidateArtifactPathRaw) ||
    resolve(candidateArtifactPathRaw) !== candidateArtifactPathRaw ||
    !candidateArtifactPathRaw.toLowerCase().endsWith(".aab")
  ) {
    fail("ROLLOUT_CANDIDATE_AAB_PATH_MISSING_OR_INVALID");
    return null;
  }

  const artifact = hashStableRegularFile(
    candidateArtifactPathRaw,
    MAXIMUM_CANDIDATE_AAB_BYTES,
  );
  if (artifact === null) {
    fail("ROLLOUT_CANDIDATE_AAB_NOT_STABLE_REGULAR_OR_BOUNDED");
    return null;
  }
  return artifact;
};

const validateTairaDeployment = (expectedManifestSha256) => {
  if (
    !SHA256.test(expectedManifestSha256 ?? "") ||
    !isAbsolute(tairaDeploymentAdmissionPathRaw) ||
    resolve(tairaDeploymentAdmissionPathRaw) !==
      tairaDeploymentAdmissionPathRaw
  ) {
    fail("ROLLOUT_TAIRA_DEPLOYMENT_ADMISSION_PATH_OR_IDENTITY_INVALID");
    return null;
  }
  const admissionRecord = readStrictJsonFile(
    tairaDeploymentAdmissionPathRaw,
    MAXIMUM_RECEIPT_BYTES,
  );
  const admittedAt = admissionRecord?.value?.evaluationEpochSeconds;
  if (
    admissionRecord === null ||
    !isBoundedInteger(admittedAt, 9_999_999_999) ||
    admittedAt === 0 ||
    admittedAt > evaluationEpoch ||
    evaluationEpoch - admittedAt > MAXIMUM_TAIRA_DEPLOYMENT_AGE_SECONDS
  ) {
    fail("ROLLOUT_TAIRA_DEPLOYMENT_ADMISSION_STALE_OR_INVALID");
    return null;
  }
  let verified;
  try {
    verified = verifyTairaDeploymentManifestV1({
      manifestPath: process.env.TAIRA_DEPLOYMENT_MANIFEST_PATH ?? "",
      operatorSignaturePath:
        process.env.TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_PATH ?? "",
      reviewerSignaturePath:
        process.env.TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_PATH ?? "",
      operatorPublicKeyPath:
        process.env.TAIRA_DEPLOYMENT_OPERATOR_PUBLIC_KEY_PATH ?? "",
      reviewerPublicKeyPath:
        process.env.TAIRA_DEPLOYMENT_REVIEWER_PUBLIC_KEY_PATH ?? "",
      expectedOperatorKeySha256:
        process.env.TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256 ?? "",
      expectedReviewerKeySha256:
        process.env.TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256 ?? "",
      evaluationEpochSeconds: admittedAt,
    });
  } catch {
    fail("ROLLOUT_TAIRA_DEPLOYMENT_SIGNATURE_OR_MANIFEST_INVALID");
    return null;
  }
  if (
    JSON.stringify(verified) !== JSON.stringify(admissionRecord.value) ||
    verified.manifestSha256 !== expectedManifestSha256
  ) {
    fail("ROLLOUT_TAIRA_DEPLOYMENT_ADMISSION_DIVERGED");
    return null;
  }
  return verified;
};

const productionAdmissionIdentityKeys =
  PRODUCTION_ADMISSION_IDENTITY_KEYS;
const validateProductionAdmission = (
  candidateArtifact,
  runtimeMetadata,
) => {
  if (
    productionQualificationPathRaw.length === 0 ||
    !isAbsolute(productionQualificationPathRaw) ||
    resolve(productionQualificationPathRaw) !== productionQualificationPathRaw
  ) {
    fail("ROLLOUT_PRODUCTION_ADMISSION_PATH_MISSING_OR_INVALID");
    return null;
  }
  const record = readStrictJsonFile(
    productionQualificationPathRaw,
    MAXIMUM_RECEIPT_BYTES,
  );
  if (record === null) {
    fail("ROLLOUT_PRODUCTION_ADMISSION_MISSING_OR_INVALID");
    return null;
  }
  if (
    candidatePiReceiptPathRaw.length === 0 ||
    !isAbsolute(candidatePiReceiptPathRaw) ||
    resolve(candidatePiReceiptPathRaw) !== candidatePiReceiptPathRaw
  ) {
    fail("ROLLOUT_CANDIDATE_PI_RECEIPT_PATH_MISSING_OR_INVALID");
    return null;
  }
  const candidatePiRecord = readStrictJsonFile(
    candidatePiReceiptPathRaw,
    MAXIMUM_RECEIPT_BYTES,
  );
  if (candidatePiRecord === null) {
    fail("ROLLOUT_CANDIDATE_PI_RECEIPT_MISSING_OR_INVALID");
    return null;
  }
  const result = record.value;
  const admission = result?.admission;
  const identity = admission?.identity;
  const projection = exactKeys(identity, [
    ...productionAdmissionIdentityKeys,
    "bindingSha256",
  ])
    ? [
        ...productionAdmissionV3ProjectionPrefix(),
        ...productionAdmissionIdentityKeys.map(
          (key) => `${key}=${identity[key]}`,
        ),
      ]
    : [];
  if (
    !exactKeys(result, ["mode", "failures", "releaseBlockers", "admission"]) ||
    result.mode !== "release" ||
    !Array.isArray(result.failures) ||
    result.failures.length !== 0 ||
    !Array.isArray(result.releaseBlockers) ||
    result.releaseBlockers.length !== 0 ||
    !exactKeys(admission, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "identity",
    ]) ||
    !isProductionAdmissionV3Envelope(admission) ||
    admission.status !== "qualified" ||
    admission.platform !== "android" ||
    projection.length === 0 ||
    identity.candidateAabSha256 !== candidateArtifact.sha256 ||
    identity.candidateAabBytes !== candidateArtifact.bytes ||
    identity.sourceRevision !== candidateSourceRevisionRaw ||
    !/^[0-9a-f]{40}$/.test(identity.sourceRevision ?? "") ||
    !isBoundedInteger(identity.qualifiedAtEpochSeconds, 9_999_999_999) ||
    identity.qualifiedAtEpochSeconds === 0 ||
    identity.qualifiedAtEpochSeconds > evaluationEpoch ||
    identity.candidatePiProbeReceiptSha256 !== candidatePiRecord.sha256 ||
    validateProductionPiReceipt(
      candidatePiRecord.value,
      identity.qualifiedAtEpochSeconds,
    ) === null ||
    !isBoundedInteger(
      candidatePiRecord.value?.checkedAtEpochSeconds,
      9_999_999_999,
    ) ||
    candidatePiRecord.value.checkedAtEpochSeconds === 0 ||
    candidatePiRecord.value.checkedAtEpochSeconds >
      identity.qualifiedAtEpochSeconds + MAXIMUM_FUTURE_SKEW_SECONDS ||
    identity.qualifiedAtEpochSeconds -
      candidatePiRecord.value.checkedAtEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    identity.sora2NetworkRevision !== SORA2_REVISION ||
    identity.runtimeSpecVersion !== 130 ||
    identity.runtimeTransactionVersion !== 130 ||
    identity.runtimeMetadataSha256 !== runtimeMetadata.sha256 ||
    productionAdmissionIdentityKeys
      .filter((key) => key.endsWith("Sha256"))
      .some((key) => !SHA256.test(identity[key] ?? "")) ||
    identity.tairaCanaryReceiptSha256 ===
      identity.minamotoCanaryReceiptSha256 ||
    identity.productionRolloutTrustSha256 !== trustRecord?.sha256 ||
    identity.bindingSha256 !== canonicalDigest(projection)
  ) {
    fail("ROLLOUT_PRODUCTION_ADMISSION_NOT_QUALIFIED");
    return null;
  }
  const tairaDeployment = validateTairaDeployment(
    identity.tairaDeploymentManifestSha256,
  );
  if (tairaDeployment === null) {
    return null;
  }
  const provenance = validateQualifiedCandidateProvenance({
    root,
    admissionIdentity: identity,
    expectedRepository: candidateRepositoryRaw,
    expectedSourceRevision: candidateSourceRevisionRaw,
    candidateRunIdRaw,
    candidateRunReceiptPath: candidateRunReceiptPathRaw,
    candidateArtifactReceiptPath: candidateArtifactReceiptPathRaw,
  });
  if (provenance === null) {
    fail("ROLLOUT_QUALIFIED_CANDIDATE_PROVENANCE_INVALID");
    return null;
  }
  return {
    receiptSha256: record.sha256,
    bindingSha256: identity.bindingSha256,
    tairaCanaryReceiptSha256: identity.tairaCanaryReceiptSha256,
    minamotoCanaryReceiptSha256: identity.minamotoCanaryReceiptSha256,
    tairaDeployment,
    provenance,
  };
};

const identityBinding = (identity) =>
  createHash("sha256")
    .update(
      [
        "platform=android",
        `candidateArtifactSha256=${identity.candidateArtifactSha256}`,
        `sora2NetworkRevision=${identity.sora2NetworkRevision}`,
        `runtimeSpecVersion=${identity.runtimeSpecVersion}`,
        `runtimeTransactionVersion=${identity.runtimeTransactionVersion}`,
        `runtimeMetadataSha256=${identity.runtimeMetadataSha256}`,
        `productionQualificationReceiptSha256=${identity.productionQualificationReceiptSha256}`,
        `productionAdmissionBindingSha256=${identity.productionAdmissionBindingSha256}`,
        `tairaCanaryReceiptSha256=${identity.tairaCanaryReceiptSha256}`,
        `minamotoCanaryReceiptSha256=${identity.minamotoCanaryReceiptSha256}`,
        `productionRolloutTrustSha256=${identity.productionRolloutTrustSha256}`,
      ].join("\n"),
      "utf8",
    )
    .digest("hex");

const validateIdentity = (
  identity,
  candidateArtifactSha256,
  runtimeMetadataSha256,
  productionAdmission,
) =>
  exactKeys(identity, [
    "bindingSha256",
    "candidateArtifactSha256",
    "sora2NetworkRevision",
    "runtimeSpecVersion",
    "runtimeTransactionVersion",
    "runtimeMetadataSha256",
    "productionQualificationReceiptSha256",
    "productionAdmissionBindingSha256",
    "tairaCanaryReceiptSha256",
    "minamotoCanaryReceiptSha256",
    "productionRolloutTrustSha256",
  ]) &&
  typeof identity.bindingSha256 === "string" &&
  SHA256.test(identity.bindingSha256) &&
  typeof identity.candidateArtifactSha256 === "string" &&
  SHA256.test(identity.candidateArtifactSha256) &&
  identity.candidateArtifactSha256 === candidateArtifactSha256 &&
  identity.sora2NetworkRevision === SORA2_REVISION &&
  identity.runtimeSpecVersion === 130 &&
  identity.runtimeTransactionVersion === 130 &&
  typeof identity.runtimeMetadataSha256 === "string" &&
  SHA256.test(identity.runtimeMetadataSha256) &&
  identity.runtimeMetadataSha256 === runtimeMetadataSha256 &&
  SHA256.test(identity.productionQualificationReceiptSha256 ?? "") &&
  identity.productionQualificationReceiptSha256 ===
    productionAdmission.receiptSha256 &&
  SHA256.test(identity.productionAdmissionBindingSha256 ?? "") &&
  identity.productionAdmissionBindingSha256 ===
    productionAdmission.bindingSha256 &&
  SHA256.test(identity.tairaCanaryReceiptSha256 ?? "") &&
  identity.tairaCanaryReceiptSha256 ===
    productionAdmission.tairaCanaryReceiptSha256 &&
  SHA256.test(identity.minamotoCanaryReceiptSha256 ?? "") &&
  identity.minamotoCanaryReceiptSha256 ===
    productionAdmission.minamotoCanaryReceiptSha256 &&
  identity.tairaCanaryReceiptSha256 !==
    identity.minamotoCanaryReceiptSha256 &&
  SHA256.test(identity.productionRolloutTrustSha256 ?? "") &&
  identity.productionRolloutTrustSha256 === trustRecord?.sha256 &&
  identity.bindingSha256 === identityBinding(identity);

const rolloutMetricCountKeys = [
  "upgradedWalletsObserved",
  "accountsObserved",
  "confirmedMissingWalletEvents",
  "confirmedMissingAccountEvents",
  "addressMismatchEvents",
  "signatureMismatchEvents",
  "crossNetworkRoutingEvents",
  "terminalTransactionsObserved",
  "excludedUserCancellationEvents",
  "excludedInsufficientFundsEvents",
  "eligibleTerminalSuccessEvents",
  "eligibleTerminalFailureEvents",
];
const validateMetrics = (metrics) => {
  if (
    !exactKeys(metrics, [
      ...rolloutMetricCountKeys,
      "telemetryCompletenessQualified",
    ]) ||
    !rolloutMetricCountKeys.every((key) => isBoundedInteger(metrics[key])) ||
    metrics.telemetryCompletenessQualified !== true ||
    metrics.upgradedWalletsObserved === 0 ||
    metrics.accountsObserved === 0 ||
    metrics.accountsObserved < metrics.upgradedWalletsObserved ||
    metrics.terminalTransactionsObserved === 0
  ) {
    return false;
  }
  if (
    metrics.confirmedMissingWalletEvents !== 0 ||
    metrics.confirmedMissingAccountEvents !== 0 ||
    metrics.addressMismatchEvents !== 0 ||
    metrics.signatureMismatchEvents !== 0 ||
    metrics.crossNetworkRoutingEvents !== 0
  ) {
    return false;
  }
  const excluded =
    metrics.excludedUserCancellationEvents +
    metrics.excludedInsufficientFundsEvents;
  if (excluded > metrics.terminalTransactionsObserved) return false;
  const eligible = metrics.terminalTransactionsObserved - excluded;
  if (
    eligible === 0 ||
    metrics.eligibleTerminalSuccessEvents +
      metrics.eligibleTerminalFailureEvents !==
      eligible
  ) {
    return false;
  }
  // The release plan halts only when the eligible rate is strictly above 1%.
  return metrics.eligibleTerminalFailureEvents * 100 <= eligible;
};

const rolloutIdentityProjectionKeys = [
  "bindingSha256",
  "candidateArtifactSha256",
  "sora2NetworkRevision",
  "runtimeSpecVersion",
  "runtimeTransactionVersion",
  "runtimeMetadataSha256",
  "productionQualificationReceiptSha256",
  "productionAdmissionBindingSha256",
  "tairaCanaryReceiptSha256",
  "minamotoCanaryReceiptSha256",
  "productionRolloutTrustSha256",
];
const rolloutPrivacyProjectionKeys = [
  "aggregateOnly",
  "accountIdentifiersIncluded",
  "addressesIncluded",
  "transactionIdentifiersIncluded",
  "phrasesOrSeedsIncluded",
  "privateKeysIncluded",
  "rawSignedPayloadsIncluded",
  "perWalletRecordsIncluded",
];
const distributionProjectionFor = (cohort) => {
  const distribution = cohort.distributionAttestation;
  return [
    "contractId=sora-android-production-rollout-distribution-v2",
    "platform=android",
    `provider=${distribution.provider}`,
    `track=${distribution.track}`,
    `applicationId=${distribution.applicationId}`,
    `providerReleaseId=${distribution.providerReleaseId}`,
    `providerReceiptSha256=${distribution.providerReceiptSha256}`,
    `providerObservedAtEpochSeconds=${distribution.providerObservedAtEpochSeconds}`,
    `candidateArtifactSha256=${distribution.candidateArtifactSha256}`,
    `identityBindingSha256=${distribution.identityBindingSha256}`,
    `cohortPercent=${distribution.cohortPercent}`,
    `effectiveAtEpochSeconds=${distribution.effectiveAtEpochSeconds}`,
    `releaseAuthorizerKeyId=${distribution.releaseAuthorizerKeyId}`,
  ];
};
const distributionAttestationQualified = (cohort, identity) => {
  const distribution = cohort.distributionAttestation;
  return (
    exactKeys(distribution, [
      "provider",
      "track",
      "applicationId",
      "providerReleaseId",
      "providerReceiptSha256",
      "providerObservedAtEpochSeconds",
      "candidateArtifactSha256",
      "identityBindingSha256",
      "cohortPercent",
      "effectiveAtEpochSeconds",
      "releaseAuthorizerKeyId",
      "releaseAuthorizerSignatureBase64",
    ]) &&
    distribution.provider === "google-play" &&
    distribution.track === "production" &&
    distribution.applicationId === "jp.co.soramitsu.sora" &&
    typeof distribution.providerReleaseId === "string" &&
    /^[A-Za-z0-9][A-Za-z0-9._:-]{0,255}$/.test(
      distribution.providerReleaseId,
    ) &&
    SHA256.test(distribution.providerReceiptSha256 ?? "") &&
    !/^0{64}$/.test(distribution.providerReceiptSha256) &&
    isBoundedInteger(
      distribution.providerObservedAtEpochSeconds,
      9_999_999_999,
    ) &&
    distribution.providerObservedAtEpochSeconds >=
      cohort.startedAtEpochSeconds &&
    distribution.providerObservedAtEpochSeconds <= cohort.endedAtEpochSeconds &&
    distribution.candidateArtifactSha256 ===
      identity.candidateArtifactSha256 &&
    distribution.identityBindingSha256 === identity.bindingSha256 &&
    distribution.cohortPercent === cohort.cohortPercent &&
    distribution.effectiveAtEpochSeconds === cohort.startedAtEpochSeconds &&
    distribution.releaseAuthorizerKeyId ===
      trust?.authorities?.releaseAuthorizer?.keyId &&
    signatureQualified(
      "releaseAuthorizer",
      distributionProjectionFor(cohort),
      distribution.releaseAuthorizerSignatureBase64,
    )
  );
};
const telemetryProjectionFor = (evidence) => [
  "contractId=sora-android-production-rollout-telemetry-v1",
  `schemaVersion=${evidence.schemaVersion}`,
  `status=${evidence.status}`,
  `platform=${evidence.platform}`,
  `fromCohortPercent=${evidence.fromCohortPercent}`,
  `targetCohortPercent=${evidence.targetCohortPercent}`,
  `evaluatedAtEpochSeconds=${evidence.evaluatedAtEpochSeconds}`,
  `priorGateReceiptSha256=${evidence.priorGateReceiptSha256}`,
  ...rolloutIdentityProjectionKeys.map(
    (key) => `identity.${key}=${evidence.identity[key]}`,
  ),
  ...rolloutCheckpointKeys.map(
    (key) => `checkpoint.${key}=${evidence.checkpoint[key]}`,
  ),
  ...rolloutPrivacyProjectionKeys.map(
    (key) => `privacy.${key}=${evidence.privacy[key]}`,
  ),
  `cohortCount=${evidence.cohorts.length}`,
  ...evidence.cohorts.flatMap((cohort, index) => [
    `cohorts.${index}.cohortPercent=${cohort.cohortPercent}`,
    `cohorts.${index}.startedAtEpochSeconds=${cohort.startedAtEpochSeconds}`,
    `cohorts.${index}.endedAtEpochSeconds=${cohort.endedAtEpochSeconds}`,
    `cohorts.${index}.identityBindingSha256=${cohort.identityBindingSha256}`,
    ...distributionProjectionFor(cohort).map(
      (line) => `cohorts.${index}.distribution.${line}`,
    ),
    `cohorts.${index}.distribution.signatureSha256=${canonicalSignatureSha256(
      cohort.distributionAttestation.releaseAuthorizerSignatureBase64,
    )}`,
    ...rolloutMetricCountKeys.map(
      (key) =>
        `cohorts.${index}.aggregateMetrics.${key}=${cohort.aggregateMetrics[key]}`,
    ),
    `cohorts.${index}.aggregateMetrics.telemetryCompletenessQualified=${cohort.aggregateMetrics.telemetryCompletenessQualified}`,
  ]),
  `telemetrySourceId=${evidence.telemetryAttestation.telemetrySourceId}`,
  `completenessPolicySha256=${evidence.telemetryAttestation.completenessPolicySha256}`,
  `observedThroughEpochSeconds=${evidence.telemetryAttestation.observedThroughEpochSeconds}`,
  `collectorKeyId=${evidence.telemetryAttestation.collectorKeyId}`,
];
const authorizationProjectionFor = (evidence) => [
  "contractId=sora-android-production-rollout-authorization-v1",
  "platform=android",
  `targetCohortPercent=${evidence.targetCohortPercent}`,
  `evaluatedAtEpochSeconds=${evidence.evaluatedAtEpochSeconds}`,
  `identityBindingSha256=${evidence.identity.bindingSha256}`,
  `productionQualificationReceiptSha256=${evidence.identity.productionQualificationReceiptSha256}`,
  `priorGateReceiptSha256=${evidence.priorGateReceiptSha256}`,
  `telemetryBindingSha256=${evidence.authorization.telemetryBindingSha256}`,
  `collectorSignatureSha256=${evidence.authorization.collectorSignatureSha256}`,
  `authorizedAtEpochSeconds=${evidence.authorization.authorizedAtEpochSeconds}`,
  `releaseAuthorizerKeyId=${evidence.authorization.releaseAuthorizerKeyId}`,
];

const validatePriorGateReceipt = (
  currentEvidence,
  priorRolloutReceiptPathRaw,
) => {
  // v3 intentionally has no v2 compatibility path. Requiring every prior
  // receipt to be v3 forces the new cohort chain to begin again at 1%.
  if (
    priorRolloutReceiptPathRaw.length === 0 ||
    !isAbsolute(priorRolloutReceiptPathRaw) ||
    resolve(priorRolloutReceiptPathRaw) !== priorRolloutReceiptPathRaw
  ) {
    fail("ROLLOUT_PRIOR_GATE_PATH_MISSING_OR_INVALID");
    return null;
  }
  const priorRecord = readStrictJsonFile(
    priorRolloutReceiptPathRaw,
    MAXIMUM_RECEIPT_BYTES,
  );
  if (priorRecord === null) {
    fail("ROLLOUT_PRIOR_GATE_RECEIPT_MISSING_OR_INVALID");
    return null;
  }
  const prior = priorRecord.value;
  const expectedTarget = currentEvidence.fromCohortPercent;
  const expectedTargetIndex = COHORT_SEQUENCE.indexOf(expectedTarget);
  const expectedPriorCohorts = COHORT_SEQUENCE.slice(0, expectedTargetIndex);
  if (
    priorRecord.sha256 !== currentEvidence.priorGateReceiptSha256 ||
    !exactKeys(prior, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "fromCohortPercent",
      "targetCohortPercent",
      "evaluatedAtEpochSeconds",
      "priorGateReceiptSha256",
      "identity",
      "checkpoint",
      "privacy",
      "cohorts",
      "telemetryAttestation",
      "authorization",
    ]) ||
    !isProductionRolloutPriorV3Envelope(prior) ||
    prior.status !== "qualified" ||
    prior.platform !== "android" ||
    !isBoundedInteger(prior.evaluatedAtEpochSeconds, 9_999_999_999) ||
    prior.evaluatedAtEpochSeconds === 0 ||
    prior.targetCohortPercent !== expectedTarget ||
    prior.fromCohortPercent !==
      (expectedTargetIndex === 0 ? 0 : COHORT_SEQUENCE[expectedTargetIndex - 1]) ||
    prior.evaluatedAtEpochSeconds > currentEvidence.evaluatedAtEpochSeconds ||
    (expectedTarget === 1
      ? prior.priorGateReceiptSha256 !== null
      : !SHA256.test(prior.priorGateReceiptSha256 ?? "")) ||
    !exactKeys(prior.identity, rolloutIdentityProjectionKeys) ||
    rolloutIdentityProjectionKeys.some(
      (key) => prior.identity[key] !== currentEvidence.identity[key],
    ) ||
    !exactKeys(prior.checkpoint, rolloutCheckpointKeys) ||
    !SHA256.test(prior.checkpoint.piReceiptSha256 ?? "") ||
    !SHA256.test(prior.checkpoint.capabilitySnapshotSha256 ?? "") ||
    prior.checkpoint.capabilitySnapshotSha256 !==
      currentEvidence.checkpoint.capabilitySnapshotSha256 ||
    prior.checkpoint.sora2GenesisHash !==
      currentEvidence.checkpoint.sora2GenesisHash ||
    !isBoundedInteger(prior.checkpoint.finalizedCheckpoint) ||
    prior.checkpoint.finalizedCheckpoint === 0 ||
    !/^0x[0-9a-f]{64}$/.test(
      prior.checkpoint.finalizedCheckpointBlockHash ?? "",
    ) ||
    /^0x0{64}$/.test(prior.checkpoint.finalizedCheckpointBlockHash) ||
    !isBoundedInteger(prior.checkpoint.indexedCheckpoint) ||
    prior.checkpoint.indexedCheckpoint === 0 ||
    prior.checkpoint.indexedCheckpoint > prior.checkpoint.finalizedCheckpoint ||
    prior.checkpoint.finalizedCheckpoint - prior.checkpoint.indexedCheckpoint >
      MAXIMUM_CHECKPOINT_LAG_BLOCKS ||
    !isBoundedInteger(prior.checkpoint.capabilityFinalizedCheckpoint) ||
    prior.checkpoint.capabilityFinalizedCheckpoint === 0 ||
    prior.checkpoint.capabilityFinalizedCheckpoint <
      prior.checkpoint.finalizedCheckpoint ||
    !/^0x[0-9a-f]{64}$/.test(
      prior.checkpoint.capabilityFinalizedCheckpointBlockHash ?? "",
    ) ||
    /^0x0{64}$/.test(
      prior.checkpoint.capabilityFinalizedCheckpointBlockHash,
    ) ||
    !isBoundedInteger(prior.checkpoint.capabilityIndexedCheckpoint) ||
    prior.checkpoint.capabilityIndexedCheckpoint === 0 ||
    prior.checkpoint.capabilityIndexedCheckpoint <
      prior.checkpoint.indexedCheckpoint ||
    prior.checkpoint.capabilityIndexedCheckpoint >
      prior.checkpoint.capabilityFinalizedCheckpoint ||
    prior.checkpoint.capabilityFinalizedCheckpoint -
      prior.checkpoint.capabilityIndexedCheckpoint >
      MAXIMUM_CHECKPOINT_LAG_BLOCKS ||
    (prior.checkpoint.capabilityFinalizedCheckpoint ===
      prior.checkpoint.finalizedCheckpoint &&
      prior.checkpoint.capabilityFinalizedCheckpointBlockHash !==
        prior.checkpoint.finalizedCheckpointBlockHash) ||
    prior.checkpoint.minamotoChainId !==
      currentEvidence.checkpoint.minamotoChainId ||
    !SHA256.test(prior.checkpoint.minamotoGenesisHash ?? "") ||
    /^0{64}$/.test(prior.checkpoint.minamotoGenesisHash) ||
    !isBoundedInteger(prior.checkpoint.minamotoFinalizedCheckpoint) ||
    prior.checkpoint.minamotoFinalizedCheckpoint === 0 ||
    !SHA256.test(prior.checkpoint.minamotoFinalizedBlockHash ?? "") ||
    /^0{64}$/.test(prior.checkpoint.minamotoFinalizedBlockHash) ||
    prior.checkpoint.tairaChainId !== currentEvidence.checkpoint.tairaChainId ||
    !SHA256.test(prior.checkpoint.tairaGenesisHash ?? "") ||
    /^0{64}$/.test(prior.checkpoint.tairaGenesisHash) ||
    !isBoundedInteger(prior.checkpoint.tairaFinalizedCheckpoint) ||
    prior.checkpoint.tairaFinalizedCheckpoint === 0 ||
    !SHA256.test(prior.checkpoint.tairaFinalizedBlockHash ?? "") ||
    /^0{64}$/.test(prior.checkpoint.tairaFinalizedBlockHash) ||
    !isBoundedInteger(
      prior.checkpoint.nexusObservedAtEpochSeconds,
      9_999_999_999,
    ) ||
    prior.checkpoint.nexusObservedAtEpochSeconds === 0 ||
    prior.checkpoint.nexusObservedAtEpochSeconds >
      prior.evaluatedAtEpochSeconds + MAXIMUM_FUTURE_SKEW_SECONDS ||
    prior.evaluatedAtEpochSeconds -
      prior.checkpoint.nexusObservedAtEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    currentEvidence.checkpoint.finalizedCheckpoint <
      prior.checkpoint.finalizedCheckpoint ||
    currentEvidence.checkpoint.indexedCheckpoint <
      prior.checkpoint.indexedCheckpoint ||
    currentEvidence.checkpoint.capabilityFinalizedCheckpoint <
      prior.checkpoint.capabilityFinalizedCheckpoint ||
    currentEvidence.checkpoint.capabilityIndexedCheckpoint <
      prior.checkpoint.capabilityIndexedCheckpoint ||
    currentEvidence.checkpoint.minamotoGenesisHash !==
      prior.checkpoint.minamotoGenesisHash ||
    currentEvidence.checkpoint.minamotoFinalizedCheckpoint <
      prior.checkpoint.minamotoFinalizedCheckpoint ||
    (currentEvidence.checkpoint.minamotoFinalizedCheckpoint ===
      prior.checkpoint.minamotoFinalizedCheckpoint &&
      currentEvidence.checkpoint.minamotoFinalizedBlockHash !==
        prior.checkpoint.minamotoFinalizedBlockHash) ||
    currentEvidence.checkpoint.tairaGenesisHash !==
      prior.checkpoint.tairaGenesisHash ||
    currentEvidence.checkpoint.tairaFinalizedCheckpoint <
      prior.checkpoint.tairaFinalizedCheckpoint ||
    (currentEvidence.checkpoint.tairaFinalizedCheckpoint ===
      prior.checkpoint.tairaFinalizedCheckpoint &&
      currentEvidence.checkpoint.tairaFinalizedBlockHash !==
        prior.checkpoint.tairaFinalizedBlockHash) ||
    (currentEvidence.checkpoint.finalizedCheckpoint ===
      prior.checkpoint.finalizedCheckpoint &&
      currentEvidence.checkpoint.finalizedCheckpointBlockHash !==
        prior.checkpoint.finalizedCheckpointBlockHash) ||
    (currentEvidence.checkpoint.capabilityFinalizedCheckpoint ===
      prior.checkpoint.capabilityFinalizedCheckpoint &&
      currentEvidence.checkpoint.capabilityFinalizedCheckpointBlockHash !==
        prior.checkpoint.capabilityFinalizedCheckpointBlockHash) ||
    !privacyIsAggregateOnly(prior.privacy) ||
    !Array.isArray(prior.cohorts) ||
    prior.cohorts.length !== expectedPriorCohorts.length ||
    !exactKeys(prior.telemetryAttestation, [
      "telemetrySourceId",
      "completenessPolicySha256",
      "observedThroughEpochSeconds",
      "collectorKeyId",
      "collectorSignatureBase64",
    ]) ||
    !exactKeys(prior.authorization, [
      "telemetryBindingSha256",
      "collectorSignatureSha256",
      "authorizedTargetPercent",
      "authorizedAtEpochSeconds",
      "releaseAuthorizerKeyId",
      "releaseAuthorizerSignatureBase64",
    ])
  ) {
    fail("ROLLOUT_PRIOR_GATE_RECEIPT_NOT_QUALIFIED");
    return null;
  }
  let priorEnd = 0;
  for (const [index, cohort] of prior.cohorts.entries()) {
    if (
      !exactKeys(cohort, [
        "cohortPercent",
        "startedAtEpochSeconds",
        "endedAtEpochSeconds",
        "identityBindingSha256",
        "distributionAttestation",
        "aggregateMetrics",
      ]) ||
      cohort.cohortPercent !== expectedPriorCohorts[index] ||
      cohort.identityBindingSha256 !== prior.identity.bindingSha256 ||
      !isBoundedInteger(cohort.startedAtEpochSeconds, 9_999_999_999) ||
      !isBoundedInteger(cohort.endedAtEpochSeconds, 9_999_999_999) ||
      cohort.endedAtEpochSeconds - cohort.startedAtEpochSeconds <
        MINIMUM_DWELL_SECONDS ||
      cohort.startedAtEpochSeconds < priorEnd ||
      cohort.endedAtEpochSeconds > prior.evaluatedAtEpochSeconds ||
      !distributionAttestationQualified(cohort, prior.identity) ||
      !validateMetrics(cohort.aggregateMetrics)
    ) {
      fail("ROLLOUT_PRIOR_GATE_COHORTS_NOT_QUALIFIED");
      return null;
    }
    priorEnd = cohort.endedAtEpochSeconds;
  }
  const attestation = prior.telemetryAttestation;
  const authorization = prior.authorization;
  const telemetryProjection = telemetryProjectionFor(prior);
  const collectorSignatureBytes = canonicalSignature(
    attestation.collectorSignatureBase64,
  );
  if (
    attestation.telemetrySourceId !== trust?.telemetrySourceId ||
    attestation.completenessPolicySha256 !==
      trust?.completenessPolicySha256 ||
    !isBoundedInteger(attestation.observedThroughEpochSeconds, 9_999_999_999) ||
    attestation.observedThroughEpochSeconds < priorEnd ||
    attestation.observedThroughEpochSeconds >
      prior.evaluatedAtEpochSeconds + MAXIMUM_FUTURE_SKEW_SECONDS ||
    prior.evaluatedAtEpochSeconds - attestation.observedThroughEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    attestation.collectorKeyId !==
      trust?.authorities?.telemetryCollector?.keyId ||
    collectorSignatureBytes === null ||
    !signatureQualified(
      "telemetryCollector",
      telemetryProjection,
      attestation.collectorSignatureBase64,
    ) ||
    authorization.telemetryBindingSha256 !==
      canonicalDigest(telemetryProjection) ||
    authorization.collectorSignatureSha256 !==
      createHash("sha256").update(collectorSignatureBytes).digest("hex") ||
    authorization.authorizedTargetPercent !== expectedTarget ||
    !isBoundedInteger(authorization.authorizedAtEpochSeconds, 9_999_999_999) ||
    authorization.authorizedAtEpochSeconds <
      attestation.observedThroughEpochSeconds ||
    authorization.authorizedAtEpochSeconds < prior.evaluatedAtEpochSeconds ||
    authorization.authorizedAtEpochSeconds >
      prior.evaluatedAtEpochSeconds + MAXIMUM_FUTURE_SKEW_SECONDS ||
    authorization.releaseAuthorizerKeyId !==
      trust?.authorities?.releaseAuthorizer?.keyId ||
    !signatureQualified(
      "releaseAuthorizer",
      authorizationProjectionFor(prior),
      authorization.releaseAuthorizerSignatureBase64,
    )
  ) {
    fail("ROLLOUT_PRIOR_GATE_SIGNATURE_CHAIN_INVALID");
    return null;
  }
  if (
    !isDeepStrictEqual(
      currentEvidence.cohorts.slice(0, prior.cohorts.length),
      prior.cohorts,
    )
  ) {
    fail("ROLLOUT_PRIOR_COHORT_PREFIX_REWRITTEN");
    return null;
  }
  const newlyCompletedCohort =
    currentEvidence.cohorts[prior.cohorts.length];
  if (
    newlyCompletedCohort === undefined ||
    newlyCompletedCohort.startedAtEpochSeconds <= prior.evaluatedAtEpochSeconds ||
    newlyCompletedCohort.startedAtEpochSeconds <=
      prior.authorization.authorizedAtEpochSeconds
  ) {
    fail("ROLLOUT_COMPLETED_COHORT_PREDATES_PRIOR_AUTHORIZATION");
    return null;
  }
  return { receipt: prior, sha256: priorRecord.sha256 };
};

const validatePriorGateChain = (currentEvidence) => {
  const targetIndex = COHORT_SEQUENCE.indexOf(
    currentEvidence.targetCohortPercent,
  );
  if (targetIndex < 0) {
    fail("ROLLOUT_PRIOR_GATE_CHAIN_TARGET_INVALID");
    return null;
  }
  const requiredPriorTargets = COHORT_SEQUENCE.slice(0, targetIndex);
  for (const [target, path] of priorRolloutReceiptPaths) {
    const required = requiredPriorTargets.includes(target);
    if ((required && path.length === 0) || (!required && path.length !== 0)) {
      fail("ROLLOUT_PRIOR_GATE_CHAIN_PATH_SET_INVALID");
      return null;
    }
  }
  let nextReceipt = currentEvidence;
  for (const target of [...requiredPriorTargets].reverse()) {
    const prior = validatePriorGateReceipt(
      nextReceipt,
      priorRolloutReceiptPaths.get(target),
    );
    if (prior === null || prior.receipt.targetCohortPercent !== target) {
      fail("ROLLOUT_PRIOR_GATE_CHAIN_INCOMPLETE");
      return null;
    }
    nextReceipt = prior.receipt;
  }
  return { oldestReceipt: nextReceipt };
};

const validateEvidence = (
  target,
  candidateArtifactSha256,
  runtimeMetadataSha256,
  capabilitySnapshot,
  productionAdmission,
) => {
  if (
    evidencePathRaw.length === 0 ||
    !isAbsolute(evidencePathRaw) ||
    resolve(evidencePathRaw) !== evidencePathRaw
  ) {
    fail("ROLLOUT_ADVANCEMENT_EVIDENCE_PATH_MISSING_OR_INVALID");
    return;
  }
  const evidenceRecord = readStrictJsonFile(
    evidencePathRaw,
    MAXIMUM_RECEIPT_BYTES,
  );
  if (evidenceRecord === null) {
    fail("ROLLOUT_ADVANCEMENT_EVIDENCE_MISSING_OR_INVALID");
    return;
  }
  const evidence = evidenceRecord.value;
  if (containsSensitiveField(evidence)) {
    fail("ROLLOUT_ADVANCEMENT_EVIDENCE_CONTAINS_SENSITIVE_FIELD");
    return;
  }
  const targetIndex = COHORT_SEQUENCE.indexOf(target);
  const expectedPriorCohorts = COHORT_SEQUENCE.slice(0, targetIndex);
  const expectedFromCohort =
    targetIndex === 0 ? 0 : COHORT_SEQUENCE[targetIndex - 1];
  if (
    !exactKeys(evidence, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "fromCohortPercent",
      "targetCohortPercent",
      "evaluatedAtEpochSeconds",
      "priorGateReceiptSha256",
      "identity",
      "checkpoint",
      "privacy",
      "cohorts",
      "telemetryAttestation",
      "authorization",
    ]) ||
    !isProductionRolloutCurrentV3Envelope(evidence) ||
    evidence.status !== "qualified" ||
    evidence.platform !== "android" ||
    evidence.targetCohortPercent !== target ||
    evidence.fromCohortPercent !== expectedFromCohort ||
    !isBoundedInteger(evidence.evaluatedAtEpochSeconds, 9_999_999_999) ||
    evidence.evaluatedAtEpochSeconds !== evaluationEpoch ||
    (target === 1
      ? evidence.priorGateReceiptSha256 !== null
      : !SHA256.test(evidence.priorGateReceiptSha256 ?? "")) ||
    !validateIdentity(
      evidence.identity,
      candidateArtifactSha256,
      runtimeMetadataSha256,
      productionAdmission,
    ) ||
    !exactKeys(evidence.checkpoint, rolloutCheckpointKeys) ||
    evidence.checkpoint.piReceiptSha256 !== capabilitySnapshot.receiptSha256 ||
    evidence.checkpoint.capabilitySnapshotSha256 !==
      capabilitySnapshot.bindingSha256 ||
    evidence.checkpoint.sora2GenesisHash !==
      capabilitySnapshot.sora2GenesisHash ||
    evidence.checkpoint.finalizedCheckpoint !==
      capabilitySnapshot.finalizedCheckpoint ||
    evidence.checkpoint.finalizedCheckpointBlockHash !==
      capabilitySnapshot.finalizedCheckpointBlockHash ||
    evidence.checkpoint.indexedCheckpoint !==
      capabilitySnapshot.indexedCheckpoint ||
    evidence.checkpoint.capabilityFinalizedCheckpoint !==
      capabilitySnapshot.capabilityFinalizedCheckpoint ||
    evidence.checkpoint.capabilityFinalizedCheckpointBlockHash !==
      capabilitySnapshot.capabilityFinalizedCheckpointBlockHash ||
    evidence.checkpoint.capabilityIndexedCheckpoint !==
      capabilitySnapshot.capabilityIndexedCheckpoint ||
    evidence.checkpoint.minamotoChainId !==
      "00000000-0000-0000-0000-000000000753" ||
    !SHA256.test(evidence.checkpoint.minamotoGenesisHash ?? "") ||
    /^0{64}$/.test(evidence.checkpoint.minamotoGenesisHash) ||
    !isBoundedInteger(
      evidence.checkpoint.minamotoFinalizedCheckpoint,
      MAXIMUM_AGGREGATE_COUNT,
    ) ||
    evidence.checkpoint.minamotoFinalizedCheckpoint === 0 ||
    !SHA256.test(evidence.checkpoint.minamotoFinalizedBlockHash ?? "") ||
    /^0{64}$/.test(evidence.checkpoint.minamotoFinalizedBlockHash) ||
    evidence.checkpoint.tairaChainId !==
      productionAdmission.tairaDeployment.current.chainId ||
    !SHA256.test(evidence.checkpoint.tairaGenesisHash ?? "") ||
    /^0{64}$/.test(evidence.checkpoint.tairaGenesisHash) ||
    evidence.checkpoint.tairaGenesisHash !==
      productionAdmission.tairaDeployment.current.genesisSha256 ||
    !isBoundedInteger(
      evidence.checkpoint.tairaFinalizedCheckpoint,
      MAXIMUM_AGGREGATE_COUNT,
    ) ||
    evidence.checkpoint.tairaFinalizedCheckpoint === 0 ||
    !SHA256.test(evidence.checkpoint.tairaFinalizedBlockHash ?? "") ||
    /^0{64}$/.test(evidence.checkpoint.tairaFinalizedBlockHash) ||
    !isBoundedInteger(
      evidence.checkpoint.nexusObservedAtEpochSeconds,
      9_999_999_999,
    ) ||
    evidence.checkpoint.nexusObservedAtEpochSeconds === 0 ||
    evidence.checkpoint.nexusObservedAtEpochSeconds >
      evaluationEpoch + MAXIMUM_FUTURE_SKEW_SECONDS ||
    evaluationEpoch - evidence.checkpoint.nexusObservedAtEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    !privacyIsAggregateOnly(evidence.privacy) ||
    !Array.isArray(evidence.cohorts) ||
    evidence.cohorts.length !== expectedPriorCohorts.length
  ) {
    fail("ROLLOUT_ADVANCEMENT_EVIDENCE_SCHEMA_OR_IDENTITY_INVALID");
    return;
  }

  let priorEnd = 0;
  let cohortsQualified = true;
  evidence.cohorts.forEach((cohort, index) => {
    const validShape = exactKeys(cohort, [
      "cohortPercent",
      "startedAtEpochSeconds",
      "endedAtEpochSeconds",
      "identityBindingSha256",
      "distributionAttestation",
      "aggregateMetrics",
    ]);
    if (!validShape) {
      cohortsQualified = false;
      fail(`ROLLOUT_COHORT_${expectedPriorCohorts[index]}_NOT_QUALIFIED`);
      return;
    }
    const validTimes =
      isBoundedInteger(cohort.startedAtEpochSeconds, 9_999_999_999) &&
      isBoundedInteger(cohort.endedAtEpochSeconds, 9_999_999_999) &&
      cohort.endedAtEpochSeconds >= cohort.startedAtEpochSeconds &&
      cohort.endedAtEpochSeconds - cohort.startedAtEpochSeconds >=
        MINIMUM_DWELL_SECONDS &&
      cohort.startedAtEpochSeconds >= priorEnd &&
      cohort.endedAtEpochSeconds <= evidence.evaluatedAtEpochSeconds;
    if (
      cohort.cohortPercent !== expectedPriorCohorts[index] ||
      cohort.identityBindingSha256 !== evidence.identity.bindingSha256 ||
      !distributionAttestationQualified(cohort, evidence.identity) ||
      !validTimes ||
      !validateMetrics(cohort.aggregateMetrics)
    ) {
      cohortsQualified = false;
      fail(`ROLLOUT_COHORT_${expectedPriorCohorts[index]}_NOT_QUALIFIED`);
    }
    if (isBoundedInteger(cohort.endedAtEpochSeconds, 9_999_999_999)) {
      priorEnd = cohort.endedAtEpochSeconds;
    }
  });
  if (!cohortsQualified) return;
  const priorGate = validatePriorGateChain(evidence);
  if (priorGate === null) return;

  const telemetryAttestation = evidence.telemetryAttestation;
  const authorization = evidence.authorization;
  if (
    !exactKeys(telemetryAttestation, [
      "telemetrySourceId",
      "completenessPolicySha256",
      "observedThroughEpochSeconds",
      "collectorKeyId",
      "collectorSignatureBase64",
    ]) ||
    telemetryAttestation.telemetrySourceId !== trust?.telemetrySourceId ||
    telemetryAttestation.completenessPolicySha256 !==
      trust?.completenessPolicySha256 ||
    !isBoundedInteger(
      telemetryAttestation.observedThroughEpochSeconds,
      9_999_999_999,
    ) ||
    telemetryAttestation.observedThroughEpochSeconds === 0 ||
    telemetryAttestation.observedThroughEpochSeconds < priorEnd ||
    telemetryAttestation.observedThroughEpochSeconds >
      evaluationEpoch + MAXIMUM_FUTURE_SKEW_SECONDS ||
    evaluationEpoch - telemetryAttestation.observedThroughEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    telemetryAttestation.collectorKeyId !==
      trust?.authorities?.telemetryCollector?.keyId
  ) {
    fail("ROLLOUT_TELEMETRY_ATTESTATION_INVALID");
    return;
  }

  const telemetryProjection = telemetryProjectionFor(evidence);
  const telemetryBindingSha256 = canonicalDigest(telemetryProjection);
  const collectorSignatureBytes = canonicalSignature(
    telemetryAttestation.collectorSignatureBase64,
  );
  if (
    collectorSignatureBytes === null ||
    !signatureQualified(
      "telemetryCollector",
      telemetryProjection,
      telemetryAttestation.collectorSignatureBase64,
    )
  ) {
    fail("ROLLOUT_TELEMETRY_SIGNATURE_INVALID");
    return;
  }
  const collectorSignatureSha256 = createHash("sha256")
    .update(collectorSignatureBytes)
    .digest("hex");
  if (
    !exactKeys(authorization, [
      "telemetryBindingSha256",
      "collectorSignatureSha256",
      "authorizedTargetPercent",
      "authorizedAtEpochSeconds",
      "releaseAuthorizerKeyId",
      "releaseAuthorizerSignatureBase64",
    ]) ||
    authorization.telemetryBindingSha256 !== telemetryBindingSha256 ||
    authorization.collectorSignatureSha256 !== collectorSignatureSha256 ||
    authorization.authorizedTargetPercent !== target ||
    !isBoundedInteger(authorization.authorizedAtEpochSeconds, 9_999_999_999) ||
    authorization.authorizedAtEpochSeconds <
      telemetryAttestation.observedThroughEpochSeconds ||
    authorization.authorizedAtEpochSeconds < evaluationEpoch ||
    authorization.authorizedAtEpochSeconds >
      evaluationEpoch + MAXIMUM_FUTURE_SKEW_SECONDS ||
    authorization.releaseAuthorizerKeyId !==
      trust?.authorities?.releaseAuthorizer?.keyId
  ) {
    fail("ROLLOUT_AUTHORIZATION_INVALID");
    return;
  }
  if (
    !signatureQualified(
      "releaseAuthorizer",
      authorizationProjectionFor(evidence),
      authorization.releaseAuthorizerSignatureBase64,
    )
  ) {
    fail("ROLLOUT_AUTHORIZER_SIGNATURE_INVALID");
  }
};

validateBlockedTemplate({
  path: candidateTemplatePath,
  expectedFrom: 0,
  expectedTarget: 1,
  expectedWarning:
    "Template only: populate solely from the exact built AAB identity after all candidate qualifications pass; never infer or fabricate evidence.",
  failurePrefix: "ROLLOUT_CANDIDATE_BLOCKED_TEMPLATE",
});
validateBlockedTemplate({
  path: templatePath,
  expectedFrom: 1,
  expectedTarget: 5,
  expectedWarning:
    "Template only: populate solely from approved aggregate observations after the prior cohort reaches terminal dwell; never infer or fabricate evidence.",
  failurePrefix: "ROLLOUT_ADVANCEMENT_BLOCKED_TEMPLATE",
});
if (!/^(1|5|25|100)$/.test(targetRaw)) {
  fail(targetRaw === "" ? "ROLLOUT_TARGET_MISSING" : "ROLLOUT_TARGET_INVALID");
} else {
  const target = Number(targetRaw);
  if (!Number.isSafeInteger(evaluationEpoch) || evaluationEpoch <= 0) {
    fail("ROLLOUT_EVALUATION_EPOCH_MISSING_OR_INVALID");
  } else {
    const candidateArtifact = hashCandidateArtifact();
    const runtimeMetadata = hashReviewedRuntimeMetadata();
    const capabilitySnapshot = validateLiveCapabilitySnapshot();
    if (
      candidateArtifact !== null &&
      runtimeMetadata !== null &&
      capabilitySnapshot !== null
    ) {
      const productionAdmission = validateProductionAdmission(
        candidateArtifact,
        runtimeMetadata,
      );
      if (productionAdmission !== null) {
        // One percent has no prior cohort, but still requires the exact candidate
        // admission, dual-authority evidence, and actual bounded AAB.
        validateEvidence(
          target,
          candidateArtifact.sha256,
          runtimeMetadata.sha256,
          capabilitySnapshot,
          productionAdmission,
        );
      }
    }
  }
}

if (failures.length > 0) {
  process.stderr.write(`${[...new Set(failures)].sort().join("\n")}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write("production rollout gate qualified\n");
}
