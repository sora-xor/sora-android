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
every screen now shows "Not sent ... Nothing was sent" instead of nothing.

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
  only a claim that failed on chain as failed. A waiting claim shows "submitted,
  waiting" and is not stored as failed. A second tap while the first is
  unresolved (the existing claim gate) shows the same message.

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

27 strings `tx_outcome_*` at the end of every `common/src/main/res/values*/strings.xml`
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

Checked against the old behaviour: with the wait loop changed back to "one failed
read ends the wait", three `Sora2FinalityWaitTest` tests fail (retry with pauses,
bounded retries end as pending, read failure in the middle of polling).

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

Not run on a device: the outcome screens themselves. They need a funded mainnet
account; see the QA steps below.

Build and checks at the end of this task: `:app:assembleProductionDebug`,
`testProductionDebugUnitTest` (all modules) and `:app:lintProductionDebug`
passed. `ktlintCheck` reports no finding on lines this task changed.

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
- Translations: the 27 `tx_outcome_*` strings need translation in the production
  locales other than English and Japanese.
- Product: the foreground wait still lasts up to 120 seconds on a working
  connection. Nothing is misreported in that time; shortening it is a product
  decision.

## Not changed

- The single signing and submission host (audit F1), the metadata download per
  fee quote (F3) and the frozen-balance figure (F4) are other tasks.
- Polkamarkt keeps its own pending journal and failure policy. For it, a node
  refusal is still "unknown" until the journal resolves, exactly as before.
- `ExtrinsicManager.submitExtrinsic` (faucet builds only) is unchanged.
