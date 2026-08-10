import {
  createHash,
  createPublicKey,
  verify as verifySignature,
} from "node:crypto";

export const PRODUCTION_PI_RAW_LIVE_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-pi-production-raw-live-probe-v1",
  status: "observed-read-only",
});

export const PRODUCTION_PI_CANDIDATE_V3 = Object.freeze({
  schemaVersion: 3,
  contractId: "sora-pi-production-capability-probe-v3",
  status: "qualified",
});

export const PRODUCTION_PI_CANDIDATE_SIGNATURE_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-pi-production-capability-probe-signature-v1",
  signatureAlgorithm: "Ed25519",
});

export const PRODUCTION_PI_CAPABILITY_KEYS = [
  "nexusAvailable",
  "nexusSendsAvailable",
  "polkamarktVisible",
  "polkamarktMutationsAvailable",
  "tairaDefaultVisible",
];

export const PRODUCTION_PI_RAW_LIVE_RECEIPT_KEYS = [
  "schemaVersion",
  "contractId",
  "status",
  "endpoint",
  "checkedAtEpochSeconds",
  "serviceId",
  "ecosystem",
  "chainId",
  "network",
  "readOnly",
  "finalizedCheckpoint",
  "indexedCheckpoint",
  "lastIndexedAtEpochSeconds",
  "mobileConfigHealthBound",
  "configRevision",
  "capabilityFinalizedCheckpoint",
  "capabilityIndexedCheckpoint",
  "capabilityLastIndexedAtEpochSeconds",
  "sora2GenesisHash",
  "finalizedCheckpointBlockHash",
  "indexedCheckpointBlockHash",
  "capabilityFinalizedCheckpointBlockHash",
  "capabilityIndexedCheckpointBlockHash",
  "historyBlockHeightContractDeployed",
  "capabilities",
  "privacy",
];

export const PRODUCTION_PI_RAW_LIVE_PRIVACY_KEYS = [
  "accountIdentifiersIncluded",
  "mutationsAttempted",
];

export const PRODUCTION_PI_CANDIDATE_RECEIPT_KEYS = [
  "schemaVersion",
  "contractId",
  "status",
  "controllerId",
  "capturedAtEpochSeconds",
  "endpoint",
  "privacy",
  "health",
  "capabilities",
  "networkCheckpoints",
  "candidate",
];

export const PRODUCTION_PI_CANDIDATE_HEALTH_KEYS = [
  "serviceId",
  "ecosystem",
  "chainId",
  "network",
  "readOnly",
  "workerReady",
  "genesisHash",
  "runtimeSpecVersion",
  "runtimeTransactionVersion",
  "runtimeMetadataSha256",
  "workerLatestFinalizedBlock",
  "workerLatestFinalizedBlockHash",
  "canonicalRpcFinalizedBlockHash",
  "workerLatestIndexedBlock",
  "workerLatestIndexedBlockHash",
  "workerLag",
  "workerLastSuccessfulIndexTimestamp",
  "canonicalRpcCheckpointMatched",
];

export const PRODUCTION_PI_CANDIDATE_CAPABILITY_KEYS = [
  "configRevision",
  "capturedAtEpochSeconds",
  "mobileConfigHealthBound",
  "historyBlockHeightContractDeployed",
  ...PRODUCTION_PI_CAPABILITY_KEYS,
];

export const PRODUCTION_PI_NETWORK_CHECKPOINT_KEYS = [
  "networkId",
  "chainId",
  "toriiEndpoint",
  "i105Discriminant",
  "genesisHash",
  "finalizedHeight",
  "finalizedBlockHash",
  "canonicalToriiMatched",
];

export const PRODUCTION_PI_CANDIDATE_KEYS = [
  "platform",
  "applicationId",
  "artifactSha256",
  "artifactBytes",
  "sourceRevision",
  "rawLiveProbeReceiptSha256",
  "bindingSha256",
];

export const PRODUCTION_PI_PRIVACY_KEYS = [
  "aggregateOnly",
  "accountIdentifiersIncluded",
  "addressesIncluded",
  "transactionIdentifiersIncluded",
  "phrasesOrSeedsIncluded",
  "privateKeysIncluded",
  "publicKeysIncluded",
  "rawSignedPayloadsIncluded",
  "perWalletRecordsIncluded",
  "deviceIdentifiersIncluded",
  "ipAddressesIncluded",
  "rawResponsesIncluded",
  "rawErrorsIncluded",
];

