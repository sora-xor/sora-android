import {
  constants as fsConstants,
  closeSync,
  fstatSync,
  lstatSync,
  openSync,
  readSync,
  realpathSync,
} from "node:fs";
import { createHash, createPublicKey, verify as verifySignature } from "node:crypto";
import { isAbsolute, relative, resolve } from "node:path";

import {
  validateAndroidMigrationRawEvidenceV1,
} from "./android-migration-raw-evidence-v1.mjs";

const MAX_JSON_BYTES = 2 * 1024 * 1024;
const MAX_KEY_BYTES = 16 * 1024;
const MAX_SIGNATURE_BYTES = 16 * 1024;
const SHA256 = /^[0-9a-f]{64}$/;
const SOURCE_REVISION = /^[0-9a-f]{40}$/;
const KEY_ID = /^[a-z0-9][a-z0-9._-]{2,127}$/;
const RECEIPT = "docs/modernization/qualification/android-migration-matrix.json";
const EVIDENCE = "docs/modernization/qualification/android-migration-evidence.json";
const TRUST = "docs/modernization/qualification/android-migration-trust.json";
const RECEIPT_TEMPLATE = "docs/modernization/qualification/android-migration-matrix.blocked.json";
const EVIDENCE_TEMPLATE = "docs/modernization/qualification/android-migration-evidence.blocked.json";
const TRUST_TEMPLATE = "docs/modernization/qualification/android-migration-trust.blocked.json";
const ARTIFACT_PATHS = Object.freeze({
  retainedReleaseSnapshotManifest:
    "docs/modernization/qualification/android-migration-retained-snapshot-manifest.json",
  testResult:
    "docs/modernization/qualification/android-migration-test-results.json",
  encryptedStorageEvidence:
    "docs/modernization/qualification/android-migration-encrypted-storage-evidence.json",
  deviceExecutionEvidence:
    "docs/modernization/qualification/android-migration-device-execution-evidence.json",
  rawExecutionEvidence:
    "docs/modernization/qualification/android-migration-raw-execution-evidence.json",
});
const RECEIPT_PRIVACY_KEYS = Object.freeze([
  "aggregateOnly", "accountIdentifiersIncluded", "addressesIncluded",
  "deviceIdentifiersIncluded", "secretsIncluded", "phrasesOrSeedsIncluded",
  "privateKeysIncluded", "publicKeysIncluded", "ciphertextsIncluded",
  "wrappedKeysIncluded", "keystoreMaterialIncluded", "signedPayloadsIncluded",
  "rawSignedPayloadsIncluded", "perWalletRecordsIncluded",
]);
const EVIDENCE_PRIVACY_KEYS = RECEIPT_PRIVACY_KEYS.filter(
  (key) => key !== "secretsIncluded" && key !== "signedPayloadsIncluded",
);
const RECEIPT_KEYS = Object.freeze([
  "schemaVersion", "contractId", "platform", "status", "runId",
  "qualificationSequenceNumber", "sourceRevision", "qualifiedAtEpochSeconds",
  "reviewedAtEpochSeconds", "trustRootSha256", "evidenceManifestSha256",
  "deviceEvidenceProducerKeyId", "independentReviewerKeyId", "identity", "privacy",
  "sourceSchemaVersions", "targetSchemaVersion", "retainedSchemaCohortCount",
  "singleAccountCohortCount", "multiAccountCohortCount", "successfulSecretSourceCohortCount",
  "secretFailureCohortCount", "currentSchemaSnapshotCohortCount", "productionPathCohortCount",
  "encryptedStoragePhaseCount", "retainedReleaseSnapshotCount",
  "retainedReleaseSnapshotManifestSha256", "executedWalletIdentityMigration75TestCount",
  "executedWalletUpgradeBackupTestCount", "executedMigrationManagerSafetyTestCount",
  "executedMigrationManagerProductionPathQualificationTestCount",
  "executedEncryptedWalletMigrationStorageTestCount", "executedSora2AddressCodecTestCount",
  "testFailureCount", "testUnexpectedFailureCount", "aggregateTestResultSha256",
  "rawExecutionEvidenceSha256", "rawRunIndexSha256", "rawResultFileCount",
  "rawResultBundleByteCount", "rawResultBundleSha256", "zeroLostAccounts",
  "accountCountParity", "selectedWalletParity", "preferencesParity", "verifiedPreImportBackup",
  "encryptedSecretCiphertextParity", "wrappedAesKeyParity", "keystoreAliasContinuity",
  "noCredentialRewrite", "sora2SigningParity", "roomSchemaSha256",
  "qualificationContractSha256", "qualificationChecks", "blockingReasons",
]);
const QUALIFICATION_CHECK_KEYS = Object.freeze([
  "singleAccountQualified", "multiAccountQualified", "successfulActivationQualified",
  "currentSchemaSnapshotQualified", "legacyEmptySuffixQualified",
  "verifiedPreImportBackupQualified", "legacySharedPreferencesImportQualified",
  "retainedDataStoreRestartQualified", "encryptedCiphertextParityQualified",
  "wrappedAesKeyParityQualified", "keystoreAliasContinuityQualified", "noCredentialRewriteQualified",
  "authenticatedEnvelopeTamperQualified", "wrappedKeyFailureQualified",
  "missingKeystoreAliasQualified", "twelveWordMnemonicQualified", "twentyFourWordMnemonicQualified",
  "retainedFifteenWordMnemonicQualified", "rawSeedQualified", "legacySecretQualified",
  "watchOnlyQualified",
  "missingOrCorruptSecretQualified", "encryptedStorageReadbackQualified",
  "sora2AddressParityQualified", "sora2SignatureParityQualified", "interruptedMigrationQualified",
  "reinstallUpgradeQualified", "rollbackQualified", "lowStorageQualified",
  "deleteJournalModeQualified", "walJournalModeQualified", "walSidecarByteParityQualified",
  "rawExecutionEvidenceReviewed", "rawExecutionInventoryQualified",
]);
const EVIDENCE_KEYS = Object.freeze([
  "schemaVersion", "contractId", "platform", "status", "runId",
  "qualificationSequenceNumber", "sourceRevision", "producedAtEpochSeconds",
  "qualificationContractSha256", "trustRootSha256", "deviceEvidenceProducerKeyId",
  "independentReviewerKeyId", "identity", "chronology", "artifacts", "privacy",
  "blockingReasons",
]);
const CHRONOLOGY_KEYS = Object.freeze([
  "runStartedAtEpochSeconds", "runFinishedAtEpochSeconds",
  "snapshotManifestProducedAtEpochSeconds", "testResultProducedAtEpochSeconds",
  "encryptedStorageEvidenceProducedAtEpochSeconds",
  "deviceExecutionEvidenceProducedAtEpochSeconds",
  "rawExecutionEvidenceProducedAtEpochSeconds",
]);
const TRUST_KEYS = Object.freeze([
  "schemaVersion", "contractId", "platform", "status", "signatureAlgorithm",
  "authorities", "replayPolicy", "blockingReasons",
]);
const FORBIDDEN_KEY_PARTS = Object.freeze([
  "accountid", "accountidentifier", "address", "ciphertext", "deviceid",
  "deviceidentifier", "keystorematerial", "mnemonic", "phrase", "privatekey",
  "publickey", "rawsignedpayload", "seed", "secret", "serialnumber",
  "signature", "signedpayload", "udid", "walletid", "wrappedkey",
]);
const SAFE_AGGREGATES = new Set([
  "encryptedsecretciphertextparity", "encryptedciphertextparityqualified",
  "executedwalletidentitymigration75testcount",
  "executedsora2addresscodectestcount",
  "keystorealiascontinuity", "keystorealiascontinuityqualified",
  "missingorcorruptsecretqualified", "secretfailurecohortcount",
  "sora2addressparityqualified", "sora2signatureparityqualified",
  "successfulsecretsourcecohortcount",
  "twelvewordmnemonicqualified", "twentyfourwordmnemonicqualified",
  "retainedfifteenwordmnemonicqualified", "rawseedqualified", "legacysecretqualified",
  "wrappedaeskeyparity", "wrappedaeskeyparityqualified", "wrappedkeyfailurequalified",
]);

