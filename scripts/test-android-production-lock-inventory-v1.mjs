#!/usr/bin/env node

import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  ANDROID_MATERIALIZED_DEPENDENCY_SNAPSHOT_V1,
  ANDROID_PRODUCTION_LOCK_PROJECTS_V1,
  ANDROID_TEST_ONLY_LOCK_SOURCE_BINDINGS_V1,
  inspectAndroidProductionLockProjectsV1,
  matchesAndroidMaterializedDependencySnapshotV1,
} from "./lib/android-production-lock-inventory-v1.mjs";

const root = resolve(new URL("..", import.meta.url).pathname);
const read = (path) => readFileSync(resolve(root, path));
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const sources = Object.fromEntries(Object.keys(ANDROID_TEST_ONLY_LOCK_SOURCE_BINDINGS_V1)
  .map((path) => [path, read(path).toString("utf8")]));
const admitted = inspectAndroidProductionLockProjectsV1(sources);
assert.deepEqual(admitted.failures, []);
assert.equal(admitted.lockFilePaths.length, 31);
assert(admitted.lockFilePaths.includes("app/gradle.lockfile"));
assert(!admitted.lockFilePaths.includes("baselineprofile/gradle.lockfile"));

let mutations = 0;
const rejectSources = (name, path, mutate, expectedCode) => {
  const changed = { ...sources, [path]: mutate(sources[path]) };
  assert.notEqual(changed[path], sources[path], `${name}: mutation must change source`);
  assert(inspectAndroidProductionLockProjectsV1(changed).failures.includes(expectedCode), name);
  mutations += 1;
};
for (const project of ANDROID_PRODUCTION_LOCK_PROJECTS_V1) {
  rejectSources(`missing production coverage: ${project}`, "settings.gradle.kts",
    (value) => value.replace(`include(":${project}")`, ""),
    "GRADLE_PRODUCTION_LOCK_PROJECT_INVENTORY_CHANGED");
}
for (const [name, suffix] of [["unlisted production module", '\ninclude(":unlisted")\n'],
  ["duplicate test exception", '\ninclude(":baselineprofile")\n']]) {
  rejectSources(name, "settings.gradle.kts", (value) => value + suffix,
    "GRADLE_PRODUCTION_LOCK_PROJECT_INVENTORY_CHANGED");
}
for (const [name, oldText, newText] of [
  ["production application disguised as test", "alias(libs.plugins.androidTest)", "alias(libs.plugins.androidApplication)"],
  ["production library disguised as test", "alias(libs.plugins.androidTest)", "alias(libs.plugins.androidLibrary)"],
  ["test namespace drift", "jp.co.soramitsu.sora.baselineprofile", "jp.co.soramitsu.sora"],
  ["target project drift", 'targetProjectPath = ":app"', 'targetProjectPath = ":common"'],
  ["production dimension drift", 'missingDimensionStrategy("default", "production")', 'missingDimensionStrategy("default", "staging")'],
]) {
  rejectSources(name, "baselineprofile/build.gradle.kts", (value) => value.replace(oldText, newText),
    "GRADLE_TEST_ONLY_LOCK_EXCEPTION_NOT_ISOLATED");
}
for (const [name, addition] of [
  ["production configuration added", '\nconfigurations.create("productionReleaseRuntimeClasspath")\n'],
  ["new production build type", '\nandroid { buildTypes { create("production") } }\n'],
  ["new production flavor", '\nandroid { productFlavors { create("production") } }\n'],
]) {
  rejectSources(name, "baselineprofile/build.gradle.kts", (value) => value + addition,
    "GRADLE_TEST_ONLY_LOCK_EXCEPTION_NOT_ISOLATED");
}
for (const [name, path, addition] of [
  ["unrecognized test-module configuration", "baselineprofile/build.gradle.kts", '\napply(from = "other.gradle.kts")\n'],
  ["root production configuration hook", "build.gradle.kts", '\nproject(":baselineprofile") { configurations.create("productionReleaseRuntimeClasspath") }\n'],
  ["plugin alias repointed", "gradle/libs.versions.toml", '\n# altered plugin catalog input\n'],
  ["hidden settings include", "settings.gradle.kts", '\ninclude( ":other" )\n'],
]) {
  rejectSources(name, path, (value) => value + addition, "GRADLE_TEST_ONLY_LOCK_EXCEPTION_SOURCE_CHANGED");
}
for (const path of Object.keys(sources)) {
  rejectSources(`missing canonical source: ${path}`, path, () => null,
    "GRADLE_TEST_ONLY_LOCK_EXCEPTION_SOURCE_CHANGED");
}

