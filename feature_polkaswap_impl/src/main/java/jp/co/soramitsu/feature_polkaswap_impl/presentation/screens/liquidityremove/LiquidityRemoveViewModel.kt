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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityremove

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.lazyAsync
import jp.co.soramitsu.common.util.ext.nullZero
import jp.co.soramitsu.common.util.ext.orZero
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.common_wallet.domain.model.UserPoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.AmountFormat
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_impl.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityRemoveConfirmState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityRemoveEstimatedState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityRemovePricesState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityRemoveState
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class LiquidityRemoveViewModel @AssistedInject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val poolsInteractor: PoolsInteractor,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    @Assisted("id1") private val token1Id: String,
    @Assisted("id2") private val token2Id: String,
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedLiquidityRemoveViewModelFactory {
        fun create(
            @Assisted("id1") id1: String,
            @Assisted("id2") id2: String
        ): LiquidityRemoveViewModel
    }

    private companion object {
        const val ASSET_PRECISION = 8
    }

    private var amount1 = BigDecimal.ZERO
    private var amount2 = BigDecimal.ZERO
    private var networkFee: BigDecimal? = null
    private var balanceFee = BigDecimal.ZERO
    private val assetList = mutableListOf<Asset>()
    private var poolInFarming = false

    private val feeTokenAsync by viewModelScope.lazyAsync { walletInteractor.getFeeToken() }
    private suspend fun feeToken() = feeTokenAsync.await()

    private var poolDataUsable: UserPoolData? = null
    private var poolDataReal: UserPoolData? = null
    private val amount1Flow = MutableStateFlow(BigDecimal.ZERO)
    private val amount2Flow = MutableStateFlow(BigDecimal.ZERO)
    private var percent: Double = 0.0

    var removeState by mutableStateOf(
        LiquidityRemoveState(
            btnState = ButtonState(
                text = resourceManager.getString(R.string.common_enter_amount),
                enabled = false,
                loading = false,
            ),
            slippage = 0.5,
            assetState1 = null,
            assetState2 = null,
            hintVisible = false,
            estimated = LiquidityRemoveEstimatedState(
                token1 = "",
                token1Value = "",
                token2 = "",
                token2Value = "",
                shareOfPool = "",
            ),
            prices = LiquidityRemovePricesState(
                pair1 = "",
                pair1Value = "",
                pair2 = "",
                pair2Value = "",
                apy = null,
                fee = "",
            ),
            confirm = LiquidityRemoveConfirmState(
                text = "",
                confirmResult = null,
                btnState = ButtonState(
                    text = resourceManager.getString(R.string.common_confirm),
                    enabled = true,
                    loading = false,
                ),
            ),
            shouldTransactionReminderInsufficientWarningBeShown = false,
            transactionFeeToken = "",
            poolInFarming = poolInFarming,
        )
    )

    override fun startScreen(): String = LiquidityRemoveRoutes.start

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = R.string.remove_liquidity_title,
                navIcon = R.drawable.ic_settings_info,
                menu = listOf(Action.Close()),
            ),
        )
        viewModelScope.launch {
            assetsInteractor.subscribeAssetsActiveOfCurAccount()
                .catch { onError(it) }
                .distinctUntilChanged()
                .debounce(500)
                .collectLatest { assets ->
                    assetList.clear()
                    assetList.addAll(assets)

                    val asset1 = assets.first { it.token.id == token1Id }
                    val asset2 = assets.first { it.token.id == token2Id }
                    if (networkFee == null) {
                        networkFee = poolsInteractor.fetchRemoveLiquidityNetworkFee(
                            asset1.token,
                            asset2.token,
                        )
                    }
                    if (removeState.assetState1 == null) {
                        removeState = removeState.copy(
                            assetState1 = AssetAmountInputState(
                                token = asset1.token,
                                balance = getAssetBalanceText(asset1),
                                amount = BigDecimal.ZERO,
                                initialAmount = null,
                                amountFiat = "",
                                enabled = true,
                            ),
                        )
                    }
                    if (removeState.assetState2 == null) {
                        removeState = removeState.copy(
                            assetState2 = AssetAmountInputState(
                                token = asset2.token,
                                balance = getAssetBalanceText(asset2),
                                amount = BigDecimal.ZERO,
                                initialAmount = null,
                                amountFiat = "",
                                enabled = true,
                            ),
                        )
                    }
                    balanceFee =
                        assets.first { it.token.id == SubstrateOptionsProvider.feeAssetId }.balance.transferable

                    removeState.assetState1?.let { state ->
                        assets.find { it.token.id == state.token.id }?.let { asset ->
                            removeState = removeState.copy(
                                assetState1 = state.copy(
                                    balance = getAssetBalanceText(asset)
                                )
                            )
                        }
                    }
                    removeState.assetState2?.let { state ->
                        assets.find { it.token.id == state.token.id }?.let { asset ->
                            removeState = removeState.copy(
                                assetState2 = state.copy(
                                    balance = getAssetBalanceText(asset)
                                )
                            )
                        }
                    }
                    reCalcDetails()
                }
        }
        viewModelScope.launch {
            poolsInteractor.subscribePoolCache(token1Id, token2Id)
                .catch { onError(it) }
                .distinctUntilChanged()
                .debounce(500)
                .map { poolDataLocal ->
                    poolDataReal = poolDataLocal
                    poolInFarming = false
                    if (poolDataLocal != null) {
                        val maxPercent = demeterFarmingInteractor.getFarmedPools()?.filter { pool ->
                            pool.tokenBase.id == token1Id && pool.tokenTarget.id == token2Id
                        }?.maxOfOrNull {
                            PolkaswapFormulas.calculateShareOfPoolFromAmount(
                                it.amount,
                                poolDataLocal.poolProvidersBalance,
                            )
                        }
                        if (maxPercent != null && !maxPercent.isNaN()) {
                            poolInFarming = true
                            val usablePercent = 100 - maxPercent
                            poolDataLocal.copy(
                                basePooled = PolkaswapFormulas.calculateAmountByPercentage(
                                    poolDataLocal.basePooled,
                                    usablePercent,
                                    poolDataLocal.basic.baseToken.precision,
                                ),
                                targetPooled = PolkaswapFormulas.calculateAmountByPercentage(
                                    poolDataLocal.targetPooled,
                                    usablePercent,
                                    poolDataLocal.basic.baseToken.precision,
                                ),
                                poolProvidersBalance = PolkaswapFormulas.calculateAmountByPercentage(
                                    poolDataLocal.poolProvidersBalance,
                                    usablePercent,
                                    poolDataLocal.basic.baseToken.precision,
                                ),
                            )
                        } else {
                            poolDataLocal
                        }
                    } else {
                        null
                    }
                }
                .collectLatest { poolDataLocal ->
                    poolDataUsable = poolDataLocal
                    amount1 =
                        if (poolDataLocal != null) PolkaswapFormulas.calculateAmountByPercentage(
                            poolDataLocal.basePooled,
                            percent,
                            poolDataLocal.basic.baseToken.precision,
                        ) else BigDecimal.ZERO
                    amount2 =
                        if (poolDataLocal != null) PolkaswapFormulas.calculateAmountByPercentage(
                            poolDataLocal.targetPooled,
                            percent,
                            poolDataLocal.basic.targetToken.precision,
                        ) else BigDecimal.ZERO
                    reCalcDetails()
                }
        }
        viewModelScope.launch {
            amount1Flow
                .debounce(ViewHelper.debounce)
                .onEach { amount ->
                    poolDataUsable?.let {
                        amount1 = if (amount <= it.basePooled) amount else it.basePooled
                        amount2 = PolkaswapFormulas.calculateOneAmountFromAnother(
                            amount1,
                            it.basePooled,
                            it.targetPooled,
                        )
                        percent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                            amount1,
                            it.basePooled,
                        )
                        reCalcDetails()
                    }
                }.filter {
                    removeState.assetState1?.token?.id == SubstrateOptionsProvider.feeAssetId
                }.onEach {
                    updateTransactionReminderWarningVisibility()
                }.collect()
        }
        viewModelScope.launch {
            amount2Flow
                .debounce(ViewHelper.debounce)
                .collectLatest { amount ->
                    poolDataUsable?.let {
                        amount2 = if (amount <= it.targetPooled) amount else it.targetPooled
                        amount1 =
                            PolkaswapFormulas.calculateOneAmountFromAnother(
                                amount2,
                                it.targetPooled,
                                it.basePooled
                            )
                        percent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                            amount1,
                            it.basePooled,
                        )
                        reCalcDetails()
                    }
                }
        }
    }

    override fun onCurrentDestinationChanged(curDest: String) {
        _toolbarState.value?.let { state ->
            _toolbarState.value = state.copy(
                basic = state.basic.copy(
                    title = when (curDest) {
                        LiquidityRemoveRoutes.start -> R.string.remove_liquidity_title
                        LiquidityRemoveRoutes.slippage -> R.string.slippage
                        LiquidityRemoveRoutes.confirm -> R.string.remove_pool_confirmation_title
                        else -> R.string.remove_liquidity_title
                    },
                    navIcon = when (curDest) {
                        LiquidityRemoveRoutes.start -> R.drawable.ic_settings_info
                        else -> R.drawable.ic_arrow_left
                    }
                ),
            )
        }
    }

    override fun onNavIcon() {
        if (currentDestination == LiquidityRemoveRoutes.start) {
            removeState = removeState.copy(
                hintVisible = removeState.hintVisible.not()
            )
        } else {
            super.onNavIcon()
        }
    }

    override fun onMenuItem(action: Action) {
        this.onBackPressed()
    }

    fun dismissHint() {
        removeState = removeState.copy(
            hintVisible = removeState.hintVisible.not()
        )
    }

    fun onPercentClick(p: Int) {
        viewModelScope.launch {
            poolDataUsable?.let { poolData ->
                removeState.assetState1?.let { asset1 ->
                    removeState.assetState2?.let { asset2 ->
                        percent = p.toDouble()
                        if (percent == 100.0) {
                            amount1 = poolData.basePooled
                            amount2 = poolData.targetPooled
                        } else {
                            amount1 = PolkaswapFormulas.calculateAmountByPercentage(
                                poolData.basePooled,
                                percent,
                                asset1.token.precision,
                            )
                            amount2 = PolkaswapFormulas.calculateAmountByPercentage(
                                poolData.targetPooled,
                                percent,
                                asset2.token.precision,
                            )
                        }
                        reCalcDetails()
                    }
                }
            }
        }
    }

    fun onSlippageClick() {
        removeState = removeState.copy(
            assetState1 = removeState.assetState1?.copy(
                initialAmount = removeState.assetState1?.amount?.nullZero(),
            ),
            assetState2 = removeState.assetState2?.copy(
                initialAmount = removeState.assetState2?.amount?.nullZero(),
            ),
        )
    }

    fun slippageChanged(slippageTolerance: Double) {
        removeState = removeState.copy(
            slippage = slippageTolerance
        )
    }

    fun onAmount1Change(value: BigDecimal) {
        removeState = removeState.copy(
            assetState1 = removeState.assetState1?.copy(
                amount = value,
                amountFiat = removeState.assetState1?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty()
            )
        )
        amount1Flow.value = value
    }

    fun onAmount2Change(value: BigDecimal) {
        removeState = removeState.copy(
            assetState2 = removeState.assetState2?.copy(
                amount = value,
                amountFiat = removeState.assetState2?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty()
            )
        )
        amount2Flow.value = value
    }

    fun onReviewClick() {
        removeState.assetState1?.token?.let {
            removeState.assetState2?.token?.let {
                removeState = removeState.copy(
                    confirm = removeState.confirm.copy(
                        text = resourceManager.getString(R.string.remove_pool_confirmation_description)
                            .format(removeState.slippage),
                        btnState = removeState.btnState.copy(
                            text = resourceManager.getString(R.string.common_confirm),
                            enabled = true,
                            loading = false,
                        ),
                    ),
                )
            }
        }
    }

    fun onConfirmClick() {
        viewModelScope.launch {
            removeState.assetState1?.let { fromToken ->
                removeState.assetState2?.let { toToken ->
                    poolDataUsable?.let { poolData ->
                        val firstAmountMin =
                            PolkaswapFormulas.calculateMinAmount(
                                fromToken.amount,
                                removeState.slippage
                            )
                        val secondAmountMin =
                            PolkaswapFormulas.calculateMinAmount(
                                toToken.amount,
                                removeState.slippage
                            )
                        val desired =
                            if (percent == 100.0) poolData.poolProvidersBalance else PolkaswapFormulas.calculateAmountByPercentage(
                                poolData.poolProvidersBalance,
                                percent,
                                poolData.basic.baseToken.precision
                            )

                        removeState = removeState.copy(
                            confirm = removeState.confirm.copy(
                                btnState = removeState.confirm.btnState.copy(
                                    enabled = false,
                                    loading = true,
                                )
                            )
                        )
                        var result = ""
                        try {
                            result = poolsInteractor.removeLiquidity(
                                fromToken.token,
                                toToken.token,
                                desired,
                                firstAmountMin,
                                secondAmountMin,
                                networkFee.orZero(),
                            )
                        } catch (t: Throwable) {
                            onError(t)
                        } finally {
                            removeState = removeState.copy(
                                confirm = removeState.confirm.copy(
                                    btnState = removeState.confirm.btnState.copy(
                                        enabled = false,
                                        loading = false,
                                    ),
                                    confirmResult = result.isNotEmpty()
                                )
                            )
                            delay(500)
                            removeState = removeState.copy(
                                confirm = removeState.confirm.copy(
                                    confirmResult = null
                                )
                            )
                            if (result.isNotEmpty())
                                assetsRouter.showTxDetails(result, true)
                            else router.returnToHubFragment()
                        }
                    }
                }
            }
        }
    }

    private suspend fun reCalcDetails() {
        removeState.assetState1?.token?.let { firstToken ->
            removeState.assetState2?.token?.let { secondToken ->
                val newPoolShare =
                    if (amount1 != BigDecimal.ZERO && amount2 != BigDecimal.ZERO) {
                        poolDataReal?.let { poolDataRealLocal ->
                            val share = PolkaswapFormulas.estimateRemovingShareOfPool(
                                amount2,
                                poolDataRealLocal.targetPooled,
                                poolDataRealLocal.basic.targetReserves,
                            )
                            "${
                                numbersFormatter.formatBigDecimal(
                                    share,
                                    ASSET_PRECISION,
                                )
                            }%"
                        } ?: ""
                    } else {
                        ""
                    }

                val firstPerSecond = numbersFormatter.formatBigDecimal(
                    PolkaswapFormulas.calculateTokenPerTokenRate(
                        poolDataUsable?.basePooled.orZero(),
                        poolDataUsable?.targetPooled.orZero()
                    ),
                    ASSET_PRECISION
                )

                val secondPerFirst = numbersFormatter.formatBigDecimal(
                    PolkaswapFormulas.calculateTokenPerTokenRate(
                        poolDataUsable?.targetPooled.orZero(),
                        poolDataUsable?.basePooled.orZero()
                    ),
                    ASSET_PRECISION
                )

                val poolDataLocal = poolDataUsable
                val networkFeeString = "$networkFee ${feeToken().symbol}"
                removeState = removeState.copy(
                    poolInFarming = poolInFarming,
                    prices = removeState.prices.copy(
                        pair1 = "${firstToken.symbol}/${secondToken.symbol}",
                        pair1Value = firstPerSecond,
                        pair2 = "${secondToken.symbol}/${firstToken.symbol}",
                        pair2Value = secondPerFirst,
                        apy = if (poolDataLocal?.basic?.sbapy != null) "${
                            numbersFormatter.format(
                                poolDataLocal.basic.sbapy ?: 0.0,
                                ASSET_PRECISION
                            )
                        }%" else "",
                        fee = networkFeeString,
                    ),
                    estimated = removeState.estimated.copy(
                        token1 = firstToken.symbol,
                        token1Value = if (poolDataLocal == null) "" else numbersFormatter.formatBigDecimal(
                            poolDataLocal.basePooled - amount1,
                            ASSET_PRECISION
                        ),
                        token2 = secondToken.symbol,
                        token2Value = if (poolDataLocal == null) "" else numbersFormatter.formatBigDecimal(
                            poolDataLocal.targetPooled - amount2,
                            ASSET_PRECISION
                        ),
                        shareOfPool = newPoolShare,
                    )
                )
                if (amount1.compareTo(removeState.assetState1?.amount) != 0) {
                    removeState = removeState.copy(
                        assetState1 = removeState.assetState1?.copy(
                            amount = amount1,
                            initialAmount = amount1,
                            amountFiat = removeState.assetState1?.token?.printFiat(
                                amount1,
                                numbersFormatter
                            ).orEmpty()
                        )
                    )
                }
                if (amount2.compareTo(removeState.assetState2?.amount) != 0) {
                    removeState = removeState.copy(
                        assetState2 = removeState.assetState2?.copy(
                            amount = amount2,
                            initialAmount = amount2,
                            amountFiat = removeState.assetState2?.token?.printFiat(
                                amount2,
                                numbersFormatter
                            ).orEmpty()
                        )
                    )
                }
                checkErrors()
            }
        }
    }

    private fun checkErrors() {
        removeState.assetState1?.token?.let {
            val (text, enabled) = when {
                amount1.compareTo(BigDecimal.ZERO) == 0 || amount2.compareTo(BigDecimal.ZERO) == 0 -> {
                    resourceManager.getString(R.string.common_enter_amount) to false
                }

                balanceFee < networkFee -> {
                    resourceManager.getString(R.string.polkaswap_insufficient_balance)
                        .format(resourceManager.getString(R.string.xor)) to false
                }

                else -> {
                    null to true
                }
            }

            removeState = removeState.copy(
                btnState = removeState.btnState.copy(
                    text = text ?: resourceManager.getString(R.string.pool_button_remove),
                    enabled = enabled,
                ),
                confirm = removeState.confirm.copy(
                    btnState = removeState.btnState.copy(
                        text = text ?: resourceManager.getString(R.string.common_confirm),
                        enabled = enabled,
                    ),
                )
            )
        }
    }

    private suspend fun updateTransactionReminderWarningVisibility() =
        with(removeState) {
            if (assetState1 == null)
                return@with

            val result = assetsInteractor.isEnoughXorLeftAfterTransaction(
                primaryToken = assetState1.token,
                primaryTokenAmount = assetState1.amount,
                secondaryToken = null,
                secondaryTokenAmount = null,
                networkFeeInXor = networkFee.orZero()
            )

            removeState = removeState.copy(
                shouldTransactionReminderInsufficientWarningBeShown = result,
                transactionFeeToken = feeToken().symbol
            )
        }

    private fun getAssetBalanceText(asset: Asset) =
        AmountFormat.getAssetBalanceText(asset, numbersFormatter, 7)
}
