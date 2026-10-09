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
