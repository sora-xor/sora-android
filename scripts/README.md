# Scripts

None of these scripts is part of the Gradle build. CI runs the first three and the
lock-coverage init script with its test.

| Script | What it does | Where it runs |
|---|---|---|
| `verify-android-native-16kb.py APK_OR_AAB` | Fails if a 64-bit native library is not aligned for 16 KB pages. | `android_ci.yml` (debug APK), `android_release.yml` (release AAB and APK) |
| `verify-release-signing.py ARTIFACT...` | Fails unless each APK/AAB is signed by `uploadCertificateSha256` in `config/android-production-signing-identity.json`. | `android_release.yml` |
| `test-android-native-16kb.py`, `test-verify-release-signing.py` | Tests for the two checks above. | `android_ci.yml` |
| `verify-production-release-lock-coverage.gradle` | Gradle init script with two tasks. `verifyProductionReleaseLockCoverage`: every `productionRelease` configuration has a lock entry, and no stale ones remain. `resolveReleaseDependencyLocks`: resolves every `productionRelease` configuration, so a dependency change without an updated `gradle.lockfile` fails (locking is STRICT only for those configurations, which debug builds never resolve). Needs no secrets. Run with `./gradlew -I scripts/verify-production-release-lock-coverage.gradle verifyProductionReleaseLockCoverage resolveReleaseDependencyLocks`; after an intended dependency change, add `--write-locks` to that command and commit the updated lockfiles. `test-production-release-lock-coverage.mjs` tests both tasks. | `android_ci.yml` (unit-test job) |
| `run-production-connected-device-qualification.sh [--offline] [--report-dir DIR]` | Runs the wallet-upgrade device tests (storage, Room migration, upgrade scenarios from 3.8.6.3 and 2.3.3 storage, recovery screens, crypto runtime, encrypted wallet storage, migration production path) and the other device tests of `:app`. Runs every group even when one fails, then writes `summary.txt` with the commit, the device and the result of each group. Emulator only: it stops on any other device, because `:app:connectedProductionDebugAndroidTest` installs and then uninstalls `jp.co.soramitsu.sora`, deleting that app's data. Required for every release candidate; see "Device tests" below. | `android_device_tests.yml` (push to `release/**`, on demand, and as the first job of `android_release.yml`); by hand with `--offline` |
| `run-encrypted-wallet-upgrade-qualification.sh [--offline]`, `run-migration-manager-production-path-qualification.sh [--offline]` | The two multi-process device tests used by the script above: 5 and 10 phases, each in a new process. The second one installs the isolated `jp.co.soramitsu.sora.qualification` test APK, never the production package; its last three phases take a pre-multiaccount wallet at Room 50 through every migration and a restart. | Through the script above, or by hand |
| `start-emulator-for-device-tests.sh --avd NAME [--port PORT] [--read-only] [--create SYSTEM_IMAGE [--install-image]]` | Starts a headless emulator and waits until Android has booted. Refuses a port on which an emulator is already attached. | `android_device_tests.yml`; by hand |
| `device-test-common.sh` | Shared by the device-test scripts (`--offline`, `SORA_GRADLE_FLAGS`, finding `adb`, the emulator-only guard). Not run by itself. | |
| `test-device-test-scripts.py` | Runs the device-test scripts against recorders in place of Gradle, `adb`, `apkanalyzer` and `aapt2`: `--offline` reaches every Gradle call, all 15 phases run in order, every group runs after a failure, a group without tests fails, a phone or an ambiguous device is refused, a non-isolated APK is never installed. | `android_ci.yml` |
| `verify-release-manifest.py [--expect-version-code N] [--expect-version-name NAME] ARTIFACT...` | Reads the merged manifest out of each APK (binary XML) and AAB (protobuf XML) and fails unless it matches `config/android-production-manifest-policy.json`: package, minimum and target SDK, backups off, not debuggable, no deep link, no IrohaConnect or debug-only component, and exactly the reviewed permissions, `<queries>` and exported components. Needs no SDK tool. `test-verify-release-manifest.py` tests it. | `android_release.yml` (release AAB and APK); test in `android_ci.yml` |
| `verify-production-google-identity.gradle` | Applied by `app/build.gradle.kts`. Refuses a `productionRelease` build unless `SORA_GOOGLE_TOKEN_PROD` is the Google Drive backup client pinned in `config/android-production-google-identity.json` (an empty pin is refused too) and, when pinned, `google-services.json` is for the pinned Firebase app. See "Google identity pins" below. | Every `productionRelease` build |
| `google-identity-pin.py from-apk\|hash\|check` | `from-apk` and `hash` print the SHA-256 values to put into the pin file; `check` compares the configured values with the pins. Prints hashes only. `test-google-identity-pin.py` tests it, the Gradle script above and the release gate of `:app`. | `android_release.yml` (`check`, before the build); test in `android_ci.yml` |
| `run-signed-production-apk-device-smoke.sh` | Checks, installs and launches the signed release APK on a test device. | Manual |
| `update-sora2-metadata.mjs` | Refreshes the bundled SORA2 runtime metadata asset. | Manual |
| `generate-middle-egyptian-localization.mjs`, `verify-middle-egyptian-localization.mjs` | Localization tools for the Middle Egyptian locale. | Manual |

