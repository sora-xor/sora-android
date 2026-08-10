import { createHash } from "node:crypto";
import { lstatSync, readdirSync, realpathSync } from "node:fs";
import { isAbsolute, relative, resolve } from "node:path";

import {
  hashStableRegularFile,
  readStrictJsonFile,
} from "./strict-evidence.mjs";

const MAX_INDEX_BYTES = 2 * 1024 * 1024;
const MAX_RAW_FILE_BYTES = 128 * 1024 * 1024;
const MAX_RAW_BUNDLE_BYTES = 4 * 1024 * 1024 * 1024;
const MAX_RAW_FILE_COUNT = 50_000;
const SHA256 = /^[0-9a-f]{64}$/;
const SOURCE_REVISION = /^[0-9a-f]{40}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export const ANDROID_MIGRATION_RAW_RUN_INDEX_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-android-wallet-migration-raw-run-index-v1",
  platform: "android",
  status: "complete",
});

export const ANDROID_MIGRATION_RAW_EVIDENCE_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-android-wallet-migration-raw-execution-evidence-v1",
  platform: "android",
  status: "collected-unreviewed",
});

export const ANDROID_MIGRATION_RAW_EVIDENCE_AUTHORIZATION = Object.freeze({
  authorizesQualification: false,
  authorizesRelease: false,
  authorizesProductionMutation: false,
});

const NON_AUTHORIZATION_WARNING =
  "Collection output only: this artifact does not authorize qualification, release, or production mutation without the distinct signed v7 receipt and v2 evidence manifest.";

export const ANDROID_MIGRATION_RAW_EVIDENCE_SUITES = Object.freeze([
  "encrypted-wallet-migration-storage",
  "migration-manager-production-path-qualification",
  "migration-manager-safety",
  "sora2-address-codec",
  "wallet-identity-migration-75",
  "wallet-upgrade-backup",
]);

export const ANDROID_MIGRATION_RAW_EVIDENCE_CATEGORIES = Object.freeze([
  "apk-identity",
  "method-inventory",
  "report",
  "transcript",
]);

export const ANDROID_MIGRATION_RAW_EVIDENCE_PRIVACY_KEYS = Object.freeze([
  "aggregateOnly",
  "accountIdentifiersIncluded",
  "addressesIncluded",
  "deviceIdentifiersIncluded",
  "phrasesOrSeedsIncluded",
  "privateKeysIncluded",
  "publicKeysIncluded",
  "ciphertextsIncluded",
  "wrappedKeysIncluded",
  "keystoreMaterialIncluded",
  "rawSignedPayloadsIncluded",
  "perWalletRecordsIncluded",
]);

export const ANDROID_MIGRATION_RAW_EVIDENCE_AGGREGATE_KEYS = Object.freeze([
  "rawRunIndexSha256",
  "rawResultFileCount",
  "rawResultBundleByteCount",
  "rawResultBundleSha256",
  "requiredSuiteCount",
  "apkIdentityArtifactCount",
  "methodInventoryArtifactCount",
  "reportArtifactCount",
  "transcriptArtifactCount",
  "allRequiredSuitesPresent",
  "allInputsRegular",
  "allInputsHashVerified",
  "allInputsWithinRunRoot",
  "rawValuesExcludedFromPublicEvidence",
]);

const INDEX_KEYS = Object.freeze([
  "schemaVersion",
  "contractId",
  "platform",
  "status",
  "runId",
  "qualificationSequenceNumber",
  "sourceRevision",
  "producedAtEpochSeconds",
  "runStartedAtEpochSeconds",
  "runFinishedAtEpochSeconds",
  "identity",
  "files",
]);
const IDENTITY_KEYS = Object.freeze([
  "appBuildIdentitySha256",
  "deviceClasses",
  "operatingSystemBuilds",
]);
const FILE_KEYS = Object.freeze([
  "relativePath",
  "category",
  "suite",
  "byteCount",
  "sha256",
]);
const OUTPUT_KEYS = Object.freeze([
  "schemaVersion",
  "contractId",
  "platform",
  "status",
  "runId",
  "qualificationSequenceNumber",
  "sourceRevision",
  "producedAtEpochSeconds",
  "identity",
  "privacy",
  "authorization",
  "aggregate",
  "blockingReasons",
]);

