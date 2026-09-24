import { createHash } from "node:crypto";

// Observed source/materialization identities, not dependency approval or signing authority.
export const ANDROID_MATERIALIZED_DEPENDENCY_SNAPSHOT_V1 = Object.freeze({
  metadataSha256: "d909b535db50330ddce6c2f418c93092bfd9592a886da1c1e0f1c9a15a6b0ebd",
  metadataDigestSha256: "6e59e0a666f2d09f016119e196258d1779ce5cb0b32de44707ae586db721da9b",
  lockFileSetSha256: "206ce5977c8f330b00536d1d71767b9bf8ffe88f7b0a6b92584e495451305b03",
  configurationInventorySha256: "4bc0c68745a3114f0b8caae98556d4205296e10bcf0058909c06d03f80194a00",
  configurationCount: 566,
  productionReleaseConfigurationCount: 565,
  appLockSha256: "c6d9fb14f42e552c691c04a48a4d90bdde15884d627e5cfffa73f55144355f57",
  appConfigurationCount: 19,
});

export const ANDROID_PRODUCTION_LOCK_PROJECTS_V1 = Object.freeze([
  "app", "common", "common_wallet", "core_db", "demeter", "feature_account_api",
  "feature_account_impl", "feature_assets_api", "feature_assets_impl",
  "feature_blockexplorer_api", "feature_blockexplorer_impl", "feature_ecosystem_impl",
  "feature_ethereum_api", "feature_main_api", "feature_main_impl", "feature_multiaccount_api",
  "feature_multiaccount_impl", "feature_polkaswap_api", "feature_polkaswap_impl",
  "feature_referral_api", "feature_referral_impl", "feature_select_node_api",
  "feature_select_node_impl", "feature_sora_card_api", "feature_sora_card_impl",
  "feature_wallet_api", "feature_wallet_impl", "network", "sorasubstrate", "test_data",
]);

// The sole exception has no productionRelease configurations (offline Gradle model checked).
// Bind its entire source and all files that select its plugins/configurations. This prevents
// disguising production configuration in comments, extra blocks, root hooks, or plugin aliases.
export const ANDROID_TEST_ONLY_LOCK_SOURCE_BINDINGS_V1 = Object.freeze({
  "baselineprofile/build.gradle.kts": "70c435a4b63c7582f81e7ef65135ff2b5fe73618331b68f7af5a8b0fbdc73036",
  "build.gradle.kts": "48fef08f6c3c5e3f018d786f2f02330b3d38b70639d7057b8d2da0c00f6db8a0",
  "settings.gradle.kts": "a6f14a1b9bd4178782372d3d91b1e14a7e22a61247d5353e7486474cd2a3bc9c",
  "gradle/libs.versions.toml": "a926d9e809a9d8ce4ed13dc7dc84e37016e90984cdf56a93b8b916b0a905972d",
});

export function inspectAndroidProductionLockProjectsV1(sources) {
  const failures = [];
  const settings = sources["settings.gradle.kts"] ?? "";
  const included = [...settings.matchAll(/^include\(":([A-Za-z0-9_.-]+)"\)$/gm)].map(([, name]) => name);
  const expected = [...ANDROID_PRODUCTION_LOCK_PROJECTS_V1, "baselineprofile"].sort();
  if (included.length !== new Set(included).size || [...included].sort().join("\0") !== expected.join("\0")) {
    failures.push("GRADLE_PRODUCTION_LOCK_PROJECT_INVENTORY_CHANGED");
  }
  if (!Object.entries(ANDROID_TEST_ONLY_LOCK_SOURCE_BINDINGS_V1).every(([path, digest]) =>
    typeof sources[path] === "string" && createHash("sha256").update(sources[path]).digest("hex") === digest
  )) failures.push("GRADLE_TEST_ONLY_LOCK_EXCEPTION_SOURCE_CHANGED");
  const baseline = sources["baselineprofile/build.gradle.kts"] ?? "";
  if (!baseline.includes("alias(libs.plugins.androidTest)") ||
      !baseline.includes("alias(libs.plugins.baselineProfile)") ||
      !baseline.includes('namespace = "jp.co.soramitsu.sora.baselineprofile"') ||
      !baseline.includes('targetProjectPath = ":app"') ||
      !baseline.includes('missingDimensionStrategy("default", "production")') ||
      /androidApplication|androidLibrary|applicationId|productFlavors|buildTypes|productionRelease/.test(baseline)) {
    failures.push("GRADLE_TEST_ONLY_LOCK_EXCEPTION_NOT_ISOLATED");
  }
  return {
    failures,
    lockFilePaths: [...ANDROID_PRODUCTION_LOCK_PROJECTS_V1.map((project) => `${project}/gradle.lockfile`), "settings-gradle.lockfile"].sort(),
  };
}

export function matchesAndroidMaterializedDependencySnapshotV1({ verification, inventory, appLock }, observed) {
  const expected = ANDROID_MATERIALIZED_DEPENDENCY_SNAPSHOT_V1;
  return Object.entries(expected).every(([key, value]) => observed[key] === value) &&
    verification?.metadataSha256 === expected.metadataSha256 &&
    verification?.metadataDigestSha256 === expected.metadataDigestSha256 &&
    inventory?.lockFileSetSha256 === expected.lockFileSetSha256 &&
    inventory?.configurationInventorySha256 === expected.configurationInventorySha256 &&
    inventory?.configurationCount === expected.configurationCount &&
    inventory?.productionReleaseConfigurationCount === expected.productionReleaseConfigurationCount &&
    appLock?.sha256 === expected.appLockSha256 && appLock?.configurationCount === expected.appConfigurationCount;
}