// Compute the actual materialization independently of both the declaration and snapshot constants.
const config = JSON.parse(read("config/gradle-dependency-provenance.json"));
const declared = {
  verification: config.dependencyVerification,
  inventory: config.dependencyLocking.materializedInventory,
  appLock: config.dependencyLocking.lockFiles.find(({ path }) => path === "app/gradle.lockfile"),
};
const allConfigurations = [];
const lockHash = createHash("sha256");
let appConfigurationCount;
for (const path of admitted.lockFilePaths) {
  const bytes = read(path);
  lockHash.update(path).update("\0").update(bytes).update("\0");
  const configurations = new Set(bytes.toString("utf8").trimEnd().split("\n").slice(3)
    .flatMap((line) => line.split("=")[1].split(",")));
  const project = path === "settings-gradle.lockfile" ? ":settings" : `:${path.split("/")[0]}`;
  allConfigurations.push(...[...configurations].map((name) => `${project}:${name}`));
  if (path === "app/gradle.lockfile") appConfigurationCount = configurations.size;
}
allConfigurations.sort();
const observed = {
  metadataSha256: sha256(read(config.dependencyVerification.metadataPath)),
  metadataDigestSha256: sha256(read(config.dependencyVerification.metadataDigestPath)),
  lockFileSetSha256: lockHash.digest("hex"),
  configurationInventorySha256: sha256(allConfigurations.join("\n")),
  configurationCount: allConfigurations.length,
  productionReleaseConfigurationCount: allConfigurations.filter((name) =>
    !name.startsWith(":settings:") && name.toLowerCase().includes("productionrelease")).length,
  appLockSha256: sha256(read("app/gradle.lockfile")),
  appConfigurationCount,
};
assert.deepEqual(observed, ANDROID_MATERIALIZED_DEPENDENCY_SNAPSHOT_V1);
assert(matchesAndroidMaterializedDependencySnapshotV1(declared, observed));
const drift = (value) => typeof value === "number" ? value - 1 : "0".repeat(64);
for (const key of Object.keys(observed)) {
  assert(!matchesAndroidMaterializedDependencySnapshotV1(declared, { ...observed, [key]: drift(observed[key]) }),
    `observed materialization drift: ${key}`);
  mutations += 1;
}
for (const [section, keys] of [
  ["verification", ["metadataSha256", "metadataDigestSha256"]],
  ["inventory", ["lockFileSetSha256", "configurationInventorySha256", "configurationCount", "productionReleaseConfigurationCount"]],
  ["appLock", ["sha256", "configurationCount"]],
]) {
  for (const key of keys) {
    const changed = structuredClone(declared);
    changed[section][key] = drift(changed[section][key]);
    assert(!matchesAndroidMaterializedDependencySnapshotV1(changed, observed), `declared materialization drift: ${section}.${key}`);
    mutations += 1;
  }
}
// Exercise the actual release-evidence predicate, including every approval/credential flag.
const currentObservation = JSON.parse(read("docs/modernization/qualification/android-dependency-materialization-2026-09-06.json"));
const HISTORICAL_PROBE_PATH = "docs/modernization/release-probe-evidence-2026-08-02.json";
const HISTORICAL_PROBE_SHA256 = "8cc5d52691e4f48595c1ce70b945bd23fb3fecc6c6a6cc785b076808cb00e07e";
assert.equal(currentObservation.historicalEvidencePath, HISTORICAL_PROBE_PATH);
assert.equal(currentObservation.historicalEvidenceSha256, HISTORICAL_PROBE_SHA256);
assert.equal(currentObservation.historicalEvidenceSha256, sha256(read(currentObservation.historicalEvidencePath)));
const verifierSource = read("scripts/verify-production-modernization.mjs").toString("utf8");
const historicalProbe = JSON.parse(read(currentObservation.historicalEvidencePath));
const exactKeysStart = verifierSource.indexOf("const hasExactKeys = ") + "const hasExactKeys = ".length;
const exactKeysEnd = verifierSource.indexOf(";\n", exactKeysStart);
const exactKeys = new Function(`return (${verifierSource.slice(exactKeysStart, exactKeysEnd)});`)();
const observationStart = verifierSource.indexOf("assert(\n  hasExactKeys(dependencyMaterializationRecord,");
const observationEnd = verifierSource.indexOf(',\n  "GRADLE_DEPENDENCY_MATERIALIZATION_OBSERVATION_INVALID",', observationStart);
assert(observationStart > 0 && observationEnd > observationStart);
const observationShapeMatches = new Function("dependencyMaterializationRecord", "probe", "sha256", "hasExactKeys", "historicalProbePath", "historicalProbeSha256",
  `return (${verifierSource.slice(observationStart + "assert(".length, observationEnd)});`);
