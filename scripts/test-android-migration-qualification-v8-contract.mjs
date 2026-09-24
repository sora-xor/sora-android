#!/usr/bin/env node

import assert from "node:assert/strict";
import { createHash, generateKeyPairSync, sign } from "node:crypto";
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  realpathSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import {
  AndroidMigrationQualificationError,
  lintAndroidMigrationQualificationTemplates,
  verifyAndroidMigrationQualificationV8,
} from "./lib/android-migration-qualification-v8.mjs";
import { ANDROID_PRE_ACCOUNT_CHECKS } from "./lib/android-migration-coverage-v1.mjs";
import {
  ANDROID_MIGRATION_RAW_EVIDENCE_V2,
  ANDROID_MIGRATION_RAW_EVIDENCE_AUTHORIZATION,
} from "./lib/android-migration-raw-evidence-v2.mjs";

const sourceRoot = realpathSync(
  new URL("..", import.meta.url).pathname.replace(/\/$/, ""),
);
const fixturePaths = [
  "android-migration-matrix.blocked.json",
  "android-migration-evidence.blocked.json",
  "android-migration-trust.blocked.json",
];

const createRoot = () => {
  const root = realpathSync(
    mkdtempSync(join(tmpdir(), "sora-android-migration-v8-contract-")),
  );
  const destination = join(root, "docs/modernization/qualification");
  mkdirSync(destination, { recursive: true });
  for (const name of fixturePaths) {
    writeFileSync(
      join(destination, name),
      readFileSync(
        join(sourceRoot, "docs/modernization/qualification", name),
      ),
      { flag: "wx", mode: 0o600 },
    );
  }
  return { root, destination };
};

const mutateJson = (path, mutate) => {
  const value = JSON.parse(readFileSync(path, "utf8"));
  mutate(value);
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
};

