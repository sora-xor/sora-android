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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.txlist

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TxListViewModel @AssistedInject constructor(
    assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val transactionHistoryHandler: TransactionHistoryHandler,
    @Assisted private val tokenId: String
) : BaseViewModel() {

    @AssistedFactory
    interface TxListViewModelFactory {
        fun create(assetId: String): TxListViewModel
    }

    val state = transactionHistoryHandler.historyState

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.asset_details_recent_activity,
        )
        assetsInteractor.flowCurSoraAccount()
            .catch { onError(it) }
            .onEach { refresh() }
            .launchIn(viewModelScope)

        transactionHistoryHandler.flowLocalTransactions()
            .catch { onError(it) }
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun onMoreHistoryEventsRequested() {
        viewModelScope.launch {
            transactionHistoryHandler.onMoreHistoryEventsRequested()
        }
    }

    fun onTxHistoryItemClick(txHash: String) {
        assetsRouter.showTxDetails(txHash)
    }

    fun refresh() {
        viewModelScope.launch {
            transactionHistoryHandler.refreshHistoryEvents(tokenId)
        }
    }
}
