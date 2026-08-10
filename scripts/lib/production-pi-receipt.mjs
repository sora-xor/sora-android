import { createHash } from "node:crypto";

export const PRODUCTION_PI_CAPABILITY_KEYS = [
  "nexusAvailable",
  "nexusSendsAvailable",
  "polkamarktVisible",
  "polkamarktMutationsAvailable",
  "tairaDefaultVisible",
];
export const PRODUCTION_PI_RECEIPT_KEYS = [
  "schemaVersion",
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
  "capabilityFinalizedCheckpoint",
  "capabilityIndexedCheckpoint",
  "sora2GenesisHash",
  "finalizedCheckpointBlockHash",
  "capabilityFinalizedCheckpointBlockHash",
  "historyBlockHeightContractDeployed",
  "capabilities",
  "privacy",
];
export const PRODUCTION_PI_PRIVACY_KEYS = [
  "accountIdentifiersIncluded",
  "mutationsAttempted",
];

const MAXIMUM_CAPABILITY_AGE_SECONDS = 5 * 60;
const MAXIMUM_FUTURE_SKEW_SECONDS = 30;
const MAXIMUM_CHECKPOINT_LAG_BLOCKS = 32;
const MAXIMUM_INTEGER = 90_000_000_000_000;
const SORA2_GENESIS_HASH =
  "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5";
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

export const productionPiCapabilityBinding = (receipt) =>
  createHash("sha256")
    .update(
      [
        `schemaVersion=${receipt.schemaVersion}`,
        `endpoint=${receipt.endpoint}`,
        `serviceId=${receipt.serviceId}`,
        `ecosystem=${receipt.ecosystem}`,
        `chainId=${receipt.chainId}`,
        `network=${receipt.network}`,
        `readOnly=${receipt.readOnly}`,
        `mobileConfigHealthBound=${receipt.mobileConfigHealthBound}`,
        `historyBlockHeightContractDeployed=${receipt.historyBlockHeightContractDeployed}`,
        ...PRODUCTION_PI_CAPABILITY_KEYS.map(
          (key) => `${key}=${receipt.capabilities[key]}`,
        ),
      ].join("\n"),
      "utf8",
    )
    .digest("hex");

export const validateProductionPiReceipt = (
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
    !exactKeys(receipt, PRODUCTION_PI_RECEIPT_KEYS) ||
    !exactKeys(receipt.capabilities, PRODUCTION_PI_CAPABILITY_KEYS) ||
    !exactKeys(receipt.privacy, PRODUCTION_PI_PRIVACY_KEYS) ||
    receipt.schemaVersion !== 1 ||
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
    receipt.checkedAtEpochSeconds >
      evaluationEpoch + maximumFutureSkewSeconds ||
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
    receipt.sora2GenesisHash !== SORA2_GENESIS_HASH ||
    !/^0x[0-9a-f]{64}$/.test(
      receipt.finalizedCheckpointBlockHash ?? "",
    ) ||
    /^0x0{64}$/.test(receipt.finalizedCheckpointBlockHash) ||
    !/^0x[0-9a-f]{64}$/.test(
      receipt.capabilityFinalizedCheckpointBlockHash ?? "",
    ) ||
    /^0x0{64}$/.test(receipt.capabilityFinalizedCheckpointBlockHash) ||
    (receipt.capabilityFinalizedCheckpoint === receipt.finalizedCheckpoint &&
      receipt.capabilityFinalizedCheckpointBlockHash !==
        receipt.finalizedCheckpointBlockHash) ||
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
    bindingSha256: productionPiCapabilityBinding(receipt),
    finalizedCheckpoint: receipt.finalizedCheckpoint,
    indexedCheckpoint: receipt.indexedCheckpoint,
    capabilityFinalizedCheckpoint: receipt.capabilityFinalizedCheckpoint,
    capabilityIndexedCheckpoint: receipt.capabilityIndexedCheckpoint,
    sora2GenesisHash: receipt.sora2GenesisHash,
    finalizedCheckpointBlockHash: receipt.finalizedCheckpointBlockHash,
    capabilityFinalizedCheckpointBlockHash:
      receipt.capabilityFinalizedCheckpointBlockHash,
  };
};
