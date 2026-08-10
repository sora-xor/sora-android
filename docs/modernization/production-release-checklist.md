# SORA Wallet Production Release Checklist

This release is fail-closed. A build is not a production candidate while
`node scripts/verify-production-modernization.mjs --release` reports any blocker.

## Identity and migration

- Confirm the production application ID, signing certificate, Room database name, Android
  Keystore alias, DataStore/shared-preference names, and SORA2 derivation have not changed.
- Release builds must use the dedicated `productionRelease` signing configuration and must never
  fall back to the checked-in debug key. Supply all four CI signing inputs, reject missing or
  symlinked keystores, and match the bundle, upload, and Play App Signing public certificate
  SHA-256 fingerprints against the retained production identities before rollout.
- Run `testProductionDebugUnitTest` for every changed Android module, including assets/send,
  referral/finality, node selection, and main-app integration. On API 36, execute the common
  file-recovery, runtime-cache recovery, retained Room, encrypted-storage, and production-path
  device suites. Run `testProductionReleaseUnitTest` and dependency-aware
  `lintProductionRelease`, assemble and bundle the minified signed production variant, require a
  nonempty regular R8 mapping file, verify the release APK signature, install that exact APK on an
  API-36 device, and launch its production `SplashActivity` before candidate admission.
- Export Room schema 77 and migrate retained snapshots for every shipped schema 58–76 under
  both DELETE and WAL journal modes, including the journaled explicit-wallet-deletion and generic
  SORA2 ambiguity tables and crash-recovery matrix. The retained-schema identity matrix is 76
  distinct cohorts: 19 source schemas × 2 journal modes × exact single- and multi-account
  inventories.
- Generate the current schema 77 from `AppDatabase` and retained schemas 74, 75, and 76 with the
  test-only `AppDatabaseV74SchemaFixture`, `AppDatabaseV75SchemaFixture`, and
  `AppDatabaseV76SchemaFixture` using the pinned Room/KSP compiler. Copy each retained JSON byte-for-byte
  into the retained `AppDatabase` schema directory and accept them only after the direct 73→74,
  74→75, 75→76, and 76→77 `runMigrationsAndValidate` checks. The canonical retained schema directory must
  be packaged as an Android-test asset so `MigrationTestHelper` resolves each copied schema by
  `AppDatabase`'s canonical name. Run the main and Android-test schema processors serially with
  `--no-parallel` because they publish below the same schema root. Never hand-edit generated JSON.
- Exercise single- and multi-account, legacy empty-suffix, 12/24-word mnemonic, retained
  15-word SORA2-only mnemonic (no Nexus children and no secret rewrite), raw-seed,
  explicitly watch-only, missing/corrupt secret, interrupted activation, rollback, reinstall,
  low-storage, WAL, and journal-mode fixtures.
- After the complete matrix passes, record only non-sensitive aggregate proof in
  `docs/modernization/qualification/android-migration-matrix.json`: schemas 58–76, target 77,
  zero lost accounts, SORA2 signing parity, and the independently reviewed SHA-256 identities of
  the exact exported `74.json`, `75.json`, `76.json`, and `77.json` Room schemas. All four schema files must be regular,
  non-symlink files whose embedded Room version and bytes match that receipt. The release verifier
  rejects a missing or partial receipt, including absent DELETE-journal, WAL-journal, or live
  WAL/SHM byte-parity qualifications. Record those three aggregate checks as
  `deleteJournalModeQualified`, `walJournalModeQualified`, and
  `walSidecarByteParityQualified`; never put account identifiers or secret material in it.
- The Android receipt must separately bind exact-schema single- and multi-account cohorts, a
  successful first activation through `MigrationManager`, an exact exported-schema 77-to-77
  snapshot, encrypted-storage read-back, SORA2 address parity, and SORA2 signature verification.
  Failure-only tests, mocked address conversion, placeholder account rows, or a public-key-only
  vector cannot qualify those fields. The current-schema snapshot must use a pinned public
  SORA2 vector and a coherent verified activation journal; placeholder addresses, synthetic
  public keys, and count-only assertions do not qualify. Record the aggregate counts exactly: 76 retained-schema
  cohorts (38 single-account and 38 multi-account), six successful secret-source cohorts
  (12-word, 24-word, retained 15-word SORA2-only, raw seed, retained keypair-only legacy secret,
  and watch-only), two secret-failure cohorts, and one exact current-schema snapshot cohort, seven isolated production-path cohorts,
  and five ordered encrypted-storage process phases. Record `productionPathCohortCount` as exactly
  7, `successfulSecretSourceCohortCount` as exactly 6, `legacySecretQualified` as true only after
  the retained keypair-only cohort passes, and `retainedFifteenWordMnemonicQualified` as true only
  from the complete current-source run. Current-schema backup admission must require the complete
  encrypted key tuple for every signing source, reject every partial private/public/nonce tuple,
  and reject conflicting mnemonic, raw-seed, keypair, or watch-only evidence for the verified
  source label. Bind
  the receipt to the full direct migration/crypto/storage/database test closure, all retained Room schemas
  58–76, build and dependency provenance, reviewed verification metadata and lock files, workflow,
  runner, checklist, and verifier through `qualificationContractSha256`; any source or dependency
  evidence change invalidates the receipt. The receipt has an exact aggregate-only key allowlist and must not carry
  free-form notes, per-wallet rows, addresses, account identifiers, phrases, seeds, private keys,
  signatures, or raw signed payloads.
- Receipt schema 7 must also record a positive retained release-produced snapshot count and the
  reviewed SHA-256 of its manifest; the exact executed test-method counts for
  `WalletIdentityMigration75Test`, `WalletUpgradeBackupTest`, `MigrationManagerSafetyTest`,
  `MigrationManagerProductionPathQualificationTest`, `EncryptedWalletMigrationStorageTest`, and
  `Sora2AddressCodecTest`; zero failures and zero unexpected failures; the exact
  `aggregateTestResultSha256`; and independently reviewed `rawExecutionEvidenceSha256`,
  `rawRunIndexSha256`, and `rawResultBundleSha256` values covering the protected raw outputs.
  The raw collector output is explicitly non-authorizing and public aggregate-only; keep release
  blocked until a reviewed raw-run inventory and immutable producer retain and bind every indexed
  method inventory, APK identity, report, and transcript. Source inspection, inferred
  counters, shell exit status, or a partial run cannot produce the receipt. Follow
  `docs/modernization/qualification/android-migration-matrix-README.md`.
- Authenticate schema 7 and evidence-manifest schema 2 with the independently pinned Android migration trust root: a distinct
  device producer and reviewer, role-prefixed P-256 key IDs, the reviewer signature over the exact
  receipt, and producer plus reviewer signatures over the exact evidence manifest. Bind the
  protected source revision, run UUID, monotonic sequence, app-build identity, four fixed-path
  qualified aggregate artifacts, the fixed-path non-authorizing raw-execution artifact, and the
  seven-day/48-hour/24-hour replay policy. Schema 6/v1 and older wire shapes populated with copied
  hashes or true booleans are not evidence and must be rejected.
- Keep production admission blocked until a separately reviewed protected handoff can materialize
  the signed migration receipt, evidence, trust root, public keys, signatures, and five fixed-path
  artifacts into the fresh release runner. The current workflow supplies identity pins and paths
  only; it does not fetch those bytes. Do not commit the receipt as a shortcut because its required
  `sourceRevision == github.sha` binding would be self-referential.
- A missing live Room database is not an empty install when any retained upgrade-backup namespace
  or wrapped AES-key entry is present, even if current wallet preferences are also missing. Startup
  must remain in recovery mode and preserve the complete recovery bytes; it must not expose wallet
  creation as the default path.
- Run `scripts/run-encrypted-wallet-upgrade-qualification.sh` on the release emulator contract.
  Its five methods must run as separate instrumentation processes in order. The first installs a
  retained v73 database, unsuffixed release-era AES-CBC ciphertext in `sora_prefs.xml`, the exact
  RSA-wrapped AES key under `key_alias/secret_key`, and a verified immutable pre-import backup.
  The next two processes must import the unchanged ciphertext through SharedPreferencesMigration,
  recover the keypair and mnemonic while preserving the historically absent mnemonic-seed record,
  create no address-suffixed replacement, then read the retained DataStore bytes again after a
  second process death without mutating them. The retained AES-CBC fixture must encrypt the real
  pinned mnemonic-derived private/public/nonce tuple, and both restarted reads must sign and verify
  a fixed public challenge with the recovered key; placeholder private/nonce bytes or a
  public-key-only assertion do not qualify. The final processes must prove authenticated-envelope
  tamper, wrapped-key corruption, and missing Android Keystore alias all fail closed without
  rewriting ciphertext, replacing the wrapped AES key, or creating a new alias.
