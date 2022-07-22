/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.AssetConfigurableModel
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AssetSettingsViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val router: WalletRouter
) : BaseViewModel() {

    private val _assetsListLiveData = MutableLiveData<List<AssetConfigurableModel>>()
    val assetsListLiveData: LiveData<List<AssetConfigurableModel>> = _assetsListLiveData

    private val _assetPositions = MutableLiveData<Pair<Int, Int>>()
    val assetPositions: LiveData<Pair<Int, Int>> = _assetPositions

    private val curAssetList = mutableListOf<AssetConfigurableModel>()
    private var positions = mutableListOf<String>()
    private var curFilter: String = ""
    private val tokensToMove = mutableListOf<Pair<String, Boolean>>()

    init {
        updateAssetList()
    }

    private fun updateAssetList() {
        viewModelScope.launch {
            val list = mapAssetToAssetModel(interactor.getWhitelistAssets())
            curAssetList.addAll(list)
            positions.addAll(curAssetList.map { it.id })
            _assetsListLiveData.value = curAssetList
        }
    }

    private fun mapAssetToAssetModel(assets: List<Asset>): List<AssetConfigurableModel> =
        assets.sortedBy { it.position }.map {
            AssetConfigurableModel(
                it.token.id,
                it.token.name,
                it.token.symbol,
                it.token.icon,
                it.token.isHidable,
                numbersFormatter.formatBigDecimal(it.balance.transferable, it.token.precision),
                it.isDisplaying
            )
        }

    private fun filterAssetsList() {
        val filter = curFilter.lowercase(Locale.getDefault())
        val list = if (filter.isBlank()) {
            curAssetList
        } else {
            mutableListOf<AssetConfigurableModel>().apply {
                addAll(
                    curAssetList.filter {
                        it.assetFirstName.lowercase(Locale.getDefault())
                            .contains(filter) || it.assetLastName.lowercase(Locale.getDefault())
                            .contains(filter)
                    }
                )
            }
        }
        _assetsListLiveData.value = list
    }

    fun searchAssets(filter: String) {
        curFilter = filter
        filterAssetsList()
    }

    fun checkChanged(asset: AssetConfigurableModel, checked: Boolean) {
        curAssetList.find { it.id == asset.id }?.visible = checked
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
                interactor.displayAssets(listOf(asset.id))
            } else {
                interactor.hideAssets(listOf(asset.id))
            }
        }
    }

    fun backClicked() {
        val unknownVisibleCount =
            curAssetList.count { it.visible && !AssetHolder.isKnownAsset(it.id) }
        val position = AssetHolder.knownCount() + unknownVisibleCount
        val lastUnknownVisibleIndex = curAssetList.indexOfLast { it.visible && !AssetHolder.isKnownAsset(it.id) }
        val offTokensId = tokensToMove.filter { !it.second }.map { pair ->
            curAssetList.indexOfFirst { it.id == pair.first }
        }.filter { it in 0 until lastUnknownVisibleIndex }
        moveTokens(offTokensId.sortedDescending(), position)
        val firstUnknownInvisibleIndex = curAssetList.indexOfFirst { !it.visible && !AssetHolder.isKnownAsset(it.id) }
        if (firstUnknownInvisibleIndex >= 0) {
            val onTokensId = tokensToMove.filter { it.second }.map { pair ->
                curAssetList.indexOfFirst { it.id == pair.first }
            }.filter { it >= 0 && it > firstUnknownInvisibleIndex }
            moveTokens(onTokensId.sortedDescending(), firstUnknownInvisibleIndex)
        }
        viewModelScope.launch {
            updatePositions()
            router.popBackStackFragment()
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

    private fun moveTokens(from: List<Int>, to: Int) {
        if (from.isEmpty()) return
        val removingIds = mutableListOf<String>()
        val removingTokens = mutableListOf<AssetConfigurableModel>()
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
        interactor.updateAssetPositions(
            positions.mapIndexed { index, s -> s to index }.toMap()
        )
    }
}
