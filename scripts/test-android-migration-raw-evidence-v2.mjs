#!/usr/bin/env node

import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

import {
  ANDROID_MIGRATION_RAW_EVIDENCE_AUTHORIZATION,
  ANDROID_MIGRATION_RAW_EVIDENCE_SUITES,
  AndroidMigrationRawEvidenceError,
  collectAndroidMigrationRawEvidenceV2,
  validateAndroidMigrationRawEvidenceV2,
} from "./lib/android-migration-raw-evidence-v2.mjs";

import { ANDROID_PRE_ACCOUNT_EVIDENCE_CATEGORIES } from "./lib/android-migration-coverage-v1.mjs";

const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const clone = (value) => structuredClone(value);

const createRun = () => {
  const root = realpathSync(
    mkdtempSync(join(tmpdir(), "sora-android-migration-raw-v2-")),
  );
  const files = [];
  for (const [suiteIndex, suite] of ANDROID_MIGRATION_RAW_EVIDENCE_SUITES.entries()) {
    for (const category of ["method-inventory", "report", "transcript", ...(suite === "legacy-pre-account-upgrade" ? ANDROID_PRE_ACCOUNT_EVIDENCE_CATEGORIES : [])]) {
      const relativePath = `${suite}/${category}.txt`;
      const bytes = Buffer.from(`${suite}:${category}:sanitized\n`, "utf8");
      mkdirSync(join(root, suite), { recursive: true, mode: 0o700 });
      writeFileSync(join(root, relativePath), bytes, { flag: "wx", mode: 0o600 });
      files.push({
        relativePath,
        category,
        suite,
        byteCount: bytes.length,
        sha256: sha256(bytes),
      });
    }
    if (suiteIndex < 2) {
      const relativePath = `${suite}/apk-identity-${suiteIndex + 1}.txt`;
      const bytes = Buffer.from(`apk-identity:${suiteIndex + 1}\n`, "utf8");
      writeFileSync(join(root, relativePath), bytes, { flag: "wx", mode: 0o600 });
      files.push({
        relativePath,
        category: "apk-identity",
        suite,
        byteCount: bytes.length,
        sha256: sha256(bytes),
      });
    }
  }
  const index = {
    schemaVersion: 2,
    contractId: "sora-android-wallet-migration-raw-run-index-v2",
    platform: "android",
    status: "complete",
    runId: "12345678-1234-4234-9234-123456789abc",
    qualificationSequenceNumber: 7,
    sourceRevision: "1234567890abcdef1234567890abcdef12345678",
    producedAtEpochSeconds: 300,
    runStartedAtEpochSeconds: 100,
    runFinishedAtEpochSeconds: 200,
    identity: {
      appBuildIdentitySha256: sha256("candidate-aab"),
      deviceClasses: ["Pixel 9"],
      operatingSystemBuilds: ["Android 16 (BP2A.260705.008)"],
    },
    files,
  };
  const writeIndex = (value = index) =>
    writeFileSync(
      join(root, "raw-run-index.json"),
      `${JSON.stringify(value, null, 2)}\n`,
      { mode: 0o600 },
    );
  writeIndex();
  return { root, index, writeIndex };
};

const expectRejected = (mutate, expectedMessage) => {
  const run = createRun();
  try {
    mutate(run);
    assert.throws(
      () => collectAndroidMigrationRawEvidenceV2({ root: run.root }),
      (error) =>
        error instanceof AndroidMigrationRawEvidenceError &&
        error.message.includes(expectedMessage),
    );
  } finally {
    rmSync(run.root, { recursive: true, force: true });
  }
};

const positive = createRun();
try {
  const result = collectAndroidMigrationRawEvidenceV2({ root: positive.root });
  validateAndroidMigrationRawEvidenceV2(result.record);
  assert.deepEqual(
    result.record.authorization,
    ANDROID_MIGRATION_RAW_EVIDENCE_AUTHORIZATION,
  );
  assert.equal(result.record.aggregate.rawResultFileCount, 36);
  assert.equal(result.record.aggregate.requiredSuiteCount, 7);
  assert.equal(result.record.aggregate.apkIdentityArtifactCount, 2);
  assert.equal(result.record.aggregate.methodInventoryArtifactCount, 7);
  assert.equal(result.record.aggregate.reportArtifactCount, 7);
  assert.equal(result.record.aggregate.transcriptArtifactCount, 7);
  assert.equal(result.record.aggregate.preAccountEvidenceArtifactCount, 13);
  assert.equal(result.record.aggregate.allPreAccountEvidencePresent, true);
  assert.equal(result.record.aggregate.allRequiredSuitesPresent, true);
  assert.equal(result.record.aggregate.rawValuesExcludedFromPublicEvidence, true);
  const publicBytes = result.bytes.toString("utf8");
  assert.equal(publicBytes.includes("relativePath"), false);
  assert.equal(publicBytes.includes("sanitized"), false);
  assert.equal(publicBytes.includes(positive.index.files[0].relativePath), false);
  assert.equal(JSON.stringify(JSON.parse(publicBytes)), JSON.stringify(result.record));
} finally {
  rmSync(positive.root, { recursive: true, force: true });
}

