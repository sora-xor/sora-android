package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swapconfirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.HealthChecker
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal

@FlowPreview
class SwapConfirmationViewModel(
    private val router: WalletRouter,
    walletInteractor: WalletInteractor,
    healthChecker: HealthChecker,
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
        const val ROUNDING_SWAP = 9
    }

    private var job: Job? = null

    private val _inputTokenLiveData = MutableLiveData<Token>()
    val inputTokenLiveData: LiveData<Token> = _inputTokenLiveData

    private val _inputAmountLiveData = MutableLiveData<String>()
    val inputAmountLiveData: LiveData<String> = _inputAmountLiveData

    private val _outputTokenLiveData = MutableLiveData<Token>()
    val outputTokenLiveData: LiveData<Token> = _outputTokenLiveData

    private val _outputAmountLiveData = MutableLiveData<String>()
    val outputAmountLiveData: LiveData<String> = _outputAmountLiveData

    private val _per1LiveData = MutableLiveData<String>()
    val per1LiveData: LiveData<String> = _per1LiveData

    private val _per2LiveData = MutableLiveData<String>()
    val per2LiveData: LiveData<String> = _per2LiveData

    private val _per1ValueLiveData = MutableLiveData<String>()
    val per1ValueLiveData: LiveData<String> = _per1ValueLiveData

    private val _per2ValueLiveData = MutableLiveData<String>()
    val per2ValueLiveData: LiveData<String> = _per2ValueLiveData

    private val _minmaxLiveData = MutableLiveData<String>()
    val minmaxLiveData: LiveData<String> = _minmaxLiveData

    private val _minmaxValueLiveData = MutableLiveData<String>()
    val minmaxValueLiveData: LiveData<String> = _minmaxValueLiveData

    private val _liquidityLiveData = MutableLiveData<String>()
    val liquidityLiveData: LiveData<String> = _liquidityLiveData

    private val _networkFeeLiveData = MutableLiveData<String>()
    val networkFeeLiveData: LiveData<String> = _networkFeeLiveData

    private val _descLiveData = MutableLiveData<Pair<String, String>>()
    val descLiveData: LiveData<Pair<String, String>> = _descLiveData

    private val _confirmBtnProgressLiveData = MutableLiveData<Boolean>()
    val confirmBtnProgressLiveData: LiveData<Boolean> = _confirmBtnProgressLiveData

    private val _confirmBtnEnableLiveData = MutableLiveData<Boolean>()
    val confirmBtnEnableLiveData: LiveData<Boolean> = _confirmBtnEnableLiveData

    private val _confirmBtnTitleLiveData = MutableLiveData<String>()
    val confirmBtnTitleLiveData: LiveData<String> = _confirmBtnTitleLiveData

    private val _extrinsicEvent = SingleLiveEvent<Boolean>()
    val extrinsicEvent: LiveData<Boolean> = _extrinsicEvent

    private var fromTokenBalance: BigDecimal? = null
    private var toTokenBalance: BigDecimal? = null
    private var feeTokenBalance: BigDecimal? = null
    private var newDetails: SwapDetails = details

    init {
        updateScreen()
        walletInteractor.subscribeVisibleAssets()
            .catch { onError(it) }
            .onEach {
                fromTokenBalance = it.find { a -> a.token.id == fromToken.id }?.balance?.transferable
                toTokenBalance = it.find { a -> a.token.id == toToken.id }?.balance?.transferable
                feeTokenBalance =
                    it.find { a -> a.token.id == OptionsProvider.feeAssetId }?.balance?.transferable
                onChangeAssetsOrReserves()
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            tryCatch {
                healthChecker.observeHealthState()
                    .collectLatest {
                        if (it) {
                            observePoolReserves()
                        }
                    }
            }
        }
    }

    fun onBackButtonClicked() {
        router.popBackStackFragment()
    }

    private fun observePoolReserves() {
        viewModelScope.launch {
            job?.cancel()
            job = polkaswapInteractor.observePoolReserves()
                .catch { onError(it) }
                .onEach {
                    onChangeAssetsOrReserves()
                }
                .launchIn(viewModelScope)
        }
    }

    fun onConfirmClicked() {
        viewModelScope.launch {
            _confirmBtnEnableLiveData.value = false
            _confirmBtnProgressLiveData.value = true
            var swapResult = false
            try {
                swapResult = polkaswapInteractor.swap(
                    fromToken,
                    toToken,
                    desired,
                    if (desired == WithDesired.INPUT) fromAmount else toAmount,
                    newDetails.minmax,
                    newDetails.networkFee,
                    newDetails.liquidityFee,
                )
            } catch (t: Throwable) {
                onError(t)
            } finally {
                delay(500)
                _extrinsicEvent.value = swapResult
                _confirmBtnProgressLiveData.value = false
                router.returnToPolkaswap()
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
        _inputAmountLiveData.value = numbersFormatter.formatBigDecimal(fromAmount, fromToken.precision)

        _outputTokenLiveData.value = toToken
        _outputAmountLiveData.value = numbersFormatter.formatBigDecimal(toAmount, toToken.precision)

        val per = resourceManager.getString(R.string.common_per)
        _per1LiveData.value = "%s %s %s".format(fromToken.symbol, per, toToken.symbol)
        _per2LiveData.value = "%s %s %s".format(toToken.symbol, per, fromToken.symbol)

        _minmaxLiveData.value =
            if (desired == WithDesired.INPUT) resourceManager.getString(R.string.polkaswap_minimum_received) else resourceManager.getString(
                R.string.polkaswap_maximum_sold
            )

        _networkFeeLiveData.value = "%s %s".format(
            numbersFormatter.formatBigDecimal(newDetails.networkFee, ROUNDING_SWAP),
            feeToken.symbol
        )

        updateDetails()
    }

    private fun updateDetails() {
        newDetails?.let { swapDetails ->
            val (p1, p2) = if (desired == WithDesired.INPUT) swapDetails.per1 to swapDetails.per2 else
                swapDetails.per2 to swapDetails.per1
            _per1ValueLiveData.value = numbersFormatter.formatBigDecimal(p1, ROUNDING_SWAP)
            _per2ValueLiveData.value = numbersFormatter.formatBigDecimal(p2, ROUNDING_SWAP)

            val minmax = "%s %s".format(
                numbersFormatter.formatBigDecimal(swapDetails.minmax, ROUNDING_SWAP),
                if (desired == WithDesired.INPUT) toToken.symbol else fromToken.symbol
            )
            _minmaxValueLiveData.value = minmax
            val desc = resourceManager.getString(
                if (desired == WithDesired.INPUT) R.string.polkaswap_output_estimated else R.string.polkaswap_input_estimated
            ).format(minmax)
            _descLiveData.value = desc to minmax

            _liquidityLiveData.value = "%s %s".format(
                numbersFormatter.formatBigDecimal(swapDetails.liquidityFee, ROUNDING_SWAP),
                feeToken.symbol
            )
        } ?: run {
            _per1ValueLiveData.value = ""
            _per2ValueLiveData.value = ""
            _minmaxValueLiveData.value = ""
            _descLiveData.value = "" to ""
            _liquidityLiveData.value = ""
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
                resourceManager.getString(R.string.polkaswap_insufficient_balance)
                    .format("") to false
            }
            else -> {
                resourceManager.getString(R.string.common_confirm) to true
            }
        }
        _confirmBtnEnableLiveData.value = enabled
        _confirmBtnTitleLiveData.value = text
    }

    /**
     * @return null - can't calculate, empty - ok, not empty - token symbol
     */
    private fun isBalanceOk(): String? {
        return fromTokenBalance?.let { ftb ->
            toTokenBalance?.let { ttb ->
                feeTokenBalance?.let { feetb ->
                    if (fromAmount > BigDecimal.ZERO) {
                        val result = polkaswapInteractor.checkSwapBalances(
                            fromToken = fromToken,
                            fromTokenBalance = ftb,
                            fromAmount = fromAmount,
                            swapFee = newDetails.networkFee,
                            feeBalance = feetb,
                            feeToken = feeToken,
                            toToken = toToken,
                            toTokenBalance = ttb,
                            toAmount = toAmount,
                            desired = desired,
                            swapDetails = newDetails
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

    private suspend fun recalcDetails() {
        val amountToCalc = if (desired == WithDesired.INPUT) fromAmount else toAmount
        if (amountToCalc > BigDecimal.ZERO) {
            _confirmBtnProgressLiveData.value = true
            tryCatchFinally(
                finally = { _confirmBtnProgressLiveData.value = false },
                block = {
                    newDetails = polkaswapInteractor.calcDetails(
                        fromToken,
                        toToken,
                        feeToken,
                        amountToCalc,
                        desired,
                        slippageTolerance,
                    ) ?: newDetails

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
