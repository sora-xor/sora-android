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
import jp.co.soramitsu.common.domain.Token
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
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionsInfo
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.canonicalExtrinsicHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class TransactionHistoryRepositoryImpl @Inject constructor(
    private val polkaswapIndexerClient: PolkaswapIndexerClient,
    extrinsicManager: ExtrinsicManager,
    private val substrateCalls: SubstrateCalls,
    private val validatedHistoryCache: PiValidatedHistoryCache,
    private val database: AppDatabase,
) : TransactionHistoryRepository {

    init {
        extrinsicManager.setWatchingExtrinsicListener(::updateTransactionStatus)
    }

    private val _state = SuspendableProperty<Boolean>(1)
    override val state = _state.observe().debounce(700)

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
    }

    override suspend fun getContacts(query: String): Set<String> =
        runCatching {
            polkaswapIndexerClient.getTransactionPeers(query)
        }.getOrElse {
            if (it is CancellationException) throw it
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_PEERS
            )
            emptySet()
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

        // A validated PI row supplies semantic history, but the exact generic
        // status-only witness remains authoritative for pending/terminal
        // execution state until that journal is pruned under its own policy.
        indexedTransactions.forEach { transaction ->
            canonicalTransactionHashOrNull(transaction.base.txHash)
                ?.let(rowsByHash::get)
                ?.let { row ->
                    transaction.base.status = row.toTransactionStatus()
                    transaction.base.blockHash = row.terminalBlockHash
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
                transaction.base.status = row.toTransactionStatus()
                transaction.base.blockHash = row.terminalBlockHash
            }
            localOverlay[hash] = transaction
        }
        journalRows.forEach { row ->
            if (
                row.transactionHash !in indexedHashes &&
                row.transactionHash !in localOverlay &&
                row.operationKind == Sora2PendingSubmissionLocal.OPERATION_GENERIC
            ) {
                localOverlay[row.transactionHash] = row.toActivityTransaction()
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

    private fun Sora2PendingSubmissionLocal.toActivityTransaction(): Transaction =
        Transaction.Sora2Submission(
            base = jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase(
                txHash = transactionHash,
                blockHash = terminalBlockHash,
                fee = BigDecimal.ZERO,
                status = toTransactionStatus(),
                timestamp = createdAt,
            ),
            networkId = networkId,
            // A crash-restored pre-arm witness proves only that the durable UNKNOWN transition was
            // not observed. Recovery deliberately keeps it and scans by hash, so the activity UI
            // must not claim that transport definitely happened.
            submissionIsAmbiguous =
                submissionIsAmbiguous ||
                    state == Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT,
            terminalBlockNumber = terminalBlockNumber,
        )

    private fun Sora2PendingSubmissionLocal.toTransactionStatus(): TransactionStatus =
        when (state) {
            Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS -> TransactionStatus.COMMITTED
            Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE,
            Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED -> TransactionStatus.REJECTED
            Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT,
            Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN,
            Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY ->
                TransactionStatus.PENDING
            else -> error("SORA2_PENDING_OVERLAY_STATE_INVALID")
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
