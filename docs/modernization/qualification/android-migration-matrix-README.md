# Android wallet-migration qualification receipt

`android-migration-matrix.json` is release evidence, not configuration or a unit-test fixture.
Do not create it from source inspection, inferred counters, logs from a partially passing run, or
an older source tree. Publish it only after an independently reviewed qualification run over every
retained production Room snapshot and the exact current release candidate.

The receipt uses schema version 7 and contains aggregate evidence only. Schema 7 authenticates the
retained-device run, four qualified aggregate artifacts, one explicitly non-authorizing raw-run
inventory artifact, the exact release source/app build, and an independent review. Schema 6 and all
earlier receipts, plus evidence-manifest schema 1 and earlier, are operationally rejected. It must never
contain a wallet or account identifier, address, device identifier, phrase, seed, private or public
key, signature, signed payload, ciphertext, wrapped key, Keystore material, or a per-wallet row.

The checked-in `android-migration-matrix.blocked.json`,
`android-migration-evidence.blocked.json`, and `android-migration-trust.blocked.json` files are
shape-only, fail-closed templates. They contain no authority, run, key, artifact, or qualification
claim. The qualified receipt, evidence manifest, trust root, detached signatures, public keys, and
all five evidence artifacts remain deliberately absent until a retained-device authority supplies
them. Do not rename a blocked template or populate it from source inspection.

The GitHub-hosted release workflow now has a source-side protected handoff. It uploads the exact
candidate AAB once to an independently configured, TLS-SPKI-pinned controller and accepts only the
exact `sora-android-migration-controller-envelope-v1` response. The strict envelope contains 13
base64-encoded files: the qualified receipt, evidence manifest, trust root, five aggregate-only
artifacts, three detached signatures, and two public keys. It binds the candidate AAB hash/size,
source revision, protected run UUID, monotonic sequence, and app-build identity. The extractor
rejects duplicate/unknown/missing fields, non-canonical base64, duplicate-key or invalid JSON,
stale v6/v1 wire contracts, internal artifact/hash drift, aliases, unsafe modes, and changed input.
It creates a fresh owner-only output root and emits a non-authorizing extraction receipt.

The controller deployment, TLS pin, bearer token, retained snapshots/devices, producer/reviewer
keys, and protected identity pins are still external authority inputs; repository code cannot
create or approve them. Committing a supposedly final receipt remains invalid because its source
identity would be self-referential. The original envelope and extraction receipt are sealed into
the immutable candidate package so every later cohort revalidates the same delivery bytes.

`scripts/verify-android-migration-qualification.mjs --verify-qualified` is the standalone
authenticator. Release admission calls the same standard-library module. Audit mode only lints the
blocked templates; release mode requires an authenticated v7 receipt and v2 evidence manifest. The protected production
environment must provide the exact `PRODUCTION_CANDIDATE_SOURCE_REVISION`, plus:

- `ANDROID_MIGRATION_QUALIFICATION_RUN_ID` and a positive, monotonically advanced
  `ANDROID_MIGRATION_QUALIFICATION_SEQUENCE_NUMBER`;
- `ANDROID_MIGRATION_QUALIFICATION_APP_BUILD_IDENTITY_SHA256` and
  `ANDROID_MIGRATION_QUALIFICATION_TRUST_SHA256`;
- `ANDROID_MIGRATION_QUALIFICATION_CONTRACT_SHA256` when invoking the standalone verifier directly
  (the broad release verifier instead computes and passes the exact current-source contract hash);
- independently pinned producer/reviewer key hashes in
  `ANDROID_MIGRATION_QUALIFICATION_DEVICE_PRODUCER_PUBLIC_KEY_SHA256` and
  `ANDROID_MIGRATION_QUALIFICATION_REVIEWER_PUBLIC_KEY_SHA256`;
- the controller origin/TLS pin and protected bearer token. After exact envelope extraction, the
  workflow sets absolute runner-local `ANDROID_MIGRATION_QUALIFICATION_*_PATH` values for the
  receipt signature, both evidence signatures, and both public keys.

