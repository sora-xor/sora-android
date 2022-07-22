/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.util.DateTimeUtils
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionHistoryHandler @Inject constructor(
    private val transactionMappers: TransactionMappers,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository,
    coroutineManager: CoroutineManager,
) {

    init {
        userRepository.flowCurSoraAccount()
            .catch { }
            .onEach { transactionHistoryRepository.onSoraAccountChange(it) }
            .launchIn(coroutineManager.applicationScope)
    }

    val historyState = MutableStateFlow<HistoryState>(HistoryState.Loading)
    private var historyEvents = mutableListOf<EventUiModel>()
    private var historyPage: Long = 1
    private var historyEndReached: Boolean = false
    private var loadMoreHistoryJob: Job? = null
    private var tokenIdFilter: String? = null

    fun flowLocalTransactions(): Flow<Boolean> = transactionHistoryRepository.state

    fun onMoreHistoryEventsRequested(scope: CoroutineScope) {
        if (historyEndReached) return
        val job = loadMoreHistoryJob
        if (job == null || job.isCompleted || job.isCancelled) {
            loadMoreHistoryJob = scope.launch {
                try {
                    historyPage++
                    val events = loadEvents()
                    historyState.emit(events)
                } catch (t: Throwable) {
                    historyState.emit(HistoryState.Error)
                }
            }
        }
    }

    suspend fun refreshHistoryEvents(tokenId: String? = null) {
        tokenIdFilter = tokenId
        historyState.emit(HistoryState.Loading)
        val events = reloadHistoryEvents()
        historyState.emit(events)
    }

    suspend fun getTransaction(txHash: String): Transaction? {
        return transactionHistoryRepository.getTransaction(
            txHash,
            walletRepository.tokensList(),
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
        val transactionsInfo = transactionHistoryRepository.getTransactionHistory(
            historyPage,
            walletRepository.tokensList(),
            userRepository.getCurSoraAccount(),
            tokenIdFilter,
        )
        historyEndReached = transactionsInfo.endReached
        val mapped = transactionsInfo.transactions.map {
            transactionMappers.mapTransaction(it)
        }
        historyEvents.addAll(mapped)
        return buildHistoryList()
    }

    private fun buildHistoryList(): HistoryState {
        return if (historyEvents.isEmpty()) HistoryState.NoData else HistoryState.History(
            historyEndReached,
            insertHistoryTimeSeparators(historyEvents)
        )
    }

    private fun insertHistoryTimeSeparators(events: MutableList<EventUiModel>): List<EventUiModel> {
        var i = events.lastIndex
        while (i >= 0) {
            val before = events[i]
            val after = events.getOrNull(i - 1)
            if (before is EventUiModel.EventTxUiModel && after is EventUiModel.EventTxUiModel?) {
                if (after != null && !DateTimeUtils.isSameDay(
                        before.timestamp,
                        after.timestamp
                    )
                ) {
                    events.add(
                        i,
                        EventUiModel.EventTimeSeparatorUiModel(
                            transactionMappers.dateTimeFormatter.formatDate(
                                Date(before.timestamp),
                                DateTimeFormatter.MMMM_DD_YYYY
                            )
                        )
                    )
                }
                if (i == 0) {
                    events.add(
                        0,
                        EventUiModel.EventTimeSeparatorUiModel(
                            transactionMappers.dateTimeFormatter.formatDate(
                                Date(before.timestamp),
                                DateTimeFormatter.MMMM_DD_YYYY
                            )
                        )
                    )
                }
            }
            i--
        }
        return events.toList()
    }
}

sealed interface HistoryState {
    object Loading : HistoryState
    object Error : HistoryState
    object NoData : HistoryState
    data class History(
        val endReached: Boolean,
        val events: List<EventUiModel>,
    ) : HistoryState
}