- Run `scripts/run-migration-manager-production-path-qualification.sh` on the same reviewed
  emulator. It must target exactly `jp.co.soramitsu.sora.qualification` with test package
  `jp.co.soramitsu.sora.qualification.test`; the app-only flavor has no release variant and its
  minimal Application must not start the production Hilt graph. Before installing either APK, the
  runner must inspect both built manifests and reject any application ID that is not the exact
  isolated target/test pair, aliases production, or declares a shared Android UID. Each of its seven methods runs in
  a separately force-stopped process and invokes the real `MigrationManager.start` with a
  retained file-backed Room 76 database migrated to Room 77. The Room 76 source must contain the
  exact `UNKNOWN`/`PENDING_VERIFICATION` wallet identity and disabled SORA2 rows produced by the
  73→74 copy-on-write migration for every retained account; an accounts-only schema fixture is not
  production-path evidence. The cohort also uses real encrypted preferences, real `PrefsCredentialsDatasource`,
  real `PrefsUserDatasource`, real `UserRepositoryImpl`, and real prefix-69
  `Sora2AddressCodec`. Selection and `REGISTRATION_FINISHED` must be loaded from the retained
  DataStore through the production user repository; its constructor initialization must reach a
  successful terminal state and its controlled application scope must be canceled and joined
  before Room closes. It must reopen Room and prove the complete verified journal,
  identity/network rows, exact selection, ciphertext/data-store parity, and recovered-key signing
  for 12-word, 24-word, retained 15-word SORA2-only, raw-seed, retained keypair-only,
  watch-only, and two-account cohorts. The retained keypair-only method must preserve blank
  mnemonic/seed fields and byte-identical encrypted private/public/nonce storage, verify address
  binding and stored-key signing parity, remain `LEGACY_SECRET`, and create no Nexus child.
  The retained 15-word method must prove the phrase is rejected as a new public import, remains
  `MNEMONIC_UNSUPPORTED`, recovers the unchanged SORA2 signing key, and creates no Nexus child.
  Unit tests with mocked Room,
  user-repository migration boundaries, credential reads, address conversion, journal activation,
  derivation, or signing are supporting evidence only and cannot set
  `successfulActivationQualified`. Record only aggregate cohort and pass/fail evidence in the
  reviewed receipt; never copy fixture or device wallet material.
- Do not label SharedPreferences-to-DataStore import as raw storage-file immutability: the
  authoritative file legitimately changes. Receipt schema 7 must instead record the exact
  aggregate booleans `verifiedPreImportBackup`, `encryptedSecretCiphertextParity`,
  `wrappedAesKeyParity`, `keystoreAliasContinuity`, and `noCredentialRewrite`, with matching
  qualification checks. An old `encryptedSecretStorageUnchanged` assertion is not acceptable
  evidence for this migration.
- Account count, selected account, SORA2 address, public/private signing vector, and preferences
  must match. A mismatch must leave the legacy tables and encrypted backup intact.
- Treat legacy on-chain Iroha-migration completion flags as account-scoped advisory state, never
  signing authority. Re-query `needsMigration` for the exact still-selected account immediately
  before loading its SORA2 signing key; an already-completed authoritative response succeeds
  idempotently without signing, while an account switch or unavailable check fails closed. Wipe
  the decrypted private key and nonce after every attempted claim. Tag the claim purpose in memory
  and reject it before signing, then recheck at durable staging, while that wallet's
  backward-compatible generic SORA2 journal witness remains unresolved; status recovery is
  read-only and never resubmits it.
- Treat every `VERIFIED` migration receipt as untrusted input. Its migration ID, positive and
  internally consistent counts, bounded selected-wallet identifier, canonical SHA-256 integrity
  hash, null failure, positive start time, and monotonic terminal completion time must first pass
  structural validation. A malformed verified receipt must enter recovery through an exact
  compare-and-set transition; it must never be overwritten with a new `VERIFYING` row or trigger
  secret inspection.
- A structurally valid receipt is the last fully verified checkpoint, not an assertion that users
  can never create, rename, select, or remove an account. If its count, selection, or integrity
  hash differs from the live snapshot, re-fetch and verify every current secret source, SORA2
  address/signing vector, wallet identity, and network child under the wallet lifecycle lock.
  Re-read the complete current account snapshot and model in one Room transaction, then advance
  the exact old receipt to the current count/selection/hash with a complete compare-and-set that
  binds both old counts, selected wallet, hash, start time, and completion time. Any secret/model
  mismatch or failed CAS must latch recovery; ordinary verified lifecycle changes must never lock
  a healthy wallet out on its next launch.
- Migration-time SORA2 identity verification must use the production `Sora2AddressCodec` pinned
  to the installed wallet's existing SS58 prefix 69. It must not depend on an asynchronously
  loaded runtime snapshot or a mocked address-conversion boundary; cross-prefix and malformed
  addresses fail closed. The live SORA2 runtime loader must likewise reject metadata whose
  `System.SS58Prefix` is missing, undecodable, out of range, or anything other than the exact
  integer 69 before publishing the metadata hash or cached snapshot that can authorize wallet or
  Polkamarkt behavior. A live identity mismatch must propagate; it must never be swallowed as an
  ordinary fetch failure that falls back to previously cached metadata. Cache refreshes must write
  content-addressed metadata and type-definition generation files through `AtomicFile`, verify
  both read-backs, and atomically publish a final manifest that binds their full hashes and runtime
  version. Cancellation or process death before that pointer may leave only unreachable generation
  files; it must never expose a mixed pair. An invalid, partial, or evicted published generation
  must not be combined with either legacy file: only the exact bundled metadata/types pair is a
  permitted recovery source. Legacy cache files are accepted only when both hashes equal that
  bundled pair; malformed, oversize, unreadable, directory-backed, or one-sided legacy entries
  must be recorded and ignored without blocking the validated bundled fallback, while invalid
  packaged fixtures still fail closed. Manifest reads and writes are capped at 16 KiB, runtime
  metadata at 16 MiB, and mobile/default type definitions at 8 MiB. File and asset readers must
  stop after at most one byte beyond their cap and reject malformed UTF-8. HTTP type downloads
  must request identity encoding, stream through the same limit+one admission, and follow at most
  three HTTPS-only redirects. They must run through interruptible coroutine I/O, discard every
  cancelled result, disconnect the active transport from a cancellation sibling even when reads
  ignore thread interruption, and enforce a 45-second absolute deadline in addition to finite
  connect/read inactivity timeouts. Legacy RPC strings materialized by the externally owned socket
  layer must still pass the same encoded-byte admission immediately after the callback and before
  parsing, hashing, or publication, but that callback-side check is defense in depth for read-only
  traffic and is not production runtime authority. The pinned stock `nv-websocket-client` 2.14
  allocates an incoming frame before callbacks and has unbounded fragmented-message/decompression
  aggregation; `setMaxPayloadSize` and `setFrameQueueSize` are outbound-only controls and must never
  be accepted as receive-side qualification. No production runtime-authority byte may traverse
  that path; the bounded HTTPS contract below is authoritative.
  Production-reviewed metadata and type hashes must be checked before either runtime parser is
  invoked for mutation preparation.
  After a newly published manifest is read back and verified, maintenance may retain at most 32
  matching cache candidates in one pass and delete at most four exact, regular, non-symlink
  content-addressed generation files that are not the active metadata/types pair. It must not
  delete the active generation,
  AtomicFile recovery files (`.bak`/`.new`), legacy entries, or unrelated cache content. Before
  every AtomicFile write, the base, `.bak`, and `.new` paths must be admitted without following
  links; any pre-existing symlink or non-regular entry fails closed. The advisory runtime-version
  preference may trigger a redundant refresh, but must
  never define the identity of a published generation. The in-memory metadata hash, exact raw
  type-definition hash, prefix, and snapshot may be published only after this validation.
- Before qualification, execute Android fault-injection coverage for process death/cancellation
  after each AtomicFile generation write and immediately before/after manifest publication. The
  real-device `FileManagerAtomicRecoveryTest` covers base/backup/new no-follow admission and
  old-versus-new single-entry recovery, but its source is not evidence of an executed device pass.
  The runtime-generation matrix must additionally exercise every two-file/manifest interruption
  boundary. Qualification must retain the old complete pointer or load the new complete pointer
  and must never expose a mixed pair.
- iOS ordinary SORA2 sends and fee preparations must apply the same exact reviewed runtime-130
  metadata digest used by Polkamarkt to the `RuntimeCoderFactoryProtocol` that encodes and signs
  the extrinsic. Matching spec/transaction versions alone cannot authorize any mutation.
- Schema 73→74, 74→75, 75→76, and 76→77 migrations must reject any pre-existing destination table or index before
  their first write, including names that differ only by SQLite casing. An unexpected copy-on-write
  or deletion namespace is recovery evidence; it must not be accepted through `IF NOT EXISTS`/
  `INSERT OR IGNORE` row provenance assumptions.
- The pre-activation backup must be validated from its copied SQLite/WAL/SHM bytes, not from
  pre-copy in-memory counts. Its manifest, account/identity/network inventory, selected-wallet
  value, encrypted preference inventory, migration journal, and SQLite integrity check must all
  agree before the backup gate can clear.
- Wallet migration and explicit wallet deletion share one process-wide mutation lock. SORA2
  signing must refuse to start while an explicit deletion journal is active, and must recheck the
  verified wallet, migration journal, and enabled SORA2 account under that lock.
- A signed pending transaction must be inserted atomically only while no deletion journal is
  active. Unresolved rows block deletion; terminal overlay rows may be removed with their wallet
  and must not permanently prevent a user-confirmed deletion. The deletion-begin DAO transaction
  must independently recheck the exact target set for unresolved rows, and a late callback must
  never regress a durable `FINALIZED` or `REJECTED` row to an unresolved state.
- Every pending transaction row created at schema 77 or later must persist the exact configured
  chain UUID/genesis hash as part of its unique identity. Every new Taira row must additionally
  namespace its existing `localId` with the exact admitted deployment-manifest SHA-256, because a
  later signed mapping may reuse either reviewed UUID. The 76→77 copy leaves the chain column null
  for every retained row. Null, retired-chain, unbound-ID, and other-manifest rows remain immutable,
  readable recovery evidence: they must block mutation admission, must never be rewritten to the
  current deployment, must never be pruned as ordinary terminal history, and must be rejected
  before any Torii/status request.
