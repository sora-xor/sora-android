#!/usr/bin/env bash
set -euo pipefail

release_apk="app/build/outputs/apk/production/release/app-production-release.apk"
test -s "$release_apk"
test ! -L "$release_apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" \
  verify --verbose --print-certs "$release_apk"
adb install --no-streaming "$release_apk"
adb shell am force-stop jp.co.soramitsu.sora
adb shell am start -W \
  -n jp.co.soramitsu.sora/.splash.presentation.SplashActivity
adb shell pidof jp.co.soramitsu.sora