export class AndroidMigrationRawEvidenceError extends Error {}

const fail = (message) => {
  throw new AndroidMigrationRawEvidenceError(message);
};

const exactKeys = (value, keys, label) => {
  if (
    value === null ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    Object.keys(value).length !== keys.length ||
    !keys.every((key) => Object.hasOwn(value, key))
  ) {
    fail(`${label} contains missing or unreviewed fields`);
  }
};

const requireSha256 = (value, label) => {
  if (typeof value !== "string" || !SHA256.test(value) || /^0+$/.test(value)) {
    fail(`${label} must be a nonzero lowercase SHA-256`);
  }
  return value;
};

const requirePositiveInteger = (value, label) => {
  if (!Number.isSafeInteger(value) || value <= 0) {
    fail(`${label} must be a positive safe integer`);
  }
  return value;
};

const validateIdentity = (identity) => {
  exactKeys(identity, IDENTITY_KEYS, "raw-run identity");
  requireSha256(identity.appBuildIdentitySha256, "raw-run app-build identity");
  for (const [key, pattern] of [
    ["deviceClasses", /^(?:Android|Pixel|Samsung|Xiaomi|OnePlus|Motorola|Sony|Nokia|Emulator)[A-Za-z0-9 ,._()\/-]{0,63}$/],
    ["operatingSystemBuilds", /^Android [0-9]+(?:\.[0-9]+){0,2} \([0-9A-Za-z._-]{1,32}\)$/],
  ]) {
    const values = identity[key];
    if (
      !Array.isArray(values) ||
      values.length === 0 ||
      values.length > 64 ||
      new Set(values).size !== values.length
    ) {
      fail(`raw-run identity.${key} must be a nonempty unique bounded array`);
    }
    for (const item of values) {
      if (
        typeof item !== "string" ||
        Buffer.byteLength(item) > 128 ||
        !pattern.test(item) ||
        /(?:identifier|serial|device-id|android-id|[0-9a-f]{40}|[0-9a-f]{64})/i.test(item)
      ) {
        fail(`raw-run identity.${key} contains an identifier or invalid value`);
      }
    }
  }
};

const canonicalRoot = (root) => {
  if (typeof root !== "string" || !isAbsolute(root) || resolve(root) !== root) {
    fail("raw-run root must be a canonical absolute path");
  }
  let stat;
  try {
    stat = lstatSync(root);
  } catch {
    fail("raw-run root is absent");
  }
  if (
    !stat.isDirectory() ||
    stat.isSymbolicLink() ||
    realpathSync(root) !== root ||
    (typeof process.getuid === "function" && stat.uid !== process.getuid()) ||
    (stat.mode & 0o077) !== 0
  ) {
    fail("raw-run root must be a canonical owner-only non-symlink directory");
  }
  return root;
};

const stableIdentity = (stat) => [
  stat.dev,
  stat.ino,
  stat.mode,
  stat.nlink,
  stat.uid,
  stat.size,
  stat.mtimeMs,
  stat.ctimeMs,
];

