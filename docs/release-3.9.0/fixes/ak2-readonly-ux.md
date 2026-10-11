# ak2-readonly-ux: read-only accounts are explained and repairable; removal is safe

Audit items: **A2** (read-only accounts are not explained, and the advice given is dangerous),
`and-lockout.md` F2, `and-newcode.md` N2, N4 and N11, and from `verdicts.md` the SEED64,
SEED64-DYN and SEC-AND findings that concern screens: the missing label, Send and Swap failing
without an explanation, the referral screen losing its button, the crash after the only account
is removed, removal without showing the phrase, and "Remove all accounts" leaving wallets that
the next person can restore under a new PIN.

The signing rule itself (which accounts can sign) is ak1's and is not changed here. This task is
about what a person sees and can do when an account cannot sign, and about removal.

## What a person sees now

| Where | Before | Now |
|---|---|---|
| Main screen | A read-only account looked like any other. | The Read-only chip next to the account's name, and a card: "This account can't send from this phone", why, and **Restore its keys** (opens Account options). |
| Account list | No label. | The row says "Read-only · Can't send from this phone". The account stays selectable. |
| Account options | The normal screen. | A panel on top: the reason (no key, part of the key missing, key unreadable, key not usable for the address), then the actions `UserRepository.unavailableWalletOptions` computes: **Restore keys with recovery phrase**, **Restore from Google Drive** (where Google backup exists in the build), **Show the backup saved on this phone**, and last **Remove from this phone**. It says that removing is never needed to repair. |
| Send (recipient and amount), Swap, add and remove liquidity, referral, farm staking, reward claim, SORA v1 claim | The form worked until the last step. Send then said "Something went wrong", Swap closed without a word, the referral screen lost its button. | A notice above the form, before anything is filled in, with **Restore its keys**. The action button is off (Send also says why next to it). |
| A signing refusal that still happens | Nothing, or "Something went wrong". | "This account can't send from this phone ... nothing was signed or sent. To use the account here again, open Account options and restore its keys." |
| Any other failure a screen reports | Nothing, when it was not a `SoraException`. | A calm message: check the connection, or "SORA couldn't complete this just now ... If you were making a transaction, check Activity first, so that nothing is sent twice." |
| Forget account | PIN, then a "Logout" dialog. | PIN or biometrics once, then a screen that shows the account's matching recovery phrase (or raw seed) behind a veil. **I have written it down** can be ticked only after it was shown; only then is **Remove from this phone** enabled. **Keep this account** is the main button. |
| Forget account, no matching backup on the phone | The same dialog. | "SORA found no recovery phrase or raw seed on this phone that matches this account ... this phone can't bring it back", **Export JSON first** when the account still signs, and its own acknowledgement. |
| Forget the only account | The app crashed (2 of 2 runs in the audit). | The screen says that the PIN and custom nodes go too and that SORA starts again; the app restarts at the welcome screen. |
| The removal screen, "What stays on this phone" | Not said. | Other accounts, PIN and settings (one of several), or: wallet keys that no account uses (with their number) and that they then need their own recovery phrase; and the encrypted safety copies, which leave the phone only when SORA is uninstalled. |
| After "Remove all accounts" | Leftover key sets could be restored from Login & Security under whatever PIN the next person set. | They are marked. "Restore this wallet" is not offered and the repository refuses it; Login & Security shows "Import its recovery phrase". Importing the phrase adopts the stored keys unchanged. |
| Onboarding after a database-only restore (or a lost database) | Only Create and Import. "Wallets found on this phone" was under Login & Security, which needs a wallet first. | **Wallets found on this phone** on the welcome screen, when key sets exist behind a PIN that is stored and readable. The PIN is asked for first. |
| Passphrase warning (Account options, and Create account) | "the 24-word passphrase" for every wallet. | The number of words the phrase really has; no number when no stored phrase matches the account. |
| Referral program opened without network | The app closed (seen once in the audit, SEED64-DYN). | "Please, check internet connection and try again later." The screen stays. |
| Create account, wallet cannot be prepared | Crash. | "A new account can't be made right now ... Nothing was created or changed." |
| Create account, final save fails | Nothing; the person was stuck on the last step. | "Your new account isn't saved yet ... Your recovery phrase is still shown on this screen", back on the phrase screen; Continue tries again. |

