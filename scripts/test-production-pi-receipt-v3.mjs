#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  createHash,
  generateKeyPairSync,
  sign as signBytes,
} from "node:crypto";
import {
  PRODUCTION_PI_CANDIDATE_SIGNATURE_V1,
  PRODUCTION_PI_CANDIDATE_V3,
  PRODUCTION_PI_RAW_LIVE_V1,
  productionPiCandidateBinding,
  validateProductionPiCandidateReceiptV3,
  validateProductionPiRawLiveReceipt,
  verifyProductionPiCandidateReceiptV3,
} from "./lib/production-pi-receipt.mjs";

const NOW = 2_000_000_000;
const SOURCE_REVISION = "1".repeat(40);
const ARTIFACT_SHA256 = "2".repeat(64);
const ARTIFACT_BYTES = 123_456;
const RAW_SHA256 = "3".repeat(64);
const RUNTIME_METADATA_SHA256 = "4".repeat(64);
const CONTROLLER_ID = "synthetic-android-pi-controller";
const TAIRA = Object.freeze({
  chainId: "fc56984b-2be7-431d-840e-21514d1883f0",
  toriiEndpoint: "https://node-2.taira.sora.org",
  genesisHash: "5".repeat(64),
});

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonRecord = (value) => {
  const bytes = Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");
  return { value, bytes, byteCount: bytes.length, sha256: sha256(bytes) };
};

const rawReceipt = () => ({
  schemaVersion: PRODUCTION_PI_RAW_LIVE_V1.schemaVersion,
  contractId: PRODUCTION_PI_RAW_LIVE_V1.contractId,
  status: PRODUCTION_PI_RAW_LIVE_V1.status,
  endpoint: "https://pi.soramitsu.io/graphql",
  checkedAtEpochSeconds: NOW - 2,
  serviceId: "pi.soramitsu.io",
  ecosystem: "sora2",
  chainId: "sora:mainnet",
  network: "mainnet",
  readOnly: true,
  finalizedCheckpoint: 10_000,
  indexedCheckpoint: 10_000,
  lastIndexedAtEpochSeconds: NOW - 3,
  mobileConfigHealthBound: true,
  configRevision: "6".repeat(64),
  capabilityFinalizedCheckpoint: 10_001,
  capabilityIndexedCheckpoint: 10_001,
  capabilityLastIndexedAtEpochSeconds: NOW - 2,
  sora2GenesisHash:
    "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5",
  finalizedCheckpointBlockHash: `0x${"7".repeat(64)}`,
  indexedCheckpointBlockHash: `0x${"7".repeat(64)}`,
  capabilityFinalizedCheckpointBlockHash: `0x${"8".repeat(64)}`,
  capabilityIndexedCheckpointBlockHash: `0x${"8".repeat(64)}`,
  historyBlockHeightContractDeployed: true,
  capabilities: {
    nexusAvailable: true,
    nexusSendsAvailable: true,
    polkamarktVisible: true,
    polkamarktMutationsAvailable: true,
    tairaDefaultVisible: true,
  },
  privacy: {
    accountIdentifiersIncluded: false,
    mutationsAttempted: false,
  },
});

const privacy = () => ({
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
});

const candidateReceipt = () => {
  const value = {
    schemaVersion: PRODUCTION_PI_CANDIDATE_V3.schemaVersion,
    contractId: PRODUCTION_PI_CANDIDATE_V3.contractId,
    status: PRODUCTION_PI_CANDIDATE_V3.status,
    controllerId: CONTROLLER_ID,
    capturedAtEpochSeconds: NOW - 2,
    endpoint: "https://pi.soramitsu.io/graphql",
    privacy: privacy(),
    health: {
      serviceId: "pi.soramitsu.io",
      ecosystem: "sora2",
      chainId: "sora:mainnet",
      network: "mainnet",
      readOnly: true,
      workerReady: true,
      genesisHash:
        "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5",
      runtimeSpecVersion: 130,
      runtimeTransactionVersion: 130,
      runtimeMetadataSha256: RUNTIME_METADATA_SHA256,
      workerLatestFinalizedBlock: 10_001,
      workerLatestFinalizedBlockHash: "8".repeat(64),
      canonicalRpcFinalizedBlockHash: "8".repeat(64),
      workerLatestIndexedBlock: 10_001,
      workerLatestIndexedBlockHash: "8".repeat(64),
      workerLag: 0,
      workerLastSuccessfulIndexTimestamp: NOW - 2,
      canonicalRpcCheckpointMatched: true,
    },
    capabilities: {
      configRevision: "6".repeat(64),
      capturedAtEpochSeconds: NOW - 2,
      mobileConfigHealthBound: true,
      historyBlockHeightContractDeployed: true,
      nexusAvailable: true,
      nexusSendsAvailable: true,
      polkamarktVisible: true,
      polkamarktMutationsAvailable: true,
      tairaDefaultVisible: true,
    },
    networkCheckpoints: {
      minamoto: {
        networkId: "minamoto",
        chainId: "00000000-0000-0000-0000-000000000753",
        toriiEndpoint: "https://minamoto.sora.org",
        i105Discriminant: 753,
        genesisHash: "9".repeat(64),
        finalizedHeight: 20_001,
        finalizedBlockHash: "a".repeat(64),
        canonicalToriiMatched: true,
      },
      taira: {
        networkId: "taira",
        chainId: TAIRA.chainId,
        toriiEndpoint: TAIRA.toriiEndpoint,
        i105Discriminant: 369,
        genesisHash: TAIRA.genesisHash,
        finalizedHeight: 30_001,
        finalizedBlockHash: "b".repeat(64),
        canonicalToriiMatched: true,
      },
    },
    candidate: {
      platform: "android",
      applicationId: "jp.co.soramitsu.sora",
      artifactSha256: ARTIFACT_SHA256,
      artifactBytes: ARTIFACT_BYTES,
      sourceRevision: SOURCE_REVISION,
      rawLiveProbeReceiptSha256: RAW_SHA256,
      bindingSha256: "",
    },
  };
  value.candidate.bindingSha256 = productionPiCandidateBinding(value);
  return value;
};

