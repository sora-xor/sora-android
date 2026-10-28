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
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.util.DateTimeUtils
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionMappers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TransactionHistoryHandlerImpl @Inject constructor(
    private val assetsRepository: AssetsRepository,
    private val transactionMappers: TransactionMappers,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val resourceManager: ResourceManager,
    private val userRepository: UserRepository,
    private val dateTimeFormatter: DateTimeFormatter,
    private val coroutineManager: CoroutineManager,
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

    private val historyEvents = mutableListOf<EventUiModel.EventTxUiModel>()
    // Last successfully loaded page. Failed reads must leave their page available for retry.
    private var historyPage: Long = 0
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
        withContext(coroutineManager.io) {
            if (mutex.isLocked) return@withContext
            mutex.withLock {
                if (historyEndReached) return@withLock
                try {
                    val events = loadEvents()
                    _historyState.emit(events)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    _historyState.emit(buildHistoryList(hasError = true))
                }
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
        withContext(coroutineManager.io) {
            // Account changes must wait for the current read, not silently drop the refresh
            // and leave the previous wallet's cached history on screen.
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
    }

    override suspend fun getTransaction(txHash: String): Transaction? {
        return withContext(coroutineManager.io) {
            transactionHistoryRepository.getTransaction(
                txHash,
                assetsRepository.tokensList(),
                userRepository.getCurSoraAccount()
            )
        }
    }

    override suspend fun checkWaitingTransaction(txHash: String) {
        withContext(coroutineManager.io) {
            transactionHistoryRepository.checkWaitingTransaction(
                walletId = userRepository.getCurSoraAccount().substrateAddress,
                txHash = txHash,
            )
        }
    }

    private suspend fun reloadHistoryEvents(): HistoryState =
        try {
            historyPage = 0
            historyEndReached = false
            historyEvents.clear()
            loadEvents()
        } catch (cancelled: CancellationException) {
            // A cancelled account/asset refresh must not expose the previous selection's rows
            // or leave its pull-to-refresh spinner running indefinitely.
            _historyState.value = HistoryState.Loading
            throw cancelled
        } catch (t: Throwable) {
            HistoryState.Error
        }

    /**
     * Loads the page after [historyPage], updates matching rows, and appends new rows.
     *
     * The indexer adapter re-reads pages 1..N on every "load more". When a new row for the
     * account is indexed between two loads, every older row shifts down by one, so page N starts
     * with rows the previous page already returned. The local overlay on page 1 can also list a
     * transaction that later appears in an indexed page. Appending those again would show the same
     * transaction twice and give the Activity list two items with one key, which crashes Compose.
     *
     * If a page brings no new rows (all shifted duplicates, or none matching the token filter),
     * the next page is read automatically a bounded number of times, so the list never waits on
     * a load-more that the scroll position cannot trigger again.
     */
    private suspend fun loadEvents(): HistoryState {
        val curAccount = userRepository.getCurSoraAccount()
        val tokens = assetsRepository.tokensList()
        val listed = HashMap<String, Int>()
        historyEvents.forEachIndexed { index, event ->
            listed[event.historyIdentity()] = index
        }
        var pagesWithoutNewRows = 0
        while (true) {
            val page = historyPage + 1
            val transactionsInfo = transactionHistoryRepository.getTransactionHistory(
                page,
                tokens,
                curAccount,
                tokenIdFilter,
            )
            val mappedRows = transactionsInfo.transactions
                .map { transactionMappers.mapTransaction(it, curAccount.substrateAddress) }
            var newRowCount = 0
            mappedRows.forEach { row ->
                val identity = row.historyIdentity()
                val existingIndex = listed[identity]
                if (existingIndex == null) {
                    listed[identity] = historyEvents.size
                    historyEvents.add(row)
                    newRowCount++
                } else {
                    // A retried/overlapping page may confirm a previously pending local row.
                    historyEvents[existingIndex] = row
                }
            }
            val hasError = !transactionsInfo.errorMessage.isNullOrEmpty()
            // An offline response may contain local rows and endReached=true. Keep those rows,
            // but retry the indexed page before advancing or declaring history exhausted.
            historyEndReached = !hasError && transactionsInfo.endReached
            if (!hasError) historyPage = page
            if (
                newRowCount > 0 ||
                historyEndReached ||
                hasError ||
                page == 1L ||
                pagesWithoutNewRows >= MAX_EXTRA_PAGES_WITHOUT_NEW_ROWS
            ) {
                return buildHistoryList(hasError)
            }
            pagesWithoutNewRows++
        }
    }

    private fun EventUiModel.EventTxUiModel.historyIdentity(): String = txHash.lowercase()

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

    private fun insertHistoryTimeSeparators(rows: List<EventUiModel>): List<EventUiModel> {
        // Rebuild headers from current timestamps without retaining them in the transaction cache.
        val events = rows.toMutableList()
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

    private companion object {
        const val MAX_EXTRA_PAGES_WITHOUT_NEW_ROWS = 2
    }
}
