# SORA Wallet Android 3.9.0: changes

Each section covers one release task: what changed, why, and what users see.

## a1: Qualification gates retired, lean CI

### Why

- No signed, minified production build had ever been produced. The only
  workflow that could sign one (`production_release_qualification.yml`)
  needed receipts, controllers, canary bundles and dual-signed reviews that do
  not exist. It stopped before the build because
  `verify-production-modernization.mjs --dependency-preflight` exits 1, and
  it could not be started anyway because it was not on the default branch.
- `verify-production-modernization.mjs` (16,278 lines) matched exact strings,
  counts and hashes in Kotlin, Gradle and workflow files. It ran on every pull
  request, so every source change also needed a verifier change.
- Jenkins held the signing secrets but ran only develop-flavor tests.

### What changed

**New CI**

- `.github/workflows/android_ci.yml` (pull requests; pushes to `master`,
  `modernize`, `develop`, `release/**`). Two jobs, JDK 17, no secrets:
  - "Unit tests (productionDebug)": first the release dependency lock check
    (`verifyProductionReleaseLockCoverage` and the new
    `resolveReleaseDependencyLocks` from
    `scripts/verify-production-release-lock-coverage.gradle`), then
    `testProductionDebugUnitTest`, plus the tests of the release helper
    scripts and of the lock check. Dependency locking is STRICT only for
    `productionRelease` configurations, which debug builds and tests never
    resolve; without this step a dependency change with a stale
    `gradle.lockfile` would first fail in the release run.
  - "Build, lint and 16 KB check": `:app:assembleProductionDebug`, the 16 KB
    native page check on that APK, and `:app:lintProductionDebug`.
- `.github/workflows/android_release.yml` (manual `workflow_dispatch` with
  `version_code` and `version_name` inputs, or a pushed `android/v*` tag,
  whose name gives the version name). One job in the `production`
  environment:
  1. Fails early, with a named list, when a release secret is missing, the
     version code is not a whole number above 122, the version name is not
     like `3.9.0`, or `appVersionName` in `app/build.gradle.kts` differs from
     the expected version name.
  2. Writes the upload keystore from `CI_KEYSTORE_BASE64` to an owner-only
     file in the runner's temp directory and checks password and alias with
     `keytool`. Writes `app/google-services.json` from `GOOGLE_SERVICES_JSON`
     and checks that it has a client for `jp.co.soramitsu.sora`.
  3. Runs `testProductionDebugUnitTest` (blocking) and
     `:app:lintProductionDebug` (report only; pull-request CI enforces it, and
     a failure here adds a warning to the run). Then builds
     `:app:assembleProductionRelease` and `:app:bundleProductionRelease`:
     minified, signed, strict dependency verification, Gradle cache off, and
     AGP's blocking release `lintVital` checks.
  4. Checks the APK's package name, and that its versionCode and versionName
     equal the requested ones, so a release commit without the version bump
     cannot produce a green run.
  5. Checks that the AAB and APK are signed by the upload certificate pinned
     in `config/android-production-signing-identity.json` (`b35dfe16…`, the
     certificate of 3.8.6.3 on Google Play).
  6. Runs the 16 KB native page check on the AAB and the APK.
  7. Uploads the AAB, APK, R8 mapping, lint report and `SHA256SUMS.txt` as
     `sora-android-release-<versionName>-<versionCode>` (kept 90 days), then
     deletes the keystore and `google-services.json`.
  It does not publish to Google Play.
- `Jenkinsfile`: also runs `testProductionDebugUnitTest`.

**New signing check**: `scripts/verify-release-signing.py`

- APK: `apksigner verify --print-certs`; exactly one signer, whose SHA-256
  must equal the pin.
- AAB: `keytool -printcert -jarfile` for the signer certificate (exactly one),
  then `jarsigner -verify -strict` against a temporary truststore that holds
  only the pinned certificate.
- `scripts/test-verify-release-signing.py` signs throwaway artifacts with a
  generated key and checks the pass, wrong-pin, unsigned and tampered cases.
- `config/android-production-signing-identity.json` now holds only the public
  certificate pins and the names of the credentials that hold the keystore.

**Removed**

- Workflows `android_verification.yml` and
  `production_release_qualification.yml`; the root Gradle task
  `verifyProductionRollout` (it ran a deleted node script).
