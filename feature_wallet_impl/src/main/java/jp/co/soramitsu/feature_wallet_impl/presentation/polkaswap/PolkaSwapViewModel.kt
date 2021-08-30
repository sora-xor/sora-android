/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.launch

class PolkaSwapViewModel(
    private val router: WalletRouter,
    private val polkaswapInteractor: PolkaswapInteractor,
) : BaseViewModel() {

    private val _selectedMarketLiveData = MutableLiveData<Market>()
    val selectedMarketLiveData: LiveData<Market> = _selectedMarketLiveData

    private val _marketListLiveData = SingleLiveEvent<Pair<List<Market>, Market>>()
    val marketListLiveData: LiveData<Pair<List<Market>, Market>> = _marketListLiveData

    init {
        _selectedMarketLiveData.value = Market.SMART
    }

    fun marketClicked(market: Market) {
        _selectedMarketLiveData.value = market
        polkaswapInteractor.setSwapMarket(market)
    }

    fun marketSettingsClicked() {
        viewModelScope.launch {
            tryCatch {
                val markets = polkaswapInteractor.getAvailableSources()
                if (markets.isNotEmpty()) {
                    _marketListLiveData.value =
                        markets to (_selectedMarketLiveData.value ?: Market.SMART)
                }
            }
        }
    }

    fun backPressed() {
        router.popBackStackFragment()
    }
}