export class AndroidMigrationQualificationError extends Error {}

const fail = (message) => {
  throw new AndroidMigrationQualificationError(message);
};
const hash = (bytes) => createHash("sha256").update(bytes).digest("hex");
const exactKeys = (value, keys, label) => {
  if (
    value === null || typeof value !== "object" || Array.isArray(value) ||
    Object.keys(value).length !== keys.length ||
    !keys.every((key) => Object.hasOwn(value, key))
  ) fail(`${label} contains missing or unreviewed fields`);
};
const sameShape = (value, template, label) => {
  if (Array.isArray(template)) {
    if (!Array.isArray(value)) fail(`${label} differs from the reviewed template shape`);
    return;
  }
  if (template !== null && typeof template === "object") {
    exactKeys(value, Object.keys(template), label);
    for (const key of Object.keys(template)) sameShape(value[key], template[key], `${label}.${key}`);
  } else if (template !== null && typeof value !== typeof template) {
    fail(`${label} differs from the reviewed template type`);
  }
};
const requireSha256 = (value, label, expected) => {
  if (typeof value !== "string" || !SHA256.test(value)) fail(`${label} must be lowercase SHA-256`);
  if (expected !== undefined && value !== expected) fail(`${label} differs from its protected identity`);
  return value;
};
const requireRevision = (value, label, expected) => {
  if (typeof value !== "string" || !SOURCE_REVISION.test(value) || /^0+$/.test(value)) {
    fail(`${label} must be a nonzero lowercase 40-hex source revision`);
  }
  if (expected !== undefined && value !== expected) fail(`${label} differs from the protected release revision`);
  return value;
};
const requirePositiveInteger = (value, label) => {
  if (!Number.isSafeInteger(value) || value <= 0) fail(`${label} must be a positive safe integer`);
  return value;
};
const requireEnv = (environment, name) => {
  const value = environment[name];
  if (typeof value !== "string" || value.length === 0) fail(`required protected environment value is absent: ${name}`);
  return value;
};
const requireUuid = (value, label, expected) => {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value) || value === "00000000-0000-0000-0000-000000000000") {
    fail(`${label} must be a lowercase canonical UUID`);
  }
  if (expected !== undefined && value !== expected) fail(`${label} differs from the protected qualification run`);
  return value;
};

// JSON.parse silently accepts duplicate object keys. This small parser admits only the
// integer-only JSON subset used by qualification evidence and rejects duplicates.
const parseStrictJson = (bytes, label) => {
  if (bytes.length > MAX_JSON_BYTES || bytes.length === 0) fail(`${label} has an invalid byte size`);
  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) fail(`${label} must not contain a UTF-8 BOM`);
  let source;
  try { source = new TextDecoder("utf-8", { fatal: true }).decode(bytes); }
  catch { fail(`${label} is not valid UTF-8`); }
  let index = 0;
  const invalid = () => fail(`${label} is not strict integer-only JSON`);
  const whitespace = () => { while (/\s/.test(source[index] ?? "")) index += 1; };
  const string = () => {
    if (source[index] !== '"') invalid();
    const start = index++;
    while (index < source.length) {
      const character = source[index];
      if (character === '"') {
        index += 1;
        let value;
        try { value = JSON.parse(source.slice(start, index)); } catch { invalid(); }
        for (const scalar of value) {
          const code = scalar.codePointAt(0);
          if (code >= 0xd800 && code <= 0xdfff) invalid();
        }
        return value;
      }
      if (character === "\\") {
        index += 1;
        const escape = source[index];
        if (escape === "u") {
          if (!/^[0-9a-fA-F]{4}$/.test(source.slice(index + 1, index + 5))) invalid();
          index += 5;
          continue;
        }
        if (!['"', "\\", "/", "b", "f", "n", "r", "t"].includes(escape)) invalid();
      } else if (character.charCodeAt(0) < 0x20) invalid();
      index += 1;
    }
    invalid();
  };
  const value = (depth = 0) => {
    if (depth > 32) fail(`${label} exceeds maximum JSON depth`);
    whitespace();
    if (source[index] === '"') {
      const result = string();
      if (Buffer.byteLength(result) > 16_384) fail(`${label} contains an oversized string`);
      return result;
    }
    if (source[index] === "{") {
      index += 1; whitespace();
      const result = Object.create(null); const keys = new Set();
      if (source[index] === "}") { index += 1; return result; }
      while (true) {
        whitespace(); const key = string();
        if (!key || Buffer.byteLength(key) > 256 || keys.has(key)) fail(`${label} contains an invalid or duplicate JSON key`);
        keys.add(key); if (keys.size > 512) fail(`${label} contains too many object fields`);
        whitespace(); if (source[index++] !== ":") invalid();
        result[key] = value(depth + 1); whitespace();
        if (source[index] === "}") { index += 1; break; }
        if (source[index++] !== ",") invalid();
      }
      return result;
    }
    if (source[index] === "[") {
      index += 1; whitespace(); const result = [];
      if (source[index] === "]") { index += 1; return result; }
      while (true) {
        result.push(value(depth + 1)); if (result.length > 20_000) fail(`${label} contains too many array items`);
        whitespace(); if (source[index] === "]") { index += 1; break; }
        if (source[index++] !== ",") invalid();
      }
      return result;
    }
    for (const [token, result] of [["true", true], ["false", false], ["null", null]]) {
      if (source.startsWith(token, index)) { index += token.length; return result; }
    }
    const match = /^(?:0|-?[1-9][0-9]*)/.exec(source.slice(index));
    if (match === null) invalid();
    index += match[0].length;
    const result = Number(match[0]);
    if (!Number.isSafeInteger(result)) invalid();
    return result;
  };
  const result = value(); whitespace();
  if (index !== source.length || result === null || typeof result !== "object" || Array.isArray(result)) invalid();
  return result;
};