- `scripts/verify-production-modernization.mjs`, `scripts/lib/*` (17 files)
  and 31 more scripts for candidate packages, migration controllers and raw
  evidence, funded canaries, production rollout and PI receipts, Taira
  deployment manifests, Polkamarkt extrinsic receipts and dependency-signing
  reviews.
- `scripts/qualification/legacy-encrypted-preferences` (a PayWings DataStore
  harness that no build used).
- `scripts/verify-android-localization-coverage.py`. It failed whenever a
  translation was added. Android lint (`MissingTranslation`,
  `ExtraTranslation`, `abortOnError = true`) already checks translations.
- `config/funded-canary-trust.json`, `config/production-rollout-trust.json`,
  `config/iroha-mobile-sdk-pin.json`, and the `*.blocked.json` and canary
  templates in `docs/modernization/qualification/`. All held only `null`
  placeholders and `blocked` statuses.

**Moved**: `docs/modernization/` and `config/gradle-dependency-provenance.json`
are now under `docs/history/modernization/`, unchanged.
`docs/history/README.md` explains them. The 16 KB rebuild notes there for
xcrypto, lazysodium and graphics-path still describe vendored native libraries
that ship; the camera-core, rootbeer and tensorflow-lite notes describe
libraries that the SORA Card removal later dropped. The one vendor note that
links to them points at the new path. `vendor/soramitsu-maven/CONTENTS.sha256`
and `SOURCE_PROVENANCE.json` were updated for that one-line edit and still
verify with `shasum -c`. This task changed no vendored artifact.

**Kept**: dependency lockfiles, `gradle/verification-metadata.*`, the
`vendor/` repository, the 16 KB check and its test, the lock-coverage init
script, the SORA2 metadata and Middle Egyptian localization tools, the
`qualification` debug flavor with its isolated migration instrumentation test,
and the wallet-upgrade device test runners. `scripts/README.md` lists what
each remaining script does. The connected-device runner no longer calls the
deleted verifier, and the signed-APK smoke script checks the certificate pin
with `verify-release-signing.py`. The connected-device runner now stops
unless the device is an emulator (`ro.boot.qemu` / `ro.kernel.qemu`):
`:app:connectedProductionDebugAndroidTest` installs and then uninstalls
`jp.co.soramitsu.sora`, which would delete a developer's wallet data on a
phone.

The open credential action that only the verifier tracked
(`GRADLE_CREDENTIAL_INCIDENT_NOT_CLOSED`: revoke the JitPack auth token that
was committed through 3.8.6.3 and rotate its replacement) is now an item in
`RELEASE-CHECKLIST.md`.

`feature_polkaswap_impl/src/test/resources/polkamarkt_web_contract.json` still
names `scripts/qualify-polkamarkt-extrinsic-receipts.mjs` as the receipt
merger of the recorded web contract. The test only compares that string, so
it was left as recorded.

### User-visible effects

- None in the app. No Kotlin, resource, manifest, dependency or lockfile
  change. Wallet keys, accounts, addresses and stored data are not touched.
- For the team: pull requests get two plain CI checks. A signed production
  AAB and APK come from one manual workflow run (see
  `RELEASE-CHECKLIST.md`).

### Verification

- `:app:assembleProductionDebug`: succeeded.
- `testProductionDebugUnitTest` (offline, all tasks rerun): 1,056 tests in 16
  modules, 0 failures, 6 skipped. `:network` has no unit tests, but its test classpath needs the
  `androidx.lifecycle:lifecycle-common:2.3.1` POM, which is missing from this
  machine's offline Gradle cache, so the local run used
  `-x :network:testProductionDebugUnitTest`. CI resolves it online; the
  checksum is in `gradle/verification-metadata.xml`.
- 16 KB check on the productionDebug APK: passed (16 native libraries).
- `scripts/test-android-native-16kb.py` (11 tests) and
  `scripts/test-verify-release-signing.py` (11 tests): passed.
- `verify-release-signing.py` rejects the debug APK (signed with the
  `cidebug` key, not the production certificate).
- Release rehearsal with a throwaway upload key (local, offline):
  `verifyProductionReleaseSigning` accepted the owner-only keystore;
  `CI_BUILD_ID` mirroring gave the APK `versionCode='3090001'`; the minified
  release APK and AAB passed the 16 KB check; `verify-release-signing.py`
  rejected both against the production pin and accepted both against the
  throwaway certificate (APK through apksigner, AAB through
  `jarsigner -verify -strict`). The workflow's input, keystore,
  google-services, version and checksum steps were run locally with good and
  bad inputs. The offline cache lacks the lint tool (`groovy:3.0.22`) and
  `compose-group-mapping:2.3.21`, so lint and the Compose mapping merge could
  not run here; both are in `gradle/verification-metadata.xml` and resolve
  in CI.
