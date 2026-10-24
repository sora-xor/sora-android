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