export const PRODUCTION_PI_CANDIDATE_SIGNATURE_KEYS = [
  "schemaVersion",
  "contractId",
  "controllerId",
  "keyId",
  "signatureAlgorithm",
  "receiptSha256",
  "signatureBase64",
];

const MAXIMUM_CAPABILITY_AGE_SECONDS = 5 * 60;
const MAXIMUM_FUTURE_SKEW_SECONDS = 30;
const MAXIMUM_CHECKPOINT_LAG_BLOCKS = 32;
const MAXIMUM_INTEGER = 90_000_000_000_000;
const SORA2_GENESIS_HASH =
  "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5";
const SORA2_GENESIS_SHA256 = SORA2_GENESIS_HASH.slice(2);
const MINAMOTO_CHAIN_ID = "00000000-0000-0000-0000-000000000753";
const MINAMOTO_TORII_ENDPOINT = "https://minamoto.sora.org";
const SHA256 = /^[0-9a-f]{64}$/;
const REVISION = /^[0-9a-f]{40}$/;
const safeBindingString = (value, maximum = 256) =>
  typeof value === "string" &&
  [...value].length > 0 &&
  [...value].length <= maximum &&
  !/[=\u0000-\u001f\u007f-\u009f]/u.test(value);

const sensitiveKeys = new Set([
  "phrase",
  "mnemonic",
  "seed",
  "privatekey",
  "publickey",
  "address",
  "walletaddress",
  "accountid",
  "transactionhash",
  "txhash",
  "signedpayload",
  "rawsignedpayload",
  "rawpayload",
]);

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

const boundedInteger = (value, maximum = MAXIMUM_INTEGER) =>
  Number.isSafeInteger(value) && value >= 0 && value <= maximum;

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

export const containsPrivacySensitiveField = (value) => {
  if (Array.isArray(value)) {
    return value.some(containsPrivacySensitiveField);
  }
  if (value === null || typeof value !== "object") return false;
  return Object.entries(value).some(([key, child]) => {
    const normalized = key.toLowerCase().replace(/[^a-z0-9]/g, "");
    return (
      sensitiveKeys.has(normalized) || containsPrivacySensitiveField(child)
    );
  });
};

const privacyIsRedacted = (privacy) =>
  exactKeys(privacy, PRODUCTION_PI_PRIVACY_KEYS) &&
  privacy.aggregateOnly === true &&
  PRODUCTION_PI_PRIVACY_KEYS.filter((key) => key !== "aggregateOnly").every(
    (key) => privacy[key] === false,
  );

export const productionPiRawCapabilityBinding = (receipt) =>
  createHash("sha256")
    .update(
      [
        `schemaVersion=${receipt.schemaVersion}`,
        `contractId=${receipt.contractId}`,
        `endpoint=${receipt.endpoint}`,
        `serviceId=${receipt.serviceId}`,
        `ecosystem=${receipt.ecosystem}`,
        `chainId=${receipt.chainId}`,
        `network=${receipt.network}`,
        `readOnly=${receipt.readOnly}`,
        `mobileConfigHealthBound=${receipt.mobileConfigHealthBound}`,
        `configRevision=${receipt.configRevision}`,
        `historyBlockHeightContractDeployed=${receipt.historyBlockHeightContractDeployed}`,
        ...PRODUCTION_PI_CAPABILITY_KEYS.map(
          (key) => `${key}=${receipt.capabilities[key]}`,
        ),
      ].join("\n"),
      "utf8",
    )
    .digest("hex");

