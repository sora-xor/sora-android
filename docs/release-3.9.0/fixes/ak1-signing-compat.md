# ak1-signing-compat: what could sign before must sign after

Audit items: **A1** (64-byte cached seed), **A3** (phrase stored with commas), the dangerous
"remove it from this phone" advice of **A2**, the "no in-app exit" finding for a key set without an
account row (`verdicts.md`, SEED64), the journal and welcome sheet that reported success for
read-only accounts (SEED64-DYN), and the 128-character "raw seed" (SEED64, low).

## The rule

One rule now decides everywhere an account's stored secrets are judged
(`StoredWalletSecretsJudge`, `feature_account_impl/.../domain/keys/`):

1. **Signing is decided by the stored signing key alone.** An account can sign when
   - a complete key pair is stored (32-byte private scalar, nonce, public key),
   - the stored public key is the public key in the account's SORA2 address (SS58 prefix 69), and
   - a signature made with the stored private key over a fresh random challenge verifies against
     that public key.

   The packaged library has no "public key from private key" call. A Schnorr signature verifies
   only under the public key that belongs to the private scalar that made it, so the
   sign-and-verify step is the proof that the stored public key derives from the stored private
   key (`Sr25519NativeContractTest` pins this behaviour of the native library on a device).
2. **The phrase and the seed are judged separately, only as backup material.** Each one
   "matches" when it re-derives the account's address, "does not match" when it is stored but
   unreadable, malformed or derives another address, or is "absent".
3. An account is read-only **only** when its stored signing key is missing, incomplete,
   unreadable or not the key of its address. Nothing else makes it read-only: not a 64-byte seed,
   not a phrase stored with commas, not key or nonce bytes that differ from a fresh derivation,
   not a phrase or seed that belongs to another wallet, not a label recorded by an earlier run.

Nothing stored is rewritten to make it agree. The rule only reads.

## What changed

| Area | Before | Now |
|---|---|---|
| Upgrade verification (`MigrationManager.prepareWallet`) | Required stored seed = 32-byte phrase seed, and stored private key and nonce byte-equal to a fresh derivation. A phrase counted by whitespace. | The judge. The run records per account whether it can sign and whether phrase and seed match (`MigrationManager.lastSecretsReports`, in memory; the durable part is the `secretSource` label). |
| Stored seed | Equal to the 32-byte seed or the account was read-only. | Matches when it is the 32-byte seed that derives the address, **or** the full 64-byte PBKDF2 output of the account's own phrase (all 64 bytes compared; arbitrary trailing bytes are not accepted). One helper (`StoredSecretHex.canonicalSeedOf`) used by the judge and by import. |
| Stored phrase | Raw whitespace split for count, comparison and Nexus derivation. | Parsed once with the library parser (`RecoveryPhrase.parseOrNull`, NFKD and `[\s,]+`). The parsed words give the count, the label, the comparison and every derivation. The stored text is never changed. |
| Nexus derivation (`IrohaKeyDerivation`) | Validated the parsed phrase, then fed the raw text into PBKDF2: "a, b" and "a b" derived different Nexus identities. | Derived from the parsed words. Only a 12- or 24-word phrase that re-derives the SORA2 address derives Nexus accounts. |
| SORA v1 claim identity (`getIrohaData`) | Raw stored text. | Parsed words. SORA v1 generated its phrases (single spaces) and could only restore an identity from exactly that text, so the v1 identity always belongs to the canonical words. For a phrase stored in canonical form the bytes are identical to before. |
| Hex values | `0X` prefix made the key unreadable. | Either case and `0x`/`0X` read as the same bytes (`StoredSecretHex`). |
| Secret-source continuity | An account verified as one source and now seen as another became read-only. | Removed: the label is a fact proven from the stored values on every start and never blocks signing. |
| `retrieveSeed`, Raw seed screen, Google backup, JSON export | Returned the stored value (128 hex for A1 accounts). | The canonical 32 bytes that re-derive the account. Stored bytes stay. |
| Phrase and seed screens (`BackupViewModel`), account-screen Google backup | Showed whatever was stored. | Show only material that re-derives the account. When none does, the screen says so. If the stored key signs it offers the encrypted JSON export (the sr25519 encoder never used the seed, so it works for every account with a working key pair); a read-only account is told to import its phrase instead, and no export button is shown. No Google backup is made from material that does not match. |
| Safe Mode "Show my recovery phrase" | Raw whitespace split. | Parsed words; a phrase or seed proven to derive another address is marked with the existing "could not be confirmed" text, and a stored seed that derives the address is preferred over such a phrase. |
| Import of a phrase whose account exists | "This account can't be repaired as it is ... remove it from this phone" (`ACCOUNT_REPAIR_NEEDS_REMOVAL`). | Never asks for removal. See "Import outcomes". The response code and its strings are deleted in every locale. |
| Key set without an account row | "Restore this wallet" and phrase import both failed and pointed at each other. | Adopted by both under the same rule; a conflicting set is repaired by the phrase. |
| Removal check (`requireDeletionWalletSecrets`) | Re-ran the strict phrase/seed/nonce comparison, so the affected accounts could not be removed either. | Proves the stored key signs for the address. |
| Migration journal | `verifiedAccountCount` = number of accounts classified. | Number of accounts whose identity is `VERIFIED`, the same condition the signing gate uses. The gate (`WalletUpgradeBackup`), the receipt check and `refreshVerifiedMigrationJournal` accept and write a count below the account count. A journal written by an earlier candidate build (count = all accounts) is refreshed to the true count on the next start. |
| Upgrade-complete sheet | Always a plain tick for accounts. | Waits for the real counts; when some accounts cannot sign it adds "Accounts that can send from this phone: N of M. To repair the rest, import their recovery phrase." |

