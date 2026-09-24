// Source-inspected production baselines. Do not invent retained snapshots for the
// other cache versions accepted by the runtime bridge.
export const ANDROID_PRE_ACCOUNT_RELEASE_BASELINES = Object.freeze({
  "release/sora/2.3.2": "0011066db0865bd1f43dcad6c4d09c40499651d0",
  "release/sora/2.3.3": "2dc0a751025aef8a9f852a791bdaa2275fd3848b",
});
export const ANDROID_MIGRATION_SOURCE_SCHEMAS = Object.freeze([
  50, ...Array.from({ length: 19 }, (_, index) => index + 58),
]);
export const ANDROID_PRE_ACCOUNT_RESTART_PHASES = Object.freeze([
  "schema-transaction", "schema-committed", "account-inserted",
  "cards-inserted", "selection-published", "network-activated",
]);
export const ANDROID_PRE_ACCOUNT_EVIDENCE_CATEGORIES = Object.freeze([
  "retained-room50-provenance",
  ...ANDROID_PRE_ACCOUNT_RESTART_PHASES.flatMap((phase) =>
    ["delete", "wal"].map((mode) => `pre-account-${phase}-${mode}`)),
]);
export const ANDROID_PRE_ACCOUNT_CHECKS = Object.freeze([
  "releasedSnapshotProvenanceVerified",
  "originalRegistrationStateVerified", "preAccountSelectionAbsentVerified",
  "unsuffixedCredentialCiphertextParity", "signingOwnerAndSelectionParity",
  "migrationFlagsParity", "cacheTableRowAndIndexParity",
  "verifiedBackupBeforeBridge", "transactionRollbackQualified",
  "schemaBridgeRestartQualified", "accountInsertRestartQualified",
  "cardInsertRestartQualified", "selectionPublishRestartQualified",
  "networkActivationRestartQualified", "idempotentRetryQualified",
  "noCredentialRewrite",
]);

export const blockedAndroidPreAccountCoverage = () => ({
  sourceSchemaVersion: 50,
  retainedReleaseTags: [],
  releasedSnapshotCount: 0,
  journalModes: ["DELETE", "WAL"],
  retainedSchemaCohortCount: 2,
  restartPhaseCount: 6,
  restartCohortCount: 12,
  checks: Object.fromEntries(ANDROID_PRE_ACCOUNT_CHECKS.map((key) => [key, false])),
});

// This validates aggregate claims only. The caller must first authenticate the
// exact receipt/artifacts with independent protected producer/reviewer pins.
export const validateAndroidPreAccountCoverage = (value, { qualified }) => {
  const fail = () => { throw new Error("pre-account Room50 coverage is incomplete or unreviewed"); };
  const exact = (record, keys) => record !== null && typeof record === "object" &&
    !Array.isArray(record) && Object.keys(record).length === keys.length &&
    keys.every((key) => Object.hasOwn(record, key));
  const template = blockedAndroidPreAccountCoverage();
  if (!exact(value, Object.keys(template)) ||
      !exact(value.checks, ANDROID_PRE_ACCOUNT_CHECKS) ||
      value.sourceSchemaVersion !== 50 ||
      JSON.stringify(value.journalModes) !== JSON.stringify(template.journalModes) ||
      value.retainedSchemaCohortCount !== 2 || value.restartPhaseCount !== 6 ||
      value.restartCohortCount !== 12 ||
      !Array.isArray(value.retainedReleaseTags) ||
      new Set(value.retainedReleaseTags).size !== value.retainedReleaseTags.length ||
      value.retainedReleaseTags.some((tag) => !Object.hasOwn(ANDROID_PRE_ACCOUNT_RELEASE_BASELINES, tag)) ||
      !Number.isSafeInteger(value.releasedSnapshotCount) ||
      Object.values(value.checks).some((claim) => claim !== qualified)) fail();
  if (qualified) {
    if (value.retainedReleaseTags.length === 0 ||
        value.releasedSnapshotCount < value.retainedReleaseTags.length) fail();
  } else if (value.retainedReleaseTags.length !== 0 || value.releasedSnapshotCount !== 0) fail();
};
