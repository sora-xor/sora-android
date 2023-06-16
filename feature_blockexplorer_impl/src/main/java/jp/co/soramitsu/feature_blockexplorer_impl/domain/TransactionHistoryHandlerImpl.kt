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

package jp.co.soramitsu.feature_blockexplorer_impl.domain

import java.util.Date
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DateTimeUtils
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionMappers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionHistoryHandlerImpl @Inject constructor(
    private val assetsRepository: AssetsRepository,
    private val transactionMappers: TransactionMappers,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val resourceManager: ResourceManager,
    private val userRepository: UserRepository,
    private val dateTimeFormatter: DateTimeFormatter,
    coroutineManager: CoroutineManager,
) : TransactionHistoryHandler {

    init {
        userRepository.flowCurSoraAccount()
            .drop(1)
            .catch { }
            .onEach {
                transactionHistoryRepository.onSoraAccountChange()
            }
            .launchIn(coroutineManager.applicationScope)
    }

    private var historyEvents = mutableListOf<EventUiModel>()
    private var historyPage: Long = 1
    private var historyEndReached: Boolean = false
    private var tokenIdFilter: String? = null
    private val _historyState = MutableStateFlow<HistoryState>(HistoryState.Loading)

    private val mutex = Mutex()

    override val historyState = _historyState.asStateFlow()

    override fun flowLocalTransactions(): Flow<Boolean> {
        return transactionHistoryRepository.state
    }

    override suspend fun hasNewTransaction(): Boolean {
        val curTime = getCachedEvents(1).getOrNull(0)
            ?.safeCast<EventUiModel.EventTxUiModel>()?.timestamp ?: 0
        refreshHistoryEvents()
        val newTime = _historyState.value.safeCast<HistoryState.History>()?.events?.firstOrNull {
            it is EventUiModel.EventTxUiModel
        }?.safeCast<EventUiModel.EventTxUiModel>()?.timestamp
        return newTime != null && newTime > curTime
    }

    override suspend fun onMoreHistoryEventsRequested() {
        if (mutex.isLocked) return
        mutex.withLock {
            if (historyEndReached) return
            try {
                historyPage++
                val events = loadEvents()
                _historyState.emit(events)
            } catch (t: Throwable) {
                _historyState.emit(HistoryState.Error)
            }
        }
    }

    override suspend fun getCachedEvents(count: Int, filterTokenId: String?): List<EventUiModel> {
        val curAccount = userRepository.getCurSoraAccount()
        val txs = transactionHistoryRepository.getLastTransactions(
            curAccount,
            assetsRepository.tokensList(),
            count,
            filterTokenId,
        )
        val mapped = txs.map {
            transactionMappers.mapTransaction(it, curAccount.substrateAddress)
        }
        return mapped
    }

    override suspend fun refreshHistoryEvents(tokenId: String?) {
        if (mutex.isLocked) return
        mutex.withLock {
            tokenIdFilter = tokenId
            if (historyEvents.isEmpty()) {
                _historyState.emit(HistoryState.Loading)
            }
            _historyState.value.safeCast<HistoryState.History>()?.let { history ->
                _historyState.emit(history.copy(pullToRefresh = true))
            }
            val events = reloadHistoryEvents()
            _historyState.emit(events)
        }
    }

    override suspend fun getTransaction(txHash: String): Transaction? {
        return transactionHistoryRepository.getTransaction(
            txHash,
            assetsRepository.tokensList(),
            userRepository.getCurSoraAccount()
        )
    }

    private suspend fun reloadHistoryEvents(): HistoryState =
        try {
            historyPage = 1
            historyEvents.clear()
            loadEvents()
        } catch (t: Throwable) {
            HistoryState.Error
        }

    private suspend fun loadEvents(): HistoryState {
        val curAccount = userRepository.getCurSoraAccount()
        val transactionsInfo = transactionHistoryRepository.getTransactionHistory(
            historyPage,
            assetsRepository.tokensList(),
            curAccount,
            tokenIdFilter,
        )

        historyEndReached = transactionsInfo.endReached
        val mapped = transactionsInfo.transactions.map {
            transactionMappers.mapTransaction(it, curAccount.substrateAddress)
        }
        historyEvents.addAll(mapped)
        return buildHistoryList(!transactionsInfo.errorMessage.isNullOrEmpty())
    }

    private fun buildHistoryList(hasError: Boolean): HistoryState {
        return if (historyEvents.isEmpty()) {
            if (hasError) {
                HistoryState.Error
            } else {
                HistoryState.NoData
            }
        } else {
            HistoryState.History(
                endReached = historyEndReached,
                events = insertHistoryTimeSeparators(historyEvents),
                hasErrorLoadingNew = hasError,
                pullToRefresh = false,
            )
        }
    }

    private fun insertHistoryTimeSeparators(events: MutableList<EventUiModel>): List<EventUiModel> {
        var i = events.lastIndex
        while (i >= 0) {
            val before = events[i]
            val after = events.getOrNull(i - 1)
            if (before is EventUiModel.EventTxUiModel && after is EventUiModel.EventTxUiModel?) {
                val dateString = dateTimeFormatter.dateToDayWithoutCurrentYear(
                    Date(before.timestamp),
                    resourceManager.getString(R.string.common_today),
                    resourceManager.getString(R.string.common_yesterday)
                )

                if (after != null && !DateTimeUtils.isSameDay(
                        before.timestamp,
                        after.timestamp
                    )
                ) {
                    events.add(
                        i,
                        EventUiModel.EventTimeSeparatorUiModel(dateString)
                    )
                }
                if (i == 0) {
                    events.add(
                        0,
                        EventUiModel.EventTimeSeparatorUiModel(dateString)
                    )
                }
            }
            i--
        }
        return events.toList()
    }
}