export const validateProductionPiRawLiveReceipt = (
  receipt,
  evaluationEpoch,
  {
    requireAllCapabilities = true,
    maximumFutureSkewSeconds = MAXIMUM_FUTURE_SKEW_SECONDS,
  } = {},
) => {
  if (
    typeof requireAllCapabilities !== "boolean" ||
    !Number.isSafeInteger(maximumFutureSkewSeconds) ||
    maximumFutureSkewSeconds < 0 ||
    maximumFutureSkewSeconds > MAXIMUM_FUTURE_SKEW_SECONDS ||
    containsPrivacySensitiveField(receipt) ||
    !exactKeys(receipt, PRODUCTION_PI_RAW_LIVE_RECEIPT_KEYS) ||
    !exactKeys(receipt.capabilities, PRODUCTION_PI_CAPABILITY_KEYS) ||
    !exactKeys(receipt.privacy, PRODUCTION_PI_RAW_LIVE_PRIVACY_KEYS) ||
    receipt.schemaVersion !== PRODUCTION_PI_RAW_LIVE_V1.schemaVersion ||
    receipt.contractId !== PRODUCTION_PI_RAW_LIVE_V1.contractId ||
    receipt.status !== PRODUCTION_PI_RAW_LIVE_V1.status ||
    receipt.endpoint !== "https://pi.soramitsu.io/graphql" ||
    receipt.serviceId !== "pi.soramitsu.io" ||
    receipt.ecosystem !== "sora2" ||
    receipt.chainId !== "sora:mainnet" ||
    receipt.network !== "mainnet" ||
    receipt.readOnly !== true ||
    !boundedInteger(evaluationEpoch, 9_999_999_999) ||
    evaluationEpoch === 0 ||
    !boundedInteger(receipt.checkedAtEpochSeconds, 9_999_999_999) ||
    receipt.checkedAtEpochSeconds === 0 ||
    receipt.checkedAtEpochSeconds > evaluationEpoch + maximumFutureSkewSeconds ||
    evaluationEpoch - receipt.checkedAtEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    !boundedInteger(receipt.finalizedCheckpoint) ||
    receipt.finalizedCheckpoint === 0 ||
    !boundedInteger(receipt.indexedCheckpoint) ||
    receipt.indexedCheckpoint === 0 ||
    receipt.indexedCheckpoint > receipt.finalizedCheckpoint ||
    receipt.finalizedCheckpoint - receipt.indexedCheckpoint >
      MAXIMUM_CHECKPOINT_LAG_BLOCKS ||
    !boundedInteger(receipt.lastIndexedAtEpochSeconds, 9_999_999_999) ||
    receipt.lastIndexedAtEpochSeconds === 0 ||
    receipt.lastIndexedAtEpochSeconds >
      receipt.checkedAtEpochSeconds + maximumFutureSkewSeconds ||
    receipt.checkedAtEpochSeconds - receipt.lastIndexedAtEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    receipt.lastIndexedAtEpochSeconds >
      evaluationEpoch + maximumFutureSkewSeconds ||
    evaluationEpoch - receipt.lastIndexedAtEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    receipt.mobileConfigHealthBound !== true ||
    !SHA256.test(receipt.configRevision ?? "") ||
    /^0{64}$/.test(receipt.configRevision) ||
    !boundedInteger(receipt.capabilityFinalizedCheckpoint) ||
    receipt.capabilityFinalizedCheckpoint === 0 ||
    !boundedInteger(receipt.capabilityIndexedCheckpoint) ||
    receipt.capabilityIndexedCheckpoint === 0 ||
    receipt.capabilityFinalizedCheckpoint < receipt.finalizedCheckpoint ||
    receipt.capabilityIndexedCheckpoint < receipt.indexedCheckpoint ||
    receipt.capabilityIndexedCheckpoint >
      receipt.capabilityFinalizedCheckpoint ||
    receipt.capabilityFinalizedCheckpoint -
      receipt.capabilityIndexedCheckpoint >
      MAXIMUM_CHECKPOINT_LAG_BLOCKS ||
    !boundedInteger(
      receipt.capabilityLastIndexedAtEpochSeconds,
      9_999_999_999,
    ) ||
    receipt.capabilityLastIndexedAtEpochSeconds === 0 ||
    receipt.capabilityLastIndexedAtEpochSeconds <
      receipt.lastIndexedAtEpochSeconds ||
    receipt.capabilityLastIndexedAtEpochSeconds >
      receipt.checkedAtEpochSeconds + maximumFutureSkewSeconds ||
    receipt.checkedAtEpochSeconds -
      receipt.capabilityLastIndexedAtEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    receipt.sora2GenesisHash !== SORA2_GENESIS_HASH ||
    !/^0x[0-9a-f]{64}$/.test(receipt.finalizedCheckpointBlockHash ?? "") ||
    /^0x0{64}$/.test(receipt.finalizedCheckpointBlockHash) ||
    !/^0x[0-9a-f]{64}$/.test(receipt.indexedCheckpointBlockHash ?? "") ||
    /^0x0{64}$/.test(receipt.indexedCheckpointBlockHash) ||
    !/^0x[0-9a-f]{64}$/.test(
      receipt.capabilityFinalizedCheckpointBlockHash ?? "",
    ) ||
    /^0x0{64}$/.test(receipt.capabilityFinalizedCheckpointBlockHash) ||
    !/^0x[0-9a-f]{64}$/.test(
      receipt.capabilityIndexedCheckpointBlockHash ?? "",
    ) ||
    /^0x0{64}$/.test(receipt.capabilityIndexedCheckpointBlockHash) ||
    (receipt.indexedCheckpoint === receipt.finalizedCheckpoint &&
      receipt.indexedCheckpointBlockHash !==
        receipt.finalizedCheckpointBlockHash) ||
    (receipt.capabilityFinalizedCheckpoint === receipt.finalizedCheckpoint &&
      receipt.capabilityFinalizedCheckpointBlockHash !==
        receipt.finalizedCheckpointBlockHash) ||
    (receipt.capabilityIndexedCheckpoint === receipt.indexedCheckpoint &&
      receipt.capabilityIndexedCheckpointBlockHash !==
        receipt.indexedCheckpointBlockHash) ||
    (receipt.capabilityIndexedCheckpoint ===
      receipt.capabilityFinalizedCheckpoint &&
      receipt.capabilityIndexedCheckpointBlockHash !==
        receipt.capabilityFinalizedCheckpointBlockHash) ||
    receipt.historyBlockHeightContractDeployed !== true ||
    (requireAllCapabilities &&
      !PRODUCTION_PI_CAPABILITY_KEYS.every(
        (key) => receipt.capabilities[key] === true,
      )) ||
    (receipt.capabilities.nexusSendsAvailable &&
      !receipt.capabilities.nexusAvailable) ||
    (receipt.capabilities.polkamarktMutationsAvailable &&
      !receipt.capabilities.polkamarktVisible) ||
    receipt.privacy.accountIdentifiersIncluded !== false ||
    receipt.privacy.mutationsAttempted !== false
  ) {
    return null;
  }
  return {
    bindingSha256: productionPiRawCapabilityBinding(receipt),
    finalizedCheckpoint: receipt.finalizedCheckpoint,
    indexedCheckpoint: receipt.indexedCheckpoint,
    capabilityFinalizedCheckpoint: receipt.capabilityFinalizedCheckpoint,
    capabilityIndexedCheckpoint: receipt.capabilityIndexedCheckpoint,
    sora2GenesisHash: receipt.sora2GenesisHash,
    finalizedCheckpointBlockHash: receipt.finalizedCheckpointBlockHash,
    indexedCheckpointBlockHash: receipt.indexedCheckpointBlockHash,
    capabilityFinalizedCheckpointBlockHash:
      receipt.capabilityFinalizedCheckpointBlockHash,
    capabilityIndexedCheckpointBlockHash:
      receipt.capabilityIndexedCheckpointBlockHash,
  };
};

