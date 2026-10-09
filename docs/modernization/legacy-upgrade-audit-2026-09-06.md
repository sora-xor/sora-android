# Legacy upgrade audit — 2026-09-06

The audit found two compatibility regressions in the new wallet model: direct upgrades from the pre-multiaccount releases were rejected despite intact encrypted keys, and retained 18/21-word BIP39 accounts were rejected even though the released importer accepted them.

## Release source baselines

| Release tag | Commit | Room version | Wallet storage |
| --- | --- | --- | --- |
| `release/sora/2.3.2` | `0011066db0865bd1f43dcad6c4d09c40499651d0` | 50 | Unsuffixed encrypted preferences; no `accounts` table |
| `release/sora/2.3.3` | `2dc0a751025aef8a9f852a791bdaa2275fd3848b` | 50 | Unsuffixed encrypted preferences; no `accounts` table |
| `release/sora/2.4.0` | `2bd8848d97a1207333d8c5cf5127194856c5009a` | 58 | Multiaccount table plus the single-account promotion path |
| `release/sora/3.8.6.3` | `c79ad6c7c64ad92e756023ff51d9e2a7040da375` | 73 | Old schemas 15–57 destructively rebuilt; `MigrationManager` then promoted unsuffixed credentials |

These are source inspections of retained Git tags, not release-produced device snapshots.

## Changes

- Schemas 15–57 now enter the additive Room chain at 58. Every old cache table, row and explicitly named index stays under `legacy_v<version>_<table>`; any existing account rows are copied intact. The original database/settings snapshot must be backed up first. No destructive database migration is enabled.
- Backup admission recognizes a registered single-account predecessor only when all three unsuffixed encrypted key fields exist, selection and scoped credential records are absent, and no activated wallet/migration/deletion namespace conflicts. Missing or mismatched key material is preserved for protected recovery; this does not establish that missing keys can be reconstructed or that signing remains available in that state.
- Repository startup derives the account address from the retained public key and proves the retained signing key. When mnemonic/seed material exists, its signing-key parity is checked too. Only account metadata and missing wallet cards are added. The original key, nonce, mnemonic and seed ciphertexts are not replaced or removed.
- Owner, selection and the old Iroha migration flags are published in one DataStore edit. An interruption after the account insert retries the same proven account without `REPLACE`; missing cards are inserted without overriding existing choices. An interrupted schema migration rolls back via Room/SQLite; an interruption after the completed schema bridge is admitted for account promotion on restart.
- Retained 12/15/18/21/24-word phrases can verify/export their original SORA2 seed. Only 12/24-word phrases create Nexus children. Legacy 15/18/21-word wallets remain `MNEMONIC_UNSUPPORTED` and SORA2-only, without saving a newly derived seed during reads.
- An exception during speculative application-scope account initialization no longer crashes the application before Splash can present recovery. Cancellation still propagates.

Activation uses local database/preferences, local address encoding and local cryptography. Node availability and cloud services are not prerequisites for these migration operations.

## Executed local checks

The initial compatibility implementation passed:

- 85 JVM tests: `UserRepositoryTest` (40), `CredentialsRepositoryTest` (18), `LegacySingleAccountUpgradeTest` (3), `MigrationManagerSafetyTest` (24).
- 111 Android instrumentation tests on Android 14 / API 34 arm64 emulator: full `WalletUpgradeBackupTest` and `WalletIdentityMigration75Test`. This includes existing Room 58–76 single/multiaccount and WAL/DELETE cohorts, the new Room 50 → 77 archive fixture (including a synthetic future-index-name collision), encrypted-preference byte preservation, missing nonce rejection, and restart after the schema bridge.
- One real DataStore instrumentation test: metadata publication retains every ciphertext/flag, is idempotent, and rejects another owner without changing selection.
- All seven native production-path instrumentation cohorts in the isolated qualification package: 12-word, 24-word, retained 15/18/21-word, raw seed, legacy keypair, explicit watch-only, and two accounts with exact selection. The retained phrase cohort uses three distinct valid phrases and confirms native signing plus SORA2-only activation for each.
- Migration qualification v7/v2 template/negative mutation checks and template lint.