- Every ordinary SORA2 transfer, swap, liquidity, referral, and shared `ExtrinsicManager` mutation
  must stage its exact deterministic hash, wallet/account identity, genesis/runtime/type identity,
  and mortal-era witness in `sora2PendingSubmissions` before the sole transport attempt. Restart
  recovery is status-only: canonical finalized blocks and exact System success/failure events may
  resolve inclusion, and absence is terminal only after the complete `[birth, death)` era has been
  scanned and finality is at or beyond the exclusive death height. It must never sign, retry, or
  resubmit. Recovery event decoding must use only a checked-in runtime fixture whose genesis,
  spec/transaction versions, metadata SHA-256, and type-definition SHA-256 exactly equal the row.
  If that fixture is unavailable after a runtime upgrade, the row stays unresolved and
  non-prunable until a separately reviewed historical fixture is shipped. Bounded recovery
  batches must rotate after every attempted pass so one unavailable old fixture cannot starve
  newer exact witnesses in the same process. Corrupt or incomplete rows remain fail-closed and
  block wallet destruction.
- The ordinary and Polkamarkt signed handoff must use exactly one manifest-bound bounded HTTPS
  `author_submitExtrinsic` request. Redirects, non-200 responses, non-JSON media, malformed RPC,
  and a returned hash unequal to the deterministic local hash all preserve durable submission
  ambiguity; none may fall back to or retry through nv-websocket. Stock nv-websocket 2.14 receive
  aggregation remains unbounded and therefore may not carry signed submission or authoritative
  finality recovery.
- Project ordinary generic journal rows into the existing activity overlay after process restart.
  A matching PI hash suppresses only the duplicate UI row and never deletes journal evidence; the
  exact journal still overrides PI's displayed pending/terminal status and canonical block hash.
  Pending rows sort ahead of indexed history, while old terminal semantic-free rows participate in
  normal timestamp order so they cannot crowd newer PI records out of bounded lists. Asset-filtered
  history must omit a semantic-free generic placeholder rather than inventing an amount, token,
  fee, or recipient. Polkamarkt keeps its richer trade overlay and may terminalize
  that legacy row only from the exact matching generic Polkamarkt recovery witness. A pre-existing
  Polkamarkt row without that witness remains unresolved.
- Explicit deletion is authorized only by a short-lived preview whose before/after Room snapshots
  and before/after encrypted-preference fingerprints match exactly. A crash may resume the
  journaled operation; a mismatch freezes it for recovery and must never widen the deletion set.
- On iOS, explicit removal must first match the active copy-on-write wallet-network snapshot
  against the exact Core Data wallet inventory and selected wallet. The verified removal snapshot
  must activate before the atomic Core Data delete/selection update; retained-wallet selection and
  legacy selected-account metadata must read back successfully before any target Keychain material
  is deleted. A missing snapshot, target, selection, or post-commit match enters recovery and keeps
  the Keychain intact.
- iOS account import, account switching, wallet-network migration, and explicit removal must use
  one lifecycle mutation coordinator through the final metadata read-back and Keychain cleanup;
  release stress tests must prove a concurrent lifecycle operation cannot replace the verified
  removal snapshot or reintroduce the target between those phases.
- Under that same iOS lifecycle lease, explicit removal must read and validate both the Nexus and
  Polkamarkt pending journals before activating the removal snapshot. Any nonterminal mutation,
  legacy Nexus commit without history reconciliation, unreadable journal, or symlinked journal
  pauses deletion before Core Data or Keychain changes. Nexus must publish its pre-sign marker
  only after acquiring this lease and revalidating the active wallet, so deletion cannot pass the
  preflight while a new send creates an orphan unresolved row.
- Nexus finality on both platforms must require a strictly positive committed block, an independently
  qualified finalized checkpoint at or beyond that block, authoritative account inclusion, balance
  read-back, and exactly one history transfer matching hash, sender, receiver, asset, and exact
  amount. An installed legacy `committed` row without the newer history timestamp must retain its
  monotonic transport state while these checks run and gain only the proof timestamp on success;
  it must never be downgraded, resubmitted, or stranded by a legacy zero-height placeholder.
- After an iOS account switch commits, construct Wallet, Invest, Activity, and More—including its
  SORA2 balance provider, Nexus coordinator, and Polkamarkt wallet context—from the same captured
  selection, recheck that selection, and publish the complete five-tab graph once. If any factory
  fails, remove every old-account tab and show recovery/export assistance; never leave a partial
  old/new controller graph or continue refreshing the previous wallet context.
- iOS mnemonic, raw-seed, and keystore validation must be pure: derive and prove
  secret-to-public-key-to-address consistency in memory without a Keychain write. A new wallet is
  eligible for activation only after duplicate and exact-selection checks run under the lifecycle
  lease. One non-secret, ordered commit journal must then cover Keychain persistence, an atomic
  Core Data insert-and-select save, wallet-network activation, and legacy selected-account
  read-back. Any unfinished stage routes to recovery on the next launch.
- iOS startup must inspect the new-wallet commit journal before opening or migrating the wallet
  database, and run the copy/verification migration on a bounded background queue under the
  lifecycle coordinator. No database migration, wallet-network bootstrap, final removal
  verification, or Keychain mutation may wait on the main thread; only terminal UI callbacks may
  hop there.
- Pin the iOS production Core Data destination explicitly to `UserDataModel 2`. Enumeration order,
  a missing `CaseIterable` value, or any `fatalError` path must never choose or terminate an
  installed-wallet migration.
- If the live iOS Core Data store is missing, any retained `WalletMigrationSafety` namespace or
  SQLite WAL/SHM/rollback-journal sidecar is wallet evidence even when settings, Keychain, and the
  active wallet-network snapshot are also unavailable. Use no-follow namespace checks so a
  dangling store, sidecar, or safety-directory symlink cannot masquerade as absence. Preserve the
  evidence, enter recovery, and never let Core Data create a replacement empty store; a truly empty
  post-deletion namespace must remain separately covered.
- Treat every known legacy wallet Keychain tag and retained wallet setting as installation
  evidence, including the pre-account-model `userName` key in either protected storage or settings
  when its paired entropy or selected-account payload is missing or unreadable. That inconsistent
  namespace must enter recovery and must never route to onboarding or new-wallet creation. The
  legacy decentralized-ID, public-key-ID, and completed-migration settings are wallet-backed
  identity evidence too. The wallet-network schema-version setting is likewise activation evidence
  if its snapshot is later missing or unreadable.
- Resolve the pre-account-model display name without rewriting either source: accept a valid
  Settings-only or UTF-8 Keychain-only value, or two exact matches. Malformed, oversized, or
  conflicting name evidence must enter recovery before Core Data or wallet-network activation.
- Before any Core Data metadata lookup, persistent-store open, SQLite checkpoint, or legacy copy,
  require the installed main store and every existing WAL/SHM/rollback-journal sidecar to be
  regular non-symlink namespace entries using `lstat`. A reachable store symlink and a sidecar
  symlink must both enter recovery without reading or changing either target. Revalidate the
  staged activation source and retained legacy rollback source immediately before each Core Data
  replacement operation; never follow a backup symlink during rollback.
- iOS name and asset-preference changes must perform field-specific merges against the latest
  durable row under the lifecycle lease. Name copy-on-write activation failures are sticky
  recovery failures, and immutable wallet-network snapshots must remain recovery-safe while being
  retained under a fixed bound.
- Exercise iOS interruption points before snapshot activation, after activation but before Core
  Data deletion, after Core Data deletion, during retained-wallet reselection, and immediately
  before Keychain cleanup. Each case must either resume/complete only the confirmed removal or
  retain recoverable signing material without onboarding or silently recreating a wallet.
- Record the privacy-safe iOS result in
  `Fixtures/Modernization/ios-migration-qualification.json`. The receipt must bind every retained
  Core Data snapshot and the current-schema safety snapshot, account count, selected wallet,
  unchanged Keychain identity, SORA2 signing parity, multi-account, mnemonic/raw-seed/watch-only,
  missing/corrupt-secret, interruption, reinstall/upgrade, rollback, and low-storage cohorts.
  Aggregate `status` and `zeroLostAccounts` strings alone do not qualify the migration.
- iOS receipt schema 4 must bind the exact retained model identities (`UserDataModel` and
  `UserDataModel 2`) and their checked-in SHA-256 values, an independently reviewed retained-release
  snapshot manifest, two single-account plus two multi-account model cohorts, six successful
  secret-source cohorts (12-word, retained 15-word SORA2-only, 24-word, raw-seed,
  retained keystore/secret-only, and watch-only),
  two secret-failure cohorts, two current-schema safety-snapshot cohorts, and all five
  explicit-removal interruption points. The retained 15-word cohort must prove unchanged SORA2
  derivation/signing and Keychain identifiers, no address-scoped secret promotion, no Nexus child,
  and a rederived fail-closed identity preflight before explicit deletion. The secret-only cohort
  must likewise prove unchanged SORA2 signing identity, unchanged Keychain identifiers, no Nexus
  child, and fail-closed deletion on corrupt material. It must record the exact executed method
  counts for `WalletModernizationTests`, `WalletRecoveryCapabilityGateTests`, and
  `WalletRecoveryExporterTests`, zero failures, and a reviewed result-bundle SHA-256 covering all
  three suites.
  Bind the receipt to the migration, recovery, lifecycle, deletion-preflight, pending-journal,
  Core Data model, SORA2 extrinsic/signing implementation, project, test, checklist, and verifier
  sources through `qualificationContractSha256`; any bound source change invalidates the receipt.
- Migration diagnostics may record only stable outcome classes. Never log a raw localized error,
  wallet/account identifier, address, phrase, seed, private key, or signed payload; detailed
  recovery guidance remains local to the recovery-safe UI.
- A missing selected-wallet preference, an incomplete or unreadable secret, an unknown
  wallet-scoped preference type, malformed/unknown preference serialization, or an interrupted
  migration journal is a recovery condition—not permission to select, recreate, or delete a
  wallet.
