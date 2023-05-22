/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import jp.co.soramitsu.common_wallet.domain.model.PoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.AmountFormat
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.calculateAmountByPercentage
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.calculateOneAmountFromAnother
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.calculateTokenPerTokenRate
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.estimateRemovingShareOfPool
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class LiquidityRemoveViewModel @AssistedInject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val router: WalletRouter,
    private val mainRouter: MainRouter,
    private val walletInteractor: WalletInteractor,
    private val poolsInteractor: PoolsInteractor,
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

    private val feeTokenAsync by viewModelScope.lazyAsync { walletInteractor.getFeeToken() }
    private suspend fun feeToken() = feeTokenAsync.await()

    private var poolData: PoolData? = null
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
                    recalcDetails()
                }
        }
        viewModelScope.launch {
            poolsInteractor.subscribePoolCache(token1Id, token2Id)
                .catch { onError(it) }
                .distinctUntilChanged()
                .debounce(500)
                .collectLatest { data ->
                    poolData = data
                    amount1 = if (data != null) calculateAmountByPercentage(
                        data.basePooled,
                        percent,
                        data.baseToken.precision,
                    ) else BigDecimal.ZERO
                    amount2 = if (data != null) calculateAmountByPercentage(
                        data.secondPooled,
                        percent,
                        data.token.precision,
                    ) else BigDecimal.ZERO
                    recalcDetails()
                }
        }
        viewModelScope.launch {
            amount1Flow
                .debounce(ViewHelper.debounce)
                .collectLatest { amount ->
                    poolData?.let {
                        amount1 = if (amount <= it.basePooled) amount else it.basePooled
                        amount2 = calculateOneAmountFromAnother(
                            amount1,
                            it.basePooled,
                            it.secondPooled
                        )
                        percent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                            amount1,
                            it.basePooled,
                        )
                        recalcDetails()
                    }
                }
        }
        viewModelScope.launch {
            amount2Flow
                .debounce(ViewHelper.debounce)
                .collectLatest { amount ->
                    poolData?.let {
                        amount2 = if (amount <= it.secondPooled) amount else it.secondPooled
                        amount1 =
                            calculateOneAmountFromAnother(amount2, it.secondPooled, it.basePooled)
                        percent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                            amount1,
                            it.basePooled,
                        )
                        recalcDetails()
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
            poolData?.let { poolData ->
                removeState.assetState1?.let { asset1 ->
                    removeState.assetState2?.let { asset2 ->
                        percent = p.toDouble()
                        if (percent == 100.0) {
                            amount1 = poolData.basePooled
                            amount2 = poolData.secondPooled
                        } else {
                            amount1 = calculateAmountByPercentage(
                                poolData.basePooled,
                                percent,
                                asset1.token.precision,
                            )
                            amount2 = calculateAmountByPercentage(
                                poolData.secondPooled,
                                percent,
                                asset2.token.precision,
                            )
                        }
                        recalcDetails()
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
                    poolData?.let { poolData ->
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
                            if (percent == 100.0) poolData.poolProvidersBalance else calculateAmountByPercentage(
                                poolData.poolProvidersBalance,
                                percent,
                                poolData.baseToken.precision
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

    private suspend fun recalcDetails() {
        val poolData = poolData
        removeState.assetState1?.token?.let { firstToken ->
            removeState.assetState2?.token?.let { secondToken ->
                val newPoolshare =
                    if (poolData != null && amount1 != BigDecimal.ZERO && amount2 != BigDecimal.ZERO) {
                        "${
                            numbersFormatter.formatBigDecimal(
                                estimateRemovingShareOfPool(
                                    amount2,
                                    poolData.secondPooled,
                                    poolData.secondReserves
                                ),
                                ASSET_PRECISION
                            )
                        }%"
                    } else {
                        ""
                    }

                val firstPerSecond = numbersFormatter.formatBigDecimal(
                    calculateTokenPerTokenRate(
                        poolData?.basePooled.orZero(),
                        poolData?.secondPooled.orZero()
                    ),
                    ASSET_PRECISION
                )

                val secondPerFirst = numbersFormatter.formatBigDecimal(
                    calculateTokenPerTokenRate(
                        poolData?.secondPooled.orZero(),
                        poolData?.basePooled.orZero()
                    ),
                    ASSET_PRECISION
                )

                val networkFeeString = "$networkFee ${feeToken().symbol}"
                removeState = removeState.copy(
                    prices = removeState.prices.copy(
                        pair1 = "${firstToken.symbol}/${secondToken.symbol}",
                        pair1Value = firstPerSecond,
                        pair2 = "${secondToken.symbol}/${firstToken.symbol}",
                        pair2Value = secondPerFirst,
                        apy = if (poolData?.strategicBonusApy != null) "${
                            numbersFormatter.format(
                                poolData.strategicBonusApy ?: 0.0,
                                ASSET_PRECISION
                            )
                        }%" else "",
                        fee = networkFeeString,
                    ),
                    estimated = removeState.estimated.copy(
                        token1 = firstToken.symbol,
                        token1Value = if (poolData == null) "" else numbersFormatter.formatBigDecimal(
                            poolData.basePooled - amount1,
                            ASSET_PRECISION
                        ),
                        token2 = secondToken.symbol,
                        token2Value = if (poolData == null) "" else numbersFormatter.formatBigDecimal(
                            poolData.secondPooled - amount2,
                            ASSET_PRECISION
                        ),
                        shareOfPool = newPoolshare,
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

    private fun getAssetBalanceText(asset: Asset) =
        AmountFormat.getAssetBalanceText(asset, numbersFormatter, 7)
}
