#!/usr/bin/env node
// Exercise the verifier's actual predicates, including their source readers and Kotlin lexer.
// Mutations exist only in memory; no wallet stores, candidate artifacts, or authority keys are used.
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import vm from "node:vm";

const root = resolve(new URL("..", import.meta.url).pathname);
const verifier = readFileSync(resolve(root, "scripts/verify-production-modernization.mjs"), "utf8");
function between(start, end) {
  const first = verifier.indexOf(start);
  const last = verifier.indexOf(end, first);
  assert(first >= 0 && last > first, `source contract section missing: ${start}`);
  return verifier.slice(first, last);
}
const helpers = between("const maskedLexicalCharacter =", "const collectGradleBuildSources =");
const buildBlocks = between("const appBuildSource =", "const databaseSource =");
const readInterface = between("const nexusToriiReadInterface =", "const directAliasAsAssetIdentityFixture =");
const sources = {};
for (const match of verifier.matchAll(/const (\w+) = read\(\s*"([^"]+)"\s*,?\s*\);/g)) {
  const path = resolve(root, match[2]);
  if (existsSync(path)) sources[match[1]] = readFileSync(path, "utf8");
}
const predicates = [
  "PRODUCTION_ANDROID_VARIANT_IDENTITY_CHANGED",
  "SORA2_GENERIC_AMBIGUITY_RECOVERY_MISSING",
  "PENDING_RECOVERY_STARTUP_FAILURE_ISOLATION_MISSING",
  "NEXUS_PENDING_OVERLAY_SCOPE_GUARD_MISSING",
  "UNIFIED_SORA2_PORTFOLIO_ROW_MISSING",
  "UNIFIED_NEXUS_NETWORK_SCOPE_OR_ACCOUNT_SWITCH_CLEAR_MISSING",
  "NEXUS_DURABLE_STATUS_ONLY_RECOVERY_MISSING",
  "POLKAMARKT_RUNTIME_AUTHORITATIVE_PRESENTATION_MISSING",
];
const expressions = Object.fromEntries(predicates.map((code) => {
  const end = verifier.indexOf(`  "${code}",\n);`);
  assert(end >= 0, `missing predicate: ${code}`);
  const start = verifier.lastIndexOf("\nassert(\n", end);
  assert(start >= 0);
  return [code, verifier.slice(start + 1, end + `  "${code}",\n);`.length)];
}));
function evaluate(code, overrides = {}) {
  const failures = [];
  const context = vm.createContext({
    ...sources, ...overrides,
    assert: (condition, actualCode) => { if (!condition) failures.push(actualCode); },
  });
  vm.runInContext(`${helpers}\n${buildBlocks}\n${readInterface}\nconst strippedNexusPortfolioRepository = stripGradleComments(nexusPortfolioRepository);`, context);
  vm.runInContext(expressions[code], context);
  return failures;
}
for (const code of predicates) assert.deepEqual(evaluate(code), [], `current implementation: ${code}`);

let mutationCount = 0;
function rejects(code, source, before, after) {
  assert(sources[source]?.includes(before), `mutation target missing: ${source}: ${before}`);
  assert.notEqual(before, after);
  const failures = evaluate(code, { [source]: sources[source].replace(before, after) });
  assert(failures.includes(code), `mutation escaped ${code}: ${source}: ${before}`);
  mutationCount += 1;
}
const identity = predicates[0];
rejects(identity, "appBuild", 'namespace = "jp.co.soramitsu.sora"', 'namespace = "jp.co.soramitsu.changed"');
rejects(identity, "appBuild", 'applicationId = "jp.co.soramitsu.sora"', 'applicationId = "jp.co.soramitsu.changed"');
rejects(identity, "appBuild", 'signingConfig = signingConfigs.getByName("productionRelease")', 'signingConfig = signingConfigs.getByName("cidebug")');
rejects(identity, "appBuild", 'create("production") {', 'create("production") {\napplicationIdSuffix = ".changed"');
rejects(identity, "appBuild", 'release {\n', 'release {\ninitWith(getByName("debug"))\n');
rejects(identity, "appBuild", 'create("benchmarkRelease") {', 'create("otherBenchmarkRelease") {');
rejects(identity, "appBuild", 'create("nonMinifiedRelease") {', 'create("otherNonMinifiedRelease") {');
rejects(identity, "appBuild", 'create("benchmarkRelease") {\n            initWith(getByName("release"))\n            signingConfig = signingConfigs.getByName("cidebug")', 'create("benchmarkRelease") {\n            initWith(getByName("release"))\n            signingConfig = signingConfigs.getByName("productionRelease")');
rejects(identity, "appBuild", 'create("nonMinifiedRelease") {\n            initWith(getByName("release"))', 'create("nonMinifiedRelease") {\n            initWith(getByName("debug"))');

