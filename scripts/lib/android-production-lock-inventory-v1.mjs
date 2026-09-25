import { createHash } from "node:crypto";

// Observed source/materialization identities, not dependency approval or signing authority.
export const ANDROID_MATERIALIZED_DEPENDENCY_SNAPSHOT_V1 = Object.freeze({
  metadataSha256: "568c9e4d3fad0872b955e801dc5084dec2bacbab1a42da31a973a6f9305b52eb",
  metadataDigestSha256: "29706737c442f0ff0b6ae6f7c804de80db7c61f01c135607fc4f78470291426d",
  lockFileSetSha256: "bf73bee62e3da74dca238607cd3497250c9a34bd51b45a9ed3e01fd403f64593",
  configurationInventorySha256: "4bc0c68745a3114f0b8caae98556d4205296e10bcf0058909c06d03f80194a00",
  configurationCount: 566,
  productionReleaseConfigurationCount: 565,
  appLockSha256: "16674d9f67f6cab13c7970604d261b2fb399658ec4dc2f8d87ae93d4e4baed70",
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
  "settings.gradle.kts": "1a518eefcc788a89ab3aaae1cfd3694dec8da836dcd1397b9420162920444a7d",
  "gradle/libs.versions.toml": "18ff991785a7446698cf51e2b605e5993777795a637ff95bc0ca6d97c1326ff6",
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