const openRegularSnapshot = (path, maximum, label) => {
  let before;
  try { before = lstatSync(path); } catch { fail(`${label} is absent: ${path}`); }
  if (!before.isFile() || before.isSymbolicLink() || before.size <= 0 || before.size > maximum) {
    fail(`${label} must be a bounded regular non-symlink file`);
  }
  let descriptor;
  try { descriptor = openSync(path, fsConstants.O_RDONLY | (fsConstants.O_NOFOLLOW ?? 0)); }
  catch { fail(`${label} could not be opened safely`); }
  try {
    const opened = fstatSync(descriptor);
    if (!opened.isFile() || opened.dev !== before.dev || opened.ino !== before.ino || opened.size !== before.size) {
      fail(`${label} changed during admission`);
    }
    const bytes = Buffer.alloc(opened.size); let offset = 0;
    while (offset < bytes.length) {
      const count = readSync(descriptor, bytes, offset, bytes.length - offset, null);
      if (count <= 0) fail(`${label} changed length during admission`);
      offset += count;
    }
    return { path, bytes, sha256: hash(bytes), stat: opened };
  } finally { closeSync(descriptor); }
};
const finalRecheck = (snapshot, maximum, label) => {
  const after = openRegularSnapshot(snapshot.path, maximum, label);
  if (
    after.stat.dev !== snapshot.stat.dev || after.stat.ino !== snapshot.stat.ino ||
    after.stat.size !== snapshot.stat.size || after.stat.mtimeMs !== snapshot.stat.mtimeMs ||
    after.sha256 !== snapshot.sha256
  ) fail(`${label} changed during verification`);
};
const fixedRepoPath = (root, relativePath) => {
  const canonicalRoot = resolve(root);
  let rootStat;
  try { rootStat = lstatSync(canonicalRoot); }
  catch { fail("qualification repository root is absent"); }
  if (!rootStat.isDirectory() || rootStat.isSymbolicLink() || realpathSync(canonicalRoot) !== canonicalRoot) {
    fail("qualification repository root must be a canonical non-symlink directory");
  }
  const components = relativePath.split("/");
  if (
    components.length === 0 || components.some((part) => !part || part === "." || part === ".." || part.includes("\\"))
  ) fail(`qualification path is not canonical: ${relativePath}`);
  const absolute = resolve(canonicalRoot, relativePath);
  if (relative(canonicalRoot, absolute) !== relativePath) fail(`qualification path escapes repository: ${relativePath}`);
  let cursor = canonicalRoot;
  for (const component of components.slice(0, -1)) {
    cursor = resolve(cursor, component);
    let current;
    try { current = lstatSync(cursor); }
    catch { fail(`qualification path parent is absent: ${relativePath}`); }
    if (!current.isDirectory() || current.isSymbolicLink()) {
      fail(`qualification path has a non-directory or symlink parent: ${relativePath}`);
    }
  }
  return absolute;
};
const absoluteEnvPath = (environment, name) => {
  const value = requireEnv(environment, name);
  if (!isAbsolute(value)) fail(`${name} must be an absolute protected path`);
  return value;
};

const validatePrivacy = (value, keys, label) => {
  exactKeys(value, keys, label);
  if (value.aggregateOnly !== true) fail(`${label} must be aggregate-only`);
  for (const key of keys.filter((key) => key !== "aggregateOnly")) {
    if (value[key] !== false) fail(`${label}.${key} must be false`);
  }
};
const privacyScan = (value, label) => {
  if (Array.isArray(value)) return value.forEach((child, index) => privacyScan(child, `${label}[${index}]`));
  if (value === null || typeof value !== "object") return;
  for (const [key, child] of Object.entries(value)) {
    const normalized = key.toLowerCase().replace(/[^a-z0-9]/g, "");
    const forbidden = FORBIDDEN_KEY_PARTS.some((part) => normalized.includes(part));
    const allowed = (normalized.endsWith("included") && child === false) ||
      (SAFE_AGGREGATES.has(normalized) && (typeof child === "boolean" || Number.isSafeInteger(child)));
    if (forbidden && !allowed) fail(`${label} contains prohibited field: ${key}`);
    privacyScan(child, `${label}.${key}`);
  }
};
const validateIdentity = (identity, expectedApp, label) => {
  exactKeys(identity, ["appBuildIdentitySha256", "deviceClasses", "operatingSystemBuilds"], label);
  requireSha256(identity.appBuildIdentitySha256, `${label}.appBuildIdentitySha256`, expectedApp);
  for (const key of ["deviceClasses", "operatingSystemBuilds"]) {
    const values = identity[key];
    if (!Array.isArray(values) || values.length === 0 || values.length > 64 || new Set(values).size !== values.length) {
      fail(`${label}.${key} must be a nonempty unique bounded array`);
    }
    for (const item of values) {
      const valid = typeof item === "string" && Buffer.byteLength(item) <= 128 &&
        (key === "deviceClasses"
          ? /^(?:Android|Pixel|Samsung|Xiaomi|OnePlus|Motorola|Sony|Nokia|Emulator)[A-Za-z0-9 ,._()\/-]{0,63}$/.test(item)
          : /^Android [0-9]+(?:\.[0-9]+){0,2} \([0-9A-Za-z._-]{1,32}\)$/.test(item));
      if (!valid || /(?:identifier|serial|device-id|android-id|[0-9a-f]{40}|[0-9a-f]{64})/i.test(item)) {
        fail(`${label}.${key} contains a device-specific identifier or invalid value`);
      }
    }
  }
};
const validateIdentityShape = (identity, label) =>
  exactKeys(identity, ["appBuildIdentitySha256", "deviceClasses", "operatingSystemBuilds"], label);
