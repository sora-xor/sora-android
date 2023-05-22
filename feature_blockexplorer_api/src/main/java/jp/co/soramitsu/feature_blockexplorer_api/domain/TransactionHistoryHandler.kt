/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.domain

import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TransactionHistoryHandler {

    val historyState: StateFlow<HistoryState>

    fun flowLocalTransactions(): Flow<Boolean>

    suspend fun onMoreHistoryEventsRequested()

    suspend fun getCachedEvents(count: Int, filterTokenId: String? = null): List<EventUiModel>

    suspend fun refreshHistoryEvents(tokenId: String? = null)

    suspend fun getTransaction(txHash: String): Transaction?
}
