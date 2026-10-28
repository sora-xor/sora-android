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

    /**
     * Asks the network once for the final status of the current account's waiting transaction
     * [txHash], so that a screen showing it follows it promptly. Read-only and never an error
     * for the caller.
     */
    suspend fun checkWaitingTransaction(txHash: String)

    suspend fun hasNewTransaction(): Boolean
}
