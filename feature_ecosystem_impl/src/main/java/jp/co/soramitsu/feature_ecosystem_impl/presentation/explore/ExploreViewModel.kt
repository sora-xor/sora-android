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

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.format.formatFiatSuffix
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.isMatchFilter
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.domain.printFiatChange
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.common_wallet.domain.model.isFilterMatch
import jp.co.soramitsu.common_wallet.presentation.compose.BasicFarmListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.BasicPoolListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.demeter.domain.isFilterMatch
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_ecosystem_impl.R
import jp.co.soramitsu.feature_ecosystem_impl.presentation.explore.model.ExploreScreenState
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ExploreViewModel @Inject constructor(
    resourceManager: ResourceManager,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    private val poolsInteractor: PoolsInteractor,
    private val assetsInteractor: AssetsInteractor,
    private val polkaswapRouter: PolkaswapRouter,
    private val assetsRouter: AssetsRouter,
    private val numbersFormatter: NumbersFormatter,
) : BaseViewModel() {

    private val _state = MutableStateFlow(
        ExploreScreenState()
    )
    val state = _state.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.Small(),
            basic = BasicToolbarState(
                title = resourceManager.getString(R.string.common_explore),
                navIcon = null,
                searchEnabled = true,
                searchPlaceholder = R.string.search_token_placeholder
            ),
        )
    }

    fun onTokenClicked(tokenId: String) {
        assetsRouter.showAssetDetails(tokenId)
    }

    fun onPoolClicked(pool: StringPair) {
        polkaswapRouter.showPoolDetails(pool)
    }

    fun onFarmClicked(ids: StringTriple) {
        polkaswapRouter.showFarmDetails(ids)
    }

    fun onPoolPlus() {
        polkaswapRouter.showAddLiquidity(SubstrateOptionsProvider.feeAssetId)
    }

    override fun onToolbarSearch(value: String) {
        super.onToolbarSearch(value)
        _toolbarState.value = toolbarState.value?.copy(
            basic = toolbarState.value!!.basic.copy(
                searchValue = value
            )
        )

        if (value.isEmpty()) {
            _state.value = ExploreScreenState()
        } else {
            _state.value = ExploreScreenState(isSearching = true, isLoading = true)
            viewModelScope.launch {
                val assets = loadAssets(value)
                val pools = loadPools(value)
                val farms = loadFarms(value)
                _state.value = state.value.copy(
                    isLoading = false,
                    pools = pools,
                    assets = assets,
                    farms = farms,
                )
            }
        }
    }

    private suspend fun loadAssets(search: String): List<AssetItemCardState> {
        return assetsInteractor.getWhitelistAssets()
            .filter { it.token.isMatchFilter(search) }
            .map {
                AssetItemCardState(
                    tokenIcon = it.token.iconUri(),
                    tokenId = it.token.id,
                    tokenName = it.token.name,
                    tokenSymbol = it.token.symbol,
                    assetAmount = it.token.printBalance(
                        it.balance.transferable,
                        numbersFormatter,
                        it.token.precision
                    ),
                    assetFiatAmount = it.printFiat(numbersFormatter),
                    fiatChange = it.token.printFiatChange(numbersFormatter),
                )
            }
    }

    private suspend fun loadPools(search: String): List<BasicPoolListItemState> {
        return poolsInteractor.getBasicPools().filter {
            it.isFilterMatch(search)
        }.mapIndexed { index, pool ->
            val tvl = pool.baseToken.fiatPrice?.times(2)?.toBigDecimal()
                ?.multiply(pool.baseReserves)

            BasicPoolListItemState(
                ids = StringPair(pool.baseToken.id, pool.targetToken.id),
                number = "${index + 1}",
                token1Icon = pool.baseToken.iconUri(),
                token2Icon = pool.targetToken.iconUri(),
                text1 = "${pool.baseToken.symbol}-${pool.targetToken.symbol}",
                text2 = pool.baseToken.printFiat(tvl?.formatFiatSuffix()).orEmpty(),
                text3 = pool.sbapy?.let {
                    "%s%%".format(numbersFormatter.format(it, 2))
                }.orEmpty(),
            )
        }
    }

    private suspend fun loadFarms(search: String): List<BasicFarmListItemState> {
        return demeterFarmingInteractor.getFarmedBasicPools().filter {
            it.isFilterMatch(search)
        }.mapIndexed { index, farm ->
            BasicFarmListItemState(
                StringTriple(farm.tokenBase.id, farm.tokenTarget.id, farm.tokenReward.id),
                number = (index + 1).toString(),
                token1Icon = farm.tokenBase.iconUri(),
                token2Icon = farm.tokenTarget.iconUri(),
                rewardTokenIcon = farm.tokenReward.iconUri(),
                rewardTokenSymbol = farm.tokenReward.symbol,
                text1 = "%s-%s".format(farm.tokenBase.symbol, farm.tokenTarget.symbol),
                text2 = farm.tokenBase.printFiat(farm.tvl.formatFiatSuffix()).orEmpty(),
                text3 = farm.apr.let {
                    "%s%%".format(numbersFormatter.format(it, 2))
                },
            )
        }
    }
}