- Keep every published, verified upgrade backup immutable through this migration release and at
  least the following dual-read release. Do not prune it during migration, recovery export, retry,
  or normal startup. A changed legacy source must publish a separately fingerprinted backup. On
  Android, publication must use exclusive destination-directory and no-follow destination-file
  creation, then byte-copy and SHA-256/size-verify the fully verified staging tree without replacing
  any existing name. Copy `.complete` last, fsync every file and populated directory bottom-up,
  remove only the exact owned staging inode tree, and revalidate the independently owned published
  tree and installed database/settings immediately before allowing Room to open. The
  already-snapshotted current-schema shortcut must hash-bind the complete installed database,
  SQLite sidecar, DataStore, SharedPreferences, and wrapped-key file set across that final admission;
  wrapped-key presence alone is not continuity evidence. Never fall back to check-then-rename. Any
  partial destination or staging residue remains recovery evidence and must keep startup
  fail-closed. Every staged and final regular file must remain exactly single-linked, the published
  file must have a distinct dev/inode identity from its staging source, and their size and SHA-256
  inventories must match before and after exact-owned staging cleanup. An extra link makes the
  backup mutable through an unreviewed name and must fail qualification.
- Publish an Android recovery ZIP by exclusively creating and durably copying its final name only
  after the staged inode, SHA-256, central-directory inventory, manifest, and every uncompressed
  entry have been verified. Revalidate the independently owned final file before and after removing
  only the exact owned hidden staging file. Never path-delete a published ZIP on failure; an
  uncertain publication returns no share URI and remains local recovery evidence for explicit
  handling. Enforce a single link for both staged and final files and exact final size/SHA-256 and
  archive-inventory parity.
- Create SQLite backup-validation namespaces exclusively and clean only a twice-inventoried,
  no-follow dev/inode-owned set of expected validation files and directories. Never use recursive
  path deletion, and preserve the namespace while failing closed on any unexpected entry, link, or
  parent identity. Android API 26 exposes no public descriptor-relative unlink/rmdir primitive, so
  parent/leaf identities must bracket every removal; this protects cooperating app processes but
  is not represented as atomic protection from a hostile same-UID component in the final syscall
  window.
- Do not equate “already on Room schema 77” with “wallet namespace already activated.” Every
  schema-77 cohort must have one verified immutable `77-to-77` database/settings snapshot for its
  authoritative legacy wallet inventory. An exact pristine copy-on-write namespace publishes it
  before application migration may read secrets or activate identities; already-verified cohorts
  publish it before normal startup proceeds, without manufacturing repeat backups from WAL
  sidecar churn. Interrupted schema-77 states publish the snapshot and remain recovery-gated.
- Deletion-preview and confirmation failures must always dismiss progress. A durable journal
  failure restarts into the encrypted-backup recovery route; a pre-journal stale preview may only
  be replaced by a newly verified preview and a fresh user confirmation.
- Before offering deletion for a retained 15-word wallet, rederive its SORA2 signing key from the
  unchanged phrase and require exact stored-key/address parity. A mismatched key or phrase must
  fail before preference preview or durable deletion-journal creation; the valid preview itself is
  non-destructive and does not grant mutation authority without a separate confirmation.
- Verify recovery archive export and explicit “continue with existing SORA wallet” mode. Neither
  path may create, normalize, rotate, delete, or select a different wallet. Export must prefer a
  verified immutable backup, fall back to hash-stable still-encrypted live storage when backup
  publication could not complete, never present an interrupted staging directory as verified, and
  atomically no-replace-link a fully synced archive name. Failure before that link may leave only
  hidden staging evidence; uncertainty after the link may retain the exact verified final inode and
  its hidden link, but must return failure, grant no share URI, and never path-delete either name.
  Legacy continuation is visible only after a raw, read-only current-schema
  inspection proves that the selected preference resolves to the exact durable legacy account;
  migration failure must never route to onboarding.
- Android JSON wallet export must embed the checked-in SORA2 mainnet genesis rather than remote
  configuration and must pass every stored hexadecimal seed to the encoder as its decoded raw
  bytes, including multi-account exports. A blank, stale, or compromised configuration response
  must not change recovery material. Focused source coverage is present but is not execution
  evidence until the guarded account test suite passes on the current tree.
- Restarted `VERIFYING`/`RECOVERY_REQUIRED` recovery must keep Room closed until raw SQLite and
  encrypted-preference inventory validation succeeds. “Continue” then installs connection-local
  write guards over every authoritative wallet/journal table and a process-wide capability gate
  over account creation, selection, removal, secret writes, and SORA2/Nexus signing.
- Because the legacy write guards are SQLite TEMP triggers, close and reopen the gated helper while
  legacy read-only mode remains active and prove the new connection rejects wallet-table insert,
  update, and delete before any DAO can use it. A cached recovery-mode Boolean is not evidence that
  a replacement connection still owns the triggers.
- “Retry migration” must be an explicit user action that compare-and-sets the one expected journal
  row and revalidates account count, selected wallet, canonical integrity hash, preferences, and
  post-transition state. Startup alone must never retry secret access or copy-on-write mutations;
  failed retry returns to guarded recovery without deleting or replacing legacy material.
- Once a `VERIFYING` journal write may have committed, cancellation, low storage, and any later
  failure must synchronously close the live-process wallet mutation/signing gate before escaping.
  This remains mandatory even if writing `RECOVERY_REQUIRED` fails; restart then re-inspects the
  durable journal without treating cancellation as a normal migration failure.
- An unclassified splash/migration exception must never demote an existing backup-preflight
  `BLOCKED` state or clear its failure. Raw migration retry remains unavailable for stale staging,
  invalid backup, low-space, and every other non-migration blocker until backup preparation passes.
- Force backup failure before dependency injection and assert the recovery Activity still renders;
  eager singletons, DAOs, and scheduled workers must be unable to open or migrate Room until the
  backup gate is successfully cleared.

## Network and indexer

- Negotiate Torii `/health` as `text/plain` and accept only the exact `Healthy` payload; keep
  versioned account, asset, history, pipeline, and MCP routes on `application/json`. A 406 or any
  other body is an unavailable network, never permission to enable sends or reuse cached health.
- Reject a nominal 2xx Torii response when fanout headers are absent in part, malformed, report a
  first failure, or show fewer successful routes than attempted. Partial proxy data is never an
  authoritative zero balance, quote, status, history page, or finality result.
- Balance, asset-definition, exact account-transaction, and instruction-history reads must include
  the complete bounded fanout count family; stripping that proof fails closed. A
  transaction-submission receipt or an authoritative local/hinted pipeline-status response may
  legitimately include `x-iroha-routed-by` without fanout counters. Terminal status is still
  accepted only after its global state/hash/block identity checks; any fanout counters a response
  declares must be complete and healthy.
- Resolve `xor#universal` through the exact singleton asset-definition route and require its active
  alias binding, display name `xor`, and opaque canonical Base58 ID. Use that returned ID for the
  balance query, native quote, signed instruction, exact-count account-transaction proof, finality
  read-back, and history filter. Every current-XOR portfolio/send balance must repeat the exact
  opaque ID, display name, active alias, global scope, and account. Never select XOR by display
  name: Taira can contain more than one distinct definition named `xor`.
- Persist the resolved opaque asset ID in every new pending Nexus send before signing. A journal
  written by an older mobile release can legitimately lack that field; keep it readable and
  unresolved, and never fill it from the alias's current binding during recovery. Android restart
  reconciliation must validate and use the persisted exact ID for account-transaction proof,
  balance read-back, and instruction history without resolving today's alias first or requiring
  mutable name/alias metadata still to point at that ID. The portfolio may project a pending row
  only beneath a current-XOR balance with that exact canonical ID; mismatched or missing identities
  remain available for recovery but fail closed from the displayed asset row.
- The mobile transport's Base58/version/UUID checks are only an early wire-shape gate. The pinned
  native Iroha bridge must perform the full 21-byte version, UUIDv4, and BLAKE3 checksum parse and
  prove the exact same asset ID in quote and signed-Norito parity fixtures before mutations can be
  enabled.
- The first wallet screen must always include an account-bound SORA2 XOR row alongside Minamoto
  and enabled Taira rows, even when XOR is not a favorite asset. On account switch, replace the
  address immediately and show an unavailable balance until that same account's XOR stream emits;
  never retain or pair a previous wallet's quantity with the newly selected address. Keep the
  SORA2 mainnet and Taira testnet badges visible on every receive, send, history, QR, clipboard,
  and explorer route.
- PI `_health` must match the reviewed endpoint, service identity, SORA2 ecosystem/chain/network,
  read-only mode, and a fresh coherent finalized/indexed worker checkpoint. Validate the genesis
  and checkpoint hash as additional constraints whenever the deployed schema exposes them; every
  consumed history record must carry a block hash that matches authoritative SORA RPC.
- PI `mobileConfig` must expose all five emergency capability flags before a production mutation
  can be enabled. A missing or rejected field is a release blocker, not permission to use a local
  default for sends or trades. Mutation authority must be session-local and invalid at process
  start and while a live refresh is in flight; persisted flags may support cached read-only UI but
  must not enable a send or trade in a later process. Publish one atomic live capability snapshot,
  and use refresh generations so an older response or failure cannot overwrite a newer result.
- Before any production Gradle task, run the bounded read-only PI contract probe against the exact
  reviewed HTTPS endpoint. Reject redirects, oversized or malformed responses, GraphQL partial
  errors, wrong service/chain identity, stale or incoherent finalized/indexed checkpoints, missing
  or non-Boolean emergency flags, and an absent history block-height contract. The probe uses only
  a synthetic non-account filter, records no returned account or transaction data, and performs no
  mutation. Persist its aggregate-only output in the CI runner's temporary directory and require
  the static release gate to consume that exact regular, non-symlink receipt within five minutes.
  The receipt must bind the capability response to health in the same GraphQL operation and must
  never be checked into source or accepted from an earlier release run.