## How it works

### One source for the label

- `AccountDao.flowAccountsNotVerified()` (a query, no schema change) lists accounts whose identity
  is not `VERIFIED`, the condition `WalletSigningGate` refuses on.
- `UserRepository.flowUnavailableWalletIds()` and `flowSelectedAccountSigning()` follow it, so a
  label appears after the start-up check and goes the moment the keys are restored, without a
  restart. They have no side effect and never throw to a screen (a failed read is "no label").
- `SelectedAccountSigningSource` (`common`) is bound to that flow. `SoraBaseFragment`, the host of
  every screen, collects it once and provides `LocalSelectedAccountReadOnly`. A fragment that sets
  `explainsReadOnlyAccount = true` gets the notice above its content; its composables read the
  local to switch their action button off. No view model of a signing screen was changed.
- While the whole app is in read-only mode (the upgrade banner), the account notice is not shown:
  an account cannot be repaired until that ends.
- This is display only. The signer still applies its own rule to the stored key every time.

### Every failure ends in a message (`BaseViewModel`)

- `onError` shows the failure's own text when it has one. Everything else is classified by
  `FailureNotice.of` from the failure and its causes: the signing gate's refusal
  (`SIGNING_WALLET_UNAVAILABLE`), read-only mode (`WALLET_RECOVERY_READ_ONLY`), an `IOException`,
  or generic. A `SoraException` of kind `UNEXPECTED` is classified the same way, so "Something went
  wrong ... Telegram chat" is no longer shown from this path.
- `onErrorResetProgress` is called for every reported failure. The referral screen puts its
  button back; the PIN screen clears a row of dots that would take no more digits.
- A cancelled coroutine is ignored. A report from a background dispatcher is posted instead of
  throwing (`MainViewModel` reports from `flowOn(io)`).
- The referral screen read the bond fee again whenever the XOR balance arrived and whenever the
  number of invitations changed, in places no handler covered; offline that ended the app.
  `ReferralViewModel.reCalcOnChange` now reports the failure, and each of its subscriptions has
  its handler after the work as well as before.
- `onBackgroundError` keeps the old behaviour for work nobody waits for. `MainViewModel` uses it
  for its subscriptions and for the fiat refresh that runs every 20 seconds; otherwise an offline
  phone would get a dialog every 20 seconds.

### Removal (`PinCodeViewModel`, `AccountRemovalScreen`)

