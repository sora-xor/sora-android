/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.fiatSymbol
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetSettingsState
import kotlinx.coroutines.launch

@HiltViewModel
class FullAssetSettingsViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val numbersFormatter: NumbersFormatter,
) : BaseViewModel() {

    private val _settingsState = MutableLiveData<List<AssetSettingsState>>()
    val settingsState: LiveData<List<AssetSettingsState>> = _settingsState

    private val _assetPositions = MutableLiveData<Pair<Int, Int>>()
    val assetPositions: LiveData<Pair<Int, Int>> = _assetPositions

    private val curAssetList = mutableListOf<AssetSettingsState>()
    private var positions = mutableListOf<String>()
    private var curFilter: String = ""
    private val tokensToMove = mutableListOf<Pair<String, Boolean>>()
    private var symbol: String = ""

    internal var fiatSum by mutableStateOf("")

    init {
        viewModelScope.launch {
            curAssetList.clear()
            curAssetList.addAll(
                assetsInteractor.getWhitelistAssets()
                    .also {
                        symbol = it.fiatSymbol()
                    }
                    .map { asset ->
                        AssetSettingsState(
                            id = asset.token.id,
                            tokenIcon = asset.token.iconUri(),
                            tokenName = asset.token.name,
                            symbol = asset.token.symbol,
                            assetAmount = asset.token.printBalance(
                                asset.balance.transferable,
                                numbersFormatter,
                                AssetHolder.ROUNDING
                            ),
                            favorite = asset.favorite,
                            visible = asset.visibility,
                            hideAllowed = asset.token.isHidable,
                            fiat = asset.fiat,
                        )
                    }
            )
            positions.addAll(curAssetList.map { it.id })
            filterAndUpdateAssetsList()
        }
    }

    private fun filterAndUpdateAssetsList() {
        val filter = curFilter.lowercase()
        val list = if (filter.isBlank()) {
            curAssetList
        } else {
            buildList {
                addAll(
                    curAssetList.filter {
                        it.tokenName.lowercase().contains(filter) ||
                            it.symbol.lowercase().contains(filter) ||
                            it.id.lowercase().contains(filter)
                    }
                )
            }
        }
        _settingsState.value = list
        fiatSum = if (list.isNotEmpty())
            list.map { it.fiat ?: 0.0 }.reduce { acc, d -> acc + d }.let {
                formatFiatAmount(it, symbol, numbersFormatter)
            } else ""
    }

    fun searchAssets(filter: String) {
        curFilter = filter
        filterAndUpdateAssetsList()
    }

    fun onFavoriteClick(asset: AssetSettingsState) {
        val checked = asset.favorite.not()
        val position = curAssetList.indexOfFirst { it.id == asset.id }
        if (position < 0) return
        curAssetList[position] = curAssetList[position].copy(favorite = checked)
        filterAndUpdateAssetsList()
        val index = tokensToMove.indexOfFirst { it.first == asset.id }
        if (index >= 0) {
            tokensToMove.removeAt(index)
        } else {
            if (!AssetHolder.isKnownAsset(asset.id)) {
                tokensToMove.add(asset.id to checked)
            }
        }
        viewModelScope.launch {
            if (checked) {
                assetsInteractor.tokenFavoriteOn(listOf(asset.id))
            } else {
                assetsInteractor.tokenFavoriteOff(listOf(asset.id))
            }
        }
    }

    fun assetPositionChanged(from: Int, to: Int): Boolean {
        if (!curAssetList[from].hideAllowed ||
            !curAssetList[to].hideAllowed ||
            AssetHolder.isKnownAsset(curAssetList[from].id) ||
            AssetHolder.isKnownAsset(curAssetList[to].id)
        ) return false
        val index = tokensToMove.indexOfFirst { it.first == curAssetList[from].id }
        if (index >= 0) {
            tokensToMove.removeAt(index)
        }
        moveToken(from, to)
        viewModelScope.launch {
            updatePositions()
        }
        _assetPositions.value = from to to
        return true
    }

    fun onVisibilityClick(asset: AssetSettingsState) {
        val position = curAssetList.indexOfFirst { it.id == asset.id }
        if (position < 0) return
        viewModelScope.launch {
            tryCatch {
                val visibility = asset.visible.not()
                assetsInteractor.toggleVisibilityOfToken(asset.id, visibility)
                curAssetList[position] = curAssetList[position].copy(visible = visibility)
                filterAndUpdateAssetsList()
            }
        }
    }

    fun onCloseClick() {
        val unknownVisibleCount =
            curAssetList.count { it.favorite && !AssetHolder.isKnownAsset(it.id) }
        val position = AssetHolder.knownCount() + unknownVisibleCount
        val lastUnknownVisibleIndex =
            curAssetList.indexOfLast { it.favorite && !AssetHolder.isKnownAsset(it.id) }
        val offTokensId = tokensToMove.filter { !it.second }.map { pair ->
            curAssetList.indexOfFirst { it.id == pair.first }
        }.filter { it in 0 until lastUnknownVisibleIndex }
        moveTokens(offTokensId.sortedDescending(), position)
        val firstUnknownInvisibleIndex =
            curAssetList.indexOfFirst { !it.favorite && !AssetHolder.isKnownAsset(it.id) }
        if (firstUnknownInvisibleIndex >= 0) {
            val onTokensId = tokensToMove.filter { it.second }.map { pair ->
                curAssetList.indexOfFirst { it.id == pair.first }
            }.filter { it >= 0 && it > firstUnknownInvisibleIndex }
            moveTokens(onTokensId.sortedDescending(), firstUnknownInvisibleIndex)
        }
        viewModelScope.launch {
            updatePositions()
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
