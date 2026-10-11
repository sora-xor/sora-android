/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_blockexplorer_impl.data

import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.SuspendableProperty
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.IndexerHistoryElement
import jp.co.soramitsu.feature_blockexplorer_api.data.IndexerHistoryPage
import jp.co.soramitsu.feature_blockexplorer_api.data.PiHistoryCheckpointValidator
import jp.co.soramitsu.feature_blockexplorer_api.data.PiIndexerOfflineFallbackPolicy
import jp.co.soramitsu.feature_blockexplorer_api.data.PiValidatedHistoryCache
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkaswapIndexerClient
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.DemeterType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatusDetail
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionsInfo
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingSubmissionCoordinator
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionDetails
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionDetailsStore
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.canonicalExtrinsicHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class TransactionHistoryRepositoryImpl @Inject constructor(
    private val polkaswapIndexerClient: PolkaswapIndexerClient,
    extrinsicManager: ExtrinsicManager,
    private val substrateCalls: SubstrateCalls,
    private val validatedHistoryCache: PiValidatedHistoryCache,
    private val database: AppDatabase,
    private val detailsStore: Sora2SubmissionDetailsStore,
    private val pendingSubmissionCoordinator: Sora2PendingSubmissionCoordinator,
) : TransactionHistoryRepository {

    init {
        extrinsicManager.setWatchingExtrinsicListener(::updateTransactionStatus)
    }

    private val _state = SuspendableProperty<Boolean>(1)

    /**
     * Ticks when a local transaction is added or resolved in this process, and also whenever the
     * durable status journal is written. The second source is what makes a waiting entry change
     * on screen when the background status worker resolves it.
     */
    override val state = merge(_state.observe(), journalChanges()).debounce(700)

    private fun journalChanges(): Flow<Boolean> = flow {
        emitAll(
            database.walletIdentityDao().observeSora2PendingSubmissionChanges()
                // The first value is the current content, not a change.
                .drop(1)
                .map { true }
        )
    }.catch {
        // Observing the journal is a convenience. If the database is unavailable the screens
        // still refresh on their own events and on pull-to-refresh.
    }

    private data class LocalPendingTransactionKey(
        val walletId: String,
        val transactionHash: String,
    )

    private val localPendingTransactionsLock = Any()
    private val localPendingTransactions =
        mutableMapOf<LocalPendingTransactionKey, Transaction>()
    override fun onSoraAccountChange() {
        synchronized(localPendingTransactionsLock) {
            localPendingTransactions.clear()
        }
        recentPeers = null
    }

    private class RecentPeers(
        val account: String,
        val loadedAtNanos: Long,
        val peers: List<String>,
    )

    private val recentPeersMutex = Mutex()

    @Volatile
    private var recentPeers: RecentPeers? = null

    /**
     * The contact screen searches on every keystroke. The account's recent counterparties are
     * read once (one indexer page) and filtered on the device for a short while.
     */
    override suspend fun getContacts(account: String, query: String): Set<String> {
        val peers = runCatching {
            recentPeersMutex.withLock {
                val now = System.nanoTime()
                recentPeers
                    ?.takeIf {
                        it.account == account && now - it.loadedAtNanos < RECENT_PEERS_TTL_NANOS
                    }
                    ?.peers
                    ?: polkaswapIndexerClient.getRecentTransactionPeers(account).also {
                        recentPeers = RecentPeers(account, now, it)
                    }
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_PEERS
            )
            return emptySet()
        }
        val trimmed = query.trim()
        return peers.filterTo(linkedSetOf()) {
            trimmed.isEmpty() || it.contains(trimmed, ignoreCase = true)
        }
    }

    private fun updateTransactionStatus(txHash: String, status: Boolean, block: String?) {
        val canonicalHash = canonicalLocalPendingHashOrNull(txHash) ?: return
        synchronized(localPendingTransactionsLock) {
            localPendingTransactions.forEach { (key, transaction) ->
                if (key.transactionHash == canonicalHash) {
                    transaction.base.status =
                        if (status) TransactionStatus.COMMITTED else TransactionStatus.REJECTED
                    transaction.base.blockHash = block
                }
            }
        }
        _state.set(true)
    }

    override suspend fun getLastTransactions(
        soraAccount: SoraAccount,
        tokens: List<Token>,
        count: Int,
        filterTokenId: String?,
    ): List<Transaction> {
        val tx = runCatching {
            getLastTransactionsValidated(
                address = soraAccount.substrateAddress,
                count = count,
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_HISTORY
            )
            emptyList()
        }.filter {
            if (filterTokenId != null)
                return@filter isReferral(it, filterTokenId)

            return@filter true
        }
        val mapped = mapHistoryItemsToTransactions(
            tx,
            soraAccount.substrateAddress,
            tokens
        )
        return durableHistoryMerge(
            walletId = soraAccount.substrateAddress,
            indexedHashes = canonicalIndexerHashes(tx),
            indexedTransactions = mapped,
            filterTokenId = filterTokenId,
            includeLocalOverlay = true,
            tokens = tokens,
        ).take(count)
    }

    override suspend fun getTransaction(
        txHash: String,
        tokens: List<Token>,
        soraAccount: SoraAccount
    ): Transaction? {
        val requestedHash = canonicalTransactionHashOrNull(txHash)
            ?: return null
        val tx = runCatching {
            getTransactionValidated(
                address = soraAccount.substrateAddress,
                txHash = txHash,
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_DETAIL
            )
            emptyList()
        }
        val indexedTransactions = mapHistoryItemsToTransactions(
            tx,
            soraAccount.substrateAddress,
            tokens
        )
        val indexedHashes = canonicalIndexerHashes(tx)
        return durableHistoryMerge(
            walletId = soraAccount.substrateAddress,
            indexedHashes = indexedHashes,
            indexedTransactions = indexedTransactions,
            filterTokenId = null,
            includeLocalOverlay = true,
            tokens = tokens,
        ).firstOrNull {
            canonicalTransactionHashOrNull(it.base.txHash) == requestedHash
        }
    }

    override suspend fun getTransactionHistory(
        page: Long,
        tokens: List<Token>,
        soraAccount: SoraAccount,
        filterTokenId: String?,
    ): TransactionsInfo {
        val historyInfo = runCatching {
            getTransactionHistoryValidated(
                address = soraAccount.substrateAddress,
                page = page,
                pageCount = 100,
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_HISTORY
            )
            return TransactionsInfo(
                buildList {
                    if (page == 1L) {
                        addAll(
                            durableHistoryMerge(
                                walletId = soraAccount.substrateAddress,
                                indexedHashes = emptySet(),
                                indexedTransactions = emptyList(),
                                filterTokenId = filterTokenId,
                                includeLocalOverlay = true,
                                tokens = tokens,
                            )
                        )
                    }
                },
                endReached = true,
                errorMessage = it.message,
            )
        }

        val referralTransactions = historyInfo.items
            .filter { item ->
                if (filterTokenId != null)
                    return@filter isReferral(item, filterTokenId)

                return@filter true
            }.let { item ->
                mapHistoryItemsToTransactions(
                    item,
                    soraAccount.substrateAddress,
                    tokens
                )
            }
        val indexedHashes = canonicalIndexerHashes(
            historyInfo.items.filter { item ->
                filterTokenId == null || isReferral(item, filterTokenId)
            }
        )

        return TransactionsInfo(
            durableHistoryMerge(
                walletId = soraAccount.substrateAddress,
                indexedHashes = indexedHashes,
                indexedTransactions = referralTransactions,
                filterTokenId = filterTokenId,
                includeLocalOverlay = page == 1L,
                tokens = tokens,
            ),
            historyInfo.endReached,
        )
    }

    override fun saveTransaction(
        walletId: String,
        transfer: Transaction
    ) {
        check(walletId.isNotBlank()) { "SORA2_PENDING_OVERLAY_WALLET_INVALID" }
        val canonicalHash = requireCanonicalLocalPendingHash(transfer.base.txHash)
        synchronized(localPendingTransactionsLock) {
            localPendingTransactions[
                LocalPendingTransactionKey(walletId, canonicalHash)
            ] = transfer
        }
        _state.set(true)
    }

    /**
     * One status-only pass over the journal, the same read the background worker makes. It
     * exists so that a screen showing a waiting transaction does not have to wait for the
     * worker's next attempt, which can be minutes away after a few unsuccessful ones. The
     * coordinator used here has no signer and cannot submit anything.
     */
    override suspend fun checkWaitingTransactions() {
        try {
            pendingSubmissionCoordinator.recoverStatusOnly()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Offline, a slow node or an unreadable block: the entry stays as it is and the
            // background check keeps trying. Never an error for the screen.
        }
    }

    private fun isReferral(item: IndexerHistoryElement, tokenId: String): Boolean {
        val hasTokenIdInMainParams =
            item.data?.find {
                it.paramValue == tokenId
            } != null

        val hasTokenIdInNestedParams =
            item.nestedData?.find { nested ->
                nested.data.find { it.paramValue == tokenId } != null
            } != null

        val usedInReferralModule =
            tokenId == SubstrateOptionsProvider.feeAssetId &&
                item.module.equals(Pallete.Referrals.palletName, true)

        return hasTokenIdInMainParams || hasTokenIdInNestedParams || usedInReferralModule
    }

    /**
     * Merges the existing rich in-memory overlay with the non-secret Room recovery journal. Room
     * remains authoritative for status across process death; PI rows only suppress duplicate UI
     * entries and never delete or prune recovery evidence.
     */
    private suspend fun durableHistoryMerge(
        walletId: String,
        indexedHashes: Set<String>,
        indexedTransactions: List<Transaction>,
        filterTokenId: String?,
        includeLocalOverlay: Boolean,
        tokens: List<Token>,
    ): List<Transaction> {
        removeIndexedLocalTransactions(walletId, indexedHashes)
        val localSnapshot = synchronized(localPendingTransactionsLock) {
            localPendingTransactions.entries
                .filter { it.key.walletId == walletId }
                .associate { it.key.transactionHash to it.value }
        }
        val journalRows = database.walletIdentityDao()
            .getSora2SubmissionOverlayRows(walletId)
        val rowsByHash = journalRows.associateBy { it.transactionHash }
        check(rowsByHash.size == journalRows.size) {
            "SORA2_PENDING_OVERLAY_DUPLICATE_HASH"
        }
        // What each journaled transaction does and why it failed, when known. Never an authority
        // for status: the journal row decides, the details only describe.
        val detailsByHash = detailsStore.list(walletId).associateBy { it.transactionHash }
        if (detailsByHash.keys.any { it !in rowsByHash }) {
            detailsStore.retainOnly(
                walletId = walletId,
                transactionHashes = rowsByHash.keys,
                createdBefore = System.currentTimeMillis() - ORPHAN_DETAILS_GRACE_MILLIS,
            )
        }

        // A validated PI row supplies semantic history, but the exact generic
        // status-only witness remains authoritative for pending/terminal
        // execution state until that journal is pruned under its own policy.
        indexedTransactions.forEach { transaction ->
            canonicalTransactionHashOrNull(transaction.base.txHash)
                ?.let(rowsByHash::get)
                ?.let { row ->
                    transaction.base.applyJournal(
                        row = row,
                        details = detailsByHash[row.transactionHash],
                        listedByIndexer = true,
                    )
                }
        }

        if (!includeLocalOverlay) {
            return indexedTransactions
        }

        val localOverlay = linkedMapOf<String, Transaction>()
        localSnapshot.forEach { (rawHash, transaction) ->
            val hash = requireCanonicalLocalPendingHash(rawHash)
            if (hash in indexedHashes) return@forEach
            rowsByHash[hash]?.let { row ->
                transaction.base.applyJournal(row, detailsByHash[hash])
            }
            localOverlay[hash] = transaction
        }
        journalRows.forEach { row ->
            if (
                row.transactionHash !in indexedHashes &&
                row.transactionHash !in localOverlay &&
                row.operationKind == Sora2PendingSubmissionLocal.OPERATION_GENERIC
            ) {
                val details = detailsByHash[row.transactionHash]
                // After a restart the in-memory entry is gone. The stored description still
                // shows the transaction as what it is, so it also appears under its tokens.
                localOverlay[row.transactionHash] =
                    details?.activity?.toActivityTransactionOrNull(row, details, tokens)
                        ?: row.toActivityTransaction(details)
            }
        }
        return (
            filterLocalPendingTx(localOverlay.values, filterTokenId) +
                indexedTransactions
            ).sortedWith(
                compareByDescending<Transaction> {
                    it.base.status == TransactionStatus.PENDING
                }.thenByDescending {
                    it.base.timestamp
                }.thenBy {
                    it.base.txHash
                }
            )
    }

    private fun canonicalIndexerHashes(
        elements: List<IndexerHistoryElement>,
    ): Set<String> = elements.mapNotNullTo(linkedSetOf()) { element ->
        canonicalTransactionHashOrNull(element.id)
    }

    private fun canonicalTransactionHashOrNull(value: String): String? =
        value.takeIf(SORA2_TRANSACTION_HASH::matches)?.lowercase()

    /**
     * The rich in-memory overlay is fed only by locally produced extrinsic hashes. Require the exact
     * producer representation instead of normalizing a second identity into the same map key.
     */
    private fun canonicalLocalPendingHashOrNull(value: String): String? {
        if (!CANONICAL_LOCAL_SORA2_TRANSACTION_HASH.matches(value)) return null
        return try {
            value.canonicalExtrinsicHash().takeIf { it == value }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun requireCanonicalLocalPendingHash(value: String): String =
        checkNotNull(canonicalLocalPendingHashOrNull(value)) {
            "SORA2_PENDING_OVERLAY_HASH_INVALID"
        }

    private fun removeIndexedLocalTransactions(
        walletId: String,
        indexedHashes: Set<String>,
    ) {
        if (indexedHashes.isEmpty()) return
        synchronized(localPendingTransactionsLock) {
            localPendingTransactions.keys
                .filter {
                    it.walletId == walletId &&
                        it.transactionHash in indexedHashes
                }
                .forEach(localPendingTransactions::remove)
        }
    }

    private fun Sora2PendingSubmissionLocal.toActivityTransaction(
        details: Sora2SubmissionDetails?,
    ): Transaction =
        Transaction.Sora2Submission(
            base = journalBase(this, details, fee = BigDecimal.ZERO),
            networkId = networkId,
            // A crash-restored pre-arm witness proves only that the durable UNKNOWN transition was
            // not observed. Recovery deliberately keeps it and scans by hash, so the activity UI
            // must not claim that transport definitely happened.
            submissionIsAmbiguous =
                submissionIsAmbiguous ||
                    state == Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT,
            terminalBlockNumber = terminalBlockNumber,
        )

    private fun journalBase(
        row: Sora2PendingSubmissionLocal,
        details: Sora2SubmissionDetails?,
        fee: BigDecimal,
    ): TransactionBase = TransactionBase(
        txHash = row.transactionHash,
        blockHash = row.terminalBlockHash,
        fee = fee,
        status = TransactionStatus.PENDING,
        timestamp = row.createdAt,
    ).apply { applyJournal(row, details) }

    /**
     * The journal row is the only authority for status; the details explain it.
     *
     * [listedByIndexer] is true for a transaction the indexer lists, which means it is in a
     * block. A refusal recorded for it at submission can then no longer be the whole story, so
     * it is not used to call the transaction failed; the row stays pending until the app's own
     * check of finalized blocks has resolved it.
     */
    private fun TransactionBase.applyJournal(
        row: Sora2PendingSubmissionLocal,
        details: Sora2SubmissionDetails?,
        listedByIndexer: Boolean = false,
    ) {
        val refusal = details?.nodeRefusal?.takeUnless { listedByIndexer }?.let { refusal ->
            TransactionStatusDetail(
                kind = when (refusal.reason) {
                    TransactionNodeRefusal.CANNOT_PAY_FEE ->
                        TransactionStatusDetail.Kind.REFUSED_CANNOT_PAY_FEE
                    TransactionNodeRefusal.NETWORK_BUSY ->
                        TransactionStatusDetail.Kind.REFUSED_NETWORK_BUSY
                    TransactionNodeRefusal.NOT_VALID ->
                        TransactionStatusDetail.Kind.REFUSED_NOT_VALID
                },
                text = refusal.message,
            )
        }
        blockHash = row.terminalBlockHash
        when (row.state) {
            Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS -> {
                // Chain proof wins over everything recorded earlier.
                status = TransactionStatus.COMMITTED
                statusDetail = null
            }
            Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE -> {
                status = TransactionStatus.REJECTED
                statusDetail = TransactionStatusDetail(
                    kind = TransactionStatusDetail.Kind.FAILED_ON_CHAIN,
                    text = details?.chainFailure,
                )
            }
            Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED -> {
                status = TransactionStatus.REJECTED
                statusDetail = refusal
                    ?: TransactionStatusDetail(TransactionStatusDetail.Kind.NOT_INCLUDED)
            }
            Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY -> {
                status = TransactionStatus.PENDING
                statusDetail = TransactionStatusDetail(
                    TransactionStatusDetail.Kind.WAITING_FOR_CONFIRMATION
                )
            }
            Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT,
            Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN -> if (refusal != null) {
                // The node refused it explicitly: a failure, shown as one at once. The row itself
                // stays unresolved until its validity window has been scanned.
                status = TransactionStatus.REJECTED
                statusDetail = refusal
            } else {
                status = TransactionStatus.PENDING
                statusDetail = TransactionStatusDetail(
                    if (listedByIndexer) {
                        // It did reach the network; only the final confirmation is outstanding.
                        TransactionStatusDetail.Kind.WAITING_FOR_CONFIRMATION
                    } else {
                        TransactionStatusDetail.Kind.SUBMISSION_UNCERTAIN
                    }
                )
            }
            else -> error("SORA2_PENDING_OVERLAY_STATE_INVALID")
        }
    }

    /**
     * Rebuilds the Activity entry of a journaled transaction from its stored description. Returns
     * null (generic entry) when a token is unknown or the description does not fit its kind.
     */
    private fun Sora2SubmissionActivity.toActivityTransactionOrNull(
        row: Sora2PendingSubmissionLocal,
        details: Sora2SubmissionDetails,
        tokens: List<Token>,
    ): Transaction? {
        val resolved = tokenIds.map { id -> tokens.firstOrNull { it.id == id } ?: return null }
        val parsedAmounts = amounts.map { it.toBigDecimalOrNull() ?: return null }
        val base = journalBase(row, details, fee = fee?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        fun token(index: Int) = resolved.getOrNull(index)
        fun amount(index: Int) = parsedAmounts.getOrNull(index)
        return when (kind) {
            Sora2SubmissionActivity.Kind.TRANSFER -> Transaction.Transfer(
                base = base,
                amount = amount(0) ?: return null,
                peer = peer ?: return null,
                transferType = TransactionTransferType.OUTGOING,
                token = token(0) ?: return null,
            )
            Sora2SubmissionActivity.Kind.SWAP -> Transaction.Swap(
                base = base,
                tokenFrom = token(0) ?: return null,
                tokenTo = token(1) ?: return null,
                amountFrom = amount(0) ?: return null,
                amountTo = amount(1) ?: return null,
                market = Market.entries.firstOrNull { it.name == market } ?: Market.SMART,
            )
            Sora2SubmissionActivity.Kind.LIQUIDITY_ADD,
            Sora2SubmissionActivity.Kind.LIQUIDITY_REMOVE -> Transaction.Liquidity(
                base = base,
                token1 = token(0) ?: return null,
                token2 = token(1) ?: return null,
                amount1 = amount(0) ?: return null,
                amount2 = amount(1) ?: return null,
                type = if (kind == Sora2SubmissionActivity.Kind.LIQUIDITY_ADD) {
                    TransactionLiquidityType.ADD
                } else {
                    TransactionLiquidityType.WITHDRAW
                },
            )
            Sora2SubmissionActivity.Kind.REFERRAL_BOND -> Transaction.ReferralBond(
                base = base,
                amount = amount(0) ?: return null,
                token = token(0) ?: return null,
            )
            Sora2SubmissionActivity.Kind.REFERRAL_UNBOND -> Transaction.ReferralUnbond(
                base = base,
                amount = amount(0) ?: return null,
                token = token(0) ?: return null,
            )
            Sora2SubmissionActivity.Kind.REFERRAL_SET_REFERRER -> Transaction.ReferralSetReferrer(
                base = base,
                who = peer ?: return null,
                myReferrer = true,
                token = token(0) ?: return null,
            )
            Sora2SubmissionActivity.Kind.DEMETER_STAKE,
            Sora2SubmissionActivity.Kind.DEMETER_UNSTAKE,
            Sora2SubmissionActivity.Kind.DEMETER_CLAIM -> Transaction.DemeterFarming(
                base = base,
                type = when (kind) {
                    Sora2SubmissionActivity.Kind.DEMETER_STAKE -> DemeterType.STAKE
                    Sora2SubmissionActivity.Kind.DEMETER_UNSTAKE -> DemeterType.UNSTAKE
                    else -> DemeterType.REWARD
                },
                amount = amount(0) ?: return null,
                baseToken = token(0) ?: return null,
                targetToken = token(1) ?: return null,
                rewardToken = token(2) ?: return null,
            )
            // No dedicated Activity entry exists for the legacy claim; the generic one is used.
            Sora2SubmissionActivity.Kind.LEGACY_CLAIM -> null
        }
    }

    private fun filterLocalPendingTx(
        transactions: Collection<Transaction>,
        tokenId: String?,
    ): Collection<Transaction> =
        if (tokenId == null) transactions
        else transactions.filter { transaction ->
            when (transaction) {
                is Transaction.Liquidity -> transaction.token1.id == tokenId || transaction.token2.id == tokenId
                is Transaction.ReferralBond -> transaction.token.id == tokenId
                is Transaction.ReferralSetReferrer -> transaction.token.id == tokenId
                is Transaction.ReferralUnbond -> transaction.token.id == tokenId
                is Transaction.Swap -> transaction.tokenFrom.id == tokenId || transaction.tokenTo.id == tokenId
                is Transaction.Transfer -> transaction.token.id == tokenId
                is Transaction.EthTransfer -> transaction.token.id == tokenId
                is Transaction.DemeterFarming -> transaction.baseToken.id == tokenId || transaction.targetToken.id == tokenId || transaction.rewardToken.id == tokenId
                is Transaction.AdarIncome -> transaction.token.id == tokenId
                is Transaction.Sora2Submission -> false
            }
        }

    private companion object {
        const val RECENT_PEERS_TTL_NANOS = 60_000_000_000L
        const val ORPHAN_DETAILS_GRACE_MILLIS = 60L * 60L * 1000L
        val CANONICAL_LOCAL_SORA2_TRANSACTION_HASH = Regex("^0x[0-9a-f]{64}$")
        val SORA2_TRANSACTION_HASH = Regex(
            "^0x[0-9a-f]{64}$",
            RegexOption.IGNORE_CASE,
        )
    }

    private suspend fun getLastTransactionsValidated(
        address: String,
        count: Int,
    ): List<IndexerHistoryElement> = try {
        val qualified = polkaswapIndexerClient.getLastTransactionsQualified(
            address = address,
            count = count,
        )
        val persisted = if (qualified.fromCache) {
            validatedHistoryCache.readLast(address, count)
        } else {
            null
        }
        if (persisted != null) {
            persisted.items
        } else {
            val validation = PiHistoryCheckpointValidator.validate(
                elements = qualified.value,
                expectedAddress = address,
                health = qualified.health,
                maximumRecords = count,
                canonicalBlockHash = substrateCalls::getBlockHash,
            )
            storeValidatedHistoryBestEffort {
                validatedHistoryCache.storeLast(
                    address = address,
                    count = count,
                    validation = validation,
                )
            }
            qualified.value
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (!PiIndexerOfflineFallbackPolicy.allows(error)) throw error
        validatedHistoryCache.readLast(address, count)?.items ?: throw error
    }

    private suspend fun getTransactionValidated(
        address: String,
        txHash: String,
    ): List<IndexerHistoryElement> = try {
        val qualified = polkaswapIndexerClient.getTransactionQualified(txHash)
        val persisted = if (qualified.fromCache) {
            validatedHistoryCache.readTransaction(address, txHash)
        } else {
            null
        }
        if (persisted != null) {
            persisted.items
        } else {
            val validation = PiHistoryCheckpointValidator.validate(
                elements = qualified.value,
                expectedAddress = address,
                health = qualified.health,
                maximumRecords = 1,
                canonicalBlockHash = substrateCalls::getBlockHash,
            )
            storeValidatedHistoryBestEffort {
                validatedHistoryCache.storeTransaction(
                    address = address,
                    transactionHash = txHash,
                    validation = validation,
                )
            }
            qualified.value
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (!PiIndexerOfflineFallbackPolicy.allows(error)) throw error
        validatedHistoryCache.readTransaction(address, txHash)?.items ?: throw error
    }

    private suspend fun getTransactionHistoryValidated(
        address: String,
        page: Long,
        pageCount: Int,
    ): IndexerHistoryPage = try {
        val qualified = polkaswapIndexerClient.getTransactionHistoryQualified(
            address = address,
            page = page,
            pageCount = pageCount,
        )
        val persisted = if (qualified.fromCache) {
            validatedHistoryCache.readPage(address, page, pageCount)
        } else {
            null
        }
        if (persisted != null) {
            IndexerHistoryPage(
                items = persisted.items,
                endReached = checkNotNull(persisted.endReached),
                totalCount = checkNotNull(persisted.totalCount),
            )
        } else {
            val validation = PiHistoryCheckpointValidator.validate(
                elements = qualified.value.items,
                expectedAddress = address,
                health = qualified.health,
                maximumRecords = pageCount,
                canonicalBlockHash = substrateCalls::getBlockHash,
            )
            storeValidatedHistoryBestEffort {
                validatedHistoryCache.storePage(
                    address = address,
                    page = page,
                    pageCount = pageCount,
                    pageValue = qualified.value,
                    validation = validation,
                )
            }
            qualified.value
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (!PiIndexerOfflineFallbackPolicy.allows(error)) throw error
        validatedHistoryCache.readPage(address, page, pageCount)?.let { cached ->
            IndexerHistoryPage(
                items = cached.items,
                endReached = checkNotNull(cached.endReached),
                totalCount = checkNotNull(cached.totalCount),
            )
        } ?: throw error
    }

    private suspend fun storeValidatedHistoryBestEffort(
        store: suspend () -> Unit,
    ) {
        try {
            store()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The live, canonical-RPC-qualified result remains usable when
            // optional offline persistence is unavailable.
        }
    }
}