The Room 50 device fixture recreates the released `TokenLocal` columns and unsuffixed preference layout; it is not a complete database captured from a released APK. The new repository promotion JVM tests isolate cryptography at the existing signing boundary. The separately executed production-path cohorts use native signing and synthetic credentials in the isolated qualification package; they do not establish production package Keystore continuity or replace a retained-release device receipt.

The production source verifier was updated to require all retained BIP39 lengths and their regressions, and to bind the new source/test files into its qualification digest. Its initial broad audit also flagged dependency, signing, wallet runtime/UI and deployment qualification requirements. The wallet/runtime and variant flags were individually investigated below; they were not dismissed as unrelated. No production admission or signed retained-device receipt was created.

## Authenticated coverage contract follow-up

A subsequent read-only artifact audit found that the former v7 receipt/v2 manifest required
source schemas exactly 58–76. It could not admit released Room 50 snapshots, so the new runtime
bridge alone did not close the authenticated historical-coverage requirement.

The receipt is now v8, the evidence manifest v3, the raw index/collector evidence v2, and the
controller envelope/extraction v2. Snapshot, encrypted-storage, and device-execution aggregate
artifacts are v2; their exact `preAccountUpgrade` object must match the authenticated receipt.
The source schema inventory is exactly 50 plus 58–76, totaling 78 retained schema cohorts
(40 single-account, 38 multi-account). The Room 50 source must come from retained release
2.3.2 or 2.3.3, with provenance, original registered unsuffixed credentials, exact owner/selection,
flags, signing and cache/index parity verified. It also requires six interruption/retry phases
in both DELETE and WAL modes, totaling twelve restart cohorts. Other runtime-supported cache
versions do not become invented released-snapshot requirements.

The protected raw producer must now provide the seventh `legacy-pre-account-upgrade` suite,
with its method inventory/report/transcript, one retained Room 50 provenance category and all
twelve phase/journal categories. Missing any required category rejects collection. Collection
is still non-authorizing, and independent authorities must sign the exact receipt/manifest and
review the indexed bytes. Prior v7/v2 receipts, v1 raw inventories and v1 controller envelopes
are rejected. The source digest binds the new coverage module, versioned validators, collectors,
tests, workflow and producer instructions.

The workflow now requests `/v2/android/migration-qualification/evidence-envelope`, with
`X-SORA-Contract: sora-android-migration-controller-v2` and
`Accept: application/vnd.sora.android-migration-envelope-v2+json`. The external producer must
implement this exact handoff before qualification can proceed.

Scoped checks passed without changing wallet runtime code or repeating the 204 runtime tests:

- `node scripts/test-android-migration-qualification-v8-contract.mjs`: exact templates and a
  fully authenticated synthetic positive fixture, 6 template and 34 authenticated-path rejection
  cases. Ephemeral fixture-only keys are never release authority or repository evidence.
- `node scripts/test-android-migration-raw-evidence-v2.mjs`: positive collection and 24 rejection cases.
- `node scripts/test-android-migration-controller-envelope-v2.mjs`: positive extraction and 17 rejection cases.
- `node scripts/test-android-qualified-candidate-package-v1.mjs`: positive seal/download and 30 rejection cases.
- Template lint, JavaScript syntax, and scoped whitespace checks passed. At that point the source
  audit reported no migration/controller/candidate failures, but had 11 other failures and 33
  release blockers. Seven of those failures required the subsequent continuity triage below.

Saved local logs: `/tmp/sora-android-migration-v8-contract.log`,
`/tmp/sora-android-migration-raw-v2.log`, `/tmp/sora-android-migration-envelope-v2.log`,
`/tmp/sora-android-migration-v8-package.log`, and `/tmp/sora-android-migration-v8-audit.json`.
These are local test/audit outputs, not a signed retained-device qualification receipt.

## Startup recovery and source predicate follow-up

