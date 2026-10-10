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

## a3: Live-runtime signing, indexer tolerance, flags, version

### Why

- No SORA2 transaction could be signed on mainnet. Production builds required
  runtime spec 130 / transaction version 130 and the packaged metadata hash
  for every fee estimate, transfer, swap, liquidity, referral and claim, but
  mainnet runs spec 133 / transaction version 131 (three runtime upgrades
  since 20 September).
- The runtime refresh fetched its type aliases through the PI indexer's
  `mobileConfig`, which the deployed indexer rejected, so balances and other
  runtime reads failed on every online start once the chain moved past 130.
  The URL it pointed to is also the iOS aliases file (numeric lookup ids),
  which Android cannot use.
- Several PI queries did not match the indexer that is actually deployed:
  `mobileConfig` capability fields, the history `calls(first:)`/`totalCount`
  shape and its `or` account filter, the referral `TIMESTAMP` order, and
  filter arrays over 100 ids. Multi-page reads (prices, liquidity, pool APYs)
  also failed whenever the indexer indexed a block during the read.
- The Taira testnet, the Nexus (Minamoto) portfolio and Polkamarkt were
  visible in production, and IrohaConnect could be opened by any web page or
  QR code. Old Akkadian and Middle Egyptian were offered as production
  languages.
- The app still said 3.8.6.3, a release build could fall back to versionCode
  122, and missing release secrets silently became empty strings.

### What changed

**Signing on the live runtime** (`sorasubstrate`)

- `RuntimeManager.getMutationRuntimeContext()` no longer checks a fixed spec
  version, transaction version or packaged metadata hash. It still: pins the
  canonical mainnet genesis; checks the SS58 prefix 69 from the metadata; and
  reads runtime version and metadata from the reviewed endpoint
  (`https://ws.mof.sora.org`) at one finalized block and requires the snapshot
  that encodes the call to have the same spec version, genesis and metadata
  hash (`requireMutationRuntimeIdentity`). Since the review round (below),
  every mutation-authorizing read and the submission use that endpoint
  whichever node the app's WebSocket is connected to.
  The signed payload uses the node's live spec and transaction version. The
  pre-transport check (`requireSameSora2MutationRuntimeIdentity`) is
  unchanged. All fee, transfer, swap, liquidity, referral and claim paths go
  through this context (`ExtrinsicManager`).
- Type aliases for live metadata come from the remote config file
  `substrate_types_android` (the file 3.8.6.3 used; byte-identical to the
  packaged `types_scalecodec_mobile.json` today). If it cannot be fetched or
  does not produce a valid SORA2 snapshot, the packaged file is used. The PI
  indexer is no longer involved in the runtime.
- Pending-submission recovery can decode a submission signed with the live
  runtime, including after later runtime upgrades (see "Review fixes" below
  for how the signing runtime stays available).
- `Sora2RuntimeContract.SPEC_VERSION`/`TRANSACTION_VERSION` and the hashes now
  only describe the packaged fixture and the Polkamarkt pin (Polkamarkt
  mutations stay compiled off).
- A JSON-RPC error from the node keeps its code and reason in a typed
  exception (`Sora2RuntimeRpcRemoteException`); the message stays the fixed
  code `SORA2_RUNTIME_RPC_REMOTE_ERROR`.
- Address derivation, key storage, SS58 prefix, genesis pin and the reviewed
  HTTPS endpoint are unchanged.

**PI indexer** (`PolkaswapIndexerClient`)

- A response that carries only `GRAPHQL_VALIDATION_FAILED` / `BAD_USER_INPUT`
  errors and no data becomes `PiIndexerSchemaUnsupportedException` (still an
  `IllegalStateException`, never served from the offline cache; no server
  text in the message).
- `mobileConfig`: when every error names one of the five capability fields,
  the client asks again without them and treats those capabilities as off
  (remembered for the process). Other rejections still fail.
- History: the query uses the shape every indexer branch serves
  (`calls { nodes { module method data } }`) and the account filter
  `address equalTo <account>`, the only timestamp-ordered account plan the
  indexer has. Lookups by transaction hash are unchanged.
- Referral rewards are read in `ID_ASC` order (the indexer has no timestamp
  plan for them; the app sums all rows anyway).
- Asset prices and liquidity for many tokens are requested in batches of at
  most 100 ids (the indexer rejects longer filter arrays).
- A live read now follows indexer progress: the response is attributed to the
  checkpoint seen after it, and pages of one live read may come from
  successive checkpoints of the same indexer. A checkpoint that goes back, a
  different indexer identity, or cached pages from different checkpoints are
  still rejected.
- The Activity error state now reads "Activity is temporarily unavailable.
  Your balance is not affected. Tap Refresh to try again." (Japanese
  translated; locales that held the English text get the new English text).
  It is an inline state with a Refresh button, never a dialog.

**Feature flags**

- New `ReleaseSurfacePolicy` (`:common`). In the production flavor test
  networks (Taira), the Nexus portfolio, Polkamarkt and IrohaConnect are off
  regardless of remote configuration. Develop and Soralution builds keep them
  and follow the indexer flags.
- `ProductionFeatureManager` never throws for an indexer problem: an offline
  read keeps the last complete flag set, anything else turns the features off.
  `SAFE_DEFAULT_FLAGS` and the missing-cache Taira default are now off.
- The unused remote-config feature flags (`SoraConfig.emergencyFlags`,
  `MobileDto` flag fields) were removed.
- `NexusNetwork` admission and stored network accounts are unchanged.
- During this task the deployed PI indexer was upgraded and now reports
  `nexusAvailable`, `polkamarktVisible` and `tairaDefaultVisible` as true
  (its defaults). The production gate keeps all three off in 3.9.0; this was
  checked on the emulator against the upgraded indexer.

**IrohaConnect**

- `app/src/production/AndroidManifest.xml` removes `IrohaConnectActivity`
  from production builds, so `iroha://connect` and `irohaconnect://connect`
  resolve to nothing.
- The activity closes itself in production if it is ever started; the QR
  scanner no longer routes those links in production and hands the text back
  like any other scan; a missing activity cannot crash the scanner.

**Languages**

- Old Akkadian and Middle Egyptian are no longer in the production language
  picker or the production `locale_config.xml` (Android's per-app language
  list). Develop and Soralution keep them; the resource folders stay, so lint
  baselines are unchanged.
- Deleted the orphan `common/src/tsting` source set (no `tsting` flavor
  exists). `scripts/verify-middle-egyptian-localization.mjs` now checks that
  production does not register the two test locales.

**Version and release inputs** (`app/build.gradle.kts`)

- versionName `3.9.0`.
- versionCode: `SORA_VERSION_CODE` (Gradle property or environment), else
  `CI_BUILD_ID` for Jenkins develop builds, else `3090000` for local builds.
- `verifyProductionReleaseSigning` (runs before every `productionRelease`
  task) now lists every missing input and stops: signing credentials;
  `SORA_VERSION_CODE` (required; must be above 122 and within
  `3090000..3090099` for 3.9.0); `app/google-services.json`;
  `SORA_GOOGLE_TOKEN_PROD`.
- Play `releaseName` is `3.9.0 (<versionCode>)`.
- The release workflow no longer mirrors the code into `CI_BUILD_ID`.

**Background work**

- The SORA2 and Nexus pending-recovery workers stop (success) instead of
  retrying while the wallet is in recovery or upgrade mode, and stop after 10
  attempts; the journal is durable and the next app start schedules them
  again.
- On app start they are scheduled only when the journal has unresolved rows
  (or cannot be read).
- `SoraApp` starts `NodeManager` with the first activity (once the upgrade
  gate is clear) instead of in `Application.onCreate`, so a process started
  only for WorkManager does not open the node socket or fetch node lists.

### User-visible effects

- Transfers, swaps, liquidity, referral bonding and claims can be signed and
  fees shown again on mainnet (runtime 133), and keep working after future
  runtime upgrades without an app update.
- Balances and other runtime reads work online again.
- Activity lists the account's own transactions from the indexer; prices,
  liquidity and pool APYs load reliably.
- No Taira, Minamoto or Polkamarkt entries and no IrohaConnect in the store
  app. No ancient languages in the language list.
- Known limitation: the deployed indexer can only list operations the account
  signed, so transfers received from other accounts do not appear in Activity
  yet (balances are correct). Activity and asset history now say so (see
  "Review fixes"). See `RELEASE-CHECKLIST.md`.
- Fees and sending work whichever node is selected in More > Nodes (see
  "Review fixes").

### Verification

- **Live mainnet signing** (`MainnetLiveSigningTest`, opt-in instrumented
  test, emulator SORA-Api36-16k, Android 16, 16 KB pages, arm64 native
  `libsr25519java`), 2026-10-10 03:02 JST: throwaway key from a new random
  phrase; live runtime spec 133 / tx 131, metadata SHA-256 `ab22a2d4…`, types
  `e87760d7…` (remote config file), finalized block 27,973,878;
  `payment_queryInfo` partialFee 100018412589707326 (~0.1 XOR);
  `author_submitExtrinsic` rejected with 1010 "Invalid Transaction" /
  "Inability to pay some fees (e.g. account balance too low)", so the
  signature and signed extensions were accepted. Nothing was included in a
  block. Without the opt-in argument the test is skipped.
- **Live indexer contract** (`PolkaswapIndexerClientLiveContractTest`, opt-in
  JVM test, real client with health preflight and postflight): capabilities,
  prices (957 assets, 10 pages), 150-id price lookup, pool APYs (5 pages),
  account history pages 1 and 2, last transactions and referral rewards all
  pass against `pi.soramitsu.io`.
- Unit tests: `testProductionDebugUnitTest`, 1,064 tests in 15 modules,
  0 failures, 9 skipped (the 6 skipped before plus the 3 opt-in live indexer
  tests). New: live-runtime `RuntimeManager` tests with the real packaged
  metadata (spec 133/140, remote and fallback aliases, recovery lookup),
  schema fallback, history/referral query shapes, checkpoint progress, id
  batching, node error details, `ProductionFeatureManager`, IrohaConnect
  routing and startup scheduling.
- `:sorasubstrate:connectedProductionDebugAndroidTest` without the opt-in
  argument: `RuntimeCacheAtomicRecoveryTest` (5) passes and the live test is
  skipped.
- `verifyProductionReleaseSigning` without inputs lists the four missing
  inputs; `SORA_VERSION_CODE=122` and `3100001` are rejected; `3090001` is
  accepted.
- `:app:assembleProductionDebug`, `:app:lintProductionDebug` (no new errors;
  baseline unchanged), `verifyProductionReleaseLockCoverage` and
  `resolveReleaseDependencyLocks` (28 projects) pass. The only lockfile change
  is `feature_blockexplorer_api/gradle.lockfile`: its unit-test compile
  classpath now lists mockk 1.13.11 (already on its other classpaths and in
  `gradle/verification-metadata.xml`); shipped classpaths are unchanged.
