/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.fullpoolsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.PoolSettingsState
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.launch

@HiltViewModel
class FullPoolSettingsViewModel @Inject constructor(
    private val poolsInteractor: PoolsInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val walletRouter: WalletRouter,
) : BaseViewModel() {

    private val _settingsState = MutableLiveData<List<PoolSettingsState>>()
    val settingsState: LiveData<List<PoolSettingsState>> = _settingsState

    private val _assetPositions = MutableLiveData<Pair<Int, Int>>()
    val assetPositions: LiveData<Pair<Int, Int>> = _assetPositions

    private val curPoolList = mutableListOf<PoolSettingsState>()
    private var positions = mutableListOf<StringPair>()
    private var curFilter: String = ""
    private var symbol: String = ""

    internal var fiatSum by mutableStateOf("")

    init {
        viewModelScope.launch {
            val pools = poolsInteractor.getPoolsCache()

            curPoolList.clear()
            symbol = pools.fiatSymbol
            val mapped = pools.map { poolData ->
                PoolSettingsState(
                    id = poolData.baseToken.id to poolData.token.id,
                    token1Icon = poolData.baseToken.iconUri(),
                    token2Icon = poolData.token.iconUri(),
                    tokenName = "%s-%s".format(
                        poolData.baseToken.symbol,
                        poolData.token.symbol
                    ),
                    assetAmount = "%s - %s".format(
                        poolData.baseToken.printBalance(
                            poolData.basePooled,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        ),
                        poolData.token.printBalance(
                            poolData.secondPooled,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        ),
                    ),
                    favorite = poolData.favorite,
                    fiat = poolData.printFiat()?.first ?: 0.0
                )
            }
            curPoolList.addAll(mapped)
            positions.addAll(curPoolList.map { p -> p.id })
            filterAndUpdateAssetsList()
        }
    }

    private fun filterAndUpdateAssetsList() {
        val filter = curFilter.lowercase()
        val list = if (filter.isBlank()) {
            curPoolList
        } else {
            buildList {
                addAll(
                    curPoolList.filter {
                        it.tokenName.lowercase().contains(filter) ||
                            it.assetAmount.lowercase().contains(filter) ||
                            it.id.first.lowercase().contains(filter) ||
                            it.id.second.lowercase().contains(filter)
                    }
                )
            }
        }

        _settingsState.value = list
        fiatSum = if (list.isNotEmpty())
            list.map { it.fiat }.reduce { acc, d -> acc + d }.let {
                formatFiatAmount(it, symbol, numbersFormatter)
            } else ""
    }

    fun searchAssets(filter: String) {
        curFilter = filter
        filterAndUpdateAssetsList()
    }

    fun onFavoriteClick(asset: PoolSettingsState) {
        val checked = asset.favorite.not()
        val position = curPoolList.indexOfFirst { it.id == asset.id }
        if (position < 0) return
        curPoolList[position] = curPoolList[position].copy(favorite = checked)
        filterAndUpdateAssetsList()
        viewModelScope.launch {
            if (checked) {
                poolsInteractor.poolFavoriteOn(asset.id)
            } else {
                poolsInteractor.poolFavoriteOff(asset.id)
            }
        }
    }

    fun assetPositionChanged(from: Int, to: Int): Boolean {
        moveToken(from, to)
        _assetPositions.value = from to to
        viewModelScope.launch {
            updatePositions()
        }
        return true
    }

    fun onCloseClick() {
        walletRouter.popBackStackFragment()
    }

    private fun moveToken(from: Int, to: Int) {
        with(positions) {
            val item = removeAt(from)
            add(to, item)
        }
        with(curPoolList) {
            val item = removeAt(from)
            add(to, item)
        }
    }

    private suspend fun updatePositions() {
        poolsInteractor.updatePoolPosition(
            positions.mapIndexed { index, s -> s to index }.toMap()
        )
    }
}
