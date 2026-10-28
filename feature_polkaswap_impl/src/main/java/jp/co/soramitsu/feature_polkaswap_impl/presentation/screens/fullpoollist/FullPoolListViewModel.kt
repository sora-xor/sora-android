/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.fullpoollist

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.common_wallet.domain.model.isFilterMatch
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapPoolsData
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@HiltViewModel
class FullPoolListViewModel @Inject constructor(
    private val poolsInteractor: PoolsInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val polkaswapRouter: PolkaswapRouter,
) : BaseViewModel() {

    private val allPools = mutableListOf<CommonUserPoolData>()
    private val filter = MutableStateFlow("")

    private val _state = MutableStateFlow(
        FullPoolListState(
            "",
            PoolsListState(emptyList()),
            true,
        )
    )
    internal val state = _state.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.Small(),
            basic = BasicToolbarState(
                title = "",
                navIcon = R.drawable.ic_cross_24,
                visibility = true,
                searchEnabled = true,
                searchValue = "",
                searchPlaceholder = jp.co.soramitsu.common.R.string.common_search_pools,
                actionLabel = jp.co.soramitsu.common.R.string.common_edit,
            ),
        )
        viewModelScope.launch {
            poolsInteractor.subscribePoolsCacheOfCurAccount()
                .catch { onError(it) }
                .collectLatest {
                    allPools.clear()
                    allPools.addAll(it)
                    calcState(filter.value)
                }
        }
        viewModelScope.launch {
            filter
                .drop(1)
                .debounce(400)
                .collectLatest {
                    calcState(it)
                }
        }
    }

    override fun onToolbarSearch(value: String) {
        _toolbarState.value = toolbarState.value?.copy(
            basic = toolbarState.value!!.basic.copy(
                searchValue = value
            )
        )

        filter.value = value
    }

    private fun calcState(filter: String) {
        val filtered =
            if (filter.isBlank()) allPools else allPools.filter { it.basic.isFilterMatch(filter) }
        val data = mapPoolsData(filtered, numbersFormatter)
        _state.value = _state.value.copy(
            loading = false,
            list = data.first,
            fiatSum = formatFiatAmount(
                data.second,
                filtered.fiatSymbol,
                numbersFormatter,
            ),
        )
    }

    override fun onAction() {
        polkaswapRouter.showFullPoolsSettings()
    }

    fun onPoolClick(poolId: StringPair) {
        polkaswapRouter.showPoolDetails(poolId)
    }
}
