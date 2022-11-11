/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.decimalPartSized
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model.ButtonState
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model.LiquidityTableDetails
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateAmountByPercentage
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateOneAmountFromAnother
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateShareOfPoolFromAmount
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateTokenPerTokenRate
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.estimateRemovingShareOfPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class RemoveLiquidityViewModel @Inject constructor(
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private companion object {
        const val ASSET_PRECISION = 8
        const val INITIAL_SLIPPAGE_TOLERANCE = 0.5f
    }

    private val _fromToken: MutableLiveData<Token> = MutableLiveData()
    val fromToken: LiveData<Token> = _fromToken

    private val _fromAssetAmount: MutableLiveData<String> = MutableLiveData()
    val fromAssetAmount: LiveData<String> = _fromAssetAmount

    private val _toToken: MutableLiveData<Token> = MutableLiveData()
    val toToken: LiveData<Token> = _toToken

    private val _toAssetBalance: MutableLiveData<String> = MutableLiveData()
    val toAssetBalance: LiveData<String> = _toAssetBalance

    private val _toAssetAmount: MutableLiveData<String> = MutableLiveData()
    val toAssetAmount: LiveData<String> = _toAssetAmount

    private val _priceDetailsTitles: MutableLiveData<Pair<String, String>> = MutableLiveData()
    val priceDetailsTitles: LiveData<Pair<String, String>> = _priceDetailsTitles

    private val _sliderPercent: MutableLiveData<Int> = MutableLiveData()
    val sliderPercent: LiveData<Int> = _sliderPercent

    private val _details: MutableLiveData<LiquidityTableDetails> = MutableLiveData()
    val details: LiveData<LiquidityTableDetails> = _details

    private val _showSlippageToleranceBottomSheet: MutableLiveData<Float> = MutableLiveData()
    val showSlippageToleranceBottomSheet: LiveData<Float> = _showSlippageToleranceBottomSheet

    private val _slippageToleranceLiveData = MutableLiveData(INITIAL_SLIPPAGE_TOLERANCE)
    val slippageToleranceLiveData: LiveData<Float> = _slippageToleranceLiveData

    private val _buttonState: MutableStateFlow<ButtonState> =
        MutableStateFlow(ButtonState(text = resourceManager.getString(R.string.common_enter_amount)))
    val buttonState: StateFlow<ButtonState> = _buttonState

    private var amountFrom = BigDecimal.ZERO
    private var balanceFrom = BigDecimal.ZERO
    private var amountTo = BigDecimal.ZERO
    private var networkFee = BigDecimal.ZERO
    private var poolData: PoolData? = null

    fun setTokensFromArgs(tokenFrom: Token, tokenTo: Token) {
        _fromToken.value = tokenFrom
        _toToken.value = tokenTo

        _priceDetailsTitles.value =
            "${tokenFrom.symbol}/${tokenTo.symbol}" to "${tokenTo.symbol}/${tokenFrom.symbol}"

        viewModelScope.launch {
            polkaswapInteractor.subscribePoolCache(tokenFrom.id, tokenTo.id)
                .collectLatest { poolData ->
                    this@RemoveLiquidityViewModel.poolData = poolData
                    sliderPercent.value?.let {
                        amountFrom = calculateAmountByPercentage(
                            poolData.basePooled,
                            it,
                            tokenFrom.precision
                        )
                        amountTo = calculateAmountByPercentage(
                            poolData.secondPooled,
                            it,
                            tokenTo.precision
                        )
                    }
                    recalcDetails()
                }
        }

        viewModelScope.launch {
            networkFee = polkaswapInteractor.fetchRemoveLiquidityNetworkFee(
                tokenFrom,
                tokenTo
            )

            walletInteractor.subscribeActiveAssetsOfCurAccount()
                .catch { onError(it) }
                .distinctUntilChanged()
                .collectLatest { assets ->
                    assets.find { it.token.id == fromToken.value?.id }?.let { asset ->
                        _fromToken.value = asset.token
                        _fromAssetAmount.value = numbersFormatter.formatBigDecimal(
                            amountFrom,
                            asset.token.precision
                        )

                        balanceFrom = asset.balance.transferable
                    }
                    assets.find { it.token.id == toToken.value?.id }?.let { asset ->
                        _toToken.value = asset.token
                        _toAssetBalance.value = numbersFormatter.formatBigDecimal(
                            asset.balance.transferable
                        )
                        _toAssetAmount.value = numbersFormatter.formatBigDecimal(
                            amountTo,
                            asset.token.precision
                        )
                    }

                    recalcDetails()
                }
        }
    }

    fun slippageToleranceClicked() {
        _slippageToleranceLiveData.value?.let {
            _showSlippageToleranceBottomSheet.value = it
        }
    }

    fun slippageChanged(slippageTolerance: Float) {
        _slippageToleranceLiveData.value = slippageTolerance
    }

    fun onSliderChanged(value: Int) {
        poolData?.let { poolData ->
            _fromToken.value?.let { fromToken ->
                _toToken.value?.let { toToken ->
                    if (value == 100) {
                        amountFrom = poolData.basePooled
                        amountTo = poolData.secondPooled
                    } else {
                        amountFrom =
                            calculateAmountByPercentage(
                                poolData.basePooled,
                                value,
                                fromToken.precision
                            )
                        amountTo =
                            calculateAmountByPercentage(
                                poolData.secondPooled,
                                value,
                                toToken.precision
                            )
                    }
                }
            }
            _sliderPercent.value = value
            recalcDetails()
        }
    }

    fun fromAmountChanged(fromAmount: BigDecimal) {
        poolData?.let {
            amountFrom = if (fromAmount <= it.basePooled) fromAmount else it.basePooled
            amountTo = calculateOneAmountFromAnother(amountFrom, it.basePooled, it.secondPooled)
            recalcPercentAmount()
            recalcDetails()
        }
    }

    fun toAmountChanged(toAmount: BigDecimal) {
        poolData?.let {
            amountTo = if (toAmount <= it.secondPooled) toAmount else it.secondPooled
            amountFrom = calculateOneAmountFromAnother(amountTo, it.secondPooled, it.basePooled)
            recalcPercentAmount()
            recalcDetails()
        }
    }

    fun nextButtonClicked() {
        _fromToken.value?.let { fromToken ->
            _toToken.value?.let { toToken ->
                _slippageToleranceLiveData.value?.let { slippage ->
                    router.showRemoveLiquidityConfirmation(
                        fromToken,
                        amountFrom,
                        toToken,
                        amountTo,
                        slippage,
                        _sliderPercent.value?.toDouble() ?: 0.0
                    )
                }
            }
        }
    }

    private fun recalcPercentAmount() {
        poolData?.let {
            _sliderPercent.value = calculateShareOfPoolFromAmount(amountFrom, it.basePooled).toInt()
        }
    }

    private fun recalcDetails() {
        poolData?.let { poolData ->
            _fromToken.value?.let { firstToken ->
                _toToken.value?.let { secondToken ->
                    val newPoolshare =
                        if (amountFrom != BigDecimal.ZERO && amountTo != BigDecimal.ZERO) {
                            "${
                            numbersFormatter.formatBigDecimal(
                                estimateRemovingShareOfPool(
                                    amountTo,
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
                        calculateTokenPerTokenRate(poolData.basePooled, poolData.secondPooled),
                        ASSET_PRECISION
                    )

                    val secondPerFirst = numbersFormatter.formatBigDecimal(
                        calculateTokenPerTokenRate(poolData.secondPooled, poolData.basePooled),
                        ASSET_PRECISION
                    )

                    val networkFeeString = "$networkFee ${firstToken.symbol}"
                    _details.value =
                        LiquidityTableDetails(
                            firstToken.symbol,
                            numbersFormatter.formatBigDecimal(
                                poolData.basePooled - amountFrom,
                                ASSET_PRECISION
                            ).decimalPartSized(),
                            secondToken.symbol,
                            numbersFormatter.formatBigDecimal(
                                poolData.secondPooled - amountTo,
                                ASSET_PRECISION
                            ).decimalPartSized(),
                            "${firstToken.symbol}/${secondToken.symbol}",
                            firstPerSecond.decimalPartSized(ticker = firstToken.symbol),
                            "${secondToken.symbol}/${firstToken.symbol}",
                            secondPerFirst.decimalPartSized(ticker = secondToken.symbol),
                            resourceManager.getString(R.string.pool_share_title),
                            newPoolshare,
                            resourceManager.getString(R.string.polkaswap_sbapy),
                            if (poolData.strategicBonusApy != null) "${
                            numbersFormatter.formatBigDecimal(
                                poolData.strategicBonusApy!!,
                                ASSET_PRECISION
                            )
                            }%" else "",
                            resourceManager.getString(R.string.polkaswap_network_fee),
                            networkFeeString.decimalPartSized(ticker = firstToken.symbol)
                        )

                    _fromAssetAmount.value = numbersFormatter.formatBigDecimal(
                        amountFrom,
                        firstToken.precision
                    )

                    _toAssetAmount.value = numbersFormatter.formatBigDecimal(
                        amountTo,
                        secondToken.precision
                    )

                    checkErrors()
                }
            }
        }
    }

    private fun checkErrors() {
        _fromToken.value?.let {
            val (text, enabled) = when {
                amountFrom.compareTo(BigDecimal.ZERO) == 0 || amountTo.compareTo(BigDecimal.ZERO) == 0 -> {
                    resourceManager.getString(R.string.common_enter_amount) to false
                }
                balanceFrom < networkFee -> {
                    resourceManager.getString(R.string.polkaswap_insufficient_balance)
                        .format(it.symbol) to false
                }
                else -> {
                    resourceManager.getString(R.string.pool_button_remove) to true
                }
            }

            _buttonState.value = _buttonState.value.copy(
                text = text,
                enabled = enabled
            )
        }
    }
}
