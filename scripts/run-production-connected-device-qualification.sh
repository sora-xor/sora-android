#!/usr/bin/env bash
set -euo pipefail

# Wallet-upgrade device tests on a connected device or emulator. With more than one
# device attached, set ANDROID_SERIAL first (adb and Gradle both honour it).
# These are optional manual checks before a release; CI does not run them.
# The emulator runner executes each script input line in a fresh shell, so keep every
# command in this script where `set -e` stops at the first failure.
./gradlew \
  :common:connectedProductionDebugAndroidTest \
  :sorasubstrate:connectedProductionDebugAndroidTest \
  --stacktrace --no-daemon --no-parallel

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
