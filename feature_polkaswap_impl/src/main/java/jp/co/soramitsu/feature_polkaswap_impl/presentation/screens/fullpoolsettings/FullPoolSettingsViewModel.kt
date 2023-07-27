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
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
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
            val pools: List<CommonUserPoolData> = poolsInteractor.getPoolsCacheOfCurAccount()

            curPoolList.clear()
            symbol = pools.fiatSymbol
            val mapped = pools.map { poolData ->
                PoolSettingsState(
                    id = poolData.basic.baseToken.id to poolData.basic.targetToken.id,
                    token1Icon = poolData.basic.baseToken.iconUri(),
                    token2Icon = poolData.basic.targetToken.iconUri(),
                    tokenName = "%s-%s".format(
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
