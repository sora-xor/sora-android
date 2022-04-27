/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swapconfirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model.ButtonState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal

@FlowPreview
class SwapConfirmationViewModel(
    private val router: WalletRouter,
    walletInteractor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val fromToken: Token,
    private val fromAmount: BigDecimal,
    private val toToken: Token,
    private val toAmount: BigDecimal,
    private val desired: WithDesired,
    private val details: SwapDetails,
    private val feeToken: Token,
    private val slippageTolerance: Float,
) : BaseViewModel() {

    companion object {
        const val ROUNDING_SWAP = 7
    }

    private var isExtrinsicSubmitted = false

    private val _inputTokenLiveData = MutableLiveData<Token>()
    val inputTokenLiveData: LiveData<Token> = _inputTokenLiveData

    private val _inputAmountLiveData = MutableLiveData<String>()
    val inputAmountLiveData: LiveData<String> = _inputAmountLiveData

    private val _outputTokenLiveData = MutableLiveData<Token>()
    val outputTokenLiveData: LiveData<Token> = _outputTokenLiveData

    private val _outputAmountLiveData = MutableLiveData<String>()
    val outputAmountLiveData: LiveData<String> = _outputAmountLiveData

    private val _per1LiveData = MutableLiveData<Pair<String, String>>()
    val per1LiveData: LiveData<Pair<String, String>> = _per1LiveData.distinctUntilChanged()

    private val _per2LiveData = MutableLiveData<Pair<String, String>>()
    val per2LiveData: LiveData<Pair<String, String>> = _per2LiveData.distinctUntilChanged()

    private val _minmaxLiveData = MutableLiveData<Pair<String, String?>>()
    val minmaxLiveData: LiveData<Pair<String, String?>> = _minmaxLiveData.distinctUntilChanged()

    private val _liquidityLiveData = MutableLiveData<String?>()
    val liquidityLiveData: LiveData<String?> = _liquidityLiveData

    private val _networkFeeLiveData = MutableLiveData<String?>()
    val networkFeeLiveData: LiveData<String?> = _networkFeeLiveData

    private val _descLiveData = MutableLiveData<Pair<String, String>>()
    val descLiveData: LiveData<Pair<String, String>> = _descLiveData

    private val _confirmButtonState: MutableStateFlow<ButtonState> = MutableStateFlow(ButtonState())
    val confirmButtonState: StateFlow<ButtonState> = _confirmButtonState

    private val _extrinsicEvent = SingleLiveEvent<Boolean>()
    val extrinsicEvent: LiveData<Boolean> = _extrinsicEvent

    private var fromTokenBalance: BigDecimal? = null
    private var toTokenBalance: BigDecimal? = null
    private var feeTokenBalance: BigDecimal? = null
    private var newDetails: SwapDetails? = details

    private var line1Title: String = ""
    private var line2Title: String = ""
    private var line3Title: String = ""

    init {
        updateScreen()
        walletInteractor.subscribeVisibleAssetsOfCurAccount()
            .catch { onError(it) }
            .onEach {
                fromTokenBalance =
                    it.find { a -> a.token.id == fromToken.id }?.balance?.transferable
                toTokenBalance = it.find { a -> a.token.id == toToken.id }?.balance?.transferable
                feeTokenBalance =
                    it.find { a -> a.token.id == OptionsProvider.feeAssetId }?.balance?.transferable
                onChangeAssetsOrReserves()
            }
            .launchIn(viewModelScope)

        polkaswapInteractor.observePoolReserves()
            .catch { onError(it) }
            .onEach {
                onChangeAssetsOrReserves()
            }
            .launchIn(viewModelScope)
    }

    fun onBackButtonClicked() {
        router.popBackStackFragment()
    }

    fun onConfirmClicked() {
        isExtrinsicSubmitted = true
        newDetails?.let { details ->
            viewModelScope.launch {
                _confirmButtonState.value = _confirmButtonState.value.copy(
                    enabled = false,
                    loading = true
                )
                var swapResult = false
                try {
                    swapResult = polkaswapInteractor.swap(
                        fromToken,
                        toToken,
                        desired,
                        if (desired == WithDesired.INPUT) fromAmount else toAmount,
                        details.minmax,
                        details.networkFee,
                        details.liquidityFee,
                    )
                } catch (t: Throwable) {
                    onError(t)
                } finally {
                    delay(500)
                    _extrinsicEvent.value = swapResult
                    setConfirmButtonLoading(false)
                    router.returnToPolkaswap()
                }
            }
        }
    }

    private fun onChangeAssetsOrReserves() {
        viewModelScope.launch {
            recalcDetails()
            toggleConfirmButtonStatus()
        }
    }

    private fun updateScreen() {
        _inputTokenLiveData.value = fromToken
        _inputAmountLiveData.value =
            numbersFormatter.formatBigDecimal(fromAmount, fromToken.precision)

        _outputTokenLiveData.value = toToken
        _outputAmountLiveData.value = numbersFormatter.formatBigDecimal(toAmount, toToken.precision)

        line1Title = "%s / %s".format(fromToken.symbol, toToken.symbol)
        line2Title = "%s / %s".format(toToken.symbol, fromToken.symbol)

        line3Title = if (desired == WithDesired.INPUT) resourceManager.getString(R.string.polkaswap_minimum_received) else resourceManager.getString(
            R.string.polkaswap_maximum_sold
        )

        _networkFeeLiveData.value = "%s %s".format(
            numbersFormatter.formatBigDecimal(details.networkFee, ROUNDING_SWAP),
            feeToken.symbol
        )

        updateDetails()
    }

    private fun updateDetails() {
        newDetails?.let { swapDetails ->
            val (p1, p2) = if (desired == WithDesired.INPUT) swapDetails.per1 to swapDetails.per2 else
                swapDetails.per2 to swapDetails.per1
            _per1LiveData.value = line1Title to numbersFormatter.formatBigDecimal(p1, ROUNDING_SWAP)
            _per2LiveData.value = line2Title to numbersFormatter.formatBigDecimal(p2, ROUNDING_SWAP)

            val minmax1 = numbersFormatter.formatBigDecimal(swapDetails.minmax, ROUNDING_SWAP)
            val minmax2 = if (desired == WithDesired.INPUT) toToken.symbol else fromToken.symbol
            val minmax = "%s %s".format(minmax1, minmax2)
            _minmaxLiveData.value = line3Title to minmax
            val desc = resourceManager.getString(
                if (desired == WithDesired.INPUT) R.string.polkaswap_output_estimated else R.string.polkaswap_input_estimated
            ).format(minmax)
            _descLiveData.value = desc to minmax

            _liquidityLiveData.value = "%s %s".format(
                numbersFormatter.formatBigDecimal(swapDetails.liquidityFee, ROUNDING_SWAP),
                feeToken.symbol
            )
        } ?: run {
            _per1LiveData.value = line1Title to ""
            _per2LiveData.value = line2Title to ""
            _minmaxLiveData.value = line3Title to null
            _descLiveData.value = "" to ""
            _liquidityLiveData.value = null
        }
    }

    private fun toggleConfirmButtonStatus() {
        val ok = isBalanceOk()
        val (text, enabled) = when {
            ok?.isNotEmpty() == true -> {
                resourceManager.getString(R.string.polkaswap_insufficient_balance)
                    .format(ok) to false
            }
            newDetails == null -> {
                resourceManager.getString(R.string.polkaswap_insufficient_liqudity)
                    .format("") to false
            }
            else -> {
                resourceManager.getString(R.string.common_confirm) to true
            }
        }

        _confirmButtonState.value = _confirmButtonState.value.copy(
            text = text,
            enabled = enabled && !isExtrinsicSubmitted
        )
    }

    /**
     * @return null - can't calculate, empty - ok, not empty - token symbol
     */
    private fun isBalanceOk(): String? {
        return fromTokenBalance?.let { ftb ->
            toTokenBalance?.let { ttb ->
                feeTokenBalance?.let { feetb ->
                    newDetails?.let { details ->
                        if (fromAmount > BigDecimal.ZERO) {
                            val result = polkaswapInteractor.checkSwapBalances(
                                fromToken = fromToken,
                                fromTokenBalance = ftb,
                                fromAmount = fromAmount,
                                swapFee = details.networkFee,
                                feeBalance = feetb,
                                feeToken = feeToken,
                                toToken = toToken,
                                toTokenBalance = ttb,
                                toAmount = toAmount,
                                desired = desired,
                                swapDetails = details
                            )
                            when (result) {
                                null -> {
                                    ""
                                }
                                fromToken -> {
                                    fromToken.symbol
                                }
                                else -> {
                                    feeToken.symbol
                                }
                            }
                        } else null
                    }
                }
            }
        }
    }

    private fun setConfirmButtonLoading(loading: Boolean) {
        _confirmButtonState.value = _confirmButtonState.value.copy(loading = loading)
    }

    private suspend fun recalcDetails() {
        val amountToCalc = if (desired == WithDesired.INPUT) fromAmount else toAmount
        if (amountToCalc > BigDecimal.ZERO) {
            setConfirmButtonLoading(true)
            tryCatchFinally(
                finally = { setConfirmButtonLoading(false) },
                block = {
                    newDetails = polkaswapInteractor.calcDetails(
                        fromToken,
                        toToken,
                        feeToken,
                        amountToCalc,
                        desired,
                        slippageTolerance,
                    )

                    updateDetails()
                    newDetails?.amount?.let {
                        if (desired == WithDesired.INPUT) {
                            _outputAmountLiveData.value = numbersFormatter.formatBigDecimal(
                                it,
                                toToken.precision
                            )
                        } else {
                            _inputAmountLiveData.value =
                                numbersFormatter.formatBigDecimal(
                                    it,
                                    fromToken.precision
                                )
                        }
                    }
                },
            )
        }
    }
}