- Both mobile PI clients must issue JSON POST only to the byte-exact consolidated production URL,
  bypass URL/intermediary caches, require exact HTTP 200, reject every redirect, non-JSON response,
  and unsupported content encoding, cap request and streamed response bytes, and admit strict
  UTF-8/RFC JSON before model decoding. Duplicate decoded object names—including escaped aliases
  and invalid surrogate escapes—must fail at every depth under independent depth/token work limits.
  Apply the same admission when reading or writing an offline response cache and to Android remote
  node/config payloads; an ambiguous or malformed live response must never replace the last
  qualified cache, and stale data is eligible only after an allowlisted transient transport or
  HTTP 408/429/5xx failure.
  Android must decode all seven PI health integer properties through one exact canonical unsigned
  decimal-lexeme adapter. It accepts either the deployed JSON integer token or a compatibility
  string without fixed-width or floating-point conversion, rejects booleans, exponents, signs,
  leading zeroes, non-scalars, and lexemes above 4,096 bytes, and always writes the app-private
  cache form as a JSON string.
  iOS must apply the same seven-field canonical unsigned admission to the raw `_health` response
  and cached `health` object before `JSONDecoder` can normalize a numeric token. It may then project
  an admitted value into its bounded `Int` health domain; an otherwise canonical value outside that
  range must fail closed without retaining the raw lexeme, while numeric and compatibility-string
  forms remain equivalent and cache round-trips remain deterministic.
  The Android/iOS strict-admission tests in this tree remain source-only until their guarded suites
  record terminal passes.
- The tester-release PI, image, and production Compose defaults must advertise
  `tairaDefaultVisible=true`. Android and iOS may apply a later remote value only while the user
  has never made an explicit persistent test-network visibility choice; the remote default must
  never overwrite that choice. An already-active never-chosen portfolio must consume every complete
  remote capability snapshot atomically and remove Taira before any new balance/history read when
  the default or Nexus kill switch becomes false; an interrupted legacy cache publication is not
  permission to expose the testnet.
- PI pagination, repeated cursors, GraphQL partial errors, numeric precision, cached catalog
  fallback, capability changes, and pending reconciliation must pass contract tests.
- Every PI connection used by production—including asset prices, pool APYs, and referral rewards—
  must walk stable ordered cursors under explicit page/row ceilings, reject cursor or item replay,
  total-count drift, and mixed cache/checkpoint provenance, and fail when the ceiling is reached
  rather than silently truncating. Referral identity, amount, and block height are required exact
  fields; missing values must never be synthesized as empty strings or zero, and reward heights
  must be bounded by the qualifying PI checkpoint. A filtered asset response must prove every
  returned ID belongs to the exact request before that page is eligible for offline caching.
  iOS market-cap liquidity likewise requires a canonical nonnegative integer PI quantity;
  missing, fractional, negative, or unrepresentable values are omitted/fail closed and are never
  displayed or cached as zero.
- PI chart fields must first decode and validate as canonical exact decimal tokens. Conversion to
  floating point is permitted only after that boundary for bounded chart rendering; balances,
  fees, quotes, slippage, claims, and transaction inputs never pass through floating point.
- SORA2 transaction preparation must obtain `system_accountNextIndex` only through the bounded
  reviewed runtime RPC endpoint, validate the selected account as an exact canonical prefix-69
  address before transport, admit only a raw canonical unsigned decimal JSON integer in the
  runtime `u32` range, and preserve it as `BigInteger` through `ExtrinsicBuilder.nonce`. Reject
  signs, fractions, exponents, quoted numbers, booleans, duplicate results, overflow, and any
  fixed-width or floating-point conversion before signing.
- Android offline PI replay must decode the cached GraphQL response with the same non-lenient,
  non-coercing type contract as the live response. A numeric JSON token must never be admitted into
  a string-only exact quantity field through the application-wide legacy `Json` configuration;
  reject and evict that cache entry instead.
- iOS fiat-price and APY adapters must retain the validated `PIQuantity` wire string in native
  models instead of passing through KMM `KotlinDouble`. Any bounded `Decimal` conversion is a
  presentation-only terminal adapter and must never feed balance, fee, quote, or signing logic.
- iOS legacy Polkaswap slippage and percentage presets must remain exact from entry through the
  signed call. Store slippage as validated `UInt16` basis points (1–1000), round desired-input
  minimum output down and desired-output maximum input up at chain precision, dual-read legacy
  canonical decimal context, and fail closed on missing, exponent-form, over-precision, zero, or
  out-of-range context. Balance/XOR-fee checks must reserve the desired-output maximum input, the
  confirmation must revalidate its exact quote/limit immediately before submission, and local
  history encoding must reproduce those exact submitted limits.
- iOS first-liquidity submissions must preserve the runtime call shape end to end: an existing
  registered but uninitialized pair signs an atomic `initialize_pool + deposit_liquidity` batch,
  a new pair signs `register + initialize_pool + deposit_liquidity`, fee estimation includes the
  same calls through the one canonical builder, and the pending-history overlay stores and decodes
  the exact ordered outer batch. Re-estimate that exact call immediately before signing, require a
  positive fresh fee no greater than the reviewed fee, and resolve that fee only from exactly one
  configured XOR fee asset. Load the live spendable balances before final qualification, then sign
  once and query the fee for those exact signed bytes under runtime 130. Submit that one-shot
  preparation without rebuilding nonce, head, metadata, or call bytes; aggregate the fee with any
  supplied XOR amount, and force a new review when the fee rises or cannot be qualified. Dismissing
  confirmation must revoke a queued signer before secret access, cancel the underlying queued RPC,
  and serialize cancellation with request-id publication so no handoff escapes either state.
  Async confirmation state and UIKit effects must resume on the main actor. Persist pending history from the
  captured exact call and raw XOR fee before transport, retain it for unknown submission, and never
  turn successful transport into failure because local history persistence failed. A duplicate
  durable hash must be treated as a prior possibly submitted attempt, retain its reconciliation
  overlay, and fail closed as unknown rather than being exposed as safe to retry. PI page age or
  pagination position must never expire a pending row; only an exact remote transaction-hash match
  may replace it. Every runtime
  fee component must decode from a nonempty `0x` hexadecimal
  quantity; a missing prefix, invalid digit, wrong wire type, or missing component must fail the
  whole estimate rather than contributing zero.
- iOS removal confirmation must load Demeter farm positions through an account-bound throwing
  path. A failed or missing farm-position query is not equivalent to an empty position list and
  must block LP removal preflight rather than overstating withdrawable pool tokens. Invalid exact
  amount conversion in any matched position must fail the same gate and must never become zero.
- Every iOS liquidity balance read, fee estimate, pool/farm preflight, signature, and pending-history
  record must remain bound to the same wallet-network facade account. An account selection change
  must fail before transport; pending history must use the bound signer address rather than a later
  global selection. KXOR is a distinct runtime asset and pair and must never be normalized to XOR
  in a liquidity read, validation, history, or mutation path. Fee estimates must not reuse a
  transaction-type-only cache across changing amounts, call shapes, runtime state, or accounts.
- Every asynchronous iOS supply/removal pair-state request must carry a unique generation token.
  Both success and error paths must verify that token and cancellation state before mutating the
  displayed pair, transaction type, fee eligibility, or confirmation state, including when an
  older cancelled request targets the same asset pair as its replacement.
- The iOS SORA2 operation factory must bridge submit and fee callbacks with asynchronous operations;
  production signing must use a nominal lifecycle-capable signer type and must not block an
  operation thread with an outer semaphore/timeout or force-cast the signer protocol.
- The Float-backed iOS Demeter slider is a presentation boundary only. Quantize it once to a
  validated 0–10,000 basis-point pool-share selection; calculate deposit/withdraw amounts and fees
  only from that integer selection and `Decimal`, and keep confirmation disabled until the exact
  network fee is loaded and the selected share produces a positive valid amount.
- Run operation-specific account, market, transaction-hash, and indexed-height validation before a
  live PI payload is eligible for the offline cache. Revalidate cached payloads against their
  attached qualification on every read and remove any entry that fails; a rejected live response
  must never overwrite the last qualified cache record.
- Every PI history row must bind exactly to the requested account through `address`, `dataFrom`,
  or `dataTo`, carry a canonical transaction hash, block height, and nonzero block hash at or below
  the qualified indexed checkpoint, and match `chain_getBlockHash` for that height. Deduplicate
  equal heights and bound canonical RPC verification to eight concurrent requests; any mismatch
  rejects the PI page while retaining the local pending overlay. The release-probe receipt must
  keep each individual history-qualification field fail-closed; changing only the aggregate
  `status` or `historyCheckpointContractStatus` string does not qualify history.
- `mobileConfig` qualification must record deployment of all five fields, strict Boolean typing,
  health-bound fetching, and fail-closed capability changes. Changing only an aggregate status
  string is not qualification.
- Every PI Polkamarkt market, snapshot, position, and trade row must carry its required nonnegative
  market/checkpoint coordinates. Snapshot pages must remain in canonical timestamp/ID order across
  cursor boundaries; account rows must embed the same qualified market identity; trade block and
  extrinsic hashes must be canonical and nonzero; and duplicate legacy/current quantity aliases
  must agree exactly before the row is accepted. Malformed rows must not replace a qualified cache
  entry, and a cross-page ordering failure must remain rejected when those individual pages are
  replayed from cache.
- Taira must pass funded receive/send/status/finality/history tests on the live testnet. An
  authorized low-value Minamoto canary must pass the equivalent production-mainnet sequence.
  `docs/modernization/qualification/taira-canary.json` and
  `docs/modernization/qualification/minamoto-canary.json` remain checked-in, non-qualifying schema
  fixtures; qualified receipts and their approval, PI, artifact-install, execution, and
  approval-consumption evidence stay in the access-controlled release controller.
