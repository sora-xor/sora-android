#!/usr/bin/env node

import {
  ANDROID_MIGRATION_CONTROLLER_MAXIMUM_BYTES,
  extractAndroidMigrationControllerEnvelopeV1,
} from "./lib/android-migration-controller-envelope-v1.mjs";
import { hashStableRegularFile } from "./lib/strict-evidence.mjs";

const required = (name) => {
  const value = process.env[name] ?? "";
  if (value.length === 0) throw new Error(`${name}_MISSING`);
  return value;
};

const sequenceRaw = required(
  "ANDROID_MIGRATION_QUALIFICATION_SEQUENCE_NUMBER",
);
if (!/^[1-9][0-9]{0,14}$/.test(sequenceRaw)) {
  throw new Error("ANDROID_MIGRATION_QUALIFICATION_SEQUENCE_NUMBER_INVALID");
}
const candidatePath = required("PRODUCTION_CANDIDATE_AAB_PATH");
const candidate = hashStableRegularFile(candidatePath, 512 * 1024 * 1024);
if (candidate === null) {
  throw new Error("PRODUCTION_CANDIDATE_AAB_INVALID");
}

const receipt = extractAndroidMigrationControllerEnvelopeV1({
  envelopePath: required("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PATH"),
  outputRoot: required("ANDROID_MIGRATION_CONTROLLER_EVIDENCE_ROOT"),
  expectedSourceRevision: required("PRODUCTION_CANDIDATE_SOURCE_REVISION"),
  expectedCandidateAabSha256: candidate.sha256,
  expectedCandidateAabBytes: candidate.bytes,
  expectedRunId: required("ANDROID_MIGRATION_QUALIFICATION_RUN_ID"),
  expectedQualificationSequenceNumber: Number(sequenceRaw),
  expectedAppBuildIdentitySha256: required(
    "ANDROID_MIGRATION_QUALIFICATION_APP_BUILD_IDENTITY_SHA256",
  ),
});
if (receipt.envelopeBytes > ANDROID_MIGRATION_CONTROLLER_MAXIMUM_BYTES) {
  throw new Error("ANDROID_MIGRATION_CONTROLLER_ENVELOPE_TOO_LARGE");
}
process.stdout.write(`${JSON.stringify(receipt, null, 2)}\n`);
