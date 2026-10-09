# Scripts

None of these scripts is part of the Gradle build. CI runs the first three and the
lock-coverage init script with its test.

| Script | What it does | Where it runs |
|---|---|---|
| `verify-android-native-16kb.py APK_OR_AAB` | Fails if a 64-bit native library is not aligned for 16 KB pages. | `android_ci.yml` (debug APK), `android_release.yml` (release AAB and APK) |
| `verify-release-signing.py ARTIFACT...` | Fails unless each APK/AAB is signed by `uploadCertificateSha256` in `config/android-production-signing-identity.json`. | `android_release.yml` |
| `test-android-native-16kb.py`, `test-verify-release-signing.py` | Tests for the two checks above. | `android_ci.yml` |
| `verify-production-release-lock-coverage.gradle` | Gradle init script with two tasks. `verifyProductionReleaseLockCoverage`: every `productionRelease` configuration has a lock entry, and no stale ones remain. `resolveReleaseDependencyLocks`: resolves every `productionRelease` configuration, so a dependency change without an updated `gradle.lockfile` fails (locking is STRICT only for those configurations, which debug builds never resolve). Needs no secrets. Run with `./gradlew -I scripts/verify-production-release-lock-coverage.gradle verifyProductionReleaseLockCoverage resolveReleaseDependencyLocks`; after an intended dependency change, add `--write-locks` to that command and commit the updated lockfiles. `test-production-release-lock-coverage.mjs` tests both tasks. | `android_ci.yml` (unit-test job) |
| `run-production-connected-device-qualification.sh` | Runs the wallet-upgrade device tests (storage, Room migration, crypto runtime, encrypted wallet storage, migration production path). Emulator only: it stops on any other device, because `:app:connectedProductionDebugAndroidTest` installs and then uninstalls `jp.co.soramitsu.sora`, deleting that app's data. Optional before a release. | Manual |
| `run-encrypted-wallet-upgrade-qualification.sh`, `run-migration-manager-production-path-qualification.sh` | The two multi-process device tests used by the script above. The second one installs the isolated `jp.co.soramitsu.sora.qualification` test APK, never the production package. | Manual |
| `run-signed-production-apk-device-smoke.sh` | Checks, installs and launches the signed release APK on a test device. | Manual |
| `update-sora2-metadata.mjs` | Refreshes the bundled SORA2 runtime metadata asset. | Manual |
| `generate-middle-egyptian-localization.mjs`, `verify-middle-egyptian-localization.mjs` | Localization tools for the Middle Egyptian locale. | Manual |

The release-qualification scripts (receipts, controllers, canaries, rollout gates and
`verify-production-modernization.mjs`) were removed in 3.9.0. Their documentation is in
`docs/history/modernization/`.