- Both external Android receipts must satisfy the exact-key, bounded
  `sora-android-funded-nexus-canary-v4` contract documented in
  `docs/modernization/qualification/funded-nexus-canary-README.md`. They bind the real production
  AAB/application/version/build/source identity; runtime-130 metadata, types, and genesis; the
  complete canary-time feature-flag snapshot plus a fresh release-time recheck; concrete reviewed
  signer artifact and provenance; exact AAB upload certificate, generated APK-set digest, installed
  Play app-signing certificate, package/version identity; exact network,
  Torii, explorer, discriminant, and XOR alias; fresh dual-controlled operator approval; and
  domain-separated receive, send, fee, signing, submission, terminal, finality, balance, explorer,
  history, and restart evidence. Terminal/finality evidence must bind the actual committed height,
  require a finalized height at or above it, and prove the exact current network/chain UUID plus a
  canonical lowercase prefix-free finalized block hash. The exact network trust context's positive
  trusted-first height must be at or below that finalized height, and the finality stage must bind
  `liveBoundedSequentialStatefulSuccessorChainVerified: true` after a live, bounded, sequential,
  stateful verification of the successor chain. A fresh deterministic challenge must bind
  the canary run/candidate/network/chain/finalized height to distinct attestation and bundle
  evidence through the reviewed verifier, signed trust manifest, and network trust context;
  restart evidence must prove recovery had no signing or submission capability. Approval and review must
  verify against distinct pinned Ed25519
  authorities, and a signed append-only consumption receipt must prove the one-use approval was
  atomically reserved before signing, observed again before handoff, had no prior reservation, and
  produced exactly one non-ambiguous handoff. A status string or set of generic Booleans alone is
  not qualification. The reviewed finality-native receipt must prove exact ABI `21`, a clean
  reviewed release artifact, matching export inventories, verifier/manifest identity, nine distinct
  attestation/bundle/challenge/genesis/tip/signature/successor/finalized-projection KATs, each KAT's
  own exact `*KatPassed: true` flag, and every qualification flag. Qualified manifest, context, and
  native receipts must each use an actual empty JSON `blockingReasons` array, and every verifier or
  server source revision must be a nonzero 40-hex commit ID. Network-context review must precede native-canary review, which must precede
  manifest review and execution; low-value-policy review must precede dual-control approval.
- Keep `fundedTairaCanaryQualified` and `fundedMinamotoCanaryQualified` false in the pre-canary SDK
  readiness receipt. Only the two fully validated external v4 receipts and production admission
  establish those results. Admission and every rollout cohort must retain the exact Taira and
  Minamoto receipt hashes with the unchanged AAB, source, runtime, signing, and provenance identity.
- Run the hermetic v4 proof harness in the production GitHub qualification workflow and require its
  source contract from the static modernization verifier. The shared Jenkins Android pipeline has
  no reviewed pre-Gradle Node hook, so it must not claim this gate or smuggle a shell pipeline into
  its Gradle-task-only `testCmd`. The harness must cover successful synthetic Taira and Minamoto
  contracts plus exactly 33 fail-closed mutations for preclaimed results, current-chain or
  challenge drift, finalized height below committed height, trusted-first height after finality,
  missing live bounded sequential/stateful successor-chain proof, every independently false KAT pass
  flag, non-array blocker collections, all-zero source revisions, invalid native ABI, dirty DEBUG
  artifacts, aliased evidence/KAT identities, and retroactive trust or policy review. Synthetic
  harness evidence never qualifies the blocked source fixtures.
- Reject the pair even when both individual receipts validate if Taira and Minamoto share a receipt
  hash, candidate binding, finality binding, canary run ID, approval nonce, or attestation challenge.
  These are distinct funded executions, not two labels over one evidence bundle.
- Never put an account ID, address, transaction identifier (including a digest of one), device ID,
  operator name, key, phrase, seed, signed payload, or raw network response in either receipt.
  OS-secured signing, local/receipt hash equality, one handoff, status-only restart recovery, and no
  automatic retry must be proven by independently reviewed, privacy-redacted evidence digests.
  The equivalent iOS receipts live at
  `Fixtures/Modernization/taira-funded-canary.json` and
  `Fixtures/Modernization/minamoto-funded-canary.json`.
- Submission timeout/ambiguity tests must prove there is no automatic resubmission.
- After the signed Nexus hash is durably journaled, reacquire the wallet lifecycle lock immediately
  before Torii handoff and revalidate live Nexus/send flags, signer qualification, prepared
  network/account identity, selected account, and deletion state. A failure before handoff is
  definitively not submitted; once handoff begins, an unclassified failure remains ambiguous and
  is reconciled by exact hash without retry.
- A committed send must remain `COMMITTED_PENDING_RECONCILIATION` until a bounded, complete-fanout
  `count_mode=exact` account-transaction scan proves the successful exact hash and authority, then
  balance read-back and an exact network-scoped instruction-history match agree on hash, sender,
  recipient, asset, and decimal amount. Count drift, missing/partial fanout headers, duplicate
  hashes, repeated pages, empty intermediate pages, or scan-bound exhaustion must fail closed.
- Android must enqueue unique network-constrained status-only recovery at process startup and after
  a signed hash is journaled. Each worker run is batch-bounded, uses exponential backoff while any
  exact hash is nonterminal or lacks finality/history confirmation, and has no signing or submission
  capability. The post-journal kick must append behind an already-running empty pass, retry batches
  must rotate so failing old hashes cannot starve newer rows, and in-process `SIGNED` rows must not
  be queried until their active Torii handoff completes. Legacy read-only mode must reject insert,
  update, and delete on the pending overlay.

## SORA2 and Polkamarkt

- Runtime metadata must match ref `411dcdb70c5c00b21482a44d02334840d5f338c6`,
  `spec_version=130`, `transaction_version=130`, and the checked-in SHA-256 manifests.
- Runtime-130 mutations must additionally bind the active snapshot to the exact reviewed
  `types_scalecodec_mobile.json` bytes and checked-in type-definition manifest (SHA-256
  `e87760d7a566d1b1b3d21a1e76ad70990fd54e14e6af3ba27dd4440461063601`). Metadata equality
  alone is insufficient to authorize a trade or claim.
- Every ordinary SORA2 mutation must resolve runtime version and metadata at one explicit
  finalized block and pass that single immutable context (snapshot, spec/transaction version,
  genesis, finalized hash, metadata hash, and type hash) into the extrinsic builder. Runtime
  upgrades and downgrades are both drift; once drift is observed, refresh or validation failure
  must fail closed instead of combining cached call indices/types with a newer live version.
- Ordinary transfer review must estimate the actual destination/asset/amount call, never a fixed
  one-unit sample. Preparation signs that exact call once; immediately before one-shot transport,
  query the fee again from those same signed bytes and require byte-qualified raw XOR fee equality
  with the reviewed fee. Re-read spendable XOR and sent-asset balances around signing, aggregate
  amount plus fee when XOR is sent, and fail before transport on drift, missing/duplicate balance,
  or insufficiency. Persist the exact local hash and fee before handoff and never rebuild or retry.
- Runtime identity and metadata authority must use bounded JSON POST at the exact reviewed
  `https://ws.mof.sora.org` endpoint from the checked-in metadata manifest. Each response must
  reject redirects, oversize/malformed UTF-8, non-JSON media, malformed or ambiguous JSON-RPC
  envelopes, mismatched IDs, remote errors, noncanonical integer versions, and noncanonical block
  hashes. Metadata JSON is capped at 2 MiB (reviewed runtime-130 is about 1.24 MiB on wire), and a
  strict streaming envelope/duplicate-field reader must mediate all structured materialization;
  runtime-version structured depth and token counts are independently bounded.
  `state_getRuntimeVersion` and `state_getMetadata` must both be explicitly pinned to the same
  admitted `chain_getFinalizedHead`; no runtime-authority byte may come from the unbounded
  nv-websocket receive path. Read-only startup may use a previously hash-validated installed
  generation only when live identity is unavailable; mutation authority has no such fallback.
- The exact same bounded client is the sole signed submission transport for ordinary SORA2 and
  Polkamarkt. It must send one fixed JSON body, reject every redirect without replaying it, require
  exact HTTP 200 plus JSON response media, compare the returned hash with the locally derived
  extrinsic hash, and expose no alternate node or WebSocket fallback.
- Fixed HTTPS authority may authorize transport only while the actual connected selected socket
  is the exact reviewed `wss://ws.mof.sora.org[/]` counterpart. Alias hosts, alternate ports,
  credentials, queries, fragments, mof2/mof3, and custom nodes fail closed. Selected-node switches
  must take `WalletMutationCoordinator`, the same process-wide boundary held continuously by
  combined generic submit flows and qualified Polkamarkt preparation/signing/handoff, and final
  submit/submit-and-watch must hold a connection lease that defers socket switching until the
  handoff reaches a terminal result.
  Focused bounded-runtime-RPC, endpoint-admission, and pure lease/deferred-switch coordinator test
  source is present. The latter covers requested-but-not-connected and unreviewed rejection, exact
  connected admission, switch deferral, admission revocation before the deferred socket callback,
  and idempotent close. The bounded-RPC source also covers exact `u32` account-nonce preservation,
  malformed/overflow/cross-prefix rejection before signing, and the exact `BigInteger` retained by
  the real extrinsic builder. None is execution evidence until the guarded affected unit suite
  records a terminal pass for the current tree.
- Every `PreparedExtrinsic`, including standalone generic and Polkamarkt preparation, now has an
  internal construction boundary that retains the exact immutable runtime context returned with
  its signing builder. Generic signing explicitly fetches one context and passes it into a
  context-bound builder instead of hiding a second fetch; final Polkamarkt fee behavior and the
  opaque qualified Polkamarkt context remain unchanged.
- Generic prepared submission, combined ordinary one-shot submission, combined wait/watch paths,
  and Polkamarkt prepared submission now share one definitive pre-transport gate. After caller
  policy validation and the final wallet check, it fetches a fresh bounded finalized context and
  compares genesis, spec/transaction version, metadata hash, and type hash to the retained signing
  context. Canonical finalized-head advance is allowed; every identity drift fails before
  `SubstrateCalls`, while errors after handoff retain unknown-submission classification. Focused
  drift/order/finalized-advance/direct-submit test source is present but remains unexecuted pending
  a guarded affected unit run.
