/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionsInfo
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapHistoryItemsToTransactions
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryItem
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWallet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.debounce
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionHistoryRepositoryImpl @Inject constructor(
    private val subQueryClient: SubQueryClientForSoraWallet,
    extrinsicManager: ExtrinsicManager,
) : TransactionHistoryRepository {

    init {
        extrinsicManager.setWatchingExtrinsicListener { txHash, success ->
            updateTransactionStatus(
                txHash,
                success
            )
        }
    }

    private val _state = SuspendableProperty<Boolean>(1)
    override val state = _state.observe().debounce(700)

    private val localPendingTransactions = mutableMapOf<String, Transaction>()
    override fun onSoraAccountChange(soraAccount: SoraAccount) {
        localPendingTransactions.clear()
    }

    override suspend fun getContacts(query: String): Set<String> =
        subQueryClient.getTransactionPeers(query, Const.SORA).toSet()

    private fun updateTransactionStatus(txHash: String, status: Boolean) {
        localPendingTransactions[txHash]?.base?.status =
            if (status) TransactionStatus.COMMITTED else TransactionStatus.REJECTED
        _state.set(true)
    }

    override suspend fun getTransaction(
        txHash: String,
        tokens: List<Token>,
        soraAccount: SoraAccount
    ): Transaction? {
        val tx = subQueryClient.getTransactionCached(
            address = soraAccount.substrateAddress,
            networkName = Const.SORA,
            txHash = txHash
        )
        val transactions =
            mapHistoryItemsToTransactions(tx.items, soraAccount.substrateAddress, tokens)
        return transactions.firstOrNull() ?: localPendingTransactions[txHash]
    }

    override suspend fun getTransactionHistory(
        page: Long,
        tokens: List<Token>,
        soraAccount: SoraAccount,
        filterTokenId: String?,
    ): TransactionsInfo {
        val historyInfo =
            subQueryClient.getTransactionHistoryPaged(
                address = soraAccount.substrateAddress,
                networkName = Const.SORA,
                page = page,
                filter = if (filterTokenId == null) null else { r ->
                    filterHistoryItem(r, filterTokenId)
                },
            )
        val transactions =
            mapHistoryItemsToTransactions(historyInfo.items, soraAccount.substrateAddress, tokens)
        val filtered = localPendingTransactions.filter { transactionLocal ->
            transactions.find { transaction -> transaction.base.txHash == transactionLocal.key } == null
        }
        localPendingTransactions.clear()
        localPendingTransactions.putAll(filtered)
        return TransactionsInfo(
            buildList {
                addAll(localPendingTransactions.values.sortedByDescending { it.base.timestamp })
                addAll(transactions)
            },
            historyInfo.endReached
        )
    }

    override fun saveTransaction(
        transfer: Transaction
    ) {
        localPendingTransactions[transfer.base.txHash] = transfer
        _state.set(true)
    }

    private fun filterHistoryItem(item: TxHistoryItem, tokenId: String): Boolean {
        return (
            item.data?.find {
                it.paramValue == tokenId
            } != null
            ) || (
            item.nestedData?.find { nested ->
                nested.data.find {
                    it.paramValue == tokenId
                } != null
            } != null
            )
    }
}
