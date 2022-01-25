/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.AssetConfigurableModel
import kotlinx.coroutines.launch
import java.util.Locale

class AssetSettingsViewModel(
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
        if (checked) {
            curAssetList.find { it.id == asset.id }?.visible = true
            viewModelScope.launch {
                interactor.displayAssets(listOf(asset.id))
            }
        } else {
            curAssetList.find { it.id == asset.id }?.visible = false
            viewModelScope.launch {
                interactor.hideAssets(listOf(asset.id))
            }
        }
    }

    fun backClicked() {
        router.popBackStackFragment()
    }

    fun assetPositionChanged(from: Int, to: Int): Boolean {
        if (!curAssetList[from].hideAllowed || !curAssetList[to].hideAllowed) return false
        with(positions) {
            val item = removeAt(from)
            add(to, item)
        }
        viewModelScope.launch {
            interactor.updateAssetPositions(
                positions.mapIndexed { index, s -> s to index }.toMap()
            )
        }
        with(curAssetList) {
            val item = removeAt(from)
            add(to, item)
        }
        _assetPositions.value = from to to
        return true
    }
}
