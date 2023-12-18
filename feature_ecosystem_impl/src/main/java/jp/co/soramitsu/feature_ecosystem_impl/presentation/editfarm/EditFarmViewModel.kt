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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.demeter.domain.DemeterFarmingPool
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.model.EditFarmScreenState
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditFarmViewModel @AssistedInject constructor(
    private val poolsInteractor: PoolsInteractor,
    private val walletInteractor: WalletInteractor,
    private val assetsRouter: AssetsRouter,
    private val assetsInteractor: AssetsInteractor,
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
        ): EditFarmViewModel
    }

    private val _state = MutableStateFlow(
        EditFarmScreenState(
            StringTriple(token1Id, token2Id, token3Id),
            "0%",
            0f,
            "1",
            "2",
            "0.1 XOR",
            "0.2 XOR",
        )
    )
    val state = _state.asStateFlow()

    private var poolData: CommonUserPoolData? = null
    private var farmedPool: DemeterFarmingPool? = null
    private var currentStackedPercent: Double = 0.0
    private var stakingAmount: BigDecimal = BigDecimal.ZERO
    private var transferableXorBalance: BigDecimal = BigDecimal.ZERO
    private var stakingNetworkFee: BigDecimal = BigDecimal.ZERO
    private var unStakingNetworkFee: BigDecimal = BigDecimal.ZERO
    private var feeToken: Token? = null

    init {
        val ids = StringTriple(token1Id, token2Id, token3Id)

        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = resourceManager.getString(R.string.edit_farm),
                navIcon = R.drawable.ic_cross_24,
                visibility = true,
                searchEnabled = false,
            ),
        )

        viewModelScope.launch {
            poolData = poolsInteractor.getPoolOfCurAccount(StringPair(ids.first, ids.second))
            feeToken = walletInteractor.getFeeToken()
            transferableXorBalance = assetsInteractor.getXorBalance(feeToken?.precision ?: 18).transferable

            poolData?.let { poolData ->
                demeterFarmingInteractor.getFarmedBasicPool(ids)?.let { basicFarm ->
                    farmedPool = demeterFarmingInteractor.getFarmedPool(ids)

                    currentStackedPercent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                        farmedPool?.amount ?: BigDecimal.ZERO,
                        poolData.user.poolProvidersBalance,
                    )

                    recalcFuturePoolShareStacked()

                    stakingNetworkFee = demeterFarmingInteractor.calcDepositDemeterNetworkFee(ids)
                    unStakingNetworkFee = demeterFarmingInteractor.calcWithdrawDemeterNetworkFee(ids)

                    onSliderChange(currentStackedPercent / 100)

                    _state.value = _state.value.copy(
                        poolShareStaked = "${numbersFormatter.format(currentStackedPercent)}%",
                        fee = "${numbersFormatter.format(basicFarm.fee)}%",
                        networkFee = "$stakingNetworkFee ${feeToken?.symbol}",
                        isCardLoading = false,
                    )
                }
            }
        }
    }

    override fun onMenuItem(action: Action) {
        this.onBackPressed()
    }

    private fun recalcFuturePoolShareStacked() {
        poolData?.let { poolData ->
            val currentStakingAmount = farmedPool?.amount ?: BigDecimal.ZERO
            stakingAmount =
                (poolData.user.poolProvidersBalance * _state.value.sliderProgressState.toBigDecimal()) - currentStakingAmount
        }
    }

    fun onConfirm() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isButtonLoading = true
            )

            _state.value.let {
                if (stakingAmount > BigDecimal.ZERO) {
                    val txHash = demeterFarmingInteractor.depositDemeterFarming(
                        it.farmIds,
                        stakingAmount,
                        stakingNetworkFee
                    )

                    if (txHash.isNotEmpty()) {
                        assetsRouter.showTxDetails(txHash, true)
                    } else {
                        _state.value = _state.value.copy(
                            isButtonLoading = false
                        )
                        onError(R.string.common_error_general_message)
                    }
                }
            }
        }
    }

    fun onSliderChange(value: Double) {
        val percentage = value * 100

        val networkFee = if (percentage > currentStackedPercent) {
            stakingNetworkFee
        } else {
            unStakingNetworkFee
        }

        val isChanged = abs(percentage - currentStackedPercent) > 0.001

        _state.value = state.value.copy(
            sliderProgressState = value.toFloat(),
            networkFee = "$networkFee ${feeToken?.symbol}",
            isButtonActive = isChanged && transferableXorBalance >= networkFee,
            percentageText = "${numbersFormatter.format(percentage)}%",
            poolShareStakedWillBe = "${numbersFormatter.format(percentage)}%"
        )

        recalcFuturePoolShareStacked()
    }
}
