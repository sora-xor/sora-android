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
