/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.txlist

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
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
