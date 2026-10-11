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
import jp.co.soramitsu.androidfoundation.format.nullZero
import jp.co.soramitsu.androidfoundation.format.orZero
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge
import jp.co.soramitsu.common.presentation.transaction.badge
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.lazyAsync
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.AmountFormat
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
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
import kotlinx.coroutines.CancellationException
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
    @Assisted("id1") private val token1IdStart: String,
    @Assisted("id2") private val token2IdStart: String,
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedLiquidityRemoveViewModelFactory {
        fun create(
            @Assisted("id1") id1: String,
            @Assisted("id2") id2: String
        ): LiquidityRemoveViewModel
    }

    private var token1Id = token1IdStart
    private var token2Id = token2IdStart

    private companion object {
        const val RESULT_BADGE_MILLIS = 700L
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

    private var poolDataUsable: CommonUserPoolData? = null
    private var poolDataReal: CommonUserPoolData? = null
    private val amount1Flow = MutableStateFlow(BigDecimal.ZERO)
    private val amount2Flow = MutableStateFlow(BigDecimal.ZERO)
    private var percent: Double = 0.0

    /**
     * True from a tapped Confirm until it is known that nothing reached the network. The pool
     * and the balances are read again with every block, and each of those updates rebuilds the
     * buttons; without this the confirm button came back, tappable, in the middle of the wait
     * for the result. Once a withdrawal has reached the network it stays true: this screen is
     * then replaced by the transaction's page and sends nothing more.
     */
    private var confirmLocked = false

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
            // A failure while collecting must never leave viewModelScope uncaught.
            tryCatch {
                assetsInteractor.subscribeAssetsActiveOfCurAccount()
                    .catch { onError(it) }
                    .distinctUntilChanged()
                    .debounce(500)
                    .collectLatest { assets ->
                        assetList.clear()
                        assetList.addAll(assets)
                        onAssetsActiveCollect()
                    }
            }
        }
        viewModelScope.launch {
            // A failure while collecting must never leave viewModelScope uncaught.
            tryCatch {
                poolsInteractor.subscribePoolCacheOfCurAccount(token1Id, token2Id)
                    .map {
                        it?.let {
                            CommonUserPoolData(
                                it.basic,
                                it.user!!,
                            )
                        }
                    }
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
                                    poolDataLocal.user.poolProvidersBalance,
                                )
                            }
                            userPoolDataFixDemeter(poolDataLocal, maxPercent)
                        } else {
                            null
                        }
                    }
                    .collectLatest(::onPoolCollect)
            }
        }
        viewModelScope.launch {
            amount1Flow
                .debounce(ViewHelper.debounce)
                .onEach { amount ->
                    // A failure while recalculating must never leave viewModelScope uncaught.
                    guarded {
                        val pdu = poolDataUsable
                        if (pdu != null) {
                            amount1 = if (amount <= pdu.user.basePooled) amount else pdu.user.basePooled
                            amount2 = PolkaswapFormulas.calculateOneAmountFromAnother(
                                amount1,
                                pdu.user.basePooled,
                                pdu.user.targetPooled,
                                removeState.assetState2?.token?.precision,
                            )
                            percent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                                amount1,
                                pdu.user.basePooled,
                            )
                            reCalcDetails()
                        }
                    }
                }.filter {
                    removeState.assetState1?.token?.id == SubstrateOptionsProvider.feeAssetId
                }.onEach {
                    guarded { updateTransactionReminderWarningVisibility() }
                }.collect()
        }
        viewModelScope.launch {
            amount2Flow
                .debounce(ViewHelper.debounce)
                .collectLatest { amount ->
                    guarded {
                        poolDataUsable?.let {
                            amount2 =
                                if (amount <= it.user.targetPooled) amount else it.user.targetPooled
                            amount1 =
                                PolkaswapFormulas.calculateOneAmountFromAnother(
                                    amount2,
                                    it.user.targetPooled,
                                    it.user.basePooled,
                                    removeState.assetState1?.token?.precision
                                )
                            percent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                                amount1,
                                it.user.basePooled,
                            )
                            reCalcDetails()
                        }
                    }
                }
        }
    }

    private fun userPoolDataFixDemeter(poolData: CommonUserPoolData, prc: Double?): CommonUserPoolData {
        return if (prc != null && !prc.isNaN()) {
            poolInFarming = true
            val usablePercent = 100 - prc
            poolData.copy(
                user = poolData.user.copy(
                    basePooled = PolkaswapFormulas.calculateAmountByPercentage(
                        poolData.user.basePooled,
                        usablePercent,
                        poolData.basic.baseToken.precision,
                    ),
                    targetPooled = PolkaswapFormulas.calculateAmountByPercentage(
                        poolData.user.targetPooled,
                        usablePercent,
                        poolData.basic.baseToken.precision,
                    ),
                    poolProvidersBalance = PolkaswapFormulas.calculateAmountByPercentage(
                        poolData.user.poolProvidersBalance,
                        usablePercent,
                        poolData.basic.baseToken.precision,
                    ),
                )
            )
        } else {
            poolData
        }
    }

    private fun onFeeUnavailable(error: Throwable) {
        onError(error)
        showFeesUnavailable(
            resourceManager = resourceManager,
            error = error,
            onClose = { _navigationPop.trigger() },
            onRetry = { viewModelScope.launch { tryCatch { onAssetsActiveCollect() } } },
        )
    }

    private suspend fun onAssetsActiveCollect() {
        val asset1 = assetList.first { it.token.id == token1Id }
        val asset2 = assetList.first { it.token.id == token2Id }
        if (networkFee == null) {
            // Asked from the SORA network; while it cannot be reached the user is told so.
            networkFee = try {
                poolsInteractor.fetchRemoveLiquidityNetworkFee(
                    asset1.token,
                    asset2.token,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                onFeeUnavailable(t)
                return
            }
        }
        if (removeState.assetState1 == null) {
            removeState = removeState.copy(
                assetState1 = AssetAmountInputState(
                    token = asset1.token,
                    balance = getAssetBalanceText(asset1),
                    amount = null,
                    amountFiat = "",
                    enabled = true,
                ),
            )
        }
        if (removeState.assetState1?.token?.id != asset1.token.id) {
            removeState = removeState.copy(
                assetState1 = removeState.assetState1?.copy(
                    token = asset1.token,
                    balance = getAssetBalanceText(asset1),
                ),
            )
        }
        if (removeState.assetState2 == null) {
            removeState = removeState.copy(
                assetState2 = AssetAmountInputState(
                    token = asset2.token,
                    balance = getAssetBalanceText(asset2),
                    amount = null,
                    amountFiat = "",
                    enabled = true,
                ),
            )
        }
        balanceFee =
            assetList.first { it.token.id == SubstrateOptionsProvider.feeAssetId }.balance.transferable

        removeState.assetState1?.let { state ->
            assetList.find { it.token.id == state.token.id }?.let { asset ->
                removeState = removeState.copy(
                    assetState1 = state.copy(
                        balance = getAssetBalanceText(asset)
                    )
                )
            }
        }
        removeState.assetState2?.let { state ->
            assetList.find { it.token.id == state.token.id }?.let { asset ->
                removeState = removeState.copy(
                    assetState2 = state.copy(
                        balance = getAssetBalanceText(asset)
                    )
                )
            }
        }
        reCalcDetails()
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

    fun onPercentClick(prc: Int) {
        viewModelScope.launch {
            guarded { applyPercent(prc) }
        }
    }

    private suspend fun applyPercent(prc: Int) {
        val poolData = poolDataUsable ?: return
        val asset1 = removeState.assetState1 ?: return
        val asset2 = removeState.assetState2 ?: return
        percent = prc.toDouble()
        if (percent == 100.0) {
            amount1 = poolData.user.basePooled
            amount2 = poolData.user.targetPooled
        } else {
            amount1 = PolkaswapFormulas.calculateAmountByPercentage(
                poolData.user.basePooled,
                percent,
                asset1.token.precision,
            )
            amount2 = PolkaswapFormulas.calculateAmountByPercentage(
                poolData.user.targetPooled,
                percent,
                asset2.token.precision,
            )
        }
        reCalcDetails()
    }

    fun onSlippageClick() {
        removeState = removeState.copy(
            assetState1 = removeState.assetState1?.copy(
                amount = removeState.assetState1?.amount?.nullZero(),
            ),
            assetState2 = removeState.assetState2?.copy(
                amount = removeState.assetState2?.amount?.nullZero(),
            ),
        )
    }

    fun slippageChanged(slippageTolerance: Double) {
        removeState = removeState.copy(
            slippage = slippageTolerance
        )
    }

    private suspend fun onPoolCollect(cups: CommonUserPoolData?) {
        poolDataUsable = cups
        amount1 =
            if (cups != null) PolkaswapFormulas.calculateAmountByPercentage(
                cups.user.basePooled,
                percent,
                cups.basic.baseToken.precision,
            ) else BigDecimal.ZERO
        amount2 =
            if (cups != null) PolkaswapFormulas.calculateAmountByPercentage(
                cups.user.targetPooled,
                percent,
                cups.basic.targetToken.precision,
            ) else BigDecimal.ZERO
        reCalcDetails()
    }

    fun onAmount1Change(value: BigDecimal) {
        removeState = removeState.copy(
            assetState1 = removeState.assetState1?.copy(
                amountFiat = removeState.assetState1?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty(),
                amount = value
            )
        )
        amount1Flow.value = value
    }

    fun onAmount2Change(value: BigDecimal) {
        removeState = removeState.copy(
            assetState2 = removeState.assetState2?.copy(
                amountFiat = removeState.assetState2?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty(),
                amount = value
            )
        )
        amount2Flow.value = value
    }

    fun onReviewClick() {
        // Nothing new is reviewed while a Confirm is on its way or its result waits to be shown.
        if (confirmLocked || resultHeld) return
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

    fun onConfirmClick() = confirmRemoveLiquidity(confirmedDuplicate = false)

    /**
     * Sends the reviewed withdrawal and shows exactly what is known afterwards. A transaction
     * that reached the network (confirmed, failed on chain, or still waiting) opens its
     * transaction page, which carries the status and follows it. One that did not reach the
     * network is explained in a dialog and this screen stays with Confirm available again.
     *
     * The result arrives after a wait of up to two minutes, so the app can be in the background
     * or behind the PIN lock by then. The dialogs are delivered when the screen is shown again;
     * the transaction page opens when it is in front (see [openTransactionPage]). Until then
     * the result mark stays on the confirm screen and Confirm stays off.
     */
    private fun confirmRemoveLiquidity(confirmedDuplicate: Boolean) {
        // A Confirm is on its way, or a withdrawal of this screen reached the network and its
        // page has not opened yet.
        if (confirmLocked || resultHeld) return
        viewModelScope.launch {
            removeState.assetState1?.let { fromToken ->
                removeState.assetState2?.let { toToken ->
                    poolDataUsable?.let { poolData ->
                        val firstAmountMin =
                            PolkaswapFormulas.calculateMinAmount(
                                fromToken.amount.orZero(),
                                removeState.slippage
                            )
                        val secondAmountMin =
                            PolkaswapFormulas.calculateMinAmount(
                                toToken.amount.orZero(),
                                removeState.slippage
                            )
                        val desired =
                            if (percent == 100.0) poolData.user.poolProvidersBalance else PolkaswapFormulas.calculateAmountByPercentage(
                                poolData.user.poolProvidersBalance,
                                percent,
                                poolData.basic.baseToken.precision
                            )

                        confirmLocked = true
                        setConfirmButton(enabled = false, loading = true)
                        var outcome: TransactionOutcome? = null
                        var notSent: Throwable? = null
                        try {
                            outcome = poolsInteractor.removeLiquidity(
                                fromToken.token,
                                toToken.token,
                                desired,
                                firstAmountMin,
                                secondAmountMin,
                                networkFee.orZero(),
                                confirmedDuplicate,
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: DuplicateWaitingTransactionException) {
                            confirmLocked = false
                            setConfirmButton(enabled = true, loading = false)
                            askBeforeSendingAgain(resourceManager) {
                                confirmRemoveLiquidity(confirmedDuplicate = true)
                            }
                            return@launch
                        } catch (t: Throwable) {
                            notSent = t
                        }
                        removeState = removeState.copy(
                            confirm = removeState.confirm.copy(
                                btnState = removeState.confirm.btnState.copy(
                                    enabled = false,
                                    loading = false,
                                ),
                                confirmResult = outcome?.badge() ?: TransactionResultBadge.NOT_SENT,
                            )
                        )
                        delay(RESULT_BADGE_MILLIS)
                        when (outcome) {
                            is TransactionOutcome.Confirmed,
                            is TransactionOutcome.Waiting,
                            is TransactionOutcome.FailedOnChain ->
                                // The result mark stays until the page has taken this
                                // screen's place.
                                openTransactionPage(outcome.txHash)
                            is TransactionOutcome.RefusedByNode -> {
                                confirmLocked = false
                                clearResultMark()
                                setConfirmButton(enabled = true, loading = false)
                                showTransactionRefused(resourceManager, outcome)
                            }
                            null -> {
                                confirmLocked = false
                                clearResultMark()
                                setConfirmButton(enabled = true, loading = false)
                                showTransactionNotSent(resourceManager, checkNotNull(notSent))
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Opens the withdrawal's transaction page in place of this screen, only while this screen is
     * in front. A result that arrives while the app is in the background, or while the PIN lock
     * is on top, is held and the page opens on the next resume; nothing is navigated before
     * that.
     */
    private fun openTransactionPage(txHash: String) = showWhenInFront {
        assetsRouter.showTxResult(txHash, TransactionScreen.REMOVE_LIQUIDITY)
    }

    private fun clearResultMark() {
        removeState = removeState.copy(
            confirm = removeState.confirm.copy(confirmResult = null)
        )
    }

    private fun setConfirmButton(enabled: Boolean, loading: Boolean) {
        removeState = removeState.copy(
            confirm = removeState.confirm.copy(
                btnState = removeState.confirm.btnState.copy(
                    enabled = enabled,
                    loading = loading,
                )
            )
        )
    }

    private suspend fun reCalcDetails() {
        removeState.assetState1?.token?.let { firstToken ->
            removeState.assetState2?.token?.let { secondToken ->
                val newPoolShare =
                    if (amount1 != BigDecimal.ZERO && amount2 != BigDecimal.ZERO) {
                        poolDataReal?.let { poolDataRealLocal ->
                            val share = PolkaswapFormulas.estimateRemovingShareOfPool(
                                amount2,
                                poolDataRealLocal.user.targetPooled,
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
                        poolDataUsable?.user?.basePooled.orZero(),
                        poolDataUsable?.user?.targetPooled.orZero()
                    ),
                    ASSET_PRECISION
                )

                val secondPerFirst = numbersFormatter.formatBigDecimal(
                    PolkaswapFormulas.calculateTokenPerTokenRate(
                        poolDataUsable?.user?.targetPooled.orZero(),
                        poolDataUsable?.user?.basePooled.orZero()
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
                            poolDataLocal.user.basePooled - amount1,
                            ASSET_PRECISION
                        ),
                        token2 = secondToken.symbol,
                        token2Value = if (poolDataLocal == null) "" else numbersFormatter.formatBigDecimal(
                            poolDataLocal.user.targetPooled - amount2,
                            ASSET_PRECISION
                        ),
                        shareOfPool = newPoolShare,
                    )
                )
                if (amount1.compareTo(removeState.assetState1?.amount.orZero()) != 0) {
                    removeState = removeState.copy(
                        assetState1 = removeState.assetState1?.copy(
                            amount = amount1,
                            amountFiat = removeState.assetState1?.token?.printFiat(
                                amount1,
                                numbersFormatter
                            ).orEmpty()
                        )
                    )
                }
                if (amount2.compareTo(removeState.assetState2?.amount.orZero()) != 0) {
                    removeState = removeState.copy(
                        assetState2 = removeState.assetState2?.copy(
                            amount = amount2,
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
                    btnState = if (confirmLocked) {
                        // A pool or balance update must not bring Confirm back while a tapped
                        // Confirm is on its way or its result waits to be shown.
                        removeState.confirm.btnState.copy(enabled = false)
                    } else {
                        removeState.btnState.copy(
                            text = text ?: resourceManager.getString(R.string.common_confirm),
                            enabled = enabled,
                        )
                    },
                )
            )
        }
    }

    private suspend fun updateTransactionReminderWarningVisibility() =
        with(removeState) {
            if (assetState1 == null)
                return@with

            val result = assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = networkFee.orZero(),
                xorChange = if (assetState1.token.id == SubstrateOptionsProvider.feeAssetId) -assetState1.amount.orZero() else null,
            )

            removeState = removeState.copy(
                shouldTransactionReminderInsufficientWarningBeShown = result,
            )
        }

    private fun getAssetBalanceText(asset: Asset) =
        AmountFormat.getAssetBalanceText(asset, numbersFormatter, 7)
}