const validateReceiptShape = (receipt, label) => {
  exactKeys(receipt, RECEIPT_KEYS, label);
  validateIdentityShape(receipt.identity, `${label}.identity`);
  exactKeys(receipt.privacy, RECEIPT_PRIVACY_KEYS, `${label}.privacy`);
  exactKeys(receipt.roomSchemaSha256, ["74", "75", "76", "77"], `${label}.roomSchemaSha256`);
  exactKeys(receipt.qualificationChecks, QUALIFICATION_CHECK_KEYS, `${label}.qualificationChecks`);
};
const validateEvidenceShape = (evidence, label) => {
  exactKeys(evidence, EVIDENCE_KEYS, label);
  validateIdentityShape(evidence.identity, `${label}.identity`);
  exactKeys(evidence.chronology, CHRONOLOGY_KEYS, `${label}.chronology`);
  exactKeys(evidence.artifacts, Object.keys(ARTIFACT_PATHS), `${label}.artifacts`);
  for (const key of Object.keys(ARTIFACT_PATHS)) {
    exactKeys(evidence.artifacts[key], ["relativePath", "sha256"], `${label}.artifacts.${key}`);
  }
  exactKeys(evidence.privacy, EVIDENCE_PRIVACY_KEYS, `${label}.privacy`);
};
const validateTrustShape = (trust, label) => {
  exactKeys(trust, TRUST_KEYS, label);
  exactKeys(trust.authorities, ["deviceEvidenceProducer", "independentReviewer"], `${label}.authorities`);
  for (const key of ["deviceEvidenceProducer", "independentReviewer"]) {
    exactKeys(trust.authorities[key], ["role", "keyId", "publicKeyPemSha256", "enabled"], `${label}.authorities.${key}`);
  }
  exactKeys(trust.replayPolicy, ["maximumQualificationAgeSeconds", "maximumRunDurationSeconds", "maximumReviewDelaySeconds"], `${label}.replayPolicy`);
};
const validateQualifiedReceiptAggregates = (receipt) => {
  const expectedSources = Array.from({ length: 19 }, (_, index) => index + 58);
  if (
    JSON.stringify(receipt.sourceSchemaVersions) !== JSON.stringify(expectedSources) ||
    receipt.targetSchemaVersion !== 77 || receipt.retainedSchemaCohortCount !== 76 ||
    receipt.singleAccountCohortCount !== 38 || receipt.multiAccountCohortCount !== 38 ||
    receipt.successfulSecretSourceCohortCount !== 6 || receipt.secretFailureCohortCount !== 2 ||
    receipt.currentSchemaSnapshotCohortCount !== 1 || receipt.productionPathCohortCount !== 7 ||
    receipt.encryptedStoragePhaseCount !== 5 ||
    !Number.isSafeInteger(receipt.retainedReleaseSnapshotCount) || receipt.retainedReleaseSnapshotCount <= 0 ||
    !Number.isSafeInteger(receipt.rawResultFileCount) || receipt.rawResultFileCount <= 0 ||
    !Number.isSafeInteger(receipt.rawResultBundleByteCount) || receipt.rawResultBundleByteCount <= 0 ||
    receipt.testFailureCount !== 0 || receipt.testUnexpectedFailureCount !== 0 ||
    [
      receipt.executedWalletIdentityMigration75TestCount,
      receipt.executedWalletUpgradeBackupTestCount,
      receipt.executedMigrationManagerSafetyTestCount,
      receipt.executedMigrationManagerProductionPathQualificationTestCount,
      receipt.executedEncryptedWalletMigrationStorageTestCount,
      receipt.executedSora2AddressCodecTestCount,
    ].some((count) => !Number.isSafeInteger(count) || count <= 0) ||
    [
      receipt.zeroLostAccounts, receipt.accountCountParity, receipt.selectedWalletParity,
      receipt.preferencesParity, receipt.verifiedPreImportBackup,
      receipt.encryptedSecretCiphertextParity, receipt.wrappedAesKeyParity,
      receipt.keystoreAliasContinuity, receipt.noCredentialRewrite, receipt.sora2SigningParity,
    ].some((claim) => claim !== true) ||
    Object.values(receipt.qualificationChecks).some((claim) => claim !== true)
  ) fail("migration receipt aggregate qualification is incomplete");
};
const validateKeyId = (value, prefix, label) => {
  const suffix = typeof value === "string" && value.startsWith(prefix) ? value.slice(prefix.length) : "";
  if (!KEY_ID.test(value ?? "") || !/^[a-z][a-z0-9._-]{2,23}$/.test(suffix) || SOURCE_REVISION.test(suffix) || SHA256.test(suffix)) {
    fail(`${label} is not a canonical role-prefixed key ID`);
  }
};
const validateP256Key = (snapshot, expectedHash, label) => {
  requireSha256(snapshot.sha256, `${label} SHA-256`, expectedHash);
  let key;
  try { key = createPublicKey(snapshot.bytes); } catch { fail(`${label} is not a public key`); }
  if (key.asymmetricKeyType !== "ec" || !["prime256v1", "P-256"].includes(key.asymmetricKeyDetails?.namedCurve)) {
    fail(`${label} must be an ECDSA P-256 public key`);
  }
  return key;
};
const verifyDetached = (payload, signature, key, label) => {
  if (!verifySignature("sha256", payload.bytes, key, signature.bytes)) fail(`${label} detached P-256 signature is invalid`);
};
const validateAggregateArtifact = (record, contractId, context, label, aggregateKeys) => {
  exactKeys(record, [
    "schemaVersion", "contractId", "platform", "status", "runId",
    "qualificationSequenceNumber", "sourceRevision", "producedAtEpochSeconds",
    "identity", "privacy", "aggregate",
  ], label);
  if (
    record.schemaVersion !== 1 || record.contractId !== contractId || record.platform !== "android" ||
    record.status !== "qualified" || record.runId !== context.runId ||
    record.qualificationSequenceNumber !== context.sequence || record.sourceRevision !== context.sourceRevision ||
    record.producedAtEpochSeconds !== context.producedAt || JSON.stringify(record.identity) !== JSON.stringify(context.identity)
  ) fail(`${label} identity is invalid`);
  validatePrivacy(record.privacy, EVIDENCE_PRIVACY_KEYS, `${label}.privacy`);
  exactKeys(record.aggregate, aggregateKeys, `${label}.aggregate`);
  privacyScan(record, label);
};

