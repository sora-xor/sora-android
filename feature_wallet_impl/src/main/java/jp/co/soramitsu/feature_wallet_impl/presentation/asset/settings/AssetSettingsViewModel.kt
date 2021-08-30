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

    private val changeVisibility = mutableListOf<Pair<String, Boolean>>()
    private val changePosition = mutableListOf<String>()
    private val curAssetList = mutableListOf<AssetConfigurableModel>()
    private var displayedAssetList = mutableListOf<AssetConfigurableModel>()
    private var curFilter: String = ""

    init {
        updateAssetList()
    }

    private fun updateAssetList() {
        viewModelScope.launch {
            val list = mapAssetToAssetModel(interactor.getWhitelistAssets())
            curAssetList.addAll(list)
            displayedAssetList.addAll(list)
            changePosition.addAll(list.map { m -> m.id })
            _assetsListLiveData.value = displayedAssetList
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
        if (filter.isBlank()) {
            displayedAssetList = curAssetList
            _assetsListLiveData.value = displayedAssetList
            return
        }
        displayedAssetList = mutableListOf<AssetConfigurableModel>().apply {
            addAll(
                curAssetList.filter {
                    it.assetFirstName.lowercase(Locale.getDefault())
                        .contains(filter) || it.assetLastName.lowercase(Locale.getDefault())
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
        viewModelScope.launch {
            tryCatch {
                interactor.updateAssetPositions(
                    changePosition.mapIndexed { index, s -> s to index }
                        .toMap()
                )
                interactor.displayAssets(changeVisibility.filter { it.second }.map { it.first })
                interactor.hideAssets(changeVisibility.filter { !it.second }.map { it.first })
                changePosition.clear()
                changeVisibility.clear()
                router.popBackStackFragment()
            }
        }
    }

    fun assetPositionChanged(from: Int, to: Int): Boolean {
        if (!displayedAssetList[from].changeCheckStateEnabled || !displayedAssetList[to].changeCheckStateEnabled) return false
        val originalFrom =
            requireNotNull(changePosition.indexOfFirst { it == displayedAssetList[from].id })
        val originalTo =
            requireNotNull(changePosition.indexOfFirst { it == displayedAssetList[to].id })
        with(changePosition) {
            val item = removeAt(originalFrom)
            add(originalTo, item)
        }
        with(displayedAssetList) {
            val item = removeAt(from)
            add(to, item)
        }
        _assetPositions.value = from to to
        return true
    }
}
