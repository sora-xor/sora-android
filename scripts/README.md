# Scripts

Run these helpers locally. The Google identity script is applied by Gradle; the other
checks are explicit commands. See the [local release checklist](../docs/release-3.9.0/RELEASE-CHECKLIST.md)
for build inputs and the commands to validate a signed release.

| Script | What it does | Local use |
|---|---|---|
| `verify-android-native-16kb.py APK_OR_AAB` | Fails if a 64-bit native library is not aligned for 16 KB pages. | After building APKs and AABs |
| `verify-release-signing.py ARTIFACT...` | Fails unless each APK/AAB is signed by `uploadCertificateSha256` in `config/android-production-signing-identity.json`. | After building signed artifacts |
| `verify-release-json-export.py ARTIFACT...` | Fails unless the DEX code of each APK/AAB has one method that writes every key of the several-account JSON backup file as a string constant (`JsonAccountsEncoder.generate`). A minified build that serialises a model class through Gson reflection writes that file as `{}`, and has no such method. | After building signed APKs and AABs |
| `test-android-native-16kb.py`, `test-verify-release-signing.py`, `test-verify-release-json-export.py` | Tests for the three checks above. | Run with `python3` |
| `verify-production-release-lock-coverage.gradle` | Gradle init script with two tasks. `verifyProductionReleaseLockCoverage`: every `productionRelease` configuration has a lock entry, and no stale ones remain. `resolveReleaseDependencyLocks`: resolves every `productionRelease` configuration, so a dependency change without an updated `gradle.lockfile` fails (locking is STRICT only for those configurations, which debug builds never resolve). Needs no secrets. Run with `./gradlew -I scripts/verify-production-release-lock-coverage.gradle verifyProductionReleaseLockCoverage resolveReleaseDependencyLocks`; after an intended dependency change, add `--write-locks` to that command and commit the updated lockfiles. `test-production-release-lock-coverage.mjs` tests both tasks. | Before a release build |
| `run-production-connected-device-qualification.sh [--offline] [--report-dir DIR]` | Runs the wallet-upgrade device tests (storage, Room migration, upgrade scenarios from 3.8.6.3 and 2.3.3 storage, recovery screens, crypto runtime, encrypted wallet storage, migration production path) and the other device tests of `:app`. Runs every group even when one fails, then writes `summary.txt` with the commit, the device and the result of each group. Emulator only: it stops on any other device, because `:app:connectedProductionDebugAndroidTest` installs and then uninstalls `jp.co.soramitsu.sora`, deleting that app's data. Required for every release candidate; see "Device tests" below. | Before each release candidate; add `--offline` for cached dependencies |
| `run-encrypted-wallet-upgrade-qualification.sh [--offline]`, `run-migration-manager-production-path-qualification.sh [--offline]` | The two multi-process device tests used by the script above: 5 and 10 phases, each in a new process. The second one installs the isolated `jp.co.soramitsu.sora.qualification` test APK, never the production package; its last three phases take a pre-multiaccount wallet at Room 50 through every migration and a restart. | Through the script above, or by hand |
| `start-emulator-for-device-tests.sh --avd NAME [--port PORT] [--read-only] [--create SYSTEM_IMAGE [--install-image]]` | Starts a headless emulator and waits until Android has booted. Refuses a port on which an emulator is already attached. | Before running emulator tests |
| `device-test-common.sh` | Shared by the device-test scripts (`--offline`, `SORA_GRADLE_FLAGS`, finding `adb`, the emulator-only guard). Not run by itself. | |
| `test-device-test-scripts.py` | Runs the device-test scripts against recorders in place of Gradle, `adb`, `apkanalyzer` and `aapt2`: `--offline` reaches every Gradle call, all 15 phases run in order, every group runs after a failure, a group without tests fails, a phone or an ambiguous device is refused, a non-isolated APK is never installed. | Run with `python3` |
| `verify-release-manifest.py [--policy FILE] [--expect-version-code N] [--expect-version-name NAME] ARTIFACT...` | Reads each APK (binary XML) and AAB (protobuf XML), checking package, SDK, backups, debug flags, permissions, queries and exported components. The default `android-production-manifest-policy.json` retains the public 3.9.x store rules (no deep links). For the current Nexus release line, explicitly select `config/android-nexus-manifest-policy.json`, which adds only the existing IrohaConnect activity and its exact `iroha://connect` and `irohaconnect://connect` filters. Extra filters, URI attributes and aliases fail. Needs no SDK tool. `test-verify-release-manifest.py` tests both policies. | After building a minified APK and AAB |
| `verify-production-google-identity.gradle` | Applied by `app/build.gradle.kts`. Refuses a `productionRelease` build unless `SORA_GOOGLE_TOKEN_PROD` is the Google Drive backup client pinned in `config/android-production-google-identity.json` (an empty pin is refused too) and, when pinned, `google-services.json` is for the pinned Firebase app. See "Google identity pins" below. | Every `productionRelease` build |
| `google-identity-pin.py from-apk\|hash\|check` | `from-apk` and `hash` print the SHA-256 values to put into the pin file; `check` compares the configured values with the pins. Prints hashes only. `test-google-identity-pin.py` tests it, the Gradle script above and the release gate of `:app`. | Before the release build |
| `run-signed-production-apk-device-smoke.sh` | Checks, installs and launches the signed release APK on a test device. | Manual |
| `update-sora2-metadata.mjs` | Refreshes the bundled SORA2 runtime metadata asset. | Manual |
| `generate-middle-egyptian-localization.mjs`, `verify-middle-egyptian-localization.mjs` | Localization tools for the Middle Egyptian locale. | Manual |