Inspection of all seven wallet/variant audit flags found one additional runtime availability
defect. `PendingRecoveryStartupScheduler` launched both WorkManager scheduling calls sequentially
in the application scope without exception isolation. A Nexus initialization/enqueue exception
could escape as an uncaught coroutine failure and suppress SORA2 scheduling. Each call now catches
non-cancellation failures independently, retaining the durable journal for the next startup/kick.
Cancellation still cancels the operation; it is not reported as a scheduling failure. Diagnostics
contain only the fixed network outcome code, emitted to Android Logcat under `WalletRecoveryStartup`
so release builds do not depend on a debug Timber tree or a cloud logging provider.

The exact predicate triage and replacements are:

- `NEXUS_DURABLE_STATUS_ONLY_RECOVERY_MISSING` and `SORA2_GENERIC_AMBIGUITY_RECOVERY_MISSING`:
  the old checks looked for calls in `SoraApp`. Actual startup is deliberately after successful
  migration in `SplashViewModel`, including successful migration retry. Those calls and the
  independent scheduler are now checked and hashed. All existing status-only, exact-hash,
  bounded-work, retry/backoff and no-submit requirements remain. A separate startup-failure
  isolation predicate requires the cancellation/error handling and regression inventory.
- `NEXUS_PENDING_OVERLAY_SCOPE_GUARD_MISSING`: UI moved from `CardsHubFragment` to
  `NexusWalletScreens`. Foreign-asset or invalid journal rows still cannot project into XOR.
  The current UI renders a recovery action through `WalletErrorMessage` instead of presenting
  their amounts as XOR. The gate now follows that component and its `walletIssue` recovery
  mapping, preserving every repository/validator scope and recovery-read check.
- `UNIFIED_NEXUS_NETWORK_SCOPE_OR_ACCOUNT_SWITCH_CLEAR_MISSING`: the current selected sheet is
  scoped by wallet/network/address and cleared when its exact row disappears. Receive/copy/send
  actions revalidate current identity. The current activity sheet displays all confirmed transfers
  inside the scrolling `WalletSheet`; expanded rows show counterparty/hash, with timestamps always
  visible. The gate follows these actual paths rather than the removed preview/full-history modal.
  All existing repository/ViewModel identity and account-switch test requirements remain.
- `UNIFIED_SORA2_PORTFOLIO_ROW_MISSING`: the relocated component calls
  `NetworkSummary("SORA2", false, balance.xorQuantity, ...)`, with the mainnet badge and guarded
  receive/open callbacks. The fee-asset, non-favorite XOR, stale-balance and account-switch
  requirements remain.
- `POLKAMARKT_RUNTIME_AUTHORITATIVE_PRESENTATION_MISSING`: two English strings changed. The gate
  now requires the actual batch-review callback, authoritative share/claimable data and per-market
  review action as well as the current unavailable/unreviewed presentation. No indexed balance is
  promoted to an authoritative balance and existing trader/confirmation checks remain.
- `PRODUCTION_ANDROID_VARIANT_IDENTITY_CHANGED`: the two new local measurement build types
  increased the global `signingConfig` count from 3 to 5 and `initWith` from 1 to 3. The gate now
  explicitly requires unique `benchmarkRelease` and `nonMinifiedRelease` blocks that inherit
  release and override signing to `cidebug`, while retaining exact global counts and production
  namespace/flavor/release-signing restrictions. `productionRelease` still requires its distinct
  configured production signer. This is not evidence that any existing local test-signed APK can
  upgrade the installed production application.

Retained 2.3.2 and 3.8.6.3 source and the current implementation agree on application ID
`jp.co.soramitsu.sora`, database `app.db`, DataStore `sora_prefs_datastore`, legacy preference
file `sora_prefs`, Android Keystore alias `key_alias`, and wrapped-key field `secret_key`.
3.8.6.3 used CI environment inputs through a configuration named `cidebug`; the current release
configuration removes its test-keystore fallback. The recorded expected production certificate
SHA-256 remains `b35dfe16cb3226da4432607288c6287362c5e623532c428f933d552297e9e3e0`.
No production signing key was read and no signed-candidate/retained-install continuity was inferred
from matching source names.

