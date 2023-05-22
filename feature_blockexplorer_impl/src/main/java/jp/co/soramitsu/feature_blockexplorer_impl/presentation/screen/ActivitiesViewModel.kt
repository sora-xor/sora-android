/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val walletInteractor: WalletInteractor,
    private val transactionHistoryHandler: TransactionHistoryHandler,
    private val mainRouter: MainRouter,
) : BaseViewModel() {

    val historyState = transactionHistoryHandler.historyState

    init {
        viewModelScope.launch {
            assetsInteractor.flowCurSoraAccount()
                .catch { onError(it) }
                .collectLatest {
                    refresh()
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
        viewModelScope.launch {
            transactionHistoryHandler.refreshHistoryEvents()
        }
    }
}
