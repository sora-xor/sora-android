/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add

import android.text.SpannableString
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.decimalPartSized
import jp.co.soramitsu.common.util.ext.divideBy
import jp.co.soramitsu.common.util.ext.safeDivide
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model.ButtonState
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.model.DetailsItem
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.model.DetailsSection
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.model.MessageAlert
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.estimateAddingShareOfPool
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapMath.isZero
import jp.co.soramitsu.sora.substrate.models.WithDesired
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
@HiltViewModel
class AddLiquidityViewModel @Inject constructor(
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val poolsManager: PoolsManager,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
) : BaseViewModel() {

    private companion object {
        const val INITIAL_SLIPPAGE_TOLERANCE = 0.5f
        const val PER_FORMAT = "%s/%s"
        const val PERCENT_FORMAT = "%s%%"
        const val NETWORK_FEE_FORMAT = "%s %s"
        const val NETWORK_FEE_PRECISION = 8
        const val DEFAULT_PRECISION = 7
        const val SB_APY_POSITION = 2
        val PERCENT_100: BigDecimal = BigDecimal.valueOf(100L)
    }

    private var balanceFrom: BigDecimal = BigDecimal.ZERO
    private var balanceTo: BigDecimal = BigDecimal.ZERO
    private var networkFee: BigDecimal = BigDecimal.ZERO

    private val onChangedProperty = SuspendableProperty<Boolean>(1)

    private val _fromToken: MutableLiveData<Token> = MutableLiveData()
    val fromToken: LiveData<Token> = _fromToken

    private val _fromAssetBalance: MutableLiveData<String> = MutableLiveData()
    val fromAssetBalance: LiveData<String> = _fromAssetBalance

    private val _fromAssetAmount: MutableLiveData<String> = MutableLiveData()
    val fromAssetAmount: LiveData<String> = _fromAssetAmount

    private val _toToken: MutableLiveData<Token> = MutableLiveData()
    val toToken: LiveData<Token> = _toToken

    private val _toAssetBalance: MutableLiveData<String> = MutableLiveData()
    val toAssetBalance: LiveData<String> = _toAssetBalance

    private val _toAssetAmount: MutableLiveData<String> = MutableLiveData()
    val toAssetAmount: LiveData<String> = _toAssetAmount

    private val _showSlippageToleranceBottomSheet: SingleLiveEvent<Float> = SingleLiveEvent()
    val showSlippageToleranceBottomSheet: LiveData<Float> = _showSlippageToleranceBottomSheet

    private val _slippageTolerance = MutableLiveData(INITIAL_SLIPPAGE_TOLERANCE)
    val slippageTolerance: LiveData<Float> = _slippageTolerance

    private val _liquidityDetailsItems: MutableLiveData<List<DetailsSection>> = MutableLiveData()
    val liquidityDetailsItems: LiveData<List<DetailsSection>> = _liquidityDetailsItems

    private val _buttonState: MutableStateFlow<ButtonState> = MutableStateFlow(ButtonState())
    val buttonState: StateFlow<ButtonState> = _buttonState

    private val _pairNotExists: MutableLiveData<Boolean> = MutableLiveData(false)
    val pairNotExists: LiveData<Boolean> = _pairNotExists

    private var amountFrom: BigDecimal = BigDecimal.ZERO
    private var amountTo: BigDecimal = BigDecimal.ZERO
    private var desired: WithDesired = WithDesired.INPUT
    private var liquidityData: LiquidityData = LiquidityData()
    private var liquidityDataSubscriptionJob: Job? = null
    private var poolChangesSubscriptionJob: Job? = null
    private var liquidityDetails: LiquidityDetails? = null

    private var pairEnabled: Boolean = true
    private var pairPresented: Boolean = true

    init {
        viewModelScope.launch {
            tryCatch {
                val asset = walletInteractor.getAssetOrThrow(SubstrateOptionsProvider.feeAssetId)
                _fromToken.value = asset.token
                _fromAssetBalance.value = numbersFormatter.formatBigDecimal(
                    asset.balance.transferable,
                    DEFAULT_PRECISION
                )
            }
        }

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

        _buttonState.value = ButtonState(
            text = resourceManager.getString(R.string.choose_tokens)
        )
        subscribeToAssets()
        poolsManager.bind()
    }

    private fun subscribeToAssets() {
        viewModelScope.launch {
            walletInteractor.subscribeActiveAssetsOfCurAccount()
                .catch { onError(it) }
                .distinctUntilChanged()
                .collectLatest(::updateData)
        }
    }

    private fun updateData(assets: List<Asset>) {
        assets.find { it.token.id == SubstrateOptionsProvider.feeAssetId }?.let { asset ->
            balanceFrom = asset.balance.transferable
            _fromToken.value = asset.token
            _fromAssetBalance.value = numbersFormatter.formatBigDecimal(
                asset.balance.transferable,
                DEFAULT_PRECISION
            )
            _fromAssetAmount.value = numbersFormatter.formatBigDecimal(
                amountFrom,
                asset.token.precision
            )
        }
        assets.find { it.token.id == toToken.value?.id }?.let { asset ->
            balanceTo = asset.balance.transferable
            _toToken.value = asset.token
            _toAssetBalance.value = numbersFormatter.formatBigDecimal(
                asset.balance.transferable,
                DEFAULT_PRECISION
            )
            _toAssetAmount.value = numbersFormatter.formatBigDecimal(
                amountTo,
                asset.token.precision
            )
        }
    }

    private fun setButtonLoading(loading: Boolean) {
        _buttonState.value = _buttonState.value.copy(loading = loading)
    }

    private fun updateButtonState() {
        val balanceCheckResult = checkBalance()
        val (text, enabled) = when {
            (_toToken.value == null) -> {
                resourceManager.getString(R.string.choose_tokens) to false
            }

            (_fromToken.value != null && amountFrom == BigDecimal.ZERO) -> {
                resourceManager.getString(R.string.common_enter_amount) to false
            }

            (_toToken.value != null && amountTo == BigDecimal.ZERO) -> {
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

        _buttonState.value = _buttonState.value.copy(
            text = text,
            enabled = enabled
        )
    }

    private fun checkBalance(): Boolean = polkaswapInteractor.checkLiquidityBalance(
        balanceFrom,
        amountFrom,
        balanceTo,
        amountTo,
        networkFee
    )

    private suspend fun recalculateData() {
        val tokenFrom = _fromToken.value
        val tokenTo = _toToken.value
        val basedAmount = if (desired == WithDesired.INPUT) amountFrom else amountTo
        val targetAmount = if (desired == WithDesired.INPUT) amountTo else amountFrom

        if (tokenFrom == null || tokenTo == null || basedAmount <= BigDecimal.ZERO) {
            return
        }

        if (_pairNotExists.value == true) {
            return
        }

        setButtonLoading(true)

        tryCatchFinally(
            finally = { setButtonLoading(false) },
            block = {
                val details = polkaswapInteractor.calcLiquidityDetails(
                    tokenFrom,
                    tokenTo,
                    liquidityData.firstReserves,
                    liquidityData.secondReserves,
                    liquidityData.secondPooled,
                    basedAmount,
                    targetAmount,
                    desired,
                    slippageTolerance.value ?: INITIAL_SLIPPAGE_TOLERANCE,
                    pairEnabled,
                    pairPresented
                )

                networkFee = details.networkFee
                liquidityDetails = details
                details.targetAmount.let {
                    if (desired == WithDesired.INPUT) {
                        _toAssetAmount.value = numbersFormatter.formatBigDecimal(
                            it,
                            tokenTo.precision
                        )
                        amountTo = it
                    } else {
                        _fromAssetAmount.value =
                            numbersFormatter.formatBigDecimal(
                                it,
                                tokenFrom.precision
                            )
                        amountFrom = it
                    }
                }

                updateLiquidityDetails(details)
            }
        )
    }

    private fun updateLiquidityDetails(details: LiquidityDetails) {
        val tokenFrom = _fromToken.value
        val tokenTo = _toToken.value

        if (tokenFrom == null || tokenTo == null) {
            return
        }

        val positionDetails = DetailsSection(
            title = resourceManager.getString(R.string.polkaswap_your_position),
            items = listOf(
                DetailsItem(
                    tokenFrom.symbol,
                    numbersFormatter.formatBigDecimal(
                        liquidityData.firstPooled + amountFrom,
                        DEFAULT_PRECISION
                    ).decimalPartSized()
                ),
                DetailsItem(
                    tokenTo.symbol,
                    numbersFormatter.formatBigDecimal(
                        liquidityData.secondPooled + amountTo,
                        DEFAULT_PRECISION
                    ).decimalPartSized()
                ),
                DetailsItem(
                    resourceManager.getString(R.string.pool_share_title),
                    SpannableString.valueOf(
                        PERCENT_FORMAT.format(
                            numbersFormatter.formatBigDecimal(
                                details.shareOfPool,
                                DEFAULT_PRECISION
                            )
                        )
                    )
                )
            )
        )

        val feesDetails = DetailsSection(
            title = resourceManager.getString(R.string.polkaswap_info_prices_and_fees),
            items = mutableListOf(
                DetailsItem(
                    PER_FORMAT.format(tokenFrom.symbol, tokenTo.symbol),
                    numbersFormatter.formatBigDecimal(
                        details.perFirst,
                        DEFAULT_PRECISION
                    ).decimalPartSized()
                ),
                DetailsItem(
                    PER_FORMAT.format(tokenTo.symbol, tokenFrom.symbol),
                    numbersFormatter.formatBigDecimal(
                        details.perSecond,
                        DEFAULT_PRECISION
                    ).decimalPartSized()
                ),
                DetailsItem(
                    resourceManager.getString(R.string.polkaswap_network_fee),
                    NETWORK_FEE_FORMAT.format(
                        numbersFormatter.formatBigDecimal(
                            details.networkFee,
                            NETWORK_FEE_PRECISION
                        ),
                        tokenFrom.symbol
                    ).decimalPartSized(ticker = tokenFrom.symbol),
                    messageAlert = MessageAlert(
                        title = R.string.polkaswap_network_fee,
                        message = R.string.polkaswap_network_fee_info,
                        positiveButton = android.R.string.ok
                    )
                )
            ).apply {
                liquidityData.sbApy?.let { sbApy ->
                    add(
                        SB_APY_POSITION,
                        DetailsItem(
                            resourceManager.getString(R.string.polkaswap_sbapy),
                            PERCENT_FORMAT.format(
                                numbersFormatter.format(
                                    sbApy,
                                    DEFAULT_PRECISION
                                )
                            )
                                .decimalPartSized()
                        )
                    )
                }
            }
        )

        _liquidityDetailsItems.value = listOf(positionDetails, feesDetails)
    }

    fun setTokensFromArgs(tokenFrom: Token, tokenTo: Token? = null) {
        if (_toToken.value != tokenTo) {
            cleanUpSubscriptions()
        }

        _fromToken.value = tokenFrom
        _toToken.value = tokenTo

        fetchAssetData()
        fetchNetworkFee()

        if (tokenTo != null) {
            subscribePoolChanges(tokenFrom.id, tokenTo.id)
            subscribeReserves(tokenTo.id)
        } else {
            onChangedProperty.set(false)
        }
    }

    private fun cleanUpSubscriptions() {
        poolChangesSubscriptionJob?.cancel()
        liquidityDataSubscriptionJob?.cancel()
        liquidityData = LiquidityData()
    }

    private fun fetchAssetData() {
        viewModelScope.launch {
            val assets = walletInteractor.getActiveAssets()
            updateData(assets)
        }
    }

    private fun fetchNetworkFee() {
        val tokenFrom = _fromToken.value
        val tokenTo = _toToken.value

        if (tokenFrom == null || tokenTo == null) {
            return
        }

        viewModelScope.launch {
            networkFee = polkaswapInteractor.fetchAddLiquidityNetworkFee(
                tokenFrom,
                tokenTo,
                amountFrom,
                amountTo,
                pairEnabled,
                pairPresented,
                slippageTolerance.value ?: INITIAL_SLIPPAGE_TOLERANCE
            )
        }
    }

    private fun subscribePoolChanges(tokenFromId: String, tokenToId: String) {
        polkaswapInteractor.isPairEnabled(tokenFromId, tokenToId)
            .catch {
                onError(it)
            }
            .distinctUntilChanged()
            .onEach {
                pairEnabled = it
                onChangedProperty.set(false)
            }
            .launchIn(viewModelScope)

        polkaswapInteractor.isPairPresentedInNetwork(tokenToId)
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

    private fun subscribeReserves(tokenToId: String) {
        liquidityDataSubscriptionJob = polkaswapInteractor.subscribeReservesCache(tokenToId)
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
                    _pairNotExists.value = false
                    onChangedProperty.set(false)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun fetchLiquidityData() {
        val tokenFrom = _fromToken.value
        val tokenTo = _toToken.value

        if (tokenFrom == null || tokenTo == null) {
            return
        }

        viewModelScope.launch {
            liquidityData =
                polkaswapInteractor.getLiquidityData(tokenFrom, tokenTo, pairEnabled, pairPresented)
            _pairNotExists.value =
                liquidityData.secondReserves.isZero() && liquidityData.firstReserves.isZero()
            onChangedProperty.set(false)
        }
    }

    fun slippageToleranceClicked() {
        _slippageTolerance.value?.let {
            _showSlippageToleranceBottomSheet.value = it
        }
    }

    fun slippageChanged(slippageTolerance: Float) {
        _slippageTolerance.value = slippageTolerance
        onChangedProperty.set(false)
    }

    fun onChooseToken() {
        router.showSelectToken(
            AssetListMode.SELECT_FOR_LIQUIDITY,
            SubstrateOptionsProvider.feeAssetId
        )
    }

    fun fromAmountChanged(amount: BigDecimal) {
        amountFrom = amount
        _fromToken.value?.let { token ->
            _fromAssetAmount.value = numbersFormatter.formatBigDecimal(
                amountFrom,
                token.precision
            )
        }
        desired = WithDesired.INPUT
        onChangedProperty.set(false)
    }

    fun toAmountChanged(amount: BigDecimal) {
        amountTo = amount
        _toToken.value?.let { token ->
            _toAssetAmount.value = numbersFormatter.formatBigDecimal(
                amountTo,
                token.precision
            )
        }
        desired = WithDesired.OUTPUT
        onChangedProperty.set(false)
    }

    fun fromAmountFocused() {
        if (desired != WithDesired.INPUT) {
            desired = WithDesired.INPUT
        }
    }

    fun toAmountFocused() {
        if (desired != WithDesired.OUTPUT) {
            desired = WithDesired.OUTPUT
        }
    }

    fun optionSelected(percent: Int) {
        val tokenFrom = _fromToken.value
        val tokenTo = _toToken.value

        if (desired == WithDesired.INPUT && tokenFrom != null) {
            val amount = balanceFrom
                .subtract(networkFee)
                .multiply(
                    percent.toBigDecimal()
                        .divideBy(PERCENT_100, tokenFrom.precision)
                )
            fromAmountChanged(amount)
        } else if (desired == WithDesired.OUTPUT && tokenTo != null) {
            val amount = balanceTo
                .multiply(
                    percent.toBigDecimal()
                        .divideBy(PERCENT_100, tokenTo.precision)
                )
            toAmountChanged(amount)
        }
    }

    fun onConfirmation() {
        val tokenFrom = _fromToken.value
        val tokenTo = _toToken.value
        val shareOfPool = estimateAddingShareOfPool(
            amountFrom,
            liquidityData.secondPooled,
            liquidityData.secondReserves
        )
        val details = LiquidityDetails(
            baseAmount = amountFrom,
            targetAmount = amountTo,
            perFirst = amountFrom.safeDivide(amountTo, DEFAULT_PRECISION),
            perSecond = amountTo.safeDivide(amountFrom, DEFAULT_PRECISION),
            networkFee = networkFee,
            shareOfPool = shareOfPool,
            pairPresented = pairPresented,
            pairEnabled = pairEnabled
        )

        if (tokenFrom == null || tokenTo == null) {
            return
        }

        router.confirmAddLiquidity(
            tokenFrom,
            tokenTo,
            _slippageTolerance.value ?: INITIAL_SLIPPAGE_TOLERANCE,
            details
        )
    }

    override fun onCleared() {
        poolsManager.unbind()
        super.onCleared()
    }
}