Additional focused checks (separate from the earlier 204 runtime tests):

- `:app:testDevelopDebugUnitTest --tests jp.co.soramitsu.sora.splash.domain.PendingRecoveryStartupSchedulerTest --tests jp.co.soramitsu.sora.splash.presentation.SplashViewModelTest`:
  7 scheduler tests and 9 Splash tests. Cases cover both independent failures, both failing
  together, symmetric cancellation, non-sensitive diagnostics, and successful retry.
- `node scripts/test-android-wallet-continuity-source-contract.mjs`: all 8 actual current
  verifier predicates and 41 in-memory rejecting mutations. These cover removal of network
  scheduling, migration ordering, exception/cancellation guards, log sanitization, scoped identity,
  recovery routing, full-history accessibility, authoritative balances, and production
  ID/signing/build-type drift. The release workflow runs this test and the qualification source
  digest binds it and all relocated implementation/test/component sources.
- The final source audit has exactly four dependency qualification failures:
  `GRADLE_DEPENDENCY_LOCK_CONFIGURATION_INVENTORY_INVALID`,
  `GRADLE_DEPENDENCY_LOCK_INVALID:app/gradle.lockfile`,
  `GRADLE_DEPENDENCY_LOCK_INVENTORY_SCHEMA_INVALID`, and
  `GRADLE_VERIFICATION_METADATA_INCOMPLETE`. Its 33 release blockers remain explicit; the audit
  does not claim production admission.

Evidence: `/tmp/sora-android-pending-startup-regression.log`, app JUnit XML under
`app/build/test-results/testDevelopDebugUnitTest/`,
`/tmp/sora-android-wallet-continuity-source-contract.log`, and
`/tmp/sora-android-wallet-continuity-audit.json`.

## Actual encrypted DataStore/Tink upgrade interoperability

The remaining dependency flags were investigated before being categorized as review blockers.
They include local inventory drift: the baseline profile plugin adds an empty production
configuration (271 → 272 overall), and the new Android test module is absent from the frozen
31-lock inventory. They also bind actual runtime dependency changes: encrypted DataStore adapters
1.0.0-beta01 → 1.1.1-beta03 and Tink 1.8.0 → 1.13.0. Verification metadata adds 160 artifacts
across 88 new coordinates while retaining all existing artifact entries. The current XML is
structurally valid and matches its own sidecar, but the frozen aggregate hashes remain different.
Simply updating counts/hashes would obscure the unreviewed dependency set; no approval or frozen
pin was changed.

The prior `EncryptedPreferencesCompatibilityTest` creates a synthetic old protobuf representation
and encrypts it with the current library graph. That is useful but does not exercise an old-library
writer. A separate rerunnable harness now exists under
`scripts/qualification/legacy-encrypted-preferences/`, outside all production Gradle modules.
It builds three small APKs with a fresh test package and freshly generated test-only signer.
No existing application, wallet, production key or production store is used.

The final Android 14 / API 34 arm64 run passed four phases:

1. Actual adapter 1.0.0-beta01, DataStore 1.0.0 and Tink 1.8.0 wrote and reopened seven real
   typed preferences, the encrypted file, Android Keystore master key and encrypted keyset.
2. After `adb install -r`, adapter 1.1.1-beta03, DataStore 1.1.7 and Tink 1.13.0 read the exact
   retained values without rewriting ciphertext, updated all seven types and reopened them.
3. A separate current-reader process reopened the updated store without rewriting it.
4. An old-library APK with a higher test version code reopened the updated store, establishing
   rollback-format interoperability for those types.

All phases retained UID 10123 and signer SHA-256
`685dde65bcdaecc1f250e23dd694fa89a30bcae237e1475e47d59bb0252b998d` in generated package
`jp.co.soramitsu.interop.pdcfbf7a82c3b4b1a9366617b8b23e2b5`, with installed versions 1 → 2 → 3.
The persistent file stayed under `filesDir/datastore` with the same basename, which both adapter
versions authenticate as associated data. Each reader checked identical keyset XML bytes,
Keystore alias inventory and creation times. Decrypting an old AES challenge proved continuity
of the original master key without exporting it. The seven types include Unicode strings,
Boolean, Int/Long boundaries, Float, Double and string sets. No synthetic plaintext encoding
was substituted for the old adapter's writer, and the test app has no Internet permission.

