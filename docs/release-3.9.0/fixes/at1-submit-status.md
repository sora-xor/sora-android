# at1-submit-status: truthful transaction status on every screen (audit A5)

Fixes audit finding A5 of the 10 October 2026 audit (`and-signing.md` F2), and the
verifier findings TX-SWAP (Demeter crash, silent referral failure), TX-HOST-FROZEN
("a transaction the node refused is shown as Transaction submitted") and SEED64-DYN
(Referral program screen crashes offline).

No wallet key material, account row, address or derivation code is touched. The
status journal (`sora2PendingSubmissions`) keeps its schema and its state machine;
nothing in this change deletes, rewrites or re-submits a journal row.

## What was wrong

After `author_submitExtrinsic` succeeded, every path polled for finality. One
failed HTTPS request, or 120 seconds without finality, ended the wait with an
exception. Only Send treated that as "submitted, still pending".

| Screen | What the user saw for a broadcast transaction |
|---|---|
| Swap, add and remove liquidity | red cross, "Transaction rejected" for half a second, then back |
| Referral bond, unbond, set referrer | nothing; the button's progress mark stayed on |
| Demeter stake, unstake, claim | the app closed (uncaught exception) |
| Legacy claim | "Transition of assets failed", stored and repeated on every visit |

Separately, an explicit refusal by the node (for example "Inability to pay some
fees" when XOR is locked) was reported by Send as "Transaction submitted" and left
pending for about six and a half minutes. Opening Invite Friends, Edit farm, Claim
rewards or Remove liquidity without network closed the app.

## What changed

### 1. The finality wait tolerates failed and slow reads

`Sora2PendingSubmissionCoordinator.awaitTerminalStatus`
(`sorasubstrate/.../substrate/Sora2PendingSubmissionCoordinator.kt`):

- A failed status pass is retried after 1, 2, 4, 8 and 8 seconds. Six failures in
  a row end the wait as "still pending". A read failure is never a result.
- A successful pass that finds no final state is repeated every 3 seconds, as
  before, and does not count towards the retry bound.
- The whole wait runs under one time limit (120 seconds, unchanged). The limit
  now also interrupts a request that is in flight; before, a single slow request
  (up to 45 seconds) could overrun it.
- When the limit fires just after a pass stored the final state, that state is
  still returned.
- "Still pending" is `Sora2FinalityPendingException`. `ExtrinsicManager` turns it
  into `ExtrinsicSubmissionUnknown` with `acceptedByNode = true`, kicks the
  background status worker and no longer records it as an error class.

### 2. One outcome model for every path

`common/.../domain/TransactionOutcome.kt` defines the four outcomes. The single
mapping is `sora2TransactionOutcome { ... }` in
`sorasubstrate/.../substrate/Sora2TransactionOutcomes.kt`.

| Outcome | Source | Shown as |
|---|---|---|
| `Confirmed` | finalized block, `ExtrinsicSuccess` | "Transaction confirmed", then the transaction page |
| `FailedOnChain` | finalized block, `ExtrinsicFailed`; or expired without inclusion | "Transaction failed", then the transaction page with the reason |
| `RefusedByNode` | explicit JSON-RPC error from the one submission call | "Not sent" and a dialog with the reason; never "submitted" |
| `Waiting` | lost answer, or the finality wait ended without a final state | "Transaction submitted", then the transaction page: "waiting for confirmation, don't send it again" |

Errors raised before the transaction reaches the network (no connection while
preparing, a changed fee, the signing gate) are not outcomes. They are thrown, and
every screen now shows "Not sent ... Nothing was sent" instead of nothing
(`BaseViewModel.showTransactionNotSent`, `TransactionOutcomeTexts.notSent`). The
text depends on the reason, so that it never asks for another try that cannot
help:

| Reason | Text |
|---|---|
| network unreachable while preparing | "couldn't reach the network ... check your internet connection and try again" |
| fee changed during review (`SORA2_TRANSFER_FEE_CHANGED`) | "the network fee changed ... check the new fee and confirm again" |
| the signer refused this account (`SIGNING_WALLET_UNAVAILABLE`, `WALLET_DELETION_ACTIVE`) | "This account can't sign transactions on this phone right now ... Your keys were not changed and your funds are safe" |
| read-only mode after an unfinished upgrade check (`WALLET_RECOVERY_READ_ONLY`) | the existing read-only explanation (`safe_mode_read_only_details`) |
| anything else | "couldn't be prepared. Nothing was sent ... Please try again." |

Only a business error with its own message (for example an unavailable fee rate)
keeps that message. Every other `SoraException` gets the "Not sent" dialog: the
general error handler shows nothing at all for some of them and a bare "unexpected
error" for others, and neither says what happened to the transaction.

Paths converted (interactor returns `TransactionOutcome`, view model handles all
four with an exhaustive `when`):

- transfer: `AssetsInteractorImpl.observeTransfer`, `TransferAmountViewModel`
- swap: `SwapInteractorImpl.swap`, `SwapViewModel`
- add and remove liquidity: `PoolsInteractorImpl`, `LiquidityAddViewModel`,
  `LiquidityRemoveViewModel`
- referral bond, unbond, set referrer: `ReferralInteractor`, `ReferralViewModel`
- Demeter stake, unstake, claim: `DemeterFarmingInteractorImpl`,
  `EditFarmViewModel`, `ClaimDemeterViewModel`
- legacy claim: `WalletInteractorImpl.migrate`, `ClaimViewModel`

Screen behaviour:

- Swap and liquidity: the result mark comes from the outcome
  (`TransactionResultBadgeOverlay`). Confirmed, failed on chain and waiting open
  the transaction page. Refused and not-sent show a dialog and the screen stays
  with Confirm available again. (Liquidity used to leave for the hub, which would
  have hidden the dialog.)
- Send: confirmed and waiting show the toast ("Transaction confirmed" or
  "Transaction submitted") and open the transaction page. Failed on chain,
  refused and not-sent show a dialog and stay on the Send screen.
- Referral and Demeter: same rule; the progress mark is cleared on every path.
- Legacy claim: only a confirmed (or already present) claim is stored as done and
  only a claim that was included and failed there as failed. A claim that expired
  without being included is explained ("nothing was sent ... you can try again")
  and not stored, because the stored "failed" state is shown again on every visit.
  A waiting claim shows "submitted, waiting" and is not stored as failed. The
  claim screen cannot be left without closing the app, so it then follows the
  claim by itself: every 10 seconds for 8 minutes (longer than the 64 blocks in
  which the transaction can be included) it asks the network whether the claim is
  there, and closes as soon as it is. Nothing is signed or sent while following,
  and a failed read is simply repeated. While any earlier transaction of the
  account is unresolved, the existing claim gate sends nothing; the screen says
  "Not sent ... an earlier transaction is still being checked ... try again in a
  few minutes".

### 3. Explicit refusal by the node

`Sora2SubmissionRefusalClassifier`
(`sorasubstrate/.../substrate/Sora2SubmissionRefusal.kt`) treats an error from
`author_submitExtrinsic` as a refusal only when it is a JSON-RPC error object with
a transaction-pool refusal code of the Substrate author RPC:

- `1010` invalid transaction: "cannot pay the fee" when the text names payment,
  otherwise "not valid";
- `1014` priority too low and `1016` immediately dropped: "network busy";
- `1001`, `1002`, `1011`, `1015`, `1018`, `1019`, `1020`, `1021`: "not valid".

These stay "waiting" on purpose: transport errors, timeouts, malformed answers,
unknown codes, `1012` (temporarily banned), `1013` (already imported) and a
"stale/outdated" answer. Each can mean the node has the transaction or that it
was already included.

A refusal does not change the journal row. The row stays unresolved, its whole
validity window is still scanned and it expires normally. If a refused
transaction does appear in a finalized block, Activity shows the chain result:
chain proof wins over a recorded refusal.

### 4. A waiting transaction is visible where the user looks

- `Sora2SubmissionActivity` describes what a signed transaction does (kind, token
  ids, amounts, fee, counterparty). `ExtrinsicManager` stores it right after the
  journal row is written and before transport, so it survives the app being
  closed during the wait.
- Storage: `FileSora2SubmissionDetailsStore`, one JSON file
  `files/sora2_submission_details.json` (at most 256 entries, none older than
  30 days, written through a temporary file and an atomic rename). No database
  schema change. It also keeps
  the node's refusal and the runtime error of a failed transaction. It holds
  nothing secret and is never an authority for status. If the file is lost or
  unreadable, entries fall back to the generic "Transaction submitted / SORA2"
  presentation; no method of the store throws.
- `TransactionHistoryRepositoryImpl` rebuilds the Activity entry from the journal
  row plus this description: a waiting swap is a swap entry with its tokens and
  amounts. It therefore also appears in the activity of each of its tokens
  (asset page and "Recent activity"), which the generic entry did not.
- The repository's `state` flow now also ticks on every write to the journal
  table (`WalletIdentityDao.observeSora2PendingSubmissionChanges`). Activity,
  the per-asset list, the asset page and an open transaction page refresh when
  the background worker resolves a row.
- The transaction page (`TxDetailsViewModel`, `BasicTxDetails`) shows a note
  under the status: waiting, being checked, failed on chain with the reason,
  never included, or refused with the reason. The explorer button is hidden for
  a transaction that never reached a block.
- The transaction page follows a waiting transaction by itself. While it shows
  one, it asks for the status every 6 seconds for at most 8 minutes
  (`TransactionHistoryRepository.checkWaitingTransactions`: one status-only
  pass of the coordinator, which has no signer and cannot submit). The
  background worker retries after 30, 60, 120, 240 ... seconds, so without this
  a page opened right after sending could show "Pending" for minutes after the
  transaction was final. A failed check changes nothing and is not shown.
- The transaction page no longer skips the first refresh tick. It used to
  (`drop(1)`), which is right only when an earlier tick is replayed, that is,
  when a transaction was saved in the same app session. After a restart nothing
  is replayed, so the skipped tick was the very journal write that made the
  waiting transaction final. Seen on the emulator: the journal row was
  `EXPIRED_NOT_INCLUDED` and the open page still said "Pending ... don't send
  it again" until it was closed and opened again.
- For a transaction that never reached a block (refused or expired) the page
  does not list the network fee. The note says no fee was charged; the fee that
  was only expected used to be shown as "Network fee" next to it.
- The pending and failed marks on Activity rows have a spoken description
  ("Pending", "Failed"). Before, a screen reader could not tell a waiting entry
  from a completed one.
- An entry that is known only by its hash (the legacy claim, or a transaction
  whose token is not in the token list) is titled "Not sent" when the node
  refused it (`Transaction.Sora2Submission.genericTitle`). Its journal row is
  still "submission unknown" at that point, so it used to be titled "Submission
  status unknown" or "Transaction submitted" next to the status Failed.
- A transaction the indexer already lists is in a block. A refusal recorded for
  it at submission is then not used to call it failed; it stays pending
  ("waiting for confirmation") until the app's own check of finalized blocks
  has resolved the journal row.
- Failed on chain: the reason is the `DispatchError` of the `ExtrinsicFailed`
  event, for example `LiquidityProxy.SlippageNotTolerated` or
  `Token.FundsUnavailable` (`Sora2DispatchErrorDescriber`). It only describes a
  failure the event counts already proved; an unreadable reason yields no text
  and never changes the status.

### 5. Duplicate protection

`ExtrinsicManager.requireNoWaitingSameAction` runs before signing on every
generic submit path that carries a description. If the same account has an
unresolved journal row with the same kind, the same tokens and the same
counterparty (amounts may differ), it throws
`DuplicateWaitingTransactionException` and nothing is signed. Each screen then
asks: "An earlier one is still waiting ... If you send it again, it may be
carried out twice." with **Wait** and **Send again anyway**. Only the second
button repeats the request, with `duplicateConfirmed = true`.

- A transaction the node refused does not count as waiting.
- If the check itself cannot be made (local read failure), the transaction
  proceeds: a failed read must not block a transaction.
- The legacy claim keeps its own stricter gate (no second claim at all while one
  is unresolved).

### 6. Crashes removed (every `viewModelScope.launch` on these paths reviewed)

| Where | Was | Now |
|---|---|---|
| `ReferralViewModel` init collectors | exception in `onEach` after `.catch` was uncaught (audit crash log: `BoundedHttpTextException` from `getMutationRuntimeContext`) | each collector handles its own work; a failed fee read shows "Network fee unavailable" with Try again and Close |
| `ReferralViewModel` recalculation launches | uncaught | `launchSafely` |
| `ReferralViewModel.calcInvitationsCount` | division by a zero fee | returns 0 until the fee is known |
| `EditFarmViewModel`, `ClaimDemeterViewModel` init and confirm | uncaught | dialog with Try again and Close; outcome handling |
| `FarmDetailsViewModel` init | uncaught | guarded |
| `LiquidityRemoveViewModel` fee read while collecting assets | uncaught | dialog with Try again and Close |
| `ClaimViewModel.checkMigrationIsAlreadyFinished`, status collector | uncaught | guarded |
| `TxDetailsViewModel.calcState`, `AssetDetailsViewModel.refreshActivities` | uncaught | guarded |
| `TransferAmountViewModel` account, balance and amount collectors, token change, fee retry | a failed local read (selected account, XOR row) was uncaught | `guarded`; the collector stays alive |
| `SwapViewModel` recalculation collector, form reset, disclaimer, fee retry | a failed local read was uncaught | handled; the button stops spinning |
| `LiquidityAddViewModel` collectors, token pickers and the reserves read of a pair that is not cached (network) | uncaught | `guarded` |
| `LiquidityRemoveViewModel` amount collectors and percent shortcut | uncaught | `guarded` |

`BaseViewModel.guarded` reports a failure through `onError` and passes
cancellation on, so it is safe inside `collectLatest` (the older `tryCatch`
swallows cancellation).

## User-visible effect

- A swap, liquidity, referral, staking or claim transaction that was broadcast is
  never shown as rejected and never closes the app. It says "Transaction
  submitted", opens the transaction page with "waiting for confirmation, please
  don't send it again", and that page and Activity change to the final result by
  themselves.
- A transaction the network refuses is shown as "Not sent" with the reason, and
  appears in Activity as failed, not as pending.
- A failed transaction says why (the network's error name) and that the fee was
  charged; an expired one says nothing was sent and no fee was charged.
- Starting the same action again while the first is waiting needs a second,
  explicit confirmation.
- Invite Friends, Edit farm, Claim rewards and Remove liquidity explain an
  unreachable network instead of closing the app.

## New strings

29 strings `tx_outcome_*` at the end of every `common/src/main/res/values*/strings.xml`
under the comment `at1-submit-status`. English and Japanese are written; the
other locales carry the English text (as for earlier 3.9.0 strings) so lint
`MissingTranslation` passes.

## Tests

Unit tests (all through production code; only the HTTPS transport, the DAO and
interactor boundaries are doubles):

- `sorasubstrate` `Sora2FinalityWaitTest` (11): failed reads retried with 1, 2, 4
  second pauses and the result still returned; six failures end as pending after
  23 seconds with the journal row untouched; one slow request cannot hold the
  wait past 120 seconds; a shorter limit interrupts a request in flight; polling
  until the limit; a read failure in the middle of polling; failed on chain with
  the runtime error (generic event form, as mainnet decodes); non-module error;
  expired; duplicate lookup rules.
- `sorasubstrate` `FileSora2SubmissionDetailsStoreTest` (11): the description
  survives a restart; refusal and failure reason merge into the same entry and
  are kept on one line; a damaged file or a malformed entry is ignored; an
  unwritable location never throws; pruning by journal, by age and by count.
- `sorasubstrate` `ExtrinsicManagerOutcomeTest` (10): an explicit JSON-RPC
  rejection through the production RPC client is `RefusedByNode`, recorded,
  never marked submitted; `1012`, `1013`, "outdated", `-32603`, `4003` and a lost
  connection stay waiting; finalized results map to confirmed, failed and
  expired; the description is stored before transport; the duplicate guard
  refuses before signing and lets a confirmed repeat through; a failed duplicate
  check never blocks.
- `feature_blockexplorer_impl` `TransactionHistoryRepositoryTest` (+9): a waiting
  swap keeps kind, tokens and amounts after a restart; it is listed under both of
  its tokens only; resolved rows update the entry; refused is failed at once;
  chain proof wins over a recorded refusal; failure reason; expired; journal
  writes tick the refresh flow.
- `feature_blockexplorer_impl` `TxDetailsViewModelTest` (+6): status notes, no
  explorer link for a refused transaction, a failed read does not crash.
- View-model tests for the four outcomes, "not sent" and the duplicate question:
  `SwapViewModelTest` (+6), `AddLiquidityViewModelTest` (+5),
  `RemoveLiquidityViewModelTest` (+5), `ReferralViewModelTest` (+7, including the
  offline crash), `EditFarmViewModelTest` (new, 7), `ClaimDemeterViewModelTest`
  (new, 7), `TransferAmountViewModelTest` (+6), `ClaimViewModelTest` (+8).
- `AssetsInteractorTest`: failed on chain and refused are returned as outcomes
  with the signing key zeroed.

Added in the second pass (all through production code):

- `common` `TransactionNotSentTest` (new, 11): the "Not sent" text for an
  unreachable network, a changed fee, the two signer refusals (direct and
  wrapped by the pre-transport check), read-only mode and any other error; a
  `SoraException` without a message of its own still gets the dialog; a generic
  network or unexpected `SoraException` says nothing was sent; a business error
  keeps its message.
- `feature_wallet_impl` `ClaimViewModelTest` (+3, 1 changed): a waiting claim is
  followed for 48 checks and never sent again; the screen closes by itself once
  the network has the claim; a failed read while following is asked again; a
  claim that was never included is explained and not stored as failed.
- `feature_blockexplorer_impl` `TxDetailsViewModelTest` (+7): the page asks for
  the status of a waiting transaction and stops once it is final; the first
  journal write after a restart changes the page; it stops after 80 checks; a
  final transaction is never asked about; a refused entry known only by its
  hash is titled "Not sent"; the submitted and unknown titles are kept; a
  transaction that never reached a block lists no fee, one that did keeps it.
- `feature_blockexplorer_impl` `TransactionMappersTest` (+3): the Activity row
  titles of an entry known only by its hash (refused, waiting, uncertain).
- `feature_blockexplorer_impl` `TransactionHistoryRepositoryTest` (+3): a
  transaction the indexer lists is not called failed because of a recorded
  refusal; the status check goes to the status-only pass of the coordinator; a
  failed status check is not an error.
- `feature_assets_impl` `TransferAmountViewModelTest` (+2), `feature_polkaswap_impl`
  `SwapViewModelTest` (+1) and `AddLiquidityViewModelTest` (+1): a failed local
  read, or a failed network read of pool reserves, inside a collector does not
  leave viewModelScope uncaught, and the collector keeps working.

Checked against the old behaviour: with the wait loop changed back to "one failed
read ends the wait", three `Sora2FinalityWaitTest` tests fail (retry with pauses,
bounded retries end as pending, read failure in the middle of polling).

The second-pass tests were run once against the main code as it was before the
second pass (the changed view models, `BaseViewModel`, `TransactionOutcomeTexts`
and the activity mapper put back, and the indexer rule taken out of the
repository). 21 of them fail there: 6 in `TransactionNotSentTest`, 4 in
`ClaimViewModelTest`, 7 in the block explorer module (5 `TxDetailsViewModelTest`,
1 `TransactionMappersTest`, 1 `TransactionHistoryRepositoryTest`), 2 in
`TransferAmountViewModelTest`, and 1 each in `SwapViewModelTest` and
`AddLiquidityViewModelTest`. The other new tests guard behaviour that was
already right (for example that a transaction with a final status is never
polled, and that one in a block keeps its fee).

The view-model tests for "waiting", "not prepared" and the offline screens fail
on the previous code: either with the old "rejected" state, or because `runTest`
reports the uncaught exception that closed the app.

Offline check against live data (not part of the unit tests): the dispatch-error
decoder was compiled outside Android with the vendored xsubstrate 1.2.7 and run
on the mainnet spec-133 metadata saved by the audit. All 70 pallets that define
errors number them 0..n-1 in declaration order, and synthetic `ExtrinsicFailed`
events decoded to `LiquidityProxy.InvalidFeeValue`, `Assets.InsufficientBalance`,
`Referrals.ReferrerInsufficientBalance`, `BadOrigin`, `Token.FundsUnavailable`
and `Arithmetic.Overflow` as expected.

Device check (emulator `SORA-Api34`, Android 14, production debug build of this
branch, a wallet created in the app): with airplane mode on, More > Invite
Friends & Earn shows "Network fee unavailable" with Close and Try again. The
audit's run crashed here with `BoundedHttpTextException: BOUNDED_HTTP_IO`. With
the network back, Try again loads the referral page. Logcat has no
`FATAL EXCEPTION`. Screen text was captured with `uiautomator dump`.

Second device check (emulator `SORA-Api34`, port 5592, production debug build of
this branch, a new wallet with no funds; no transaction was signed or sent, and
every confirm was tapped in airplane mode only):

- Invite Friends in airplane mode: "Network fee unavailable" with Close and Try
  again; with the network back, Try again loads the page.
- Enter referrer's link, Activate in airplane mode: dialog "Not sent / SORA
  couldn't reach the network to prepare this transaction. Nothing was sent.
  Check your internet connection and try again." After OK the Activate button is
  there again. The audit saw no message and no button here. The status journal
  stayed empty.
- A waiting swap was simulated by writing one journal row (state
  `SUBMITTED_AWAITING_FINALITY`, a hash that was never signed, the current
  finalized block as its birth) and its description into the app's own storage
  while the app was stopped. Result: the XOR asset page lists it under "Recent
  activity" as "Swapped XOR -> VAL, 1.5 XOR -> 30 VAL", the Activity tab lists
  the same entry, and its page shows "Status Pending" with the note "Submitted.
  The network has not confirmed it yet ... Please don't send it again. The
  status here and in Activity updates by itself."
- The same entry, 64 blocks later: the journal row became
  `EXPIRED_NOT_INCLUDED` at the first finalized block past its window, and the
  page shows "Status Failed" with "The network did not include this transaction
  before it expired. Nothing was sent and no fee was charged. You can try
  again.", no explorer button. This check found that the open page skipped its
  first refresh after a restart; see "The transaction page no longer skips"
  above.
- Duplicate question: with a simulated waiting "set referrer" entry for the same
  referrer, Activate (airplane mode) shows "An earlier one is still waiting /
  You already sent this action from this account ... it may be carried out
  twice" with Wait and Send again anyway. Wait sends nothing and leaves the
  screen as it was. Send again anyway goes on and, being offline, ends in "Not
  sent". The journal still held only the two simulated rows.
- With the final build and a second simulated waiting swap, the page was opened
  after an app restart and left open. It changed from "Pending" to "Failed"
  by itself 8 seconds after the first finalized block past the transaction's
  window (the watcher looked every 4 seconds), and it then listed neither a
  network fee nor an explorer button. The Activity tab listed the entries with
  the spoken marks "Pending" and "Failed", waiting entries first.
- Logcat had no `FATAL EXCEPTION` in any of these steps. Screen texts were
  captured with `uiautomator dump`. The emulator was shut down afterwards.

Not run on a device: confirmed, failed-on-chain and refused outcomes of a real
transaction. They need a funded mainnet account; see the QA steps below.

Build and checks at the end of this task (first and second pass):
`:app:assembleProductionDebug`, `testProductionDebugUnitTest` (all modules) and
`:app:lintProductionDebug` passed. `ktlintCheck` reports no finding on lines
this task changed.

## Human and ops actions

- QA on a funded mainnet test wallet (cannot be automated here, it moves funds):
  1. Swap a small amount and switch to airplane mode right after Confirm. Expect
     "Transaction submitted", then the transaction page with the waiting note.
     Turn the network back on: the page and Activity change to Successful within
     about a minute without any action.
  2. Repeat the same swap while the first is still waiting. Expect the question
     "An earlier one is still waiting" with Wait and Send again anyway.
  3. With an account whose XOR is locked so that the fee cannot be paid, try to
     send. Expect "Not sent" with the fee reason, and a failed entry in Activity.
  4. Open Invite Friends, a farm's Edit and Claim screens and Remove liquidity in
     airplane mode. Expect the "Network fee unavailable" dialog with Try again
     and Close, no crash.
- Support: the reply for "my swap shows pending" is now on the transaction page
  itself. A pending entry resolves by itself; at the latest about 6.5 minutes
  after sending it shows as failed, "not included".
- Translations: the 29 `tx_outcome_*` strings need translation in the production
  locales other than English and Japanese.
- QA, legacy claim (an account that still has an Iroha claim): submit the claim
  and switch to airplane mode right after. Expect "Submitted, waiting for
  confirmation ... This screen continues by itself". Turn the network back on:
  the claim screen closes by itself within about ten seconds of the claim being
  on the network.
- QA, transaction page: in step 1 above, keep the transaction page open. It must
  change from Pending to Successful by itself within a few seconds of the
  network coming back, also when the app was closed and opened again in between.
- Product: the "Not sent" text for an account that may not sign says "This
  account can't sign transactions on this phone right now". If the key-recovery
  work adds a screen for such an account, this text should point to it.
- Product: the foreground wait still lasts up to 120 seconds on a working
  connection. Nothing is misreported in that time; shortening it is a product
  decision.

## Not changed

- The single signing and submission host (audit F1), the metadata download per
  fee quote (F3) and the frozen-balance figure (F4) are other tasks.
- Polkamarkt keeps its own pending journal and failure policy. For it, a node
  refusal is still "unknown" until the journal resolves, exactly as before.
- `ExtrinsicManager.submitExtrinsic` (faucet builds only) is unchanged.
