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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityadd

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
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common.util.ext.lazyAsync
import jp.co.soramitsu.common.util.ext.nullZero
import jp.co.soramitsu.common.util.ext.orZero
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.common_wallet.presentation.compose.components.SelectSearchAssetState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.common_wallet.presentation.compose.util.AmountFormat
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddConfirmState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddEstimatedState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddPricesState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.LiquidityAddState
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@FlowPreview
@ExperimentalCoroutinesApi
class LiquidityAddViewModel @AssistedInject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val router: WalletRouter,
    private val mainRouter: MainRouter,
    private val walletInteractor: WalletInteractor,
    private val poolsInteractor: PoolsInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val coroutineManager: CoroutineManager,
    @Assisted("id1") private val token1Id: String,
    @Assisted("id2") private val token2Id: String?,
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedLiquidityAddViewModelFactory {
        fun create(
            @Assisted("id1") id1: String,
            @Assisted("id2") id2: String?,
        ): LiquidityAddViewModel
    }

    private companion object {
        const val PER_FORMAT = "%s/%s"
        const val PERCENT_FORMAT = "%s%%"
        const val NETWORK_FEE_FORMAT = "%s %s"
        const val NETWORK_FEE_PRECISION = 8
        const val DEFAULT_PRECISION = 7
    }

    private var addToken1: String? = null
    private var addToken2: String? = null
    private var balance1: BigDecimal = BigDecimal.ZERO
    private var balance2: BigDecimal = BigDecimal.ZERO
    private var balanceFee: BigDecimal = BigDecimal.ZERO
    private var networkFee: BigDecimal = BigDecimal.ZERO

    private val feeTokenAsync by viewModelScope.lazyAsync { walletInteractor.getFeeToken() }
    private suspend fun feeToken() = feeTokenAsync.await()

    private val onChangedProperty = SuspendableProperty<Boolean>(1)

    private var amountFrom: BigDecimal = BigDecimal.ZERO
    private var amountTo: BigDecimal = BigDecimal.ZERO
    private var desired: WithDesired = WithDesired.INPUT
    private var liquidityData: LiquidityData = LiquidityData()
    private var liquidityDataSubscriptionJob: Job? = null
    private var pairEnabledJob: Job? = null
    private var pairPresentedJob: Job? = null
    private var liquidityDetails: LiquidityDetails? = null
    private val assets = mutableListOf<Asset>()
    private var hasXorReminderWarningBeenChecked = false

    private val amount1Flow = MutableStateFlow(BigDecimal.ZERO)
    private val amount2Flow = MutableStateFlow(BigDecimal.ZERO)

    private var pairEnabled: Boolean = true
    private var pairPresented: Boolean = true

    private val syntheticRegex = SubstrateOptionsProvider.syntheticTokenRegex.toRegex()

    var addState by mutableStateOf(
        LiquidityAddState(
            btnState = ButtonState(
                text = resourceManager.getString(R.string.choose_tokens),
                enabled = false,
                loading = false,
            ),
            slippage = 0.5,
            assetState1 = null,
            assetState2 = null,
            hintVisible = false,
            estimated = LiquidityAddEstimatedState(
                token1 = "",
                token1Value = "",
                token2 = "",
                token2Value = "",
                shareOfPool = "",
            ),
            prices = LiquidityAddPricesState(
                pair1 = "",
                pair1Value = "",
                pair2 = "",
                pair2Value = "",
                apy = null,
                fee = "",
            ),
            confirm = LiquidityAddConfirmState(
                text = "",
                confirmResult = null,
                btnState = ButtonState(
                    text = resourceManager.getString(R.string.common_confirm),
                    enabled = true,
                    loading = false,
                ),
            ),
            selectSearchAssetState = null,
            shouldTransactionReminderInsufficientWarningBeShown = false,
        )
    )

    override fun startScreen(): String = LiquidityAddRoutes.start

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = R.string.common_supply_liquidity_title,
                navIcon = R.drawable.ic_settings_info,
                menu = listOf(Action.Close()),
            ),
        )
        onChangedProperty.observe()
            .debounce(ViewHelper.debounce)
            .catch {
                onError(it)
            }
            .onEach {
                recalculateData()
                updateButtonState()
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            assetsInteractor.subscribeAssetsActiveOfCurAccount()
                .catch { onError(it) }
                .distinctUntilChanged()
                .collectLatest {
                    assets.clear()
                    assets.addAll(it)
                    if (addState.assetState1 == null) {
                        val a = assets.first { t -> t.token.id == token1Id }
                        addState = addState.copy(
                            assetState1 = buildInitialAssetState(a),
                        )
                    }
                    if (addState.assetState2 == null && token2Id != null) {
                        val a = assets.first { t -> t.token.id == token2Id }
                        addState = addState.copy(
                            assetState2 = buildInitialAssetState(a),
                        )
                    }
                    setTokensFromArgs()
                }
        }
        viewModelScope.launch {
            amount1Flow
                .drop(1)
                .debounce(ViewHelper.debounce)
                .onEach { amount ->
                    amountFrom = amount
                    desired = WithDesired.INPUT
                    onChangedProperty.set(false)
                }.filter {
                    addState.assetState1?.token?.id == SubstrateOptionsProvider.feeAssetId ||
                        !hasXorReminderWarningBeenChecked
                }.onEach {
                    updateTransactionReminderWarningVisibility()
                    hasXorReminderWarningBeenChecked = true
                }.collect()
        }
        viewModelScope.launch {
            amount2Flow
                .drop(1)
                .debounce(ViewHelper.debounce)
                .collectLatest { amount ->
                    amountTo = amount
                    desired = WithDesired.OUTPUT
                    onChangedProperty.set(false)
                }
        }
    }

    override fun onCurrentDestinationChanged(curDest: String) {
        _toolbarState.value?.let { state ->
            _toolbarState.value = state.copy(
                basic = state.basic.copy(
                    title = when (curDest) {
                        LiquidityAddRoutes.start -> R.string.common_supply_liquidity_title
                        LiquidityAddRoutes.slippage -> R.string.slippage
                        LiquidityAddRoutes.confirm -> R.string.add_liquidity_confirmation_title
                        LiquidityAddRoutes.selectToken -> R.string.common_choose_asset
                        else -> R.string.remove_liquidity_title
                    },
                    navIcon = when (curDest) {
                        LiquidityAddRoutes.start -> R.drawable.ic_settings_info
                        else -> R.drawable.ic_arrow_left
                    }
                ),
            )
        }
    }

    override fun onNavIcon() {
        if (currentDestination == LiquidityAddRoutes.start) {
            addState = addState.copy(
                hintVisible = addState.hintVisible.not()
            )
        } else {
            super.onNavIcon()
        }
    }

    override fun onMenuItem(action: Action) {
        this.onBackPressed()
    }

    fun dismissHint() {
        addState = addState.copy(
            hintVisible = addState.hintVisible.not()
        )
    }

    private fun setButtonLoading(loading: Boolean) {
        addState = addState.copy(
            btnState = addState.btnState.copy(
                loading = loading,
            ),
        )
    }

    private fun updateButtonState() {
        val balanceCheckResult = if (amountTo > balance2) {
            false
        } else if (amountFrom > balance1) {
            false
        } else !(SubstrateOptionsProvider.feeAssetId == addToken1 && (amountFrom + networkFee) > balance1)

        val (text, enabled) = when {
            (addState.assetState2?.token == null) -> {
                resourceManager.getString(R.string.choose_tokens) to false
            }

            (addState.assetState1?.token != null && amountFrom == BigDecimal.ZERO) -> {
                resourceManager.getString(R.string.common_enter_amount) to false
            }

            (addState.assetState2?.token != null && amountTo == BigDecimal.ZERO) -> {
                resourceManager.getString(R.string.common_enter_amount) to false
            }

            balanceCheckResult -> {
                resourceManager.getString(R.string.common_supply) to true
            }

            !balanceCheckResult -> {
                resourceManager.getString(R.string.common_insufficient_balance) to false
            }

            else -> {
                resourceManager.getString(R.string.choose_tokens) to false
            }
        }

        addState = addState.copy(
            btnState = addState.btnState.copy(
                text = text,
                enabled = enabled,
            )
        )
    }

    private suspend fun recalculateData() {
        val tokenFrom = addState.assetState1?.token
        val tokenTo = addState.assetState2?.token
        val basedAmount = if (desired == WithDesired.INPUT) amountFrom else amountTo
        val targetAmount = if (desired == WithDesired.INPUT) amountTo else amountFrom

        if (basedAmount.isZero() && targetAmount.isZero()) return
        if (tokenFrom == null || tokenTo == null || basedAmount < BigDecimal.ZERO || targetAmount < BigDecimal.ZERO) {
            return
        }

        setButtonLoading(true)

        tryCatchFinally(
            finally = { setButtonLoading(false) },
            block = {
                val details = poolsInteractor.calcLiquidityDetails(
                    tokenFrom,
                    tokenTo,
                    liquidityData.firstReserves,
                    liquidityData.secondReserves,
                    liquidityData.secondPooled,
                    basedAmount,
                    targetAmount,
                    desired,
                    addState.slippage,
                    pairEnabled,
                    pairPresented
                )

                networkFee = details.networkFee
                liquidityDetails = details
                details.targetAmount.let {
                    if (desired == WithDesired.INPUT) {
                        addState = addState.copy(
                            assetState2 = addState.assetState2?.copy(
                                amount = it,
                                amountFiat = addState.assetState2?.token?.printFiat(
                                    it,
                                    numbersFormatter
                                ).orEmpty(),
                                enabled = true,
                            ),
                        )
                        amountTo = it
                    } else {
                        addState = addState.copy(
                            assetState1 = addState.assetState1?.copy(
                                amount = it,
                                amountFiat = addState.assetState1?.token?.printFiat(
                                    it,
                                    numbersFormatter
                                ).orEmpty(),
                                enabled = true,
                            ),
                        )
                        amountFrom = it
                    }
                }

                updateLiquidityDetails(details)
            }
        )
    }

    private suspend fun updateLiquidityDetails(details: LiquidityDetails) {
        val tokenFrom = addState.assetState1?.token
        val tokenTo = addState.assetState2?.token

        if (tokenFrom == null || tokenTo == null) {
            return
        }

        addState = addState.copy(
            estimated = addState.estimated.copy(
                token1 = tokenFrom.symbol,
                token2 = tokenTo.symbol,
                token1Value = numbersFormatter.formatBigDecimal(
                    liquidityData.firstPooled + amountFrom,
                    DEFAULT_PRECISION
                ),
                token2Value = numbersFormatter.formatBigDecimal(
                    liquidityData.secondPooled + amountTo,
                    DEFAULT_PRECISION
                ),
                shareOfPool = PERCENT_FORMAT.format(
                    numbersFormatter.formatBigDecimal(
                        details.shareOfPool,
                        DEFAULT_PRECISION
                    )
                ),
            ),
            prices = addState.prices.copy(
                pair1 = PER_FORMAT.format(tokenFrom.symbol, tokenTo.symbol),
                pair1Value = numbersFormatter.formatBigDecimal(
                    details.perFirst,
                    DEFAULT_PRECISION
                ),
                pair2 = PER_FORMAT.format(tokenTo.symbol, tokenFrom.symbol),
                pair2Value = numbersFormatter.formatBigDecimal(
                    details.perSecond,
                    DEFAULT_PRECISION
                ),
                fee = NETWORK_FEE_FORMAT.format(
                    numbersFormatter.formatBigDecimal(
                        details.networkFee,
                        NETWORK_FEE_PRECISION
                    ),
                    feeToken().symbol,
                ),
                apy = liquidityData.sbApy?.let { d ->
                    PERCENT_FORMAT.format(
                        numbersFormatter.format(
                            d,
                            DEFAULT_PRECISION
                        )
                    )
                }
            )
        )
    }

    private suspend fun updateTransactionReminderWarningVisibility() =
        with(addState) {
            if (assetState1 == null)
                return@with

            val result = assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = networkFee,
                xorChange = if (assetState1.token.id == SubstrateOptionsProvider.feeAssetId) assetState1.amount else null,
            )

            addState = addState.copy(
                shouldTransactionReminderInsufficientWarningBeShown = result,
            )
        }

    fun onToken1Click() {
        if (assets.isNotEmpty()) {
            viewModelScope.launch {
                val bases = poolsInteractor.getPoolDexList().map { it.tokenId }
                val list = assets.filter { it.token.id in bases && it.token.id != addToken2 }
                addState = addState.copy(
                    selectSearchAssetState = SelectSearchAssetState(
                        filter = "",
                        fullList = mapAssetsToCardState(list, numbersFormatter),
                    ),
                    assetState1 = addState.assetState1?.copy(
                        amount = addState.assetState1?.amount?.nullZero(),
                    ),
                    assetState2 = addState.assetState2?.copy(
                        amount = addState.assetState2?.amount?.nullZero(),
                    ),
                )
            }
        }
    }

    fun onToken2Click() {
        if (assets.isNotEmpty()) {
            viewModelScope.launch {
                val bases = poolsInteractor.getPoolDexList()
                val curBase = bases.find { it.tokenId == addToken1 }
                val list = assets
                    .filter { asset ->
                        asset.token.id.matches(syntheticRegex).not()
                    }
                    .filter { asset ->
                        if (addToken1 == SubstrateOptionsProvider.xstusdTokenId) {
                            asset.token.id != SubstrateOptionsProvider.xstTokenId
                        } else if (addToken1 == SubstrateOptionsProvider.feeAssetId) {
                            asset.token.id != SubstrateOptionsProvider.xstusdTokenId
                        } else {
                            true
                        }
                    }
                    .filter { asset ->
                        val inBases = bases.find { it.tokenId == asset.token.id }
                        if (inBases != null && curBase != null) {
                            inBases.dexId > curBase.dexId
                        } else {
                            asset.token.id != addToken1
                        }
                    }
                addState = addState.copy(
                    selectSearchAssetState = SelectSearchAssetState(
                        filter = "",
                        fullList = mapAssetsToCardState(list, numbersFormatter),
                    ),
                    assetState1 = addState.assetState1?.copy(
                        amount = addState.assetState1?.amount?.nullZero(),
                    ),
                    assetState2 = addState.assetState2?.copy(
                        amount = addState.assetState2?.amount?.nullZero(),
                    ),
                )
            }
        }
    }

    fun onToken1Change(id: String) {
        if (id == addState.assetState1?.token?.id) return
        addState.assetState1?.let { state ->
            val a = assets.first { t -> t.token.id == id }
            addState = addState.copy(
                assetState1 = state.copy(
                    token = a.token,
                    balance = getAssetBalanceText(a),
                    amountFiat = a.token.printFiat(state.amount.orZero(), numbersFormatter),
                ),
            )
            hasXorReminderWarningBeenChecked = false
        }
        setTokensFromArgs()
    }

    fun onToken2Change(id: String) {
        if (id == addState.assetState2?.token?.id) return
        val a = assets.first { t -> t.token.id == id }
        val state = addState.assetState2
        addState = addState.copy(
            assetState2 = state?.copy(
                token = a.token,
                balance = getAssetBalanceText(a),
                amountFiat = a.token.printFiat(state.amount.orZero(), numbersFormatter),
            )
                ?: buildInitialAssetState(a),
        )
        setTokensFromArgs()
    }

    private fun setTokensFromArgs() {
        val token1State = addState.assetState1?.token
        val token2State = addState.assetState2?.token
        if (token1State?.id != addToken1 || token2State?.id != addToken2) {
            cleanUpSubscriptions()
        }
        addToken1 = token1State?.id
        addToken2 = token2State?.id

        assets.find { it.token.id == SubstrateOptionsProvider.feeAssetId }?.let {
            balanceFee = it.balance.transferable
        }

        assets.find { it.token.id == addToken1 }?.let { asset ->
            balance1 = asset.balance.transferable
            addState = addState.copy(
                assetState1 = addState.assetState1?.copy(
                    balance = getAssetBalanceText(asset),
                    token = asset.token,
                )
            )
        }
        assets.find { it.token.id == addToken2 }?.let { asset ->
            balance2 = asset.balance.transferable
            addState = addState.copy(
                assetState2 = addState.assetState2?.copy(
                    balance = getAssetBalanceText(asset),
                    token = asset.token,
                )
            )
        }
        if (token1State != null && token2State != null) {
            subscribePoolChanges(token1State.id, token2State.id)
            subscribeReserves(token1State.id, token2State.id)
        } else {
            onChangedProperty.set(false)
        }
    }

    private fun cleanUpSubscriptions() {
        pairEnabledJob?.cancel()
        pairPresentedJob?.cancel()
        liquidityDataSubscriptionJob?.cancel()
        liquidityData = LiquidityData()
    }

    private fun subscribePoolChanges(tokenFromId: String, tokenToId: String) {
        if (pairEnabledJob == null ||
            pairEnabledJob?.isCancelled == true || pairEnabledJob?.isCompleted == true
        ) {
            pairEnabledJob = poolsInteractor.isPairEnabled(tokenFromId, tokenToId)
                .catch {
                    onError(it)
                }
                .distinctUntilChanged()
                .onEach {
                    pairEnabled = it
                    onChangedProperty.set(false)
                }
                .launchIn(viewModelScope)
        }

        if (pairPresentedJob == null ||
            pairPresentedJob?.isCancelled == true || pairPresentedJob?.isCompleted == true
        ) {
            pairPresentedJob = poolsInteractor.isPairPresentedInNetwork(tokenFromId, tokenToId)
                .catch {
                    onError(it)
                }
                .distinctUntilChanged()
                .onEach {
                    pairPresented = it
                    onChangedProperty.set(false)
                }
                .launchIn(viewModelScope)
        }
    }

    private fun subscribeReserves(baseTokenId: String, tokenToId: String) {
        if (liquidityDataSubscriptionJob == null ||
            liquidityDataSubscriptionJob?.isCancelled == true || liquidityDataSubscriptionJob?.isCompleted == true
        ) {
            liquidityDataSubscriptionJob =
                poolsInteractor.subscribeReservesCache(baseTokenId, tokenToId)
                    .distinctUntilChanged()
                    .debounce(500)
                    .catch {
                        onError(it)
                    }
                    .onEach { localData ->
                        if (localData == null) {
                            fetchLiquidityData()
                        } else {
                            liquidityData = localData
                            addState = addState.copy(
                                pairNotExist = false,
                            )
                        }
                        onChangedProperty.set(false)
                    }
                    .launchIn(viewModelScope)
        }
    }

    private suspend fun fetchLiquidityData() {
        val tokenFrom = addState.assetState1?.token
        val tokenTo = addState.assetState2?.token
        if (tokenFrom == null || tokenTo == null) {
            return
        }

        liquidityData = withContext(coroutineManager.io) {
            poolsInteractor.getLiquidityData(tokenFrom, tokenTo, pairEnabled, pairPresented)
        }
        addState = addState.copy(
            pairNotExist = liquidityData.secondReserves.isZero() && liquidityData.firstReserves.isZero(),
        )
    }

    fun onSlippageClick() {
        addState = addState.copy(
            assetState1 = addState.assetState1?.copy(
                amount = addState.assetState1?.amount?.nullZero(),
            ),
            assetState2 = addState.assetState2?.copy(
                amount = addState.assetState2?.amount?.nullZero(),
            ),
        )
    }

    fun slippageChanged(slippageTolerance: Double) {
        addState = addState.copy(
            slippage = slippageTolerance,
        )
        onChangedProperty.set(false)
    }

    fun onAmount1Change(value: BigDecimal) {
        addState = addState.copy(
            assetState1 = addState.assetState1?.copy(
                amount = value,
                amountFiat = addState.assetState1?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty()
            )
        )
        amount1Flow.value = value
    }

    fun onAmount2Change(value: BigDecimal) {
        addState = addState.copy(
            assetState2 = addState.assetState2?.copy(
                amount = value,
                amountFiat = addState.assetState2?.token?.printFiat(
                    value,
                    numbersFormatter
                ).orEmpty()
            )
        )
        amount2Flow.value = value
    }

    fun onAmount1Focused() {
        if (desired != WithDesired.INPUT) {
            desired = WithDesired.INPUT
        }
    }

    fun onAmount2Focused() {
        if (desired != WithDesired.OUTPUT) {
            desired = WithDesired.OUTPUT
        }
    }

    fun optionSelected(percent: Int) {
        val tokenFrom = addState.assetState1?.token
        val tokenTo = addState.assetState2?.token

        if (desired == WithDesired.INPUT && tokenFrom != null) {
            val amount = PolkaswapFormulas.calculateAmountByPercentage(
                balance1.apply {
                    if (tokenFrom.id == SubstrateOptionsProvider.feeAssetId) this.subtract(
                        networkFee
                    )
                },
                percent.toDouble(), tokenFrom.precision
            )
            addState = addState.copy(
                assetState1 = addState.assetState1?.copy(
                    amountFiat = tokenFrom.printFiat(amount, numbersFormatter),
                    amount = amount,
                )
            )
            amount1Flow.value = amount
        } else if (desired == WithDesired.OUTPUT && tokenTo != null) {
            val amount =
                PolkaswapFormulas.calculateAmountByPercentage(
                    balance2,
                    percent.toDouble(),
                    tokenTo.precision
                )
            addState = addState.copy(
                assetState2 = addState.assetState2?.copy(
                    amountFiat = tokenTo.printFiat(amount, numbersFormatter),
                    amount = amount,
                )
            )
            amount2Flow.value = amount
        }
    }

    fun onConfirmClick() {
        addState.assetState1?.let { state1 ->
            addState.assetState2?.let { state2 ->
                viewModelScope.launch {
                    addState = addState.copy(
                        confirm = addState.confirm.copy(
                            btnState = addState.confirm.btnState.copy(
                                enabled = false,
                                loading = true,
                            )
                        )
                    )
                    var result = ""
                    try {
                        result = poolsInteractor.observeAddLiquidity(
                            state1.token,
                            state2.token,
                            amountFrom,
                            amountTo,
                            pairEnabled,
                            pairPresented,
                            addState.slippage,
                        )
                    } catch (t: Throwable) {
                        onError(t)
                    } finally {
                        addState = addState.copy(
                            confirm = addState.confirm.copy(
                                btnState = addState.confirm.btnState.copy(
                                    enabled = false,
                                    loading = false,
                                ),
                                confirmResult = result.isNotEmpty()
                            )
                        )
                        delay(700)
                        addState = addState.copy(
                            confirm = addState.confirm.copy(
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

    fun onReviewClick() {
        val tokenFrom = addState.assetState1?.token
        val tokenTo = addState.assetState2?.token
        if (tokenFrom == null || tokenTo == null) {
            return
        }
        addState = addState.copy(
            confirm = addState.confirm.copy(
                text = resourceManager.getString(R.string.remove_pool_confirmation_description)
                    .format(addState.slippage),
                btnState = addState.btnState.copy(
                    text = resourceManager.getString(R.string.common_confirm),
                    enabled = true,
                    loading = false,
                ),
            )
        )
    }

    private fun buildInitialAssetState(a: Asset) = AssetAmountInputState(
        token = a.token,
        balance = getAssetBalanceText(a),
        amount = null,
        amountFiat = "",
        enabled = true,
    )

    private fun getAssetBalanceText(asset: Asset) =
        AmountFormat.getAssetBalanceText(asset, numbersFormatter, DEFAULT_PRECISION)
}