const snapshotRawRunTree = (root) => {
  const identities = new Map();
  const seenFiles = new Set();
  const walk = (directory, prefix) => {
    let names;
    try {
      names = readdirSync(directory).sort();
    } catch {
      fail("raw-run tree cannot be enumerated safely");
    }
    for (const name of names) {
      if (!/^[A-Za-z0-9._-]+$/.test(name) || name === "." || name === "..") {
        fail("raw-run tree contains an unsafe path component");
      }
      const relativePath = prefix ? `${prefix}/${name}` : name;
      const absolute = resolve(directory, name);
      let stat;
      try {
        stat = lstatSync(absolute);
      } catch {
        fail("raw-run tree changed during inventory");
      }
      if (
        stat.isSymbolicLink() ||
        realpathSync(absolute) !== absolute ||
        (typeof process.getuid === "function" && stat.uid !== process.getuid()) ||
        (stat.mode & 0o077) !== 0
      ) {
        fail("raw-run tree contains an aliased or non-private entry");
      }
      identities.set(relativePath, stableIdentity(stat));
      if (stat.isDirectory()) {
        walk(absolute, relativePath);
      } else if (stat.isFile()) {
        if (stat.nlink !== 1) {
          fail("raw-run tree contains a hard-linked file");
        }
        const fileIdentity = `${stat.dev}:${stat.ino}`;
        if (seenFiles.has(fileIdentity)) {
          fail("raw-run tree contains an inode alias");
        }
        seenFiles.add(fileIdentity);
      } else {
        fail("raw-run tree contains a special file");
      }
    }
  };
  const rootStat = lstatSync(root);
  identities.set("", stableIdentity(rootStat));
  walk(root, "");
  return identities;
};

const sameTreeSnapshot = (left, right) =>
  left.size === right.size &&
  [...left].every(
    ([path, identity]) =>
      right.has(path) &&
      JSON.stringify(right.get(path)) === JSON.stringify(identity),
  );

const canonicalRelativePath = (value, root) => {
  if (
    typeof value !== "string" ||
    value.length === 0 ||
    value.length > 512 ||
    value.includes("\\") ||
    value.startsWith("/") ||
    value.split("/").some((part) => !part || part === "." || part === "..") ||
    !/^[A-Za-z0-9._/-]+$/.test(value)
  ) {
    fail("raw-run file path is not canonical");
  }
  const absolute = resolve(root, value);
  if (relative(root, absolute) !== value) {
    fail("raw-run file path escapes the protected root");
  }
  return absolute;
};

const validateIndex = (index) => {
  exactKeys(index, INDEX_KEYS, "raw-run index");
  if (
    index.schemaVersion !== ANDROID_MIGRATION_RAW_RUN_INDEX_V1.schemaVersion ||
    index.contractId !== ANDROID_MIGRATION_RAW_RUN_INDEX_V1.contractId ||
    index.platform !== ANDROID_MIGRATION_RAW_RUN_INDEX_V1.platform ||
    index.status !== ANDROID_MIGRATION_RAW_RUN_INDEX_V1.status
  ) {
    fail("raw-run index is not exact v1");
  }
  if (!UUID.test(index.runId) || index.runId === "00000000-0000-0000-0000-000000000000") {
    fail("raw-run ID must be a nonzero lowercase canonical UUID");
  }
  requirePositiveInteger(index.qualificationSequenceNumber, "raw-run sequence");
  if (
    typeof index.sourceRevision !== "string" ||
    !SOURCE_REVISION.test(index.sourceRevision) ||
    /^0+$/.test(index.sourceRevision)
  ) {
    fail("raw-run source revision must be nonzero lowercase 40-hex");
  }
  const started = requirePositiveInteger(index.runStartedAtEpochSeconds, "raw-run start");
  const finished = requirePositiveInteger(index.runFinishedAtEpochSeconds, "raw-run finish");
  const produced = requirePositiveInteger(index.producedAtEpochSeconds, "raw-run index production time");
  if (!(started <= finished && finished <= produced) || finished - started > 172800) {
    fail("raw-run chronology is invalid or exceeds 48 hours");
  }
  validateIdentity(index.identity);
  if (
    !Array.isArray(index.files) ||
    index.files.length === 0 ||
    index.files.length > MAX_RAW_FILE_COUNT
  ) {
    fail("raw-run file inventory must be nonempty and bounded");
  }
};

const fixedPrivacy = () =>
  Object.fromEntries(
    ANDROID_MIGRATION_RAW_EVIDENCE_PRIVACY_KEYS.map((key) => [
      key,
      key === "aggregateOnly",
    ]),
  );