const validationContext = Object.freeze({
  expectedControllerId: CONTROLLER_ID,
  expectedCandidate: {
    artifactSha256: ARTIFACT_SHA256,
    artifactBytes: ARTIFACT_BYTES,
    sourceRevision: SOURCE_REVISION,
  },
  expectedRawLiveReceiptSha256: RAW_SHA256,
  expectedRuntimeMetadataSha256: RUNTIME_METADATA_SHA256,
  expectedTairaDeployment: TAIRA,
});

const keys = generateKeyPairSync("ed25519");
const publicKeyPem = keys.publicKey.export({ type: "spki", format: "pem" });
const publicKeySha256 = sha256(
  keys.publicKey.export({ type: "spki", format: "der" }),
);
const trustValue = {
  schemaVersion: 1,
  contractId: "sora-android-production-rollout-trust-v1",
  platform: "android",
  assessedAt: "2033-05-18",
  status: "qualified",
  signatureAlgorithm: "Ed25519",
  telemetrySourceId: "synthetic-telemetry",
  completenessPolicySha256: "c".repeat(64),
  authorities: {
    telemetryCollector: {
      role: "privacy-aggregate-telemetry-collector",
      keyId: "synthetic-telemetry-key",
      publicKeyPem,
      publicKeySha256,
      enabled: true,
    },
    releaseAuthorizer: {
      role: "production-rollout-authorizer",
      keyId: "synthetic-release-key",
      publicKeyPem,
      publicKeySha256,
      enabled: true,
    },
  },
  blockingReasons: [],
};
const trustRecord = jsonRecord(trustValue);

const signedRecords = (value, privateKey = keys.privateKey) => {
  const receiptRecord = jsonRecord(value);
  const signatureBase64 = signBytes(
    null,
    receiptRecord.bytes,
    privateKey,
  ).toString("base64");
  const signatureRecord = jsonRecord({
    schemaVersion: PRODUCTION_PI_CANDIDATE_SIGNATURE_V1.schemaVersion,
    contractId: PRODUCTION_PI_CANDIDATE_SIGNATURE_V1.contractId,
    controllerId: CONTROLLER_ID,
    keyId: "synthetic-release-key",
    signatureAlgorithm:
      PRODUCTION_PI_CANDIDATE_SIGNATURE_V1.signatureAlgorithm,
    receiptSha256: receiptRecord.sha256,
    signatureBase64,
  });
  return { receiptRecord, signatureRecord };
};

assert.notEqual(
  validateProductionPiRawLiveReceipt(rawReceipt(), NOW),
  null,
  "raw live v1 receipt must validate only through its distinct contract",
);
const rawLegacyCandidate = rawReceipt();
rawLegacyCandidate.contractId = "sora-pi-production-capability-probe-v1";
assert.equal(
  validateProductionPiRawLiveReceipt(rawLegacyCandidate, NOW),
  null,
  "legacy candidate v1 must not masquerade as raw-live evidence",
);

const valid = candidateReceipt();
assert.notEqual(
  validateProductionPiCandidateReceiptV3(valid, NOW, validationContext),
  null,
);
const validSigned = signedRecords(valid);
assert.notEqual(
  verifyProductionPiCandidateReceiptV3({
    ...validSigned,
    trustRecord,
    expectedTrustSha256: trustRecord.sha256,
    evaluationEpoch: NOW,
    validationContext,
  }),
  null,
  "exact candidate-bound v3 receipt and protected signature must validate",
);