### Import outcomes (`MultiaccountInteractor.importDraft`, `CredentialsRepositoryImpl.commitDraft`)

| Stored for the imported address | Result |
|---|---|
| Account that can sign | "Already imported". Nothing is touched. |
| Key pair that proves the address (account row or not), whatever else is stored | Adopted unchanged. The typed phrase or seed is added only where none is stored. The account is added or made usable again. |
| Some values missing, everything stored agrees (same words; seed in 32- or 64-byte form; same key bytes) | Missing values are added (add-only), then verified. |
| A stored value differs or cannot be read, and the stored key does not prove the address | Nothing is written. The dialog "Repair this account with its recovery phrase" explains what will happen. Confirming needs the PIN when one is set (three wrong entries close the dialog, nothing changed). The repair first copies the encrypted preference files to `no_backup/wallet-quarantine-<time>-repaired-account/` (no copy, no write), then writes the proven key pair and secret for that one address and reads them back with the judge. |

`CredentialsDatasource.replaceCredentialsForRepair` / `SoraPreferences.replaceStringsForConfirmedRepair`
is the only code path that replaces stored credentials, and only `repairWithDraft` calls it.

## User-visible effect

- A1 and A3 accounts sign again after the update with no action. Accounts already recorded as
  unavailable by an earlier candidate build heal on the next start (the state was never latched).
- Raw seed shows 64 hex characters for A1 accounts (it showed 128, which the app's own import
  rejects).
- Importing the phrase of a read-only account repairs it in place. No screen tells anyone to
  remove an account.
- An account whose stored phrase belongs to another wallet keeps working; that phrase is not
  shown as its backup, and the JSON export is offered.
- The welcome sheet and the journal no longer claim that every account is verified when one is
  read-only.

## Key-safety notes for review

- No automatic path writes, moves or deletes key material. New writes: add-only values (as
  before) and the confirmed repair.
- Derivation is unchanged. `draftFromPhrase` still calls `SubstrateSeedFactory.deriveSeed32`;
  `RecoveryPhrase.miniSeed()` is the same PBKDF2 output cropped to 32 bytes (pinned against the
  existing vector `4ed8d4b1...`).
- The random challenge is `"sora-wallet-stored-key-proof-v1:"` plus 32 random bytes, so the signed
  bytes can never be a transaction payload; the signature is wiped after verification.
- Malformed stored keys make the native library throw a Java exception (they do not abort the
  process); the judge reports that as a failed proof for that account only.
- A Keystore or storage failure while reading still ends the run as transient. Only a value that
  fails to decrypt is treated as "unreadable", and for a phrase or seed that no longer affects
  signing.
- The repair PIN is a confirmation of intent, not the security boundary: the person has just
  entered the account's own recovery phrase, which controls its funds anyway.

## Tests

JVM (`testProductionDebugUnitTest`):

- `common`: `StoredWalletSecretFormatsTest` (separators, NFKD, every BIP-39 length, 32/64-byte
  seed rule including wrong trailing bytes, hex case and prefixes), `IrohaKeyDerivationTest`
  (comma phrase derives the same Nexus identity).
- `feature_account_impl`: `StoredWalletSecretsJudgeTest`, `CredentialsRepositoryStoredSecretsTest`
  (in-memory store and a stand-in for the native library; adopt, fill-in, conflict, repair
  refusal without a copy, canonical seed, JSON export), `MigrationManagerSafetyTest`,
  `UserRepositoryTest`, `LegacySingleAccountUpgradeTest`, `CredentialsRepositoryTest` (updated;
  eleven mock-only tests were replaced by the fake-backed class).
- `feature_multiaccount_impl`: `MultiaccountInteractorTest` (repair offer, PIN, already here),
  `BackupViewModelTest`, `AccountDetailsViewModelTest`.
- `feature_main_impl`: `PinCodeInteractorTest` (15-word phrase stored with commas).

Device (`:feature_account_impl:connectedProductionDebugAndroidTest`, real Keystore, DataStore,
Room and native sr25519), `WalletUpgradeScenarioTest` with verbatim historical writers
(`HistoricalWalletWriters.kt`: `Release3820SeedCache`, `Release3863SeedCache`,
`Release232PrefsCredentialsDatasource`, `Release232CredentialsRepository`):