const matchesObservation = (value) => observationShapeMatches(
  value, historicalProbe, (path) => sha256(read(path)), exactKeys,
  HISTORICAL_PROBE_PATH, HISTORICAL_PROBE_SHA256,
);
assert(matchesObservation(currentObservation));
assert(!matchesObservation(null), "missing materialization observation");
mutations += 1;
for (const key of Object.keys(currentObservation)) {
  const changed = structuredClone(currentObservation);
  delete changed[key];
  assert(!matchesObservation(changed), `missing materialization observation field: ${key}`);
  mutations += 1;
}
for (const [key, value] of [
  ["historicalEvidenceSha256", "0".repeat(64)],
  ["historicalEvidencePath", "docs/modernization/other.json"],
  ["observedAtUtc", "unrecorded"],
  ["schemaVersion", 2],
  ["authorizesRelease", true],
]) {
  assert(!matchesObservation({ ...currentObservation, [key]: value }), `tampered materialization observation: ${key}`);
  mutations += 1;
}
const predicateStart = verifierSource.indexOf("assert(\n  dependencyMaterializationProbe.retainedBaselineAssessedAt");
assert(predicateStart > 0);
const predicateEnd = verifierSource.indexOf(',\n  "GRADLE_DEPENDENCY_RELEASE_EVIDENCE_DIVERGED",', predicateStart);
assert(predicateEnd > predicateStart);
const releaseEvidenceMatches = new Function("dependencyMaterializationProbe", "gradleDependencyProvenance", "gradleWrapperJarSha256",
  `return (${verifierSource.slice(predicateStart + "assert(".length, predicateEnd)});`);
const wrapperHash = sha256(read("gradle/wrapper/gradle-wrapper.jar"));
assert(releaseEvidenceMatches(currentObservation.gradleDependencyProvenance, config, wrapperHash));
const staleMaterialization = { ...historicalProbe.gradleDependencyProvenance,
  retainedBaselineAssessedAt: historicalProbe.gradleDependencyProvenance.dependencyInventoryMaterializedAt };
delete staleMaterialization.dependencyInventoryMaterializedAt;
assert(!releaseEvidenceMatches(staleMaterialization, config, wrapperHash), "stale historical materialization observation");
assert(!releaseEvidenceMatches({ ...currentObservation.gradleDependencyProvenance, qualification: "qualified" }, config, wrapperHash),
  "claimed qualified without review");
mutations += 2;

for (const [key, value] of Object.entries(currentObservation.gradleDependencyProvenance)) {
  const changed = { ...currentObservation.gradleDependencyProvenance, [key]:
    typeof value === "boolean" ? !value : typeof value === "number" ? value - 1 : `${value}-changed` };
  assert(!releaseEvidenceMatches(changed, config, wrapperHash), `release observation drift: ${key}`);
  mutations += 1;
}
// This helper attests only observed bytes; signed review admission is tested separately.
console.log(`Android production lock inventory: exact 30 production projects + sole source-bound test exception; current 31 locks / 272 configurations / 271 production; ${mutations} rejecting mutations passed. No review authority granted.`);