const canonicalBundleSha256 = (records) => {
  const digest = createHash("sha256");
  digest.update("sora-android-wallet-migration-raw-result-bundle-v1\n", "utf8");
  for (const record of records) {
    digest.update(record.relativePath, "utf8");
    digest.update("\0", "utf8");
    digest.update(String(record.byteCount), "ascii");
    digest.update("\0", "utf8");
    digest.update(record.sha256, "ascii");
    digest.update("\n", "utf8");
  }
  return digest.digest("hex");
};

export const validateAndroidMigrationRawEvidenceV1 = (record) => {
  exactKeys(record, OUTPUT_KEYS, "raw execution evidence");
  if (
    record.schemaVersion !== ANDROID_MIGRATION_RAW_EVIDENCE_V1.schemaVersion ||
    record.contractId !== ANDROID_MIGRATION_RAW_EVIDENCE_V1.contractId ||
    record.platform !== ANDROID_MIGRATION_RAW_EVIDENCE_V1.platform ||
    record.status !== ANDROID_MIGRATION_RAW_EVIDENCE_V1.status
  ) {
    fail("raw execution evidence is not exact collected-unreviewed v1");
  }
  if (!UUID.test(record.runId) || record.runId === "00000000-0000-0000-0000-000000000000") {
    fail("raw execution evidence run ID is invalid");
  }
  requirePositiveInteger(record.qualificationSequenceNumber, "raw execution evidence sequence");
  if (!SOURCE_REVISION.test(record.sourceRevision) || /^0+$/.test(record.sourceRevision)) {
    fail("raw execution evidence source revision is invalid");
  }
  requirePositiveInteger(record.producedAtEpochSeconds, "raw execution evidence production time");
  validateIdentity(record.identity);
  exactKeys(record.privacy, ANDROID_MIGRATION_RAW_EVIDENCE_PRIVACY_KEYS, "raw execution evidence privacy");
  if (JSON.stringify(record.privacy) !== JSON.stringify(fixedPrivacy())) {
    fail("raw execution evidence must be aggregate-only and exclude protected values");
  }
  exactKeys(
    record.authorization,
    Object.keys(ANDROID_MIGRATION_RAW_EVIDENCE_AUTHORIZATION),
    "raw execution evidence authorization",
  );
  if (
    JSON.stringify(record.authorization) !==
    JSON.stringify(ANDROID_MIGRATION_RAW_EVIDENCE_AUTHORIZATION)
  ) {
    fail("raw execution evidence must be explicitly non-authorizing");
  }
  exactKeys(
    record.aggregate,
    ANDROID_MIGRATION_RAW_EVIDENCE_AGGREGATE_KEYS,
    "raw execution evidence aggregate",
  );
  for (const key of [
    "rawRunIndexSha256",
    "rawResultBundleSha256",
  ]) {
    requireSha256(record.aggregate[key], `raw execution evidence ${key}`);
  }
  for (const key of [
    "rawResultFileCount",
    "rawResultBundleByteCount",
    "requiredSuiteCount",
    "apkIdentityArtifactCount",
    "methodInventoryArtifactCount",
    "reportArtifactCount",
    "transcriptArtifactCount",
  ]) {
    requirePositiveInteger(record.aggregate[key], `raw execution evidence ${key}`);
  }
  if (
    record.aggregate.requiredSuiteCount !== ANDROID_MIGRATION_RAW_EVIDENCE_SUITES.length ||
    [
      "allRequiredSuitesPresent",
      "allInputsRegular",
      "allInputsHashVerified",
      "allInputsWithinRunRoot",
      "rawValuesExcludedFromPublicEvidence",
    ].some((key) => record.aggregate[key] !== true)
  ) {
    fail("raw execution evidence aggregate is incomplete");
  }
  if (
    record.aggregate.rawResultFileCount > MAX_RAW_FILE_COUNT ||
    record.aggregate.rawResultBundleByteCount > MAX_RAW_BUNDLE_BYTES ||
    record.aggregate.apkIdentityArtifactCount < 2 ||
    record.aggregate.methodInventoryArtifactCount < record.aggregate.requiredSuiteCount ||
    record.aggregate.reportArtifactCount < record.aggregate.requiredSuiteCount ||
    record.aggregate.transcriptArtifactCount < record.aggregate.requiredSuiteCount ||
    record.aggregate.rawResultFileCount !==
      record.aggregate.apkIdentityArtifactCount +
        record.aggregate.methodInventoryArtifactCount +
        record.aggregate.reportArtifactCount +
        record.aggregate.transcriptArtifactCount
  ) {
    fail("raw execution evidence counts are incomplete or inconsistent");
  }
  if (
    !Array.isArray(record.blockingReasons) ||
    record.blockingReasons.length !== 1 ||
    record.blockingReasons[0] !== NON_AUTHORIZATION_WARNING
  ) {
    fail("raw execution evidence must retain its non-authorization warning");
  }
  return record;
};

