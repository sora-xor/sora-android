package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.adapter.AssetListItemModel
import java.util.Locale

class AssetListViewModel(
    private val interactor: WalletInteractor,
    private val ethInteractor: EthereumInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val assetListMode: AssetListMode,
) : BaseViewModel() {

    private val _displayingAssetsLiveData = MutableLiveData<List<AssetListItemModel>>()
    val displayingAssetsLiveData: LiveData<List<AssetListItemModel>> = _displayingAssetsLiveData

    private val _title = MutableLiveData<Int>()
    val title: LiveData<Int> = _title

    private val assetAddresses: MutableMap<String, String> = mutableMapOf()
    private val assetsList: MutableList<AssetListItemModel> = mutableListOf()
    private var curFilter: String = ""

    init {
        with(disposables) {
            add(
                interactor.getAssets()
                    .map { mapAssetToAssetModel(it) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            assetsList.clear()
                            assetsList.addAll(it)
                            filterAssetsList()
                        },
                        {
                            logException(it)
                        }
                    )
            )
        }
        _title.value = if (assetListMode == AssetListMode.RECEIVE) R.string.common_receive else R.string.common_choose_asset
    }

    private fun filterAssetsList() {
        val filter = curFilter.toLowerCase(Locale.getDefault())
        _displayingAssetsLiveData.value = if (curFilter.isBlank()) assetsList
        else mutableListOf<AssetListItemModel>().apply {
            addAll(
                assetsList.filter {
                    it.title.toLowerCase(Locale.getDefault()).contains(filter) || it.tokenName.toLowerCase(Locale.getDefault()).contains(filter)
                }
            )
        }
    }

    private fun mapAssetToAssetModel(assets: List<Asset>): List<AssetListItemModel> {
        return assets.map {
            AssetListItemModel(
                it.iconShadow, it.assetName,
                numbersFormatter.formatBigDecimal(it.balance, it.roundingPrecision),
                it.symbol, it.position, it.id
            )
        }
    }

    fun itemClicked(asset: AssetListItemModel) {
        when (assetListMode) {
            AssetListMode.RECEIVE -> {
                router.showReceive(ReceiveAssetModel(asset.assetId, asset.tokenName, asset.title, asset.icon))
            }
            AssetListMode.SEND -> {
                router.showContacts(asset.assetId)
            }
        }
    }

    fun backClicked() {
        router.popBackStackFragment()
    }

    fun searchAssets(filter: String) {
        curFilter = filter
        filterAssetsList()
    }
}