| Scenario | Test |
|---|---|
| (a) 64-byte seed cached by the 3.8.2.0 code path, carried through 3.8.6.3 | `seedCachedAs64BytesBy3820KeepsSigningAndReadsAs32Bytes` |
| the same key set without an account row | `seed64KeySetWithoutAccountRowIsRestoredFromThePhone`, `...IsAdoptedByImportingItsPhrase` |
| (b) 2.3.2 phrase with commas / comma plus space, unsuffixed and promoted by 2.4.0 | `phraseStoredWithCommasBy232...` (four tests) |
| (c) upper-case hex and `0X` prefixes | `upperCaseHexAndPrefixedValuesUpgradeAndSign` |
| (d) stored nonce differs from a fresh derivation | `storedNonceThatDiffersFromAFreshDerivationStillSigns` |
| (e) stored phrase of another wallet next to a working key | `storedPhraseOfAnotherWalletNeverBlocksAWorkingKeyAndIsNotShownAsItsBackup` |
| (f) stored key that does not derive the address | `storedKeyOfAnotherWalletIsReadOnlyAndRepairedWithItsPhrase`, `conflictingKeySetWithoutAccountRowIsRepairedByImportingItsPhrase`, `undecryptableAccountIsRepairedInPlaceWithItsPhrase` |

Each ends with the same address and a fresh signature that verifies against the address's public
key wherever the rule says the account can sign, and with every untouched ciphertext byte-equal.

### Before and after

The core assertions of four scenarios (same historical writers, `verify()` then `assertUsable`)
were run against the audited commit `42013abd` in a separate worktree on the same emulator:
64-byte seed, comma phrase, other nonce and other wallet's phrase. All four failed there with the
account reported as unavailable (`expected:<[]> but was:<[cnTon6cV...]>`). With this change they
pass.

### Results on this branch

- JVM: `testProductionDebugUnitTest` for all modules, 0 failures.
- Device, Android 14 emulator (SORA-Api34): `feature_account_impl` 50 of 50 (all of
  `WalletUpgradeScenarioTest` and `Sr25519NativeContractTest`), `core_db` 133 of 133, `common` 14
  of 14, `app` 24 of 24 (`WalletUxAcceptanceTest.normalLocalSampleWalletReachesHomeAndCenteredPolkaswap`
  timed out waiting for the Swap screen in the full run while another build was using the
  machine, and passed when run again on its own).
- `EncryptedWalletMigrationStorageTest` (five `phaseNN` tests) is a qualification sequence that
  needs a process restart between phases (`scripts/run-encrypted-wallet-upgrade-qualification.sh`).
  Run as one class it fails on its stage markers, with or without this change; it was left out of
  the class run above and not run through the script here.
- `:app:assembleProductionDebug` and `:app:lintProductionDebug` pass (no new lint findings).

## Limits

- The 3.8.2.0 seed cache is the verbatim 3.8.2.0 function run against the library on today's
  classpath (xsubstrate 1.2.7). 3.8.2.0 linked 1.1.4, which cannot be obtained offline. Both are
  expected to return the uncropped 64 bytes (see the fix commit `cb6f5614`).
- 2.3.2 derived keys with fearless_utils, which is not on the test classpath. The writer stores
  the text and key values exactly as 2.3.2 did; the key pair comes from the packaged library for
  the same words.
- The repair dialog and the "no matching backup" card were not run through the UI on a device
  (headless emulator); their logic is covered by the view-model and repository tests.

## Human and ops actions

- **Translations.** New strings exist in English and Japanese; other locales carry the English
  text (block `ak1-signing-compat` at the end of each `common/src/main/res/values*/strings.xml`):
  `account_repair_*`, `backup_no_matching_secret`, `backup_no_matching_secret_read_only`,
  `safe_mode_whats_new_accounts_some_read_only`.
- **Sign-off.** Two assurance notes marked `verified:` were added next to
  `account_repair_body_*` and `backup_no_matching_secret` in `values/strings.xml`; the
  engineering owner signs them off like the other `safe_mode_*` assurances.
- **Not in this change.** The account removal flow still does not show the recovery phrase
  first (audit A2). Read-only accounts still have no label and no explanation on Send and Swap
  (A2). This change only removes the instruction that sent people to removal.
- **Support.** The text "remove it from this phone, then import the same recovery phrase" no
  longer exists. The advice for a read-only account is: Import account, enter its recovery
  phrase, confirm "Repair this account" with the PIN.
- **Support.** A repair leaves `no_backup/wallet-quarantine-<time>-repaired-account/` on the
  device (encrypted, same Keystore key). Nothing in the app reads it back; it is evidence for a
  support case.
- **QA on a real device** (release checklist, real-upgrade gate): open "Show raw seed" on a
  3.8.x build before 3.8.3, update, and check the account sends and shows 64 hex characters;
  import the phrase of a deliberately broken account and walk through the repair dialog.
- **Play Console.** The number of A1 accounts is still unknown; nothing in this change reports
  it. If telemetry for read-only accounts is added, `MigrationManager.lastSecretsReports` holds
  the per-account codes (never key bytes).
