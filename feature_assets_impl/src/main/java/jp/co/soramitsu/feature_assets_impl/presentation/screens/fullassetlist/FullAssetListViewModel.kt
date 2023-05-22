/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.fiatSum
import jp.co.soramitsu.common.domain.fiatSymbol
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.FullAssetListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@HiltViewModel
class FullAssetListViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val numbersFormatter: NumbersFormatter,
) : BaseViewModel() {

    private val thresholdBalance: Double = 1.0
    private val invisibleAssets = mutableListOf<Asset>()
    private val allAssets = mutableListOf<Asset>()
    private val visAssets = mutableListOf<Asset>()
    private val filter = MutableStateFlow("")

    var state by mutableStateOf(FullAssetListState(false, "", emptyList(), emptyList()))
        private set

    init {
        viewModelScope.launch {
            allAssets.addAll(assetsInteractor.getWhitelistAssets())
            launch {
                assetsInteractor.subscribeAssetsVisibleOfCurAccount()
                    .catch { onError(it) }
                    .collectLatest { assets ->
                        visAssets.clear()
                        visAssets.addAll(assets)
                        calcState(filter.value)
                    }
            }
            launch {
                filter.debounce(400)
                    .collectLatest {
                        calcState(it)
                    }
            }
        }
    }

    private fun calcState(filter: String) {
        val idMap = visAssets.map { t -> t.token.id }
        val excluded = allAssets.filter { it.token.id !in idMap }
        invisibleAssets.clear()
        invisibleAssets.addAll(excluded)
        if (filter.isBlank()) {
            val group = visAssets.groupBy {
                (it.fiat ?: 0.0) >= thresholdBalance
            }
            state = state.copy(
                searchMode = false,
                topList = mapAssetsToCardState(
                    group[true] ?: emptyList(),
                    numbersFormatter
                ),
                bottomList = mapAssetsToCardState(
                    group[false] ?: emptyList(),
                    numbersFormatter
                ),
                fiatSum = formatFiatAmount(
                    visAssets.fiatSum(),
                    visAssets.fiatSymbol(),
                    numbersFormatter
                ),
            )
        } else {
            val topFilter = visAssets.filter { isFilterMatch(it, filter) }
            val bottomFilter = invisibleAssets.filter { isFilterMatch(it, filter) }
            val topSum = topFilter.fiatSum()
            val bottomSum = bottomFilter.fiatSum()
            state = state.copy(
                searchMode = true,
                topList = mapAssetsToCardState(topFilter, numbersFormatter),
                bottomList = mapAssetsToCardState(bottomFilter, numbersFormatter),
                fiatSum = formatFiatAmount(
                    topSum + bottomSum,
                    topFilter.fiatSymbol(),
                    numbersFormatter
                ),
            )
        }
    }

    fun searchAssets(search: String) {
        filter.value = search
    }

    override fun onAction() {
        assetsRouter.showFullAssetsSettings()
    }

    fun onAssetClick(tokenId: String) {
        assetsRouter.showAssetDetails(tokenId)
    }

    private fun isFilterMatch(asset: Asset, filter: String): Boolean {
        return asset.token.name.lowercase().contains(filter.lowercase()) ||
            asset.token.symbol.lowercase().contains(filter.lowercase()) ||
            asset.token.id.lowercase().contains(filter.lowercase())
    }
}
