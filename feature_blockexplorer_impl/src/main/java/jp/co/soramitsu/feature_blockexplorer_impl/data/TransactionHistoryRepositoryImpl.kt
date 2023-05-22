/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.data

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionsInfo
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryItem
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWallet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.debounce

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionHistoryRepositoryImpl @Inject constructor(
    private val subQueryClient: SubQueryClientForSoraWallet,
    extrinsicManager: ExtrinsicManager,
) : TransactionHistoryRepository {

    init {
        extrinsicManager.setWatchingExtrinsicListener(::updateTransactionStatus)
    }

    private val _state = SuspendableProperty<Boolean>(1)
    override val state = _state.observe().debounce(700)

    private val localPendingTransactions = mutableMapOf<String, Transaction>()
    override fun onSoraAccountChange() {
        localPendingTransactions.clear()
    }

    override suspend fun getContacts(query: String): Set<String> =
        subQueryClient.getTransactionPeers(query).toSet()

    private fun updateTransactionStatus(txHash: String, status: Boolean, block: String?) {
        localPendingTransactions[txHash]?.base?.status =
            if (status) TransactionStatus.COMMITTED else TransactionStatus.REJECTED
        localPendingTransactions[txHash]?.base?.blockHash = block
        _state.set(true)
    }

    override suspend fun getLastTransactions(
        soraAccount: SoraAccount,
        tokens: List<Token>,
        count: Int,
        filterTokenId: String?,
    ): List<Transaction> {
        val tx = subQueryClient.getTransactionHistoryCached(
            soraAccount.substrateAddress,
            count,
            filter = if (filterTokenId == null) null else { r ->
                filterHistoryItem(r, filterTokenId)
            }
        )
        val mapped = mapHistoryItemsToTransactions(tx, soraAccount.substrateAddress, tokens)
        return buildList {
            addAll(filterLocalPendingTx(filterTokenId))
            addAll(mapped)
        }.take(count)
    }

    override suspend fun getTransaction(
        txHash: String,
        tokens: List<Token>,
        soraAccount: SoraAccount
    ): Transaction? {
        val tx = subQueryClient.getTransactionCached(
            address = soraAccount.substrateAddress,
            txHash = txHash
        )
        val transaction =
            mapHistoryItemsToTransactions(
                tx.items,
                soraAccount.substrateAddress,
                tokens
            ).firstOrNull()
        val local = localPendingTransactions[txHash]
        return transaction ?: local
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
                page = page,
                filter = if (filterTokenId == null) null else { r ->
                    filterHistoryItem(r, filterTokenId)
                },
            )

        val transactions = historyInfo?.items?.let {
            mapHistoryItemsToTransactions(it, soraAccount.substrateAddress, tokens)
        }.orEmpty()
        val filtered = localPendingTransactions.filter { transactionLocal ->
            transactions.find { transaction -> transaction.base.txHash == transactionLocal.key } == null
        }
        localPendingTransactions.clear()
        localPendingTransactions.putAll(filtered)
        return TransactionsInfo(
            buildList {
                if (page == 1L) addAll(filterLocalPendingTx(filterTokenId).sortedByDescending { it.base.timestamp })
                addAll(transactions)
            },
            historyInfo?.endReached ?: true,
            historyInfo?.errorMessage,
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
            ) || (
            tokenId == SubstrateOptionsProvider.feeAssetId && item.module.equals(
                Pallete.Referrals.palletName,
                true
            )
            )
    }

    private fun filterLocalPendingTx(tokenId: String?): Collection<Transaction> =
        if (tokenId == null) localPendingTransactions.values
        else localPendingTransactions.filter {
            when (val transaction = it.value) {
                is Transaction.Liquidity -> transaction.token1.id == tokenId || transaction.token2.id == tokenId
                is Transaction.ReferralBond -> transaction.token.id == tokenId
                is Transaction.ReferralSetReferrer -> transaction.token.id == tokenId
                is Transaction.ReferralUnbond -> transaction.token.id == tokenId
                is Transaction.Swap -> transaction.tokenFrom.id == tokenId || transaction.tokenTo.id == tokenId
                is Transaction.Transfer -> transaction.token.id == tokenId
            }
        }.values
}
