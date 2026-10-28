/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
    /**
     * Past counterparties of [account] whose address contains [query] (all of them, newest
     * first, for a blank query).
     */
    suspend fun getContacts(account: String, query: String): Set<String>
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

    fun saveTransaction(walletId: String, transfer: Transaction)

    /**
     * Asks the network once for the final status of one transaction of [walletId] that this
     * device sent and that is still waiting for confirmation. Only that transaction is looked
     * at, so another waiting transaction that cannot be checked never holds it back. Read-only:
     * it never signs or sends anything. A read that fails changes nothing and is not an error;
     * [state] ticks when the status became final.
     */
    suspend fun checkWaitingTransaction(walletId: String, txHash: String)
    suspend fun getLastTransactions(
        soraAccount: SoraAccount,
        tokens: List<Token>,
        count: Int,
        filterTokenId: String?
    ): List<Transaction>
}