const candidateBindingProjection = (receipt) => {
  const { bindingSha256: ignored, ...candidate } = receipt.candidate;
  return {
    schemaVersion: receipt.schemaVersion,
    contractId: receipt.contractId,
    status: receipt.status,
    controllerId: receipt.controllerId,
    capturedAtEpochSeconds: receipt.capturedAtEpochSeconds,
    endpoint: receipt.endpoint,
    privacy: receipt.privacy,
    health: receipt.health,
    capabilities: receipt.capabilities,
    networkCheckpoints: receipt.networkCheckpoints,
    candidate,
  };
};

export const productionPiCandidateBinding = (receipt) =>
  canonicalJsonSha256(candidateBindingProjection(receipt));

export const productionPiCandidateCapabilityBinding = (receipt) =>
  canonicalJsonSha256({
    schemaVersion: receipt.schemaVersion,
    contractId: receipt.contractId,
    endpoint: receipt.endpoint,
    health: {
      serviceId: receipt.health.serviceId,
      ecosystem: receipt.health.ecosystem,
      chainId: receipt.health.chainId,
      network: receipt.health.network,
      readOnly: receipt.health.readOnly,
      genesisHash: receipt.health.genesisHash,
      runtimeSpecVersion: receipt.health.runtimeSpecVersion,
      runtimeTransactionVersion: receipt.health.runtimeTransactionVersion,
      runtimeMetadataSha256: receipt.health.runtimeMetadataSha256,
    },
    capabilities: receipt.capabilities,
  });