export const lintAndroidMigrationQualificationTemplates = ({ root }) => {
  const receipt = parseStrictJson(openRegularSnapshot(fixedRepoPath(root, RECEIPT_TEMPLATE), MAX_JSON_BYTES, "blocked receipt template").bytes, "blocked receipt template");
  const evidence = parseStrictJson(openRegularSnapshot(fixedRepoPath(root, EVIDENCE_TEMPLATE), MAX_JSON_BYTES, "blocked evidence template").bytes, "blocked evidence template");
  const trust = parseStrictJson(openRegularSnapshot(fixedRepoPath(root, TRUST_TEMPLATE), MAX_JSON_BYTES, "blocked trust template").bytes, "blocked trust template");
  if (
    receipt.schemaVersion <= 6 ||
    [
      "sora-android-wallet-migration-qualification-v5",
      "sora-android-wallet-migration-qualification-v6",
    ].includes(receipt.contractId)
  ) fail("stale v6 or older blocked receipt template is rejected");
  if (
    evidence.schemaVersion <= 1 ||
    evidence.contractId === "sora-android-wallet-migration-evidence-v1"
  ) fail("stale v1 or older blocked evidence template is rejected");
  validateReceiptShape(receipt, "blocked receipt template");
  validateEvidenceShape(evidence, "blocked evidence template");
  validateTrustShape(trust, "blocked trust template");
  if (receipt.schemaVersion !== 7 || receipt.contractId !== "sora-android-wallet-migration-qualification-v7" || receipt.platform !== "android" || receipt.status !== "blocked-template") fail("blocked receipt template is not exact v7");
  if (evidence.schemaVersion !== 2 || evidence.contractId !== "sora-android-wallet-migration-evidence-v2" || evidence.platform !== "android" || evidence.status !== "blocked-template") fail("blocked evidence template is not exact v2");
  if (trust.schemaVersion !== 1 || trust.contractId !== "sora-android-wallet-migration-qualification-trust-v1" || trust.platform !== "android" || trust.status !== "blocked") fail("blocked trust template is not exact v1");
  validatePrivacy(receipt.privacy, RECEIPT_PRIVACY_KEYS, "blocked receipt privacy");
  validatePrivacy(evidence.privacy, EVIDENCE_PRIVACY_KEYS, "blocked evidence privacy");
  privacyScan(receipt, "blocked receipt template");
  privacyScan(evidence, "blocked evidence template");
  const expectedSources = Array.from({ length: 19 }, (_, index) => index + 58);
  if (
    receipt.runId !== null || receipt.qualificationSequenceNumber !== 0 ||
    receipt.sourceRevision !== null || receipt.qualifiedAtEpochSeconds !== 0 ||
    receipt.reviewedAtEpochSeconds !== 0 || receipt.trustRootSha256 !== null ||
    receipt.evidenceManifestSha256 !== null || receipt.deviceEvidenceProducerKeyId !== null ||
    receipt.independentReviewerKeyId !== null || receipt.qualificationContractSha256 !== null ||
    JSON.stringify(receipt.identity) !== JSON.stringify({ appBuildIdentitySha256: null, deviceClasses: [], operatingSystemBuilds: [] }) ||
    JSON.stringify(receipt.sourceSchemaVersions) !== JSON.stringify(expectedSources) ||
    receipt.targetSchemaVersion !== 77 || receipt.retainedSchemaCohortCount !== 76 ||
    receipt.singleAccountCohortCount !== 38 || receipt.multiAccountCohortCount !== 38 ||
    receipt.successfulSecretSourceCohortCount !== 6 || receipt.secretFailureCohortCount !== 2 ||
    receipt.currentSchemaSnapshotCohortCount !== 1 || receipt.productionPathCohortCount !== 7 ||
    receipt.encryptedStoragePhaseCount !== 5 || receipt.retainedReleaseSnapshotCount !== 0 ||
    receipt.retainedReleaseSnapshotManifestSha256 !== null ||
    receipt.executedWalletIdentityMigration75TestCount !== 0 ||
    receipt.executedWalletUpgradeBackupTestCount !== 0 ||
    receipt.executedMigrationManagerSafetyTestCount !== 0 ||
    receipt.executedMigrationManagerProductionPathQualificationTestCount !== 0 ||
    receipt.executedEncryptedWalletMigrationStorageTestCount !== 0 ||
    receipt.executedSora2AddressCodecTestCount !== 0 || receipt.testFailureCount !== 0 ||
    receipt.testUnexpectedFailureCount !== 0 || receipt.aggregateTestResultSha256 !== null ||
    receipt.rawExecutionEvidenceSha256 !== null || receipt.rawRunIndexSha256 !== null ||
    receipt.rawResultFileCount !== 0 || receipt.rawResultBundleByteCount !== 0 ||
    receipt.rawResultBundleSha256 !== null ||
    Object.values(receipt.roomSchemaSha256).some((identity) => identity !== null) ||
    [
      receipt.zeroLostAccounts, receipt.accountCountParity, receipt.selectedWalletParity,
      receipt.preferencesParity, receipt.verifiedPreImportBackup,
      receipt.encryptedSecretCiphertextParity, receipt.wrappedAesKeyParity,
      receipt.keystoreAliasContinuity, receipt.noCredentialRewrite, receipt.sora2SigningParity,
    ].some((claim) => claim !== false) ||
    Object.values(receipt.qualificationChecks).some((claim) => claim !== false) ||
    !Array.isArray(receipt.blockingReasons) || receipt.blockingReasons.length !== 1 ||
    typeof receipt.blockingReasons[0] !== "string" || receipt.blockingReasons[0].length === 0
  ) fail("blocked receipt template carries fabricated qualification state");
  if (
    evidence.runId !== null || evidence.qualificationSequenceNumber !== 0 ||
    evidence.sourceRevision !== null || evidence.producedAtEpochSeconds !== 0 ||
    evidence.qualificationContractSha256 !== null || evidence.trustRootSha256 !== null ||
    evidence.deviceEvidenceProducerKeyId !== null || evidence.independentReviewerKeyId !== null ||
    JSON.stringify(evidence.identity) !== JSON.stringify({ appBuildIdentitySha256: null, deviceClasses: [], operatingSystemBuilds: [] }) ||
    Object.values(evidence.chronology).some((epoch) => epoch !== 0) ||
    Object.entries(evidence.artifacts).some(([key, artifact]) =>
      artifact.relativePath !== ARTIFACT_PATHS[key] || artifact.sha256 !== null
    ) || !Array.isArray(evidence.blockingReasons) || evidence.blockingReasons.length !== 1 ||
    typeof evidence.blockingReasons[0] !== "string" || evidence.blockingReasons[0].length === 0
  ) fail("blocked evidence template carries fabricated qualification state");
  exactKeys(trust.authorities, ["deviceEvidenceProducer", "independentReviewer"], "blocked trust authorities");
  for (const [authority, role] of [
    [trust.authorities.deviceEvidenceProducer, "device-evidence-producer"],
    [trust.authorities.independentReviewer, "independent-reviewer"],
  ]) {
    exactKeys(authority, ["role", "keyId", "publicKeyPemSha256", "enabled"], "blocked trust authority");
    if (authority.role !== role || authority.keyId !== null || authority.publicKeyPemSha256 !== null || authority.enabled !== false) fail("blocked trust template carries fabricated authority state");
  }
  if (
    trust.signatureAlgorithm !== "ecdsa-p256-sha256" ||
    JSON.stringify(trust.replayPolicy) !== JSON.stringify({ maximumQualificationAgeSeconds: 604800, maximumRunDurationSeconds: 172800, maximumReviewDelaySeconds: 86400 }) ||
    !Array.isArray(trust.blockingReasons) || trust.blockingReasons.length !== 1 ||
    typeof trust.blockingReasons[0] !== "string" || trust.blockingReasons[0].length === 0
  ) fail("blocked trust template carries fabricated or unreviewed state");
};

