/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PolkaSwapViewModel @Inject constructor(
    private val router: WalletRouter,
    private val polkaswapInteractor: PolkaswapInteractor,
) : BaseViewModel() {

    private val _selectedMarketLiveData = MutableLiveData<Market>()
    val selectedMarketLiveData: LiveData<Market> = _selectedMarketLiveData

    private val _showMarketDialogLiveData = SingleLiveEvent<Pair<Market, List<Market>>>()
    val showMarketDialogLiveData: LiveData<Pair<Market, List<Market>>> = _showMarketDialogLiveData

    private val _disclaimerLiveData = MutableLiveData<Boolean>()
    val disclaimerLiveData: LiveData<Boolean> = _disclaimerLiveData

    private var currentVisibility: Boolean = true

    init {
        _selectedMarketLiveData.value = Market.SMART
        polkaswapInteractor.getPolkaswapDisclaimerVisibility()
            .catch {
                onError(it)
            }
            .onEach {
                currentVisibility = it
                _disclaimerLiveData.value = it
            }
            .launchIn(viewModelScope)

        polkaswapInteractor.observeSelectedMarket()
            .catch {
                onError(it)
            }
            .onEach {
                _selectedMarketLiveData.value = it
            }
            .launchIn(viewModelScope)
    }

    fun onDisclaimerSwipe() {
        viewModelScope.launch {
            tryCatch {
                polkaswapInteractor.setPolkaswapDisclaimerVisibility(currentVisibility.not())
            }
        }
    }

    fun marketClicked(market: Market) {
        polkaswapInteractor.setSwapMarket(market)
    }

    fun marketSettingsClicked() {
        viewModelScope.launch {
            tryCatch {
                val selected = _selectedMarketLiveData.value ?: Market.SMART
                val markets = polkaswapInteractor.getAvailableSources()

                if (markets.isNotEmpty()) {
                    _showMarketDialogLiveData.value = selected to markets
                }
            }
        }
    }

    fun backPressed() {
        router.popBackStackFragment()
    }
}
