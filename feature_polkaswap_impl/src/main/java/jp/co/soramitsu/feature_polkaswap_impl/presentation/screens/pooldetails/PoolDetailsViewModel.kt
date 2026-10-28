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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.pooldetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.androidfoundation.format.formatFiatSuffix
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.common.util.ext.lazyAsync
import jp.co.soramitsu.common_wallet.presentation.compose.BasicFarmListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.BasicUserFarmListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.PoolDetailsState
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PoolDetailsViewModel @AssistedInject constructor(
    private val poolsInteractor: PoolsInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val polkaswapRouter: PolkaswapRouter,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    @Assisted("id1") private val token1Id: String,
    @Assisted("id2") private val token2Id: String,
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedPoolDetailsViewModelFactory {
        fun create(@Assisted("id1") id1: String, @Assisted("id2") id2: String): PoolDetailsViewModel
    }

    internal var detailsState by mutableStateOf(
        PoolDetailsState(
            DEFAULT_ICON_URI, DEFAULT_ICON_URI, DEFAULT_ICON_URI,
            "", "", "", "", "", "", "",
            true, true, "", emptyList(), emptyList(), false,
        )
    )

    private val rewardTokenAsync by viewModelScope.lazyAsync { poolsInteractor.getRewardToken() }
    private suspend fun rewardToken() = rewardTokenAsync.await()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = R.string.pool_details,
                navIcon = R.drawable.ic_cross_24,
            ),
        )

        viewModelScope.launch {
            poolsInteractor.subscribePoolCacheOfCurAccount(token1Id, token2Id)
                .catch { onError(it) }
                .collectLatest { data ->
                    if (data == null) {
                        detailsState = detailsState.copy(
                            addEnabled = false,
                            removeEnabled = false,
                        )
                    } else {
                        val userData = data.user
                        val farms = demeterFarmingInteractor.getFarmedBasicPools()
                            .filter {
                                it.tokenBase.id == data.basic.baseToken.id && it.tokenTarget.id == data.basic.targetToken.id
                            }
                            .mapIndexed { index, farm ->
                                BasicFarmListItemState(
                                    Triple(farm.tokenBase.id, farm.tokenTarget.id, farm.tokenReward.id),
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

                        var pools100 = false

                        val pools = demeterFarmingInteractor.getFarmedPools()?.filter { pool ->
                            pool.tokenBase.id == token1Id && pool.tokenTarget.id == token2Id
                        }?.map { farming ->
                            val percent = if (userData != null) {
                                PolkaswapFormulas.calculateShareOfPoolFromAmount(
                                    farming.amount,
                                    userData.poolProvidersBalance,
                                ).toFloat()
                            } else {
                                0.0f
                            }

                            pools100 = percent == 100.0f

                            BasicUserFarmListItemState(
                                ids = StringTriple(
                                    farming.tokenBase.id,
                                    farming.tokenTarget.id,
                                    farming.tokenReward.id
                                ),
                                token1Icon = farming.tokenBase.iconUri(),
                                token2Icon = farming.tokenTarget.iconUri(),
                                rewardTokenIcon = farming.tokenReward.iconUri(),
                                text1 = "${farming.tokenBase.symbol}-${farming.tokenTarget.symbol}",
                                text2 = "${resourceManager.getString(R.string.pool_details_reward)}: ${
                                    numbersFormatter.formatBigDecimal(
                                        farming.amountReward
                                    )
                                } ${farming.tokenReward.symbol}",
                                text3 = "${numbersFormatter.format(farming.apr)}% ${resourceManager.getString(R.string.polkaswap_apr)}",
                                text4 = "${numbersFormatter.format(percent.toDouble())}%",
                            )
                        }

                        detailsState = PoolDetailsState(
                            token1Icon = data.basic.baseToken.iconUri(),
                            token2Icon = data.basic.targetToken.iconUri(),
                            rewardsUri = rewardToken().iconUri(),
                            rewardsTokenSymbol = rewardToken().symbol,
                            symbol1 = data.basic.baseToken.symbol,
                            symbol2 = data.basic.targetToken.symbol,
                            apy = data.basic.sbapy?.let { apy ->
                                "%s%%".format(
                                    numbersFormatter.format(
                                        apy,
                                        2,
                                    )
                                )
                            } ?: "",
                            pooled1 = userData?.basePooled?.let {
                                data.basic.baseToken.printBalance(
                                    it,
                                    numbersFormatter,
                                    AssetHolder.ROUNDING,
                                )
                            },
                            pooled2 = userData?.targetPooled?.let {
                                data.basic.targetToken.printBalance(
                                    it,
                                    numbersFormatter,
                                    AssetHolder.ROUNDING,
                                )
                            },
                            tvl = "${
                                data.basic.baseToken.printFiat(data.basic.tvl?.formatFiatSuffix()).orEmpty()
                            } ${resourceManager.getString(R.string.total_value_locked)}",
                            addEnabled = true,
                            removeEnabled = (userData != null) && (!pools100),
                            userPoolSharePercent = userData?.poolShare?.let {
                                "%s%%".format(
                                    numbersFormatter.format(it, 2, true)
                                )
                            },
                            availableDemeterFarms = farms,
                            demeterPools = pools,
                            demeter100Percent = pools100,
                        )
                    }
                }
        }
    }

    override fun onMenuItem(action: Action) {
        this.onBackPressed()
    }

    fun onSupply() {
        polkaswapRouter.showAddLiquidity(token1Id, token2Id)
    }

    fun onRemove() {
        polkaswapRouter.showRemoveLiquidity(token1Id to token2Id)
    }

    fun onFarm(ids: Triple<String, String, String>) {
        polkaswapRouter.showFarmDetails(ids)
    }
}