The release-qualification scripts (receipts, controllers, canaries, rollout gates and
`verify-production-modernization.mjs`) were removed in 3.9.0. Their documentation is in
`docs/history/modernization/`.

## Device tests

Run them on the release commit before building signed artifacts. On a machine without
network access:

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
- The full device-test gate runs on a device with 4 KB pages, such as the AVD `SORA-Api34`.
- Repeat on Android 16 with 16 KB pages before a release: locally with the AVD
  `SORA-Api36-16k`, created with an installed 16 KB system image such as
  `system-images;android-36;google_apis_ps16k;x86_64`. The existing subset leaves out
  `WalletUxAcceptanceTest` and the MigrationManager phases, which mock final classes and
  load MockK's agent from the test APK. The full suite is qualified on 4 KB devices;
  mockk-agent-android 1.14.3 remains unqualified for the full 16 KB run. Its `PT_LOAD`
  segments pass inspection, but the strict repository gate rejects its `GNU_RELRO[5]`
  end addresses (`0x142000` on arm64 and `0x143000` on x86_64), which are not 16 KB aligned.
  This static finding does not establish that a device loader rejects the agent. The
  summary retains "PASS for the 16 KB subset". Production native libraries are checked
  separately by `verify-android-native-16kb.py`.
- The MigrationManager script proves that its two APKs use the isolated package before it
  installs them, with `apkanalyzer` or, when the SDK command-line tools are not installed,
  with `aapt2` from build-tools (`SORA_APK_INSPECTOR=aapt2` forces the second).

## Full application code coverage

Run `ANDROID_SERIAL=emulator-5560 bash scripts/run-code-coverage.sh --offline` on a disposable
4 KB emulator. This runs every productionDebug unit test, lint, the device suites, and the
ordered storage qualification phases, then enforces **90.01% line coverage**. The JSON summary is
`build/reports/coverage/summary.json`; Kover and device HTML reports remain under each module's
`build/reports/` directory. Generated files, dependencies, and the test-only `test_data`/`baselineprofile` modules are excluded; all checked-in
productionDebug application sources, including UI and debug support, remain in scope.

Kover measures JVM tests and JaCoCo measures instrumented device tests. The gate combines hits
across all application and library modules by source file and executable line, so running the same line twice never increases coverage.
It snapshots production source hashes before the run and refuses results if source files were
added, deleted, or changed during testing. The ordered multi-process qualification suites are
also required to pass; their separate executions do not add hits to this conservative report.
`python3 scripts/test-code-coverage.py` checks the merge, source scope, and threshold behavior.

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
secret matches itself. To check the release machine’s configured inputs against the pins:

```
SORA_GOOGLE_TOKEN_PROD=... python3 scripts/google-identity-pin.py check --google-services app/google-services.json
./gradlew :app:verifyProductionGoogleIdentity
```

The verified Play 3.8.6.2/3.8.6.3 APKs use different clients for Drive and Firebase.
Their compiled `requestIdToken` Drive client has SHA-256 `398674a0b4925677d8f4041c88bffd42e64ef7f6a9b4ba4e26c356699d744277`.
The `default_web_client_id` in Firebase resources has SHA-256 `f9200c28c6a38cc326f89e92e5ee6134e4c21e84194ffb7fd408a9aa175a9059`;
it must not be substituted into `SORA_GOOGLE_TOKEN_PROD`. Keep the official Firebase
configuration unchanged and provide the verified historical Drive client separately.
The pin provenance records both authentic APK hashes and the compiled sign-in call site.