- Device test runners: not run. Building their instrumentation APKs offline
  fails here because `org.hamcrest:hamcrest-library:1.3` and
  `androidx.tracing:tracing:1.1.0` are not in the local Gradle cache. The only
  change to them is the removed verifier call.
- Review fixes (lock check, versionName check, emulator guard), offline:
  - `verifyProductionReleaseLockCoverage`: 565 configurations in 30 projects.
    `resolveReleaseDependencyLocks`: 29 of 30 projects match their lockfile;
    `:network` cannot be resolved offline here for the same missing
    `lifecycle-common:2.3.1` metadata noted above (its checksum is in
    `gradle/verification-metadata.xml`; CI resolves it online).
  - Removing one `app/gradle.lockfile` entry, or adding a stale one, makes
    `:app:resolveReleaseDependencyLocks` fail with Gradle's lock message;
    `:app:dependencies` stays green in the same state (it only prints
    `FAILED`). Adding `--write-locks` restored the removed entry byte for
    byte. `node scripts/test-production-release-lock-coverage.mjs` covers
    both tasks and passes.
  - The release workflow's input and badging steps, run locally: the
    current source (`appVersionName` 3.8.6.3) with `version_name` 3.9.0
    stops early; tag/input mismatches, a missing or malformed name, and an
    injected newline are rejected; the debug APK (`3.8.6.3-debug`) fails the
    versionName check.
  - Emulator guard: passes on the SORA-Api36-16k emulator
    (`ro.boot.qemu=1`), stops when both properties are empty, and stops
    when the serial is not found.
  - `:app:assembleProductionDebug` succeeded; `testProductionDebugUnitTest`
    (all up to date, no test inputs changed): 1,056 tests, 0 failures,
    6 skipped; the two Python script tests pass (11 each).
- Re-checked on 2026-10-10, after the SORA Card removal commits landed on
  the branch (offline):
  - `verifyProductionReleaseLockCoverage`: 527 configurations in 28
    projects. `resolveReleaseDependencyLocks`: all 28 projects match their
    lockfile, `:network` included (its metadata now resolves offline).
  - `:app:assembleProductionDebug` succeeded; the 16 KB check passed on the
    APK (10 64-bit native libraries).
  - `testProductionDebugUnitTest` with no exclusions: 1,038 tests in 15
    modules, 0 failures, 6 skipped.
  - `scripts/test-android-native-16kb.py` (11), `scripts/test-verify-release-signing.py`
    (11, with `JAVA_HOME` and `ANDROID_HOME` set; without them its four
    tool tests skip) and `scripts/test-production-release-lock-coverage.mjs`:
    passed.
  - `docs/history/README.md` now says which 16 KB rebuild reports still
    describe shipping libraries (xcrypto, lazysodium, graphics-path) and
    which libraries were removed with SORA Card (camera-core, rootbeer,
    tensorflow-lite).

## a2: SORA Card, PayWings/KYC and X1 removed

### Why

- SORA Card is no longer offered in SORA Wallet. The integration brought in
  proprietary PayWings and Sumsub (IDensic) binaries (audit blocker 7), a
  KYC stack with location, microphone, NFC, storage and media permissions,
  a SORA Card backend and PayWings request on every app start, and SORA
  Card / PayWings KYC credentials compiled into `BuildConfig`, where anyone
  can read them from the APK.
- The X1 "Buy crypto" screen could only be opened from SORA Card results and
  used the SORA Card backend, so it went with the card.

### What changed

**Features and screens**

- Deleted the `:feature_sora_card_api` and `:feature_sora_card_impl` modules
  (Get SORA Card, card details, interactor, navigation graph, tests).
- More (Profile): the "SORA Card" and "Buy XOR with fiat" rows are gone.
- Wallet hub: the "SORA Card" and "Buy XOR" banners are gone; the hub no
  longer reads the remote config each time its card list changes.
- Asset details: the XOR "Buy" button is gone.
- Swap: the unused "launched from SORA Card" argument is gone. What happens
  after a swap is unchanged (transaction details, or back).
