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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.explore

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_ecosystem_impl.presentation.ExploreRoutes
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val polkaswapRouter: PolkaswapRouter,
    private val assetsRouter: AssetsRouter,
) : BaseViewModel() {

    private val _searchState = MutableStateFlow("")
    val searchState = _searchState.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.Small(),
            basic = BasicToolbarState(
                title = "",
                navIcon = jp.co.soramitsu.ui_core.R.drawable.ic_arrow_left,
                visibility = false,
                searchEnabled = false,
            ),
        )
    }

    override fun startScreen(): String = ExploreRoutes.START

    override fun onMenuItem(action: Action) {
        when (action) {
            is Action.Plus -> {
                onPoolPlus()
            }
            else -> {}
        }
    }

    override fun onCurrentDestinationChanged(curDest: String) {
        _toolbarState.value?.let { state ->
            when (curDest) {
                ExploreRoutes.ALL_POOLS -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            visibility = true,
                            title = R.string.discovery_polkaswap_pools,
                            searchEnabled = true,
                            searchValue = _searchState.value,
                            menu = listOf(Action.Plus()),
                        )
                    )
                }
                ExploreRoutes.ALL_CURRENCIES -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            visibility = true,
                            title = R.string.common_currencies,
                            searchEnabled = true,
                            searchValue = _searchState.value,
                            menu = emptyList(),
                        )
                    )
                }
                else -> {
                    _toolbarState.value = state.copy(
                        basic = state.basic.copy(
                            visibility = false,
                            searchEnabled = false,
                        )
                    )
                }
            }
        }
    }

    override fun onToolbarSearch(value: String) {
        _searchState.value = value
    }

    fun onTokenClicked(tokenId: String) {
        assetsRouter.showAssetDetails(tokenId)
    }

    fun onPoolClicked(pool: StringPair) {
        polkaswapRouter.showPoolDetails(pool)
    }

    fun onPoolPlus() {
        polkaswapRouter.showAddLiquidity(SubstrateOptionsProvider.feeAssetId)
    }
}
