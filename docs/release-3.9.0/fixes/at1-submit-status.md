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
| the balance read from the network just before sending no longer covers the amount and the fee (`SORA2_TRANSFER_XOR_BALANCE_INSUFFICIENT`, `SORA2_TRANSFER_ASSET_BALANCE_INSUFFICIENT` from `AssetsRepositoryImpl.requireExactTransferBalances`) | "Your balance is no longer enough for this amount and the network fee ... Check your balance and change the amount." Send then reads the balances again; when they arrive the amount is marked as too high and Confirm is off. |
| the signer refused this account (`SIGNING_WALLET_UNAVAILABLE`, `WALLET_DELETION_ACTIVE`) | "This account can't sign transactions on this phone right now ... Your keys were not changed and your funds are safe" |
| the stored signing key is absent or cannot be decrypted (`SigningKeyUnavailableException` from `CredentialsRepositoryImpl.retrieveKeyPair`, `WalletDecryptionException`, a `KeyMaterialUnavailableException` that is not transient) | "SORA can't read this account's signing key on this phone ... Trying again won't change this ... To sign with this account again, you need its recovery phrase or backup." A Keystore that is only busy (transient) keeps "Please try again". |
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
  A waiting claim shows "submitted, waiting" and is not stored as failed; when the
  phone never got the node's answer the text says that SORA could not confirm the
  network received the claim, not that it was submitted.
  The claim screen cannot be left without closing the app, so it follows the claim
  by itself (see "Legacy claim screen: following and closing" below). While an
  earlier transaction of the account is unresolved and may still reach the
  chain, the existing claim gate sends nothing; the screen says "Not sent ... an
  earlier transaction is still being checked ... try again in a few minutes". A
  transaction the node explicitly refused is not one of these (see "Duplicate
  protection"): after "the network was too busy ... please try again in a
  moment" Submit works at once.

#### Opening the transaction page: only from the screen in front

A transaction is followed in the foreground until it is final, typically 15 to
30 seconds and at most 120. The page of the transaction used to be opened from
that coroutine the moment the wait ended (`assetsRouter.showTxDetails(hash,
pop = true)`), whatever was on screen by then. That had two faults:

- The PIN lock is only a destination on the navigation stack
  (`MainActivity.onResume` puts `pincodeFragment` on top after five minutes in
  the background). `showTxDetails(pop = true)` navigated with a pop back to the
  wallet hub, which removes everything above the hub, the PIN lock included.
  Tap Confirm, lock the phone, open the app more than five minutes later: when
  the wait ended behind the PIN screen, the PIN screen was replaced by the
  transaction page and Back led into the wallet. No PIN or biometric was
  entered. The referral screen navigated without the pop and put the page over
  the PIN lock instead. The store version has the same blind navigation after a
  wait of about a second; the wait until finality made the window 15 to 120
  seconds.
- With the activity stopped (Home, app switch, screen timeout) the fragment
  manager has saved its state and ignores both the pop and the navigation.
  Nothing delivered the result again. On return Swap showed a disabled Confirm
  with the amounts reset and no result; Send showed its toast but kept Confirm
  on with the same fee, so a second tap sent a second transfer (without the
  duplicate question when the first was already final); the Demeter button was
  simply active again.

Now:

- `BaseViewModel` knows whether its screen is in front: `SoraBaseFragment`
  calls `onScreenResumed()` in `onResume` and `onScreenPaused()` in `onPause`.
  A screen is not in front while the app is in the background or while another
  destination (the PIN lock) is on top of it.
- `BaseViewModel.showWhenInFront { ... }` holds a result until the screen is in
  front: it runs at once when it is, otherwise on the next resume, and is asked
  again on a later resume when it reports that nothing was shown.
- `AssetsRouter.showTxResult(txHash, from)` (`Navigator`) opens the page only
  while `from` (`TransactionScreen`: Send, Swap, Add liquidity, Remove
  liquidity, Demeter claim, Demeter stake, Referral) is the current
  destination, and answers whether the page is open afterwards. With any other
  destination on top, the PIN lock in particular, it pops nothing, opens
  nothing and answers false. The pop back to the hub therefore only removes
  what the user put there on the way to the screen.
- All seven view models open the page through
  `showWhenInFront { assetsRouter.showTxResult(hash, <their screen>) }`. The
  `pop` option of `showTxDetails` is removed; `showTxDetails(hash)` remains for
  a tap on a list entry.
- While a result is held the screen says what it can and sends nothing more:
  Swap and liquidity keep the result mark ("Transaction confirmed",
  "submitted" or "failed") on the confirm screen with Confirm off; Send has
  Confirm off (the toast is delivered when the screen is shown again); the
  Demeter button is off; the referral screen sends nothing. Dialogs (refused,
  not sent, failed on chain on Send) already went through LiveData and are
  delivered when the screen is shown again.
- Send never confirms a second time once a transfer of that screen reached the
  network (`sentTxHash`): the screen is replaced by the page.

Two orders are possible when the app returns after more than five minutes,
because `MainActivity` asks for the PIN screen asynchronously. Either the PIN
lock is already the current destination when the confirm screen resumes: the
router answers false, the result stays held, and the page opens after the PIN
was entered and the PIN lock removed itself. Or the confirm screen resumes
first: the page opens (the confirm screen is the current destination, nothing
else is popped) and the PIN lock is then put on top of the page. In both the
PIN lock ends on top and stays until the PIN or biometric is entered.

Found on the way: on Remove liquidity every pool update (one per block) rebuilt
both buttons from the same state, which brought Confirm back, enabled and
without its progress mark, in the middle of the wait. On Swap and Add liquidity
going back and tapping Review again did the same. A second tap was caught by
the duplicate question, but the button should not have been there. Confirm now
stays off from the tap until it is known that nothing reached the network
(`confirmLocked` in both liquidity view models, `isExtrinsicSubmitted` in
`SwapViewModel`).

#### Legacy claim screen: following and closing

- The claim screen closes only itself, and only while it is the screen in front.
  `WalletRouter.closeClaimScreen()` (`Navigator`) pops `claimFragment` when it is
  the current destination and does nothing otherwise. `ClaimViewModel` asks for
  it only between the fragment's `onResume` and `onPause`; a claim found done at
  any other time is remembered and the screen closes on its next `onResume`.
  Before, every path that found the claim done called `popBackStack()`, which
  removes whatever is on top. After five minutes in the background that is the
  PIN lock: it was popped, the claim screen below popped itself, and the wallet
  was open with no PIN or biometric entered. This could already happen in the
  store version when a tapped submission finished behind the PIN lock; the
  follow timer of the second pass made it likely.
- Following runs only while the claim screen is in front. It stops in `onPause`
  and starts again, with an immediate check, in `onResume`. Per visit it checks
  at most 48 times, 10 seconds apart.
- Each check asks two things, both read-only (nothing is signed or sent):
  1. `needsMigration()`: does the network have this account's claim? Then the
     screen closes.
  2. `WalletInteractor.checkSubmittedClaim(hash)`: what became of the claim this
     device sent? This runs one status-only pass for that transaction
     (`Sora2PendingSubmissionCoordinator.recoverStatusOnlyFor`) and reads its
     journal row. Confirmed closes the screen. Failed on chain is stored as
     failed (the existing "Transition of assets failed"). Expired without being
     included shows "The network did not include this transaction before it
     expired ... You can try again." Before, the loop only asked the first
     question, got "not yet" 48 times and ended in silence.
- A claim sent before the screen was opened again is followed too. After a PIN
  re-lock the app creates a new claim screen with a new view model. On `onResume`
  it looks up the newest claim this device recorded for the account
  (`latestSubmissionHashOrNull`, from the description stored next to the journal)
  and, when its journal row is still unresolved, says "Submitted, waiting" and
  follows it. The final result of an old claim is not announced again.
- After "Not sent ... an earlier transaction is still being checked" the screen
  starts following as well, so it continues by itself when that earlier
  transaction was a claim.

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
chain proof wins over a recorded refusal. While the row is unresolved it holds
back neither the duplicate question nor the legacy-claim gate.

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
  (`TransactionHistoryRepository.checkWaitingTransaction(walletId, txHash)`: one
  status-only pass of the coordinator for that transaction alone,
  `recoverStatusOnlyFor`; the coordinator has no signer and cannot submit). The
  background worker retries after 30, 60, 120, 240 ... seconds, so without this
  a page opened right after sending could show "Pending" for minutes after the
  transaction was final. A failed check changes nothing and is not shown.
- The page asks only while it is in front. It stops in `onPause` (Home, another
  screen, the PIN lock on top) and on return it is redrawn once and follows
  again if the transaction is still waiting. Before, it went on reading the
  finalized head and blocks every 6 seconds for up to 8 minutes with nobody
  looking at it. The background worker keeps resolving the transaction.
- One transaction that cannot be checked no longer holds the others back. A
  status pass over a batch used to be all or nothing: when the wallet of one
  selected row no longer passed its witness, when the first block of its
  validity window was not the canonical one, when the events of an included row
  could not be read or decoded, or when its journal row changed under the pass,
  the pass threw and no row was resolved. With up to 8 unresolved rows every
  pass selects all of them, so one such row kept every other transaction, also
  of other accounts, on "Pending ... don't send it again", with the duplicate
  question and the legacy-claim gate active. Such a row is now left exactly as
  it is and counted in `failures` (the worker retries), and the other rows are
  resolved. Only a pass asked for that very transaction (the foreground wait,
  the transaction page, the claim screen) still reports its failure. Checks
  that concern the node rather than one row (genesis, finalized height, a hash
  found in two blocks) still fail the whole pass.
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
- A transaction the indexer already lists is in a block. Nothing recorded on
  this device may then say that it never reached one:
  - A refusal recorded for it at submission is not used to call it failed; it
    stays pending ("waiting for confirmation") until the app's own check of
    finalized blocks has resolved the journal row.
  - A journal row that expired as "not included" does not turn the listed entry
    into "The network did not include this transaction ... Nothing was sent ...
    You can try again". The entry stays exactly as the indexer gave it: its
    status, its block, its fee and the explorer link. (The indexer rows shown
    are checked against the canonical block hash, `PiHistoryCheckpointValidator`.)
    With the one endpoint of this branch the two cannot disagree; they could if
    the status scan ever read a block from a node that is behind.
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
- The legacy claim keeps its own stricter gate (no second claim at all while any
  earlier transaction of the account is unresolved). Like the duplicate
  question it does not count a transaction the node explicitly refused
  (`Sora2PendingSubmissionCoordinator.requireLegacyMigrationAdmissible`,
  `Sora2LegacyMigrationAdmissionGate`). Only a row that never left "submission
  unknown" and has a refusal recorded for this account and this hash qualifies.
  A row the node accepted always blocks, and without a record of the refusal
  (the description file is lost or unreadable) the gate blocks as before.
- One Confirm at a time on every screen. Each confirm path sets a flag before
  its coroutine starts and ignores a second tap while it is set: Swap
  (`isExtrinsicSubmitted`), Add and Remove liquidity (`confirmLocked`), the
  legacy claim (`submitting`), and since the fifth pass Send, Referral (bond,
  unbond, set referrer), Demeter stake and unstake and Demeter claim
  (`confirmInFlight`, `submitInFlight`, `claimInFlight`). These four relied on
  the button being hidden behind its progress mark, and the button stays
  clickable while that mark fades in. A second tap started a second coroutine.
  The duplicate question stopped it from signing, but it cleared the progress
  mark of the first and asked "send again anyway?" about a transaction sent a
  moment ago. On the referral screen a balance update during the wait also
  brought the button back; the progress mark now stays until the result.

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
| `LiquidityAddViewModel` collectors and token pickers | uncaught | `guarded` |
| `LiquidityAddViewModel` reserves read of a pair that is not cached (network) | uncaught | caught; Supply is off, "Pool data unavailable" dialog with Try again and Close (see "Screens that must not go on with stale or missing data") |
| `LiquidityRemoveViewModel` amount collectors and percent shortcut | uncaught | `guarded` |

`BaseViewModel.guarded` reports a failure through `onError` and passes
cancellation on, so it is safe inside `collectLatest` (the older `tryCatch`
swallows cancellation).

### 7. Screens that must not go on with stale or missing data

- Add liquidity: the reserves of the chosen pair have to be known, from the
  local cache or from the network, before anything is estimated, reviewed or
  signed. Before, the screen worked with reserves of zero until they arrived,
  and for good when the read failed. Zero reserves are treated like a new pool,
  so both amounts could be typed at any ratio and confirmed; the deposit then
  failed on chain and the fee was charged.
  - While the reserves are being read (offline the read does not fail, it waits
    for the connection; the app's "Connecting..." bar is shown) Supply is shown
    and off. When they arrive the second amount follows the pool's ratio.
  - When the read fails, Supply is off ("Pool data unavailable") and a dialog
    says "SORA couldn't read this pool from the network, so the amounts can't be
    checked right now. Nothing was sent." with **Try again** (reads the reserves
    again; the cache flow never asks a second time for the same pair) and
    **Close** (leaves the screen).
  - The button is refreshed as soon as both tokens are chosen; it used to keep
    "Choose tokens" until the reserves answered.
- Add liquidity, the estimate: besides the reserves, the amounts on screen need
  a successful estimate (`calcLiquidityDetails`: the second amount at the
  pool's ratio, and the network fee, which is read from the network). Before,
  a failed estimate was passed over in silence unless it was a business error.
  On the first Add liquidity of a session with cached reserves and no
  connection the second amount stayed empty with no explanation; with both
  amounts typed by hand Supply and Confirm were on, at a ratio never checked
  against the pool and with a blank fee.
  - A failed estimate drops the earlier one, clears the share, the prices and
    the fee, turns Supply off ("Network fee unavailable") and shows the same
    notice with **Try again** as Send and Swap (`NetworkFeeUnavailableNotice`).
  - Supply is on only while the two amounts are the ones the last successful
    estimate was made for (`estimatedAmounts`). It turns off as soon as an
    amount is edited and comes back with the new estimate. Before, Supply
    stayed on during that time and Confirm could sign the new first amount with
    the second amount of the earlier estimate.
  - Confirm checks the same thing. When it does not hold (the estimate failed
    after the review, or has not arrived), nothing is signed, the screen says
    "Not sent ... SORA couldn't check these amounts and the network fee with
    the network just now ... try again", and the estimate is asked again, so
    the next Confirm works as soon as the network answers.
  - An estimate belongs to its pair: choosing another token drops it.
- Swap: when the request for a price fails, the earlier quote is dropped. Before
  (also in the store version) the error was shown but the old quote stayed, and
  Review and Confirm signed the new amount with the minimum received of the old
  one, so the slippage limit no longer protected the trade. Now the details are
  cleared and both buttons are off ("Price unavailable") until a quote for the
  current pair and amount exists. The price is asked again on the next change of
  the amount, the tokens, the slippage or the pool reserves (which tick with
  every block). A failure of the optional XOR reminder after a valid quote does
  not drop that quote.
- Send: after "the network fee changed ... check the new fee and confirm again"
  the reviewed fee is dropped and asked again for the same token and amount.
  Confirm is off while that runs and comes back with the new fee on screen.
  Before, the old fee stayed and every further Confirm ended in the same dialog
  until the amount was edited. If the new fee cannot be read, the fee field is
  empty and Confirm stays off; the amount step shows the fee notice with Try
  again.

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
- Leaving the app (or locking the phone) after Confirm no longer loses the
  result: on return the transaction page opens. If the PIN screen is shown
  first, it stays until the PIN or biometric is entered, and the page opens
  after that.
- Confirm cannot be tapped a second time for a transaction that is being
  followed or whose result has not been shown yet.

## New strings

36 strings `tx_outcome_*` at the end of every `common/src/main/res/values*/strings.xml`
under the comment `at1-submit-status` (29 from the first two passes; the third
pass added `tx_outcome_claim_uncertain`, `tx_outcome_not_sent_key_unavailable`,
`tx_outcome_pool_unavailable_title`, `tx_outcome_pool_unavailable` and
`tx_outcome_quote_unavailable`; the fifth pass added
`tx_outcome_not_sent_balance` and `tx_outcome_not_sent_estimate`). English and
Japanese are written; the other locales carry the English text (as for earlier
3.9.0 strings) so lint `MissingTranslation` passes. The fourth pass adds no
strings.

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

Added in the third pass (review round; all through production code, only the
transport, the DAO, the router and the interactor boundaries are doubles):

- `sorasubstrate` `Sora2PendingRecoveryIsolationTest` (new, 7): a row whose
  wallet fails its witness, and an included row whose events cannot be decoded,
  do not hold back another row of the pass; a screen's check reads only the 64
  blocks of its own transaction; a check asked for one transaction reports that
  transaction's own failure; nothing is asked for a row that is final, unknown
  or of another account; the journal row is reported as the outcome every
  screen shows (waiting, uncertain, refused, confirmed, failed with its reason,
  expired); the newest transaction of a kind is found from its description.
- `feature_wallet_impl` `ClaimViewModelTest` (rewritten around a fake interactor,
  23): a claim found done while another screen is on top does not navigate, and
  the screen closes on its next resume; a submission confirmed behind another
  screen closes the claim screen only on return; nothing is followed while the
  screen is not in front; a waiting claim that expired, or failed on chain, is
  reported; a claim the journal shows confirmed closes the screen also when the
  claim state cannot be read; a claim sent before the screen was opened again
  is followed; the result of an old claim is not announced again; a submission
  without an answer from the node is not said to be submitted; and the earlier
  outcome tests.
- `app` `NavigatorClaimScreenTest` (new, 4): the claim screen is popped only
  while it is the current destination; nothing is popped while the PIN lock or
  any other screen is on top.
- `feature_wallet_impl` `WalletRepositoryTest` (+4), `WalletInteractorTest` (+1):
  the claim check asks for the claim's hash (or the newest recorded claim),
  reports the journal row also after a failed status read, asks nothing when no
  claim was recorded, and never touches the submitting `ExtrinsicManager`.
- `feature_blockexplorer_impl` `TransactionHistoryRepositoryTest` (2 changed),
  `TxDetailsViewModelTest` (5 changed): the page asks for its own transaction
  alone, not for the batch.
- `feature_polkaswap_impl` `AddLiquidityViewModelTest` (+1, 1 replaced): a failed
  read of the reserves keeps Supply off, estimates nothing, signs nothing and
  offers Try again, which reads them; nothing can be supplied while the
  reserves are still being read.
- `feature_polkaswap_impl` `SwapViewModelTest` (+1): a failed quote clears the
  earlier one, Review and Confirm are off, Confirm signs nothing, and the next
  quote brings Review back.
- `feature_assets_impl` `TransferAmountViewModelTest` (+2): after a changed fee
  the new fee is shown and the next Confirm sends with it; while the new fee
  cannot be read nothing can be confirmed.
- `common` `TransactionNotSentTest` (+2): an absent or undecryptable signing key
  is not told to try again; a Keystore that is only busy keeps "try again".
- `feature_account_impl` `CredentialsRepositoryTest` (+1): `retrieveKeyPair`
  without a stored key throws the typed error and writes nothing.

The third-pass tests were run once with each fix switched off in the main code
(the close guard and the pause in `ClaimViewModel`, the destination check in
`Navigator`, the per-row handling in the coordinator, the reserves and quote
states, the fee refresh, the key classification and the typed error). 22 of
them fail there: 10 in `ClaimViewModelTest`, 3 in `NavigatorClaimScreenTest`,
2 in `Sora2PendingRecoveryIsolationTest`, 2 in `AddLiquidityViewModelTest`, 1 in
`SwapViewModelTest`, 2 in `TransferAmountViewModelTest`, 1 in
`TransactionNotSentTest` and 1 in `CredentialsRepositoryTest`. The tests of
the new read-only coordinator functions and of the repository and interactor
plumbing do not compile against the earlier code.

Added in the fourth pass (second review round; all through production code,
only the router and the interactor boundaries are doubles, and on the device
only the fragments):

- `app` `NavigatorTxResultTest` (new, 7): every screen that sends a transaction
  has its own destination; the page opens while that screen is the current
  destination, with the pop back to the hub for the six screens the page
  replaces and without it for the referral screen; with the PIN lock on top
  nothing is opened and nothing is popped, for all seven; nothing is opened
  from another screen, from the page itself or without a current destination;
  a navigation that was dropped is answered as not done; nothing happens
  without a controller; a tap on a list entry still opens the page on top.
- `app` `NavigatorPinLockStackTest` (new, device test, 4): the app's router and
  its real navigation graph in a real `NavController`; only the fragments are
  replaced by a navigator that shows nothing. For each of the seven screens,
  opened the way the app opens it: the page opens while the screen is in front
  and the stack is hub, page (hub, referral, page for the referral screen);
  with the PIN lock put on top, a result changes nothing on the stack and the
  PIN lock stays the current destination; after the PIN lock removed itself
  the held result opens the page. A result for another screen opens nothing.
- View models, "the result arrives while the screen is not in front":
  `TransferAmountViewModelTest` (+4), `SwapViewModelTest` (+5),
  `AddLiquidityViewModelTest` (+5), `RemoveLiquidityViewModelTest` (+5),
  `ReferralViewModelTest` (+2), `ClaimDemeterViewModelTest` (+2),
  `EditFarmViewModelTest` (+2). For every screen: a result that arrives while
  the screen is paused asks nothing of the router, the page opens exactly once
  on the next resume, and a further Confirm before that sends nothing; while
  the router answers "not in front" (the PIN lock is on top) the result is
  kept and the page opens on the resume after the PIN. For Send, Swap and
  liquidity also: Confirm is off (and the result mark stays) while the result
  is held, a refusal that arrives in the background is still explained by its
  dialog and opens nothing, and Confirm stays off during the wait itself (a
  pool update on Remove liquidity, Review again on Swap and Add liquidity).
- The earlier outcome tests of these seven view models now run with the screen
  in front and expect `showTxResult(hash, <screen>)`.

The fourth-pass tests were run once with the fixes switched off in the main
code (`showWhenInFront` showing at once, the destination check and the
truthful answer in `Navigator`, the Send latch and the three confirm locks).
24 unit tests fail there (3 in `TransferAmountViewModelTest`, 4 each in
`SwapViewModelTest`, `AddLiquidityViewModelTest` and
`RemoveLiquidityViewModelTest`, 2 each in `ReferralViewModelTest`,
`ClaimDemeterViewModelTest` and `EditFarmViewModelTest`, 3 in
`NavigatorTxResultTest`) and 2 of the 4 device tests (the PIN lock on top, and
a result for another screen).

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

Third device check (emulator `SORA-Api34`, port 5592, production debug build of
the third pass, a new unfunded wallet; nothing was signed or sent):

- Swap XOR to VAL online: a quote is shown. In airplane mode, after changing
  the amount, the request for the new price does not fail: it waits for the
  connection, the button shows its progress mark and cannot be tapped. With
  the network back the quote for the new amount appears. (The "Price
  unavailable" state needs a request that fails; it is covered by the
  view-model test.)
- Add liquidity (Explore > Pools > add) in airplane mode, XOR and VAL chosen:
  the button reads "Enter amount" (it read "Choose tokens" before this pass).
  With 1 XOR and 5 VAL typed, the button reads "Supply" and is off. This run
  found that the read waits instead of failing, which is why the reserves now
  have to be known and not only "not failed". With the network back the XOR
  amount changed to the pool's ratio for 5 VAL, the pool share and the fee
  were shown, and the button read "Insufficient balance" (unfunded wallet).
- Logcat had no `FATAL EXCEPTION`. The emulator was shut down afterwards.
- Not reachable on this emulator: the legacy claim screen. It needs an account
  with an outstanding Iroha claim; see the QA steps below, including the one
  for the PIN lock.

Not run on a device: confirmed, failed-on-chain and refused outcomes of a real
transaction. They need a funded mainnet account; see the QA steps below.

Fourth device check (emulator `SORA-Api34`, port 5592, production debug build of
the fourth pass, a new unfunded wallet; nothing was signed or sent):

- `NavigatorPinLockStackTest` (4 tests) passed on the emulator, and 2 of them
  failed there with the fixes switched off (see Tests).
- Every `SoraBaseFragment` now tells its view model when it comes to the front
  and when it leaves it, so the app was walked through: onboarding, PIN setup,
  the wallet hub, Swap (disclaimer and form), Invite Friends, and Home and back
  on Swap and on Invite Friends. Each screen came back as it was left.
- The PIN lock with a confirm screen below it: on Swap, Home was pressed and
  the app opened again 5 minutes 20 seconds later. "Enter pin code" was shown
  and was still there 20 seconds later. After the PIN the Swap form was back.
- Logcat had no `FATAL EXCEPTION`. The emulator was shut down afterwards.
- Not done on a device: a result arriving behind the PIN lock or in the
  background. It needs a transaction that reaches the network, that is, a
  funded wallet; see the QA steps.

Fifth pass tests (unit; production view models, the production submit manager
and status coordinator; only interactors, the database, the description store
and the network are doubles):

- One Confirm at a time: `TransferAmountViewModelTest`, `ReferralViewModelTest`,
  `EditFarmViewModelTest`, `ClaimDemeterViewModelTest`, one test each: a second
  tap while the first transaction is followed starts nothing, keeps the
  progress mark and asks no duplicate question (on the referral screen also
  after a balance update during the wait). One more test each shows that the
  action can be used again after a transaction that did not reach the network.
- Legacy-claim gate: `ExtrinsicManagerSafetyTest` (+4, and the existing gate
  test now runs the production coordinator instead of a mock): a claim the node
  refused does not hold back the next claim; a refused claim next to one that
  is still waiting does; a transaction the node accepted does, whatever was
  recorded; a refusal recorded for another account does not open this
  account's gate.
- "Not sent" for a balance that is no longer enough: `TransactionNotSentTest`
  (+1, both codes), `TransferAmountViewModelTest` (+3): the dialog names the
  balance, the balances are read again and the amount is then marked as too
  high with Confirm off; Confirm is not held back while that read is running;
  a read that fails does not end the app.
- Indexer and an expired row: `TransactionHistoryRepositoryTest` (+1): the
  listed entry keeps the indexer's status, block and fee and has no "not
  included" note.
- Add liquidity estimate: `AddLiquidityViewModelTest` (+4): with the estimate
  failing on a cached pair and both amounts typed, Supply is off, the notice
  is shown, Review and Confirm sign nothing and say why, and Try again
  recovers; an estimate that fails after the review does not let Confirm sign,
  and the next Confirm works when the network answers; an edited amount
  cannot be supplied before its estimate is there; an estimate does not carry
  over to another pair. The outcome tests of this class now enter an amount
  first, as a person does before Review.
- Transaction page: `TxDetailsViewModelTest` (+2): no status check is made
  between pause and resume, also not after a journal write; on return the page
  is read again and the status is followed again; a transaction that became
  final while the page was away is shown on return without another check.

Of these, 15 fail against the code as it was before the fifth pass (checked by
running them with the production Kotlin changes set aside; for the Add
liquidity tests the old view model was given the two new names they use,
`estimateUnavailable` and `onRetryEstimate`, with the old behaviour). The five
"can be used again" and "is not held back" tests pass before and after: they
guard the new one-at-a-time flag against staying set.

Fifth device check (emulator `SORA-Api34`, port 5592, production debug build of
the fifth pass, a new unfunded wallet; nothing was signed or sent):

- Add liquidity (Explore > Pools > add), XOR and VAL chosen with the network
  on, then airplane mode before the first estimate of the app process.
  - 1 XOR typed: the notice "The network fee can't be checked because the SORA
    network can't be reached right now. Check your internet connection and try
    again. Your funds are safe on the blockchain." with Try again is shown
    above the button. The VAL amount stays empty; the button reads "Enter
    amount" and is off.
  - 5 VAL typed by hand: the button reads "Network fee unavailable" and is off.
    Before this pass it read "Supply" and was on.
  - Try again while offline: unchanged. Network on, Try again: the XOR amount
    changed to the pool's ratio for 5 VAL, the pool share and the fee
    (0.10002361 XOR) were shown, the notice was gone, and the button read
    "Insufficient balance" (unfunded wallet).
- Found in this run and fixed: with the fee unknown the low-balance reminder
  read "Ensure that you have  left after this transaction", because the fee it
  names was blank. (Before this pass that could happen when the very first
  estimate failed; clearing the fee after a failed estimate would have made it
  happen after every failed one.) The reminder is now shown only while the fee
  is known (`LiquidityAddScreen`). A second run showed the notice without it.
- Invite Friends opened; Home and back left it as it was. Logcat had no
  `FATAL EXCEPTION`. The emulator was shut down afterwards.
- Not done on a device: the double tap, the stale balance on Send, the claim
  gate and the pause of the transaction page. They need a transaction that
  reaches the network or an account with an outstanding claim; see the QA
  steps.

Build and checks at the end of the fifth pass: `:app:assembleProductionDebug`,
`testProductionDebugUnitTest` (all modules, 1407 tests, 10 skipped, none failed)
and `:app:lintProductionDebug` passed, with no change to the lint baseline.
`ktlintCheck` reports no finding on lines this pass changed (the project has
985 older findings).

Build and checks at the end of the fourth pass: `:app:assembleProductionDebug`,
`testProductionDebugUnitTest` (all modules, 1384 tests, 10 skipped, none failed)
and `:app:lintProductionDebug` passed. `ktlintCheck` reports no finding on
lines this pass changed (the project has 985 older findings).

Build and checks at the end of the third pass: `:app:assembleProductionDebug`,
`testProductionDebugUnitTest` (all modules, 1352 tests, 10 skipped, none failed)
and `:app:lintProductionDebug` passed. `ktlintCheck` reports no finding on
lines this pass changed (the project has 985 older findings).

Build and checks at the end of the first and second pass:
`:app:assembleProductionDebug`, `testProductionDebugUnitTest` (all modules) and
`:app:lintProductionDebug` passed. `ktlintCheck` reports no finding on lines
this task changed.

## Third pass: review findings

| Finding | Decision |
|---|---|
| Major: the claim follow timer could pop the PIN lock, so the wallet opened without authentication | Fixed. The claim screen pops only itself, only while it is in front; following runs only while it is in front. |
| The claim follow only detected success; a new claim screen after the PIN lock followed nothing; a submission without an answer was called "submitted" | Fixed (three findings). |
| Add liquidity went on with zero reserves after a failed read, without a message for an I/O error | Fixed (two findings), and extended to a read that is still in flight. |
| A status pass failed as a whole when one row could not be checked | Fixed in the coordinator; the page and the claim screen ask for their own transaction alone. |
| A failed swap quote left the earlier quote in place (already in the store version) | Fixed. |
| "Not sent" asked for another try when the signing key is absent or unreadable | Fixed with a typed error and its own text. The dialog still ends in OK only; see the product note below. |
| After "the network fee changed" Send kept the old fee | Fixed. |

Left as it is, on purpose:

- A journal row whose own wallet no longer passes the witness stays unresolved,
  as before. It no longer affects other accounts, but for that account the
  duplicate question and the claim gate stay active until its wallet passes the
  witness again. Resolving such a row without the witness would weaken the
  check; whether that state can be reached at all in this build was not
  established by the review either.
- The in-memory migration status (`PrefsWalletDatasource.migrationStatusFlow`)
  is per process, not per account, as in the store version. A claim screen
  opened for a second account in the same process still receives the first
  account's "done". This pass does not widen it (a claim found done on a return
  to the screen is not stored there).

## Fourth pass: review findings

| Finding | Decision |
|---|---|
| Major: on every confirm path other than the legacy claim, the result opened the transaction page with a pop back to the hub from the coroutine, which removed a PIN lock put on top in the meantime (Send, Swap, Add and Remove liquidity, Demeter claim and stake); the referral screen put the page over it | Fixed. The page opens only while the screen that sent the transaction is the current destination and in front. See "Opening the transaction page: only from the screen in front". |
| Major: with the activity stopped at the end of the wait the navigation was dropped and nothing delivered the result again; Send kept Confirm on, so a second tap sent a second transfer | Fixed by the same change: the result is held in the view model and shown on the next resume; Confirm is off meanwhile, and on Send for good once a transfer reached the network. |

Decisions:

- The result is delivered by the view model on `onResume`, as `ClaimViewModel`
  does, not by a LiveData navigation event. A LiveData observer becomes active
  at `onStart`, before `MainActivity.onResume` asks for the PIN screen; that
  order would have been safe too (the PIN lock would land on top of the page),
  but the router check is the guard that matters, and holding the result in
  one place keeps "is it shown yet" a plain fact the screen can use for its
  button.
- The router answers from the stack after navigating instead of assuming
  success, so a dropped navigation can never clear a held result.
- Failed on chain keeps its earlier presentation: the page on Swap, liquidity,
  referral and Demeter; a dialog on Send, where the screen stays and Confirm is
  available again (the failed transfer is final, so a repeat is a new,
  deliberate transfer).
- Confirm staying off during the wait on Remove liquidity, Swap and Add
  liquidity was not in the review. It is the same harm (a second signature by
  habit) and was one line each.

Not done:

- Nothing else that navigates blindly was changed. `PinCodeViewModel` still
  pops with `popBackStack()` after a correct PIN, which is right while the PIN
  lock is on top, and with this pass nothing on these paths can put a screen
  over it. The explicit taps that close a screen (`router.popBackStack()` from
  a dialog's Close, the toolbar arrow) act on the screen the user is looking
  at.
- No real transaction was sent on a device (unfunded test wallet), so the
  return from the background with a real result is covered by the view-model
  tests and the navigation by the device test of the stack; see the QA steps.

## Fifth pass: review findings

Six findings, all minor, all confirmed against the code and fixed.

| Finding | Decision |
|---|---|
| Send, Referral, Demeter stake/unstake and Demeter claim had no in-flight guard; a double tap ended in the duplicate question about a transaction sent a moment ago, with the progress mark cleared | Fixed with a flag set before the coroutine starts, as the review proposed. See "Duplicate protection". |
| After a node refusal the legacy-claim gate blocked the retry for about 6.5 minutes with a text that contradicted the refusal dialog | Fixed in the gate (the first of the two proposed ways): a refused row does not block, as in the duplicate question. The other way, a text that says "about seven minutes", would have kept the wait. |
| "Not sent" said "please try again" when the last balance check before transport failed | Fixed with its own text, and Send reads the balances again. |
| A transaction the indexer lists was shown as "not included, nothing was sent, you can try again" when its journal row had expired | Fixed: the indexer's entry stands. Not reachable with the one endpoint of this branch. |
| Add liquidity passed over a failed estimate and could enable Supply and Confirm | Fixed: a successful estimate for the amounts on screen is required; notice with Try again. |
| The transaction page kept asking for the status while not in front | Fixed: stops on pause, redraws and follows again on resume. |

Decisions:

- The in-flight flag is cleared when the coroutine ends, on every path. After
  a transaction that reached the network the screens' own state keeps the
  action off (`sentTxHash` on Send, the held result elsewhere), so the flag
  does not need to outlive the coroutine.
- The claim gate reads the refusal from the description file, which is "never
  an authority for status". It is used here only to stop holding the person
  back, in the same way the duplicate question already uses it, and only for a
  row the journal itself still has as "submission unknown". Every missing or
  unreadable record leaves the gate closed.
- For a listed transaction whose row expired the indexer's own status is
  shown, not "being checked": the row is final, so nothing would ever move the
  entry on from "being checked".
- On Add liquidity a Confirm that cannot sign shows a dialog instead of a
  disabled button: the confirm screen has no place for the notice, and a
  disabled button with no text would not say why.
- Found on the way and fixed with the same change: on Add liquidity, editing
  an amount left Supply on with the earlier estimate until the new one arrived
  (about a second, longer on a slow connection), and Confirm in that time
  signed the new first amount with the old second amount.

Left as it is:

- After 80 checks (8 minutes) the transaction page starts another round when
  the journal changes and the transaction is still waiting. This only happens
  while the page is in front.
- The found uncommitted work in this clone (a two-endpoint RPC failover in
  `BoundedHttpTextClient`, `Sora2BoundedRuntimeRpcClient`,
  `Sora2RuntimeContract` and a new `Sora2RuntimeRpcFailoverTest`) is not part
  of this task. It was not committed and not discarded: it is in `git stash`
  of this clone and as a patch outside it; see "Human and ops actions".

## Human and ops actions

- Integration: this clone held uncommitted work of another task when the
  fifth pass started (two-endpoint RPC failover, last modified 02:09 on
  12 October, about two hours earlier; four changed files and one new test).
  It was set aside unchanged with `git stash` (entry "foreign WIP found before
  at1-submit-status fix round 3") and saved as
  `scratchpad/and-tx-foreign-wip/failover-wip-tracked.patch` plus
  `Sora2RuntimeRpcFailoverTest.kt`. Whoever owns that task restores it with
  `git stash pop` in this clone. Nothing of it is in this branch.
- QA, fifth pass (unfunded wallet is enough for the first three):
  1. Add liquidity: start the app, go to airplane mode, open Add liquidity for
     a pair you have opened before and type an amount. Expect the notice "The
     network fee can't be checked because the SORA network can't be reached
     ..." with Try again, the button "Network fee unavailable" and off. Type
     the second amount: still off. With the network back, Try again fills the
     second amount at the pool's ratio and shows the fee.
  2. Transaction page: open the page of a waiting transaction and press Home.
     In a network log the status reads (`chain_getFinalizedHead`,
     `chain_getBlock`) stop; they start again when the app is opened.
  3. Double tap: on Send, Invite Friends (bond) and a farm's Claim tap the
     confirm button twice quickly (funded wallet). Expect one transaction and
     no "An earlier one is still waiting" question.
  4. Send with a stale balance (funded wallet, two devices with the same
     account): on device A enter an amount close to the whole balance and
     stop on the confirm screen; on device B send most of the balance away and
     wait for it to be confirmed; tap Confirm on A. Expect "Not sent ... Your
     balance is no longer enough ...", then the amount marked as too high.

- QA, result after leaving the app (funded mainnet test wallet): on Swap tap
  Confirm and press Home at once. Open the app again after about 40 seconds.
  Expect the transaction page with the final status, not the confirm screen
  with empty amounts. Repeat on Send: expect the toast and the transaction
  page, and no way to tap Confirm a second time. Repeat on a farm's Claim.
- QA, result behind the PIN lock (security check of the fourth pass; funded
  wallet): on Send tap Confirm and lock the phone at once, before the result
  mark appears. Wait more than five minutes, unlock the phone and open the
  app. The PIN screen must be shown and must stay, however long it is left
  open; Back must close the app. After the PIN or biometric the transaction
  page opens. Repeat with Swap and with Invite Friends > bond.

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
- Translations: the 36 `tx_outcome_*` strings need translation in the production
  locales other than English and Japanese.
- QA, legacy claim (an account that still has an Iroha claim): submit the claim
  and switch to airplane mode right after. Expect "Submitted, waiting for
  confirmation" with the text that SORA could not confirm the network received
  the claim (when the connection dropped during the submit) or that it was
  submitted. Turn the network back on: the claim screen closes by itself within
  about ten seconds of the claim being on the network; if the claim was never
  included, the screen says so about six and a half minutes after the submit and
  Submit can be used again.
- QA, legacy claim and the PIN lock (security check of the third pass; needs the
  same kind of account): with a claim "Submitted, waiting", press Home, wait
  more than five minutes with the network on, and open the app again. The PIN
  screen must stay until the PIN or biometric is entered, however long it is
  left open. After the PIN, the wallet opens (claim done) or the claim screen is
  shown again and follows the claim.
- QA, Add liquidity: choose a pair you have no pool position in, in airplane
  mode. Expect "Pool data unavailable" with Close and Try again, and Supply off.
  With the network back, Try again enables the screen.
- QA, Swap: enter an amount, wait for the quote, switch to airplane mode and
  change the amount. Expect "Price unavailable" on the button and empty details;
  Review must not be available until the network is back and a new quote is
  shown.
- QA, transaction page: in step 1 above, keep the transaction page open. It must
  change from Pending to Successful by itself within a few seconds of the
  network coming back, also when the app was closed and opened again in between.
- Product: the "Not sent" texts for an account that may not sign ("This account
  can't sign transactions on this phone right now") and for one whose signing
  key is absent or unreadable ("... you need its recovery phrase or backup") end
  in OK only. When the key-recovery work adds a screen for such an account,
  these two dialogs should offer to open it.
- Product: the foreground wait still lasts up to 120 seconds on a working
  connection. Nothing is misreported in that time; shortening it is a product
  decision.

## Not changed

- The single signing and submission host (audit F1), the metadata download per
  fee quote (F3) and the frozen-balance figure (F4) are other tasks.
- Polkamarkt keeps its own pending journal and failure policy. For it, a node
  refusal is still "unknown" until the journal resolves, exactly as before.
- `ExtrinsicManager.submitExtrinsic` (faucet builds only) is unchanged.
