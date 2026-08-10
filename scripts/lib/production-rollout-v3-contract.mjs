const contractEnvelopeMatches = (value, contract) =>
  value !== null &&
  typeof value === "object" &&
  !Array.isArray(value) &&
  value.schemaVersion === contract.schemaVersion &&
  value.contractId === contract.contractId;

export const ANDROID_PRODUCTION_ADMISSION_V3 = Object.freeze({
  schemaVersion: 3,
  contractId: "sora-android-production-admission-v3",
});

export const ANDROID_PRODUCTION_ROLLOUT_V3 = Object.freeze({
  schemaVersion: 3,
  contractId: "sora-android-production-rollout-v3",
});

export const ANDROID_PRODUCTION_ROLLOUT_CONTROLLER_REQUEST_V3 =
  Object.freeze({
    schemaVersion: 3,
    contractId: "sora-android-production-rollout-controller-request-v3",
  });

export const isProductionAdmissionV3Envelope = (value) =>
  contractEnvelopeMatches(value, ANDROID_PRODUCTION_ADMISSION_V3);

export const isProductionRolloutCurrentV3Envelope = (value) =>
  contractEnvelopeMatches(value, ANDROID_PRODUCTION_ROLLOUT_V3);

export const isProductionRolloutPriorV3Envelope = (value) =>
  contractEnvelopeMatches(value, ANDROID_PRODUCTION_ROLLOUT_V3);

export const isProductionRolloutCursorV3Envelope = (value) =>
  contractEnvelopeMatches(value, ANDROID_PRODUCTION_ROLLOUT_V3);

export const isProductionRolloutControllerRequestV3Envelope = (value) =>
  contractEnvelopeMatches(
    value,
    ANDROID_PRODUCTION_ROLLOUT_CONTROLLER_REQUEST_V3,
  );

export const productionAdmissionV3ProjectionPrefix = () => [
  `contractId=${ANDROID_PRODUCTION_ADMISSION_V3.contractId}`,
  "platform=android",
];
