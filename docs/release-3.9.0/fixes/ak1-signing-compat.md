# ak1-signing-compat: what could sign before must sign after

Audit items: **A1** (64-byte cached seed), **A3** (phrase stored with commas), the dangerous
"remove it from this phone" advice of **A2**, the "no in-app exit" finding for a key set without an
account row (`verdicts.md`, SEED64), the journal and welcome sheet that reported success for
read-only accounts (SEED64-DYN), and the 128-character "raw seed" (SEED64, low).

It also carries the six binding amendments of the 11 October design review
(`audit-2026-10-10/codex/answer-android.md`, section ak1-signing-compat). Each is marked **[A1]**
to **[A6]** below.

## The rule

One rule decides everywhere an account's stored secrets are judged
(`StoredWalletSecretsJudge`, `feature_account_impl/.../domain/keys/`). It has two steps, and the
second cannot change the first.

**Step 1, `judgeSigningKey`: signing is decided by the stored signing key alone.** An account can
sign when

1. a complete key pair is stored: private key, nonce and public key of 32 bytes each, readable as
   hex, and the private key is a canonical non-zero scalar. This is checked in Kotlin before any
   native call **[A1]**;
2. the stored public key is the public key in the account's SORA2 address (SS58 prefix 69);
3. the public key **computed directly from the stored private scalar** is that same public key
   (`Sr25519PublicKeyDerivation`: the scalar times the Ristretto base point, RFC 9496 encoding).
   Nothing is fed into a seed factory **[A1]**;
4. a signature made by the packaged library with the stored private key and nonce over a fresh
   random challenge verifies against the address's public key. A sign-and-verify that fails or
   throws refuses signing **[A2]**.

**Step 2, `judgeBackup`: the phrase and the seed are judged separately, only as backup
material.** Each one is

- `MATCHES` when it re-derives the account's address,
- `DOES_NOT_MATCH` when it was read but is malformed or derives another address,
- `UNREADABLE` when something is stored but cannot be read **[A3]**, or
- `ABSENT`.

An account is read-only **only** when step 1 refuses. Nothing else makes it read-only: not a
64-byte seed, not a phrase stored with commas, not key or nonce bytes that differ from a fresh
derivation, not a phrase or seed that belongs to another wallet or cannot be read, not a label
recorded by an earlier run.

The rule says what the stored values prove and nothing more. An explicit watch-only marker, the
PIN and the recovery gates are checked by the callers and are never overridden by a key that
happens to be stored **[A2]**: a watch-only account is handled before any key is looked at
(a key stored next to the marker makes it unavailable, as before) and is never reported as able
to sign by the backup screens or the import. No flow of this version can create a watch-only
account.

Nothing stored is rewritten to make it agree. The rule only reads.

### Order of reading [A3]

The three places named by the review now decide signing before any backup material is read:

| Place | Before | Now |
|---|---|---|
| `MigrationManager.prepareWallet` | Read phrase and seed, then compared everything. A read that threw made the account unavailable. | `judgeSigningKey` on the key pair first. Then the phrase and the seed are read. A value that cannot be read is `UNREADABLE`. Only a Keystore or storage failure (which says nothing about the account) ends the run, as a transient outcome with no verdict and nothing recorded. |
| `UserRepositoryImpl.readSecrets` / `verifiedWalletModel` | Same strict comparison (`verifyStoredSigningKey`). | Key pair read first; phrase and seed read through a reader that turns any non-storage failure into `UNREADABLE`; then the two steps. |
| `CredentialsRepositoryImpl.commitDraft` / `backupVerdict` | Compared each stored field with the draft. | The stored fields are read as present, absent or unreadable (never thrown), and the two steps run on them. |

## What changed

