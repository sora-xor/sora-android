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

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_wallet_api.data.NetworkAccountActivity
import jp.co.soramitsu.feature_wallet_api.data.NetworkActivityRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val transactionHistoryHandler: TransactionHistoryHandler,
    private val networkActivityRepository: NetworkActivityRepository,
) : BaseViewModel() {

    private val _historyState = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val historyState = _historyState.asStateFlow()
    private val _networkActivity = MutableStateFlow(NetworkActivityState())
    val networkActivity = _networkActivity.asStateFlow()
    private val networkRetries = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            assetsInteractor.flowCurSoraAccount()
                .distinctUntilChangedBy { it.substrateAddress }
                .catch { onError(it) }
                .collectLatest { account ->
                    // Clear account-specific rows before collecting a replacement subscription.
                    val walletId = account.substrateAddress
                    _historyState.value = HistoryState.Loading
                    _networkActivity.value = NetworkActivityState(walletId = walletId)
                    coroutineScope {
                        launch {
                            // The handler is shared with asset details. Do not expose its cached
                            // rows until a refresh for this wallet has actually completed.
                            transactionHistoryHandler.refreshHistoryEvents()
                            transactionHistoryHandler.historyState.collect { _historyState.value = it }
                        }
                        networkRetries.collectLatest {
                            networkActivityRepository.observeCurrentWallet()
                                .catch {
                                    _networkActivity.value = NetworkActivityState(
                                        walletId = walletId,
                                        loading = false,
                                        error = true,
                                    )
                                }
                                .collect { networks ->
                                    _networkActivity.value = NetworkActivityState(
                                        walletId = walletId,
                                        accounts = networks.filter { it.walletId == walletId },
                                        loading = false,
                                    )
                                }
                        }
                    }
                }
        }
        transactionHistoryHandler.flowLocalTransactions()
            .catch { onError(it) }
            .onEach {
                transactionHistoryHandler.refreshHistoryEvents()
            }
            .launchIn(viewModelScope)
    }

    fun onTxHistoryItemClick(txHash: String) {
        assetsRouter.showTxDetails(txHash)
    }

    fun onMoreHistoryEventsRequested() {
        viewModelScope.launch {
            transactionHistoryHandler.onMoreHistoryEventsRequested()
        }
    }

    fun refresh() {
        val previous = _networkActivity.value
        _networkActivity.value = previous.copy(loading = true)
        networkActivityRepository.refresh()
        networkRetries.value++
        viewModelScope.launch {
            transactionHistoryHandler.refreshHistoryEvents()
        }
    }

    fun loadMoreNetworkHistory(account: NetworkAccountActivity) {
        val current = _networkActivity.value
        if (current.loading || account.walletId != current.walletId) return
        val active = current.accounts.singleOrNull {
            it.networkId == account.networkId && it.address == account.address
        } ?: return
        if (!active.hasMore) return
        _networkActivity.value = current.copy(loading = true)
        networkActivityRepository.loadMore(active.walletId, active.networkId)
    }
}