The release-qualification scripts (receipts, controllers, canaries, rollout gates and
`verify-production-modernization.mjs`) were removed in 3.9.0. Their documentation is in
`docs/history/modernization/`.

## Device tests

Run them for every release candidate. The "Android Release" workflow runs them first and
builds nothing when they fail. On a machine without network access:

```
export ANDROID_HOME=$HOME/Library/Android/sdk
bash scripts/start-emulator-for-device-tests.sh --avd SORA-Api34 --read-only --port 5560
ANDROID_SERIAL=emulator-5560 bash scripts/run-production-connected-device-qualification.sh --offline
adb -s emulator-5560 emu kill
```

- `--offline` is passed to every Gradle call, also by the two phase scripts. Further Gradle
  arguments go into `SORA_GRADLE_FLAGS`, split on spaces, for example
  `SORA_GRADLE_FLAGS="-Dorg.gradle.java.installations.paths=/path/to/jdk17,/path/to/jdk21"`.
- With more than one device attached, `ANDROID_SERIAL` is required; the scripts stop without it.
- The summary and one log per group are written to `build/device-qualification/` (or
  `--report-dir`). Keep `summary.txt` with the release record: it names the commit and the device.
- A group that ran no test counts as failed, so a wrong class name cannot pass.
- Repeat on Android 16 with 16 KB pages before a release: locally with the AVD
  `SORA-Api36-16k`; on GitHub by starting "Android Upgrade Device Tests" with the system image
  `system-images;android-36;google_apis_ps16k;x86_64`.
- The MigrationManager script proves that its two APKs use the isolated package before it
  installs them, with `apkanalyzer` or, when the SDK command-line tools are not installed,
  with `aapt2` from build-tools (`SORA_APK_INSPECTOR=aapt2` forces the second).

## Google identity pins

`config/android-production-google-identity.json` holds two SHA-256 values. The IDs behind
them are public (they are inside every released APK); only the hashes are stored.

| Field | What it pins | When it is empty |
|---|---|---|
| `googleDriveClientIdSha256` | The Google Drive backup OAuth client ID, `SORA_GOOGLE_TOKEN_PROD`. It must be the client 3.8.6.3 used, or people who update cannot see their existing Drive backups. | Every `productionRelease` build is refused. |
| `firebaseAppIdSha256` | The Firebase app ID (`mobilesdk_app_id`) of `jp.co.soramitsu.sora` in `google-services.json`. With another app, crash reports and the rollout signals go to another project. | Not compared; the build prints a warning. A zeroed or foreign `google-services.json` is refused either way. |

To fill them in, use one of these. Both print the same values when the secret is right.

```
# From the APK that Google Play serves (3.8.6.3): no secret needed.
python3 scripts/google-identity-pin.py from-apk sora-3.8.6.3.apk

# From the release secrets, on a machine that has them.
SORA_GOOGLE_TOKEN_PROD=... python3 scripts/google-identity-pin.py hash --google-services app/google-services.json
```

Copy the printed `googleDriveClientIdSha256` (and `firebaseAppIdSha256`) into the pin file,
commit, and have the commit reviewed by someone who checked the value against the Play APK.
`from-apk` is the authoritative source: a pin computed only from the secret proves that the
secret matches itself. To check a machine or the CI secrets against the pins:

```
SORA_GOOGLE_TOKEN_PROD=... python3 scripts/google-identity-pin.py check --google-services app/google-services.json
./gradlew :app:verifyProductionGoogleIdentity
```