| Area | Before | Now |
|---|---|---|
| Upgrade verification | Required stored seed = 32-byte phrase seed, and stored private key and nonce byte-equal to a fresh derivation. A phrase counted by whitespace. | The judge. The run records per account whether it can sign and the status of phrase and seed (`MigrationManager.lastSecretsReports`, in memory; the durable part is the `secretSource` label). |
| Stored seed | Equal to the 32-byte seed or the account was read-only. | Matches when it is the 32-byte seed that derives the address, **or** the full 64-byte PBKDF2 output of the account's own phrase (all 64 bytes compared; arbitrary trailing bytes are not accepted). One helper (`StoredSecretHex.canonicalSeedOf`) used by the judge and by import. |
| Stored phrase | Raw whitespace split for count and comparison. | Parsed once with the library parser (`RecoveryPhrase.parseOrNull`, NFKD and `[\s,]+`). The parsed words give the count, the label and the comparison. The stored text is never changed. |
| Hex values | `0X` prefix made the key unreadable. | Either case and `0x`/`0X` read as the same bytes, and spaces, tabs and line breaks inside the text are skipped, as the released reader (BouncyCastle `Hex.decode`) skipped them (`StoredSecretHex`). 3.8.x stored a typed raw seed as it was typed, so a seed pasted with a line break is stored with it and is still that account's seed. A seed imported by 3.9.0 is stored as its 64 lower-case hex characters. |
| Secret-source continuity | An account verified as one source and now seen as another became read-only. | Removed: the label is a fact proven from the stored values on every start and never blocks signing. |
| `retrieveSeed`, Raw seed screen, Google backup | Returned the stored value (128 hex for A1 accounts). | The canonical 32 bytes that re-derive the account. Stored bytes stay. |
| Phrase and seed screens, account-screen Google backup | Showed whatever was stored. | Show only material that re-derives the account. When none does the screen says so. If the stored key signs it offers the encrypted JSON export; a read-only account is told how to get it working again. |
| Import of a phrase whose account exists | "This account can't be repaired as it is ... remove it from this phone". | Never asks for removal. See "Import outcomes". `ACCOUNT_REPAIR_NEEDS_REMOVAL` and its strings are deleted in every locale. |
| Key set without an account row | "Restore this wallet" and phrase import both failed and pointed at each other. | Adopted by both under the same rule; a conflicting set is repaired by the phrase. |
| Removal check | Re-ran the strict comparison, so affected accounts could not be removed either. | Proves the stored key signs for the address. When the account signs but nothing stored on the phone is a proven backup of it (label `LEGACY_SECRET`, or a stored phrase or seed that is another wallet's or cannot be read), the confirmed removal first copies the encrypted preference files to `no_backup/wallet-quarantine-<time>-removed-account-without-backup/`; without the copy nothing is removed. (Before this task those accounts were read-only and got the same copy as `-removed-unavailable-account`.) |
| Migration journal | `verifiedAccountCount` = number of accounts classified. | Number of accounts whose identity is `VERIFIED`, the condition the signing gate uses. A journal written by an earlier candidate build is refreshed to the true count on the next start. |
| Upgrade-complete sheet | Always a plain tick for accounts. | Waits for the real counts; when some accounts cannot sign it says how many can and how to repair the rest. |

### Nexus and SORA v1 identities [A5]

These identities are derived from the phrase **text** (BIP-39 password), so the same words with
different separators are different identities. The rule is: never change an identity that
exists; normalise only new derivations.

- **Nexus (SORA 3) accounts** (`NexusChildAccounts`, `IrohaKeyDerivation`). A network with no
  recorded account is derived from the canonical text (parsed words, single spaces). An account
  that is already recorded and is a derivation of the phrase keeps its address, public key, path
  and derivation version exactly. That includes an account an earlier build of this version
  derived from a stored text with commas (`PhraseText.AS_FIRST_DERIVED`, the audited derivation
  kept verbatim): it is not moved to the canonical identity. Signing
  (`IrohaConnectViewModel`) re-derives the recorded account in the form it was first derived
  with (`IrohaKeyDerivation.deriveExisting`) and refuses when the phrase derives neither form;
  nothing is derived in its place.
- A recorded Nexus row that is **not** a derivation of the phrase at all (these rows are not
  secret and not authenticated) is replaced by the canonical derivation, as before the change.
  This keeps the existing protection that the address shown for a wallet is always one its own
  phrase controls (`MigrationManagerSafetyTest`, "verified journal re-derives a tampered network
  child").
- **SORA v1 claim identity** (`getIrohaData`). Derived from the stored text exactly as it is
  stored, as every release did. The first version of this fix switched it to the parsed words;
  that is reverted. A phrase imported by 3.9.0 is stored as its words joined by single spaces,
  so a new wallet always gets the canonical identity.

No Nexus account exists in any Play release (the network accounts table is new in 3.9.0), so
only internal builds of 3.9.0 can hold an account derived the earlier way.

### Encrypted JSON: export and import [A4]

- **Export works for a wallet that is only a key pair.** `generateJson` no longer calls
  `retrieveSeed` first (the sr25519 export is made from the key pair; the seed was never used by
  the encoder). An account without a matching phrase or seed exports, whether that material is
  absent, another wallet's or unreadable.
- **Only a proven key pair is exported.** `generateJson` judges every chosen account first.
  An account whose stored key pair does not prove its address is not exported
  (`ACCOUNT_KEYS_NOT_EXPORTABLE`: "This account can't be exported ... go to Import account"),
  alone or as one of several: a file is a backup of every chosen account or it is not made.
  Before, such an export crashed the screen or wrote a file that was not a backup of the
  account. Nothing unproven is handed to the native encoder **[A1]**, and the copies of the key
  given to the encoder are wiped afterwards. A read-only account that still has a matching
  phrase or seed is backed up to Google from those alone (no JSON form).
- **The several-account export is written key by key.** `JsonAccountsEncoder.generate(List)`
  builds the batch file (polkadot.js "batch-pkcs8") as a `JsonObject` with string-constant
  keys. It used to hand a model class (`JsonAccountData`) to Gson. Nothing but reflection read
  that class's fields, so in the minified release build R8 removed them and the file held only
  `{}` (fix round of 12 October, third review, below). The model class is gone.
- **An export that the import could not read is never handed out.** Before returning, the
  encoder checks that what it wrote has the shape `decode` accepts (kind, encryption and scrypt
  header), for the one-account export, the batch file and each account inside the batch.
  Otherwise the export fails and no file is made.
- **The export can be imported.** `JsonAccountsEncoder.decode` reads the one-account export and
  the batch export back. `CredentialsRepository.draftsFromJson` turns each account into a draft
  that carries the key pair only (`WalletDraft.Source.JSON`). The file is trusted for nothing: the
  public key is computed from the private key in the file and gives the SORA address; the address
  and network written in the file are ignored. Only sr25519, scrypt and xsalsa20-poly1305 are
  accepted, with exactly the scrypt cost this app and polkadot.js write (N = 32768, r = 8, p = 1),
  so a crafted file cannot ask for unbounded memory. Size and account count are bounded.
- **Import account has a third choice, "Backup file (JSON)"**: choose the file or paste its
  text, enter its password. One account behaves exactly like a phrase import (already here,
  adopted, filled in, or the confirmed repair). A batch file adds every account it can without
  replacing anything. For the accounts it holds whose keys on this phone cannot be used, it then
  offers the same confirmed repair, once, with those accounts listed by name and short address
  (`repairFromJsonBatch`); cancelling changes nothing for them and says how many were added.
- **The file is opened off the main thread.** Opening a backup file is scrypt (N = 32768,
  r = 8): one derivation for a one-account file and one more per account of a batch. It runs on
  `Dispatchers.Default`, before the wallet lock is taken (`CredentialsRepositoryImpl.draftsFromJson`,
  `MultiaccountInteractor.keyWork`); so do phrase and seed imports, the repairs and the export.
  The Continue and Download buttons show a progress indicator meanwhile.
- **A chosen file is checked before it is used.** The picker accepts any file type, so the file
  is read only up to 128 KB (a batch of the 100 accounts the import accepts is about 100 KB) and
  must have the shape of an account backup; anything else is refused with "SORA couldn't read
  that file". The file's text stays in the view model: the screen shows the file's name, the
  paste field is at most four lines high, and the text is not parsed again while the password
  is typed.
- **Google Drive.** Recovery now restores the form that derives the account the backup names:
  the phrase, else the raw seed, else the encrypted JSON (opened with the backup password, which
  is the password it was made with). Before, the JSON was ignored and a backup without a usable
  phrase or seed ended in "Something went wrong". An account whose key signs but has no matching
  phrase or seed can now be backed up to Google (as JSON alone); before this change the first
  version of this fix refused that backup.
- **Google Drive, read-only account.** The list of Google backups (Import account, Google) keeps
  the backup of an account that is listed on this phone but read-only, with a line under it that
  says so. Restoring it is the same import as typing the phrase: it completes the account's keys
  in place or offers the confirmed repair. Only the backup of an account that already signs is
  left out (`MultiaccountInteractor.backupRestores`). Before, every backup whose account had a
  row was hidden, so the Google backup of a read-only account could only be used by removing the
  account first. When the Google account holds backups and every one is of an account that
  already works on this phone, the screen stays and says "Nothing to restore"; it no longer
  opens Create account as if no backup had been found. Create account still opens when the
  Google account holds no backup.
- **Google Drive, messages.** A backup that holds only the encrypted JSON is opened by the JSON
  import, so a wrong backup password or an unusable backup used to arrive with the texts of a
  backup file ("the file you exported"). They are now the Google backup texts
  (`GOOGLE_BACKUP_DECRYPTION_FAILED`, `GOOGLE_BACKUP_UNREADABLE`). A backup that holds no
  valid phrase, seed or JSON gets the same "This backup can't be restored" text, which says
  that nothing was changed and points to Import account; it used to end in "Something went
  wrong".
- **A several-account export that is refused names the accounts.** The file is a backup of
  every chosen account or it is not made. When some of several chosen accounts cannot be
  exported, the message lists them by name and short address
  (`MultiaccountInteractor.AccountsNotExportableException`).
- **Backup screens.** Account options, Passphrase and Raw seed judge the stored secrets off the
  main thread, and once per screen (the Raw seed screen did it three times):
  `MultiaccountInteractor.getBackupMaterial`, `getSeedBackupMaterial`,
  `CredentialsRepository.retrieveBackupMaterialForSeed`.
- A wallet that arrives as a key pair is recorded as `LEGACY_SECRET`, signs, shows no phrase or
  seed, and offers its JSON export again.

### Import outcomes (`MultiaccountInteractor`, `CredentialsRepositoryImpl.commitDraft`)

| Stored for the imported address | Result |
|---|---|
| Account that can sign | "Already imported". Its keys are never touched. When no phrase (or raw seed) is stored for it, neither under its address nor through the release-era entry, the one just entered is kept as its backup: add-only, after proving it re-derives the address (`addMissingBackup`). The message then says so, and the backup label and the SORA 3 accounts of a 12- or 24-word phrase are recorded at once (`refreshVerifiedWallet`). This is how a wallet restored from a backup file gets its phrase back without being removed. |
| Key pair that proves the address (account row or not), whatever else is stored | Adopted unchanged. A typed phrase or seed is added only where none is stored. The account is added or made usable again. |
| Some values missing, everything stored agrees | Missing values are added (add-only), then verified. |
| A stored value differs or cannot be read, and the stored key does not prove the address | Nothing is written. The dialog "Repair this account with its recovery phrase" (or "... from your backup" for a seed or file) explains what will happen. Confirming needs the PIN when one is set. Wrong PINs are counted by the PIN screen's own persistent counter and lock timer (`savePinTriesUsed`, timer timestamp): the third wrong entry in a row locks PIN entry for 1 minute, then 5, 15 and 30, here and on the PIN screen, and starting the import again gives no new tries. When a PIN is stored but cannot be read, it is "set but unavailable", never "no PIN": the repair is then confirmed with the phone's screen lock (and asks for one to be set when the phone has none). Only a phone with no PIN at all confirms with the dialog alone. The repair first copies the encrypted preference files to `no_backup/wallet-quarantine-<time>-repaired-account/` (no copy, no write), then writes the proven key pair (and the phrase or seed, when there is one) for that one address and reads them back with the judge. |

`CredentialsDatasource.replaceCredentialsForRepair` / `SoraPreferences.replaceStringsForConfirmedRepair`
is the only code path that replaces stored credentials, and only `repairWithDraft` calls it.

## User-visible effect

- A1 and A3 accounts sign again after the update with no action. Accounts already recorded as
  unavailable by an earlier candidate build heal on the next start (the state was never latched).
- An account whose phrase or seed ciphertext no longer decrypts keeps signing when its key does.
- Raw seed shows 64 hex characters for A1 accounts (it showed 128, which the app's own import
  rejects).
- Importing the phrase, seed or backup file of a read-only account repairs it in place. So does
  restoring its Google backup, which Import account, Google now lists. No screen tells anyone to
  remove an account.
- An account whose stored phrase belongs to another wallet keeps working; that phrase is not
  shown as its backup, and the JSON export is offered. That file can be imported again.
- Import account, Backup file (JSON) is new. Google backup recovery uses the JSON when needed.
- "Export JSON" for several accounts writes a file that holds the accounts. In a release build
  it wrote `{}` before. People with two or more accounts are told once, on the sheet shown after
  the upgrade, that such a file made by an earlier version can be empty and to export again.
- Import account, Google says "Nothing to restore" when every backup is already on the phone.
- The welcome sheet and the journal no longer claim that every account is verified when one is
  read-only.

## Key-safety notes for review

- No automatic path writes, moves or deletes key material. New writes: add-only values (as
  before) and the confirmed repair.
- Derivation is unchanged. `draftFromPhrase` still calls `SubstrateSeedFactory.deriveSeed32`;
  `RecoveryPhrase.miniSeed()` is the same PBKDF2 output cropped to 32 bytes.
- `Sr25519PublicKeyDerivation` is new arithmetic, used only to judge: it reads a private key and
  returns the public key it belongs to. It never signs and nothing it computes is stored. It is
  checked against the vectors of the released library on the JVM, against RFC 9496 for B, 2B and
  3B, and against the packaged native library for 300 random keys on a device. `BigInteger` is
  not constant-time and cannot be wiped; the same key is already held as a hex string by the
  preference layer on every read, so nothing new is exposed inside the app's process.
- A stored private key that is not a canonical scalar was never usable: the native `sign`
  rejects it (`SecretKey::from_bytes`), so refusing it here does not take anything away.
- The random challenge is `"sora-wallet-stored-key-proof-v1:"` plus 32 random bytes, so the signed
  bytes can never be a transaction payload; the signature is wiped after verification.
- A Keystore or storage failure while reading still ends the run as transient.
- The repair PIN is a confirmation of intent, not the security boundary: the person has just
  entered the account's own recovery secret, which controls its funds anyway. It is still held
  to the PIN screen's limits, so the dialog cannot be used to guess the app PIN. The typed PIN
  is kept in memory only (not in the saved instance state), and digits typed with an Arabic,
  Persian or Hindi keyboard are read as the same numbers.
- The JSON import never replaces stored keys except through the same confirmed repair, for one
  account or for the listed accounts of a batch.
- `addMissingBackup` is the only new write of this round. It writes one value (the phrase or the
  seed), only where none is stored and none reaches the account through the release-era entry,
  only next to a key set stored under the address that proves the address, and only after the
  entered secret is proven to re-derive it. A stored phrase is never shadowed by a second one:
  the SORA v1 claim identity and SORA 3 accounts keep seeing the text they were derived from.
  The same rule now guards the adoption of a key set without an account row.

## Historical crypto fixtures [A6]

`scripts/historical-sr25519-vectors/` holds a small Rust tool and a Python script that
regenerate `feature_account_impl/src/sharedTest/java/.../historical/HistoricalSr25519Vectors.kt`,
one source file shared by the JVM and the device tests.

- The Rust tool calls schnorrkel 0.9.1 (curve25519-dalek 2.1.3, merlin 2.0.1, sha2 0.8.2)
  exactly as the shipped `sr25519-java` JNI library does:
  `MiniSecretKey::from_bytes(seed).expand_to_keypair(ExpansionMode::Ed25519).to_bytes()`, bytes
  0..32 private key, 32..64 nonce, 64..96 public key; `to_ed25519_bytes()` for the JSON export;
  `sign_simple("substrate", ...)`. These are the pins of fearless-utils `sr25519-java`
  (schnorrkel <= 0.9.1) and of `vendor/soramitsu-maven/build-inputs/xcrypto-1.2.7-Cargo.lock`.
  It builds offline from the local cargo registry.
- Phrase to seed is BIP-39 plus PBKDF2-HMAC-SHA512, computed independently (Python `hashlib`)
  and checked against what fearless-utils asserts in its own tests: `sr25519HDKD.json`
  ("bottom drive obey ..." gives public key `46ebddef...` and address `5DfhGy...`), `Bip39Test`
  (entropy of "clean wait kiss ...", and the seed of "abandon ... about" with a passphrase).
- Eight vectors: phrases of 12, 15, 18, 21 and 24 words and a raw seed, with entropy, the full
  64-byte derivation, seed, private key, nonce, public key, the JSON secret, a signature, and the
  SORA and generic addresses.

What the tests assert with them: unchanged stored bytes, identical identities, production sign
and verify, and a working export (see the table below). `WalletUpgradeScenarioTest` no longer
generates fixture keys with today's library: every fixture stores the vector's bytes and asserts
that today's library derives exactly them.

## Tests

JVM (`testProductionDebugUnitTest`):

- `common`: `StoredWalletSecretFormatsTest` (including the spaces, tabs and line breaks the
  released hex reader skipped, checked against BouncyCastle), `IrohaKeyDerivationTest` (canonical text for new
  derivations; an account derived from a comma text keeps its identity through
  `deriveExisting`; the earlier derivation is kept verbatim as the reference),
  `JsonAccountsEncoderImportTest` (what the import refuses before any key is derived: other key
  types, other scrypt costs, oversized input, wrong password, empty batch),
  `JsonAccountsEncoderExportTest` (the batch file written with a Gson that refuses reflection
  for every class the release rules do not keep whole: every key of the format by name, the
  sealed part opens with the password and holds each account's own export; an export that could
  not be read back is not handed out; the keep rule the library's one-account model depends
  on is in `app/proguard-rules.pro`), `UpgradeCompleteChecksTest` (who is asked for a new
  several-account backup file).
- `feature_account_impl`: `HistoricalSr25519VectorsTest` (public key from each historical private
  key, addresses, seeds, the Kotlin key expansion the JVM tests use, RFC 9496 points, scalar
  range), `StoredWalletSecretsJudgeTest` (two steps, `UNREADABLE`, malformed keys never reach
  the native call, failed sign-and-verify), `CredentialsRepositoryStoredSecretsTest`,
  `MigrationManagerSafetyTest` (unreadable backup material, a storage failure ends the run with
  no verdict, Nexus identity kept or newly derived), `UserRepositoryTest`,
  `LegacySingleAccountUpgradeTest`, `CredentialsRepositoryTest`. These tests now use real
  sr25519 key pairs instead of hashed stand-ins; only the native sign and verify call is stood
  in for.
- `feature_multiaccount_impl`: `MultiaccountInteractorTest` (JSON import, already here, read-only
  account completed, repair with PIN, batch outcomes and the batch repair, the address a Google
  backup names, wrong repair PINs through the PIN screen's counter and lock, a stored PIN that
  cannot be read, the phrase kept as the backup of a wallet that works, and that opening and
  writing a backup file never run on the caller's thread), `OnboardingViewModelTest` (backup
  file flow, a chosen file kept out of the text field and refused when it is not a backup, the
  repair prompt for a phrase, a raw seed, a one-account file and a batch file, wrong PIN, PIN
  lock, screen lock, cancel, "Import more" after a repaired Google restore, Google recovery
  from JSON and from the form that derives the named account, the Google backup of a read-only
  account listed, restored and repaired after the PIN, or completed without a prompt when only
  values were missing, backups of accounts that all work here answered with "Nothing to
  restore" and no Create account, a Google account without a backup still opening Create
  account, the Google backup texts for a wrong password and an unusable JSON-only backup, and
  a phone without a file picker), `BackupViewModelTest` (one judgement per screen),
  `BackupJsonViewModelTest` (the accounts a several-account export refuses are named),
  `AccountDetailsViewModelTest`. `MultiaccountInteractorTest` also covers which backups are
  offered (`backupRestores`: not on the phone, read-only, signing, and after a repair), the
  seed from one judgement, the judgement never running on the caller's thread, and the
  refused export naming its accounts and writing no file.
  `CredentialsRepositoryStoredSecretsTest` covers `retrieveBackupMaterialForSeed` (one
  sign-and-verify, add-only cache, nothing written next to a stored seed or without a match).
- `scripts/test-verify-release-json-export.py` (9 tests on hand-built DEX files).
- `app`: `SafeModeVaultTest` (a 64-byte seed shown as the 32 bytes that derive the wallet or
  marked as not confirmed, a seed stored with a line break shown as 64 characters, a phrase or
  seed proven to open another wallet has its own result).
- `feature_main_impl`: `PinCodeInteractorTest`.

Device (Android 14 emulator, real Keystore, DataStore, Room and native sr25519):

| What | Test |
|---|---|
| Packaged library derives the historical seeds and key bytes; historical signatures verify; stored historical bytes sign through the production signer; the JSON secret is unchanged; Kotlin public key equals the native one for 300 random keys | `HistoricalSr25519NativeParityTest` |
| Native behaviour the proof relies on | `Sr25519NativeContractTest` |
| One-account and batch export read back by the import through the production encoder, with a Gson that refuses reflection outside the library package the release rules keep; every key of the batch file by name | `common`: `JsonAccountsExportRoundTripTest` |
| [A6] every vector stored by 3.8.6.3 code: same address, unchanged stored bytes and ciphertext, fresh signature verifies, export holds the same key pair | `keyBytesOfTheReleasedLibraryUpgradeUnchangedSignAndExport` |
| [A4] phrase, raw seed and JSON each restore the same account on an empty phone; a key-pair-only wallet signs and exports again | `everyBackupFormatTheAppOffersRestoresTheSameAccount` |
| [A4] a read-only account is not exported, and is repaired from the JSON export made while it worked | `storedKeyOfAnotherWalletIsRepairedWithItsEncryptedJsonExport` |
| [A3] phrase and seed ciphertext that no longer decrypt | `backupMaterialThatCannotBeReadNeverBlocksSigning` |
| [A5] Nexus account first derived from a comma text | `nexusAccountFirstDerivedFromACommaTextKeepsItsIdentity` |
| (a) 64-byte seed cached by the 3.8.2.0 code path, carried through 3.8.6.3 | `seedCachedAs64BytesBy3820KeepsSigningAndReadsAs32Bytes` |
| the same key set without an account row | `seed64KeySetWithoutAccountRowIsRestoredFromThePhone`, `...IsAdoptedByImportingItsPhrase` |
| (b) 2.3.2 phrase with commas / comma plus space, unsuffixed and promoted by 2.4.0 | `phraseStoredWithCommasBy232...` (four tests) |
| (c) upper-case hex and `0X` prefixes | `upperCaseHexAndPrefixedValuesUpgradeAndSign` |
| (d) stored nonce differs from a fresh derivation | `storedNonceThatDiffersFromAFreshDerivationStillSigns` |
| (e) stored phrase of another wallet next to a working key | `storedPhraseOfAnotherWalletNeverBlocksAWorkingKeyAndIsNotShownAsItsBackup` |
| (f) stored key that does not derive the address | `storedKeyOfAnotherWalletIsReadOnlyAndRepairedWithItsPhrase`, `conflictingKeySetWithoutAccountRowIsRepairedByImportingItsPhrase`, `undecryptableAccountIsRepairedInPlaceWithItsPhrase` |
| a raw seed pasted with a line break, stored by the 3.8.6.3 raw-seed writer: signs, is shown as 64 characters, restores the account | `rawSeedPastedWithALineBreakOn3863KeepsSigningAndIsStillItsSeed` |
| a broken key next to such a seed is repaired by typing the seed as it was pasted | `brokenKeyNextToASeedStoredWithALineBreakIsRepairedWithThatSeed` |
| a wallet restored from its backup file gets its phrase, label and SORA 3 accounts by entering the phrase; no key value changes | `phraseEnteredForAWalletRestoredFromItsBackupFileIsKeptAsItsBackup` |
| wrong repair PINs count through the PIN screen's stored counter and lock; the right PIN is refused while locked | `wrongRepairPinsCountThroughThePinScreensCounterAndLockIt` |
| removing an account that signs without a proven backup keeps a copy first; one with its own phrase needs none | `accountThatSignsWithoutAProvenBackupKeepsACopyWhenItIsRemoved` |
| a batch backup file names the read-only account it can repair, and one PIN confirmation repairs it in place | `batchBackupFileRepairsTheAccountItNamesAfterOneConfirmation` |
| the Google backup of a read-only account is offered (real Room and identities), the one of an account that signs is not, and it is no longer offered once the account is completed or repaired | `partialKeyTripleIsCompletedByImportingItsPhrase`, `undecryptableAccountIsRepairedInPlaceWithItsPhrase` |
| one backup file for a working and a read-only account is refused, the refusal names the read-only one, nothing is written | `undecryptableAccountIsRepairedInPlaceWithItsPhrase` |
| the Raw seed screen's single judgement shows the 32 bytes of a 64-byte cached seed and writes nothing | `seedCachedAs64BytesBy3820KeepsSigningAndReadsAs32Bytes` |
| Safe Mode screen for a phrase proven to open another wallet | `app`: `SafeModeScreensTest.aPhraseProvenToOpenAnotherWalletIsNeverCalledUnconfirmed` |
| the sheet shown after the upgrade asks for a new several-account backup file with two accounts and not with one | `app`: `SafeModeScreensTest.upgradeSheetAsksPeopleWithSeveralAccountsForANewBatchBackupFile` |

Every import and repair in these scenarios runs through the production
`MultiaccountInteractor` on the real repositories (the scenario class used a step-for-step copy
of it before; `feature_account_impl` now has `:feature_multiaccount_impl` as a device-test
dependency).

### Results on this branch

- JVM: `testProductionDebugUnitTest` for all modules: 1306 tests, 0 failures (10 skipped by
  their own conditions in `feature_blockexplorer_api`). `:app:assembleProductionDebug` builds;
  every device-test variant compiles (`assembleProductionDebugAndroidTest`,
  `:app:compileQualificationDebugAndroidTestKotlin`); `:app:lintProductionDebug` reports no
  new finding in the changed files or strings; `ktlintCheck` reports nothing on the lines of
  this round. `scripts/test-verify-release-json-export.py`: 9 tests pass.
- Device, Android 14 emulator (SORA-Api34), on the final code: `feature_account_impl` 67 of
  67 (58 upgrade scenarios, 6 native parity, 3 native contract), `common` 17 of 17, `app`
  `SafeModeScreensTest` 12 of 12.
- Minified build (`:app:assembleProductionBenchmarkRelease`: R8 with the release rules, signed
  with the repository's test key), installed on the emulator and walked through with
  `uiautomator dump`: two accounts imported from the public test phrases, Accounts, "Select
  account for batch export", both selected, Backup, password, Download JSON. The file
  (`batch_exported_sora_accounts.json`, 2165 bytes) holds `accounts` (both addresses, names
  and the genesis hash), `encoded` and `encoding` (`batch-pkcs8`, `scrypt`,
  `xsalsa20-poly1305`, version 3). After clearing the app's data: Import account, Backup file
  (JSON), Choose file (system file picker), password, Continue restored both accounts with the
  same addresses. `scripts/verify-release-json-export.py` passes on that APK and fails on the
  minified APK built from `42013abd` (the reflective model).
- `EncryptedWalletMigrationStorageTest` (five `phaseNN` tests) is a qualification sequence that
  needs a process restart between phases (`scripts/run-encrypted-wallet-upgrade-qualification.sh`).
  It is left out of the class runs above and was not run through the script here; run in one
  process with the other classes, four of its phases fail by design.
- `core_db` device tests were not run again: this task's later commits do not touch `core_db`.
- Walked through on the emulator with the production debug build (screen text read with
  `uiautomator dump`): Import account with a phrase (keys are derived off the main thread, the
  PIN set-up follows); the "Backup file (JSON)" screen; and the repair dialog on a staged key
  conflict (the key values of one test wallet copied under a second test wallet's address):
  title, text and PIN field, "That PIN isn't right" after a wrong PIN, "Too many wrong PINs ...
  locked for about 1 min, here and on the PIN screen" after the third, the right PIN refused
  while locked, and after the lock the right PIN repaired the account, which was added and
  selected, with `no_backup/wallet-quarantine-<time>-repaired-account` present.

## Limits

- The vectors come from the pinned Rust crate built for the host, called the way the shipped JNI
  source calls it. The released Android binaries themselves (fearless-utils, shared-features,
  x-crypto 1.0.3 and 1.1.4 `.so` files) could not be obtained offline, so "every shipped binary
  returned these bytes" is supported by source and dependency pins, not by running those
  binaries. Today's packaged binary is checked against the vectors on a device.
- The 3.8.2.0 seed cache is the verbatim 3.8.2.0 function run against today's library. Both
  return the uncropped 64 bytes; the vectors record that value independently.
- The "no matching backup" card, the batch repair dialog, choosing a file with the system file
  picker and importing a real backup file through the screen were not walked through by hand
  (headless emulator image without a file picker). Their logic is covered by view-model,
  interactor and repository tests and by the device scenarios; the file picker itself is not.
  The one-account repair dialog and the phrase import were walked through (see Results).
- Safe Mode "Show my recovery phrase" still shows a stored phrase (or seed) that is proven to
  open another wallet, because in that emergency screen it may be the only copy of that other
  wallet's phrase. It is no longer described as "could not be confirmed": it has its own
  result, title and text, which say that it opens a different wallet, that it is not a backup
  of this one, that SORA found no phrase for this wallet, that nothing was changed, and that the
  JSON export is the backup once the wallet works. A stored seed, or the 2.x owner's unsuffixed
  value, that derives this wallet is shown instead when there is one.
- Safe Mode judges a stored seed on its own (it has no access to Room or to the judge's
  verdict): a seed of 64 bytes is shown as its first 32 bytes when those derive the wallet's
  address, also when the phrase it was cached from can no longer be read. The normal app keeps
  the stricter rule of the task (all 64 bytes must be the stored phrase's derivation) and offers
  the JSON export in that case.
- The batch export stays readable only as a whole file; one account cannot be picked from it.
- The screen-lock confirmation of a repair (stored PIN unreadable) was not walked through by
  hand on a device. Its logic is covered by interactor and view-model tests; the system prompt
  itself is not.
- A wallet restored from its backup file had its SORA v1 claim check marked as done (a key pair
  has no v1 identity). Entering its phrase afterwards keeps the phrase as its backup but does
  not start that check again.
- The batch repair scenario builds its batch file with the library's own encoder for that
  format, because the upgrade harness has no real `CryptoAssistant` (the production batch
  writer's outer layer). The production reader opens it, and `JsonAccountsExportRoundTripTest`
  proves the production writer and reader agree.

## Human and ops actions

- **Translations.** New strings exist in English and Japanese; other locales carry the English
  text (block `ak1-signing-compat` at the end of each `common/src/main/res/values*/strings.xml`):
  `account_repair_*`, `backup_no_matching_secret`, `backup_no_matching_secret_read_only`,
  `safe_mode_whats_new_accounts_some_read_only`, `json_backup_*`, `recovery_json_*`,
  `recovery_enter_json_title`, `onboarding_enter_json`, `export_json_keys_unusable*`,
  `account_already_imported_backup_saved`, `account_repair_screen_lock_*`,
  `account_repair_confirm_screen_lock`, `account_repair_pin_locked*`, `account_repair_*_batch`,
  `import_account_list_read_only_note`, `google_backups_all_here*`, `google_backup_unreadable*`,
  `recovery_json_no_file_picker`, `export_json_keys_unusable_named*`,
  `safe_mode_whats_new_batch_export`.
  In the app module (`app/src/main/res/values*/strings.xml`, block `ak1-signing-compat`):
  `recovery_phrase_other_wallet*`, `recovery_seed_other_wallet*`. `json_backup_batch_skipped`
  changed its text; `account_repair_too_many_attempts` was removed. The manual
  `scripts/verify-middle-egyptian-localization.mjs` check already failed before this task on
  English placeholders (test-only locale).
- **Sign-off.** Assurance notes marked `verified:` are next to `account_repair_body_*`,
  `account_already_imported_backup_saved`, `account_repair_pin_locked`,
  `import_account_list_read_only_note`, `google_backups_all_here` and
  `backup_no_matching_secret` in `common/.../values/strings.xml`, and next to
  `recovery_phrase_other_wallet` in `app/.../values/strings.xml`; the engineering owner signs
  them off like the other `safe_mode_*` assurances. `backup_no_matching_secret` now also promises that the
  JSON file can be imported again.
- **QA on a real device** (release checklist, real-upgrade gate):
  1. Open "Show raw seed" on a 3.8.x build before 3.8.3, update, and check the account sends
     and shows 64 hex characters.
  2. Import the phrase of a deliberately broken account and walk through the repair dialog.
  3. Export an account as JSON, remove the app data, choose "Import account", "Backup file
     (JSON)", pick the file with the system file picker and enter the password. Repeat by
     pasting the text.
  4. Restore a Google backup made by 3.8.6 and one made by 3.9.0 for an account without a
     matching phrase (JSON only).
  5. Import a batch backup file while one of its accounts is read-only on the phone: the
     dialog lists that account, and the PIN repairs it.
  6. In the repair dialog enter a wrong PIN three times: PIN entry is locked for a minute, and
     the PIN screen shows the same lock.
  7. Pick a large file that is not a backup in "Choose file": it is refused at once and the
     screen stays responsive. Pick a batch file of several accounts: Continue shows a progress
     indicator and the screen stays responsive.
  8. Restore a wallet from its backup file, then enter its phrase in Import account: the
     message says the phrase was saved, and Passphrase then shows it.
  9. With a Google backup of an account made in 3.8.x, make that account read-only on the
     phone (for example a deliberately broken key value), then open Import account, Google and
     sign in: the account is in the list with the line "This account is on this phone, but it
     can't send from here", its backup password opens it, the repair is confirmed with the PIN,
     and the account sends again under the same name. An account that works is not in the list.
  10. On the release build: with two or more accounts, Accounts, "Select account for batch
      export", select them, Backup, set a password, Download JSON, and open the file. It must
      hold `accounts`, `encoded` and `encoding`, not `{}`. Then restore it on a phone without
      those accounts (Import account, Backup file (JSON)).
  11. Import account, Google, with a Google account whose backups are all of accounts that
      work on the phone: the message "Nothing to restore" appears and Create account does not
      open.
  12. With two or more accounts, update from 3.8.6: the sheet shown once after the upgrade has
      the line about a JSON backup file with several accounts.
- **Play 3.8.6.2 and the several-account JSON file (to confirm before release).** The Play
  binary could not be inspected here (nothing is downloaded in this lane). The evidence that it
  writes that file as `{}`: its source and release rules are the same (`JsonAccountData` exists
  unchanged since 2.5.1.6 and only Gson reflection reads it; release builds are minified), and
  the `common` classes of 3.8.6.3 (`b7b0f0fc`) minified with that release's own R8 (8.7.18,
  from AGP 8.7.2) and `app/proguard-rules.pro` keep no field of `JsonAccountData`
  (`JsonAccountData -> a` with no members in the mapping). That run can be repeated with
  `java -cp builder-8.7.2.jar com.android.tools.r8.R8 --release --min-api 26 --lib android.jar
  --pg-conf rules.pro --pg-map-output map.txt --output out common-classes.jar`, where
  `rules.pro` is the 3.8.6.3 `app/proguard-rules.pro` plus
  `-keep class jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder { public *; }` (the
  export stays reachable) and `-dontwarn **`. To confirm on the real binary:
  install 3.8.6.2 from Play on a test phone, create two accounts, select both in Accounts,
  Backup, Download JSON, and open the file. If it holds the accounts, remove
  `safe_mode_whats_new_batch_export` and the step in `UpgradeCompleteSheet`
  (`UpgradeCompleteChecks.mayHoldEmptyBatchExport`) and drop the release note below.
- **Release note (Play "What's new", support article).** Suggested text: "If you saved several
  accounts in one JSON backup file with an earlier version, that file can be empty and cannot
  restore your accounts. Please export your accounts again: Accounts, Select account for batch
  export, Backup. Backups of a single account, recovery phrases and Google Drive backups are
  not affected." The one-account export was not affected: it is written by the library, whose
  classes the release rules keep whole.
- **Release pipeline.** `android_release.yml` now runs
  `scripts/verify-release-json-export.py` on the AAB and the APK; `android_ci.yml` runs its
  test. A release built by hand must run the script too.
- **Offline release builds.** `:app:produceProductionBenchmarkReleaseComposeMapping` (and the
  same task of `productionRelease`) needs `org.jetbrains.kotlin:compose-group-mapping:2.3.21`,
  which is not in the offline Gradle cache of this machine. The minified build here was made
  with that task and `mergeProductionBenchmarkReleaseComposeMapping` excluded; they only add
  Compose group names to the mapping file. CI resolves the artifact online.
- **Support.** The text "remove it from this phone, then import the same recovery phrase" no
  longer exists. The advice for a read-only account is: Import account, enter its recovery
  phrase, raw seed or backup file, or choose Google and restore its Google Drive backup, then
  confirm "Repair this account" with the PIN when the app asks. A repair leaves
  `no_backup/wallet-quarantine-<time>-repaired-account/` on the device (encrypted, same Keystore
  key); nothing in the app reads it back, it is evidence for a support case.
- **Older app versions cannot restore a JSON-only Google backup** (3.8.6 ignores the JSON). Such
  a backup is only made for an account that has no matching phrase or seed, which no older
  version could back up either.
- **Not in this change.** The account removal flow still does not show the recovery phrase first
  (audit A2). Read-only accounts still have no label and no explanation on Send and Swap (A2).
- **For the lane that adds "show the phrase before removal".** An account that signs but has no
  proven backup on the phone has no phrase to show (`WalletBackupMaterial.onlyJsonExport`).
  Its removal now keeps a copy of the preference files (`-removed-account-without-backup`);
  the removal screen should offer the JSON export first for these accounts instead of a phrase.
- **Support, removal copy.** `no_backup/wallet-quarantine-<time>-removed-account-without-backup/`
  is the copy kept when a working account without a proven backup was removed (encrypted with
  the same Keystore key; nothing in the app reads it back).
- **Play Console.** The number of A1 accounts is still unknown; nothing in this change reports
  it. If telemetry for read-only accounts is added, `MigrationManager.lastSecretsReports` holds
  the per-account codes (never key bytes).
- **Regenerating the vectors** needs a Rust toolchain and the crates in the local cargo registry:
  `cargo build --release --offline` and `python3 generate.py` in
  `scripts/historical-sr25519-vectors/`. Only the `signature` fields change between runs.

## Fix round of 12 October (second review)

Fourteen findings of the adversarial review of commit `4a574d97`, one major. All were verified
against the code and fixed; none was rejected.

| Finding | Fix |
|---|---|
| **Major.** The JSON import and repair ran scrypt on the main thread. | `draftsFromJson` and `generateJson` run on `Dispatchers.Default`; the interactor runs every import, repair and export off the caller's thread and opens the file before taking the wallet lock; the buttons show progress. |
| A verified account without a proven backup lost the "keep a copy before removal" safety net. | The removal keeps the copy for it too (`removalLosesTheOnlyCopy`), and refuses when the copy cannot be made. |
| Safe Mode showed a 64-byte seed as a confirmed raw seed. | The 32 bytes that derive the wallet are shown; otherwise the value is marked as not confirmed. |
| Safe Mode called a phrase proven to open another wallet "could not be confirmed". | Its own result, title and text (English and Japanese). |
| The repair needed no PIN when the stored PIN could not be read. | Unreadable is "set but unavailable": the phone's screen lock confirms. |
| Wrong repair PINs were only counted per dialog. | They count through the PIN screen's persistent counter and lock timer. |
| The typed PIN was kept in the saved instance state; non-ASCII digits never matched. | `remember`; digits of any script are read as the same numbers. |
| A raw seed stored with whitespace inside was not recognised. | The hex reader skips what the released reader skipped; new seeds are stored as 64 characters. |
| The matching phrase of a working account without a stored phrase was never kept. | `addMissingBackup` (add-only), reported as "already here, saved as its backup". |
| The device scenarios ran a copy of `MultiaccountInteractor`; the repair prompt had no test. | The scenarios call the production class; `OnboardingViewModelTest` covers the prompt. |
| A batch backup file could never repair an account. | One confirmation that lists the accounts, then `repairFromJsonBatch`. |
| A chosen file went into the text field unchecked; 512 KB cap; re-parsed per keystroke. | Checked first, kept in the view model, file name shown, 128 KB, validity cached. |
| "Import more" was hidden after a repaired Google restore. | Computed before the success screen opens, also after a repair. |

Found while fixing: `feature_multiaccount_impl` now uses `androidx.biometric` (the library the
app and `feature_main_impl` already use) for the screen-lock confirmation. Release dependency
locks are strict, so `feature_multiaccount_impl/gradle.lockfile` gained that one line;
`verifyProductionReleaseLockCoverage resolveReleaseDependencyLocks` passes for every module.
No artifact was added to the project and nothing was downloaded.

The file cap is 128 KB, not the 64 KB the review suggested: the import accepts batch files of
up to 100 accounts, which are about 100 KB.

## Fix round of 12 October (third review)

The adversarial review covered commit `4a574d97` and, for some findings, `34d019e2`. Each
finding was checked against the code at `34d019e2`. One blocker and one major were still open
and are fixed; five minors were still open and are fixed; the rest were already fixed by the
second round (`b1c42ee0` to `3a3937a3`), which this round confirmed in the code and by tests.
None was rejected.

| Finding | State at `34d019e2` | What was done |
|---|---|---|
| **Blocker.** In the minified release build "Export JSON" for several accounts writes `{}`. | Open | Fixed (below). |
| **Major.** The Google backup of a read-only account is hidden; with no other backup, Create account opens. | Open | Fixed (below). |
| Major. Removing an account that signs but has no proven backup keeps no copy. | Fixed in `16333cea` | Confirmed: `removalLosesTheOnlyCopy`, copy reason `removed-account-without-backup`, refusal when the copy fails; device test `accountThatSignsWithoutAProvenBackupKeepsACopyWhenItIsRemoved`. Showing a phrase or an export step before removal belongs to the removal flow (audit A2), see "Human and ops actions". |
| Major. A raw seed stored with whitespace inside is not recognised. | Fixed in `b1c42ee0`, `16333cea`, `3a3937a3` | Confirmed: `StoredSecretHex.decodeOrNull` skips what the released reader skipped; device tests `rawSeedPastedWithALineBreakOn3863...`, `brokenKeyNextToASeedStoredWithALineBreak...`. |
| Major. Opening a backup file runs on the main thread. | Fixed in `16333cea` | Confirmed: `keyWork`, `keyWorkDispatcher`; `opening a backup file never runs on the caller's thread`. |
| Major. Safe Mode shows a phrase of another wallet as "couldn't confirm", and a 64-byte seed as confirmed. | Fixed in `b21f1eeb` | Confirmed in `SafeModeVault.reveal` and `SafeModeVaultTest`. |
| Minor. Repair PIN: unreadable PIN treated as no PIN, per-dialog counter, saved instance state, non-ASCII digits. | Fixed in `1f54b61b` | Confirmed: `StoredPin.Unreadable`, the PIN screen's counter and lock, `remember`, `asciiPinDigits`. |
| Minor. Device scenarios used a copy of `MultiaccountInteractor`. | Fixed in `3a3937a3` | Confirmed: the scenarios construct the production class. |
| Minor. A chosen backup file went whole into the text field. | Fixed in `1f54b61b` | Confirmed. |
| Minor. The stored-secrets judgement runs on the main thread for Account options, Passphrase and Raw seed, three times for Raw seed. | Open | Fixed: `getBackupMaterial` runs on `Dispatchers.Default`; the Raw seed screen makes one call (`getSeedBackupMaterial`, `retrieveBackupMaterialForSeed`). |
| Minor. A refused several-account export does not name the account. | Open | Fixed: `AccountsNotExportableException` lists the accounts; the dialog names them. Key pairs already read are wiped when a later account is refused. |
| Minor. A JSON-only Google backup reports a wrong password and an unusable backup in backup-file words. | Open | Fixed: `asGoogleBackupError` maps them to the Google backup texts. A backup with nothing usable in it gets the same text instead of "Something went wrong". |
| Minor. "Choose file" crashes without a document picker. | Open | Fixed: `chooseBackupFile` catches `ActivityNotFoundException` and says the text can be pasted. |

### The blocker: the several-account JSON backup file

**Finding.** `JsonAccountsEncoder.generate(List)` built a `JsonAccountData` and passed it to
`gson.toJson`. The class has no `@SerializedName` and no keep rule, and nothing but Gson
reflection reads its fields. R8 removed them: in the mapping of the minified build made at
`42013abd`, `JsonAccountData -> veb` has no members. The file a release build writes is `{}`.
This lane added the batch import, said the batch export can be restored, and made the JSON
export the designated backup of an account without a matching phrase or seed; every test ran
on unminified code.

**Fix.**

- The batch file is built as a `JsonObject` with string-constant keys. `JsonAccountData` is
  deleted. Nothing in the export or the import reflects over a class of this app any more; the
  one-account export is still written by the library, whose package the release rules keep
  whole (`-keep class jp.co.soramitsu.xsubstrate.** { *; }`).
- `requireReadable`: before a file is returned, it must have the shape the import accepts.
  This is checked for the one-account export, the batch file and each account inside the
  batch. A build that loses a model again fails the export; it cannot hand out an empty file.
- The file format is unchanged: `accounts` (address, `meta.name`, `meta.genesisHash`,
  `meta.whenCreated`), `encoded`, `encoding` (`batch-pkcs8`, `scrypt`, `xsalsa20-poly1305`,
  version 3), in that order.

**Checks that would have caught it, and now do.**

- JVM, `JsonAccountsEncoderExportTest`, and device, `JsonAccountsExportRoundTripTest`: the
  encoder runs with a Gson whose `ReflectionAccessFilter` refuses reflection for every class
  outside the kept library package. On the old encoder both fail with `JsonIOException`.
- Release artifact, `scripts/verify-release-json-export.py`: reads the DEX code and requires
  one method that loads all eleven keys of the batch file as string constants. It fails on the
  minified APK of `42013abd` and passes on the minified APK of this branch. The release
  workflow runs it on the AAB and the APK.
- The minified build itself, walked through on the emulator (see "Results on this branch"):
  export of two accounts, then restore from that file after clearing the app's data.

**Earlier releases.** See "Play 3.8.6.2 and the several-account JSON file" above: the same
code minified with that release's R8 and rules loses every field, so the released builds very
likely wrote `{}` too. That could not be confirmed on the Play binary here. Acting on the
likely case: people with two or more accounts see one more line on the sheet shown after the
upgrade ("A JSON backup file with several accounts made by an earlier version of SORA can be
empty. If you rely on one, export your accounts again from Accounts."), and a release note is
proposed. The one-account export, recovery phrases, raw seeds and Google Drive backups are not
affected.

### The major: a read-only account and its Google backup

**Finding.** `OnboardingViewModel.getBackupedAccountsFiltered()` dropped every backup whose
address has an account row, and a read-only account has one. When the Google backup was the
only backup of such an account, the list was empty and Create account opened, as if there were
no backup. The only way to use the backup was to remove the account first, which invariants 5
and 6 rule out.

**Fix.**

- `MultiaccountInteractor.backupRestores(addresses)` says what restoring each backup does on
  this phone: `ADDS_ACCOUNT` (no account row), `REPAIRS_READ_ONLY_ACCOUNT` (a row that is in
  `UserRepository.unavailableWalletIds()`), or nothing (the account works; its backup is left
  out as before). It reads only.
- The list after sign-in, "Import more" on the success screen and the list it opens all use it.
  The entry of a read-only account carries `readOnlyOnThisPhone`, and the list and the password
  screen show one line under its address: "This account is on this phone, but it can't send
  from here. To repair it, restore this backup."
- The restore itself did not change: it goes through `importFromMnemonic`, `importFromRawSeed`
  or `importFromJson`, which complete a read-only account in place (add-only) or offer the
  confirmed repair. Once the account signs it leaves the list.
- When backups exist and every one is of an account that already works here, the screen stays
  and shows "Nothing to restore ... To add another account, choose Create account or Import
  account." A Google account without any backup still opens Create account, which is the
  intended start of a wallet that is backed up to Google. A new Google-backed account can
  still be made from Create account, on the recovery phrase screen.

**Key safety.** Listing writes nothing. Every write still happens in the import and repair
paths, behind the same PIN or screen-lock confirmation.

**Not walked through by hand:** the Google sign-in and the Drive list (the emulator has no
Google account and the debug build no Drive credentials). The view-model tests drive the flow
against a stand-in for the Drive service; QA steps 9 and 11 cover it on a real device.

### Tests added in this round

Each fails without its fix.

- `common`: `JsonAccountsEncoderExportTest` (3), `UpgradeCompleteChecksTest` (2);
  `JsonAccountsExportRoundTripTest` now refuses reflection and checks every key of the batch
  file (device).
- `feature_multiaccount_impl`: `MultiaccountInteractorTest` (`the backup of a read-only
  account stays restorable ...`, `once a read-only account is repaired ...`, `the seed comes
  from one judgement ...`, `judging the stored secrets for a backup screen never runs on the
  caller's thread`, `accounts that cannot be exported with the others are named ...`, `one
  account that cannot be exported keeps the message that needs no name`);
  `OnboardingViewModelTest` (the Google backup of a read-only account listed, restored and
  repaired; offered instead of Create account; "Nothing to restore"; no backup still opens
  Create account; the Google backup messages for a wrong password, an unusable JSON and a
  backup with nothing usable; no file picker); `BackupViewModelTest` (one judgement per
  screen); `BackupJsonViewModelTest` (the refused accounts are named).
- `app` (device): `SafeModeScreensTest.upgradeSheetAsksPeopleWithSeveralAccountsForANewBatchBackupFile`.
- `feature_account_impl`: `CredentialsRepositoryStoredSecretsTest` (two tests for
  `retrieveBackupMaterialForSeed`); device, `WalletUpgradeScenarioTest`: the offered backups in
  `partialKeyTripleIsCompletedByImportingItsPhrase` and
  `undecryptableAccountIsRepairedInPlaceWithItsPhrase`, the named refusal in the latter, and
  the Raw seed screen's single judgement in
  `seedCachedAs64BytesBy3820KeepsSigningAndReadsAs32Bytes`.
- `scripts/test-verify-release-json-export.py` (9).

### Seen while fixing, not changed here

- Account options lets the person delete an account's Google backup after a confirmation
  dialog, also for a read-only account, for which that backup may be the only way back. It is
  an explicit, confirmed action on the cloud copy and does not touch keys on the phone; the
  lane that labels read-only accounts (audit A2) should decide whether that dialog needs a
  stronger warning for them.
- Import account, Google with a Google account that holds no backup opens Create account
  without a word, as every release did. For a person who signed in to the wrong Google account
  a line such as "No SORA backup was found in this Google account" would help; it was left as
  it is because the same step is the start of creating a Google-backed wallet.