Before packaging, all 43 runtime binaries and their 43 POMs were fetched from official Google
Maven or Maven Central. All binaries matched cached bytes; 30 binaries also had matching frozen
HEAD checksum pins. All available frozen pins were enforced. The four DataStore 1.0.0 binaries
have no frozen HEAD pins and were independently compared with their actual Google Maven bytes.
The old graph uses the retained 3.8.6.3 catalog's Kotlin 1.9.24/coroutines 1.8.1/Gson 2.10.1;
the current graph is checked against the current production runtime lock. Publisher-byte
verification is recorded separately from independent dependency approval.

Repository evidence fixtures contain only public artifact identities, hashes and nonsecret
results:

- `scripts/qualification/legacy-encrypted-preferences/verified-artifacts-2026-09-06.json`
- `scripts/qualification/legacy-encrypted-preferences/local-result-2026-09-06.json`

Executed command:
`python3 scripts/qualification/legacy-encrypted-preferences/run.py --work /tmp/sora-storage-interop-qualified-local-20260906 --serial emulator-5554`.
Full commands, APK hashes, publisher evidence, device metadata and per-phase instrumentation logs
are under `/tmp/sora-storage-interop-qualified-local-20260906`; the summary log is
`/tmp/sora-android-storage-interop-qualified-local.log`. `StorageProbe.kt`, `run.py` and `README.md`
remain byte-identical to the source hashes recorded by that run. The repository JSON fixtures
and this audit paragraph were copied/written afterward; they were not represented as executed
probe source. Generated APK/Dex files and the temporary test signing key remain outside the repo.

No storage-format compatibility defect was demonstrated in this actual-library upgrade path.
This is local storage/key continuity evidence, not an entire released APK reconstruction,
whole-PayWings integration test, production-signer qualification, or coverage of every Android
version. The materialization reconciliation below resolves the stale local inventory; independent
dependency review remains outstanding.

## Remaining release evidence

Run the signed release candidate over actual retained release-produced encrypted storage/Keystore snapshots, including pre-multiaccount versions and a forced stop at each promotion phase. Real-device Keystore continuity, production flavor/signing identity, all supported OS versions, and the independently reviewed migration receipt remain required before claiming production-wide compatibility. Irrecoverable pre-existing key corruption or unavailable device key material cannot honestly be described as an upgrade that can never fail; recovery must preserve those bytes and avoid replacing keys.


## Current materialization reconciliation (2026-09-06)

The four local inventory failures were repairable structural drift, not an external approval
requirement. The production gate now retains exactly thirty production projects plus the settings
lock: 31 files, 272 configurations, of which 271 are production configurations. The sole
`baselineprofile` test module has no production configurations in its offline Gradle model (36
resolvable configurations total). Exact build/settings/plugin-catalog source bindings plus explicit
test-plugin, namespace, target and dimension checks guard that exception. No production module is
removed from lock coverage.

The policy records the already-present lock and verification metadata digests as
`materialized-unreviewed`, with both `independentlyReviewed` flags still false. Only eight declared
hash/count fields changed in the provenance policy. Resolved versions, actual lock/metadata files,
repository trust pins, signing identity, credentials and approval flags did not change. The
metadata's added 160 artifact entries across 88 new coordinates still require independent review;
the real-library storage experiment above does not approve that entire set.

A separate current observation,
`docs/modernization/qualification/android-dependency-materialization-2026-09-06.json`, retains all
existing review/credential/qualification claims and updates six observed hash/count fields. Its
`observedAtUtc` records the new observation and `retainedBaselineAssessedAt` identifies the older
vendor/config assessment. The original historical release probe remains byte-for-byte unchanged
and is referenced by path/hash; no live-network probe or historical re-execution is claimed.

