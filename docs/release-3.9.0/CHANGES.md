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

- The passphrase-length errors name every accepted length:
  `common_error_mnemonic_word_count` ("A passphrase has 12, 15, 18, 21 or
  24 words.") and `common_error_mnemonic_word_count_invalid` replace
  `common_error_mnemonic_length_error` / `common_error_mnemonic_is_not_valid`.
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
| 2.4–3.3 (Room 58–72) | Same layout as 3.x; the Room chain from 58 is covered by the existing `core_db` migration tests (schemas 58–77). |
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
