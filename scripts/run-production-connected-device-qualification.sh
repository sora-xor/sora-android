#!/usr/bin/env bash
set -euo pipefail

# The emulator runner executes each script input line in a fresh shell. Keep
# the connected qualification in this script so every command fails closed.
node scripts/verify-production-modernization.mjs --dependency-preflight
./gradlew \
  :common:connectedProductionDebugAndroidTest \
  :sorasubstrate:connectedProductionDebugAndroidTest \
  --stacktrace --no-daemon --no-parallel

node scripts/verify-production-modernization.mjs --dependency-preflight
./gradlew \
  :core_db:connectedProductionDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.core_db.WalletIdentityMigration75Test,jp.co.soramitsu.core_db.WalletUpgradeBackupTest \
  --stacktrace --no-daemon --no-parallel

./gradlew \
  :app:connectedProductionDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.sora.ux.CryptoRuntimeCompatibilityTest \
  --stacktrace --no-daemon --no-parallel

bash scripts/run-encrypted-wallet-upgrade-qualification.sh
bash scripts/run-migration-manager-production-path-qualification.sh