const checkpointValid = (
  checkpoint,
  { networkId, chainId, toriiEndpoint, i105Discriminant, genesisHash },
) =>
  exactKeys(checkpoint, PRODUCTION_PI_NETWORK_CHECKPOINT_KEYS) &&
  checkpoint.networkId === networkId &&
  checkpoint.chainId === chainId &&
  checkpoint.toriiEndpoint === toriiEndpoint &&
  checkpoint.i105Discriminant === i105Discriminant &&
  SHA256.test(checkpoint.genesisHash ?? "") &&
  !/^0{64}$/.test(checkpoint.genesisHash) &&
  (genesisHash === undefined || checkpoint.genesisHash === genesisHash) &&
  boundedInteger(checkpoint.finalizedHeight) &&
  checkpoint.finalizedHeight > 0 &&
  SHA256.test(checkpoint.finalizedBlockHash ?? "") &&
  !/^0{64}$/.test(checkpoint.finalizedBlockHash) &&
  checkpoint.canonicalToriiMatched === true;

export const validateProductionPiCandidateReceiptV3 = (
  receipt,
  evaluationEpoch,
  {
    expectedControllerId,
    expectedCandidate,
    expectedRawLiveReceiptSha256,
    expectedRuntimeMetadataSha256,
    expectedTairaDeployment,
    requireAllCapabilities = true,
    maximumFutureSkewSeconds = MAXIMUM_FUTURE_SKEW_SECONDS,
  },
) => {
  const health = receipt?.health;
  const capabilities = receipt?.capabilities;
  const checkpoints = receipt?.networkCheckpoints;
  const candidate = receipt?.candidate;
  if (
    typeof requireAllCapabilities !== "boolean" ||
    !Number.isSafeInteger(maximumFutureSkewSeconds) ||
    maximumFutureSkewSeconds < 0 ||
    maximumFutureSkewSeconds > MAXIMUM_FUTURE_SKEW_SECONDS ||
    !safeBindingString(expectedControllerId) ||
    !SHA256.test(expectedRuntimeMetadataSha256 ?? "") ||
    !REVISION.test(expectedCandidate?.sourceRevision ?? "") ||
    !SHA256.test(expectedCandidate?.artifactSha256 ?? "") ||
    !boundedInteger(expectedCandidate?.artifactBytes) ||
    expectedCandidate.artifactBytes === 0 ||
    !safeBindingString(expectedTairaDeployment?.chainId) ||
    !safeBindingString(expectedTairaDeployment?.toriiEndpoint) ||
    !SHA256.test(expectedTairaDeployment?.genesisHash ?? "") ||
    containsPrivacySensitiveField(receipt) ||
    !exactKeys(receipt, PRODUCTION_PI_CANDIDATE_RECEIPT_KEYS) ||
    !exactKeys(health, PRODUCTION_PI_CANDIDATE_HEALTH_KEYS) ||
    !exactKeys(capabilities, PRODUCTION_PI_CANDIDATE_CAPABILITY_KEYS) ||
    !exactKeys(checkpoints, ["minamoto", "taira"]) ||
    !exactKeys(candidate, PRODUCTION_PI_CANDIDATE_KEYS) ||
    !privacyIsRedacted(receipt.privacy) ||
    receipt.schemaVersion !== PRODUCTION_PI_CANDIDATE_V3.schemaVersion ||
    receipt.contractId !== PRODUCTION_PI_CANDIDATE_V3.contractId ||
    receipt.status !== PRODUCTION_PI_CANDIDATE_V3.status ||
    receipt.controllerId !== expectedControllerId ||
    receipt.endpoint !== "https://pi.soramitsu.io/graphql" ||
    !boundedInteger(evaluationEpoch, 9_999_999_999) ||
    evaluationEpoch === 0 ||
    !boundedInteger(receipt.capturedAtEpochSeconds, 9_999_999_999) ||
    receipt.capturedAtEpochSeconds === 0 ||
    receipt.capturedAtEpochSeconds >
      evaluationEpoch + maximumFutureSkewSeconds ||
    evaluationEpoch - receipt.capturedAtEpochSeconds >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    health.serviceId !== "pi.soramitsu.io" ||
    health.ecosystem !== "sora2" ||
    health.chainId !== "sora:mainnet" ||
    health.network !== "mainnet" ||
    health.readOnly !== true ||
    health.workerReady !== true ||
    health.genesisHash !== SORA2_GENESIS_SHA256 ||
    health.runtimeSpecVersion !== 130 ||
    health.runtimeTransactionVersion !== 130 ||
    health.runtimeMetadataSha256 !== expectedRuntimeMetadataSha256 ||
    !boundedInteger(health.workerLatestFinalizedBlock) ||
    health.workerLatestFinalizedBlock === 0 ||
    health.workerLatestIndexedBlock !== health.workerLatestFinalizedBlock ||
    health.workerLag !== 0 ||
    !SHA256.test(health.workerLatestFinalizedBlockHash ?? "") ||
    /^0{64}$/.test(health.workerLatestFinalizedBlockHash) ||
    health.canonicalRpcFinalizedBlockHash !==
      health.workerLatestFinalizedBlockHash ||
    health.workerLatestIndexedBlockHash !==
      health.workerLatestFinalizedBlockHash ||
    !boundedInteger(
      health.workerLastSuccessfulIndexTimestamp,
      9_999_999_999,
    ) ||
    health.workerLastSuccessfulIndexTimestamp === 0 ||
    health.workerLastSuccessfulIndexTimestamp >
      receipt.capturedAtEpochSeconds + maximumFutureSkewSeconds ||
    receipt.capturedAtEpochSeconds -
      health.workerLastSuccessfulIndexTimestamp >
      MAXIMUM_CAPABILITY_AGE_SECONDS ||
    health.canonicalRpcCheckpointMatched !== true ||
    !SHA256.test(capabilities.configRevision ?? "") ||
    /^0{64}$/.test(capabilities.configRevision) ||
    capabilities.capturedAtEpochSeconds !== receipt.capturedAtEpochSeconds ||
    capabilities.mobileConfigHealthBound !== true ||
    capabilities.historyBlockHeightContractDeployed !== true ||
    (requireAllCapabilities &&
      !PRODUCTION_PI_CAPABILITY_KEYS.every(
        (key) => capabilities[key] === true,
      )) ||
    (capabilities.nexusSendsAvailable && !capabilities.nexusAvailable) ||
    (capabilities.polkamarktMutationsAvailable &&
      !capabilities.polkamarktVisible) ||
    !checkpointValid(checkpoints.minamoto, {
      networkId: "minamoto",
      chainId: MINAMOTO_CHAIN_ID,
      toriiEndpoint: MINAMOTO_TORII_ENDPOINT,
      i105Discriminant: 753,
    }) ||
    !checkpointValid(checkpoints.taira, {
      networkId: "taira",
      chainId: expectedTairaDeployment.chainId,
      toriiEndpoint: expectedTairaDeployment.toriiEndpoint,
      i105Discriminant: 369,
      genesisHash: expectedTairaDeployment.genesisHash,
    }) ||
    candidate.platform !== "android" ||
    candidate.applicationId !== "jp.co.soramitsu.sora" ||
    candidate.artifactSha256 !== expectedCandidate.artifactSha256 ||
    candidate.artifactBytes !== expectedCandidate.artifactBytes ||
    candidate.sourceRevision !== expectedCandidate.sourceRevision ||
    !SHA256.test(candidate.rawLiveProbeReceiptSha256 ?? "") ||
    /^0{64}$/.test(candidate.rawLiveProbeReceiptSha256) ||
    (expectedRawLiveReceiptSha256 !== undefined &&
      candidate.rawLiveProbeReceiptSha256 !==
        expectedRawLiveReceiptSha256) ||
    candidate.bindingSha256 !== productionPiCandidateBinding(receipt)
  ) {
    return null;
  }
  return {
    bindingSha256: candidate.bindingSha256,
    capabilityBindingSha256:
      productionPiCandidateCapabilityBinding(receipt),
    capturedAtEpochSeconds: receipt.capturedAtEpochSeconds,
    finalizedCheckpoint: health.workerLatestFinalizedBlock,
    indexedCheckpoint: health.workerLatestIndexedBlock,
    sora2GenesisHash: `0x${health.genesisHash}`,
    finalizedCheckpointBlockHash: `0x${health.workerLatestFinalizedBlockHash}`,
    minamoto: checkpoints.minamoto,
    taira: checkpoints.taira,
  };
};