The signed dependency-review v1 wire shape is unchanged, but its exact reviewed configuration
count is now 272. Prior-count receipts are rejected. The helper, tests, observation and baseline
module sources are bound into the migration v8 qualification source closure. The workflow runs
the new inventory contract before candidate/signing contracts.

Focused validation (no production runtime change or broad Android rerun):

- `node scripts/test-android-production-lock-inventory-v1.mjs`: positive current inventory and
  observation; 111 rejecting mutations, including each missing production module, disguised
  production configuration, count/hash drift, missing/stale/tampered observations and unsupported
  qualification claims.
- `node scripts/test-android-dependency-signing-review-v1.mjs`: positive authenticated admission
  and 29 rejecting mutations, including old count and missing metadata/lock reviews.
- `node scripts/test-android-migration-qualification-v8-contract.mjs`: positive authenticated
  fixture, 6 template and 34 authenticated-path rejections.
- `node scripts/test-android-qualified-candidate-package-v1.mjs`: positive seal/download fixture
  and 30 rejecting mutations.
- `node scripts/test-android-wallet-continuity-source-contract.mjs`: 8 current predicates and
  41 rejecting mutations.
- `node scripts/verify-production-modernization.mjs`: zero failures, 33 release blockers.
- `node scripts/verify-production-modernization.mjs --dependency-preflight`: stable inventory,
  zero failures, six review/credential blockers; expected exit 1 while unqualified.

Logs are `/tmp/sora-android-materialized-{inventory,review,migration-v8,candidate,continuity}-contract.log`;
final gate JSON is `/tmp/sora-android-materialized-reconciliation-{audit,dependency-preflight}.json`.
The isolated before/after patch is `/tmp/sora-android-materialized-reconciliation.patch`; the
changed-file hashes and unchanged-input checks are
`/tmp/sora-android-materialized-reconciliation-evidence.json`. Current materialization identities
changed; historical executed-source and frozen-input evidence did not.

The remaining dependency prerequisites are authenticated independent review admission, credential
incident closure, production lock review, verification metadata review, the scoped IDensic/TensorFlow
workaround review and vendored dependency review. Actual retained-release snapshots, production-signed
candidate artifacts, protected controller/trust inputs and independent device qualification remain
external release inputs. Those checks did not establish that every local retry path was complete; the subsequent availability defect and its fix are documented below.


## Successful preflight retry and combined Room 50 native path

A later completion review found a local availability defect beyond safe-stop/export behavior. After a transient preflight failure, `WalletUpgradeBackup.prepare()` correctly rechecks storage and returns to normal when the failure is resolved. However, the successful Retry branch in `SplashActivity` then called `recreate()`. Android retains the same `SplashViewModel` and its completed-false migration result across Activity recreation, so the screen could return to recovery until the process was restarted. Success now invokes `splashViewModel.retryWalletMigration()` after either kind of admission, replacing the failed result and rerunning migration before navigation. The raw journal compare-and-set retry, source validation, integrity recovery and encrypted-storage gates are unchanged.

All nine `SplashViewModelTest` methods passed in an isolated snapshot of the current checkout. The existing `explicit retry reruns migration and opens main only after verification` test now first observes a failed migration on the same ViewModel, then checks successful explicit retry and deferred recovery scheduling. The Activity source wiring regression rejects a return to `recreate()`. Eight existing source predicates and 41 rejecting mutations also passed. The nine methods are a focused rerun of existing tests, not nine new distinct methods.

The review also identified a concrete combined-test gap: the earlier Room 50 bridge test used placeholder encrypted strings and stopped before account promotion; the promotion JVM tests mocked the native signing boundary; native cohorts began with Room 76 account rows and selection already present. That gap is now covered by three separately invoked methods in `MigrationManagerProductionPathQualificationTest`:

