# at2-endpoints-balances: no single point of failure for sending, correct transferable XOR, headroom for chain upgrades

Fixes these findings of the 10 October 2026 audit:

| Finding | Source | Status |
|---|---|---|
| F1 / LIVE-4 / TX-HOST: every fee quote, signature and submission depends on one host | `and-signing.md`, `live-env.md`, `verdicts.md` | fixed (section 1) |
| F4 / LIVE-2 / TX-FROZEN: locked XOR shown and treated as transferable | same | fixed (section 2) |
| F3: 1.25 MB metadata download for every quote and signature | `and-signing.md` | fixed (section 3) |
| F9 / LIVE-12: a new signed extension or metadata above 2 MiB stops all sends | same | limit raised; unknown extension now refused with a clear message (section 3) |
| TX-HOST-FROZEN new finding: Send holds the wallet lock on an untimed WebSocket read | `verdicts.md` | fixed (section 4) |
| F6 / LIVE-7: Activity cannot list received transfers; Android needs a release once the indexer can | `and-signing.md`, `live-env.md` | Android part fixed (section 5); the indexer change is still needed |
| LIVE-13: indexer data depends on the phone's clock | `live-env.md` | fixed (section 6) |

No wallet key material, account row, address, derivation or signing code is
touched. The database schema is unchanged (no Room migration). The status journal
keeps its schema and state machine. Genesis pinning and the SS58 prefix check are
unchanged and now apply to both endpoints. The runtime-identity comparison before
transport still compares genesis, runtime versions, metadata and types exactly;
only the finalized height may now be up to 10 blocks lower than the one signed
against (section 1).

## 1. Two reviewed endpoints

### What was wrong

`https://ws.mof.sora.org` was the only host for the chain identity, metadata,
nonce, fee quote, the one submission attempt and status checks. With that host
down, or blocked on a user's network, nobody on 3.9.0 could get a fee quote or
send, and choosing another node in More > Nodes did not help (3.8.6.x signed over
the selected node). `https://mof2.sora.org` was asked only for old state, and only
after `ws.mof` had answered "state already discarded", so with `ws.mof`
unreachable even the status of a sent transaction could not be read.

### What changed

`Sora2RuntimeContract.SECOND_RUNTIME_RPC_ENDPOINT = https://mof2.sora.org` (another
mainnet node, other host and address, an archive node; it replaces
`STATUS_ARCHIVE_RPC_ENDPOINT`). `Sora2BoundedRuntimeRpcClient` uses both under one
set of rules:

