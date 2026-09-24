import { createHash } from "node:crypto";

// Observed source/materialization identities, not dependency approval or signing authority.
export const ANDROID_MATERIALIZED_DEPENDENCY_SNAPSHOT_V1 = Object.freeze({
  metadataSha256: "8390862f04516357a66c51e761c4225204ed02fe9d1d8102687106dc13b0a039",
  metadataDigestSha256: "a11320aaae95f04a1b19adcfe561536c76b63b536e7c1e4ba8c2adcd7613f1e1",
  lockFileSetSha256: "0feaca60273b6a02ba9d39a89c4e808321b4cf20c6716eeb687dbb896806b9f7",
  configurationInventorySha256: "890f129aa574be27b9ca28a07103a7ecafd1f7e753de1255ec5b532dade0c0e5",
  configurationCount: 463,
  productionReleaseConfigurationCount: 462,
  appLockSha256: "d2f377811068edd84f250d1e11df013a505c6667b17c79d210eb1752fc684468",
  appConfigurationCount: 17,
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
  "settings.gradle.kts": "e0010cbfdfa339aaa1b84feeee058eb0737a1f2a5f44727a92ac30cb6a8c513b",
  "gradle/libs.versions.toml": "c21b9be4d48d47b7b79a51ea48890f808cc73d42090811ff6337970cd442a003",
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