const rolloutTrustReleaseAuthorizer = (trustRecord, expectedTrustSha256) => {
  const trust = trustRecord?.value;
  const authority = trust?.authorities?.releaseAuthorizer;
  if (
    trustRecord?.sha256 !== expectedTrustSha256 ||
    !exactKeys(trust, [
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
    trust.schemaVersion !== 1 ||
    trust.contractId !== "sora-android-production-rollout-trust-v1" ||
    trust.platform !== "android" ||
    trust.status !== "qualified" ||
    trust.signatureAlgorithm !== "Ed25519" ||
    !Array.isArray(trust.blockingReasons) ||
    trust.blockingReasons.length !== 0 ||
    !exactKeys(trust.authorities, [
      "telemetryCollector",
      "releaseAuthorizer",
    ]) ||
    !exactKeys(authority, [
      "role",
      "keyId",
      "publicKeyPem",
      "publicKeySha256",
      "enabled",
    ]) ||
    authority.role !== "production-rollout-authorizer" ||
    authority.enabled !== true ||
    !safeBindingString(authority.keyId, 64) ||
    typeof authority.publicKeyPem !== "string" ||
    authority.publicKeyPem.length > 2_048 ||
    !SHA256.test(authority.publicKeySha256 ?? "")
  ) {
    return null;
  }
  try {
    const publicKey = createPublicKey(authority.publicKeyPem);
    const spki = publicKey.export({ type: "spki", format: "der" });
    if (
      publicKey.asymmetricKeyType !== "ed25519" ||
      createHash("sha256").update(spki).digest("hex") !==
        authority.publicKeySha256
    ) {
      return null;
    }
    return { authority, publicKey };
  } catch {
    return null;
  }
};

export const verifyProductionPiCandidateReceiptV3 = ({
  receiptRecord,
  signatureRecord,
  trustRecord,
  expectedTrustSha256,
  evaluationEpoch,
  validationContext,
}) => {
  const snapshot = validateProductionPiCandidateReceiptV3(
    receiptRecord?.value,
    evaluationEpoch,
    validationContext,
  );
  const signature = signatureRecord?.value;
  const trust = rolloutTrustReleaseAuthorizer(
    trustRecord,
    expectedTrustSha256,
  );
  if (
    snapshot === null ||
    trust === null ||
    !exactKeys(signature, PRODUCTION_PI_CANDIDATE_SIGNATURE_KEYS) ||
    signature.schemaVersion !==
      PRODUCTION_PI_CANDIDATE_SIGNATURE_V1.schemaVersion ||
    signature.contractId !==
      PRODUCTION_PI_CANDIDATE_SIGNATURE_V1.contractId ||
    signature.controllerId !== validationContext.expectedControllerId ||
    signature.controllerId !== receiptRecord.value.controllerId ||
    signature.keyId !== trust.authority.keyId ||
    signature.signatureAlgorithm !==
      PRODUCTION_PI_CANDIDATE_SIGNATURE_V1.signatureAlgorithm ||
    signature.receiptSha256 !== receiptRecord.sha256 ||
    typeof signature.signatureBase64 !== "string" ||
    !/^[A-Za-z0-9+/]{86}==$/.test(signature.signatureBase64)
  ) {
    return null;
  }
  const signatureBytes = Buffer.from(signature.signatureBase64, "base64");
  if (
    signatureBytes.length !== 64 ||
    signatureBytes.toString("base64") !== signature.signatureBase64
  ) {
    return null;
  }
  try {
    if (
      !verifySignature(
        null,
        receiptRecord.bytes,
        trust.publicKey,
        signatureBytes,
      )
    ) {
      return null;
    }
  } catch {
    return null;
  }
  return {
    ...snapshot,
    receiptSha256: receiptRecord.sha256,
    signatureReceiptSha256: signatureRecord.sha256,
    controllerKeyId: trust.authority.keyId,
    controllerPublicKeySha256: trust.authority.publicKeySha256,
  };
};