- **Reads fail over.** Chain identity, runtime code hash, metadata, nonce, fee,
  blocks and storage are answered by the first endpoint that gives an acceptable
  answer. Every answer passes the same strict validation whichever endpoint gave
  it. A read is retried on the other endpoint for any failure (no connection,
  timeout, HTTP error, malformed answer, JSON-RPC error, including "state already
  discarded"). When both fail, the first failure is reported with the second
  attached.
- **An endpoint proves its chain first.** Each endpoint must itself report the
  mainnet genesis hash before it serves anything else, and loses that admission
  when it reports another one. `RuntimeManager` still checks the genesis of the
  identity it receives, as before.
- **Nothing is assembled from two endpoints.** The four requests of one chain
  identity, and a block hash with its block, always come from one endpoint.
- **A block counts as finalized only on the endpoint that says so.** Two nodes
  finalize the same chain a moment apart. A block is read by number only from an
  endpoint whose own finalized head has reached it (otherwise that endpoint is
  skipped for the block), and the chain identity prefers an endpoint that is not
  behind a finalized height this process was already given. When every reachable
  endpoint is behind, the most advanced answer is used; nothing is locked out.
- **A failed endpoint is tried last, never dropped.** After a transport failure
  an endpoint is tried after the other one for 30 s, doubling to at most 5 min
  while it keeps failing. Connecting gets 10 s instead of 30 s, so a dead host
  costs one short wait and then nothing until the pause ends.
- **An endpoint that answers but no longer follows the chain is tried last too.**
  A node that is restarting, syncing or cut off from its peers answers every
  request, from a block the network left long ago. When the chain identity is
  read, the other endpoint is asked for its finalized height as well (two small
  requests, three the first time; at most once every 2 minutes per endpoint, with
  a 3-second limit, and not while that endpoint is paused after a failure). An
  endpoint whose finalized head is more than 10 blocks (about one minute) behind
  the other's is moved behind it for 2 minutes at a time, for identity, nonce,
  fee and submission alike, until it reports a height that has caught up. It is
  still used when the other endpoint gives no answer. Without this, a first
  endpoint that was merely stale stayed the only source for as long as it
  answered, and every send failed although the second endpoint was healthy.
- **The check before transport accepts a head a few blocks lower.** A transaction
  is signed against the finalized block of one endpoint; the last check before it
  is handed over may be answered by the other one, exactly when the first has
  just dropped, and that one can be a block or two behind. Genesis, spec and
  transaction version, metadata and types are still compared exactly; only the
  height may be up to 10 blocks lower. The node that receives the transaction
  checks the signed block hash itself. A head further behind than that is not
  handed the transaction: the result is "Not sent" with the network wording
  ("couldn't reach the network ... try again") instead of the generic one.
- **A signed transaction is delivered to at most one node.**
  `submitExtrinsicOnce` tries the second endpoint only when the attempt on the
  first provably never reached its node: the HTTPS client reports
  `requestNotSent` only when the failure happened before the first byte of the
  request body was handed to the connection (name lookup, TCP connect, TLS, or a
  deadline during them). A server cannot act on a POST whose body it never
  received. After any other outcome (an answer of any kind, a timeout, a
  connection that broke later, a gateway error such as HTTP 502) the bytes are
  never offered to another endpoint: the result is "submitted, waiting" and is
  resolved by transaction hash from finalized blocks, through either endpoint.
- **"Not sent" when nothing left the phone.** When neither endpoint could be
  connected to, `Sora2SubmissionNotSentException` becomes the definitive outcome
  `TransactionNodeRefusal.NOT_REACHED`: the confirm screen and Activity say "Not
  sent ... never left your phone ... no fee was charged". Before, this was shown
  as "Transaction submitted" for about seven minutes. The journal row is left as
  it is and expires by itself, exactly like a node refusal (at1-submit-status).
- **Status recovery works without the first endpoint**, and a status pass that is
  answered by an endpoint a few blocks behind the previous one no longer fails
  (`SORA2_RECOVERY_FINALITY_BEFORE_BIRTH`,
  `SORA2_RECOVERY_FINALIZED_HEIGHT_REGRESSION` are gone): the row is simply still
  unresolved in that pass and the next pass continues where the scan stopped.

Files: `common/.../network/BoundedHttpTextClient.kt`,
`sorasubstrate/.../runtime/Sora2BoundedRuntimeRpcClient.kt`,
`Sora2RuntimeContract.kt`, `.../substrate/Sora2SubmissionRefusal.kt`,
`ExtrinsicManager.kt`, `Sora2PendingSubmissionCoordinator.kt`,
`common/.../domain/TransactionOutcome.kt`,
`common/.../presentation/transaction/TransactionOutcomeTexts.kt`,
`feature_blockexplorer_api/.../txhistory/TransactionModel.kt`,
`feature_blockexplorer_impl/.../TransactionHistoryRepositoryImpl.kt`,
`.../txdetails/TxDetailsViewModel.kt`.

Review round: `Sora2BoundedRuntimeRpcClient.kt` (height comparison),
`Sora2RuntimeContract.kt` (`requireSameSora2MutationRuntimeIdentity`).

### User-visible effect

- With `ws.mof.sora.org` down, unreachable or blocked: fee quotes, Send, Swap,
  liquidity, referral, Demeter and the legacy claim keep working through
  `mof2.sora.org`, after one wait of at most 10 seconds. Pending transactions
  keep resolving.
- With `ws.mof.sora.org` answering but stalled or syncing: the same, within one
  fee quote. Before, sending stayed unavailable until that node caught up.
- With `ws.mof.sora.org` dropping between Confirm and the hand-over: the
  transaction is handed to `mof2.sora.org`. Before, this was "Not sent" and the
  user had to confirm again.
- With both down or the phone offline: as before, "The network fee can't be
  checked because the SORA network can't be reached", Try again. A confirmed
  transaction that could not be handed to any endpoint is "Not sent".
- The node chosen in More > Nodes still only moves the WebSocket that balances
  are read from.

## 2. Transferable XOR

### What was wrong

Mainnet's `System.Account` balance is `{free, reserved, frozen, flags}` since
runtime 124 (6 May 2026). The app (and 3.8.6.x) read `miscFrozen` and `feeFrozen`,
which no longer exist, so both were zero and transferable XOR was the whole free
balance. About one funded account in ten has `frozen > 0` (staking, voting,
vesting). A transfer above the real amount reached the chain, failed and still
cost the fee; for about half of those accounts the node refused it outright.

### What changed

- `decodeXorAccountData` (`sorasubstrate/.../substrate/XorAccountData.kt`) reads
  `frozen` when the live runtime has it and the old names otherwise.
- `frozen` limits two things differently, and both limits are kept:
  - **Transfers.** A transfer may take the free balance down to
    `frozen - reserved`. The part of free that is locked against transfers is
    `min(max(frozen - reserved, 0), free)` (`BalanceWrapper.lockedFreeBalance`).
    This is pallet_balances' reducible balance (SORA has no existential deposit)
    and what the iOS candidate computes.
  - **Fees.** Every transaction pays its fee in XOR first, and that withdrawal
    may only take the free balance down to `frozen`; the reserved balance gives no
    discount (`Currency::withdraw` in pallet_balances, used by SORA's xor-fee
    pallet). The part of free that cannot pay a fee is `min(frozen, free)`
    (`BalanceWrapper.feeLockedFreeBalance`). It differs from the first only for
    an account that has both reserved and frozen XOR.
- Transferable XOR (`BalanceWrapper.calcTransferable`) is zero when no fee can be
  paid at all (`free <= frozen`), and otherwise `free` minus the transfer lock,
  never negative. The same rule is applied to the live read (`getXORBalance`),
  the stored row (`mapLocalBalance`) and the last check before a transfer leaves
  the phone (`requireExactTransferBalances`).
- A fee that would come out of frozen XOR is caught before the node has to refuse
  it. On Send, the amount screen says "Not enough unlocked XOR for the network
  fee" and Review stays off. The last check before transport ends the transfer as
  "Not sent ... the XOR it would be paid from is locked ... no fee was charged"
  (`SORA2_TRANSFER_XOR_FEE_LOCKED`). The other fee-paying screens (Swap,
  liquidity, referral, Demeter) do not make this check; there the node's refusal
  is shown, which names the fee and the lock as well.
- The two limits are carried in the existing `miscFrozen` (transfers) and
  `feeFrozen` (fees) fields and columns, so there is no schema change. Under the
  old layout they hold `max(misc_frozen, fee_frozen)` and `fee_frozen`, which
  gives the amounts the app always showed there.
- **The locked amount is shown.**
  - Asset page: the Frozen total and the Locked row of the breakdown show the
    real amount (they read 0 before). The breakdown is now read for every XOR
    account. Before, it was read only when something was transferable, so an
    account whose XOR was all locked saw "Frozen 0" and a card that did not open.
    Under the Frozen amount the card says "110 XOR is locked on the network, for
    example by staking or voting, and can't be sent." The read from the selected
    node is limited to 10 seconds; after that, or when it fails, the amounts saved
    with the balance are shown instead of an error or an endless spinner. All
    amounts on the card come from the same reading, so they always add up.
  - Home and asset lists: the XOR row shows the liquid amount, as before, and
    names the rest in the same line: "0 XOR · 110 XOR frozen" (reserved, bonded
    and locked together, the same figure as Frozen on the asset page).
  - Send: a line under the amount names the locked XOR.

### User-visible effect

An account with locked XOR sees a smaller, correct balance on Send, Swap,
liquidity and referral, the percent and Max buttons stop at what the chain
allows, and the lock is named. Accounts without locks see no change.

**The XOR figure on the home screen and the portfolio total drop by the locked
amount**, because both show liquid funds, as they did until the chain changed
its layout in May 2026 and as the iOS app does. An account whose XOR is all
locked (free 110, frozen 110) showed "110 XOR" in 3.8.6.x and now shows
"0 XOR · 110 XOR frozen"; its asset page shows Liquid 0, Frozen 110 with the
reason, and no Send button. In the verifier's sample this is about 4 % of funded
accounts. Nothing is lost: what the account holds (free + reserved + bonded) is
the liquid amount plus the frozen amount on the same row and card.

## 3. Headroom for chain upgrades

### What was wrong

- Every fee quote, signature and pre-transport check downloaded the complete
  runtime metadata (1,247,270 bytes on the wire, uncompressed): three downloads
  per transfer and one per changed amount, with a 45-second deadline. Below about
  220 kbit/s no fee could be quoted.
- The metadata response limit was 2 MiB although `RuntimeManager` accepts 16 MiB.
  Metadata grew 57 % in one upgrade in May 2026; past 2 MiB every send would have
  stopped until an app update.
- A new signed extension that carries data (for example `CheckMetadataHash`) would
  have produced malformed transactions that every node rejects: the vendored
  encoder silently skips identifiers it does not know.

### What changed

- **Metadata once per runtime.** `RuntimeManager.getMutationRuntimeContext` asks
  for the hash of the runtime code (`state_getStorageHash(":code", finalizedHash)`,
  a 32-byte answer). While that hash, the spec version and the transaction version
  equal what the active snapshot's metadata was read for, the snapshot is the live
  runtime and nothing is downloaded. Metadata is produced by the runtime code
  alone, and every runtime upgrade replaces the code, so the hash is at least as
  strict as the spec version. Any difference, or no answer, takes the old path
  (download, hash, compare). The context still carries the same metadata hash, so
  the comparison between the signing context and the pre-transport context is
  unchanged.
- The binding (genesis, versions, metadata hash, code hash) is saved in the cache
  file `sora2_runtime_code_binding_v1.json`, so the first quote after an app start
  does not download either. A missing, unreadable or foreign file costs one
  download and nothing else.
- **Limits.** Metadata response limit 16 MiB (`MAXIMUM_METADATA_RESPONSE_BYTES`),
  the limit `RuntimeManager` already applies: room for metadata about 13 times
  today's size. The one metadata download may take 150 s instead of 45 s.
- **gzip.** Every request asks for gzip and a gzip body is decoded under the same
  byte bound as a plain one. The mainnet nodes and the indexer do not compress
  today (checked 11 October); the client is ready when they do. Only data that is
  not valid gzip is a malformed body (`BOUNDED_HTTP_ENCODING_INVALID`). A read
  that times out, a connection that is cut and a body that ends early are network
  failures (`BOUNDED_HTTP_IO`, `BOUNDED_HTTP_DEADLINE`) exactly as without
  compression, so the indexer's offline data is still used for them.
- **Unknown signed extension.** `Sora2SignedExtensionAdmission` reads the signed
  extensions from the live V14 metadata. An extension whose part of the extrinsic
  or of the signed payload is not zero-sized, and that the encoder does not write,
  makes the runtime unusable for signing: fee quotes and signing fail with
  `SORA2_APP_UPDATE_REQUIRED` before anything is built. Every screen that asks
  for a fee shows "The SORA network was upgraded, and this version of the app
  can't prepare transactions for it yet. Please update the app ...", with a button
  that matches the text: **Update**, which opens the app's page in the store
  (`AppStoreListing`: the store app, or the browser when there is none). Try
  again is not offered there, because asking again gives the same answer. Send,
  Swap and Add liquidity show it as the fee notice; Remove liquidity, Referral,
  Demeter claim and Edit farm as their dialog with Update and Close (one shared
  `BaseViewModel.showFeesUnavailable`). A confirm that hits it says "Nothing was
  sent ... Please update the app and try again." Balances and Activity keep
  working. An extension without data (for example `CheckNonZeroSender`) is
  accepted, as before.

Cost of one fee quote now: 4 small requests for the identity, 1 for the code hash,
1 nonce, 1 fee (about 2 KB in total) instead of those plus 1.25 MB.

Still needing an app update after a runtime upgrade (unchanged, fail closed): a
renamed or re-typed argument of a call the app builds, a removed type path used by
the alias file, and a signed extension with data (now announced to the user).

## 4. Wallet lock and the selected node's WebSocket

The last balance check before a transfer leaves the phone reads over the selected
node's WebSocket while the wallet lock is held. A disconnected socket keeps a
request queued without a time limit, and switching nodes takes the same lock: with
the selected node's socket down and HTTPS working, a confirmed Send spun until
that node came back and the app could not switch away from it.

`AssetsRepositoryImpl.requireExactTransferBalances` now gives each of its two reads
15 seconds (`FRESH_BALANCE_READ_TIMEOUT_MILLIS`). When they pass, the transfer
ends before transport as "Not sent ... couldn't reach the network ... try again",
the staged journal row is removed as for every pre-transport failure, and the lock
is released, so the node switch can go ahead.

## 5. Received transfers in Activity

The PI indexer deployed today lists only operations an account signed; a filter on
the recipient is rejected with `BAD_USER_INPUT`. Android had this as a compile-time
constant (`PiHistoryCoverage.LISTS_RECEIVED_TRANSFERS = false`), so the indexer fix
would have needed an app release.

Now `PolkaswapIndexerClient` asks the question at run time, as iOS does:

- Account history asks for every role first: `or` over `address`, `dataFrom`,
  `dataTo` (the same filter the iOS app sends).
- A schema or query-plan rejection is remembered for 30 minutes
  (`PiHistoryCoverage.RETRY_INTERVAL_MILLIS`); during that time the signer-only
  question is asked directly. Then the full question is asked again. One rejected
  request per 30 minutes is the whole cost.
- Later pages of a list repeat the question its first page was answered with.
- When the indexer cannot be reached, the signer-only question is still asked once
  (its answer may be saved for offline use) and the next minute goes straight to
  it.
- `PiHistoryCoverage.listsReceivedTransfers` says whether the latest history read
  covered every role. The note "Transfers you received may not be listed here yet"
  under Activity and on the asset page follows it and disappears by itself.

The day the indexer gains a recipient query plan, received transfers appear in
3.9.0 within 30 minutes, without an app update. Until then nothing changes for the
user.

## 6. Indexer clock window

The indexer's "last indexed" time was accepted only between 5 minutes before and
30 seconds after the phone's clock. A phone 45 seconds slow or 6 minutes fast got
no history, prices or APY.

The time is now compared with the HTTP `Date` of the health response itself: the
answering server's own clock, received over the same TLS channel as the data. That
measures how far behind the indexer is, not how wrong the phone is. The phone's
clock is used when the response has no usable `Date`, and when the two clocks
differ by more than 26 hours (every wrong time zone is inside that; a larger gap
is not an ordinary mis-set clock and the check stays as strict as it was). An
indexer that really stopped indexing for more than 5 minutes is still rejected.

The two stores that keep validated data for offline use check the same
timestamps once more when they save (`PiValidatedHistoryCache`,
`PolkamarktCatalogCache`). They now use the same clock: each health check that
passes records how far the indexer's clock is from the phone's
(`PiIndexerClock`), and the stores judge the indexer's timestamps against the
phone's clock corrected by that. The history store writes the corrected time
into the file, so a later read, offline and in another process, checks against
it as well. The phone's own clock still decides when a file was saved and when
it is too old (24 hours). Before, such a phone loaded Activity while online and
had nothing saved for when it was offline.

## New strings

In a block `at2-endpoints-balances` at the end of every
`common/src/main/res/values*/strings.xml`. English and Japanese are written; the
other locales carry the English text.

| Name | Where |
|---|---|
| `tx_outcome_note_not_reached` | confirm screens, Activity, transaction page: nothing left the phone |
| `network_fee_needs_app_update` | fee notice on Send, Swap and Add liquidity; fee dialog on Remove liquidity, Referral, Demeter claim and Edit farm |
| `tx_outcome_not_sent_needs_app_update` | "Not sent" message after Confirm |
| `send_locked_balance_note` | Send screen, under the amount; asset page, under the Frozen amount |
| `tx_outcome_not_sent_fee_locked` | "Not sent" message when the fee would come out of frozen XOR |
| `send_fee_locked_hint` | Send amount screen, under the amount, for the same case |
| `asset_frozen_amount_suffix` | home and asset lists: "· 110 XOR frozen" after the liquid amount |

The Update button uses the existing `common_update`.

## Tests

Every test goes through production code; fakes stand only where the network is.

| Test | What it proves |
|---|---|
| `common` `BoundedHttpTextClientTest` (10 new) | gzip is requested and decoded under the byte bound; a gzip bomb is rejected; data that is not gzip, or has a wrong checksum, is a malformed body; a read that times out, a cut connection and a body that ends early while gzip is read are `BOUNDED_HTTP_IO`, and a deadline is `BOUNDED_HTTP_DEADLINE` (all four were "malformed" before); a POST that fails before its body is handed over is `requestNotSent`, one that fails after is not, a GET never is; the server date is taken only from an exact RFC 1123 header; connect timeout is configurable |
| `sorasubstrate` `Sora2RuntimeRpcFailoverTest` (26) | identity in one piece from the second endpoint; a half-read identity is read again completely; nonce, fee, metadata, code hash fail over; a failed endpoint is tried last and never locked out; an endpoint on another chain serves nothing; a transaction goes to the second endpoint only when the first was never reached; one the first may have received is never sent again; "not sent" when no endpoint was reached; status reads with the first endpoint down; a block hash and its block from one endpoint; a block is never read from an endpoint that has not finalized it; finality does not step backwards while one endpoint is not behind; archive state; cancellation is never retried. **Stalled endpoint (7 new):** a first endpoint that answers 500 blocks behind is not the source of identity, nonce, fee or submission; endpoints up to 10 blocks apart keep the reviewed order and 11 do not; the other endpoint is asked for its height once per 2 minutes with two requests; an unreachable endpoint is not asked on every identity; an endpoint that caught up is first again; the endpoint that is behind is still used when the other stops answering or cannot be reached at all |
| `ExtrinsicManagerSafetyTest` (3 new) | through the real `ExtrinsicManager`: a head 1 or 10 blocks lower than the one signed against reaches transport; a head 11 blocks lower is a definitive "not sent" that carries a network failure and is never handed over; runtime drift is refused whatever the height |
| `Sora2BoundedRuntimeRpcClientTest` (updated) | a single endpoint behaves as before |
| `ExtrinsicManagerOutcomeTest` (3 new) | through the real `ExtrinsicManager` and RPC client: no endpoint reached is `RefusedByNode(NOT_REACHED)`, never waiting; the second endpoint takes a transaction the first was never connected to; after a lost answer, a deadline or HTTP 502 the second endpoint gets nothing and the outcome is waiting |
| `Sora2PendingRecoveryWithoutRuntimeTest` (2 new) | an endpoint behind the signing block, or behind the previous pass, leaves the row pending without a failure and without re-reading |
| `RuntimeManagerLiveRuntimeTest` (9 new) | the real `RuntimeManager`: metadata downloaded once for three contexts; the download that refreshed the runtime for reading also serves the first quote; new code hash, new transaction version or new spec version downloads again; without a code hash the old path is used; the next app start does not download; a saved binding for other metadata is ignored; mainnet runtimes 130 and 133 list only extensions the encoder writes; a new extension with data (in the extrinsic or only in the signed payload) is refused with `SORA2_APP_UPDATE_REQUIRED` while reads keep working; one without data is accepted |
| `XorAccountDataDecodingTest` (8) | real mainnet `System.Account` values decoded with the captured runtime-133 metadata: field names, frozen, reserved covering part of the floor (0.3079 XOR transferable, 0.0498 XOR for fees), frozen above free; free at, just above and just below frozen with reserved XOR; the old layout by its own names, in both orders; the same bytes under the current layout |
| `common_wallet` `BalanceWrapperTest` (10) | the transfer lock and the fee lock; transferable never negative; with reserved XOR and free at or below frozen nothing is transferable (the transfer rule alone gave 20), just above it the transfer rule decides; the parts add up to the total; the stored row gives the same amounts as the live read; an account whose XOR is all locked keeps its frozen total |
| `common_wallet` `AssetCardStateTest` (3 new) | the home row of an all-locked account is "0 XOR" with "110 XOR" frozen; reserved and locked are named together; a liquid asset has no frozen amount |
| `feature_assets_impl` `TransferFreshBalanceReadTest` (3) | a balance read that never answers ends as an `IOException` after 15 s and frees the wallet lock; an answer in time is checked against the locked balance; a fee that would come out of frozen XOR ends as `SORA2_TRANSFER_XOR_FEE_LOCKED` although the amount fits |
| `TransferAmountViewModelTest` (4 new) | the locked note is shown for locked XOR and absent otherwise; XOR that no fee can be paid from is all named as locked; a fee above what is left over the frozen amount shows the fee hint and keeps Review off |
| `AssetDetailsViewModelTest` (6 new) | free 110 / frozen 110: Frozen "110 XOR", the locked note, no Send, and the breakdown opens (it showed Frozen 0 and did not open); the saved amounts are shown without an error when the network read fails; a node that never answers ends loading after 10 s; right after the update the card follows the network, not the row 3.8.6 saved; partly locked keeps Send; no note without a lock or for other assets |
| Remove liquidity, Referral, Demeter claim, Edit farm view model tests (1 new each) | a network that needs a newer app shows the update text with Update and Close; Update asks for the store page, leaves the screen and does not ask the fee again |
| `feature_blockexplorer_api` `PolkaswapIndexerClientTest` (7 new, 2 updated) | every role first, signer on rejection; the rejection is not repeated for 30 minutes and is retried after; received transfers are listed once accepted; later pages keep the question of page one; an unreachable indexer; a wrong phone clock passes, a stale indexer does not, no server date or a gap above 26 hours uses the phone clock |
| `PiIndexerClockSkewStoreTest` (4 new), `PolkamarktCatalogCacheTest` (1 new) | a health check through the production client with a phone 45 s slow, 6 or 10 minutes off or 3 hours fast, then the production stores: history is saved and read back offline by another instance, the catalog is saved; without the measurement the save is refused as before; a checkpoint that really is 6 minutes old is refused; a clock difference above 26 hours is not kept |

New test resource: `sorasubstrate/src/test/resources/sora2_metadata_mainnet_133`,
the mainnet metadata of runtime 133 captured on 10 October 2026 (623,616 bytes,
SHA-256 of the bytes `e7b2459c35f0c0d1273216bb965a36168e1ca3bd94592608b40c1eaa3b682e17`,
of the app's canonical hex form `ab22a2d4e7d9b95197966ce7c59385bb2b569f5709eebcb9aad104f014b9c732`).
The same hash was read from the live chain again on 11 October.

### Opt-in live tests (read-only)

`MainnetLiveReadContractTest` is new:

    RUN_LIVE_SORA_RPC_TESTS=true ./gradlew :sorasubstrate:testProductionDebugUnitTest \
      --tests '*MainnetLiveReadContractTest*' --rerun

It sends no transaction. Through the production HTTPS and JSON-RPC clients it
checks that both endpoints are mainnet on the same runtime version and runtime
code, that the live runtime lists no signed extension the encoder cannot write and
its metadata fits the limits, that live `System.Account` values decode to the
amount the chain allows (against an independent reading of the raw bytes), that
status reads and 2,000-block-old state are served through the two endpoints, and
that the remote type aliases arrive intact from the config host, which answers
with gzip now that the client asks for it.

`PolkaswapIndexerClientLiveContractTest` (`RUN_LIVE_PI_CLIENT_TESTS=true`) has one
more test: the history question is settled at run time, a phone clock ten
minutes slow or three hours fast passes the health check, and the history that
phone loaded is validated against the chain, saved by the production store and
read back.

`MainnetLiveReadContractTest` also checks the fee rule on the sampled accounts
(what can be paid as a fee is `free - frozen`), and that the two endpoints'
finalized heads are within the 10 blocks the app treats as healthy.

### Results (12 October 2026, 06:40-07:20 JST, after the review round)

| Run | Result |
|---|---|
| `testProductionDebugUnitTest` (all modules) | 1,517 tests in 15 modules, 0 failures, 17 skipped (the opt-in live tests) |
| `:app:assembleProductionDebug` | builds |
| `:app:lintProductionDebug` | passes: 0 new errors (179 filtered by the existing baseline), no finding for the new strings or the new files |
| `ktlintCheck` | 998 findings in 136 files, the same as before this task; none in a line this task wrote (the task fails on the old findings, as before) |
| `MainnetLiveReadContractTest`, live | 6 of 6. Both endpoints: genesis `0x7e4e32d0...8ad5`, spec 133, transaction version 131, runtime code hash `0xfe91daf5...bed3`, finalized heads 28,004,848 on both. Metadata 623,616 bytes, hash `ab22a2d4...c732`, no unsupported signed extension. Four accounts with locks decoded: free 19,528.49 XOR with 10,000 frozen gives 9,528.49 transferable (the app said 19,528.49 before); free 2.0398, reserved 0.2582, frozen 1.9900 gives 0.3079 transferable and 0.0498 for fees; free 0.6252 with 110 frozen and free 0.2167, reserved 6.3778 with 10 frozen give 0. |
| `PolkaswapIndexerClientLiveContractTest`, live | 5 of 5. The live indexer still rejects the every-role question (`lists received transfers: false`) and the signer-only page loads; the offline replay from the cache works; with the phone clock ten minutes slow and three hours fast the live health passes and the history is saved and read back |
| Device, emulator API 34: `:feature_assets_impl` `connectedProductionDebugAndroidTest` | 11 of 11 (9 from this task: locked note on Send shown and absent; update notice with Update and without Try again; Try again and no Update for the other reasons; the Update button calls the update and never the retry; the store links; asset card of an all-locked account with Frozen amount, note, no Send, and it opens; no note without a lock; home row "0 XOR · 110 XOR frozen") |
| Device: `:feature_blockexplorer_api`, `:common` `connectedProductionDebugAndroidTest` | 2 of 2, 14 of 14 |
| Device: install and start of the production debug build | starts to onboarding without a crash; the runtime is refreshed to the live 133 through the two-endpoint client |

Run in the first pass and not repeated after the review round (nothing it covers
was changed): `:sorasubstrate:connectedProductionDebugAndroidTest` with
`soraLiveNetwork=true`, 7 of 7 on 12 October 05:25-06:05 JST.
`MainnetLiveSigningTest`: runtime 133 / 131, metadata hash `ab22a2d4...c732`, type
aliases `e87760d7...3601` (fetched gzip-compressed), `payment_queryInfo` fee
100018412589707326, and the node refused the unfunded throwaway transfer only with
"Inability to pay some fees", which proves the signature. Old state was read
through the archive endpoint. Nothing was included in a block.

Before the fixes, on the same code otherwise: the two status-pass tests fail
(`SORA2_RECOVERY_FINALITY_BEFORE_BIRTH`, `..._HEIGHT_REGRESSION`), and the wallet
lock test never gets its failure because the read never returns. For the review
round: the stalled-endpoint test gets identity 500 from the first endpoint; the
lower-head test ends with `SORA2_MUTATION_FINALIZED_HEIGHT_REGRESSION`; the
all-locked asset page test gets "0 XOR" as Frozen and no call for the breakdown;
the reserved-and-frozen balance tests get 20 where the chain allows 0; the four
update tests get the "try again in a moment" text with Try again; the gzip
network-failure tests get `BOUNDED_HTTP_ENCODING_INVALID`; the skewed-clock store
tests get `PI_VALIDATED_HISTORY_CHECKPOINT_INVALID`.

Not run: a funded transfer, and any screen of a real account with locked XOR (no
such account is available to the build machine). The rule that a fee may not
come out of frozen XOR is read from the runtime source the app is pinned to
(pallet_balances `Currency::withdraw`, xor-fee `withdraw_fee`), as in the audit's
second pass; it was not tried on the chain, which would need such an account.

## Human and ops actions

- [ ] **Monitor both hosts and both certificates**: `ws.mof.sora.org` and
      `mof2.sora.org` (HTTPS JSON-RPC). The app now needs one of the two. Both
      certificates are Let's Encrypt and expire on 15 and 16 November 2026.
- [ ] **Keep `mof2.sora.org` an archive node with HTTPS JSON-RPC.** It is now a
      signing endpoint as well as the source of old state. Tell the mobile team
      before moving, pruning or re-certifying it. It must keep serving
      `state_getStorageHash`, `payment_queryInfo`, `system_accountNextIndex` and
      `author_submitExtrinsic` (all present today).
- [ ] The two release-checklist items that describe `ws.mof.sora.org` as the only
      endpoint, and the product decision "second reviewed signing endpoint", are
      settled by this change (RELEASE-CHECKLIST.md, "Product decisions to
      confirm"). Security review of `Sora2BoundedRuntimeRpcClient` should confirm
      the rule "a transaction is offered to the second endpoint only when the
      first attempt handed over no byte of the request body".
- [ ] **Indexer owners**: add a query plan for the every-role account history
      filter (`or` over `address`, `dataFrom`, `dataTo`, ordered by timestamp).
      Both apps pick it up without a release (Android within 30 minutes).
- [ ] **Before each mainnet runtime upgrade**: run `MainnetLiveReadContractTest`
      and `MainnetLiveSigningTest` against the staging runtime. If the first one
      reports a signed extension the encoder does not write, 3.9.0 will ask users
      to update the app after that upgrade; ship the update first.
- [ ] **Release notes**: locked XOR (staking, voting, vesting) is no longer shown
      as transferable. The XOR amount on the home screen and the portfolio total
      show liquid funds, so for an account with locked XOR they are lower than in
      3.8.6 by the locked amount; the row names it ("0 XOR · 110 XOR frozen") and
      the asset page shows it under Frozen with the reason. Nothing was lost or
      moved. Support should have this sentence ready: about one funded account in
      ten has a lock, and for about 4 % all XOR is locked.
- [ ] Manual check on a device with an account that has locked XOR: the home row
      names the frozen amount, the asset page shows Frozen, the locked note and
      the Locked row with the amount, Send shows the locked note, and Max stops at
      the transferable amount. With an account that also has reserved XOR and a
      free balance barely above its frozen amount: Send says "Not enough unlocked
      XOR for the network fee" when the fee does not fit. No such account was
      available here.
- [ ] **Store listing**: the Update button opens `market://details?id=<package>`
      (or the Play web page). If 3.9.x is ever distributed outside Google Play,
      point `AppStoreListing` at that store.

## Not changed

- Reads for balances, pools and swap quotes still use the WebSocket of the
  selected node.
- When the live runtime version differs from the cached one and the metadata
  cannot be downloaded from either endpoint, balances cannot be decoded until it
  can (`SORA2_RUNTIME_REFRESH_FAILED`). Two endpoints and the longer deadline make
  that less likely; the rule itself is unchanged.
- The validated history cache is keyed by account and page, not by the history
  question. Offline, right after the indexer starts to list received transfers, an
  older page saved for the signer-only question can follow a newer first page;
  rows already listed are dropped, so nothing is shown twice.
- No certificate pinning (unchanged).
- Swap, liquidity, referral and Demeter check the XOR for the fee against the
  transferable amount only. For an account with reserved and frozen XOR whose
  free balance is above its frozen amount by less than the fee, they let the user
  confirm and the node refuses; the app then says that the account can't pay the
  fee and that XOR may be locked, and nothing is sent or charged. Send checks
  this before Review.
- The 998 ktlint findings in 136 files that the repository had before this task,
  including some in files this task edited (lines it did not write).

## Review round (12 October)

Eight findings of the adversarial review, all accepted and fixed:

| Finding | Fix | Section |
|---|---|---|
| Major: an account whose XOR is all locked showed 0 XOR, Frozen 0, and a card that did not open | the breakdown is read for every XOR account with a saved-row fall-back and a 10 s limit; locked note on the asset page; frozen amount on the home row; write-up and release note corrected | 2 |
| Transferable XOR above what the chain allows with reserved XOR (the fee may not come out of frozen XOR) | both limits kept from the decoder on; 0 transferable when no fee can be paid; fee check on Send and before transport | 2 |
| A first endpoint that answers but is stalled or syncing stayed the only source | the endpoints' finalized heights are compared; one more than 10 blocks behind is tried last | 1 |
| A Send failed when the first endpoint dropped between signing and hand-over | a head up to 10 blocks lower is accepted; further behind is "Not sent" with the network wording | 1 |
| "Update the app" notice with a Try again button | Update button that opens the store page; no Try again for this reason | 3 |
| Remove liquidity, Referral, Demeter claim and Edit farm said "try again in a moment" for the same case | one shared dialog with the update text, Update and Close | 3 |
| A network failure while a gzip body is read was reported as a malformed body | only invalid gzip data is malformed | 3 |
| The offline stores still judged the indexer's timestamps by the phone's clock | they use the clock difference the health check measured | 6 |
