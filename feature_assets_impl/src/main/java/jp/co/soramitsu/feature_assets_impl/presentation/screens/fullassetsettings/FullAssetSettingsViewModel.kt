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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.fiatSymbol
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.isMatchFilter
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetSettingsState
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FullAssetSettingsViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val numbersFormatter: NumbersFormatter,
) : BaseViewModel() {

    private val _settingsState = MutableLiveData<List<AssetSettingsState>>()
    val settingsState: LiveData<List<AssetSettingsState>> = _settingsState

    private val _dragList = MutableLiveData<Boolean>()
    val dragList: LiveData<Boolean> = _dragList

    private val curAssetList = mutableListOf<AssetSettingsState>()
    private var positions = mutableListOf<String>()
    private var curFilter: String = ""
    private val tokensToMove = mutableListOf<Pair<String, Boolean>>()
    private var symbol: String = ""

    private val _fiatSum = MutableStateFlow("")
    val fiatSum = _fiatSum.asStateFlow()

    private val _settingsStateEmpty = MutableStateFlow(true)
    val settingsStateEmpty = _settingsStateEmpty.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            basic = BasicToolbarState(
                title = "",
                navIcon = R.drawable.ic_cross_24,
                actionLabel = jp.co.soramitsu.common.R.string.common_done,
                searchValue = "",
                searchEnabled = true,
                searchPlaceholder = jp.co.soramitsu.common.R.string.search_token_placeholder,
            ),
            type = SoramitsuToolbarType.Small(),
        )

        viewModelScope.launch {
            curAssetList.clear()
            curAssetList.addAll(
                assetsInteractor.getWhitelistAssets()
                    .also {
                        symbol = it.fiatSymbol()
                    }
                    .map { asset ->
                        AssetSettingsState(
                            token = asset.token,
                            symbol = asset.token.symbol,
                            assetAmount = asset.token.printBalance(
                                asset.balance.transferable,
                                numbersFormatter,
                                AssetHolder.ROUNDING
                            ),
                            favorite = asset.favorite,
                            visible = asset.visibility,
                            fiat = asset.fiat,
                        )
                    }
            )
            positions.addAll(curAssetList.map { it.token.id })
            filterAndUpdateAssetsList()
        }
    }

    override fun onToolbarSearch(value: String) {
        _dragList.value = value.isBlank()
        curFilter = value
        filterAndUpdateAssetsList()
    }

    override fun onAction() {
        onCloseClick()
    }

    override fun onNavIcon() {
        onCloseClick()
    }

    private fun filterAndUpdateAssetsList() {
        val filter = curFilter.lowercase()
        val list = if (filter.isBlank()) {
            curAssetList
        } else {
            buildList {
                addAll(
                    curAssetList.filter {
                        it.token.isMatchFilter(filter)
                    }
                )
            }
        }
        _settingsState.value = list
        _settingsStateEmpty.value = list.isEmpty()
        _fiatSum.value = if (list.isNotEmpty())
            list.map { it.fiat ?: 0.0 }.reduce { acc, d -> acc + d }.let {
                formatFiatAmount(it, symbol, numbersFormatter)
            } else ""
    }

    fun onFavoriteClick(asset: AssetSettingsState) {
        val checked = asset.favorite.not()
        val position = curAssetList.indexOfFirst { it.token.id == asset.token.id }
        if (position < 0) return
        curAssetList[position] = curAssetList[position].copy(favorite = checked)
        filterAndUpdateAssetsList()
        val index = tokensToMove.indexOfFirst { it.first == asset.token.id }
        if (index >= 0) {
            tokensToMove.removeAt(index)
        } else {
            if (!AssetHolder.isKnownAsset(asset.token.id)) {
                tokensToMove.add(asset.token.id to checked)
            }
        }
        viewModelScope.launch {
            if (checked) {
                assetsInteractor.tokenFavoriteOn(listOf(asset.token.id))
            } else {
                assetsInteractor.tokenFavoriteOff(listOf(asset.token.id))
            }
        }
    }

    fun assetPositionChanged(from: Int, to: Int): Boolean {
        if (!curAssetList[from].token.isHidable ||
            !curAssetList[to].token.isHidable ||
            AssetHolder.isKnownAsset(curAssetList[from].token.id) ||
            AssetHolder.isKnownAsset(curAssetList[to].token.id)
        ) return false
        val index = tokensToMove.indexOfFirst { it.first == curAssetList[from].token.id }
        if (index >= 0) {
            tokensToMove.removeAt(index)
        }
        moveToken(from, to)
        viewModelScope.launch {
            updatePositions()
        }
        filterAndUpdateAssetsList()
        return true
    }

    fun onVisibilityClick(asset: AssetSettingsState) {
        val position = curAssetList.indexOfFirst { it.token.id == asset.token.id }
        if (position < 0) return
        viewModelScope.launch {
            tryCatch {
                val visibility = asset.visible.not()
                assetsInteractor.toggleVisibilityOfToken(asset.token.id, visibility)
                curAssetList[position] = curAssetList[position].copy(visible = visibility)
                filterAndUpdateAssetsList()
            }
        }
    }

    private fun onCloseClick() {
        val unknownVisibleCount =
            curAssetList.count { it.favorite && !AssetHolder.isKnownAsset(it.token.id) }
        val position = AssetHolder.knownCount() + unknownVisibleCount
        val lastUnknownVisibleIndex =
            curAssetList.indexOfLast { it.favorite && !AssetHolder.isKnownAsset(it.token.id) }
        val offTokensId = tokensToMove.filter { !it.second }.map { pair ->
            curAssetList.indexOfFirst { it.token.id == pair.first }
        }.filter { it in 0 until lastUnknownVisibleIndex }
        moveTokens(offTokensId.sortedDescending(), position)
        val firstUnknownInvisibleIndex =
            curAssetList.indexOfFirst { !it.favorite && !AssetHolder.isKnownAsset(it.token.id) }
        if (firstUnknownInvisibleIndex >= 0) {
            val onTokensId = tokensToMove.filter { it.second }.map { pair ->
                curAssetList.indexOfFirst { it.token.id == pair.first }
            }.filter { it >= 0 && it > firstUnknownInvisibleIndex }
            moveTokens(onTokensId.sortedDescending(), firstUnknownInvisibleIndex)
        }
        viewModelScope.launch {
            updatePositions()
            assetsInteractor.updateBalanceVisibleAssets()
            assetsRouter.popBackStackFragment()
        }
    }

    private fun moveTokens(from: List<Int>, to: Int) {
        if (from.isEmpty()) return
        val removingIds = mutableListOf<String>()
        val removingTokens = mutableListOf<AssetSettingsState>()
        from.forEach {
            val item = positions.removeAt(it)
            removingIds.add(item)
            val itemToken = curAssetList.removeAt(it)
            removingTokens.add(itemToken)
        }
        positions.addAll(to, removingIds.reversed())
        curAssetList.addAll(to, removingTokens.reversed())
    }

    private fun moveToken(from: Int, to: Int) {
        with(positions) {
            val item = removeAt(from)
            add(to, item)
        }
        with(curAssetList) {
            val item = removeAt(from)
            add(to, item)
        }
    }

    private suspend fun updatePositions() {
        assetsInteractor.updateAssetPositions(
            positions.mapIndexed { index, s -> s to index }.toMap()
        )
    }
}