export const verifyAndroidMigrationQualificationV7 = ({
  root,
  environment = process.env,
  expectedQualificationContractSha256,
} = {}) => {
  lintAndroidMigrationQualificationTemplates({ root });
  const sourceRevision = requireRevision(requireEnv(environment, "PRODUCTION_CANDIDATE_SOURCE_REVISION"), "protected source revision");
  const runId = requireUuid(requireEnv(environment, "ANDROID_MIGRATION_QUALIFICATION_RUN_ID"), "protected run ID");
  const sequenceText = requireEnv(environment, "ANDROID_MIGRATION_QUALIFICATION_SEQUENCE_NUMBER");
  if (!/^[1-9][0-9]{0,14}$/.test(sequenceText)) fail("protected qualification sequence must be a positive decimal integer");
  const sequence = Number(sequenceText);
  const appIdentity = requireSha256(requireEnv(environment, "ANDROID_MIGRATION_QUALIFICATION_APP_BUILD_IDENTITY_SHA256"), "protected app-build identity");
  if (/^0+$/.test(appIdentity)) fail("protected app-build identity must be nonzero");
  const trustPin = requireSha256(requireEnv(environment, "ANDROID_MIGRATION_QUALIFICATION_TRUST_SHA256"), "protected trust root");
  const producerPin = requireSha256(requireEnv(environment, "ANDROID_MIGRATION_QUALIFICATION_DEVICE_PRODUCER_PUBLIC_KEY_SHA256"), "protected producer key");
  const reviewerPin = requireSha256(requireEnv(environment, "ANDROID_MIGRATION_QUALIFICATION_REVIEWER_PUBLIC_KEY_SHA256"), "protected reviewer key");
  const expectedContract = requireSha256(
    expectedQualificationContractSha256 ?? requireEnv(environment, "ANDROID_MIGRATION_QUALIFICATION_CONTRACT_SHA256"),
    "protected qualification contract",
  );

  const receiptSnapshot = openRegularSnapshot(fixedRepoPath(root, RECEIPT), MAX_JSON_BYTES, "migration receipt");
  const evidenceSnapshot = openRegularSnapshot(fixedRepoPath(root, EVIDENCE), MAX_JSON_BYTES, "migration evidence manifest");
  const trustSnapshot = openRegularSnapshot(fixedRepoPath(root, TRUST), MAX_JSON_BYTES, "migration trust root");
  const receiptTemplateSnapshot = openRegularSnapshot(fixedRepoPath(root, RECEIPT_TEMPLATE), MAX_JSON_BYTES, "blocked receipt template");
  const evidenceTemplateSnapshot = openRegularSnapshot(fixedRepoPath(root, EVIDENCE_TEMPLATE), MAX_JSON_BYTES, "blocked evidence template");
  const trustTemplateSnapshot = openRegularSnapshot(fixedRepoPath(root, TRUST_TEMPLATE), MAX_JSON_BYTES, "blocked trust template");
  const receiptTemplate = parseStrictJson(receiptTemplateSnapshot.bytes, "blocked receipt template");
  const evidenceTemplate = parseStrictJson(evidenceTemplateSnapshot.bytes, "blocked evidence template");
  const trustTemplate = parseStrictJson(trustTemplateSnapshot.bytes, "blocked trust template");
  const receipt = parseStrictJson(receiptSnapshot.bytes, "migration receipt");
  const evidence = parseStrictJson(evidenceSnapshot.bytes, "migration evidence manifest");
  const trust = parseStrictJson(trustSnapshot.bytes, "migration trust root");
  if (
    receipt.schemaVersion <= 6 ||
    [
      "sora-android-wallet-migration-qualification-v5",
      "sora-android-wallet-migration-qualification-v6",
    ].includes(receipt.contractId)
  ) fail("stale v6 or older migration receipt is rejected");
  if (
    evidence.schemaVersion <= 1 ||
    evidence.contractId === "sora-android-wallet-migration-evidence-v1"
  ) fail("stale v1 or older migration evidence manifest is rejected");
  validateReceiptShape(receipt, "migration receipt");
  validateEvidenceShape(evidence, "migration evidence manifest");
  validateTrustShape(trust, "migration trust root");
  sameShape(receipt, receiptTemplate, "migration receipt");
  sameShape(evidence, evidenceTemplate, "migration evidence manifest");
  sameShape(trust, trustTemplate, "migration trust root");

  const receiptSignature = openRegularSnapshot(absoluteEnvPath(environment, "ANDROID_MIGRATION_QUALIFICATION_RECEIPT_SIGNATURE_PATH"), MAX_SIGNATURE_BYTES, "receipt signature");
  const producerSignature = openRegularSnapshot(absoluteEnvPath(environment, "ANDROID_MIGRATION_QUALIFICATION_EVIDENCE_PRODUCER_SIGNATURE_PATH"), MAX_SIGNATURE_BYTES, "producer evidence signature");
  const reviewerSignature = openRegularSnapshot(absoluteEnvPath(environment, "ANDROID_MIGRATION_QUALIFICATION_EVIDENCE_REVIEWER_SIGNATURE_PATH"), MAX_SIGNATURE_BYTES, "reviewer evidence signature");
  const producerKeySnapshot = openRegularSnapshot(absoluteEnvPath(environment, "ANDROID_MIGRATION_QUALIFICATION_DEVICE_PRODUCER_PUBLIC_KEY_PATH"), MAX_KEY_BYTES, "producer public key");
  const reviewerKeySnapshot = openRegularSnapshot(absoluteEnvPath(environment, "ANDROID_MIGRATION_QUALIFICATION_REVIEWER_PUBLIC_KEY_PATH"), MAX_KEY_BYTES, "reviewer public key");

  requireSha256(trustSnapshot.sha256, "migration trust root", trustPin);
  if (trust.schemaVersion !== 1 || trust.contractId !== "sora-android-wallet-migration-qualification-trust-v1" || trust.platform !== "android" || trust.status !== "qualified" || trust.signatureAlgorithm !== "ecdsa-p256-sha256" || trust.blockingReasons.length !== 0) fail("migration trust root is not exact qualified v1");
  exactKeys(trust.authorities, ["deviceEvidenceProducer", "independentReviewer"], "migration trust authorities");
  const producer = trust.authorities.deviceEvidenceProducer;
  const reviewer = trust.authorities.independentReviewer;
  for (const [authority, role, prefix, label] of [
    [producer, "device-evidence-producer", "android-migration-device-producer-", "producer"],
    [reviewer, "independent-reviewer", "android-migration-independent-reviewer-", "reviewer"],
  ]) {
    exactKeys(authority, ["role", "keyId", "publicKeyPemSha256", "enabled"], `${label} authority`);
    if (authority.role !== role || authority.enabled !== true) fail(`${label} authority is not qualified`);
    validateKeyId(authority.keyId, prefix, `${label} key ID`);
  }
  if (producer.keyId === reviewer.keyId || producer.publicKeyPemSha256 === reviewer.publicKeyPemSha256) fail("producer and independent reviewer authorities must be distinct");
  requireSha256(producer.publicKeyPemSha256, "producer key pin", producerPin);
  requireSha256(reviewer.publicKeyPemSha256, "reviewer key pin", reviewerPin);
  const producerKey = validateP256Key(producerKeySnapshot, producerPin, "producer public key");
  const reviewerKey = validateP256Key(reviewerKeySnapshot, reviewerPin, "reviewer public key");
  if (
    Buffer.compare(
      producerKey.export({ type: "spki", format: "der" }),
      reviewerKey.export({ type: "spki", format: "der" }),
    ) === 0
  ) fail("producer and independent reviewer must use distinct P-256 public keys");

  // Authentication happens before any potentially expensive retained artifact is opened.
  verifyDetached(receiptSnapshot, receiptSignature, reviewerKey, "migration receipt review");
  verifyDetached(evidenceSnapshot, producerSignature, producerKey, "migration evidence production");
  verifyDetached(evidenceSnapshot, reviewerSignature, reviewerKey, "migration evidence review");

  if (receipt.schemaVersion !== 7 || receipt.contractId !== "sora-android-wallet-migration-qualification-v7" || receipt.platform !== "android" || receipt.status !== "qualified" || receipt.blockingReasons.length !== 0) fail("migration receipt is not exact qualified v7");
  if (evidence.schemaVersion !== 2 || evidence.contractId !== "sora-android-wallet-migration-evidence-v2" || evidence.platform !== "android" || evidence.status !== "qualified" || evidence.blockingReasons.length !== 0) fail("migration evidence manifest is not exact qualified v2");
  validateQualifiedReceiptAggregates(receipt);
  requireUuid(receipt.runId, "receipt run ID", runId); requireUuid(evidence.runId, "evidence run ID", runId);
  if (receipt.qualificationSequenceNumber !== sequence || evidence.qualificationSequenceNumber !== sequence) fail("migration qualification sequence differs from the protected monotonic sequence");
  requireRevision(receipt.sourceRevision, "receipt source revision", sourceRevision);
  requireRevision(evidence.sourceRevision, "evidence source revision", sourceRevision);
  requireSha256(receipt.trustRootSha256, "receipt trust root", trustSnapshot.sha256);
  requireSha256(evidence.trustRootSha256, "evidence trust root", trustSnapshot.sha256);
  requireSha256(receipt.evidenceManifestSha256, "receipt evidence manifest", evidenceSnapshot.sha256);
  requireSha256(receipt.qualificationContractSha256, "receipt qualification contract", expectedContract);
  requireSha256(evidence.qualificationContractSha256, "evidence qualification contract", expectedContract);
  if (receipt.deviceEvidenceProducerKeyId !== producer.keyId || evidence.deviceEvidenceProducerKeyId !== producer.keyId || receipt.independentReviewerKeyId !== reviewer.keyId || evidence.independentReviewerKeyId !== reviewer.keyId) fail("receipt/evidence key-role binding is invalid");
  validateIdentity(receipt.identity, appIdentity, "receipt.identity");
  validateIdentity(evidence.identity, appIdentity, "evidence.identity");
  if (JSON.stringify(receipt.identity) !== JSON.stringify(evidence.identity)) fail("receipt and evidence app/device identities differ");
  validatePrivacy(receipt.privacy, RECEIPT_PRIVACY_KEYS, "receipt.privacy");
  validatePrivacy(evidence.privacy, EVIDENCE_PRIVACY_KEYS, "evidence.privacy");
  privacyScan(receipt, "migration receipt");
  privacyScan(evidence, "migration evidence manifest");

  exactKeys(trust.replayPolicy, ["maximumQualificationAgeSeconds", "maximumRunDurationSeconds", "maximumReviewDelaySeconds"], "replay policy");
  if (JSON.stringify(trust.replayPolicy) !== JSON.stringify({ maximumQualificationAgeSeconds: 604800, maximumRunDurationSeconds: 172800, maximumReviewDelaySeconds: 86400 })) fail("replay policy differs from reviewed 7d/48h/24h limits");
  exactKeys(evidence.chronology, ["runStartedAtEpochSeconds", "runFinishedAtEpochSeconds", "snapshotManifestProducedAtEpochSeconds", "testResultProducedAtEpochSeconds", "encryptedStorageEvidenceProducedAtEpochSeconds", "deviceExecutionEvidenceProducedAtEpochSeconds", "rawExecutionEvidenceProducedAtEpochSeconds"], "evidence chronology");
  const started = requirePositiveInteger(evidence.chronology.runStartedAtEpochSeconds, "run start");
  const finished = requirePositiveInteger(evidence.chronology.runFinishedAtEpochSeconds, "run finish");
  const artifactTimes = [
    requirePositiveInteger(evidence.chronology.snapshotManifestProducedAtEpochSeconds, "snapshot time"),
    requirePositiveInteger(evidence.chronology.testResultProducedAtEpochSeconds, "test-result time"),
    requirePositiveInteger(evidence.chronology.encryptedStorageEvidenceProducedAtEpochSeconds, "encrypted-storage time"),
    requirePositiveInteger(evidence.chronology.deviceExecutionEvidenceProducedAtEpochSeconds, "device-execution time"),
    requirePositiveInteger(evidence.chronology.rawExecutionEvidenceProducedAtEpochSeconds, "raw-execution time"),
  ];
  const produced = requirePositiveInteger(evidence.producedAtEpochSeconds, "evidence produced time");
  const reviewed = requirePositiveInteger(receipt.reviewedAtEpochSeconds, "review time");
  const qualified = requirePositiveInteger(receipt.qualifiedAtEpochSeconds, "qualification time");
  const now = Math.floor(Date.now() / 1000);
  if (!(started <= Math.min(...artifactTimes) && Math.max(...artifactTimes) <= finished && finished <= produced && produced <= reviewed && reviewed <= qualified && qualified <= now)) fail("migration evidence chronology is invalid");
  if (finished - started > 172800 || reviewed - produced > 86400 || qualified - reviewed > 86400 || now - qualified > 604800) fail("migration evidence violates 7d/48h/24h chronology bounds");

  exactKeys(evidence.artifacts, Object.keys(ARTIFACT_PATHS), "migration evidence artifacts");
  const artifactSnapshots = Object.create(null);
  for (const [key, path] of Object.entries(ARTIFACT_PATHS)) {
    exactKeys(evidence.artifacts[key], ["relativePath", "sha256"], `artifact ${key}`);
    if (evidence.artifacts[key].relativePath !== path) fail(`artifact ${key} path is not fixed`);
    const expected = requireSha256(evidence.artifacts[key].sha256, `artifact ${key} hash`);
    const snapshot = openRegularSnapshot(fixedRepoPath(root, path), MAX_JSON_BYTES, `artifact ${key}`);
    if (snapshot.sha256 !== expected) fail(`artifact ${key} differs from signed evidence`);
    artifactSnapshots[key] = snapshot;
  }
  if (
    receipt.retainedReleaseSnapshotManifestSha256 !== artifactSnapshots.retainedReleaseSnapshotManifest.sha256 ||
    receipt.aggregateTestResultSha256 !== artifactSnapshots.testResult.sha256 ||
    receipt.rawExecutionEvidenceSha256 !== artifactSnapshots.rawExecutionEvidence.sha256
  ) fail("receipt artifact hashes differ from opened signed evidence");

  const contexts = [
    ["retainedReleaseSnapshotManifest", "sora-android-wallet-migration-retained-snapshot-manifest-v1", artifactTimes[0], ["retainedReleaseSnapshotCount", "sourceSchemaVersions", "targetSchemaVersion", "allSnapshotFilesRegular", "allSnapshotHashesVerified", "allSnapshotDatabasesOpenedReadOnly", "settingsSnapshotsVerified"]],
    ["testResult", "sora-android-wallet-migration-test-results-v1", artifactTimes[1], ["executedWalletIdentityMigration75TestCount", "executedWalletUpgradeBackupTestCount", "executedMigrationManagerSafetyTestCount", "executedMigrationManagerProductionPathQualificationTestCount", "executedEncryptedWalletMigrationStorageTestCount", "executedSora2AddressCodecTestCount", "testFailureCount", "testUnexpectedFailureCount"]],
    ["encryptedStorageEvidence", "sora-android-wallet-migration-encrypted-storage-evidence-v1", artifactTimes[2], ["encryptedStoragePhaseCount", "encryptedSecretCiphertextParity", "wrappedAesKeyParity", "keystoreAliasContinuity", "noCredentialRewrite", "authenticatedEnvelopeTamperQualified", "wrappedKeyFailureQualified", "missingKeystoreAliasQualified", "rawValuesExcluded"]],
    ["deviceExecutionEvidence", "sora-android-wallet-migration-device-execution-evidence-v1", artifactTimes[3], ["retainedSchemaCohortCount", "productionPathCohortCount", "interruptedMigrationQualified", "processDeathRestartQualified", "reinstallUpgradeQualified", "rollbackQualified", "lowStorageQualified"]],
  ];
  const aggregates = Object.create(null);
  for (const [key, contractId, producedAt, aggregateKeys] of contexts) {
    const record = parseStrictJson(artifactSnapshots[key].bytes, `artifact ${key}`);
    validateAggregateArtifact(record, contractId, { runId, sequence, sourceRevision, producedAt, identity: receipt.identity }, `artifact ${key}`, aggregateKeys);
    aggregates[key] = record.aggregate;
  }
  const rawExecutionEvidence = parseStrictJson(
    artifactSnapshots.rawExecutionEvidence.bytes,
    "artifact rawExecutionEvidence",
  );
  validateAndroidMigrationRawEvidenceV1(rawExecutionEvidence);
  if (
    rawExecutionEvidence.runId !== runId ||
    rawExecutionEvidence.qualificationSequenceNumber !== sequence ||
    rawExecutionEvidence.sourceRevision !== sourceRevision ||
    rawExecutionEvidence.producedAtEpochSeconds !== artifactTimes[4] ||
    JSON.stringify(rawExecutionEvidence.identity) !== JSON.stringify(receipt.identity)
  ) fail("raw-execution evidence identity differs from the signed qualification context");
  const rawAggregate = rawExecutionEvidence.aggregate;
  requireSha256(receipt.rawRunIndexSha256, "receipt raw-run index", rawAggregate.rawRunIndexSha256);
  requireSha256(receipt.rawResultBundleSha256, "receipt raw-result bundle", rawAggregate.rawResultBundleSha256);
  if (
    receipt.rawResultFileCount !== rawAggregate.rawResultFileCount ||
    receipt.rawResultBundleByteCount !== rawAggregate.rawResultBundleByteCount ||
    rawAggregate.requiredSuiteCount !== 6 ||
    rawAggregate.apkIdentityArtifactCount < 2 ||
    rawAggregate.methodInventoryArtifactCount < 6 ||
    rawAggregate.reportArtifactCount < 6 ||
    rawAggregate.transcriptArtifactCount < 6 ||
    rawAggregate.allRequiredSuitesPresent !== true ||
    rawAggregate.allInputsRegular !== true ||
    rawAggregate.allInputsHashVerified !== true ||
    rawAggregate.allInputsWithinRunRoot !== true ||
    rawAggregate.rawValuesExcludedFromPublicEvidence !== true
  ) fail("raw-execution aggregate differs from the reviewed receipt");
  const snapshotAggregate = aggregates.retainedReleaseSnapshotManifest;
  if (snapshotAggregate.retainedReleaseSnapshotCount !== receipt.retainedReleaseSnapshotCount || JSON.stringify(snapshotAggregate.sourceSchemaVersions) !== JSON.stringify(receipt.sourceSchemaVersions) || snapshotAggregate.targetSchemaVersion !== receipt.targetSchemaVersion || snapshotAggregate.allSnapshotFilesRegular !== true || snapshotAggregate.allSnapshotHashesVerified !== true || snapshotAggregate.allSnapshotDatabasesOpenedReadOnly !== true || snapshotAggregate.settingsSnapshotsVerified !== true) fail("retained snapshot aggregate differs from receipt");
  for (const key of ["executedWalletIdentityMigration75TestCount", "executedWalletUpgradeBackupTestCount", "executedMigrationManagerSafetyTestCount", "executedMigrationManagerProductionPathQualificationTestCount", "executedEncryptedWalletMigrationStorageTestCount", "executedSora2AddressCodecTestCount", "testFailureCount", "testUnexpectedFailureCount"]) if (aggregates.testResult[key] !== receipt[key]) fail(`test-result aggregate differs from receipt: ${key}`);
  const encrypted = aggregates.encryptedStorageEvidence;
  for (const key of ["encryptedStoragePhaseCount", "encryptedSecretCiphertextParity", "wrappedAesKeyParity", "keystoreAliasContinuity", "noCredentialRewrite"]) if (encrypted[key] !== receipt[key]) fail(`encrypted-storage aggregate differs from receipt: ${key}`);
  for (const key of ["authenticatedEnvelopeTamperQualified", "wrappedKeyFailureQualified", "missingKeystoreAliasQualified"]) if (encrypted[key] !== receipt.qualificationChecks[key]) fail(`encrypted-storage aggregate differs from check: ${key}`);
  if (encrypted.rawValuesExcluded !== true) fail("encrypted-storage evidence must exclude raw values");
  const device = aggregates.deviceExecutionEvidence;
  if (device.retainedSchemaCohortCount !== receipt.retainedSchemaCohortCount || device.productionPathCohortCount !== receipt.productionPathCohortCount || device.processDeathRestartQualified !== true) fail("device-execution aggregate differs from receipt");
  for (const key of ["interruptedMigrationQualified", "reinstallUpgradeQualified", "rollbackQualified", "lowStorageQualified"]) if (device[key] !== receipt.qualificationChecks[key]) fail(`device-execution aggregate differs from check: ${key}`);

  const roomSchemaSnapshots = [];
  for (const [version, expected] of Object.entries(receipt.roomSchemaSha256)) {
    requireSha256(expected, `Room ${version} schema identity`);
    const path = fixedRepoPath(root, `core_db/schemas/jp.co.soramitsu.core_db.AppDatabase/${version}.json`);
    const snapshot = openRegularSnapshot(path, MAX_JSON_BYTES, `Room ${version} schema`);
    if (snapshot.sha256 !== expected) fail(`Room ${version} schema differs from receipt`);
    roomSchemaSnapshots.push([snapshot, MAX_JSON_BYTES, `Room ${version} schema`]);
  }
  for (const [snapshot, maximum, label] of [
    ...roomSchemaSnapshots,
    ...Object.entries(artifactSnapshots).map(([key, snapshot]) => [snapshot, MAX_JSON_BYTES, `artifact ${key}`]),
    [receiptSnapshot, MAX_JSON_BYTES, "migration receipt"], [evidenceSnapshot, MAX_JSON_BYTES, "migration evidence manifest"], [trustSnapshot, MAX_JSON_BYTES, "migration trust root"],
    [receiptTemplateSnapshot, MAX_JSON_BYTES, "blocked receipt template"], [evidenceTemplateSnapshot, MAX_JSON_BYTES, "blocked evidence template"], [trustTemplateSnapshot, MAX_JSON_BYTES, "blocked trust template"],
    [receiptSignature, MAX_SIGNATURE_BYTES, "receipt signature"], [producerSignature, MAX_SIGNATURE_BYTES, "producer signature"], [reviewerSignature, MAX_SIGNATURE_BYTES, "reviewer signature"],
    [producerKeySnapshot, MAX_KEY_BYTES, "producer public key"], [reviewerKeySnapshot, MAX_KEY_BYTES, "reviewer public key"],
  ]) finalRecheck(snapshot, maximum, label);
  return Object.freeze({ receipt, receiptSha256: receiptSnapshot.sha256 });
};