rejects(predicates[1], "pendingRecoveryStartupScheduler", "sora2Scheduler.ensureOnStartup()", "Unit");
rejects(predicates[6], "pendingRecoveryStartupScheduler", "nexusScheduler.ensureOnStartup()", "Unit");
const startup = predicates[2];
rejects(startup, "splashViewModel", "interactor.getMigrationDoneAsync().await()", "true");
rejects(startup, "splashViewModel", "interactor.retryMigration()", "true");
rejects(startup, "soraApplication", "override fun onCreate() {", "override fun onCreate() {\nnexusScheduler.ensureOnStartup()");
rejects(startup, "pendingRecoveryStartupScheduler", 'scheduleSafely("NEXUS_PENDING_RECOVERY_SCHEDULING_FAILED")', "run");
rejects(startup, "pendingRecoveryStartupScheduler", 'scheduleSafely("SORA2_PENDING_RECOVERY_SCHEDULING_FAILED")', "run");
rejects(startup, "pendingRecoveryStartupScheduler", "throw error", "return");
rejects(startup, "pendingRecoveryStartupScheduler", 'Log.w("WalletRecoveryStartup", diagnostic)', 'Log.w("WalletRecoveryStartup", error.message)');
rejects(startup, "pendingRecoveryStartupSchedulerTest", "later startup retries scheduling after a temporary failure", "removed retry regression");

const overlay = predicates[3];
rejects(overlay, "nexusWalletScreens", 'WalletErrorMessage("NEXUS_RECOVERY_REQUIRED", onRecovery = onRecovery)', 'Text("unavailable")');
rejects(overlay, "walletDisplay", '"RECOVERY" in code', '"OTHER" in code');
rejects(overlay, "walletStatus", "WalletIssue.RECOVERY -> R.string.wallet_issue_recovery", "WalletIssue.RECOVERY -> R.string.wallet_issue_unavailable");
rejects(overlay, "walletStatus", "TextButton(onClick = onRecovery)", "TextButton(onClick = {})");
rejects(overlay, "nexusPortfolioRepository", "it.assetDefinitionId == canonicalAssetDefinitionId", "true");
rejects(predicates[4], "nexusWalletScreens", 'NetworkSummary("SORA2", false, balance.xorQuantity,', 'NetworkSummary("SORA2", true, balance.xorQuantity,');
rejects(predicates[4], "nexusWalletScreens", "else R.string.network_badge_mainnet", "else R.string.network_badge_testnet");

const scope = predicates[5];
rejects(scope, "nexusWalletScreens", "balances.filter { it.walletId == sora2?.address }", "balances");
rejects(scope, "nexusWalletScreens", "it.walletId == previous.walletId", "true");
rejects(scope, "nexusWalletScreens", "it.networkId == previous.networkId", "true");
rejects(scope, "nexusWalletScreens", "it.address == previous.address", "true");
rejects(scope, "nexusWalletScreens", "selected?.takeIf(isNexusBalanceCurrent)?.let", "selected?.let");
rejects(scope, "nexusWalletScreens", "if (isCurrent()) { clipboard.setText", "if (true) { clipboard.setText");
rejects(scope, "nexusWalletScreens", "balance.confirmedTransfers.forEach", "balance.confirmedTransfers.take(5).forEach");
rejects(scope, "walletSheet", ".verticalScroll(rememberScrollState())", "");
rejects(scope, "nexusWalletScreens", "walletDateTime(transfer.timestampMillis)", '""');
rejects(scope, "cardsHubScreen", "isNexusBalanceCurrent = isNexusBalanceCurrent", "isNexusBalanceCurrent = { true }");

const authority = predicates[7];
rejects(authority, "polkamarktScreen", "onClick = onReviewBatchClaims", "onClick = {}");
rejects(authority, "polkamarktScreen", "val availableShares = state.authoritativeMarket?.let", "val availableShares = state.selectedMarket?.let");
rejects(authority, "polkamarktScreen", "state.authoritativeClaimable?.yesShares", "state.selectedMarket?.yesShares");
rejects(authority, "polkamarktScreen", '"Check your latest share balance"', '"0 shares"');
rejects(authority, "polkamarktScreen", "onClick = { onReview(marketId) }", "onClick = {}");

console.log(`Android wallet continuity source contract: ${predicates.length} current predicates and ${mutationCount} rejecting mutations passed.`);

const splashActivity = readFileSync(resolve(root, "app/src/main/java/jp/co/soramitsu/sora/splash/presentation/SplashActivity.kt"), "utf8");
function acceptsPreflightRetry(source) {
  const button = source.slice(source.indexOf("viewBinding.retryWalletMigrationButton.setOnClickListener"), source.indexOf("viewBinding.contactWalletSupportButton.setOnClickListener"));
  const success = button.slice(button.indexOf("if (result.isSuccess) {"), button.indexOf("} else {\n                    showRecovery("));
  return success.includes("splashViewModel.retryWalletMigration()") && !success.includes("recreate()") && !success.includes("if (migrationRetry)");
}
assert(acceptsPreflightRetry(splashActivity), "Successful backup retry must rerun the retained ViewModel migration");
assert(!acceptsPreflightRetry(splashActivity.replace("splashViewModel.retryWalletMigration()", "recreate()")), "Activity recreation must not pass the preflight retry wiring regression");
console.log("Android successful preflight retry wiring and recreation regression passed.");