export const collectAndroidMigrationRawEvidenceV1 = ({ root, indexPath } = {}) => {
  const protectedRoot = canonicalRoot(root);
  const initialTree = snapshotRawRunTree(protectedRoot);
  const expectedIndexPath = resolve(protectedRoot, "raw-run-index.json");
  const protectedIndexPath = indexPath ?? expectedIndexPath;
  if (protectedIndexPath !== expectedIndexPath) {
    fail("raw-run index must use the fixed raw-run-index.json path");
  }
  const indexSnapshot = readStrictJsonFile(protectedIndexPath, MAX_INDEX_BYTES);
  if (indexSnapshot === null) {
    fail("raw-run index is absent, unstable, symbolic, oversized, or invalid strict JSON");
  }
  const index = indexSnapshot.value;
  validateIndex(index);

  const seenPaths = new Set();
  const records = [];
  const categoryCounts = Object.fromEntries(
    ANDROID_MIGRATION_RAW_EVIDENCE_CATEGORIES.map((category) => [category, 0]),
  );
  const suiteCategories = Object.fromEntries(
    ANDROID_MIGRATION_RAW_EVIDENCE_SUITES.map((suite) => [suite, new Set()]),
  );
  let totalBytes = 0;

  for (const [position, entry] of index.files.entries()) {
    const label = `raw-run index file ${position + 1}`;
    exactKeys(entry, FILE_KEYS, label);
    const absolute = canonicalRelativePath(entry.relativePath, protectedRoot);
    if (entry.relativePath === "raw-run-index.json" || seenPaths.has(entry.relativePath)) {
      fail(`${label} duplicates or includes the raw-run index`);
    }
    seenPaths.add(entry.relativePath);
    if (!ANDROID_MIGRATION_RAW_EVIDENCE_CATEGORIES.includes(entry.category)) {
      fail(`${label} has an unreviewed artifact category`);
    }
    if (!ANDROID_MIGRATION_RAW_EVIDENCE_SUITES.includes(entry.suite)) {
      fail(`${label} has an unreviewed suite`);
    }
    if (
      !entry.relativePath.startsWith(`${entry.suite}/`) ||
      /(?:^|\/)(?:[0-9a-f]{40}|[0-9a-f]{64}|[0-9a-f]{8}-[0-9a-f-]{27,})(?:[./]|$)/i.test(
        entry.relativePath,
      )
    ) {
      fail(`${label} is not in its sanitized suite namespace`);
    }
    const byteCount = requirePositiveInteger(entry.byteCount, `${label}.byteCount`);
    if (byteCount > MAX_RAW_FILE_BYTES) {
      fail(`${label} exceeds the per-file byte limit`);
    }
    const expectedSha256 = requireSha256(entry.sha256, `${label}.sha256`);
    const observed = hashStableRegularFile(absolute, MAX_RAW_FILE_BYTES);
    if (
      observed === null ||
      observed.bytes !== byteCount ||
      observed.sha256 !== expectedSha256
    ) {
      fail(`${label} is absent, unstable, symbolic, oversized, or differs from its index`);
    }
    totalBytes += byteCount;
    if (!Number.isSafeInteger(totalBytes) || totalBytes > MAX_RAW_BUNDLE_BYTES) {
      fail("raw-run bundle exceeds the total byte limit");
    }
    categoryCounts[entry.category] += 1;
    suiteCategories[entry.suite].add(entry.category);
    records.push({
      relativePath: entry.relativePath,
      byteCount,
      sha256: expectedSha256,
    });
  }

  const indexedPaths = new Set([
    "raw-run-index.json",
    ...index.files.map((entry) => entry.relativePath),
  ]);
  const observedFilePaths = new Set(
    [...initialTree.entries()]
      .filter(([path, identity]) => path && (identity[2] & 0o170000) === 0o100000)
      .map(([path]) => path),
  );
  if (
    indexedPaths.size !== observedFilePaths.size ||
    [...indexedPaths].some((path) => !observedFilePaths.has(path))
  ) {
    fail("raw-run index does not cover the exact protected file inventory");
  }

  records.sort((left, right) =>
    left.relativePath < right.relativePath
      ? -1
      : left.relativePath > right.relativePath
        ? 1
        : 0,
  );
  const allRequiredSuitesPresent = Object.values(suiteCategories).every(
    (categories) =>
      categories.has("method-inventory") &&
      categories.has("report") &&
      categories.has("transcript"),
  );
  if (!allRequiredSuitesPresent || categoryCounts["apk-identity"] < 2) {
    fail("raw-run inventory lacks required per-suite method/report/transcript or APK identity evidence");
  }
  const finalTree = snapshotRawRunTree(protectedRoot);
  if (!sameTreeSnapshot(initialTree, finalTree)) {
    fail("raw-run tree changed during collection");
  }
  for (const record of records) {
    const observed = hashStableRegularFile(
      resolve(protectedRoot, record.relativePath),
      MAX_RAW_FILE_BYTES,
    );
    if (
      observed === null ||
      observed.bytes !== record.byteCount ||
      observed.sha256 !== record.sha256
    ) {
      fail("raw-run file changed during final collection recheck");
    }
  }
  if (!sameTreeSnapshot(initialTree, snapshotRawRunTree(protectedRoot))) {
    fail("raw-run tree changed during terminal collection recheck");
  }

  const output = {
    ...ANDROID_MIGRATION_RAW_EVIDENCE_V1,
    runId: index.runId,
    qualificationSequenceNumber: index.qualificationSequenceNumber,
    sourceRevision: index.sourceRevision,
    producedAtEpochSeconds: index.producedAtEpochSeconds,
    identity: index.identity,
    privacy: fixedPrivacy(),
    authorization: { ...ANDROID_MIGRATION_RAW_EVIDENCE_AUTHORIZATION },
    aggregate: {
      rawRunIndexSha256: indexSnapshot.sha256,
      rawResultFileCount: records.length,
      rawResultBundleByteCount: totalBytes,
      rawResultBundleSha256: canonicalBundleSha256(records),
      requiredSuiteCount: ANDROID_MIGRATION_RAW_EVIDENCE_SUITES.length,
      apkIdentityArtifactCount: categoryCounts["apk-identity"],
      methodInventoryArtifactCount: categoryCounts["method-inventory"],
      reportArtifactCount: categoryCounts.report,
      transcriptArtifactCount: categoryCounts.transcript,
      allRequiredSuitesPresent,
      allInputsRegular: true,
      allInputsHashVerified: true,
      allInputsWithinRunRoot: true,
      rawValuesExcludedFromPublicEvidence: true,
    },
    blockingReasons: [NON_AUTHORIZATION_WARNING],
  };
  validateAndroidMigrationRawEvidenceV1(output);
  return Object.freeze({
    record: output,
    bytes: Buffer.from(`${JSON.stringify(output, null, 2)}\n`, "utf8"),
  });
};