- After the PIN or biometrics, `PinCodeInteractor.walletRemovalBriefing` reads, for each account,
  `CredentialsRepository.retrieveBackupMaterial` (ak1's judge): the phrase only when it re-derives
  the account, else the seed that does, else nothing (invariant 4). The PIN is not asked for again
  to show it, and coming back to the screen (for example from the JSON export) asks for nothing.
- The deletion preview (valid for two minutes) is created when the person confirms, not when the
  screen opens, so writing a phrase down is not cut short. If the accounts on the phone changed
  meanwhile, nothing is removed and the screen is described again.
- A check that does not pass shows "This account wasn't removed ... nothing was removed (code)"
  and leaves the screen as it is, but only when no deletion failure is journaled and every target
  is still listed. Otherwise the app restarts and the start-up finishes or reports the journaled
  removal, as before.
- The window carries `FLAG_SECURE` while a phrase or seed can be on the screen. The phrase is in
  the activity window (never a dialog) and its words are not composed before the veil is lifted.
- The crash: `AccountListViewModel` (still on the back stack) asked for the selected account as
  soon as the account list emitted no account; its `catch` sat before the code that threw. It no
  longer asks when the list is empty, and a failure inside is reported.
- After a removal, "back to the account list" falls back to the main screen when Account options
  was opened from there (the list is then not on the back stack and the pop did nothing).

### Key sets that stay after "Remove all accounts" (`SoraPreferences`, `UserRepositoryImpl`)

- `commitWalletDeletion` (scope ALL) clears every preference except retained key material, as
  before. In the same atomic edit it now writes `wallet.retainedNeedsPhrase`: the ids of the key
  sets that stay (the address a credential key is suffixed with, or `unsuffixed` for the
  release-era set). Earlier marks are kept. The marker is not key material and not part of the
  wallet-state hash, so the deletion journal and the start-up gate are unaffected.
- `adoptRetainedWallet` refuses a marked key set (`RETAINED_WALLET_NEEDS_RECOVERY_PHRASE`) before
  it reads any key. When the marks cannot be read, every key set is treated as marked.
- `savePin` marks the same way when a PIN is set while no PIN is stored: the first set-up, or the
  set-up after remove-all. A PIN nobody proved must not open what was on the phone before it.
  Changing a PIN marks nothing; neither does a new PIN in place of a stored one that cannot be
  read (damaged data, not a hand-over; marking then could leave a wallet without a phrase with no
  way back). Nothing is written when there is no such key set. Accounts are never affected.
- Importing a wallet's recovery phrase, raw seed or backup file adopts its stored keys unchanged
  (ak1's `commitDraft`), marked or not.
- Safe Mode "Show my recovery phrase" no longer lists a marked key set: the PIN or screen lock
  confirmed there never protected it.
- `UserRepository.retainedWallets()` is the one list of key sets no account uses, with
  `needsRecoveryPhrase`. Login & Security and onboarding both use it.
- Nothing is deleted. A person who wants the leftover key sets gone from the phone still has to
  uninstall SORA; the removal screen says that they stay.

### "Wallets found on this phone" from onboarding (`MultiaccountInteractor`, `FoundWalletsScreen`)

- `walletsRestorableWithPin()` lists unmarked key sets only while a PIN is stored and readable.
- `restoreRetainedWallets(pin, name)` checks that PIN first, with the PIN screen's own counter and
  lock timer (third wrong entry locks for 1 minute, then 5, 15, 30), then adopts each key set
  through `adoptRetainedWallet`. A key set whose stored keys cannot be proven is left as it is and
  named; a Keystore or storage failure ends the action instead.
- After the restore the main screen asks for the PIN as at every start.

### Create account (`OnboardingViewModel`)

- `onCreateAccountContinueClicked` creates the draft first and navigates after; a draft that
  cannot be created is explained (read-only mode has its own sentence).
- `finishCreateAccountProcess` keeps the draft on a failed save, returns to the phrase screen and
  says so. `MultiaccountInteractor.createUserFromDraft` finishes an attempt that had already
  added the account (it used to end in `WALLET_DRAFT_ALREADY_ACCOUNT` for ever). When the draft is
  gone (process restart in the middle), it says that nothing was created and returns to the start.

## Key-safety notes for review

- No automatic path writes, moves, re-encrypts or deletes key material. The only new writes are
  the marker (a list of ids) and nothing else. `SoraPreferencesRemoveAllTest` and the device
  scenarios compare the kept ciphertexts before and after.
- No address, public key or derivation is touched.
- What could sign still signs: nothing here changes an identity state. The label reads it.
- Backup material is shown only when it re-derives the account (the removal screen uses
  `WalletBackupMaterial`). A stored phrase of another wallet is never offered as the account's
  backup; such an account gets the "no matching backup" path and the JSON export.
- No text asks anyone to remove an account, clear data or reinstall as a repair. The removal
  screen states as a fact that the encrypted copies leave the phone only with an uninstall.
- Removal still needs PIN or biometrics, the acknowledgement and an explicit button; the
  repository's own journal and checks are unchanged.
- One read-only account never blocks another: every label and action is per account.

## Tests

Each test below fails without its fix.

JVM (`testProductionDebugUnitTest`):

- `common`: `BaseViewModelErrorTest` (generic message, the refusal also when wrapped as unexpected,
  never "Something went wrong", read-only mode, network, own message kept, progress reset,
  cancellation, background work), `FailureNoticeTest`, `RetainedNeedsPhraseKeysTest`,
  `SoraPreferencesRemoveAllTest` (real DataStore file: kept key sets are marked in the removal's
  edit and keep every byte; the journal's second pass changes nothing; earlier marks stay; nothing
  marked when nothing stays or when one of several accounts is removed; the first-PIN marking).
- `feature_account_impl`: `UserRepositoryTest` (the selected account's label follows the database
  and the selection; an unreadable label is no label; the reason from the stored key alone; key
  sets left by remove-all listed as needing their phrase, the unsuffixed set by its own id,
  unreadable marks; "Restore this wallet" refuses a marked set and reads no key; first-time PIN
  marks before the PIN is saved, a PIN change and an unreadable stored PIN mark nothing, failures
  save no PIN).
- `feature_multiaccount_impl`: `AccountListViewModelTest` (label follows a repair; the last
  account's removal empties the list without asking for a selected account; a failure is
  reported), `AccountDetailsViewModelTest` (panel from the options, Google only where available,
  each action, panel gone after repair, removal goes through the PIN), `ExportProtectionViewModelTest`
  (12, 15, 18, 21, 24 words; no number without a matching phrase), `OnboardingViewModelTest`
  (word count for a new wallet; refused draft; failed final save with retry; missing draft; found
  wallets offered, not offered, wrong PIN, lock, partial and no restore), `MultiaccountInteractorTest`
  (found wallets only behind a readable PIN and never a marked one; PIN counter and lock; storage
  failure; a create retry after the account was added).
- `feature_main_impl`: `PinCodeViewModelTest` (phrase shown and nothing removed yet; reveal and
  acknowledgement needed; the only account: everything removed and the app restarts; one of
  several; raw seed; no backup with JSON export; read-only without export; cancel; PIN asked once;
  failed check; journaled failure; failure after the rows were removed; accounts changed
  meanwhile; unreadable backup), `PinCodeInteractorTest` (briefing), `LoginSecurityViewModelTest`.
- `feature_referral_impl`: `ReferralViewModelTest` (a refused Activate and a failed Bond say why
  and give the button back; opening the screen and changing the invitations without network is
  reported and does not end the app).
- `app`: `SafeModeVaultTest` (a marked key set is never listed for a reveal).

Device (Android 14 emulator, real Keystore, DataStore, Room and native sr25519):

| What | Test |
|---|---|
| A read-only account (stored key of another wallet, written by 3.8.6.3 code) is reported as the selected read-only account, its row is labelled, Account options show the reason and "Restore keys with recovery phrase", the signer's refusal maps to the read-only message, and after the in-place repair every label goes without a restart | `WalletUpgradeScenarioTest.readOnlyAccountShowsItsLabelItsRepairAndSendExplainsItself` |
| Removing the only account with the account list still open: no crash, an empty list, no selected account; the phrase brings it back at the same address | `...removingTheOnlyAccountEndsCleanlyWithTheAccountListStillOpen` |
| Remove-all with an abandoned 3.8.6.3 "Create account" on the phone: PIN gone, leftover ciphertext unchanged, marked; not offered in onboarding, refused by "Restore this wallet" under a new PIN; its phrase adopts it unchanged | `...removeAllAccountsLeavesKeySetsThatOnlyTheirOwnPhraseBringsBack` |
| Database set aside: both wallets (one a raw-seed wallet without a phrase) are offered in onboarding; a wrong PIN restores nothing and is counted; the right PIN restores both with every byte kept | `...walletsFoundOnThisPhoneAreRestoredFromOnboardingBehindThePin` |
| A PIN set for the first time does not unlock a key set that was on the phone before it | `...aPinSetForTheFirstTimeNeverUnlocksKeySetsThatWereOnThePhoneBeforeIt` |
| Send form: the notice with its repair action above the form, Review off with the reason, both gone once the account signs | `feature_assets_impl` `ReadOnlySendTest.aReadOnlyAccountIsExplainedAboveTheSendFormWithItsRepairAndReviewIsOff` |
| Main screen header chip and notice; account list rows; Account options panel (every reason, every action, only what is possible); removal screen (the only account, one of several, no backup with and without export, raw seed); found wallets in onboarding; Login & Security for a marked wallet | `app` `ReadOnlyAccountScreensTest` (10 tests) |

### Results on this branch

Run on 12 October 2026 on the final tree, offline, JDK 17, emulator `SORA-Api34` (Android 14).

| Command | Result |
|---|---|
| `./gradlew --offline :app:assembleProductionDebug` | BUILD SUCCESSFUL |
| `./gradlew --offline testProductionDebugUnitTest --continue` (every module) | BUILD SUCCESSFUL; 1400 tests, 0 failed, 10 skipped (the 10 are in `feature_blockexplorer_api` and were skipped before this change) |
| `./gradlew --offline :feature_account_impl:connectedProductionDebugAndroidTest` | 77 run, 73 passed. All 63 `WalletUpgradeScenarioTest` scenarios (the 5 new ones included), `HistoricalSr25519NativeParityTest` (6) and `Sr25519NativeContractTest` (3) pass. The 4 that fail are `EncryptedWalletMigrationStorageTest.phase02` to `phase05`: an ordered sequence that needs a process restart between its phases and is run with `scripts/run-encrypted-wallet-upgrade-qualification.sh` (see `ak1-signing-compat.md`). This change does not touch that test or the code it covers. |
| `./gradlew --offline :feature_assets_impl:connectedProductionDebugAndroidTest` (class `ReadOnlySendTest`) | 4 of 4 |
| `./gradlew --offline :app:connectedProductionDebugAndroidTest` (classes `ReadOnlyAccountScreensTest`, `SafeModeScreensTest`) | 22 of 22 |
| `./gradlew --offline :app:lintProductionDebug` | BUILD SUCCESSFUL, no new error (179 errors are filtered by the existing baseline, as before) |
| `./gradlew --offline ktlintCheck` | Fails for the project as it did before this change (about 1000 findings in files this task did not write). No finding is on a line this task added or changed. |

Walk-through on the emulator with the production debug build (screens read with `uiautomator`,
because the windows that show secrets are `FLAG_SECURE`). The wallets are the two published
BIP-39 test phrases; one account was made read-only by putting the other wallet's key values
under its key names.

- Main screen: the Read-only chip, the card and **Restore its keys**. Accounts: the labelled row.
- Swap and Referral program: the notice above the screen with **Restore its keys**; it opens
  Account options with the reason, **Restore keys with recovery phrase**, **Show the backup saved
  on this phone** and **Remove from this phone**.
- Restore keys with recovery phrase: the PIN, then every label is gone without a restart.
- Forget one of two accounts: PIN once, the phrase behind the veil, the tick possible only after
  the reveal, removal, back on the main screen with the other account.
- Forget the only account: the app restarts at the welcome screen without a crash; importing the
  phrase again sets the PIN up once.
- Database set aside: the welcome screen offers **Wallets found on this phone**; a wrong PIN is
  refused, the right one restores the wallets.
- Remove all accounts with a leftover key set: the marker is written, the leftover ciphertext is
  byte-identical, the PIN is gone, onboarding does not offer the key set. Under a new PIN, Login &
  Security shows it as needing its recovery phrase with **Import its recovery phrase** and no
  **Restore this wallet**; importing the phrase adopts the stored keys byte-identical.

## Human and ops actions

- **Translations.** New strings are English and Japanese; the other locales carry the English
  text. Block `ak2-readonly-ux` at the end of each `common/src/main/res/values*/strings.xml`:
  `failure_generic_*`, `account_read_only_*`, `account_removal_*`,
  `safe_mode_retained_needs_phrase`, `safe_mode_retained_import_phrase`,
  `onboarding_found_wallets_*`, `export_protection_passphrase_description_words`,
  `export_protection_passphrase_description_neutral`, `create_account_*`.
- **Sign-off.** Assurance sentences have a `verified:` note next to their key in
  `common/.../values/strings.xml` (for example `account_read_only_body`,
  `account_read_only_refused`, `account_removal_*`, `safe_mode_retained_needs_phrase`,
  `onboarding_found_wallets_body`). The engineering owner signs them off like the other
  `safe_mode_*` assurances.
- **Product decision recorded here.** For N2 the audit offered three options. This change takes
  "keep the key sets, require their recovery phrase, and say in the dialog what stays". Nothing is
  deleted that the person did not choose to remove. If the owner prefers that "Remove all accounts"
  also erases leftover key sets and the safety copies, that is a separate change with its own
  confirmation.
- **Support.** The advice for a read-only account is: Account options, Restore keys with recovery
  phrase (or Google Drive). The advice for "I removed all accounts and a wallet is still listed
  under Login & Security": it needs its own recovery phrase; importing it brings the wallet back.
- **QA on a real device** (release checklist, real-upgrade gate):
  1. Make an account read-only (for example the A1 state on a build before ak1, or a deliberately
     broken key value on a debug build). Check the chip and card on the main screen, the row in
     Accounts, the panel in Account options, and the notice on Send, Swap, pool, referral and farm
     screens. Restore its keys with the phrase and check that every label goes.
  2. Forget one of two accounts: PIN once, phrase veiled, tick only after reveal, removal, back
     on the main screen with the other account.
  3. Forget the only account: the app restarts at the welcome screen; import the phrase again;
     the PIN is set up once.
  4. With a leftover key set on the phone (abandon "Create account" on 3.8.6, update), remove all
     accounts, set the app up with another wallet and a new PIN: Login & Security shows the
     leftover wallet with "Import its recovery phrase" and no "Restore this wallet".
  5. "Set up this device again" (database only): the welcome screen offers "Wallets found on this
     phone"; a wrong PIN is refused; the right PIN restores the wallets.
  6. With biometrics enabled, start "Forget account" with a fingerprint: the same removal screen.
  7. Open Passphrase for a 12-word and a 24-word account: the warning names 12 and 24.

## Limits

- The encrypted copies under `no_backup` (`wallet-upgrade-backup-*`, `wallet-quarantine-*`) are
  still not erased by a removal (SEC-AND, low). The removal screen now says so. No screen reads
  them back.
- Leftover key sets cannot be erased from inside the app; only an uninstall removes them.
- Farm staking and reward claim (`EditFarmViewModel`, `ClaimDemeterViewModel`) still have no error
  handling of their own for a failed submission (audit A5, another lane). For a read-only account
  their buttons are now off, so that path cannot be reached with one.
- Swap's confirm screen still shows its own short "rejected" state when a submission fails for
  another reason (A5). The message from `onError` is shown in addition.
- The notice and the disabled buttons depend on the label flow. If it cannot be read, the screens
  look as before and the refusal message appears at the last step.
- After "Wallets found on this phone" in onboarding the main screen asks for the PIN again (the
  normal app lock).
- A wallet that is listed under "Wallets found on this phone" keeps the name "Restored wallet"
  until it is renamed in Account options.
- Google sign-in for "Restore from Google Drive" was not walked through (the emulator has no
  Google account and the debug build no Drive credentials); it opens the same Google step of
  Import account that ak1 tested with a stand-in for the Drive service.