expectRejected(({ index, writeIndex }) => {
  const stale = clone(index);
  stale.schemaVersion = 1;
  stale.contractId = "sora-android-wallet-migration-raw-run-index-v1";
  writeIndex(stale);
}, "not exact v2");

expectRejected(({ root, index, writeIndex }) => {
  const incomplete = clone(index);
  const removed = incomplete.files.find(
    (entry) =>
      entry.suite === ANDROID_MIGRATION_RAW_EVIDENCE_SUITES[0] &&
      entry.category === "transcript",
  );
  incomplete.files = incomplete.files.filter(
    (entry) =>
      entry.suite !== ANDROID_MIGRATION_RAW_EVIDENCE_SUITES[0] ||
      entry.category !== "transcript",
  );
  unlinkSync(join(root, removed.relativePath));
  writeIndex(incomplete);
}, "lacks required");

expectRejected(({ index, writeIndex }) => {
  const duplicate = clone(index);
  duplicate.files.push(clone(duplicate.files[0]));
  writeIndex(duplicate);
}, "duplicates");

expectRejected(({ root, index, writeIndex }) => {
  writeFileSync(join(root, index.files[0].relativePath), "changed\n");
  writeIndex(index);
}, "differs from its index");

expectRejected(({ root, index, writeIndex }) => {
  const target = join(root, index.files[1].relativePath);
  unlinkSync(target);
  symlinkSync(join(root, index.files[2].relativePath), target);
  writeIndex(index);
}, "aliased or non-private");

expectRejected(({ index, writeIndex }) => {
  const unknown = clone(index);
  unknown.files[0].unexpected = false;
  writeIndex(unknown);
}, "missing or unreviewed fields");

expectRejected(({ root }) => {
  const source = readFileSync(join(root, "raw-run-index.json"), "utf8");
  writeFileSync(
    join(root, "raw-run-index.json"),
    source.replace('"schemaVersion": 2,', '"schemaVersion": 2,\n  "schemaVersion": 2,'),
  );
}, "invalid strict JSON");

expectRejected(({ index, writeIndex }) => {
  const noncanonical = clone(index);
  noncanonical.files[0].relativePath = `../${noncanonical.files[0].relativePath}`;
  writeIndex(noncanonical);
}, "not canonical");

expectRejected(({ index, writeIndex }) => {
  const misleading = clone(index);
  misleading.files[0].suite = ANDROID_MIGRATION_RAW_EVIDENCE_SUITES[1];
  writeIndex(misleading);
}, "sanitized suite namespace");

expectRejected(({ root }) => {
  writeFileSync(join(root, "unindexed.txt"), "unindexed\n", {
    flag: "wx",
    mode: 0o600,
  });
}, "does not cover the exact protected file inventory");

for (const category of ANDROID_PRE_ACCOUNT_EVIDENCE_CATEGORIES) {
  expectRejected(({ root, index, writeIndex }) => {
    const removed = index.files.find((entry) => entry.category === category);
    index.files = index.files.filter((entry) => entry !== removed);
    unlinkSync(join(root, removed.relativePath));
    writeIndex();
  }, "lacks Room50 provenance or required pre-account restart evidence");
}

const authorizationMutation = createRun();
try {
  const record = clone(
    collectAndroidMigrationRawEvidenceV2({ root: authorizationMutation.root }).record,
  );
  record.authorization.authorizesQualification = true;
  assert.throws(
    () => validateAndroidMigrationRawEvidenceV2(record),
    /explicitly non-authorizing/,
  );
} finally {
  rmSync(authorizationMutation.root, { recursive: true, force: true });
}

process.stdout.write(
  "Android migration raw evidence v2: positive collection and 24 fail-closed mutations passed\n",
);
