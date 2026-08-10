#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  ANDROID_PRODUCTION_ADMISSION_V3,
  ANDROID_PRODUCTION_ROLLOUT_CONTROLLER_REQUEST_V3,
  ANDROID_PRODUCTION_ROLLOUT_V3,
  isProductionAdmissionV3Envelope,
  isProductionRolloutControllerRequestV3Envelope,
  isProductionRolloutCurrentV3Envelope,
  isProductionRolloutCursorV3Envelope,
  isProductionRolloutPriorV3Envelope,
} from "./lib/production-rollout-v3-contract.mjs";

const contexts = [
  {
    name: "admission",
    contract: ANDROID_PRODUCTION_ADMISSION_V3,
    legacyContractId: "sora-android-production-admission-v2",
    predicate: isProductionAdmissionV3Envelope,
  },
  {
    name: "current rollout",
    contract: ANDROID_PRODUCTION_ROLLOUT_V3,
    legacyContractId: "sora-android-production-rollout-v2",
    predicate: isProductionRolloutCurrentV3Envelope,
  },
  {
    name: "prior rollout",
    contract: ANDROID_PRODUCTION_ROLLOUT_V3,
    legacyContractId: "sora-android-production-rollout-v2",
    predicate: isProductionRolloutPriorV3Envelope,
  },
  {
    name: "rollout cursor",
    contract: ANDROID_PRODUCTION_ROLLOUT_V3,
    legacyContractId: "sora-android-production-rollout-v2",
    predicate: isProductionRolloutCursorV3Envelope,
  },
  {
    name: "controller request",
    contract: ANDROID_PRODUCTION_ROLLOUT_CONTROLLER_REQUEST_V3,
    legacyContractId: "sora-android-production-rollout-controller-request-v2",
    predicate: isProductionRolloutControllerRequestV3Envelope,
  },
];

for (const { name, contract, legacyContractId, predicate } of contexts) {
  assert.equal(
    predicate({ ...contract }),
    true,
    `${name} must accept its exact v3 envelope`,
  );
  assert.equal(
    predicate({ schemaVersion: 2, contractId: legacyContractId }),
    false,
    `${name} must reject its complete v2 envelope`,
  );
  assert.equal(
    predicate({ schemaVersion: 3, contractId: legacyContractId }),
    false,
    `${name} must reject a v3 schema paired with a v2 contract ID`,
  );
  assert.equal(
    predicate({ schemaVersion: 2, contractId: contract.contractId }),
    false,
    `${name} must reject a v2 schema paired with a v3 contract ID`,
  );
}

process.stdout.write(
  "production rollout v3 envelope contract: 5 exact-v3 contexts and 15 fail-closed v2/mixed cases passed\n",
);
