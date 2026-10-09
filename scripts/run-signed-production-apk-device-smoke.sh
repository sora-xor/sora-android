#!/usr/bin/env bash
set -euo pipefail

# Checks the signed productionRelease APK, then installs and launches it on the connected
# test device. Run after :app:assembleProductionRelease. Never point this at a phone that
# holds real wallets.
release_apk="app/build/outputs/apk/production/release/app-production-release.apk"
test -s "$release_apk"
test ! -L "$release_apk"
python3 scripts/verify-release-signing.py "$release_apk"
python3 scripts/verify-android-native-16kb.py "$release_apk"
adb install --no-streaming "$release_apk"
adb shell am force-stop jp.co.soramitsu.sora
adb shell am start -W \
  -n jp.co.soramitsu.sora/.splash.presentation.SplashActivity
adb shell pidof jp.co.soramitsu.sora