- Emulator, new test wallet: wallet hub shows assets and fiat values; Explore
  shows prices and TVL; Activity loads (empty state for a new account); with
  the upgraded indexer reporting all capabilities on, the hub lists only
  SORA2, More has no Polkamarkt row and App settings has no test-network
  toggle; the system per-app language list offers en, de, es, fr, in, nb, tr,
  ru, zh-CN; `iroha://connect` and `irohaconnect://connect` do not resolve.
  Screenshots: `screens/android/a3-release-correctness/` in the release
  scratchpad (screens that showed a recovery phrase were deleted).
- The a2 note about missing hub cards (`DemeterFarming.UserInfos`
  `EncodeDecodeException`) did not reproduce: with the runtime refreshed to
  133 the hub's asset card appears and the log has no
  `EncodeDecodeException`. (The new test wallet has no pools, so the pool card
  itself was not exercised.)

### Review fixes (a3, round 2)

Adversarial review found three dead ends and two gaps. All were confirmed in
the code; one more was found while fixing them.

**Pending submissions signed before a runtime upgrade** (blocker)

- Problem: recovery could only decode a submission with the active runtime,
  the one runtime-cache generation the manifest pointed to, or the packaged
  130 fixture. The next mainnet upgrade pruned the old generation, so a row
  signed on 133 and unresolved when 134 arrived stayed unresolved forever.
  That permanently blocked deleting that wallet (or all wallets), the legacy
  claim for it, and left a "pending" item in Activity.
- Found while fixing: the reviewed node `ws.mof.sora.org` keeps only about
  256 blocks (~25 minutes) of state (`4003 State already discarded`). The
  System events of an inclusion block older than that could never be read
  there, so the same dead end happened without any upgrade once a row was
  older than 25 minutes. Block bodies are kept.
- `RuntimeManager` cache pruning now keeps every generation that an
  unresolved journal row names (`Sora2RecoveryRuntimeRetention`, read-only
  journal query). Nothing is pruned while the upgrade gate is closed or the
  journal cannot be read. Pruning runs after the runtime lock is released.
- Recovery looks up the exact content-addressed generation
  (`<spec>-<genesis>-<metadataSha256>-<typesSha256>`), not only the
  manifest's. If it is gone (the OS may clear the cache directory), it is
  rebuilt from the chain: runtime version and metadata at the row's
  era-birth block, admitted only when spec/transaction version and the
  metadata SHA-256 equal the journaled values, with the cached, packaged or
  remote-config type file whose SHA-256 equals the journaled one. The rebuilt
  generation is saved for later passes. The download runs outside the
  runtime lock.
- Status-only reads at historic block hashes (System events, metadata and
  runtime version) are asked of the reviewed endpoint first and, only on
  `4003 State already discarded`, of the archive node `https://mof2.sora.org`
  (one of the three default SORA nodes; `STATUS_ARCHIVE_RPC_ENDPOINT`). The
  block hash always comes from the reviewed endpoint, and the metadata is
  hash-checked. Signing, nonces, fees and submission never use it.
- The recovery pass scans the blocks of every selected row, with or without
  its runtime. A row whose hash is absent from all blocks of its mortal era
  becomes `EXPIRED_NOT_INCLUDED` without any runtime. The signing runtime is
  looked up only for a row found in a block; if it is still unavailable the
  row stays unresolved, is never guessed, and its scan restarts at the era
  birth so the inclusion is found again.
- No journal row is deleted or rewritten by any of this; only states the
  chain proves are written.

**Sending blocked when the app was on mof2 or mof3** (3 majors)

- Problem: every fee quote and signature required the WebSocket to be
  connected to exactly `wss://ws.mof.sora.org`. `NodeManager` auto-switches
  (and saves) mof2/mof3 after a few failed reconnects, and 3.8.6.3 users
  could already have them selected, so a short outage blocked all sending
  with an unexplained error until the user found More > Nodes.
- The runtime context, nonce and submission already went through the
  reviewed HTTPS endpoint; only the binding check and the fee quote used the
  socket. The binding check is removed from `getMutationRuntimeContext()`
  and the fee (`payment_queryInfo`) now also goes through the reviewed HTTPS
  endpoint (`Sora2BoundedRuntimeRpcClient.queryPartialFee`). Every value that
  authorizes a mutation and the one submission attempt therefore come from
  the reviewed endpoint whichever node shows balances; the genesis pin,
  SS58 check and runtime identity checks are unchanged. The unused WebSocket
  fee code (`SubstrateCalls.getExtrinsicFee`, `FeeCalculationRequest`,
  `FeeResponse*`) was removed. Auto-switching itself is unchanged.
- If `ws.mof.sora.org` itself is unreachable, fee quotes and sending wait
  for it (as before); nothing latches.

**No node connection after in-process recovery** (major)

- Problem: `NodeManagerImpl` checked the upgrade gate once in `init`. If the
  first activity was created while the gate was closed (low storage,
  interrupted migration), recovery on the splash screen (Try again, Check
  again, Continue read-only) opened the wallet in the same process with no
  SORA2 socket until the app was force-closed.
- `NodeManager.ensureStarted()` starts the connection once the gate is
  clear and is idempotent; `SoraApp` calls it on every activity creation and
  resume until it has started.

**Activity silently missing received transfers** (2 majors)

- The deployed indexer still has no plan for `dataTo`/`dataFrom` (checked in
  its `query-policy.ts`, read-only). Activity (list and empty state) and the
  asset page's recent activity now show: "Transfers you received may not be
  listed here yet. Your balance is always up to date." (Japanese:
  「受け取った送金は、まだここに表示されないことがあります。残高は常に最新です。」;
  other locales carry the English text, as they do for the neighbouring
  Activity strings, so lint baselines are unchanged). The switch is
  `PiHistoryCoverage.LISTS_RECEIVED_TRANSFERS`, next to the history filter.

**Smaller fixes**

- A failed save of a refreshed runtime (for example a full disk) no longer
  fails runtime reads or signing: the validated runtime is used in memory and
  saving is retried at the next refresh.