- Deleted the X1 screen, its repository, WebSocket data source and payment
  models, the unused EUR rate request (`SoraCoin`), the debug-menu X1 and
  SORA Card backend entries, `BuildConfigWrapper` (it held only card and X1
  URLs) and the SORA Card network client in `:common_wallet`.
- `MainViewModel` no longer starts the SORA Card SDK at app start.
- The `soracard` flag in the remote `mobile.json` is no longer read (the
  config JSON ignores unknown keys). The `soracard` field of the PI indexer
  `mobileConfig` query is left as it is; nothing reads it, and the indexer
  schema work owns that query.

**Stored data (no change)**

- Room stays at version 77; no schema change, no new migration.
- Migrations 65→66 and 66→67 no longer read the live `CardHubType` enum.
  They write the same rows as before from fixed values ("get sora card",
  "buy xor token", "referral system"), so a user upgrading from a very old
  version gets the same database as before.
- Existing "get sora card" / "buy xor token" rows in `globalCardsHub` stay
  where they are. `CardsHubMapper` skips ids it does not know, as before,
  so they are not shown and not deleted.
- Files that old SDK versions wrote are left in place and never read:
  `files/datastore/sora_card_data_store.preferences_pb`, the PayWings
  encrypted DataStore, the androidx.security keyset preferences and its
  AndroidKeyStore master key. Nothing deletes files or Keystore entries.
  The wallet's own key storage (encrypted preferences, `key_alias.xml`,
  AndroidKeyStore alias `key_alias`, `app.db` accounts) is not touched;
  no module that stores, derives or signs with keys depended on SORA Card.

**Build and dependencies**

- `:common_wallet` no longer exports `jp.co.soramitsu:android-sora-card`,
  and with it PayWings OAuth/KYC, IDensic, CameraX, TensorFlow Lite,
  RootBeer, JMRTD/SCUBA, `bcutil`, Koin, Retrofit, Picasso, Markwon,
  reCAPTCHA, Play Integrity, Tink, androidx.security and the `osipxd`
  encrypted DataStore adapters. The app's `osipxd` runtime dependency and
  the `bcutil`/`jmrtd` test dependencies are removed, with the two tests
  that only checked the KYC and PayWings storage formats
  (`BouncyCastleKycCompatibilityTest`, `EncryptedPreferencesCompatibilityTest`).
- BouncyCastle stays: `bcprov-jdk18on` 1.78.1 is still on every runtime
  classpath. The `bcprov` constraint in `:common_wallet` is kept because
  xcrypto and xbackup ask for 1.77.
- Vendored repository: removed `android-sora-card`, PayWings `oauth` and
  `kyc`, `idensic-mobile-sdk`, `camera-core`, `tensorflow-lite` and
  `rootbeer-lib` with their 16 KB rebuild inputs (16 → 9 modules).
  CameraX, TensorFlow Lite and RootBeer were used only by IDensic: the
  wallet's QR scanner uses zxing (journeyapps), and no source file imports
  `androidx.camera`, TensorFlow or RootBeer. `settings.gradle.kts` vendor
  filter, version catalog, `SOURCE_PROVENANCE.json` and `CONTENTS.sha256`
  (still passes `shasum -c`) are updated.
- `gradle.properties`: removed `android.uniquePackageNames=false`; it was
  needed only for the TensorFlow Lite duplicate namespace. Builds pass with
  the AGP default.
- Lockfiles: 20 regenerated offline, the two card-module lockfiles deleted.
  Reviewed line by line against the start of this task: only SDK-only
  modules disappeared, and no kept library changed version.
  `auto-value-annotations` 1.6.3 (annotations only; it came with CameraX)
  left the runtime classpaths and stays on the KSP processor classpaths.
  `gradle/verification-metadata.xml` is unchanged.
- `common/build.gradle.kts`: removed the `X1_ENDPOINT_URL`, `X1_WIDGET_ID`,
  `SORACARD_BACKEND_URL` and `SORA_CARD_*` `BuildConfig` fields and the
  environment / `local.properties` values they read. `GOOGLE_API_TOKEN`
  (Google Drive backup) is unchanged.
