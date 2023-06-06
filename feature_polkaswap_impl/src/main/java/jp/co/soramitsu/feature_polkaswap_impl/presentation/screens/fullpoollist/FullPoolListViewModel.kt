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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.PoolData
import jp.co.soramitsu.common_wallet.domain.model.fiatSymbol
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapPoolsData
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.FullPoolListState
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val allPools = mutableListOf<PoolData>()
    private val filter = MutableStateFlow("")

    internal var state by mutableStateOf(
        FullPoolListState(
            "",
            PoolsListState(emptyList())
        )
    )
        private set

    init {
        viewModelScope.launch {
            poolsInteractor.subscribePoolsCache()
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

    private fun calcState(filter: String) {
        val filtered =
            if (filter.isBlank()) allPools else allPools.filter { isFilterMatch(it, filter) }
        val data = mapPoolsData(filtered, numbersFormatter)
        state = state.copy(
            list = data.first,
            fiatSum = formatFiatAmount(
                data.second,
                filtered.fiatSymbol,
                numbersFormatter,
            ),
        )
    }

    fun searchAssets(search: String) {
        filter.value = search
    }

    override fun onAction() {
        polkaswapRouter.showFullPoolsSettings()
    }

    fun onPoolClick(poolId: StringPair) {
        polkaswapRouter.showPoolDetails(poolId)
    }

    private fun isFilterMatch(poolData: PoolData, filter: String): Boolean {
        val t1 = poolData.token.name.lowercase().contains(filter.lowercase()) ||
            poolData.token.symbol.lowercase().contains(filter.lowercase()) ||
            poolData.token.id.lowercase().contains(filter.lowercase())
        val t2 = poolData.baseToken.name.lowercase().contains(filter.lowercase()) ||
            poolData.baseToken.symbol.lowercase().contains(filter.lowercase()) ||
            poolData.baseToken.id.lowercase().contains(filter.lowercase())
        return t1 || t2
    }
}
