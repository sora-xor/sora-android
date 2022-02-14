/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.util.mapAssetToAssetModel
import kotlinx.coroutines.launch
import java.util.Locale

class AssetListViewModel(
    private val interactor: WalletInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val router: WalletRouter,
    private val assetListMode: AssetListMode,
) : BaseViewModel() {

    private val _displayingAssetsLiveData = MutableLiveData<List<AssetListItemModel>>()
    val displayingAssetsLiveData: LiveData<List<AssetListItemModel>> = _displayingAssetsLiveData

    private val _title = MutableLiveData<Int>()
    val title: LiveData<Int> = _title

    private val assetsList: MutableList<AssetListItemModel> = mutableListOf()
    private var curFilter: String = ""
    private val balanceStyle = AssetBalanceStyle(
        R.style.TextAppearance_Soramitsu_Neu_Bold_15,
        R.style.TextAppearance_Soramitsu_Neu_Bold_11
    )

    init {
        viewModelScope.launch {
            val list = interactor.getVisibleAssets().map {
                it.mapAssetToAssetModel(numbersFormatter, balanceStyle)
            }.sortedBy { it.sortOrder }
            assetsList.clear()
            assetsList.addAll(list)
            filterAssetsList()
        }
        _title.value =
            if (assetListMode == AssetListMode.RECEIVE) R.string.select_asset_receive else R.string.select_asset_send
    }

    private fun filterAssetsList() {
        val filter = curFilter.lowercase(Locale.getDefault())
        _displayingAssetsLiveData.value = if (curFilter.isBlank()) assetsList
        else mutableListOf<AssetListItemModel>().apply {
            addAll(
                assetsList.filter {
                    it.title.lowercase(Locale.getDefault())
                        .contains(filter) || it.tokenName.lowercase(Locale.getDefault())
                        .contains(filter)
                }
            )
        }
    }

    fun itemClicked(asset: AssetListItemModel) {
        when (assetListMode) {
            AssetListMode.RECEIVE -> {
                router.showReceive(
                    ReceiveAssetModel(
                        asset.assetId,
                        asset.tokenName,
                        asset.title,
                        asset.icon
                    )
                )
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