The producer and reviewer are distinct P-256 authorities with short role-local IDs prefixed
`android-migration-device-producer-` and `android-migration-independent-reviewer-`. The independent
reviewer signs the exact receipt bytes. Both authorities sign the exact evidence-manifest bytes.
Key IDs that resemble hashes or UUIDs are rejected, and checkout-provided key hashes cannot replace
the independently protected pins. Distinct PEM hashes are insufficient: the validator canonicalizes
both public keys to SPKI DER and rejects the same P-256 public point under different encodings.

The signed manifest fixes and hashes five regular, non-symlink JSON artifacts:

- `android-migration-retained-snapshot-manifest.json`;
- `android-migration-test-results.json`;
- `android-migration-encrypted-storage-evidence.json`;
- `android-migration-device-execution-evidence.json`;
- `android-migration-raw-execution-evidence.json`.

Each public artifact has a strict aggregate-only schema and binds the same run UUID, protected monotonic
sequence, source revision, app-build identity, device classes, OS builds, and production time. The
receipt field `aggregateTestResultSha256` is the exact SHA-256 of the fixed
`android-migration-test-results.json` artifact; that artifact is covered by both detached signatures
over the evidence manifest and is not a source-inspected or inferred raw bundle identity. The
validator verifies the trust/key pins and all three detached signatures before opening those
artifacts, then opens and hashes every artifact, validates its aggregate claims against the receipt,
checks Room 74–77 bytes, and reopens/re-hashes all fixed inputs before returning the authenticated
receipt hash. Inputs are bounded and symlinks are rejected. Qualification must finish within 48
hours, independent review and qualification transitions are each bounded to 24 hours, and a receipt
expires after seven days.

`scripts/collect-android-migration-raw-evidence.mjs` implements the source-side raw-run inventory
contract. It accepts only a canonical absolute protected run root containing
`raw-run-index.json`, verifies every indexed input as a stable bounded regular non-symlink file,
requires method-inventory, report, and transcript evidence for all six named suites plus at least
two APK-identity artifacts, rejects unindexed files, aliases, hard links, special files, and any
group/world-accessible entry, then re-inventories and re-hashes the complete owner-only tree before
emitting aggregate hashes and counts to standard output. The emitted
artifact deliberately carries `status: collected-unreviewed`, three false authorization fields,
and a blocking warning. It never emits raw paths or contents and cannot authorize qualification,
release, or mutation by itself. The distinct reviewer-signed v7 receipt and producer/reviewer-signed
v2 evidence manifest must bind its exact hash, raw-index hash, and raw-result bundle hash.

The collector closes the repository contract, not the external execution gap. A protected immutable
producer namespace must retain the indexed bytes for independent review, and the controller handoff
must deliver the reviewed artifact and signatures to the fresh runner. A successful shell
exit status, source-inspected count, or collector output without those signed bindings cannot
substitute for execution evidence.

Required execution evidence includes:

- Every retained Room schema from 58 through 76 migrated to target schema 77 for both exact
  single-account and exact multi-account cohorts, plus the direct 73-to-74, 74-to-75, 75-to-76,
  76-to-77, and current 77-to-77 safety contracts. The 76-to-77 copy must retain every pending
  transaction field, set only the new chain ID column to null, and never infer a current chain for
  a historical row.
- All seven isolated production-path `MigrationManager` cohorts and all five encrypted-storage
  restart phases, executed through their exact qualification packages and runners. Record
  `productionPathCohortCount` as exactly 7, independently of the one structural current-schema
  snapshot cohort. The production-path runner must prove both APK application IDs from their built
  manifests and reject either APK if it declares a shared Android UID before either package is
  installed; runtime-only package checks are too late. Each
  cohort must load the selected account and `REGISTRATION_FINISHED` through the real
  `PrefsUserDatasource` and `UserRepositoryImpl`; a mocked migration-lock snapshot is not
  production-path evidence. Its exported Room 76 source must retain the exact staged
  `UNKNOWN`/`PENDING_VERIFICATION` identity and disabled SORA2 rows that the production 73-to-74
  migration created for each account; an accounts-only v76 source does not qualify. Join the
  repository's constructor initialization and cancel/join its
  controlled application scope before closing the file-backed Room database.
