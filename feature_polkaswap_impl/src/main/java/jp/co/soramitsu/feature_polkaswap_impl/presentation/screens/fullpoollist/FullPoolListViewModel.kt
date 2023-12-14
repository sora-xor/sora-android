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
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.FullPoolListState
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
            PoolsListState(emptyList())
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
                .debounce(400)
                .collectLatest {
                    calcState(it)
                }
        }
    }

    override fun onToolbarSearch(value: String) {
        filter.value = value
    }

    private fun calcState(filter: String) {
        val filtered =
            if (filter.isBlank()) allPools else allPools.filter { it.basic.isFilterMatch(filter) }
        val data = mapPoolsData(filtered, numbersFormatter)
        _state.value = _state.value.copy(
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
