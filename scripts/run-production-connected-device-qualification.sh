#!/usr/bin/env bash
set -euo pipefail

# Wallet-upgrade device tests. Emulator only; never on a phone with wallets.
# :app:connectedProductionDebugAndroidTest installs the production package ID
# (jp.co.soramitsu.sora) and uninstalls it after the tests, which deletes that app's data,
# wallets included. With more than one device attached, set ANDROID_SERIAL to the emulator
# first (adb and Gradle both honour it).
# These are optional manual checks before a release; CI does not run them.

emulator_flags="$(adb shell 'getprop ro.boot.qemu; getprop ro.kernel.qemu' | tr -d '\r')"
if [[ "$emulator_flags" != *1* ]]; then
  echo "error: this script runs only on an Android emulator. Start an emulator and set ANDROID_SERIAL to it." >&2
  exit 1
fi

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
