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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.domain.subtractFee
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common.util.ext.lazyAsync
import jp.co.soramitsu.common.util.ext.nullZero
import jp.co.soramitsu.common.util.ext.orZero
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.common_wallet.presentation.compose.components.SelectSearchAssetState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.common_wallet.presentation.compose.util.AmountFormat
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapFeeMode
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.SwapMainState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.defaultSwapDetailsState
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@FlowPreview
class SwapViewModel @AssistedInject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val walletInteractor: WalletInteractor,
    private val swapInteractor: SwapInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val mainRouter: MainRouter,
    private val assetsRouter: AssetsRouter,
    private val coroutineManager: CoroutineManager,
    @Assisted("idfrom") private val token1Id: String,
    @Assisted("idto") private val token2Id: String,
    @Assisted("isLaunchedFromSoraCard") private val isLaunchedFromSoraCard: Boolean
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedSwapViewModelFactory {
        fun create(
            @Assisted("idfrom") idFrom: String,
            @Assisted("idto") idTo: String,
            @Assisted("isLaunchedFromSoraCard") isLaunchedFromSoraCard: Boolean
        ): SwapViewModel
    }

    private val assetsList = mutableListOf<Asset>()
    private val feeTokenAsync by viewModelScope.lazyAsync { walletInteractor.getFeeToken() }
    private suspend fun feeToken() = feeTokenAsync.await()

    private class PropertyValue(var reloadMarkets: Boolean = false) {
        @Synchronized
        fun reset() {
            reloadMarkets = false
        }

        @Synchronized
        fun newReloadMarkets(v: Boolean): PropertyValue {
            reloadMarkets = reloadMarkets or v
            return this
        }
    }

    val navigationDisclaimerEvent = SingleLiveEvent<Unit>()

    private val fromAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val toAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private var isExtrinsicSubmitted = false

    private val property = PropertyValue()

    private val onChangedProperty = SuspendableProperty<PropertyValue>(1)

    private val availableMarkets = mutableListOf<Market>()
    private var desired: WithDesired = WithDesired.INPUT
    private var swapDetails: SwapDetails? = null
    private var networkFee: BigDecimal? = null

    private val _swapMainState = MutableStateFlow(
        SwapMainState(
            tokenFromState = null,
            tokenToState = null,
            slippage = 0.5,
            selectSearchAssetState = null,
            market = Market.SMART,
            selectMarketState = null,
            details = defaultSwapDetailsState(),
            swapButtonState = ButtonState(
                text = resourceManager.getString(R.string.choose_tokens),
                enabled = false,
                loading = false
            ),
            confirmButtonState = ButtonState(
                text = resourceManager.getString(R.string.common_confirm),
                enabled = false,
                loading = false
            ),
            confirmText = AnnotatedString(""),
            confirmResult = null,
        )
    )
    val swapMainState = _swapMainState.asStateFlow()

    private var amountFromPrev: BigDecimal = BigDecimal.ZERO
    private var amountToPrev: BigDecimal = BigDecimal.ZERO
    private val amountFrom: BigDecimal
        get() = _swapMainState.value.tokenFromState?.initialAmount.orZero()
    private val amountTo: BigDecimal
        get() = _swapMainState.value.tokenToState?.initialAmount.orZero()

    override fun startScreen(): String = SwapRoutes.start

    override fun onCurrentDestinationChanged(curDest: String) {
        _toolbarState.value?.let { state ->
            _toolbarState.value = state.copy(
                type = if (curDest == SwapRoutes.start || curDest == SwapRoutes.confirm) SoramitsuToolbarType.ImageCentered() else SoramitsuToolbarType.SmallCentered(),
                basic = state.basic.copy(
                    title = if (curDest == SwapRoutes.start || curDest == SwapRoutes.confirm) R.drawable.ic_polkaswap_full_title else when (curDest) {
                        SwapRoutes.slippage -> R.string.slippage
                        SwapRoutes.markets -> R.string.polkaswap_market_title
                        SwapRoutes.selectToken -> R.string.common_choose_asset
                        else -> ""
                    },
                )
            )
        }
    }

    private val mainToolbarState = SoramitsuToolbarState(
        type = SoramitsuToolbarType.ImageCentered(),
        basic = BasicToolbarState(
            title = R.drawable.ic_polkaswap_full_title,
            navIcon = R.drawable.ic_arrow_left,
        ),
    )

    init {
        _toolbarState.value = mainToolbarState

        swapInteractor.getPolkaswapDisclaimerVisibility()
            .catch {
                onError(it)
            }
            .onEach {
                delay(500)
                if (it) navigationDisclaimerEvent.trigger()
            }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            swapInteractor.observeSelectedMarket()
                .catch {
                    onError(it)
                }
                .collectLatest {
                    _swapMainState.value = _swapMainState.value.copy(
                        market = it
                    )
                    onChangedProperty.set(property.newReloadMarkets(false))
                }
        }
        swapInteractor.observeSwap()
            .catch {
                onError(it)
            }
            .onEach {
                _swapMainState.value = _swapMainState.value.copy(
                    tokenFromState = _swapMainState.value.tokenFromState?.copy(
                        initialAmount = BigDecimal.ZERO,
                        amountFiat = "",
                    ),
                    tokenToState = _swapMainState.value.tokenToState?.copy(
                        initialAmount = BigDecimal.ZERO,
                        amountFiat = "",
                    ),
                    details = defaultSwapDetailsState(),
                    swapButtonState = ButtonState(
                        text = resourceManager.getString(R.string.common_enter_amount),
                        enabled = false,
                        loading = false,
                    ),
                    confirmButtonState = ButtonState(
                        text = resourceManager.getString(R.string.common_confirm),
                        enabled = false,
                        loading = false,
                    ),
                    confirmResult = null,
                )
                swapDetails = null
                updateDetailsView()
                onChangedProperty.set(property.newReloadMarkets(false))
            }
            .launchIn(viewModelScope)

        assetsInteractor.subscribeAssetsActiveOfCurAccount()
            .catch { onError(it) }
            .distinctUntilChanged()
            .onEach { assets ->
                assetsList.clear()
                assetsList.addAll(assets)

                if (_swapMainState.value.tokenFromState == null && token1Id.isNotEmpty()) {
                    assetsInteractor.getAssetOrThrow(token1Id).let { assetFrom ->
                        _swapMainState.value = _swapMainState.value.copy(
                            tokenFromState = AssetAmountInputState(
                                token = assetFrom.token,
                                balance = getAssetBalanceText(assetFrom),
                                initialAmount = null,
                                amountFiat = "",
                                enabled = false,
                            ),
                        )
                    }
                }

                if (_swapMainState.value.tokenToState == null && token2Id.isNotEmpty()) {
                    assetsInteractor.getAssetOrThrow(token2Id).let { assetTo ->
                        _swapMainState.value = _swapMainState.value.copy(
                            tokenToState = AssetAmountInputState(
                                token = assetTo.token,
                                balance = getAssetBalanceText(assetTo),
                                initialAmount = null,
                                amountFiat = "",
                                enabled = false,
                            ),
                        )
                    }
                }

                _swapMainState.value.tokenFromState?.let { fromState ->
                    assets.find { it.token.id == fromState.token.id }?.let { fromAsset ->
                        _swapMainState.value = _swapMainState.value.copy(
                            tokenFromState = fromState.copy(
                                balance = getAssetBalanceText(fromAsset)
                            )
                        )
                    }
                }

                _swapMainState.value.tokenToState?.let { toState ->
                    assets.find { it.token.id == toState.token.id }?.let { toAsset ->
                        _swapMainState.value = _swapMainState.value.copy(
                            tokenToState = toState.copy(
                                balance = getAssetBalanceText(toAsset)
                            )
                        )
                    }
                }

                if (networkFee == null) {
                    networkFee = swapInteractor.fetchSwapNetworkFee(feeToken())
                }

                onChangedProperty.set(property.newReloadMarkets(false))
            }
            .launchIn(viewModelScope)

        swapInteractor.observePoolReserves()
            .catch {
                onError(it)
            }
            .onEach {
                onChangedProperty.set(property.newReloadMarkets(false))
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            onChangedProperty.observe()
                .debounce(ViewHelper.debounce)
                .catch {
                    onError(it)
                }
                .collectLatest {
                    if (it.reloadMarkets) {
                        getMarkets()
                        property.reset()
                    }
                    recalcDetails()
                    toggleSwapButtonStatus()
                    updateTransactionReminderWarningVisibility()
                    resetLoading()
                }
        }
        viewModelScope.launch {
            fromAmountFlow
                .debounce(ViewHelper.debounce)
                .collectLatest { amount ->
                    if (amount == amountFromPrev && desired == WithDesired.INPUT) {
                        resetLoading()
                    } else {
                        amountFromPrev = amount
                        desired = WithDesired.INPUT
                        onChangedProperty.set(property.newReloadMarkets(false))
                    }
                }
        }
        viewModelScope.launch {
            toAmountFlow
                .debounce(ViewHelper.debounce)
                .collectLatest { amount ->
                    if (amount == amountToPrev && desired == WithDesired.OUTPUT) {
                        resetLoading()
                    } else {
                        amountToPrev = amount
                        desired = WithDesired.OUTPUT
                        onChangedProperty.set(property.newReloadMarkets(false))
                    }
                }
        }
    }

    fun onDisclaimerClose() {
        viewModelScope.launch {
            swapInteractor.setPolkaswapDisclaimerVisibility(false)
        }
    }

    fun onSlippageClick() {
        _swapMainState.value = _swapMainState.value.copy(
            tokenFromState = _swapMainState.value.tokenFromState?.copy(
                initialAmount = _swapMainState.value.tokenFromState?.initialAmount?.nullZero(),
            ),
            tokenToState = _swapMainState.value.tokenToState?.copy(
                initialAmount = _swapMainState.value.tokenToState?.initialAmount?.nullZero(),
            ),
        )
    }

    fun onMarketClick() {
        _swapMainState.value = _swapMainState.value.copy(
            selectMarketState = _swapMainState.value.market to availableMarkets,
            tokenFromState = _swapMainState.value.tokenFromState?.copy(
                initialAmount = _swapMainState.value.tokenFromState?.initialAmount?.nullZero(),
            ),
            tokenToState = _swapMainState.value.tokenToState?.copy(
                initialAmount = _swapMainState.value.tokenToState?.initialAmount?.nullZero(),
            ),
        )
    }

    fun fromCardClicked() {
        if (assetsList.isNotEmpty()) {
            _swapMainState.value = _swapMainState.value.copy(
                selectSearchAssetState = SelectSearchAssetState(
                    filter = "",
                    fullList = mapAssetsToCardState(
                        assetsList.filter { it.token.id != _swapMainState.value.tokenToState?.token?.id.orEmpty() },
                        numbersFormatter
                    )
                ),
                tokenFromState = _swapMainState.value.tokenFromState?.copy(
                    initialAmount = _swapMainState.value.tokenFromState?.initialAmount?.nullZero(),
                ),
                tokenToState = _swapMainState.value.tokenToState?.copy(
                    initialAmount = _swapMainState.value.tokenToState?.initialAmount?.nullZero(),
                ),
            )
        }
    }

    fun toCardClicked() {
        if (assetsList.isNotEmpty()) {
            _swapMainState.value = _swapMainState.value.copy(
                selectSearchAssetState = SelectSearchAssetState(
                    filter = "",
                    fullList = mapAssetsToCardState(
                        assetsList.filter { it.token.id != _swapMainState.value.tokenFromState?.token?.id.orEmpty() },
                        numbersFormatter
                    )
                ),
                tokenFromState = _swapMainState.value.tokenFromState?.copy(
                    initialAmount = _swapMainState.value.tokenFromState?.initialAmount?.nullZero(),
                ),
                tokenToState = _swapMainState.value.tokenToState?.copy(
                    initialAmount = _swapMainState.value.tokenToState?.initialAmount?.nullZero(),
                ),
            )
        }
    }

    private suspend fun updateTransactionReminderWarningVisibility() =
        with(_swapMainState.value) {
            if (tokenFromState == null || tokenToState == null)
                return@with
            val change = if (tokenFromState.token.id == SubstrateOptionsProvider.feeAssetId) {
                tokenFromState.initialAmount
            } else if (tokenToState.token.id == SubstrateOptionsProvider.feeAssetId) {
                -tokenToState.initialAmount.orZero()
            } else { null }
            val result = assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = networkFee.orZero(),
                xorChange = change,
            )

            _swapMainState.value = _swapMainState.value.copy(
                details = details.copy(
                    shouldTransactionReminderInsufficientWarningBeShown = result && swapButtonState.enabled,
                )
            )
        }

    fun fromAssetSelected(tokenId: String) {
        assetsList.find { it.token.id == tokenId }?.let {
            toAndFromAssetsSelected(null, it.token)
        }
    }

    fun toAssetSelected(tokenId: String) {
        assetsList.find { it.token.id == tokenId }?.let {
            toAndFromAssetsSelected(it.token, null)
        }
    }

    fun onMarketSelected(market: Market) {
        swapInteractor.setSwapMarket(market)
        _swapMainState.value = _swapMainState.value.copy(
            selectMarketState = null,
        )
    }

    fun onTokensSwapClick() {
        desired = if (desired == WithDesired.INPUT) WithDesired.OUTPUT else WithDesired.INPUT
        _swapMainState.value = _swapMainState.value.copy(
            tokenFromState = _swapMainState.value.tokenToState?.copy(
                initialAmount = _swapMainState.value.tokenToState?.initialAmount?.nullZero()
            ),
            tokenToState = _swapMainState.value.tokenFromState?.copy(
                initialAmount = _swapMainState.value.tokenFromState?.initialAmount?.nullZero()
            ),
        )
        setSwapButtonLoading(true)
        onChangedProperty.set(property.newReloadMarkets(false))
    }

    private fun toAndFromAssetsSelected(to: Token?, from: Token?) {
        to?.let { token ->
            getAsset(token.id)?.let { asset ->
                _swapMainState.value = _swapMainState.value.copy(
                    tokenToState = AssetAmountInputState(
                        token = token,
                        balance = getAssetBalanceText(asset),
                        initialAmount = amountTo.nullZero(),
                        amountFiat = token.printFiat(amountTo, numbersFormatter),
                        enabled = true,
                    )
                )
            }
        }
        from?.let { token ->
            getAsset(token.id)?.let { asset ->
                _swapMainState.value = _swapMainState.value.copy(
                    tokenFromState = AssetAmountInputState(
                        token = token,
                        balance = getAssetBalanceText(asset),
                        initialAmount = amountFrom.nullZero(),
                        amountFiat = token.printFiat(amountFrom, numbersFormatter),
                        enabled = _swapMainState.value.tokenToState != null,
                    )
                )
            }
        }
        onChangedProperty.set(property.newReloadMarkets(true))
    }

    private fun toggleSwapButtonStatus() {
        val ok = isBalanceOk()

        val (text, enabled) = when {
            _swapMainState.value.tokenFromState == null || _swapMainState.value.tokenToState == null -> {
                resourceManager.getString(R.string.choose_tokens) to false
            }

            availableMarkets.isEmpty() -> {
                resourceManager.getString(R.string.polkaswap_pool_not_created) to false
            }
            _swapMainState.value.tokenFromState != null && amountFrom.isZero() && desired == WithDesired.INPUT -> {
                resourceManager.getString(R.string.common_enter_amount) to false
            }
            _swapMainState.value.tokenToState != null && amountTo.isZero() && desired == WithDesired.OUTPUT -> {
                resourceManager.getString(R.string.common_enter_amount) to false
            }

            ok?.isEmpty() == true -> {
                resourceManager.getString(R.string.review) to true
            }

            ok?.isNotEmpty() == true -> {
                resourceManager.getString(R.string.polkaswap_insufficient_balance)
                    .format(ok) to false
            }

            swapDetails == null -> {
                resourceManager.getString(R.string.polkaswap_insufficient_liqudity)
                    .format("") to false
            }

            else -> {
                resourceManager.getString(R.string.choose_tokens) to false
            }
        }

        val (text2, enabled2) = when {
            ok?.isNotEmpty() == true -> {
                resourceManager.getString(R.string.polkaswap_insufficient_balance)
                    .format(ok) to false
            }

            swapDetails == null -> {
                resourceManager.getString(R.string.polkaswap_insufficient_liqudity)
                    .format("") to false
            }

            else -> {
                resourceManager.getString(R.string.common_confirm) to true
            }
        }

        _swapMainState.value = _swapMainState.value.copy(
            swapButtonState = _swapMainState.value.swapButtonState.copy(
                text = text,
                enabled = enabled,
            ),
            confirmButtonState = _swapMainState.value.confirmButtonState.copy(
                text = text2,
                enabled = enabled2 && !isExtrinsicSubmitted,
            )
        )
    }

    private suspend fun getMarkets() {
        _swapMainState.value.tokenFromState?.let { from ->
            _swapMainState.value.tokenToState?.let { to ->
                tryCatch {
                    val m = swapInteractor.fetchAvailableSources(from.token.id, to.token.id)
                    availableMarkets.clear()
                    if (!m.isNullOrEmpty()) {
                        availableMarkets.addAll(m)
                    }
                }
            }
        }
    }

    /**
     * @return null - can't calculate, empty - ok, not empty - token symbol
     */
    private fun isBalanceOk(): String? {
        return _swapMainState.value.tokenFromState?.let { fromAsset ->
            _swapMainState.value.tokenToState?.let { toAsset ->
                swapDetails?.let { details ->
                    getFeeAsset()?.let { feeAsset ->
                        if (amountFrom > BigDecimal.ZERO) {
                            val result = swapInteractor.checkSwapBalances(
                                fromToken = fromAsset.token,
                                fromTokenBalance = getTransferable(fromAsset.token.id),
                                fromAmount = amountFrom,
                                swapFee = details.networkFee,
                                feeBalance = feeAsset.balance.transferable,
                                feeToken = feeAsset.token,
                                toToken = toAsset.token,
                                toTokenBalance = getTransferable(toAsset.token.id),
                                toAmount = amountTo,
                                desired = desired,
                                swapDetails = details
                            )
                            when (result) {
                                null -> {
                                    ""
                                }

                                fromAsset.token -> {
                                    fromAsset.token.symbol
                                }

                                else -> {
                                    feeAsset.token.symbol
                                }
                            }
                        } else null
                    }
                }
            }
        }
    }

    private fun setSwapButtonLoading(loading: Boolean) {
        _swapMainState.value = _swapMainState.value.copy(
            swapButtonState = _swapMainState.value.swapButtonState.copy(
                loading = loading,
            ),
        )
    }

    private fun resetLoading() {
        setSwapButtonLoading(false)
        _swapMainState.value = _swapMainState.value.copy(
            tokenFromState = _swapMainState.value.tokenFromState?.copy(
                enabled = _swapMainState.value.tokenFromState != null && _swapMainState.value.tokenToState != null,
            ),
        )
        _swapMainState.value = _swapMainState.value.copy(
            tokenToState = _swapMainState.value.tokenToState?.copy(
                enabled = _swapMainState.value.tokenFromState != null && _swapMainState.value.tokenToState != null,
            ),
        )
    }

    private suspend fun recalcDetails() {
        _swapMainState.value.tokenFromState?.let { fromAsset ->
            _swapMainState.value.tokenToState?.let { toAsset ->
                val amountToCalc = if (desired == WithDesired.INPUT) amountFrom else amountTo
                if (amountToCalc > BigDecimal.ZERO) {
                    tryCatchFinally(
                        finally = {},
                        block = {
                            val details = swapInteractor.calcDetails(
                                fromAsset.token,
                                toAsset.token,
                                feeToken(),
                                amountToCalc,
                                desired,
                                _swapMainState.value.slippage,
                            )
                            swapDetails = details
                            updateDetailsView()
                            details?.amount?.let {
                                if (desired == WithDesired.INPUT) {
                                    _swapMainState.value = _swapMainState.value.copy(
                                        tokenToState = _swapMainState.value.tokenToState?.copy(
                                            amountFiat = toAsset.token.printFiat(
                                                it,
                                                numbersFormatter
                                            ),
                                            initialAmount = it,
                                        )
                                    )
                                } else {
                                    _swapMainState.value = _swapMainState.value.copy(
                                        tokenFromState = _swapMainState.value.tokenFromState?.copy(
                                            amountFiat = fromAsset.token.printFiat(
                                                it,
                                                numbersFormatter
                                            ),
                                            initialAmount = it,
                                        )
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    private suspend fun updateDetailsView() {
        if (swapDetails == null) {
            _swapMainState.value = _swapMainState.value.copy(
                details = defaultSwapDetailsState()
            )
        } else {
            swapDetails?.let { details ->
                val per1 = numbersFormatter.formatBigDecimal(details.per1, AssetHolder.ROUNDING)
                val per2 = numbersFormatter.formatBigDecimal(details.per2, AssetHolder.ROUNDING)
                val p1: String
                val p2: String
                val minmaxTitle: Int
                val minmaxHint: Int
                val minmaxToken: Token?
                val maxMinToken: Token?
                if (desired == WithDesired.INPUT) {
                    p1 = per1
                    p2 = per2
                    minmaxTitle = R.string.polkaswap_minimum_received
                    minmaxHint = R.string.polkaswap_minimum_received_info
                    minmaxToken = _swapMainState.value.tokenToState?.token
                    maxMinToken = _swapMainState.value.tokenFromState?.token
                } else {
                    p1 = per2
                    p2 = per1
                    minmaxTitle = R.string.polkaswap_maximum_sold
                    minmaxHint = R.string.polkaswap_maximum_sold_info
                    minmaxToken = _swapMainState.value.tokenFromState?.token
                    maxMinToken = _swapMainState.value.tokenToState?.token
                }
                val (title, desc) = when (details.feeMode) {
                    SwapFeeMode.SYNTHETIC -> R.string.polkaswap_liquidity_synthetic_fee to R.string.polkaswap_liquidity_synthetic_fee_desc
                    SwapFeeMode.NON_SYNTHETIC -> R.string.polkaswap_liquidity_fee to R.string.polkaswap_liquidity_fee_info
                    SwapFeeMode.BOTH -> R.string.polkaswap_liquidity_total_fee to R.string.polkaswap_liquidity_total_fee_desc
                }
                _swapMainState.value = _swapMainState.value.copy(
                    details = _swapMainState.value.details.copy(
                        transactionFee = feeToken().printBalance(
                            details.networkFee,
                            numbersFormatter,
                            AssetHolder.ROUNDING,
                        ),
                        transactionFeeFiat = feeToken().printFiat(
                            details.networkFee,
                            numbersFormatter,
                        ),
                        priceFromTo = p1,
                        priceFromToTitle = "%s / %s".format(
                            maxMinToken?.symbol.orEmpty(),
                            minmaxToken?.symbol.orEmpty(),
                        ),
                        priceToFrom = p2,
                        priceToFromTitle = "%s / %s".format(
                            minmaxToken?.symbol.orEmpty(),
                            maxMinToken?.symbol.orEmpty(),
                        ),
                        lpFee = feeToken().printBalance(
                            details.liquidityFee,
                            numbersFormatter,
                            AssetHolder.ROUNDING,
                        ),
                        minmaxTitle = minmaxTitle,
                        minmaxHint = minmaxHint,
                        minmaxValue = minmaxToken?.printBalance(
                            details.minmax,
                            numbersFormatter,
                            AssetHolder.ROUNDING,
                        ).orEmpty(),
                        minmaxValueFiat = minmaxToken?.printFiat(details.minmax, numbersFormatter)
                            .orEmpty(),
                        route = details.swapRoute?.joinToString("->").orEmpty(),
                        lpFeeTitle = title,
                        lpFeeHint = desc,
                    )
                )
            }
        }
    }

    private fun fromAmountOnEach() {
        if (_swapMainState.value.swapButtonState.loading.not()) {
            setSwapButtonLoading(true)
            _swapMainState.value = _swapMainState.value.copy(
                tokenToState = _swapMainState.value.tokenToState?.copy(
                    enabled = false,
                )
            )
        }
    }

    fun onFromAmountChange(value: BigDecimal) {
        _swapMainState.value = _swapMainState.value.copy(
            tokenFromState = _swapMainState.value.tokenFromState?.copy(
                initialAmount = value,
                amountFiat = _swapMainState.value.tokenFromState?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty()
            )
        )
        fromAmountOnEach()
        fromAmountFlow.value = value
    }

    fun onToAmountChange(value: BigDecimal) {
        _swapMainState.value = _swapMainState.value.copy(
            tokenToState = _swapMainState.value.tokenToState?.copy(
                initialAmount = value,
                amountFiat = _swapMainState.value.tokenToState?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty()
            )
        )
        toAmountOnEach()
        toAmountFlow.value = value
    }

    private fun toAmountOnEach() {
        if (_swapMainState.value.swapButtonState.loading.not()) {
            setSwapButtonLoading(true)
            _swapMainState.value = _swapMainState.value.copy(
                tokenFromState = _swapMainState.value.tokenFromState?.copy(
                    enabled = false,
                )
            )
        }
    }

    fun fromAmountFocused() {
        if (desired != WithDesired.INPUT) {
            desired = WithDesired.INPUT
            onChangedProperty.set(property.newReloadMarkets(false))
        }
    }

    fun toAmountFocused() {
        if (desired != WithDesired.OUTPUT) {
            desired = WithDesired.OUTPUT
            onChangedProperty.set(property.newReloadMarkets(false))
        }
    }

    fun swapClicked() {
        val token =
            if (desired == WithDesired.INPUT) _swapMainState.value.tokenToState?.token else _swapMainState.value.tokenFromState?.token
        val minmax = swapDetails?.minmax
        if (token != null && minmax != null) {
            val minmaxText =
                "\n${token.printBalance(minmax, numbersFormatter, AssetHolder.ROUNDING)}\n"
            val desc =
                (
                    if (desired == WithDesired.INPUT)
                        resourceManager.getString(R.string.polkaswap_output_estimated) else
                        resourceManager.getString(R.string.polkaswap_input_estimated)
                    ).format(minmaxText)
            val i1 = desc.indexOf(minmaxText)
            _swapMainState.value = _swapMainState.value.copy(
                confirmText = if (i1 >= 0) buildAnnotatedString {
                    append(desc.substring(0, i1))
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(desc.substring(i1, i1 + minmaxText.length))
                    }
                    append(desc.substring(i1 + minmaxText.length))
                } else buildAnnotatedString {
                    append(desc)
                },
                confirmResult = null,
                confirmButtonState = ButtonState(
                    text = resourceManager.getString(R.string.common_confirm),
                    enabled = true,
                    loading = false,
                ),
            )
        }
    }

    fun onConfirmClicked() {
        isExtrinsicSubmitted = true
        swapDetails?.let { details ->
            _swapMainState.value.tokenFromState?.let { fromState ->
                _swapMainState.value.tokenToState?.let { toState ->
                    viewModelScope.launch {
                        _swapMainState.value = _swapMainState.value.copy(
                            confirmButtonState = _swapMainState.value.confirmButtonState.copy(
                                enabled = false,
                                loading = true,
                            )
                        )
                        var swapResult = ""
                        try {
                            swapResult = swapInteractor.swap(
                                fromState.token,
                                toState.token,
                                desired,
                                if (desired == WithDesired.INPUT) fromState.initialAmount.orZero() else toState.initialAmount.orZero(),
                                details.minmax,
                                details.networkFee,
                                details.liquidityFee,
                                details.dex.dexId,
                                if (desired == WithDesired.OUTPUT) fromState.initialAmount.orZero() else toState.initialAmount.orZero(),
                            )
                        } catch (t: Throwable) {
                            onError(t)
                        } finally {
                            _swapMainState.value = _swapMainState.value.copy(
                                confirmButtonState = _swapMainState.value.confirmButtonState.copy(
                                    enabled = false,
                                    loading = false,
                                ),
                                confirmResult = swapResult.isNotEmpty(),
                            )
                            delay(500)
                            _swapMainState.value = _swapMainState.value.copy(
                                confirmResult = null
                            )
                            if (swapResult.isNotEmpty())
                                assetsRouter.showTxDetails(swapResult, true)
                            else if (isLaunchedFromSoraCard)
                                mainRouter.showGetSoraCard(
                                    shouldStartSignIn = true
                                )
                            else _navigationPop.trigger()
                        }
                    }
                }
            }
        }
    }

    fun slippageChanged(slippageTolerance: Double) {
        _swapMainState.value = _swapMainState.value.copy(slippage = slippageTolerance)
        onChangedProperty.set(property.newReloadMarkets(false))
    }

    fun fromInputPercentClicked(percent: Int) {
        _swapMainState.value.tokenFromState?.let { fromAsset ->
            val transferable = getTransferable(fromAsset.token.id)
            var amount = PolkaswapFormulas.calculateAmountByPercentage(
                transferable,
                percent.toDouble(),
                fromAsset.token.precision
            )

            if (fromAsset.token.id == SubstrateOptionsProvider.feeAssetId && amount > BigDecimal.ZERO) {
                amount = subtractFee(amount, transferable, networkFee)
            }
            _swapMainState.value = _swapMainState.value.copy(
                tokenFromState = _swapMainState.value.tokenFromState?.copy(
                    amountFiat = fromAsset.token.printFiat(amount, numbersFormatter),
                    initialAmount = amount,
                )
            )
            desired = WithDesired.INPUT

            fromAmountFlow.value = amount

            onChangedProperty.set(property.newReloadMarkets(false))
        }
    }

    private fun getTransferable(tokenId: String): BigDecimal {
        return assetsList.find { it.token.id == tokenId }?.balance?.transferable.orZero()
    }

    private fun getAsset(tokenId: String) =
        assetsList.find { it.token.id == tokenId }

    private fun getFeeAsset() =
        assetsList.find { it.token.id == SubstrateOptionsProvider.feeAssetId }

    private fun getAssetBalanceText(asset: Asset) =
        AmountFormat.getAssetBalanceText(asset, numbersFormatter, 3)
}
