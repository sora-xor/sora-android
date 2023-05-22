/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.data

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionsInfo
import kotlinx.coroutines.flow.Flow

interface TransactionHistoryRepository {
    val state: Flow<Boolean>
    fun onSoraAccountChange()
    suspend fun getContacts(query: String): Set<String>
    suspend fun getTransactionHistory(
        page: Long,
        tokens: List<Token>,
        soraAccount: SoraAccount,
        filterTokenId: String? = null,
    ): TransactionsInfo

    suspend fun getTransaction(
        txHash: String,
        tokens: List<Token>,
        soraAccount: SoraAccount,
    ): Transaction?

    fun saveTransaction(transfer: Transaction)
    suspend fun getLastTransactions(
        soraAccount: SoraAccount,
        tokens: List<Token>,
        count: Int,
        filterTokenId: String?
    ): List<Transaction>
}
