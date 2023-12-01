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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.androidfoundation.format.formatFiatSuffix
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_ecosystem_impl.presentation.alldemeter.model.FarmDetailsState
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FarmDetailsViewModel @AssistedInject constructor(
    private val poolsInteractor: PoolsInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    @Assisted("id1") private val token1Id: String,
    @Assisted("id2") private val token2Id: String,
    @Assisted("id3") private val token3Id: String,
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedFarmDetailsViewModelFactory {
        fun create(
            @Assisted("id1") id1: String,
            @Assisted("id2") id2: String,
            @Assisted("id3") id3: String
        ): FarmDetailsViewModel
    }

    private val _state = MutableStateFlow(
        FarmDetailsState(
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            loading = true
        )
    )
    val state = _state.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = "Farming details",
                navIcon = R.drawable.ic_cross_24,
                visibility = true,
                searchEnabled = false,
            ),
        )

        viewModelScope.launch {
            val ids = StringTriple(token1Id, token2Id, token3Id)
            val poolData = poolsInteractor.getPoolsCacheOfCurAccount()
                .firstOrNull {
                    it.basic.baseToken.id == token1Id && it.basic.targetToken.id == token2Id
                }
            demeterFarmingInteractor.getFarmedBasicPools()
                .firstOrNull {
                    StringTriple(it.tokenBase.id, it.tokenTarget.id, it.tokenReward.id) == ids
                }?.let { basicFarmPool ->
                    val farmPool = demeterFarmingInteractor.getUsersFarmedPool(ids = ids)
                    var farmDetailsState = FarmDetailsState(
                        title = resourceManager.getString(
                            R.string.polkaswap_farm_title_template,
                            "%s-%s".format(basicFarmPool.tokenBase.symbol, basicFarmPool.tokenTarget.symbol)
                        ),
                        tvlSubtitle = basicFarmPool.tokenBase.printFiat(basicFarmPool.tvl.formatFiatSuffix()).orEmpty(),
                        apr = basicFarmPool.apr.let { "%s%%".format(numbersFormatter.format(it, 2)) },
                        token1Icon = basicFarmPool.tokenBase.iconUri(),
                        token2Icon = basicFarmPool.tokenTarget.iconUri(),
                        rewardsTokenIcon = basicFarmPool.tokenReward.iconUri(),
                        rewardsTokenSymbol = basicFarmPool.tokenReward.symbol,
                        fee = "${numbersFormatter.format(basicFarmPool.fee)}%",
                    )

                    farmPool?.let {
                        val percent = if (poolData?.user != null) {
                            PolkaswapFormulas.calculateShareOfPoolFromAmount(
                                farmPool.amount,
                                poolData.user.poolProvidersBalance,
                            ).toFloat()
                        } else {
                            0.0f
                        }

                        farmDetailsState = farmDetailsState.copy(
                            poolShareStacked = percent,
                            poolShareStackedText = "${numbersFormatter.format(percent.toDouble())}%",
                            userRewardsAmount = "${numbersFormatter.formatBigDecimal(farmPool.amountReward)} ${farmPool.tokenTarget.symbol}",
                        )
                    }

                    _state.value = farmDetailsState
                }
        }
    }

    override fun onMenuItem(action: Action) {
        this.onBackPressed()
    }
}