- R8: removed the PayWings `-dontwarn` rules.
- Manifest: removed the `<queries>` entries for the SORA Card IBAN app. The
  merged manifest no longer contains `ACCESS_FINE_LOCATION`,
  `ACCESS_COARSE_LOCATION`, `RECORD_AUDIO`, `NFC` (and the NFC feature),
  `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`,
  `WRITE_SETTINGS`, `READ_ASSISTANT_APP_SEARCH_DATA`, the Sumsub activity
  and file provider, the CameraX metadata service or `CardActivity`. The
  productionDebug merged manifest now asks for `INTERNET`,
  `ACCESS_NETWORK_STATE`, `CAMERA` (QR scanner), `USE_BIOMETRIC`,
  `USE_FINGERPRINT` (androidx.biometric), `VIBRATE`, `POST_NOTIFICATIONS`,
  `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`
  (WorkManager), plus the app's own signature-level
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Play publisher `releaseName` now follows `appVersionName` instead of
  "3.8.6.5 - SORA Card Improvements".

**Resources and dead code**

- Removed 23 strings used only by the card, X1 and "Buy XOR" screens from
  the default locale and all 23 translations (including Japanese), in one
  commit so lint `ExtraTranslation` stays clean. No new strings.
- Removed 5 drawables used only by those screens (`ic_buy_crypto`,
  `ic_buy_xor_banner_sora`, `ic_sora_on_card`, `ic_status_denied`,
  `ic_status_pending`). `ic_settings_buy_crypto` stays (Polkamarkt row).
- Removed helpers only the card code used: `OptionsProvider.euroSign`,
  `String.splitVersions()`, `compareByTotal()`.
- Not in this change: the `:network` module (WebSocket helpers) is now
  unused by app code; removing it is a possible follow-up.

### User-visible effects

- No SORA Card row in More, no SORA Card or "Buy XOR" banner on the wallet
  hub, and no "Buy" button on XOR asset details.
- Card accounts, IBAN balances and card funds are held by the card issuer,
  not by the wallet; this release does not change them. Wallet keys,
  accounts, addresses and balances are unchanged.
- The app asks Android for fewer permissions (no location, microphone, NFC,
  storage or photo access) and makes no SORA Card or PayWings requests.

### Verification

All offline, on this branch after the last a2 commit:

- `:app:assembleProductionDebug`: succeeded. The 16 KB check on the APK
  passed with 10 64-bit native libraries; arm64 now ships only
  `libsr25519java`, `libsodium`, `libjnidispatch`, `libandroidx.graphics.path`
  and `libdatastore_shared_counter` (no TensorFlow Lite, CameraX or RootBeer
  libraries).
- `testProductionDebugUnitTest`: 1,038 tests in 15 modules, 0 failures,
  6 skipped.
- `:app:lintProductionDebug`: passed (no errors outside the existing
  baseline; baseline not changed).
- `verifyProductionReleaseLockCoverage` (527 configurations in 28 projects)
  and `resolveReleaseDependencyLocks` (all 28 projects match their
  lockfile), with `--dependency-verification=strict`.
- `scripts/test-android-native-16kb.py`, `scripts/test-production-release-lock-coverage.mjs`
  and `scripts/verify-middle-egyptian-localization.mjs`: passed.
- `vendor/soramitsu-maven/CONTENTS.sha256`: `shasum -a 256 -c` passes and
  lists every vendored file (51).
- Emulator (SORA-Api36-16k, Android 16, 16 KB pages): the debug APK
  installs and opens on the onboarding screen. With a new test wallet, the
  wallet hub, More and the debug menu show no SORA Card, X1 or "Buy XOR"
  entries. After adding the old "get sora card" and "buy xor token" rows to
  `globalCardsHub` by hand (as a 3.8.6.3 database has them), the app opens
  without errors, the rows are not shown, and they are still in the
  database. Screenshots: `screens/android/a2-remove-soracard/` in the
  release scratchpad.
- R8: `:app:minifyProductionBenchmarkReleaseWithR8` (release rules,
  minified, local `cidebug` signing; `SORA_GOOGLE_TOKEN_PROD` set to a
  placeholder) succeeded with no missing-class errors after the PayWings
  `-dontwarn` rules were removed.
- Seen during the emulator check, not caused by this change: on mainnet the
  wallet hub's asset and pool cards do not appear because
  `DemeterFarmingRepositoryImpl.getDemeter` fails while encoding the
  `DemeterFarming.UserInfos` storage key (`EncodeDecodeException`), and the
  hub's error handler then shows no cards. `:demeter` is not changed by this
  task. Reported for a separate fix.
- Not done here: an upgrade from the Play 3.8.6.3 build (no 3.8.6.3 APK on
  this machine; see `RELEASE-CHECKLIST.md`).