- Referral set-referrer, bond, and unbond mutations must wait for the canonical `finalized`
  subscription status. An `inBlock` notification is reorgable and must never be projected as a
  finalized execution result merely because its block currently exposes success/failure events.
  The shared watch primitive itself defaults to `finalized`, so future direct callers cannot
  silently reintroduce the same downgrade by omitting an explicit status key.
- Persisted runtime generations must bind their metadata and type hashes to their canonical
  genesis hash. Production mutations must reject any genesis other than the reviewed SORA2
  mainnet genesis before encoding or signing; development-only flavors may use their explicitly
  observed genesis but may never reuse a generation from another chain.
- Runtime metadata—not constants—must resolve the Polkamarkt pallet and non-contiguous call
  discriminants.
- The final Polkamarkt pre-sign runtime gate must return an opaque qualified context and pass that
  exact context through `ExtrinsicManager.calcPolkamarktFee` for the final authoritative fee and
  then through `ExtrinsicManager.preparePolkamarktExtrinsic` into the builder that encodes and
  signs the trade or claim. Qualifying context A and letting either final builder independently
  fetch context B is a release-blocking failure, even when both report runtime 130. Preview fees
  may continue to use the ordinary read path because they never authorize a signature.
- The exact canonical web revision must contain the Polkamarkt implementation and shared fixtures.
  Generic Polkaswap ticket semantics alone do not satisfy this gate.
- Keep `reviewedWebAndRuntimeReceiptQualified=false` and `reviewedReceipt=null` until the exact
  v1 full-extrinsic qualification contract has three independent receipts: the pinned web/runtime
  reference, Android's production encoder, and iOS's production encoder. Each receipt must resolve
  the pallet and all five call discriminants from metadata hash
  `2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf`, then retain the full
  call bytes, raw signing payload, threshold-adjusted signing prehash, signature, signed extrinsic,
  extrinsic hash, and metadata-decoded semantic projection for buy, sell, single trader claim,
  batch trader claim, and creator-fee claim. The reference/Android/iOS call bytes, signing prehash,
  and decoded-projection hash must match exactly. Sr25519 signature bytes may differ because the
  signing implementation can randomize them, but every retained signature must independently
  verify over the shared prehash and every signed extrinsic must round-trip through the reviewed
  metadata. Never force byte equality by substituting a fake signature or a hardcoded pallet/call
  index.
- Produce the composite only with
  `scripts/qualify-polkamarkt-extrinsic-receipts.mjs`. It accepts bounded, stable, non-symlink
  strict-JSON inputs; rejects private signing fields and production identifiers; recomputes the
  exact five-vector call/prehash/extrinsic/projection hashes and cross-platform byte parity; and
  creates a mode-`0600` output exclusively rather than overwriting a source fixture. Unknown nested
  qualification-contract fields and normalized mnemonic/phrase/seed/secret/private-key field names
  must fail closed; a failed partial output may be removed only after its device/inode is rechecked.
  It does not itself implement sr25519 verification or metadata decoding, so each guarded
  reference/Android/iOS native verifier must prove those facts and sign an exact proof statement
  bound to the candidate, checked-in source tree/generator/verifier identities, runtime metadata,
  signing context, and vectors. The merger verifies each proof's Ed25519 signature against the
  checked-in verifier-key SHA-256 before accepting the independent review. Qualification
  additionally requires an independent Ed25519 review
  over the exact input receipt hashes, with the review key SHA-256 supplied by the protected
  `POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256` environment. Preserve the raw reviewed candidate and
  review receipts inside the resulting composite so offline review can reproduce the signed
  decision instead of trusting a promoted boolean. Independently review the exact composite bytes
  and protect their SHA-256 outside the repository as
  `POLKAMARKT_EXTRINSIC_QUALIFIED_FIXTURE_SHA256`; Release must compare this pin with the installed
  fixture and the qualification workflow must never calculate the expected value from its own
  checkout. The Release verifier must also run the merger's `--validate-qualified` mode to
  reconstruct the blocked input, revalidate every embedded candidate/native proof and the review
  signature, rebuild the expected receipt, and compare the exact transition. The whole-file pin is
  an additional independent approval boundary; the Release verifier does not claim to perform
  sr25519 or runtime-metadata decoding itself. Supply the same protected
  pin to Android and iOS. Android production CI must check out the protected exact iOS source
  revision, iOS Release CI must supply the protected exact Android source checkout, and both gates
  must compare the stable sibling fixture byte-for-byte. Configure the Android side with
  `POLKAMARKT_IOS_REPOSITORY`, `POLKAMARKT_IOS_SOURCE_REVISION`, and the read-only
  `POLKAMARKT_IOS_READ_TOKEN`; configure iOS with `POLKAMARKT_ANDROID_SOURCE_ROOT`,
  `POLKAMARKT_ANDROID_SOURCE_REVISION`, `POLKAMARKT_ANDROID_FIXTURE_PATH`, and an absolute reviewed
  Node runtime plus its independent hash in `POLKAMARKT_NODE_BINARY` and
  `POLKAMARKT_NODE_BINARY_SHA256`. Missing sibling or replay-runtime provenance fails closed.
  The producer revisions embedded in the reviewed proofs necessarily precede the fixture-promotion
  commits. Fetch those commit objects and require a clean diff from producer revision to current
  candidate across the production source/dependency/build pathspecs on both platforms; do not use
  the impossible self-referential rule that an embedded producer hash equal its containing commit.
- Treat the candidate receipt's 512 KiB limit as the aggregate evidence-file cap. Per-call,
  payload, and extrinsic limits are additional bounds and need not be independently reachable;
  ordinary Polkamarkt evidence must remain well below the aggregate cap.
- Run `node scripts/test-polkamarkt-extrinsic-qualification.mjs` under the guarded owner before
  accepting the merger contract. The installed genuinely qualified fixture is its only positive
  path; public synthetic receipts are used solely for fail-closed mutations and must never produce
  a qualified fixture. The production qualification workflow must retain this regression gate.
  This harness exercises the merger boundary only; it cannot replace producer-native sr25519
  signature verification or runtime-metadata decode/encode negative tests.
- Quote, slippage, fee, balance, status, claimability, metadata identity, and selected account must
  be revalidated before every signature.
- Ordinary SORA2 signing and every Polkamarkt trade/claim must hold or safely reuse the same wallet
  lifecycle lease from final selected-account, active-snapshot, secret/public-key, runtime,
  quote/fee/claimability, balance, and live-flag checks through signature verification and durable
  signed-hash journaling. Release the lease before RPC transport, never after an unjournaled
  submission, and prove account-switch/deletion races cannot sign for a stale wallet.
- Immediately before the one-shot SORA2 RPC transport opens, reacquire the lifecycle lease and
  revalidate the prepared hash, selected wallet, active migration/deletion state, live mutation
  flags, and runtime identity. Compare the fresh context with the signing context on genesis,
  spec/transaction version, metadata hash, and type hash. Both finalized hashes must be canonical,
  but do not require them to be equal because a later finalized head may legitimately advance.
  Pre-transport callback or cancellation failures must be marked definitively not submitted with
  zero RPC calls; cancellation after transport handoff remains
  ambiguous. Task cancellation must reach the exact underlying RPC operation for both liquidity
  and Polkamarkt mutations. Persist that classification in a non-cancellable journal update so a disappearing UI
  scope cannot leave a misleading `SIGNED` record.
- Bind every Polkamarkt sell-position and claimability response to the exact selected account and
  market, require a claimable terminal market state and a positive applicable payout, and reject
  empty, oversized, negative, or duplicate batch claims before signing.

## Iroha and Norito dependencies

- Run the static release verifier before any Gradle task. The production qualification workflow
  must stop before dependency resolution when wrapper, repository, verification-metadata, lock,
  credential-rotation, or SDK provenance is incomplete.
- Run `node scripts/verify-production-modernization.mjs --dependency-preflight` as a dedicated
  no-Gradle step after checkout and before the workflow's first `./gradlew`. It must validate the
  exact repository filters and metadata sources, the strict source-provenance schema, every
  materialized module/file hash, publisher sidecars, the symlink-free whole-tree manifest, strict
  verification metadata, and the complete materialized production lock inventory. `STABLE`
  inventory status is not production approval: any pending independent review or credential
  incident must still make this preflight fail closed.
- Pin every GitHub Actions dependency to a reviewed full commit SHA, use read-only workflow
  permissions, and disable checkout credential persistence. Mutable action tags are a release
  blocker even when their displayed major version is unchanged. Keep the reviewed action/version/SHA
  matrix in `config/gradle-dependency-provenance.json`; the release verifier must reject any workflow
  reference that is missing from or differs from that matrix.
- Pin the Android and iOS Jenkins shared libraries to a reviewed full commit SHA. The unversioned
  `jenkins-library` selector is mutable and cannot drive production qualification.
- Use only the official Gradle 9.5.1 binary distribution SHA-256 and matching wrapper JAR SHA-256.
  The wrapper bytes are now pinned to the official 9.5.1 identity; any drift remains a hard
  pre-Gradle failure.
- Require reviewed strict Gradle dependency-verification metadata with a SHA-256 for every
  resolved plugin, JAR, AAR, POM, and module metadata input. Reject trusted-artifact bypasses,
  local/file/flat-directory repositories, composite substitutions, dynamic/changing versions,
  missing artifacts, and any checksum drift.