1. `preparePreMultiaccountRoom50NativeFixture` creates released Room 50 token columns with no accounts table, selected address or recorded owner. It writes five real unsuffixed AES-CBC credential envelopes to SharedPreferences and wraps their AES key with the disposable qualification package's Android Keystore alias. The retained current `EncryptionUtil.encrypt(key, cleartext)` helper produces the historical format inspected in `release/sora/2.3.3`.
2. `upgradePreMultiaccountRoom50ThroughProductionPath` opens the actual `AppDatabase.get()` production builder through the 15–57→58 bridge and every subsequent migration to 77. It then runs actual `UserRepositoryImpl` restoration/native signing proof and `MigrationManager` activation. The fixture starts with zero account/identity rows and no selection, so it exercises the restored pre-multiaccount path. Runtime acquisition is mocked; address conversion delegates to the real `Sora2AddressCodec`.
3. `restartPreMultiaccountUpgradeThroughProductionPath` runs after a separate process boundary and install over the existing qualification application, then repeats migration and native signing against the retained store.

**All three phases passed, each 1 passed / 0 failed / 0 skipped.** Both migration and restart preserved one account, all five original ciphertext records, exact private/public/nonce bytes, address/name/selection, the wrapped AES key, the old archived cache row and original source backup. Each observed three network identities and one verified native SORA2 signature. Repeated migration left the journal receipt, cards and DataStore bytes unchanged. Only `jp.co.soramitsu.sora.qualification` and its test package on the existing API 34 arm64 emulator were installed/stopped; the emulator was not reset, and production wallets were not accessed.

The qualification debug APK/test APK build completed offline with existing dependency locks. Reproduction, every command, APK/source hashes, logs and fixed-count observations are under [the local evidence directory](/Users/takemiyamakoto/dev/sora-wallet/testflight-20260906/android-combined-upgrade-evidence/README.md). The actual run is [native-run-1/run-observation.json](/Users/takemiyamakoto/dev/sora-wallet/testflight-20260906/android-combined-upgrade-evidence/native-run-1/run-observation.json); focused JVM/source validation and the exact four-file patch are bound by [completion-observation.json](/Users/takemiyamakoto/dev/sora-wallet/testflight-20260906/android-combined-upgrade-evidence/completion-observation.json).

The parent review applied those exact four tested files to primary `sora-android` at `2026-09-06T14:53:58Z`, after checking each original hash and the resulting tested hash. All **1,477 other baseline source files remained unchanged at integration**, preserving the user's existing work. [primary-integration-observation.json](/Users/takemiyamakoto/dev/sora-wallet/testflight-20260906/android-combined-upgrade-evidence/primary-integration-observation.json) records this handoff; the current audit update was written afterward.

This closes the identified local retry defect and the combined native integration gap. The fixture uses authentic historical encrypted format with synthetic disposable wallets and current retained helpers; it is not an original signed release APK or proof of physical-device production-signer/Keystore continuity. Safe-stop and export alone do not prove functional recovery, and wider retained-release device/OS/interruption proof remains pending. Separately, the iOS typed low-storage retry correction passed all 240 regression tests (including the three focused retry tests, also passed independently), with zero failures or skips and all 6,085 tracked source hashes unchanged. Runtime commit `2a1ee11b77938886a95751946469c15ce10b8dd8` and the earlier selection callback fix are integrated into primary `sora-ios`; the exact seven-file integration preserved 6,077 unrelated tracked files and file modes. Evidence is recorded in [the iOS validation observation](/Users/takemiyamakoto/dev/sora-wallet/testflight-20260906/upgrade-retry-validation-3/run-observation.json) and [the primary integration observation](/Users/takemiyamakoto/dev/sora-wallet/testflight-20260906/upgrade-retry-primary-integration-observation.json). Its signed TestFlight successor archive `3.8.7 (2026090701)` succeeded and draft [PR #470](https://github.com/sora-xor/sora-ios/pull/470) is pushed, but 0701 has **not** been uploaded: CLI export failed with `Failed to Use Accounts`, and the GUI is blocked by the locked Mac with an unlock request already pending. Already uploaded build `2026090602` does not contain the low-storage retry fix; its processing is complete and tester availability still awaits Apple's approval of the submitted France declaration. These local fixes do not establish broader retained-release device qualification.