const expectRejected = (file, mutate, message) => {
  const fixture = createRoot();
  try {
    mutateJson(join(fixture.destination, file), mutate);
    assert.throws(
      () => lintAndroidMigrationQualificationTemplates({ root: fixture.root }),
      (error) =>
        error instanceof AndroidMigrationQualificationError &&
        error.message.includes(message),
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
};

const positive = createRoot();
try {
  lintAndroidMigrationQualificationTemplates({ root: positive.root });
} finally {
  rmSync(positive.root, { recursive: true, force: true });
}

expectRejected(
  "android-migration-matrix.blocked.json",
  (receipt) => {
    receipt.schemaVersion = 7;
    receipt.contractId = "sora-android-wallet-migration-qualification-v7";
  },
  "stale v7 or older",
);

expectRejected(
  "android-migration-evidence.blocked.json",
  (evidence) => {
    evidence.schemaVersion = 2;
    evidence.contractId = "sora-android-wallet-migration-evidence-v2";
  },
  "stale v2 or older",
);

expectRejected(
  "android-migration-evidence.blocked.json",
  (evidence) => {
    delete evidence.artifacts.rawExecutionEvidence;
  },
  "missing or unreviewed fields",
);

expectRejected(
  "android-migration-evidence.blocked.json",
  (evidence) => {
    evidence.artifacts.rawExecutionEvidence.sha256 = "1".repeat(64);
  },
  "fabricated qualification state",
);

expectRejected(
  "android-migration-matrix.blocked.json",
  (receipt) => {
    receipt.rawResultFileCount = 1;
  },
  "fabricated qualification state",
);

expectRejected(
  "android-migration-matrix.blocked.json",
  (receipt) => {
    receipt.qualificationChecks.rawExecutionEvidenceReviewed = true;
  },
  "fabricated qualification state",
);

// These ephemeral keys belong only to isolated, explicitly synthetic test roots.
// Nothing is published to repository evidence or to a protected release authority.
const syntheticProducer = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const syntheticReviewer = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const bytes = (value) => Buffer.from(`${JSON.stringify(value, null, 2)}\n`);
const digest = (value) => createHash("sha256").update(value).digest("hex");
const signedFixture = () => {
  const fixture = createRoot();
  const read = (name) => JSON.parse(readFileSync(join(fixture.destination, name), "utf8"));
  const receipt = read("android-migration-matrix.blocked.json");
  const evidence = read("android-migration-evidence.blocked.json");
  const trust = read("android-migration-trust.blocked.json");
  const now = Math.floor(Date.now() / 1000);
  const identity = {
    appBuildIdentitySha256: digest("synthetic-app-build"),
    deviceClasses: ["Emulator test"],
    operatingSystemBuilds: ["Android 14 (synthetic)"],
  };
  const context = {
    platform: "android", status: "qualified",
    runId: "12345678-1234-4234-9234-123456789abc",
    qualificationSequenceNumber: 7, sourceRevision: "1".repeat(40), identity,
  };
  Object.assign(trust, { status: "qualified", blockingReasons: [] });
  for (const [role, pair, keyId] of [
    ["deviceEvidenceProducer", syntheticProducer, "android-migration-device-producer-synthetic"],
    ["independentReviewer", syntheticReviewer, "android-migration-independent-reviewer-synthetic"],
  ]) {
    const pem = pair.publicKey.export({ type: "spki", format: "pem" });
    writeFileSync(join(fixture.root, `${role}.pem`), pem, { mode: 0o600 });
    Object.assign(trust.authorities[role], { keyId, enabled: true, publicKeyPemSha256: digest(pem) });
  }
  const trustBytes = bytes(trust);
  const producerId = trust.authorities.deviceEvidenceProducer.keyId;
  const reviewerId = trust.authorities.independentReviewer.keyId;
  Object.assign(receipt, context, {
    qualifiedAtEpochSeconds: now - 5, reviewedAtEpochSeconds: now - 10,
    trustRootSha256: digest(trustBytes),
    deviceEvidenceProducerKeyId: producerId, independentReviewerKeyId: reviewerId,
    qualificationContractSha256: digest("synthetic-contract"), blockingReasons: [],
    retainedReleaseSnapshotCount: 20,
    rawResultFileCount: 36, rawResultBundleByteCount: 360,
    rawRunIndexSha256: digest("synthetic-index"),
    rawResultBundleSha256: digest("synthetic-bundle"),
  });
  for (const [key, value] of Object.entries(receipt)) {
    if (typeof value === "boolean") receipt[key] = true;
    if (key.startsWith("executed")) receipt[key] = 1;
  }
  for (const key of Object.keys(receipt.qualificationChecks)) receipt.qualificationChecks[key] = true;
  receipt.preAccountUpgrade.retainedReleaseTags = ["release/sora/2.3.2"];
  receipt.preAccountUpgrade.releasedSnapshotCount = 1;
  for (const key of ANDROID_PRE_ACCOUNT_CHECKS) receipt.preAccountUpgrade.checks[key] = true;
  for (const version of Object.keys(receipt.roomSchemaSha256)) {
    const relative = `core_db/schemas/jp.co.soramitsu.core_db.AppDatabase/${version}.json`;
    mkdirSync(join(fixture.root, "core_db/schemas/jp.co.soramitsu.core_db.AppDatabase"), { recursive: true });
    const schema = readFileSync(join(sourceRoot, relative));
    writeFileSync(join(fixture.root, relative), schema, { mode: 0o600 });
    receipt.roomSchemaSha256[version] = digest(schema);
  }
  Object.assign(evidence, context, {
    producedAtEpochSeconds: now - 20, qualificationContractSha256: receipt.qualificationContractSha256,
    trustRootSha256: digest(trustBytes), deviceEvidenceProducerKeyId: producerId,
    independentReviewerKeyId: reviewerId, blockingReasons: [],
  });
  for (const key of Object.keys(evidence.chronology)) evidence.chronology[key] = now - 40;
  evidence.chronology.runStartedAtEpochSeconds = now - 60;
  evidence.chronology.runFinishedAtEpochSeconds = now - 30;
  const pick = (keys) => Object.fromEntries(keys.map((key) => [key, receipt[key]]));
  const preAccount = () => structuredClone(receipt.preAccountUpgrade);
  const artifact = (contractId, aggregate, schemaVersion = 2) => ({
    schemaVersion, contractId, ...context, producedAtEpochSeconds: now - 40,
    privacy: evidence.privacy, aggregate,
  });
  const artifacts = {
    retainedReleaseSnapshotManifest: artifact("sora-android-wallet-migration-retained-snapshot-manifest-v2", {
      preAccountUpgrade: preAccount(), ...pick(["retainedReleaseSnapshotCount", "sourceSchemaVersions", "targetSchemaVersion"]),
      allSnapshotFilesRegular: true, allSnapshotHashesVerified: true,
      allSnapshotDatabasesOpenedReadOnly: true, settingsSnapshotsVerified: true,
    }),
    testResult: artifact("sora-android-wallet-migration-test-results-v1", {
      ...pick(Object.keys(receipt).filter((key) => key.startsWith("executed"))),
      testFailureCount: 0, testUnexpectedFailureCount: 0,
    }, 1),
    encryptedStorageEvidence: artifact("sora-android-wallet-migration-encrypted-storage-evidence-v2", {
      preAccountUpgrade: preAccount(),
      ...pick(["encryptedStoragePhaseCount", "encryptedSecretCiphertextParity", "wrappedAesKeyParity", "keystoreAliasContinuity", "noCredentialRewrite"]),
      authenticatedEnvelopeTamperQualified: true, wrappedKeyFailureQualified: true,
      missingKeystoreAliasQualified: true, rawValuesExcluded: true,
    }),
    deviceExecutionEvidence: artifact("sora-android-wallet-migration-device-execution-evidence-v2", {
      preAccountUpgrade: preAccount(), ...pick(["retainedSchemaCohortCount", "productionPathCohortCount"]),
      interruptedMigrationQualified: true, processDeathRestartQualified: true,
      reinstallUpgradeQualified: true, rollbackQualified: true, lowStorageQualified: true,
    }),
    rawExecutionEvidence: {
      ...context, ...ANDROID_MIGRATION_RAW_EVIDENCE_V2,
      producedAtEpochSeconds: now - 40, privacy: evidence.privacy,
      authorization: { ...ANDROID_MIGRATION_RAW_EVIDENCE_AUTHORIZATION },
      aggregate: {
        ...pick(["rawRunIndexSha256", "rawResultFileCount", "rawResultBundleByteCount", "rawResultBundleSha256"]),
        requiredSuiteCount: 7, preAccountEvidenceArtifactCount: 13,
        allPreAccountEvidencePresent: true, apkIdentityArtifactCount: 2,
        methodInventoryArtifactCount: 7, reportArtifactCount: 7, transcriptArtifactCount: 7,
        allRequiredSuitesPresent: true, allInputsRegular: true, allInputsHashVerified: true,
        allInputsWithinRunRoot: true, rawValuesExcludedFromPublicEvidence: true,
      },
      blockingReasons: ["Collection output only: this artifact does not authorize qualification, release, or production mutation without the distinct signed v8 receipt and v3 evidence manifest."],
    },
  };
  const persist = () => {
    for (const [key, value] of Object.entries(artifacts)) {
      const encoded = bytes(value);
      writeFileSync(join(fixture.root, evidence.artifacts[key].relativePath), encoded, { mode: 0o600 });
      evidence.artifacts[key].sha256 = digest(encoded);
    }
    receipt.retainedReleaseSnapshotManifestSha256 = evidence.artifacts.retainedReleaseSnapshotManifest.sha256;
    receipt.aggregateTestResultSha256 = evidence.artifacts.testResult.sha256;
    receipt.rawExecutionEvidenceSha256 = evidence.artifacts.rawExecutionEvidence.sha256;
    const evidenceBytes = bytes(evidence);
    receipt.evidenceManifestSha256 = digest(evidenceBytes);
    const receiptBytes = bytes(receipt);
    for (const [name, value] of [["android-migration-matrix.json", receiptBytes], ["android-migration-evidence.json", evidenceBytes], ["android-migration-trust.json", trustBytes]]) {
      writeFileSync(join(fixture.destination, name), value, { mode: 0o600 });
    }
    for (const [name, value, pair] of [["receipt.der", receiptBytes, syntheticReviewer], ["producer.der", evidenceBytes, syntheticProducer], ["reviewer.der", evidenceBytes, syntheticReviewer]]) {
      writeFileSync(join(fixture.root, name), sign("sha256", value, pair.privateKey), { mode: 0o600 });
    }
  };
  const environment = {
    PRODUCTION_CANDIDATE_SOURCE_REVISION: context.sourceRevision,
    ANDROID_MIGRATION_QUALIFICATION_RUN_ID: context.runId,
    ANDROID_MIGRATION_QUALIFICATION_SEQUENCE_NUMBER: "7",
    ANDROID_MIGRATION_QUALIFICATION_APP_BUILD_IDENTITY_SHA256: identity.appBuildIdentitySha256,
    ANDROID_MIGRATION_QUALIFICATION_CONTRACT_SHA256: receipt.qualificationContractSha256,
    ANDROID_MIGRATION_QUALIFICATION_TRUST_SHA256: digest(trustBytes),
    ANDROID_MIGRATION_QUALIFICATION_DEVICE_PRODUCER_PUBLIC_KEY_SHA256: trust.authorities.deviceEvidenceProducer.publicKeyPemSha256,
    ANDROID_MIGRATION_QUALIFICATION_REVIEWER_PUBLIC_KEY_SHA256: trust.authorities.independentReviewer.publicKeyPemSha256,
    ANDROID_MIGRATION_QUALIFICATION_DEVICE_PRODUCER_PUBLIC_KEY_PATH: join(fixture.root, "deviceEvidenceProducer.pem"),
    ANDROID_MIGRATION_QUALIFICATION_REVIEWER_PUBLIC_KEY_PATH: join(fixture.root, "independentReviewer.pem"),
    ANDROID_MIGRATION_QUALIFICATION_RECEIPT_SIGNATURE_PATH: join(fixture.root, "receipt.der"),
    ANDROID_MIGRATION_QUALIFICATION_EVIDENCE_PRODUCER_SIGNATURE_PATH: join(fixture.root, "producer.der"),
    ANDROID_MIGRATION_QUALIFICATION_EVIDENCE_REVIEWER_SIGNATURE_PATH: join(fixture.root, "reviewer.der"),
  };
  persist();
  return { ...fixture, receipt, evidence, artifacts, persist, environment };
};
const verify = (fixture) => verifyAndroidMigrationQualificationV8({ root: fixture.root, environment: fixture.environment });
const signedPositive = signedFixture();
try {
  assert.equal(verify(signedPositive).receipt.preAccountUpgrade.sourceSchemaVersion, 50);
} finally { rmSync(signedPositive.root, { recursive: true, force: true }); }

const signedMutations = [
  ["prior receipt", (f) => { f.receipt.schemaVersion = 7; f.receipt.contractId = "sora-android-wallet-migration-qualification-v7"; }, /stale v7/],
  ["prior evidence", (f) => { f.evidence.schemaVersion = 2; f.evidence.contractId = "sora-android-wallet-migration-evidence-v2"; }, /stale v2/],
  ["missing Room50", (f) => { f.receipt.sourceSchemaVersions.shift(); }, /aggregate qualification/],
  ["old cohort totals", (f) => { f.receipt.retainedSchemaCohortCount = 76; f.receipt.singleAccountCohortCount = 38; }, /aggregate qualification/],
  ["missing pre-account evidence", (f) => { delete f.receipt.preAccountUpgrade; }, /missing or unreviewed/],
  ["no released snapshot", (f) => { f.receipt.preAccountUpgrade.releasedSnapshotCount = 0; }, /Room50 coverage/],
  ["invented baseline", (f) => { f.receipt.preAccountUpgrade.retainedReleaseTags = ["release/sora/2.3.1"]; }, /Room50 coverage/],
  ["missing WAL", (f) => { f.receipt.preAccountUpgrade.journalModes = ["DELETE"]; }, /Room50 coverage/],
  ["missing phase", (f) => { f.receipt.preAccountUpgrade.restartPhaseCount = 5; }, /Room50 coverage/],
  ["incomplete phase modes", (f) => { f.receipt.preAccountUpgrade.restartCohortCount = 11; }, /Room50 coverage/],
  ...ANDROID_PRE_ACCOUNT_CHECKS.map((key) => [key, (f) => { f.receipt.preAccountUpgrade.checks[key] = false; }, /Room50 coverage/]),
  ...["retainedReleaseSnapshotManifest", "encryptedStorageEvidence", "deviceExecutionEvidence"].map((key) =>
    [`${key} mismatch`, (f) => { f.artifacts[key].aggregate.preAccountUpgrade.retainedReleaseTags = ["release/sora/2.3.3"]; }, /pre-account aggregate differs/]),
  ["old raw contract", (f) => { f.artifacts.rawExecutionEvidence.schemaVersion = 1; }, /not exact/],
  ["missing raw suite", (f) => { f.artifacts.rawExecutionEvidence.aggregate.requiredSuiteCount = 6; }, /aggregate is incomplete/],
  ["missing raw phases", (f) => { f.artifacts.rawExecutionEvidence.aggregate.preAccountEvidenceArtifactCount = 12; }, /counts are incomplete/],
  ["raw authorization", (f) => { f.artifacts.rawExecutionEvidence.authorization.authorizesQualification = true; }, /non-authorizing/],
];
for (const [label, mutate, expected] of signedMutations) {
  const fixture = signedFixture();
  try {
    mutate(fixture);
    fixture.persist(); // Valid signatures cannot excuse omitted legacy coverage.
    assert.throws(() => verify(fixture), expected, label);
  } finally { rmSync(fixture.root, { recursive: true, force: true }); }
}
const changedSignature = signedFixture();
try {
  changedSignature.receipt.preAccountUpgrade.retainedReleaseTags = ["release/sora/2.3.3"];
  writeFileSync(join(changedSignature.destination, "android-migration-matrix.json"), bytes(changedSignature.receipt));
  assert.throws(() => verify(changedSignature), /signature is invalid/);
} finally { rmSync(changedSignature.root, { recursive: true, force: true }); }

process.stdout.write(
  `Android migration v8/v3: exact templates, authenticated synthetic fixture, 6 template and ${signedMutations.length + 1} authenticated-path rejection cases passed\n`,
);