let failClosedCases = 0;
const rejects = (name, mutate, { resign = true, context = validationContext } = {}) => {
  const value = structuredClone(valid);
  mutate(value);
  if (value.candidate?.bindingSha256 !== undefined) {
    value.candidate.bindingSha256 = productionPiCandidateBinding(value);
  }
  const records = resign
    ? signedRecords(value)
    : {
        receiptRecord: jsonRecord(value),
        signatureRecord: validSigned.signatureRecord,
      };
  assert.equal(
    verifyProductionPiCandidateReceiptV3({
      ...records,
      trustRecord,
      expectedTrustSha256: trustRecord.sha256,
      evaluationEpoch: NOW,
      validationContext: context,
    }),
    null,
    name,
  );
  failClosedCases += 1;
};

rejects("legacy v1 receipt is wire-incompatible", (value) => {
  value.schemaVersion = 1;
  value.contractId = "sora-pi-production-capability-probe-v1";
});
rejects("legacy v2 receipt is wire-incompatible", (value) => {
  value.schemaVersion = 2;
  value.contractId = "sora-pi-production-capability-probe-v2";
});
rejects("mixed v2 schema with v3 contract is rejected", (value) => {
  value.schemaVersion = 2;
});
rejects("mixed v3 schema with v2 contract is rejected", (value) => {
  value.contractId = "sora-pi-production-capability-probe-v2";
});
rejects("candidate artifact drift is rejected", (value) => {
  value.candidate.artifactSha256 = "d".repeat(64);
});
rejects("candidate artifact size drift is rejected", (value) => {
  value.candidate.artifactBytes += 1;
});
rejects("candidate source drift is rejected", (value) => {
  value.candidate.sourceRevision = "e".repeat(40);
});
rejects("raw live receipt substitution is rejected", (value) => {
  value.candidate.rawLiveProbeReceiptSha256 = "f".repeat(64);
});
rejects("controller substitution is rejected", (value) => {
  value.controllerId = "different-controller";
});
rejects("stale capture is rejected", (value) => {
  value.capturedAtEpochSeconds = NOW - 301;
  value.capabilities.capturedAtEpochSeconds = NOW - 301;
  value.health.workerLastSuccessfulIndexTimestamp = NOW - 301;
});
rejects("non-atomic capability capture is rejected", (value) => {
  value.capabilities.capturedAtEpochSeconds -= 1;
});
rejects("nonzero SORA2 lag is rejected", (value) => {
  value.health.workerLatestIndexedBlock -= 1;
  value.health.workerLag = 1;
});
rejects("canonical RPC mismatch is rejected", (value) => {
  value.health.canonicalRpcFinalizedBlockHash = "c".repeat(64);
});
rejects("disabled release capability is rejected", (value) => {
  value.capabilities.nexusSendsAvailable = false;
});
rejects("false mobile-config health binding is rejected", (value) => {
  value.capabilities.mobileConfigHealthBound = false;
});
rejects("false history-height contract is rejected", (value) => {
  value.capabilities.historyBlockHeightContractDeployed = false;
});
rejects("Minamoto chain drift is rejected", (value) => {
  value.networkCheckpoints.minamoto.chainId = TAIRA.chainId;
});
rejects("Taira epoch drift is rejected", (value) => {
  value.networkCheckpoints.taira.chainId =
    "809574f5-fee7-5e69-bfcf-52451e42d50f";
});
rejects("unknown root field is rejected", (value) => {
  value.extra = true;
});
rejects("privacy identifier field is rejected", (value) => {
  value.privacy.accountIdentifiersIncluded = true;
});
rejects(
  "receipt byte tamper is rejected without a replacement signature",
  (value) => {
    value.networkCheckpoints.minamoto.finalizedHeight += 1;
  },
  { resign: false },
);

const wrongKeys = generateKeyPairSync("ed25519");
const wronglySigned = signedRecords(valid, wrongKeys.privateKey);
assert.equal(
  verifyProductionPiCandidateReceiptV3({
    ...wronglySigned,
    trustRecord,
    expectedTrustSha256: trustRecord.sha256,
    evaluationEpoch: NOW,
    validationContext,
  }),
  null,
  "untrusted signer is rejected",
);
failClosedCases += 1;
assert.equal(
  verifyProductionPiCandidateReceiptV3({
    ...validSigned,
    trustRecord,
    expectedTrustSha256: "0".repeat(64),
    evaluationEpoch: NOW,
    validationContext,
  }),
  null,
  "protected trust pin mismatch is rejected",
);
failClosedCases += 1;

process.stdout.write(
  `production PI raw-live v1 and candidate-bound signed v3 contract: 2 positive contexts and ${failClosedCases} fail-closed cases passed\n`,
);
