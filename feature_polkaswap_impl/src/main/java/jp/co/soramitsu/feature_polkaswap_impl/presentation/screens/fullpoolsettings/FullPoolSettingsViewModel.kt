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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.fullpoolsettings

import androidx.compose.runtime.getValue
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
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.PoolSettingsState
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _dragList = MutableLiveData<Boolean>()
    val dragList: LiveData<Boolean> = _dragList

    private val curPoolList = mutableListOf<PoolSettingsState>()
    private var positions = mutableListOf<StringPair>()
    private var curFilter: String = ""
    private var symbol: String = ""

    private val _fiatSum = MutableStateFlow("")
    internal val fiatSum = _fiatSum.asStateFlow()

    private val _poolsEmptyState = MutableStateFlow(false)
    val poolsEmptyState = _poolsEmptyState.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.Small(),
            basic = BasicToolbarState(
                title = "",
                navIcon = R.drawable.ic_cross_24,
                visibility = true,
                searchEnabled = true,
                searchValue = "",
                searchPlaceholder = jp.co.soramitsu.common.R.string.common_search_pools,
                actionLabel = jp.co.soramitsu.common.R.string.common_done,
            ),
        )
        viewModelScope.launch {
            val pools: List<CommonUserPoolData> = poolsInteractor.getPoolsCacheOfCurAccount()

            curPoolList.clear()
            symbol = pools.fiatSymbol
            val mapped = pools.map { poolData ->
                PoolSettingsState(
                    id = poolData.basic.baseToken.id to poolData.basic.targetToken.id,
                    token1Icon = poolData.basic.baseToken.iconUri(),
                    token2Icon = poolData.basic.targetToken.iconUri(),
                    tokenName = "%s-%s".format(
                        poolData.basic.baseToken.name,
                        poolData.basic.targetToken.name,
                    ),
                    tokenSymbol = "%s-%s".format(
                        poolData.basic.baseToken.symbol,
                        poolData.basic.targetToken.symbol,
                    ),
                    assetAmount = "%s - %s".format(
                        poolData.basic.baseToken.printBalance(
                            poolData.user.basePooled,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        ),
                        poolData.basic.targetToken.printBalance(
                            poolData.user.targetPooled,
                            numbersFormatter,
                            AssetHolder.ACTIVITY_LIST_ROUNDING
                        ),
                    ),
                    favorite = poolData.user.favorite,
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
                            it.tokenSymbol.lowercase().contains(filter) ||
                            it.assetAmount.lowercase().contains(filter) ||
                            it.id.first.lowercase().contains(filter) ||
                            it.id.second.lowercase().contains(filter)
                    }
                )
            }
        }

        _settingsState.value = list
        _poolsEmptyState.value = list.isEmpty()
        _fiatSum.value = if (list.isNotEmpty())
            list.map { it.fiat }.reduce { acc, d -> acc + d }.let {
                formatFiatAmount(it, symbol, numbersFormatter)
            } else ""
    }

    override fun onToolbarSearch(value: String) {
        _toolbarState.value = toolbarState.value?.copy(
            basic = toolbarState.value!!.basic.copy(
                searchValue = value
            )
        )

        _dragList.value = value.isBlank()
        curFilter = value
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

    override fun onAction() {
        walletRouter.popBackStackFragment()
    }

    override fun onNavIcon() {
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
