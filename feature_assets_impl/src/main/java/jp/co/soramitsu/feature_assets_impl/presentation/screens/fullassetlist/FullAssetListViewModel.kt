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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.fiatSum
import jp.co.soramitsu.common.domain.fiatSymbol
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.isMatchFilter
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.states.FullAssetListState
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _state = MutableStateFlow(FullAssetListState(false, "", emptyList(), emptyList()))
    val state = _state.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            basic = BasicToolbarState(
                title = "",
                navIcon = R.drawable.ic_cross_24,
                actionLabel = jp.co.soramitsu.common.R.string.common_edit,
                searchValue = "",
                searchEnabled = true,
            ),
            type = SoramitsuToolbarType.Small(),
        )
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

    override fun onToolbarSearch(value: String) {
        filter.value = value
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
            _state.value = _state.value.copy(
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
            val topFilter = visAssets.filter { it.token.isMatchFilter(filter) }
            val bottomFilter = invisibleAssets.filter { it.token.isMatchFilter(filter) }
            val topSum = topFilter.fiatSum()
            val bottomSum = bottomFilter.fiatSum()
            _state.value = _state.value.copy(
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

    override fun onAction() {
        assetsRouter.showFullAssetsSettings()
    }

    fun onAssetClick(tokenId: String) {
        assetsRouter.showAssetDetails(tokenId)
    }
}
