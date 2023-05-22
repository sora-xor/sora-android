/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.domain

import java.util.Date
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DateTimeUtils
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
                historyEndReached,
                insertHistoryTimeSeparators(historyEvents),
                hasError
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