- All six successful secret-source cohorts: 12-word mnemonic, 24-word mnemonic, retained 15-word
  mnemonic, raw seed, retained keypair-only legacy secret, and explicit watch-only. The retained
  keypair-only cohort must remain `LEGACY_SECRET`, bind its stored public key to the unchanged
  SORA2 address, sign and verify a fixed challenge with the retained keypair, preserve the exact
  encrypted private/public/nonce fields, retain blank mnemonic and seed fields, and create no
  Minamoto or Taira child. The retained 15-word cohort must remain
  `MNEMONIC_UNSUPPORTED`, produce only the existing SORA2 network account, reject public recovery
  import, preserve ciphertext and signing parity, and create no Minamoto or Taira child. Record
  `successfulSecretSourceCohortCount` as exactly 6, `legacySecretQualified` as true only after the
  keypair-only cohort passes, and `retainedFifteenWordMnemonicQualified` as true only after its
  cohort passes. Current-schema admission must also reject every incomplete private/public/nonce
  tuple and every conflict between the verified source label and mnemonic, raw-seed, keypair, or
  watch-only storage evidence; a positive source marker alone is not coherence.
- Exact executed method counts for `WalletIdentityMigration75Test`, `WalletUpgradeBackupTest`,
  `MigrationManagerSafetyTest`, `MigrationManagerProductionPathQualificationTest`,
  `EncryptedWalletMigrationStorageTest`, and `Sora2AddressCodecTest`. The verifier derives the
  expected values from the bound source tree; both failure counters must be zero.
- `WalletUpgradeBackupTest` must include the production no-replace publication boundary: a
  pre-existing recovery ZIP final name is byte-for-byte unchanged, an incomplete published backup
  without `.complete` blocks preparation, and database/settings mutations at the final
  pre-activation seam fail with `SOURCE_CHANGED` while the verified prior snapshot remains
  retained. This includes replacing a wrapped-key secret with another nonempty value on the
  already-snapshotted current-schema shortcut; boolean key presence is insufficient. The bound
  implementation must use API-26-compatible exclusive `Os.mkdir` and
  `O_CREAT|O_EXCL|O_NOFOLLOW` file creation, recursively fsync the tree, copy and verify
  `.complete` last, and contain no rename or blind published-archive deletion fallback. The
  executed boundary must also prove distinct staging/published dev+inode ownership, exact
  size/SHA-256 inventory parity, and a single link for every staged and published file; reject a
  published backup with any extra hard link, leave no exact-owned SQLite validation residue on
  success, and contain no
  production `deleteRecursively` cleanup. Validation cleanup is bounded to exclusively created,
  no-follow expected files with parent and leaf dev/inode rechecks; API-26 path removal is not
  claimed to be atomic against a hostile same-UID component.
- A positive count of retained release-produced database/settings snapshots, the independently
  reviewed `retainedReleaseSnapshotManifestSha256`, `aggregateTestResultSha256`,
  `rawExecutionEvidenceSha256`, `rawRunIndexSha256`, and `rawResultBundleSha256` covering every
  named suite. These hashes are evidence identities, not permission to omit the underlying
  artifacts from independent review and retention.
- Exact SHA-256 identities for the canonical generated Room 74, 75, 76, and 77 schemas and every true
  aggregate parity/qualification field required by `verify-production-modernization.mjs`.

`qualificationContractSha256` binds the ordered path and bytes of the full migration,
cryptography, encrypted-storage, database, build, dependency-provenance, workflow, runner,
checklist, v7/v2 templates/authenticator, non-authorizing raw collector and contract tests,
retained-schema, and legacy Iroha-claim closure listed by the verifier. The
claim closure includes its wallet APIs, preferences, interactor/repository implementations and
tests, plus the purpose-tagged durable SORA2 submission/recovery boundary. Any bound source or
dependency-evidence change invalidates the receipt and requires a complete rerun. Never copy a
receipt forward between release candidates.
