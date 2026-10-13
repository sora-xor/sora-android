#!/usr/bin/env node

import { isAbsolute, resolve } from "node:path";
import { readStrictJsonFile } from "./lib/strict-evidence.mjs";
import {
  isProductionRolloutCursorV3Envelope,
} from "./lib/production-rollout-v3-contract.mjs";

const receiptPath = process.env.PRODUCTION_ROLLOUT_CHAIN_CURSOR_PATH ?? "";
const expectedTargetRaw =
  process.env.PRODUCTION_ROLLOUT_CHAIN_CURSOR_TARGET ?? "";
const MAXIMUM_RECEIPT_BYTES = 64 * 1024;
const SHA256 = /^[0-9a-f]{64}$/;
const expectedKeys = [
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
];

const fail = (code) => {
  process.stderr.write(`${code}\n`);
  process.exit(1);
};
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

if (
  !isAbsolute(receiptPath) ||
  resolve(receiptPath) !== receiptPath ||
  !/^(1|5|25|100)$/.test(expectedTargetRaw)
) {
  fail("ROLLOUT_CHAIN_CURSOR_INPUT_INVALID");
}
const record = readStrictJsonFile(receiptPath, MAXIMUM_RECEIPT_BYTES);
const receipt = record?.value;
const expectedTarget = Number(expectedTargetRaw);
if (
  record === null ||
  !exactKeys(receipt, expectedKeys) ||
  !isProductionRolloutCursorV3Envelope(receipt) ||
  receipt.status !== "qualified" ||
  receipt.platform !== "android" ||
  receipt.targetCohortPercent !== expectedTarget
) {
  fail("ROLLOUT_CHAIN_CURSOR_RECEIPT_INVALID");
}
if (expectedTarget === 1) {
  if (receipt.priorGateReceiptSha256 !== null) {
    fail("ROLLOUT_CHAIN_CURSOR_INITIAL_LINK_NOT_NULL");
  }
  process.stdout.write("NONE\n");
} else {
  if (!SHA256.test(receipt.priorGateReceiptSha256 ?? "")) {
    fail("ROLLOUT_CHAIN_CURSOR_PRIOR_LINK_INVALID");
  }
  process.stdout.write(`${receipt.priorGateReceiptSha256}\n`);
}