- Contact search on the Send screen used a substring filter over every
  account's history that the indexer rejects, so it never found anything. It
  now reads the account's newest 100 signed operations once (cached for a
  minute), lists their counterparties (newest first; also as "Recent
  recipients" for an empty query) and filters on the device; only valid SORA
  addresses are offered.

**Verification (round 2)**

- New unit tests: runtime signed at 133 is still decoded after 134 is
  published and the cache pruned; an evicted runtime is rebuilt from the
  chain and saved; rebuilt runtimes with the wrong version or metadata hash,
  or an unreachable node, are rejected; no pruning when the journal is
  unreadable; full disk during a required refresh; a never-included row
  expires with no runtime; an included row without its runtime is never
  marked expired and is rescanned; fee parsing; archive fallback only on
  discarded state and only for status reads; `NodeManager` starts after the
  gate clears; contact peers. Mutation checks: removing the retention or the
  rescan makes the new tests fail.
- `testProductionDebugUnitTest`: 1,074 tests in 15 modules, 0 failures,
  9 skipped (the same opt-in live tests and earlier skips).
  `:app:assembleProductionDebug` passes; `:app:lintProductionDebug` reports
  the same counts as before (no new errors; baseline unchanged).
- `MainnetLiveSigningTest` on SORA-Api36-16k (2026-10-10 04:27 JST), now
  with the fee through the HTTPS client and no WebSocket at all: spec 133 /
  tx 131, metadata `ab22a2d4…`, partialFee 100018412589707326,
  `author_submitExtrinsic` rejected with 1010 "Inability to pay some fees".
  New `statusReadsForOldBlocksAreAnsweredByTheArchiveEndpoint`: runtime
  version, metadata (623,617 bytes) and System events at a block 20,000
  blocks old are served through the archive fallback. Both pass.
- Emulator SORA-Api34, production debug build, wallet imported from the
  public BIP39 test vector: with `wss://mof2.sora.org/` selected and the app
  restarted (socket connected to mof2 per log), the swap screen shows
  "Network fee 0.10002071 XOR". Activity's empty state shows the
  received-transfers note in English and Japanese. (This AVD returns black
  screenshots, so the evidence is the accessibility dump:
  `screens/android/a3fix/` in the release scratchpad.)

### Review fixes (a3, round 3)

**Activity crashed on "load more" after a new row was indexed** (blocker)

- Problem: once a3 made the history query work, Activity's "load more" path
  became reachable. To load page N, the indexer adapter re-reads pages 1..N
  with cursors (100 rows a page), and the handler appended page N to the rows
  it already listed without removing repeats. If one new row for the account
  was indexed between two loads (for example a swap that was still only in
  the local overlay when page 1 was read), every older row shifted down by
  one, so the new page 2 started with the last row of the old page 1. The
  Activity list keys each item by transaction hash, so Compose threw
  "Key ... was already used" and the Activity tab (and the per-token
  history) crashed. Wallets with more than 100 signed operations were
  affected.
- `TransactionHistoryHandlerImpl` now appends only rows whose transaction
  hash (compared case-insensitively) is not already listed. Rows are never
  lost: a new row only pushes older rows down, so a repeat is the only
  effect; a refresh re-reads from page 1 as before.
- If a page brings no new rows (all repeats, or none matching a token
  filter), the handler reads the next page itself, at most two extra pages
  per request, so the list never waits on a "load more" that the scroll
  position cannot trigger again.
- Safety net in the list itself: `historyListKeys` gives every LazyColumn
  item a unique key (transaction hash or header title; only a repeat gets a
  numbered suffix). This also covers a second date header with the same
  title, which can happen when a pending transaction from an earlier day is
  listed first.
- Considered and not done: continuing from the last page's cursor instead
  of re-reading pages 1..N. The re-read validates the indexer checkpoint
  across all pages in one read; keeping a cursor across user actions would
  lose that check and change the repository paging API late in the release.
  Removing repeats fixes the crash without it.

User-visible effect: no crash when scrolling Activity or a token's history;
each transaction is listed once.

Verification: new unit tests in `TransactionHistoryHandlerTest` (the indexer
gains a row between the page-1 and page-2 loads; differently cased hashes;
a page with only repeats reads the next page; the extra reads are bounded)
and `HistoryListKeysTest` (unique keys for repeated transactions and
headers). The four handler tests fail on the previous handler and pass now.
New instrumented Compose test `TxHistoryListRepeatedItemsTest` renders the
real Activity list with a repeated transaction and a repeated date header on
emulator SORA-Api34: it passes, and with the previous list code it fails with
`IllegalArgumentException: Key "Oct 7" was already used`.
`testProductionDebugUnitTest`: 1,081 tests in 15 modules, 0 failures,
9 skipped (the opt-in live tests and earlier skips).
`:app:assembleProductionDebug` passes; `:app:lintProductionDebug` reports
863 warnings, 1 hint and 180 baselined errors (baseline unchanged).

## a4: Key safety and lockout fixes (core)

### Why

The 3.9.0 candidate protected keys well, but several common 3.8.6.3 states
stopped the app at every start, with no way forward except reinstalling
(which would have deleted the keys). The keys were never at risk; the app
just refused to open. The audit's fix map (F1–F12) lists them; the main
ones:

- **Abandoned account creation (the most common case).** 3.8.6.3 saved the
  new keys and phrase when the person tapped Continue on the name screen and
  wrote the account row only at the end. Backing out left a key set with no
  account. The candidate's upgrade gate treated any such set as
  `ORPHANED_WALLET_STORAGE` and blocked startup forever, also when it was
  the first account (the person never even reached onboarding).
- **Other leftovers** blocked the same way: a selection that points at a
  deleted account, a stale 2.x `prefs_address_pure` after the original 2.x
  account was deleted, a partial key set (3.8.6.3 saved keys in three
  separate edits), an account row whose keys were removed, a blank address
  row, and a missing `app.db` with keys still present.
- **One bad account locked out every account.** A missing, incomplete or
  undecryptable key set for one account failed the whole wallet
  verification (3.8.6.3 had quietly tolerated undecryptable fields).
- **Busy Keystore became permanent.** `EncryptionUtil` remembered the first
  Keystore failure for the life of the process, and the wallet verification
  then wrote it down as `RECOVERY_REQUIRED`, which needed manual recovery.
  Full disk and I/O errors were written down the same way.
- **Backups.** Every cold start re-hashed and integrity-checked every backup
  on the main thread; a new full snapshot was made for every change of
  account set or selection; an invalid, interrupted or old-format backup,
  more than 64 backups, a gap in generations, or too little free space for
  a full copy blocked startup.
- **Data deletion by Android.** `EncryptionUtil.hasExistingWalletMaterial`
  opened `app.db` with Android's default error handler, and Room's default
  corruption callback is the same handler: both delete a database that
  SQLite reports as corrupt (F7, F8).
- **Phrase length.** SORA2 import accepted only 12 and 24 words, so 15, 18
  and 21-word phrases (3.8.x accepted them; SORA v1 phrases have 15 words)
  could not be restored (F9).
- **Onboarding kept creating orphans** (keys written at Continue), and
  importing the phrase of such an orphan failed with "already imported"
  (F10).
- **Remove all accounts** wiped key sets that no account owns, and the
  deletion preview refused to run while such a set existed (F12).

### What changed

Rules kept everywhere: key material (`prefs_priv_key*`, `prefs_pub_key*`,
`prefs_key_nonce*`, `prefs_mnemonic*`, `prefs_seed*`, the unsuffixed 2.x
keys, `user_pin_code`, `key_alias.xml`/`secret_key`, the Keystore alias
`key_alias`, account rows) is never deleted, overwritten, re-encrypted or
moved automatically. Addresses, derivation (sr25519, SS58 69), the genesis
pin and production signing are unchanged. Nothing tells the person to
reinstall or clear data.

**Keystore and encryption** (`EncryptionUtil`, new `WalletStorageFailures`)

- No remembered failure: every encrypt/decrypt probes the Keystore again,
  up to three attempts (0, 150, 500 ms). Busy, I/O and provider errors are a
  transient `KeyMaterialUnavailableException`; a missing alias while wallet
  data exists, or a wrapped key that no longer matches the alias, is
  non-transient.
- A new alias or wrapped AES key is still never created while encrypted
  wallet material exists. Creating the wrapped key is serialized across the
  process, so two callers cannot replace each other's key.
- `hasExistingWalletMaterial` opens `app.db` with a handler that refuses
  instead of deleting (F7).
- `WalletStorageFailures.isTransient` classifies busy Keystore, full disk,
  I/O, locked database and low memory as transient; the verification code
  uses it so these are never written down.

**Upgrade gate** (`WalletUpgradeBackup`, new `WalletRecoveryOptions.kt`,
`WalletSelectionPolicy.kt`)

- Leftovers are no longer errors. Key sets without an account row, partial
  key sets without a row, a stale selection, a stale `prefs_address_pure`,
  unsuffixed material without a recorded owner and blank account rows are
  recorded in an in-memory `WalletQuarantine` (`quarantine()`) and ignored.
  Nothing is written, renamed or deleted.
- Missing `app.db` with keys present (a reinstall-style state): Room
  creates an empty database and the keys stay for restore. Stale
  `-wal`/`-shm`/`-journal` files are renamed aside to
  `no_backup/wallet-quarantine-<time>/` so a new database cannot replay
  them.
- Backups (F3, F4 tier 1):
  - Startup does a cheap inventory only (directory, completion marker,
    manifest shape and name; no hashing, copying or SQLite). An entry that
    fails is renamed aside to `no_backup/wallet-backup-quarantine-*`, never
    deleted and never blocking. Interrupted staging copies are cleaned up
    when provably redundant, otherwise renamed aside the same way.
  - Generation gaps are allowed; the 64-backup limit is gone. Old
    current-schema snapshots are pruned (the newest 3 and every backup taken
    before a schema migration are kept); removal uses the existing
    exact-tree checks.
  - At the current schema, one verified snapshot is made only if none
    targets the current schema; every backup step there is best effort and
    only reported (`nonBlockingWarnings()`).
  - Before a schema migration a verified backup is still required (the
    original authors' rule). With too little space for a full copy, a
    keys-only backup is made (encrypted preferences, wrapped key and the
    account list; the database holds no secrets; manifest `kind:
    "keys-only"`). With too little space even for that,
    `INSUFFICIENT_BACKUP_STORAGE` carries the bytes needed and available.
- Interrupted or failed wallet verification is retried automatically up to
  3 times in a row (counter `no_backup/wallet-migration-auto-retry`, reset
  once the wallet verifies); then the recovery options are shown. Try again
  (`retry()`) always works. A recovery journal no longer has to match the
  earlier snapshot exactly; the retry re-reads and verifies everything.
- Room corruption (F8): the gated open helper wraps Room's callback; on
  corruption it keeps `app.db`, copies it (and its sidecars) to
  `no_backup/wallet-corrupt-quarantine-<time>/`, sets `DATABASE_CORRUPT` and
  routes to recovery. `allowDataLossOnRecovery(false)`.
- Forward paths for the later Safe Mode task: `recoveryOptions()` returns,
  for each blocking or transient state, `canRetry`, `retryAutomatically`,
  `canRestoreFromPhrase`, `canRestoreFromDrive`, `canShowPhraseAfterPin`,
  `canReadOnly`, `canExport`, `canContactSupport` and the storage numbers.
  `authorizeSafeReadOnly()` opens Room read-only only at the current schema
  after `PRAGMA quick_check` (otherwise "vault only").
- `WalletSigningGate`: one signing rule (account VERIFIED and no deletion
  running) used by `ExtrinsicManager`.

**Per-account isolation** (`MigrationManager`, now in
`feature_account_impl/.../domain/migration`; `UserRepositoryImpl`)

- `MigrationManager` moved from `:app` so device tests can run the real
  verification in a plain test process. It reads and checks every account's
  keys before writing anything.
- A transient failure is not journaled: the outcome is `Transient`, this
  process stays read-only and the next start or Try again verifies again. A
  Keystore that cannot unlock any key is `KeysUnavailable`, also not
  journaled.
- A missing, incomplete, unreadable or mismatching key set makes only that
  account `UNAVAILABLE` (read-only, address kept, can be selected and
  renamed, cannot sign). The other accounts verify and sign. When valid keys
  appear later the account becomes VERIFIED again.
- No account rows: nothing to verify. `REGISTERED_WITHOUT_ACCOUNTS`,
  `SELECTED_ACCOUNT_MISSING` and `SELECTED_ACCOUNT_MISMATCH` are no longer
  written; a journal left by an earlier build is cleared. Splash opens
  onboarding when registration finished but no account remains.
- An empty or dangling selection falls back to an existing account (the
  2.x owner if it has a row, else the lowest address); it is saved only in
  normal mode. Blank rows are ignored. Unsuffixed 2.x keys that do not
  verify are set aside instead of failing startup.
- Deleting an account is no longer refused because another, untouched
  account is unavailable or because the 2.x owner is stale.
- For Safe Mode: `UserRepository.adoptRetainedWallet()` and
  `unavailableWalletIds()`.

**Onboarding and import** (F9, F10; `CredentialsRepository`,
`MultiaccountInteractor`, `OnboardingViewModel`)

- Create: the new wallet is a `WalletDraft` held in memory. Keys and phrase
  are stored only when the flow finishes, together with the account row,
  under the wallet lock. Backing out stores nothing. The Google Drive backup
  made during creation uses the draft. The draft is wiped when the screen
  goes away.
- Import (phrase, raw seed, Drive): one step under the wallet lock. An
  existing account is reported as already imported without touching
  storage. Otherwise only absent values are written, in one atomic edit
  (`SoraPreferences.putStringsIfAbsent`). Values already stored for the
  address are kept when they decrypt to exactly the same keys; this adopts a
  key set left by an unfinished flow. A different or unreadable stored value
  is never overwritten: nothing is written and the new
  `ACCOUNT_STORAGE_CONFLICT` message is shown.
- SORA2 phrase import accepts 12, 15, 18, 21 and 24 words. Nexus derivation
  stays 12/24.
- Addresses are derived with `Sora2AddressCodec` (SS58 69, the value the
  runtime validates) instead of waiting for the runtime.

**Remove all accounts** (F12; `SoraPreferences`)

- Key sets that no account row owns are kept; everything else is cleared as
  before. Deletion coverage ignores them and a stale 2.x owner.

**Strings** (`common/src/main/res/values*/strings.xml`)

- The passphrase-length errors name every accepted length (12, 15, 18, 21
  or 24 words). (Review round: the original keys
  `common_error_mnemonic_length_error` / `common_error_mnemonic_is_not_valid`
  and every translation are kept; only the numbers changed. See below.)
- New `account_storage_conflict` ("This wallet can't be added here") and
  `account_storage_conflict_description` ("This phone already holds
  different keys for this wallet, so nothing was changed. Your funds are
  safe on the blockchain. Please contact support.").
- Japanese is translated; the other locales carry the English text. No lint
  baseline change.

### Historical storage generations

Each released layout, written by that release's own storage code, then
started with this candidate (device tests below unless noted):

| Generation | Result |
|---|---|
| 3.8.6.3 (Room 73), several accounts: 12, 15, 21, 24-word phrases and a raw seed | Upgrades; same addresses; every account signs. |
| 2.3.3 (Room 50), single unsuffixed account | Upgrades through 50→58→77; same address; signs. |
| Abandoned first create (keys, no row) | Starts at onboarding; importing the same phrase adopts the stored keys unchanged; signs. |
| Abandoned add-account | Existing wallet unaffected and signs; the leftover set is kept. |
| Selection pointing at a deleted account | Falls back to an existing account; all sign. |
| Partial key set (row or no row) | That account is read-only; the others sign. |
| Stale 2.x owner after its account was deleted in 2.4+ | Ignored; current wallet signs. |
| One undecryptable account among several | That account is read-only; the others sign. |
| Low storage | Keys-only backup and upgrade; or "needs N bytes" with retry. |
| Killed during upgrade (after schema, during verification, after staging, journal from an earlier build) | Resumes or retries automatically; signs. Four interruptions in a row: options shown, Try again works. |
| Interrupted backup copy | Cleaned up or renamed aside; upgrade continues. |
| Busy Keystore at start | Not remembered or journaled; next attempt verifies and signs. |
| 2.4–3.3 (Room 58–72) | Same layout as 3.x; the Room chain from 58 is covered by the existing `core_db` migration tests (schemas 58–77). Review round: 2.4.0 layout device-tested (see below). |
| SORA v1 (Iroha, Room 15–40), believed ~0 installs | Not device-tested. Unscoped Ed25519 keys are ignored (no SORA2 address can come from them); the 15-word phrase stays stored and can be imported. Showing it needs the Safe Mode task. |

### Not in this task

- Safe Mode screens and escape hatches (F11, fix map §4): the flags above
  are exposed, and the existing recovery panel still works (Try again;
  Continue read-only when `canReadOnly`; recovery export). A "Restore with
  phrase" button, phrase reveal after PIN, adoption banner and live network
  in read-only mode are the next task.
- F4 tier 2 (running preparation off the main thread). Tier 1 is done:
  startup at the current schema no longer hashes, copies or opens backups.

### Notes

- `mockk-android` and `datastore-preferences` were added to
  `feature_account_impl` **androidTest only**. Both are already in the
  version catalog and used by other modules; release dependency locks are
  unaffected.
- `AppDatabase.newInstance()`, `SoraPreferences.createDataStore(context,
  scope)`, `WalletUpgradeBackup.prepareWithAvailableBackupBytesForTest()` and
  `automaticMigrationRetriesUsedForTest()` exist for device tests that model
  app restarts.
- Observation (unchanged behaviour, also in 3.8.6.3): SORA2 derives the
  mini-seed with PBKDF2 over the phrase entropy, and HMAC pads short keys
  with zeros, so two phrases whose entropies differ only by trailing zero
  bytes give the same address (for example the all-zero 12 and 15-word test
  phrases). Random phrases are not affected in practice; changing it would
  change addresses. Test vectors use non-zero entropy.

### Verification

- New `feature_account_impl/src/androidTest/.../upgrade/`:
  `HistoricalWalletWriters.kt` holds the storage writers copied from
  released 3.8.6.3 (`b7b0f0fc`: `EncryptionUtil`, `SoraPreferences`,
  `EncryptedPreferences`, `PrefsCredentialsDatasource`,
  `PrefsUserDatasource`) and 2.3.3 (unsuffixed credentials, `saveAddress`),
  plus Room 50 (reconstructed 2.3.3 SQL) and Room 58+ from the exported
  schemas. `WalletUpgradeScenarioTest` (16 tests, the table above) runs each
  scenario through the production path and ends with identical addresses and
  a verified sr25519 signature per usable account.
- `core_db` `WalletUpgradeBackupTest` (99 tests) and
  `WalletIdentityMigration75Test` (18): tests that expected a block for the
  states above now expect admission with bytes unchanged; new tests for
  sidecar set-aside, automatic retry limit, pruning, gaps, keys-only backup,
  and the corruption callback keeping `app.db`.
- `common` `EncryptionUtilKeySafetyTest` (3): an unreadable `app.db` without
  alias is kept and no key is created; a wrapped key without alias is never
  replaced; a busy Keystore is retried and not remembered.
- JVM: new and updated tests in `MigrationManagerSafetyTest` (moved),
  `CredentialsRepositoryTest`, `UserRepositoryTest`,
  `LegacySingleAccountUpgradeTest`, `MultiaccountInteractorTest`,
  `OnboardingViewModelTest`, `SplashViewModelTest`.
- `MigrationManagerProductionPathQualificationTest` (`:app`
  androidTestQualification) expected 15/18/21-word phrases to be rejected for
  import; it now expects them to be accepted with the same seed as before.
- `scripts/run-production-connected-device-qualification.sh` now also runs
  `WalletUpgradeScenarioTest`.
- Results. Emulator SORA-Api36-16k (16 KB pages): `common` 8/8, `core_db`
  117/117, `WalletUpgradeScenarioTest` 16/16, encrypted-wallet upgrade
  qualification 5/5 phases. Emulator SORA-Api34: the same suites pass, plus
  `CryptoRuntimeCompatibilityTest` 2/2 and the MigrationManager
  production-path qualification 7/7 phases. (On the 16 KB emulator that
  qualification cannot start: mockk-android's JVMTI agent library is 4 KB
  aligned. This is the test tool, not the app.)
- `testProductionDebugUnitTest`: 1,097 tests in 15 modules, 0 failures,
  9 skipped (the opt-in live tests and earlier skips).
  `:app:assembleProductionDebug` and
  `:app:compileQualificationDebugAndroidTestKotlin` pass;
  `:app:lintProductionDebug` reports 864 warnings, 1 hint and 180 baselined
  errors (baseline unchanged).

### Review fixes (a4, round 1)

Reviewers found states where the first a4 round still left people without a
working way forward, a Keystore classification gap, and smaller issues. The
rules above are unchanged: no key value, wrapped key, Keystore alias or
account row is deleted, overwritten or moved automatically.

**Keystore: busy during the key unwrap** (`EncryptionUtil`)

- The Keystore reports a busy backend, a system error or a pruned operation
  handle while unwrapping the AES key as `IllegalBlockSizeException` with an
  `android.security.KeyStoreException` cause. The first round treated that
  as permanent ("this phone cannot unlock the keys"). Now only a padding
  failure, a permanently invalidated alias, a wrong-length wrapped key and
  the cause-less pre-Android 12 length error are permanent; everything else
  is transient.
- The alias probe and the unwrap run together in the bounded retry (0, 150,
  500 ms), so a single busy moment no longer fails the verification.
- A wrapped key whose first write to disk failed (full disk) is written again
  before anything is encrypted with it; before, the in-memory copy could be
  used and the next start would have found encrypted data without its key.
- New read-only probe `requireKeyMaterialReadable()`: proves stored key
  material can be unlocked without creating anything (nothing to prove on a
  new install).

**Read-only accounts can be used again** (`MultiaccountInteractor`,
`UserRepositoryImpl`)

- Importing the phrase (or raw seed) of a read-only (UNAVAILABLE) account no
  longer says "already imported". It completes the account in place: only
  missing values are added (for example the nonce lost when 3.8.6.3 was
  killed inside its three-step key save, or every value when an interrupted
  3.8.6.3 removal cleared them), every stored value must match, then the
  account is verified like the upgrade does and can sign. The account row
  and its name are kept.
- If a stored value cannot be read (left by 3.8.6.3 key regeneration),
  nothing is written and the new message `account_repair_needs_removal`
  explains the path: remove the account (confirmed by the person; a copy of
  the encrypted preference files is kept first in
  `no_backup/wallet-quarantine-<time>-removed-unavailable-account/`), then
  import the phrase again.
- Removing a read-only account is no longer refused
  (`WALLET_DELETION_WALLET_NOT_VERIFIED`); there is nothing to prove for it.
- `UserRepository.unavailableWalletOptions(address)` reports per account
  `canRepairWithPhrase`, `canRemoveAndImport`, `canShowPhraseAfterPin` and
  `canReadOnly`; `completeUnavailableWallet(address)` is the verification
  step after the add-only commit.
- Accounts restored from 2.3.x keep only unsuffixed keys. Account removal and
  adoption now read them by the same rule as signing (the unsuffixed keys
  count only when their public key derives exactly that address), so such
  accounts can be removed, and "Restore wallets found on this phone" can
  restore a 2.x account that a later version removed (the first round
  listed it but adoption always failed). Nothing is written by adoption.

**This phone cannot unlock the stored keys** (phone-to-phone transfer;
`WalletUpgradeBackup`, `MigrationManager`, `CredentialsRepositoryImpl`)

- Android 12+ "Copy apps & data" copies the app files but never the Keystore
  key. With no account rows (only leftover keys), the first round sent the
  person to onboarding, where every create or import failed. Now the
  verification runs the read-only probe first and reports
  `KeysUnavailable` (`KEYSTORE_KEY_UNAVAILABLE`); with accounts it already
  did. Nothing is written.
- New `WalletUpgradeBackup.requestSetUpAgain()` ("Set up this device again,
  keeps old data"): records the request; the next app process, before
  anything opens the wallet files, renames `app.db*`, the DataStore file,
  `sora_prefs.xml*` and `key_alias.xml*` into
  `no_backup/wallet-quarantine-<time>-set-up-again/` (renamed, never copied
  and deleted, never removed; the Keystore alias is not touched). The app
  then starts as a new install and the person restores with the recovery
  phrase or Google Drive. The caller restarts the process (Safe Mode task).
- A create or import that fails because stored keys cannot be unlocked now
  shows `wallet_keys_unavailable` instead of a generic error and records the
  state for the recovery options.

**Recovery options match the actions that work** (`WalletRecoveryOptions`)

- New `canSetUpAgain` and `restoreNeedsSetUpAgain`. Wallet writes are
  disabled in every recovery state, so a phrase or Drive restore from there
  runs after "Set up this device again" and a restart; the flags now say so
  instead of implying a direct restore.
- `canReadOnly` uses the same checks as `authorizeSafeReadOnly()` (current
  schema, `PRAGMA quick_check`, readable wallet tables, at least one
  account), so the button is never offered and then refused. A damaged
  database or no accounts: not offered.
- The existing recovery panel keeps "Continue read-only" after a failed Try
  again or read-only attempt when it is still available (it was hidden).

**Automatic retry only when the app's screens start** (`SplashInteractor`,
`SplashActivity`)

- The first round claimed an automatic retry in `Application.attachBaseContext`,
  which also runs for WorkManager, boot and receiver starts. Three background
  starts could use up the budget before the person opened the app. The retry
  is now claimed in `SplashInteractor.checkMigration`, right before the
  verification runs; the splash waits for it instead of showing the
  recovery panel first.
- Activating a verification records `completedAt` no earlier than
  `startedAt`, and a VERIFIED receipt that finished "before" it started (the
  clock moved back) is retried instead of blocking every start.
- Recovery read-only mode on Android 14 and older: Room reads through pooled
  read-only connections, where SQLite refuses the TEMP triggers that make
  wallet tables read-only. Such a connection cannot write, so it is skipped;
  the writer connection still gets the triggers before any write. Before,
  the first read in recovery mode failed.

**Backups** (`WalletUpgradeBackup`)

- Pruning keeps the newest 3 backups that pass the full validation and
  every pre-migration backup. An older current-schema snapshot is removed
  only when every wallet in it (rows, key sets, 2.x owner) is a live account
  whose keys verified on this phone; it is first renamed out of the backup
  namespace, then exactly the validated files are removed. A newer snapshot
  that only looks complete can no longer cause a valid older one to be
  removed. The decision is remembered, so unchanged backups are not
  validated again at every start.
- The current-schema snapshot (no migration is pending, so it protects
  nothing a later migration would not back up itself) is made only when its
  sources are at most 16 MiB and space is clearly enough. Space is checked
  before anything is hashed, and a failure waits a day before the next try,
  so a nearly full phone no longer hashes `app.db` on the main thread at
  every start.
- Keys-only backup when space is short needs the key files twice plus 1 MiB
  and room for one rewrite of up to 32 MiB of the database, instead of two
  copies of the whole database. Room's schema steps run in one SQLite
  transaction, so if space still runs out the database is unchanged and the
  storage options are shown.
- The "database missing" check uses `lstat` and treats only "no such file"
  as missing. Any other error (for example an I/O error) is reported as a
  transient `DATABASE_PATH_UNREADABLE` instead of setting aside a live
  write-ahead log.

**Smaller fixes**

- Blank account rows are ignored by account removal too (preview, commit,
  resume and the deletion hashes), so "Remove account" and "Remove all"
  work on phones with such a row. The blank row is never deleted.
- Creating, importing or restoring a phrase is refused before any phrase is
  shown while wallet changes are disabled, so nobody writes down or backs up
  a wallet that cannot be added.
- 2.x unsuffixed keys that cannot be verified are listed in the quarantine
  (`unrestorableLegacyOwner`) so the recovery screens can offer their phrase.
- Passphrase-length errors: the first round replaced 21 translations with
  English. The original keys and every translation are back, with only the
  numbers changed (Japanese uses the reviewed wording). Middle Egyptian keeps
  its generated text.

**User-visible effects**

- A read-only account becomes usable again by importing its own phrase, or
  can be removed and imported again; other accounts are unaffected.
- A phone that cannot unlock its transferred wallet data shows recovery
  options instead of an onboarding that always fails.
- New messages (English, Japanese; other locales show English):
  `account_repair_needs_removal` ("This account can't be repaired as it
  is"), `wallet_keys_unavailable` ("This phone can't unlock the saved wallet
  data").

**Verification**

- New device scenarios (`WalletUpgradeScenarioTest`, now 26): partial key
  set completed by its phrase; undecryptable account removed and imported
  again; keys cleared by an interrupted 3.8.6.3 removal (restored and
  removed); phone transfer without the Keystore key, with only leftover keys
  and with accounts (read-only, then set up again, then import and sign);
  2.4.0 layout written by the 2.4.0 MigrationManager (Room 58); 2.x owner
  removed in a later release, then restored; blank row with removal; clock
  moved back during an interrupted verification; busy Keystore during the
  unwrap. New historical writers: the 2.4.0 MigrationManager with its
  readers and the 3.8.6.3 account removal (copied from the release tags).
- `core_db` `WalletUpgradeBackupTest` (now 108 with
  `WalletIdentityMigration75Test`'s 18 = 126): background starts do not use
  the retry budget; pruning keeps valid snapshots behind damaged ones and
  never prunes an unverified wallet's copy; set up again renames every file
  with identical bytes and only in a new process; low-storage snapshot skip
  and back-off; read-only offered exactly when the route works; VERIFIED
  receipt clock skew; keys-only margin with a 40 MiB database; unreadable
  database directory is not "missing".
- `common` `EncryptionUtilKeySafetyTest` (now 9): unwrap retried within one
  call, persistent busy unwrap is transient and not remembered, wrong wrapped
  key is permanent and not retried, classification table, probe touches
  nothing on a new install and reports a missing alias without creating one.
- JVM: `MigrationManagerSafetyTest` (no rows with keys this phone cannot
  unlock, busy probe), `MultiaccountInteractorTest` (read-only account
  completed; unreadable values ask for removal), `CredentialsRepositoryTest`
  (keys unavailable at commit; no draft while writes are disabled).
- Results. SORA-Api36-16k: `common` 14/14, `core_db` 126/126,
  `WalletUpgradeScenarioTest` 26/26, encrypted-wallet upgrade phases 5/5 (run
  in order with process restarts; the phase class fails by design when run
  all at once). SORA-Api34: `common` 14/14, `core_db` 126/126,
  `WalletUpgradeScenarioTest` 26/26, MigrationManager production-path
  qualification 7/7. The Android 14 run found the read-only-connection
  problem in recovery mode described above; it passes after the fix.
- `testProductionDebugUnitTest`: 1,103 tests in 15 modules, 0 failures,
  9 skipped. `:app:assembleProductionDebug` passes. `:app:lintProductionDebug`:
  866 warnings, 1 hint, 180 baselined errors (baseline unchanged).

### Review fixes (a4, round 2)

Five more review findings, all fixed. As before, nothing deletes,
overwrites or moves key material automatically.

**"Set up this device again" keeps every usable key in place**
(`WalletRecoveryOptions`, `WalletUpgradeBackup`)

- Round 1 offered set-up-again in every recovery state and always moved the
  encrypted preferences (every account's keys, phrases and seeds) and the
  wrapped key aside, where no in-app path can read them. Restoring one
  wallet after a damaged database or an interrupted check could therefore
  strand the keys of every other wallet, for example one imported from a
  raw seed whose owner never wrote it down.
- New `WalletRecoveryOptions.setUpAgainScope`, recorded with the request
  and applied at the next start:
  - `DATABASE_ONLY` for database and verification problems (`MIGRATION`
    and `INTEGRITY` states, for example `DATABASE_CORRUPT` or an
    interrupted check). Only `app.db` and its sidecars move; the database
    holds no secrets. The encrypted preferences and `key_alias.xml` stay
    exactly where they are, so after the restart every key set is listed as
    a retained wallet. Importing its phrase re-uses it after proving it
    matches, and "Restore wallets found on this phone"
    (`adoptRetainedWallet`) restores the others.
  - `ALL_WALLET_FILES` only when the keys cannot be used here at all: the
    Keystore key is missing (`KEYSTORE_KEY_UNAVAILABLE`, for example after
    a phone-to-phone transfer) or the preference files cannot be read
    (`PREFERENCES_INVENTORY_INVALID`). The files are still only renamed.
  - `NONE` for transient and storage states (busy Keystore, I/O, low
    storage). They are retried automatically, or the storage options are
    shown. `requestSetUpAgain()` refuses them with
    `SET_UP_AGAIN_NOT_AVAILABLE`.
- `canRestoreFromPhrase`, `canRestoreFromDrive` and
  `restoreNeedsSetUpAgain` are now true exactly where set-up-again is
  offered.
- A request that is unreadable or was written by an earlier test build
  moves only the database. A waiting `ALL_WALLET_FILES` request is never
  narrowed.

**Removing a read-only account keeps its release-era keys**
(`UserRepositoryImpl`, `CredentialsRepositoryImpl`)

- Round 1 allowed removing a read-only account and, if `prefs_address_pure`
  named it, also removed the release-era (unsuffixed) key set and phrase.
  That set could be the only readable copy. For example, 2.4.0 copied it to
  address-scoped values that later stopped decrypting. Removal now deletes
  the unsuffixed set only when the target is verified and the set is proven
  to be its own (its keys decrypt and derive exactly that address, or it
  holds no secret at all). A read-only target, an unreadable set, or a set
  whose keys derive another address is kept. "Restore wallets found on this
  phone" can then adopt it. The confirm step recomputes the same rule.
- `unavailableWalletOptions()`:
  - `canShowPhraseAfterPin` now also counts a release-era phrase or seed
    whose keys derive the address.
  - `canRemoveAndImport` is offered only when removal loses no readable
    secret, or when that secret is a phrase or seed that the person is
    shown first.
  - New `showPhraseBeforeRemoval` and `hasReleaseEraCopy`.
- Showing the phrase (`retrieveMnemonic`, `retrieveStoredSeed`): when the
  account's own value cannot be decrypted and the release-era keys derive
  the address, the release-era value is returned (reading only). Otherwise
  the error is reported as before.
- The derived-seed cache in `retrieveSeed` is now add-only, so it can never
  replace a stored seed value, even an unreadable one.

**No read-only route while the wallet key cannot be used**
(`WalletRecoveryOptions`, `WalletUpgradeBackup`, `MainViewModel`,
`PinCodeViewModel`)

- After a phone-to-phone transfer, round 1 offered "Continue read-only".
  The main screen then decrypted the app PIN with the missing key and the
  app crashed every time. `canReadOnly` is now false for
  `KEYSTORE_KEY_UNAVAILABLE` and for a busy Keystore
  (`KEYSTORE_UNAVAILABLE`) until a retry succeeds.
  `authorizeSafeReadOnly()` returns `VAULT_ONLY` in those states, so the
  existing panel hides the button.
- Defence in depth:
  - `MainViewModel.showPinFragment` no longer crashes when the PIN cannot be
    read.
  - The PIN screen keeps asking for the stored PIN when the wallet key is
    busy or missing. It used to switch to "Set your PIN code", which would
    have replaced the stored PIN.

**Try again with no account opens onboarding** (`SplashViewModel`)

- A retry that verifies a phone with no account row (for example retained
  keys only, after a busy Keystore on the first start) used to open an
  empty main screen, which then failed. Retry now uses the same routing as
  a normal start.

**A blocked automatic retry can no longer crash every start**
(`WalletUpgradeBackup`, `MigrationManager`, `SplashInteractor`,
`SplashViewModel`)

- If the automatic retry of an interrupted check could not be authorized
  (its counter could not be saved on a full disk, or the journal changed),
  the splash waited for it. It then looked up the failure code through the
  database, which is closed while startup is blocked. The uncaught error
  crashed the app on every start, and no recovery screen was ever shown.
- Now:
  - `MigrationManager.failureCode()` returns the blocking code without
    opening the database and never throws.
  - `SplashInteractor` and `SplashViewModel` fall back to the code known in
    memory.
  - Any unexpected error while routing shows the recovery options.
  - A failed automatic attempt is remembered for the process, so
    `startupFailureToShow()` shows the options at once.

**User-visible effects**

- After a damaged database or an interrupted check, "Set up this device
  again" no longer hides the other wallets' keys. After the restart they
  can be restored from the phone, even those without a written phrase.
- A phone that received its data from another phone no longer crashes on
  "Continue read-only". That button is not shown there.
- Removing an account that cannot sign no longer removes older keys it
  shares with the phone. That account can be restored from the phone
  afterwards.
- No more crash loop on a nearly full phone after an interrupted upgrade.
  The recovery options appear.
- No new strings.

**Verification**

- JVM:
  - `SplashViewModelTest`:
    - retry with no account opens onboarding;
    - a failure-code lookup that throws still shows recovery;
    - an unexpected routing error shows recovery;
    - set-up-again scope per state and code;
    - read-only never offered while the key cannot be used;
    - restore offered exactly with set-up-again.
  - `SplashInteractorTest`: in-memory fallback.
  - `MigrationManagerSafetyTest`: the failure code while blocked does not
    open the database, and an unreadable code falls back to memory.
  - `CredentialsRepositoryTest`: release-era phrase fallback, the error is
    kept when no copy belongs to the wallet, and the seed cache is add-only.
  - `MainViewModelTest`: the PIN check failure does not crash.
  - `PinCodeViewModelTest`: an unusable key keeps asking for the stored PIN
    and never saves a new one.
- Device:
  - `core_db` `WalletUpgradeBackupTest`:
    - set-up-again with keys unavailable moves every file byte for byte;
    - set-up-again for a damaged database moves only the database and
      leaves the keys byte-identical and listed;
    - set-up-again is refused for transient, storage and no-recovery
      states;
    - an automatic retry whose counter cannot be saved shows the options
      at once, and Try again still works.
  - New `WalletUpgradeScenarioTest` scenarios (30 now):
    - Damaged database, then set-up-again. The 12-word wallet comes back
      through import, and the raw-seed wallet through "Restore wallets
      found on this phone"; both sign.
    - 2.4.0 account with an undecryptable scoped copy. The release-era
      phrase is shown; removal keeps the release-era keys; adoption
      restores it and it signs.
    - `prefs_address_pure` naming an account whose unsuffixed keys belong
      to another wallet. Removal keeps them, and that wallet is adopted and
      signs.
    - Interrupted check whose retry cannot be saved, with real gated Room.
      The failure code is read without opening the database, and the
      options appear; Try again verifies and signs.
  - Updated scenarios:
    - Phone transfer: read-only is not offered, `retrievePin` fails with
      `KeyMaterialUnavailableException`, and the route is `VAULT_ONLY`.
    - Busy Keystore: no read-only and no set-up-again.
    - Low storage: no set-up-again.
- Results:

  | Suite | SORA-Api36-16k | SORA-Api34 |
  |---|---|---|
  | `core_db` | 129/129 | 129/129 |
  | `WalletUpgradeScenarioTest` | 30/30 | 30/30 |
  | Ordered encrypted-wallet phases (process restart between phases) | 5/5 | not run |

  - `testProductionDebugUnitTest`: 1,117 tests in 15 modules, 0 failures,
    9 skipped.
  - `:app:assembleProductionDebug` passes.
  - `:app:lintProductionDebug`: 866 warnings, 1 hint and 180 baselined
    errors. The baseline is unchanged.

## a5: SORA Neon UI foundation (Android)

### Why

The owner asked for the "SORA Neon" look (design spec §2–§7: cyberpunk glow,
glassmorphism, neumorphism). Before any screen can move to it, the app needs
one set of tokens, effects and components that every screen can read. The
current theme also had real defects that hurt the 40,000 people upgrading:

- **Release blocker:** in dark mode the splash recovery panel drew `#2D2926`
  text on `#26262D` (1.04:1), and its buttons fell back to Material purple
  because the XML theme never set `colorPrimary`. In light mode the splash
  "loading" line was 1.04:1. People who reach this panel during an upgrade
  could not read it.
- Light theme: `fgOutline` equalled the page colour (1.0:1, invisible
  dividers and input borders); `fgSecondary` was 3.56:1 on white; the
  warning colour was 2.14:1 on white. Dark theme: every `*Container` colour
  was a light pastel on the purple canvas; `fgTertiary` was 3.12:1.
- The onboarding activity decoded a full-screen `bg_image` PNG (about
  19.5 MB in memory at xxxhdpi, 4.2 MB of PNGs in the APK) in light mode.
- The QR scanner ignored the app theme (`MaterialTheme {}` with defaults).
- Material controls and dialogs used Roboto and 4 dp corners.

### What changed

Nothing in this task reads, writes or moves wallet key material, accounts,
addresses or signing code. The stored theme choice (`KEY_DARK_THEME_ENABLED`,
`KEY_SYSTEM_DRIVEN_UI_ENABLED`) is never changed.

**Tokens** (`common/.../compose/theme/`)

- `tokens/NightThemeColors.kt`, `tokens/DayThemeColors.kt`: the ui-core
  colour slots now hold the SORA Neon palette (spec §2.3), including the
  `elevation` slot (`#0B0C14 @ 8%` in light, none in dark). Every screen
  that reads `MaterialTheme.customColors` recolours with no call-site change.
  `fgInverted` is the dark "ink" in dark mode, so legacy filled buttons get a
  5.64:1 label. `tokens/Colors.kt`, `FontFamilies.kt` and `FontWeights.kt`
  are gone (only the old palette used them).
- `neon/NeonTokens.kt`: `NeonTokens` (`NeonDark`, `NeonLight`) with every
  token that has no slot: aurora, glass fills and sheen, gradient hairlines,
  CTA gradients, status containers, glow specs, neumorphic shadows,
  skeleton colours. Read with `MaterialTheme.neon` (`LocalNeon`).
  `designBlur()` keeps the one blur conversion factor (0.87).
- `neon/Fx.kt`: effect tiers `FULL` / `REDUCED` / `MINIMAL` decided from API
  level, `isLowRamDevice`, total memory, power-save mode and the new
  "Reduce effects" setting; plus reduce motion (system animator scale) and
  high contrast (API 34+). `LocalFx` carries the policy. One process-wide
  power-save receiver refreshes it, and it is re-read on every resume. The
  tier rule is a pure function with unit tests.
- `tokens/TextStyles.kt`: the spec type scale (§5.3) with line heights and
  tracking, bundled Sora and Inter, SemiBold where the spec asks. Body text
  grows by 1 sp (15→16, 13→14, 11→12) to match iOS. New `NeonType` styles:
  `displayHero` (tabular figures), `eyebrow`, `amountM`/`amountS` (tabular),
  `mono` (platform monospace for addresses and codes), `phraseWord`,
  `pinDigit`. The ui-core bold slots are now filled too.
- `SoraTheme.kt`: `SoraAppTheme` provides `LocalNeon`, `LocalFx` and
  `LocalContentColor`, and gives Material controls the app type scale and
  16/24/28 dp corners. Disabled controls use 8% fill and 36% label instead
  of 4%/16%, so disabled labels stay visible on dark.

**Effects** (`common/.../compose/neon/`, no new dependencies, no RenderEffect)

- `NeonEffects.kt`: `Modifier.neonAurora` / `NeonBackground` (static
  radial washes plus a 4% grain generated once from a fixed seed, no image
  asset), `neonGrid` (hero surfaces only), `neonGlass` (fake glass: fill,
  sheen, gradient hairline; opaque on MINIMAL and high contrast; soft shadow
  in light), `neonBorder` (2 dp under high contrast), `neonChrome` (bar tint
  after scroll), `ChamferShape` (HeroCard cut), and
  `Window.applyNeonBlurBehind` (cross-window blur behind dialogs on API 31+
  FULL tier only, SDK-guarded; a plain dim otherwise).
- `NeonGlow.kt`: `NeonGlow` (cached `dropShadow` bitmap; only the layer
  alpha animates; a 1.5 dp ring on MINIMAL; nothing under high contrast),
  `rememberCtaPulse` (exactly 3 cycles, FULL tier only, never under reduce
  motion), motion tokens.
- `Neumorphic.kt`: `NeumorphicSurface` for keys and discs (outer and inner
  cached shadows; press = inset, 0.96 scale, cyan ring; flat face on
  MINIMAL).
- `Skeleton.kt`: one shimmer clock per screen and `Modifier.skeleton`
  (static under reduce motion and MINIMAL).

**Drop-in components** (`common/.../compose/neon/`)

- `GlassCard.kt`: `GlassCard`, plus `ContentCard` and `ContentCardEndless`
  with the ui-core parameter names and order. An explicit
  `backgroundColor` still renders opaque (white QR plates keep working).
  They remove ui-core's grey 10 dp shadow, which follows the system rather
  than the in-app dark mode.
- `NeonButton.kt`: `NeonButton` with Primary, Secondary (glass), Tertiary,
  Swap, Safe and Destructive variants, a disabled style, a 3-bar loading
  state with a "Loading" state description, and a focus ring. Shims with the
  ui-core signatures: `FilledButton`, `FilledPolkaswapButton`,
  `TonalButton`, `BleachedButton`, `OutlinedButton`, `TextButton`.
- Screens migrate later by swapping imports. This task migrates no call
  site except the ones below.
- `NeonGallery.kt`: a debug-menu parity screen (spec §11.3) that shows the
  tokens, glows, neumorphic key, PIN dots (4 and 6), glass card, HeroCard,
  buttons, skeletons and type styles in the current theme and tier.
  Reached from More → Debug Menu → "SORA Neon components" (not in Play
  builds, where the debug menu is hidden).

**XML theme** (`common/src/main/res/`)

- `values/neon_colors.xml` and `values-night/neon_colors.xml`: the XML
  mirrors of the palette. `values/styles.xml` now has one DayNight
  `AppTheme`; every colour that differs between themes comes from these
  files, and `values-night/styles.xml` is deleted. The theme sets
  `colorPrimary` / `colorOnPrimary` / `colorSurface` / `colorError` /
  control colours, text colours, and dialog themes (opaque sheet colour,
  24 dp corners, hairline, cyan actions) for AppCompat, framework and
  Material dialogs. Legacy attributes are remapped (spec §2.4); for example
  night `contentPrimary` is now `#F5F6FB` (16.93:1 on the panel).
- `AppTheme.Onboarding`: transparent bars without the translucent-bar
  scrim, so the aurora shows behind the status and navigation bars instead
  of a grey band.
- New `bg_neon_safe_panel` (SafeCard: opaque surface, mint-to-cyan
  hairline), `bg_neon_dialog`, `ic_neon_shield_safe`, and
  `Widget.Sora.Button.{Safe,Secondary,Tertiary}` for XML buttons.

**Splash and recovery panel** (`app/src/main/res/layout/activity_splash.xml`)

- The slow-start line is `text.secondary` on the canvas (was 1.04:1) with a
  thin cyan line instead of a spinner.
- The recovery panel is a SafeCard with a mint shield, readable title and
  body, a monospace selectable recovery code, a mint "Retry verification"
  button, glass secondary buttons and a cyan support link. It scrolls, so it
  works at large font sizes. Button ids, handlers and the `animation_view`
  id are unchanged.
- The panel message no longer mentions reinstalling or clearing app data
  (hard rule 3): "SORA paused the upgrade because it could not check every
  existing account yet. Nothing was deleted or replaced, and your funds stay
  safe on the blockchain. Try again, or contact support for help." The
  Akkadian and Middle Egyptian test locales carry the English text.
- The Safe Mode screens of spec §9 (Compose) replace this panel in a later
  task.

**Cold start in dark mode** (`DarkThemeManager`, `SoraApp`)

- The resolved night mode is mirrored into a tiny private preferences file
  (`sora_night_mode_mirror`) and applied in `SoraApp.attachBaseContext`, so
  a dark-theme user's splash and first screen open dark instead of flashing
  light. The first launch after the upgrade behaves as before (the mirror
  does not exist yet). The Android 12+ system starting window still follows
  the system theme (see the release checklist).

**Other**

- `SoraBaseActivity` (onboarding) and `ClaimFragment` draw `NeonBackground`
  in both themes; `bg_image.png` (6 densities, 4.2 MB) is deleted. The
  unused CPU blur `Blur.kt` is deleted.
- QR scanner: `SoraAppTheme` (always the dark palette, because its surface
  is the camera), a `#000 @ 55%` scrim outside the viewfinder, cyan corner
  brackets with a soft glow, the toolbar kept below the status bar, and a
  black (not white) placeholder before the camera starts.
- `OptionSwitch`: cyan track when on; a grey (`border.input`) thumb when
  off, so the off state is not white-on-white.
- Settings → App settings → Appearance: new "Reduce effects" switch with a
  short explanation. Stored under its own key `neon_reduce_effects`.

**Strings** (`common/src/main/res/values*/strings.xml`)

- New `appearance_reduce_effects` ("Reduce effects"),
  `appearance_reduce_effects_description` ("Turns off glow, glass and motion
  effects. Useful on older phones or to save battery.") and `common_loading`
  ("Loading"). Japanese is translated; the other locales carry the English
  text. No lint baseline change.

### User-visible effects

- Dark mode is the SORA Neon near-black canvas with calm cards and cyan,
  mint and red accents; light mode is the "Daylight" palette. Text is
  readable everywhere (see the contrast table below).
- The upgrade recovery panel is readable in both themes and says nothing
  about reinstalling.
- Dark-theme users no longer see a light splash after the first run.
- Onboarding shows a soft aurora behind the content in both themes.
- Body text is 1 sp larger; Material dialogs and controls use the app fonts
  and rounded corners.
- New "Reduce effects" switch.
- Most screens keep their current layout and components until the next UI
  task migrates them (cards, buttons, bottom navigation, PIN keypad).

### Contrast (WCAG 2.x, `scratchpad/tools/cc.py`)

174 token pairs from `neon_colors.xml` (both themes, on canvas, surface,
surface variant, sheet, glass, pressed glass and the navigation chrome)
pass: 4.5:1 for text, 3:1 for UI parts. Selected values:

| Pair | Dark | Light |
|---|---:|---:|
| text primary / secondary / tertiary on canvas | 18.73 / 10.16 / 7.36 | 17.60 / 7.92 / 5.42 |
| text tertiary on pressed glass | 5.98 | 5.14 |
| label on brand red (legacy primary button) | 5.64 | 5.89 |
| label on Safe CTA | 13.82 | 6.11 |
| CTA gradient labels (min of both ends) | 5.24 | 5.24 |
| input border on canvas / surface | 4.07 / 3.67 | 3.43 / 3.80 |
| recovery panel body text on panel | 9.18 | 8.77 |
| before: night recovery panel, light splash text | 1.04, 1.04 | |

The same pairs are enforced by unit tests (`NeonContrastTest`) and, on a
device, by `SplashRecoveryPanelThemeTest`, which inflates the real splash
layout in both themes and checks every rendered text colour.

### Verification

- `:app:assembleProductionDebug` and `:app:assembleProductionDebugAndroidTest`
  pass.
- `testProductionDebugUnitTest`: 1,129 tests in 15 modules, 0 failures,
  9 skipped (12 new: `NeonContrastTest`, `FxPolicyTest`).
- `SplashRecoveryPanelThemeTest` (new device test, `:app`): 2 of 2 pass on
  `SORA-Api36-16k`.
- `:app:lintProductionDebug`: no errors; 914 warnings, 1 hint and 180
  baselined errors. The baseline is unchanged. The new warnings are mostly
  `UsingMaterialAndMaterial3Libraries` on the Material 2 imports of the new
  files (the app also declares an unused Material 3 dependency).
- Emulator `SORA-Api36-16k` (API 36, 16 KB pages, productionDebug), light and
  dark: splash (system starting window and splash activity), welcome, wallet
  home, More, App settings, QR scanner, the recovery panel (rendered by the
  device test) and the debug parity screen. Screenshots:
  `scratchpad/screens/android/a5-ui-foundation/`. Before this task, the
  recovery panel left on that emulator by the a4 tests showed purple
  all-caps buttons (`before/launch.png`). The emulator reports the REDUCED
  tier (it has less than 3.5 GiB of memory).

### Not in this task (next UI task)

- Migrating screens to `GlassCard` / `NeonButton` (import swaps),
  `NeonBackground` in `SoraBaseFragment` with a transparent toolbar, the
  bottom navigation chrome and swap disc, the PIN keypad, sheets and
  blur-behind call sites, and the Compose Safe Mode screens of spec §9.
- "Dark only for new installs" (spec principle 9) is not implemented; the
  default stays as it was.

### Review fixes (a5, round 1)

Adversarial review found eleven issues (three duplicated); all were real and
all are fixed. No wallet keys, accounts, addresses, derivation or signing
code changed; the recovery panel calls the same `WalletUpgradeBackup`
functions with the same arguments.

**Recovery panel (splash, Safe Mode)**

- **Keep-installed callout (major).** a5 removed the old "before
  reinstalling or clearing app data" clause and left nothing about
  uninstalling. On Android the keys exist only on this device
  (`allowBackup=false`, no device transfer), so uninstalling or clearing
  storage without a phrase backup loses them. The panel now shows the spec
  §9.1-3 info callout: "Please don't uninstall SORA or clear its storage.
  Your keys are stored on this device." It uses `infoContainer`, a cyan
  shield and `text.primary`; no caps, bold or warning colour.
- **Disabled and running states (major).** `Widget.Sora.Button.Safe`,
  `.Secondary` and `.Tertiary` used single colours, so a disabled button
  looked enabled while Retry, Export or Continue read-only ran on IO.
  They now use colour state lists (`common/src/main/res/color/
  neon_button_*.xml`): disabled = `cta.disabled` fill and `text.disabled`
  label (Secondary loses its border). While an action runs, its button
  keeps its colours and reads "Working…" (height locked so a two-line
  label does not jump), the other actions are disabled, a thin cyan
  progress line shows, and after 10 s "Still working… this can take up to
  a minute on some devices." appears (spec §7.1, §9.1-5). Only one action
  runs at a time. Contact support and Technical details stay available.
- **Technical details.** The recovery code left the body (spec §7.17,
  §9.1-4) for a "Show technical details" toggle. The support email now
  carries "Recovery code: …" in its body, so support still gets it. If no
  email app is set up, a toast shows the support address; before, the
  button would have crashed the recovery screen.
- **Japanese (minor).** The app module had no `values-ja`, so the panel
  was English for Japanese users. `app/src/main/res/values-ja/strings.xml`
  translates every panel string. Other production locales still fall back
  to English (see the release checklist).

New strings (app module): `wallet_upgrade_recovery_keep_installed`,
`_working`, `_still_working`, `_details_show`, `_details_hide`,
`_support_no_email`. Japanese is translated; the akk/egy test locales
carry the English text. The lint baseline is unchanged.

**Toast (major).** a5 mapped `balanceColorDefault` (the toast's text
appearance colour) to the theme-aware `text.primary`, but the toast card
(`?attr/flatAboveBackground`) stayed `#FAF6F6` in both themes. The
"Transaction submitted" toast after a send drew `#F5F6FB` on `#FAF6F6`
(1.01:1) in dark mode. The card is now `bg.toast` (`#222233` dark,
`#14141F` light, spec §7.13) with its own text colour (`neon_on_toast`,
14.47:1 / 18.27:1), radius 16 and a 1 dp hairline. `flatAboveBackground`
has no other user. I also checked the other attributes that stayed fixed
in the one DayNight theme against the XML layouts still in use; this was
the only fixed background paired with a now theme-aware text colour.

**QR scanner (minor).** The scanner is always dark, but its window used
AppTheme's light-mode bar flags: dark status-bar icons on the dark scrim
on Android 15+ (about 1.3–1.5:1), and a light status-bar strip above the
scanner before Android 15. `QRCodeScannerActivity` now calls
`enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), …)` on every API
level (light icons, transparent bars); the "Show my QR code" button keeps
clear of the navigation bar.

**Compose foundation (minor)**

- `NeonButton` and its ui-core drop-ins: the click now covers a
  `minimumInteractiveComponentSize()` box, so Small (40) and ExtraSmall
  (32) buttons keep the 48 dp touch target and layout of the ui-core
  Material buttons (spec §6, §10.1).
- Text fields: ui-core `InputText` draws its unfocused border with
  `fgOutline`, the divider colour (1.24:1 dark, 1.37:1 light). New
  `compose.neon.InputText` has the same signature and gives the field
  `border.input` (3.67:1 dark, 3.80:1 light on the surface). ui-core keeps
  its colour local private, so the wrapper repeats the current ui-core
  theme with only that slot changed. The nine `InputText` screens (restore
  with phrase, create/import/backup passwords, account name, JSON backup,
  node details, referrer, contacts) swap the import. The slippage field's
  own border uses `border.input`. Dividers keep `fgOutline`.
- High contrast (API 34+ system contrast): `text.tertiary` maps to
  `text.secondary` and hairline alphas double, both in the ui-core slots
  and in `MaterialTheme.neon` (spec §4.0). `FxPolicy` already made glass
  opaque, turned glows off and doubled border widths.

**Contrast (cc.py, 27 new pairs, all pass)**

| Pair | Dark | Light |
|---|---:|---:|
| callout text on `infoContainer` | 15.41 | 17.64 |
| callout shield (cyan) on `infoContainer` | 10.99 | 5.16 |
| toast text on `bg.toast` (before: 1.01 dark) | 14.47 | 18.27 |
| running Safe / Secondary label ("Working…") | 13.82 / 15.61 | 6.11 / 17.60 |
| "Still working" line on the panel | 9.18 | 8.77 |
| field border on surface (before: 1.24 / 1.37) | 3.67 | 3.80 |
| tertiary text under high contrast on surface | 9.18 | 8.77 |
| scanner status icons on the scrim over a mid-grey scene (before: 1.45) | 11.37 | 11.37 |

**Verification**

- `:app:assembleProductionDebug` and `:app:assembleProductionDebugAndroidTest`
  pass.
- `testProductionDebugUnitTest`: 1,133 tests in 15 modules, 0 failures,
  9 skipped. `NeonContrastTest` grew from 7 to 11 tests (high contrast,
  toast, callout, button state lists).
- `:app:lintProductionDebug`: no errors; 916 warnings, 1 hint, 180
  baselined errors. The baseline is unchanged.
- Device tests on `SORA-Api36-16k` (API 36) and `SORA-Api34` (API 34),
  5 of 5 pass on each: `SplashRecoveryPanelThemeTest` (now also the
  callout, the running label and the disabled colours), the new
  `NeonToastThemeTest` and the new `QrScannerSystemBarsTest`, which
  launches the real scanner in the light theme and checks the bar
  appearance.
- Screenshots: `scratchpad/screens/android/a5-ui-foundation/fix1/`
  (recovery panel at rest and while working, light and dark; toast light
  and dark; scanner in the light theme on API 36 and 34). The API 34 AVD
  is an automated-test image without a system UI, so its scanner
  screenshot has no status bar; the bar appearance assertion still runs.

## a6: Safe Mode recovery and upgrade screens (Android)

### Why

People upgrading from any earlier version must see at most one calm screen,
with one obvious action and at least one safe way forward (design spec §9,
fix map §4 and F11). Before this task the splash showed an XML panel with
Try again, export and (sometimes) read-only; restoring, showing the phrase,
freeing space and support had no screen, the PIN screen could wait for the
network forever with a dialog, read-only mode had no visible sign, and the
"node offline" and 4-digit PIN prompts were plain alerts.

### What changed

Rules kept everywhere: no key material, wrapped key, Keystore alias or
account row is deleted, overwritten, re-encrypted or moved by any of this.
Every action calls the existing `WalletUpgradeBackup` /
`UserRepository` functions from the key-safety task (a4). Nothing tells the
person to reinstall or clear data. No new dependencies (the Compose UI test
library, already in the catalog, is used by tests only).

**Safe Mode kit** (`common/.../compose/neon/safemode/`, design spec 7.16,
7.17, 7.18, 9.3, 9.4): shield emblem (glow.safe, breathing at most five
cycles while work runs, FULL tier only), opaque SafeCard and SafetyPanel,
reassurance callout, StepList (done / running / waiting / needs action; a
tick only after the real check finished), Technical details disclosure with
a Copy button per item, opaque bottom sheet, the read-only chip, banner and
reason, the 6-digit PIN panel, the upgrade-complete sheet, the
device-transfer notice, the pending-check card and the veiled phrase grid
(always LTR, words not composed until tapped).
`WalletRecoveryCapabilityGate.modeFlow` lets screens show read-only mode;
signing still goes through `requireUserMutationAllowed()`.

**Splash** (`SplashActivity`, new `splash/presentation/recovery/`): the XML
`walletRecoveryPanel` is replaced by the Compose view `walletRecoveryCompose`
(`animation_view` and the loading line keep their ids).

- U1 / U5 upgrade progress: shown when the wallet check is still running
  after 1.5 s (U0 stays silent). Rows map to real phases reported by the new
  `MigrationManager.progress`: checking wallet data (the startup checks),
  updating wallet storage (opening Room, where schema steps run), checking
  accounts (n of m) and finishing up. Title "Finishing your update" when the
  start resumes an interrupted check. After 10 s "Still working…"; after 60 s
  Keep waiting and Get help. Back is disabled. The last row is ticked before
  the next screen opens.
- U4 "Free up some space": the MB needed (from the backup space check), a
  StepList row "Free space needed" with the MB still missing, Check again
  (also automatically when the person comes back from Settings), Open storage
  settings, Show my recovery phrase (when available), Get help. Never "clear
  data", and no export (it needs space too).
- U6 "We paused to keep your wallet safe", in the spec order, each action
  only when its a4 flag says it works: Try again; Restore with recovery
  phrase and Restore from Google Drive (both run "Set up this device again",
  which renames the old files aside, then restart the app into the restore
  screen; Drive only when Google services are configured); Show my recovery
  phrase; Continue in read-only mode (explainer sheet); Export recovery file
  (sheet listing what is and is never included; it is labelled as a file for
  support, not a backup); Contact support (sheet with the details, email
  that falls back to showing the address instead of crashing, Copy details);
  Technical details (code, app version, time, Android version). Variants:
  busy storage (TRANSIENT: retried automatically 3 times, after 1, 3 and
  10 s), "This phone can't unlock your wallet" (KEYS_UNAVAILABLE) and an
  interrupted removal the person started (WALLET_DELETION_*).
- Show my recovery phrase: the app PIN (or biometrics when enabled; the
  phone's screen lock when no app PIN exists), a wallet list from the
  preference key names and the account names read at startup (new
  `RecoveryFacts.phraseWalletIds`, `accountNames`, `legacyOwner`), then the
  veiled phrase or raw seed. It reads through `CredentialsRepository`
  (`retrieveMnemonic`, `retrieveStoredSeed`; never the seed cache) and works
  without Room. A release-era phrase that no address owns is listed as
  "Phrase saved by an older SORA version". FLAG_SECURE (and no recents
  screenshot on API 33+) while it shows; the phrase is dropped when the app
  goes to the background or after the flow closes; the veil returns after
  60 s. A phrase is read only after confirmation in that flow. Wrong PINs
  lock like the PIN screen (3 tries: 1 minute, then 5, 15, 30); the counter
  lives in `no_backup/safe-mode-pin-attempts`, never in the wallet
  preferences.
- F11: Continue read-only and a successful retry call
  `NodeManager.ensureStarted()` before the main screen opens (the lifecycle
  hook from a3 stays as a second path).
- "Restart and restore" uses the app's existing restart helper
  (`restartApplication`, the same as after removing every account).

**PIN screen** (`feature_main_impl/.../pincode/`):

- U10: after unlocking with a 4-digit PIN, "Choose a new 6-digit PIN"
  (SafetyPanel) replaces the old alert, then the 6-dot setup.
- U1 / U2: the post-unlock account check (an older SORA v1 balance to claim)
  replaces the progress dialog and the "node offline" alert with the
  progress screen (silent for 1.5 s). Only an unchecked 15-word legacy
  phrase needs the network for that check; every other account is checked
  on the phone, so being offline no longer keeps people at the PIN screen.
  Offline it shows "Waiting for network" with Change node and continues by
  itself once connected (before, only after leaving and returning). A
  failing check is logged and skipped (it runs again at the next unlock)
  instead of a progress dialog that never closed.

**Main screens**:

- U9 read-only mode (any gate mode but NORMAL): the wallet home shows the
  read-only chip and banner ("You can view balances and history. Sending is
  off until the upgrade finishes.") with Try upgrade again (opens the
  launcher, which runs Try again with the progress screen) and Learn more.
  Send and Swap on the asset screen, Review on the send form and the swap
  button are disabled with "Sending is off in read-only mode".
- U11 "Welcome to the new SORA Wallet": once per updated install
  (first install time differs from last update time), only after the PIN
  and never in read-only mode. Ticks for the account on this device, the PIN
  unlock and loaded balances; "Back up your recovery phrase" with Back up now
  / Later while the backup card is open. Opening onboarding (a wallet set up
  on this version) marks it done. Kept in its own small preferences file.
- U12: the backup card and Login & Security say the wallet won't move to a
  new phone automatically (true: `deny_data_extraction.xml`,
  `allowBackup=false`).
- U13: a pending-check state (`walletIssue == RECOVERY`) is an info card
  with the cyan "Wallet and backup options" link instead of red error text.
- U8 / restore: Login & Security lists "Wallets found on this phone" (key
  sets no account uses, `WalletUpgradeBackup.quarantine().retainedWalletIds`)
  with "Restore this wallet", which calls `UserRepository.adoptRetainedWallet`
  (proves the keys derive the address first; writes no key material). This
  is how a wallet without a phrase (raw seed) comes back after "Restore with
  recovery phrase" set the database aside.

**Copy**: every assurance in the new strings states only what is true for
that state; a "verified:" note next to the string key names the code that
makes it true. Titles never say error, failed, lost or deleted. No red, no
pulse, no caps.

**Strings**: new `safe_mode_*` (common) and `recovery_*` (app) strings in
English and Japanese. Other production locales carry the English text
(release checklist). Removed strings that became unused (old splash panel,
4-digit PIN alert, node-offline alert, red pending-check text). No lint
baseline change.

**Simplification**: deleted `PincodeNodeConnection.kt`, the XML recovery
panel, `bg_neon_safe_panel` and `bg_neon_info_callout`.

### User-visible effects

- Upgrades that take longer than 1.5 s show real progress instead of a
  frozen animation; nothing changes for fast upgrades.
- Every recovery state shows calm, specific text and at least one way
  forward besides Try again; the recovery phrase can be shown from there.
- Low storage shows how much space is needed and checks again on return.
- Read-only mode is visible on the home screen, and money actions say why
  they are off.
- Offline unlock works for every account except an unchecked 15-word v1
  phrase, which waits with a Change node option.
- People upgrading see a one-time summary after unlocking.
- Wallets left on the phone (for example by an unfinished account setup)
  can be restored from Login & Security.

### Contrast (`scratchpad/tools/cc.py`, 28 new pairs, all pass)

| Pair | Dark | Light |
|---|---:|---:|
| panel body (secondary) on SafeCard | 9.18 | 8.77 |
| tertiary action label (link) on SafeCard | 12.07 | 5.70 |
| callout text on `infoContainer` | 15.41 | 17.64 |
| read-only chip border (steel) on `infoContainer` | 9.39 | 5.50 |
| step waiting ring (`borderInput`) on surface | 3.67 | 3.80 |
| step needs-action dot (`warning`) on surface (the `warningFill` amber would be 1.6 in light) | 10.40 | 6.56 |
| phrase index (tertiary) on opaque cell | 6.65 | 6.01 |
| empty PIN dot ring on canvas | 4.07 | 3.43 |

### Verification

- Unit (JVM): `RecoveryPanelModelTest` (every code × fact combination has a
  forward action besides Try again and Contact support; flags, order, MB
  rounding), `SafeModeVaultTest` (reads only, no seed cache, release-era
  fallback, PIN attempts ladder in `no_backup`), `SafeModeViewModelTest` (no
  phrase is read before the PIN; lock after 3 wrong PINs survives closing;
  closing forgets the confirmation; progress rows follow real phases),
  `ProgressPromptTest` (10 / 60 s rule; read-only display equals the signing
  gate in every mode), `LoginSecurityViewModelTest` (only unused key sets,
  adopted through the repository, nothing offered in read-only).
- Device (Compose UI tests), on SORA-Api36-16k and SORA-Api34:
  `SafeModeScreensTest` 6/6 (every blocking state shows an enabled forward
  action in both themes and each action reaches its handler; only Contact
  support stays enabled while an action runs; phrase needs confirmation and
  is not composed before the veil is tapped; 10 / 60 s rules; Change node;
  the storage screen never offers clearing data or reinstalling) and
  `ReadOnlySendTest` 2/2 (`feature_assets_impl`: Send, Swap and Review
  disabled with the reason, following the gate).
- Real app on SORA-Api36-16k (debug build): a damaged `app.db` shows U6 with
  DATABASE_CORRUPT under Technical details; Try again keeps the panel;
  Export opens the share sheet and reports the export; "Restore with
  recovery phrase" restarts into the welcome screen with the damaged
  database renamed to `no_backup/wallet-quarantine-*-set-up-again/` byte
  for byte. With an imported test wallet: Show my recovery phrase asks for
  the PIN, refuses a wrong one, shows the veiled grid with the window
  marked SECURE (screenshots come out black), and drops the phrase when the
  app goes to the background. After the restore, the earlier wallet is
  listed under Login & Security and "Restore this wallet" brings it back
  with the same address. The upgrade-complete sheet appears once after the
  PIN and not again.
- Screenshots of every state in dark and light (debug-only
  `SafeModePreviewActivity`, `adb shell am start -n
  jp.co.soramitsu.sora/.debug.SafeModePreviewActivity --es state <STATE>
  --ez dark <true|false>`), plus the real-app run:
  `scratchpad/screens/android/a6-safe-mode/` and `.../real/`.
- `:app:assembleProductionDebug`, `testProductionDebugUnitTest` and
  `:app:lintProductionDebug` (no errors; the baseline is unchanged; the new
  warnings are the known `UsingMaterialAndMaterial3Libraries` on Material 2
  imports) pass. Numbers in the final summary below.
- Known, not caused by this task: `WalletUxAcceptanceTest` (app device
  tests) fails on API 34 because it reflects on `OnboardingViewModel
  .tempAccount`, removed in a4, and on a send-field accessibility step; on
  the 16 KB emulator mockk-android cannot load. Follow-up task suggested.

### Not in this task

- A per-account read-only label in the account list and on the home for an
  account whose keys are unavailable (a4 follow-up): the signer still
  refuses it, but the screen does not explain why yet.
- iOS-only states (U3, U7).
