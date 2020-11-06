/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.model.AssetConfigurableModel
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.hide.model.AssetHidingModel
import java.util.Collections

class AssetSettingsViewModel(
    private val interactor: WalletInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val router: WalletRouter
) : BaseViewModel() {

    private val _displayingAssetsLiveData = MutableLiveData<List<AssetConfigurableModel>>()
    val displayingAssetsLiveData: LiveData<List<AssetConfigurableModel>> = _displayingAssetsLiveData

    private val _hidingAssetsLiveData = MutableLiveData<List<AssetHidingModel>>()
    val hidingAssetsLiveData: LiveData<List<AssetHidingModel>> = _hidingAssetsLiveData

    private val _hideButtonEnabledLiveData = MediatorLiveData<Boolean>()
    val hideButtonEnabledLiveData: LiveData<Boolean> = _hideButtonEnabledLiveData

    private val _addingAccountAvailableLiveData = MediatorLiveData<Boolean>()
    val addingAccountAvailableLiveData: LiveData<Boolean> = _addingAccountAvailableLiveData

    private val _showHidingAssetsView = MutableLiveData<Event<List<AssetHidingModel>>>()
    val showHidingAssetsView: LiveData<Event<List<AssetHidingModel>>> = _showHidingAssetsView

    private val _addButtonEnabledLiveData = MediatorLiveData<Boolean>()
    val addButtonEnabledLiveData: LiveData<Boolean> = _addButtonEnabledLiveData

    private val checkedDisplayingAssetsLiveData = MutableLiveData<MutableSet<AssetConfigurableModel>>()
    private val checkedHidingAssetsLiveData = MutableLiveData<MutableSet<AssetHidingModel>>()

    init {
        _hideButtonEnabledLiveData.value = false
        _addingAccountAvailableLiveData.value = false
        _addButtonEnabledLiveData.value = false

        _hideButtonEnabledLiveData.addSource(checkedDisplayingAssetsLiveData) {
            _hideButtonEnabledLiveData.value = it.isNotEmpty()
        }

        _addingAccountAvailableLiveData.addSource(hidingAssetsLiveData) {
            _addingAccountAvailableLiveData.value = it.isNotEmpty()
        }

        _addButtonEnabledLiveData.addSource(checkedHidingAssetsLiveData) {
            _addButtonEnabledLiveData.value = it.isNotEmpty()
        }

        disposables.add(
            interactor.getAssets()
                .map { mapAssetToAssetModel(it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val displayingAssets = it.first
                    val hidingAssets = it.second
                    _displayingAssetsLiveData.setValueIfNew(displayingAssets)
                    _hidingAssetsLiveData.value = hidingAssets
                }, {
                    logException(it)
                })
        )
    }

    private fun mapAssetToAssetModel(assets: List<Asset>): Pair<List<AssetConfigurableModel>, List<AssetHidingModel>> {
        val valAsset = assets.first { AssetHolder.SORA_VAL.id == it.id }
        val valErc20Asset = assets.first { AssetHolder.SORA_VAL_ERC_20.id == it.id }

        val soraAssetState = when (valAsset.state) {
            Asset.State.NORMAL -> AssetConfigurableModel.State.NORMAL
            Asset.State.ASSOCIATING -> AssetConfigurableModel.State.ASSOCIATING
            Asset.State.ERROR -> AssetConfigurableModel.State.ERROR
            Asset.State.UNKNOWN -> AssetConfigurableModel.State.NORMAL
        }

        val valAssetBalance = valAsset.assetBalance?.balance
        val valErc20AssetBalance = valErc20Asset.assetBalance?.balance

        val totalValBalance = if (valAssetBalance == null) {
            valErc20AssetBalance
        } else {
            if (valErc20AssetBalance == null) {
                valAssetBalance
            } else {
                valAssetBalance + valErc20AssetBalance
            }
        }

        val totalValBalanceFormatted = totalValBalance?.let {
            numbersFormatter.formatBigDecimal(it)
        }

        val valAssetIconResource = R.drawable.ic_val_red_24
        val valAssetIconBackground = resourceManager.getColor(R.color.uikit_lightRed)

        val ethAsset = assets.first { AssetHolder.ETHER_ETH.id == it.id }

        val ethAssetState = when (ethAsset.state) {
            Asset.State.NORMAL -> AssetConfigurableModel.State.NORMAL
            Asset.State.ASSOCIATING -> AssetConfigurableModel.State.ASSOCIATING
            Asset.State.ERROR -> AssetConfigurableModel.State.ERROR
            Asset.State.UNKNOWN -> AssetConfigurableModel.State.NORMAL
        }

        val ethAssetIconResource = R.drawable.ic_eth_24
        val ethAssetIconBackground = resourceManager.getColor(R.color.asset_view_eth_background_color)
        val ethAssetBalance = ethAsset.assetBalance?.balance?.let {
            numbersFormatter.formatBigDecimal(it)
        }

        val displayingAssets = mutableListOf<AssetConfigurableModel>().apply {
            val soraDisplayingAsset = AssetConfigurableModel(valAsset.id, valAsset.assetFirstName, valAsset.assetLastName, valAssetIconResource, valAssetIconBackground,
                valAsset.hidingAllowed, false, totalValBalanceFormatted, soraAssetState)
            soraDisplayingAsset.position = valAsset.position
            add(soraDisplayingAsset)
        }

        val hidingAssets = mutableListOf<AssetHidingModel>()

        if (ethAsset.displayAsset) {
            val ethDisplayingAsset = AssetConfigurableModel(ethAsset.id, ethAsset.assetFirstName, ethAsset.assetLastName, ethAssetIconResource, ethAssetIconBackground,
                ethAsset.hidingAllowed, false, ethAssetBalance, ethAssetState)
            ethDisplayingAsset.position = ethAsset.position
            displayingAssets.add(ethDisplayingAsset)
        } else {
            hidingAssets.add(AssetHidingModel(ethAsset.id, ethAsset.assetFirstName, ethAsset.assetLastName, ethAssetIconResource, ethAssetBalance))
        }

        val sortedAssets = displayingAssets.sortedBy { it.position }

        return Pair(sortedAssets, hidingAssets)
    }

    fun checkChanged(asset: AssetConfigurableModel, checked: Boolean) {
        if (checked) {
            val checkedAssetList = checkedDisplayingAssetsLiveData.value ?: mutableSetOf()
            checkedAssetList.add(asset)
            checkedDisplayingAssetsLiveData.value = checkedAssetList
        } else {
            val checkedAssetList = checkedDisplayingAssetsLiveData.value ?: mutableSetOf()
            checkedAssetList.remove(asset)
            checkedDisplayingAssetsLiveData.value = checkedAssetList
        }
    }

    fun doneClicked() {
        router.popBackStackFragment()
    }

    fun hideAssetsButtonClicked() {
        checkedDisplayingAssetsLiveData.value?.let {
            val assetIds = it.map { it.id }
            disposables.add(
                interactor.hideAssets(assetIds)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        checkedDisplayingAssetsLiveData.value = mutableSetOf()
                    }, {
                        it.printStackTrace()
                    })
            )
        }
    }

    fun addAssetClicked() {
        hidingAssetsLiveData.value?.let {
            _showHidingAssetsView.value = Event(it)
        }
    }

    fun hidingAssetCheckChanged(asset: AssetHidingModel, checked: Boolean) {
        if (checked) {
            val checkedAssetList = checkedHidingAssetsLiveData.value ?: mutableSetOf()
            checkedAssetList.add(asset)
            checkedHidingAssetsLiveData.value = checkedAssetList
        } else {
            val checkedAssetList = checkedHidingAssetsLiveData.value ?: mutableSetOf()
            checkedAssetList.remove(asset)
            checkedHidingAssetsLiveData.value = checkedAssetList
        }
    }

    fun displayAssetsButtonClicked() {
        checkedHidingAssetsLiveData.value?.let {
            val assetIds = it.map { it.id }
            disposables.add(
                interactor.displayAssets(assetIds)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        checkedHidingAssetsLiveData.value = mutableSetOf()
                    }, {
                        it.printStackTrace()
                    })
            )
        }
    }

    fun assetPositionChanged(from: Int, to: Int) {
        displayingAssetsLiveData.value?.let {
            val assets = mutableListOf<AssetConfigurableModel>().apply {
                addAll(it)
            }
            Collections.swap(assets, from, to)
            val assetsMap = mutableMapOf<String, Int>()
            assets.forEachIndexed { index, asset ->
                assetsMap.put(asset.id, index)
            }
            disposables.add(
                interactor.updateAssetPositions(assetsMap)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        _displayingAssetsLiveData.value = assets
                    }, {
                        it.printStackTrace()
                    })
            )
        }
    }
}