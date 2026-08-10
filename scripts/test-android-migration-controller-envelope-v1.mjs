#!/usr/bin/env node

import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import {
  chmodSync,
  linkSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import {
  ANDROID_MIGRATION_CONTROLLER_FILES,
  extractAndroidMigrationControllerEnvelopeV1,
} from "./lib/android-migration-controller-envelope-v1.mjs";

const SOURCE_REVISION = "1".repeat(40);
const CANDIDATE_SHA256 = "2".repeat(64);
const CANDIDATE_BYTES = 123456;
const RUN_ID = "12345678-1234-1234-1234-123456789abc";
const SEQUENCE = 41;
const APP_IDENTITY = "3".repeat(64);
const EXPECTED_NAMES = Object.keys(ANDROID_MIGRATION_CONTROLLER_FILES).sort();

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

const buildPayloads = () => {
  const payloads = Object.create(null);
  for (const name of EXPECTED_NAMES) {
    payloads[name] = name.endsWith(".json")
      ? jsonBytes({ schemaVersion: 1, status: "qualified" })
      : Buffer.from(`synthetic ${name}\n`, "utf8");
  }
  payloads["android-migration-trust.json"] = jsonBytes({
    schemaVersion: 1,
    contractId: "sora-android-wallet-migration-qualification-trust-v1",
    platform: "android",
    status: "qualified",
  });
  const artifactFiles = {
    retainedReleaseSnapshotManifest:
      "android-migration-retained-snapshot-manifest.json",
    testResult: "android-migration-test-results.json",
    encryptedStorageEvidence:
      "android-migration-encrypted-storage-evidence.json",
    deviceExecutionEvidence:
      "android-migration-device-execution-evidence.json",
    rawExecutionEvidence: "android-migration-raw-execution-evidence.json",
  };
  payloads["android-migration-evidence.json"] = jsonBytes({
    schemaVersion: 2,
    contractId: "sora-android-wallet-migration-evidence-v2",
    platform: "android",
    status: "qualified",
    runId: RUN_ID,
    qualificationSequenceNumber: SEQUENCE,
    sourceRevision: SOURCE_REVISION,
    trustRootSha256: sha256(payloads["android-migration-trust.json"]),
    identity: { appBuildIdentitySha256: APP_IDENTITY },
    artifacts: Object.fromEntries(
      Object.entries(artifactFiles).map(([key, name]) => [
        key,
        {
          relativePath: `docs/modernization/qualification/${name}`,
          sha256: sha256(payloads[name]),
        },
      ]),
    ),
  });
  payloads["android-migration-matrix.json"] = jsonBytes({
    schemaVersion: 7,
    contractId: "sora-android-wallet-migration-qualification-v7",
    platform: "android",
    status: "qualified",
    runId: RUN_ID,
    qualificationSequenceNumber: SEQUENCE,
    sourceRevision: SOURCE_REVISION,
    trustRootSha256: sha256(payloads["android-migration-trust.json"]),
    evidenceManifestSha256: sha256(payloads["android-migration-evidence.json"]),
    identity: { appBuildIdentitySha256: APP_IDENTITY },
  });
  return payloads;
};

const buildEnvelope = (payloads = buildPayloads()) => ({
  schemaVersion: 1,
  contractId: "sora-android-migration-controller-envelope-v1",
  status: "delivered-unreviewed",
  platform: "android",
  sourceRevision: SOURCE_REVISION,
  candidateAabSha256: CANDIDATE_SHA256,
  candidateAabBytes: CANDIDATE_BYTES,
  runId: RUN_ID,
  qualificationSequenceNumber: SEQUENCE,
  appBuildIdentitySha256: APP_IDENTITY,
  files: Object.fromEntries(
    EXPECTED_NAMES.map((name) => [name, payloads[name].toString("base64")]),
  ),
  authorization: {
    authorizesRelease: false,
    authorizesProductionMutation: false,
  },
});

const createFixture = () => {
  const root = realpathSync(
    resolve(mkdtempSync(join(tmpdir(), "sora-migration-controller-"))),
  );
  const envelopePath = join(root, "envelope.json");
  const outputRoot = join(root, "evidence");
  const envelope = buildEnvelope();
  writeFileSync(envelopePath, jsonBytes(envelope), { mode: 0o600 });
  chmodSync(envelopePath, 0o600);
  return { root, envelopePath, outputRoot, envelope };
};

const extract = (fixture) =>
  extractAndroidMigrationControllerEnvelopeV1({
    envelopePath: fixture.envelopePath,
    outputRoot: fixture.outputRoot,
    expectedSourceRevision: SOURCE_REVISION,
    expectedCandidateAabSha256: CANDIDATE_SHA256,
    expectedCandidateAabBytes: CANDIDATE_BYTES,
    expectedRunId: RUN_ID,
    expectedQualificationSequenceNumber: SEQUENCE,
    expectedAppBuildIdentitySha256: APP_IDENTITY,
  });

const rewrite = (fixture) => {
  writeFileSync(fixture.envelopePath, jsonBytes(fixture.envelope), {
    mode: 0o600,
  });
  chmodSync(fixture.envelopePath, 0o600);
};

const positive = createFixture();
try {
  const receipt = extract(positive);
  assert.equal(receipt.status, "extracted-unreviewed");
  assert.equal(receipt.fileCount, EXPECTED_NAMES.length);
  assert.deepEqual(Object.keys(receipt.files), EXPECTED_NAMES);
  assert.equal(receipt.candidateAabSha256, CANDIDATE_SHA256);
  assert.equal(receipt.authorization.authorizesRelease, false);
  assert.equal(receipt.authorization.authorizesProductionMutation, false);
  assert.equal(
    readFileSync(join(positive.outputRoot, "android-migration-matrix.json"), "utf8"),
    Buffer.from(positive.envelope.files["android-migration-matrix.json"], "base64").toString("utf8"),
  );
} finally {
  rmSync(positive.root, { recursive: true, force: true });
}

const mutations = [
  [
    "missing-file",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_INVENTORY_INVALID",
    (fixture) => {
      delete fixture.envelope.files[EXPECTED_NAMES[0]];
      rewrite(fixture);
    },
  ],
  [
    "extra-file",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_INVENTORY_INVALID",
    (fixture) => {
      fixture.envelope.files["unexpected.json"] = Buffer.from("{}\n").toString("base64");
      rewrite(fixture);
    },
  ],
  [
    "duplicate-outer-key",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_JSON_INVALID",
    (fixture) =>
      writeFileSync(
        fixture.envelopePath,
        '{"schemaVersion":1,"schemaVersion":1}\n',
        { mode: 0o600 },
      ),
  ],
  [
    "invalid-base64",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_BASE64_INVALID",
    (fixture) => {
      fixture.envelope.files[EXPECTED_NAMES[0]] = "not-base64";
      rewrite(fixture);
    },
  ],
  [
    "duplicate-payload-json-key",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_JSON_INVALID",
    (fixture) => {
      fixture.envelope.files["android-migration-test-results.json"] =
        Buffer.from('{"status":"a","status":"b"}\n').toString("base64");
      rewrite(fixture);
    },
  ],
  [
    "source-drift",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_SOURCE_DIVERGED",
    (fixture) => {
      fixture.envelope.sourceRevision = "9".repeat(40);
      rewrite(fixture);
    },
  ],
  [
    "candidate-drift",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_CANDIDATE_DIVERGED",
    (fixture) => {
      fixture.envelope.candidateAabSha256 = "9".repeat(64);
      rewrite(fixture);
    },
  ],
  [
    "internal-run-drift",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_IDENTITY_DIVERGED",
    (fixture) => {
      const receipt = JSON.parse(
        Buffer.from(
          fixture.envelope.files["android-migration-matrix.json"],
          "base64",
        ).toString("utf8"),
      );
      receipt.runId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
      fixture.envelope.files["android-migration-matrix.json"] =
        jsonBytes(receipt).toString("base64");
      rewrite(fixture);
    },
  ],
  [
    "artifact-drift",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_ARTIFACTS_DIVERGED",
    (fixture) => {
      fixture.envelope.files["android-migration-test-results.json"] =
        jsonBytes({ changed: true }).toString("base64");
      rewrite(fixture);
    },
  ],
  [
    "authorizing-envelope",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_SHAPE_INVALID",
    (fixture) => {
      fixture.envelope.authorization.authorizesRelease = true;
      rewrite(fixture);
    },
  ],
  [
    "stale-receipt",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_QUALIFICATION_SHAPE_INVALID",
    (fixture) => {
      const receipt = JSON.parse(
        Buffer.from(
          fixture.envelope.files["android-migration-matrix.json"],
          "base64",
        ).toString("utf8"),
      );
      receipt.schemaVersion = 6;
      receipt.contractId = "sora-android-wallet-migration-qualification-v6";
      fixture.envelope.files["android-migration-matrix.json"] =
        jsonBytes(receipt).toString("base64");
      rewrite(fixture);
    },
  ],
  [
    "unprotected-mode",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PATH_INVALID",
    (fixture) => chmodSync(fixture.envelopePath, 0o644),
  ],
  [
    "hardlink-envelope",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PATH_INVALID",
    (fixture) => linkSync(fixture.envelopePath, join(fixture.root, "alias.json")),
  ],
  [
    "symlink-envelope",
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PATH_INVALID",
    (fixture) => {
      const target = join(fixture.root, "target.json");
      writeFileSync(target, readFileSync(fixture.envelopePath), { mode: 0o600 });
      unlinkSync(fixture.envelopePath);
      symlinkSync(target, fixture.envelopePath);
    },
  ],
  [
    "preexisting-output",
    "ANDROID_MIGRATION_CONTROLLER_OUTPUT_ROOT_EXISTS",
    (fixture) => mkdirSync(fixture.outputRoot, { mode: 0o700 }),
  ],
];

for (const [name, expectedCode, mutate] of mutations) {
  const fixture = createFixture();
  try {
    mutate(fixture);
    assert.throws(
      () => extract(fixture),
      (error) => error instanceof Error && error.message === expectedCode,
      name,
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
}

process.stdout.write(
  `Android migration controller envelope v1: positive extraction and ${mutations.length} fail-closed mutations passed.\n`,
);