- Resolve the eleven required Soramitsu/Polkaj/PayWings modules only from the exact checked-in,
  manifest-bound `vendor/soramitsu-maven` repository described in
  `docs/modernization/android-dependency-provenance.md`. Its filter must list exact modules (never a
  broad group), and its metadata sources must remain `gradleMetadata()` plus `mavenPom()` without
  artifact-only fallback. JitPack remains exclusive to its two exact modules; the dead Soramitsu
  Nexus and former arbitrary PayWings repository URL must not return.
- Generate and independently review strict lock state for every `productionRelease`
  configuration. Materialized lockfiles and hashes prove stable resolution input, but do not become
  reviewed merely because Gradle generated them or the preflight parser accepts their shape.
- The plaintext project `authToken` has been removed, but removal does not revoke a leaked
  credential. Confirm revocation and replacement outside the repository, then record only
  non-secret rotation evidence before marking dependency provenance qualified.
- Repository booleans never authenticate those reviews. Before dependency preflight, admit one
  canonical external `sora-android-dependency-signing-review-v1` manifest carrying distinct
  SHA-256/P-256 producer and independent-reviewer signatures. Require owner-only stable inputs,
  fixed low-S IEEE-P1363 signatures, protected SPKI pins, exact protected sequence/source/contract pins,
  freshness, and equality with the current dependency, lock, vendor, verification-metadata,
  credential-incident, and public signing-certificate projections. Only blocked templates belong
  in source; the admission authorizes dependency/signing qualification but not artifact signing,
  release, or production mutation.
- Treat the current Android release archive as unreviewed. Its SHA-256 and size differ from the
  previously reviewed Fearless archive, and its GitHub release URL is mutable. Review the exact
  current bytes and every materialized runtime artifact before changing
  `freshArtifactReviewRequired` or wiring a production dependency.
- Never promote native-canary booleans by themselves. Android and iOS must each provide the exact
  fixed-path, regular, non-symlink qualification receipt named by the platform readiness manifest,
  cap it at 65,536 bytes, admit only the exact reviewed root and nested key sets, pin it by SHA-256
  in release configuration, and bind it to the reviewed source, adapter, artifact,
  whole-tree/content-manifest, and native-slice identities. The receipt must
  prove observed ABI 21 and equal independently reviewed/platform hashes for the required export
  inventory, transaction bytes, signing prehash, signed envelope, and decode projection. A dirty
  local debug artifact or an unpinned receipt is reference input only and cannot replace the
  unavailable signer. Until promotion is fully qualified, static gates must also prove that each
  unavailable signer reports itself unqualified and throws before quote, finalized-height, or
  signing work; merely retaining the placeholder type name is insufficient.
- Require reviewed source identity, licenses/notices, SBOM, build provenance, attestation, and
  source-to-binary correspondence. Release builds must not fetch the mutable archive, use
  `mavenLocal`, accept dynamic/changing versions, or substitute a local SDK.
- Do not enable the Android signer while the fixed-`u64` transaction-hasher defect or the
  provider-owned, non-destroyable Ed25519 private-key copy remains unreviewed. Device and R8
  qualification, authoritative XOR precision/fee mapping, local/receipt hash equality,
  deployed-node compatibility, and funded Taira/Minamoto canaries are mandatory.

## Reproducible Android candidate package

- Supply the authorized production version code as canonical positive `CI_BUILD_ID`; the release
  task must reject the historical source fallback. Supply the retained keystore only through an
  absolute, canonical, owner-only regular path with no symbolic-link traversal. Passwords and
  private-key material are never written to evidence.
- Build the signed production AAB, signed minified APK, and R8 mapping twice from the same exact
  commit. The second build uses a distinct `git clone --no-local` checkout and distinct
  `GRADLE_USER_HOME`. Require byte-for-byte equality for all three outputs; matching task success
  alone is insufficient.
- Extract exactly one AAB signer certificate with the pinned JDK `keytool`, require its DER
  SHA-256 to equal the independently reviewed retained upload-certificate identity, and run strict
  JAR verification against a temporary public-certificate-only trust store so the normal Android
  self-signed upload certificate does not turn trust-chain warnings into either a false success or
  an impossible gate. Run `apksigner verify --print-certs` for each APK, admit exactly one APK
  signer, and require the same upload-certificate SHA-256. The separately reviewed Play App Signing
  certificate and continuity receipt remain mandatory. Re-hash the AAB, APK, and signing-identity
  configuration after all external verification tools finish and reject any input that changed
  during verification.
- Run `scripts/test-android-candidate-signing-verification-v1.mjs` and
  `scripts/test-android-qualified-candidate-package-v1.mjs`, plus the retained-migration controller
  envelope tests, before any build. They reject stale or
  duplicate-key evidence, symbolic or hard-linked inputs, multiple signers, certificate drift,
  unequal artifacts, unqualified dependency/signing/Iroha state, and receipt/artifact mismatch.
- Seal both copies of the AAB, APK, and R8 mapping; both build logs; both non-authorizing signing
  verification receipts; the fresh PI receipt; the exact v3 production admission; source revision;
  the exact retained-migration controller envelope and its non-authorizing extraction receipt;
  the exact dual-signed Taira deployment manifest, both detached signatures, both pinned public
  keys, and its non-authorizing build-binding admission receipt;
  the exact external dependency/signing review manifest, both P-256 signatures, both pinned public
  keys, and its non-authorizing admission receipt;
  the exact funded-canary controller ustar and its non-authorizing extraction receipt; and
  `candidate-package-manifest.json` in the immutable workflow artifact. The package manifest
  binds the reviewed dependency inventory, verification metadata, vendor manifests, signing
  identity, Iroha pin, workflow, and every packaged artifact by SHA-256 and byte count. It explicitly
  authorizes neither release nor production mutation; only the exact qualified production admission
  and subsequent protected rollout receipt can do that.
- Every cohort run must validate the downloaded package before contacting the rollout controller.
  Require the exact 30-file inventory, manifest self-binding, distinct regular files, both signing
  receipts, byte-equal reproduction artifacts, exact admission/PI/source identities, and current
  checkout dependency/workflow hashes. Revalidate both exact controller payloads and their
  extraction-receipt projections, both Taira manifest signatures, and the external dependency/signing
  manifest's two P-256 signatures against independently protected pins, sequence, source, and
  contract. Because artifact transport does not preserve Unix permissions as authority, require
  exactly 30 top-level regular files and normalize that downloaded directory/files to owner-only
  modes before parsing or signature verification. Reject an extra file, missing log,
  changed controller payload, altered reproduction, stale source revision, or manifest/source
  drift even when the primary AAB alone still hashes.

## Rollout

- Leave `PRODUCTION_ROLLOUT_TARGET_PERCENT` unset for ordinary qualification. Initial 1% rollout is
  not implicit: first complete candidate mode and retain its immutable AAB, stable v3 admission,
  source revision, and workflow run ID. Set the target to exactly `1` only through the protected
  production environment and provide that candidate run ID. Later targets are exactly `5`, `25`,
  or `100`; every target must download the same immutable candidate and obtain the dual-signed
  aggregate-only current evidence from the protected rollout controller over the independently
  pinned TLS SPKI, then retrieve every prior receipt through that same pin and its exact signed
  SHA-256 link. The candidate admission and rollout gate must also
  verify the successful first-attempt GitHub workflow run, workflow-file hash, repository/head SHA,
  and sole non-expired artifact API identity/digest. Never rebuild the candidate between cohorts.
  Every target is validated against the exact AAB, reviewed runtime
  metadata bytes, fresh live PI capability/checkpoint receipt, qualified independently hash-pinned
  rollout trust root, and adjacent fail-closed templates.
- The Android candidate admission must be `sora-android-production-admission-v3`: it binds all
  production qualifications but excludes moving PI hashes and epochs. Put the current PI receipt,
  all-enabled capability projection, and SORA2/Minamoto/Taira genesis, finalized heights, and block
  hashes in the signed per-gate checkpoint. Require byte-identical shared cohort prefixes, require
  each new cohort to start after the preceding signed authorization, reject same-height hash
  changes, and bind the Google Play release/edit ID plus provider-receipt hash and observation time.
- Reject every v2 admission, controller request, rollout receipt, and prior-chain link at the v3
  boundary. Do not mix generations or carry a v2 cohort forward: restart the staged rollout with a
  new v3 1% receipt and a fresh 48-hour dwell. Protected controller v3 deployment and compatibility
  approval remains a hard blocker, as does the separately reviewed post-AAB funded-canary
  candidate-evidence handoff; source automation must not invent either authority.
- Run `scripts/test-production-rollout-v3-contract.mjs` in production qualification. It must exercise
  the shared operational envelope predicates for admission, current and prior rollout receipts, the
  prior-link cursor, and the controller request; exact v3 must pass, while complete v2 and both mixed
  schema/contract-ID combinations must fail in every context.
- A qualified trust-root replacement must remain accepted by ordinary source/build gates while the
  rollout gate separately requires its protected exact SHA-256 and two distinct reviewed Ed25519
  authorities. Missing controller origin/token, current evidence, any prior-chain or GitHub
  provenance receipt, or candidate artifact is a hard failure, never a successful no-op.
- Android production CI must run the common, PI API/implementation, SORA2 substrate, wallet,
  Polkamarkt, and app unit-contract suites after the static provenance gate, then verify the
  retained production signing identity and build `bundleProductionRelease` with the authorized CI
  keystore. iOS CI must keep application tests enabled, and every Release build must execute the
  modernization dependency verifier before becoming a candidate.
- Enable qualified Minamoto sends and Polkamarkt mutations only after every prior gate is green.
- Roll out 1% → 5% → 25% → 100%, with at least 48 hours at each cohort.
- Halt on any confirmed missing account, address/signature mismatch, cross-network routing error,
  or terminal transaction failure rate above 1% (excluding cancellation/insufficient funds).
- Record only migration result classes, capability/runtime versions, and transaction state/error
  classes. Never record phrases, seeds, private keys, addresses, or raw signed payloads.
