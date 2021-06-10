/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.AssetConfigurableModel
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

    private val changeVisibility = mutableListOf<Pair<String, Boolean>>()
    private val changePosition = mutableListOf<String>()
    private val curAssetList = mutableListOf<AssetConfigurableModel>()
    private var displayedAssetList = mutableListOf<AssetConfigurableModel>()
    private var curFilter: String = ""

    init {
        updateAssetList()
    }

    private fun updateAssetList() {
        disposables.add(
            interactor.getAssets()
                .map { mapAssetToAssetModel(it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        curAssetList.addAll(it)
                        displayedAssetList.addAll(it)
                        changePosition.addAll(it.map { m -> m.id })
                        _assetsListLiveData.value = displayedAssetList
                    },
                    {
                        logException(it)
                    }
                )
        )
    }

    private fun mapAssetToAssetModel(assets: List<Asset>): List<AssetConfigurableModel> =
        assets.sortedBy { it.position }.map {
            AssetConfigurableModel(
                it.id, it.assetName, it.symbol, it.iconShadow,
                it.hidingAllowed, numbersFormatter.formatBigDecimal(it.balance, it.precision),
                it.display
            )
        }

    private fun filterAssetsList() {
        val filter = curFilter.toLowerCase(Locale.getDefault())
        if (filter.isBlank()) {
            displayedAssetList = curAssetList
            _assetsListLiveData.value = displayedAssetList
            return
        }
        displayedAssetList = mutableListOf<AssetConfigurableModel>().apply {
            addAll(
                curAssetList.filter {
                    it.assetFirstName.toLowerCase(Locale.getDefault())
                        .contains(filter) || it.assetLastName.toLowerCase(Locale.getDefault())
                        .contains(filter)
                }
            )
        }
        _assetsListLiveData.value = displayedAssetList
    }

    fun searchAssets(filter: String) {
        curFilter = filter
        filterAssetsList()
    }

    fun checkChanged(asset: AssetConfigurableModel, checked: Boolean) {
        changeVisibility.indexOfFirst { it.first == asset.id }.let {
            if (it >= 0) changeVisibility.removeAt(it)
        }
        changeVisibility.add(asset.id to checked)
    }

    fun backClicked() {
        router.popBackStackFragment()
    }

    fun doneClicked() {
        disposables.add(
            interactor.updateAssetPositions(
                changePosition.mapIndexed { index, s -> s to index }.toMap()
            )
                .andThen(
                    interactor.displayAssets(changeVisibility.filter { it.second }.map { it.first })
                )
                .andThen(
                    interactor.hideAssets(changeVisibility.filter { !it.second }.map { it.first })
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        router.popBackStackFragment()
                    },
                    {
                        onError(it)
                    }
                )
        )
    }

    fun assetPositionChanged(from: Int, to: Int): Boolean {
        if (!displayedAssetList[from].changeCheckStateEnabled || !displayedAssetList[to].changeCheckStateEnabled) return false
        val originalFrom =
            requireNotNull(curAssetList.indexOfFirst { it.id == displayedAssetList[from].id })
        val originalTo =
            requireNotNull(curAssetList.indexOfFirst { it.id == displayedAssetList[to].id })
        with(changePosition) {
            val item = removeAt(from)
            add(to, item)
        }
        with(displayedAssetList) {
            val item = removeAt(originalFrom)
            add(originalTo, item)
        }
        _assetPositions.value = from to to
        return true
    }
}
