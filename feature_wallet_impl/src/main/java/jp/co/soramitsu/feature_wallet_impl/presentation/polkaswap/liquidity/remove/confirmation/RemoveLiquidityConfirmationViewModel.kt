/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove.confirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.SingleLiveEvent
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
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateMinAmount
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateTokenPerTokenRate
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.estimateRemovingShareOfPool
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class RemoveLiquidityConfirmationViewModel @Inject constructor(
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private companion object {
        const val ASSET_PRECISION = 8
    }

    private val _fromToken: MutableLiveData<Token> = MutableLiveData()
    val fromToken: LiveData<Token> = _fromToken

    private val _fromAssetAmount: MutableLiveData<String> = MutableLiveData()
    val fromAssetAmount: LiveData<String> = _fromAssetAmount

    private val _toToken: MutableLiveData<Token> = MutableLiveData()
    val toToken: LiveData<Token> = _toToken

    private val _toAssetAmount: MutableLiveData<String> = MutableLiveData()
    val toAssetAmount: LiveData<String> = _toAssetAmount

    private val _descriptionTextLiveData: MutableLiveData<String> = MutableLiveData()
    val descriptionTextLiveData: LiveData<String> = _descriptionTextLiveData

    private val _poolDetailsLiveDetails: MutableLiveData<LiquidityTableDetails> = MutableLiveData()
    val poolDetailsLiveDetails: LiveData<LiquidityTableDetails> = _poolDetailsLiveDetails

    private val _buttonState: MutableStateFlow<ButtonState> = MutableStateFlow(ButtonState())
    val buttonState: StateFlow<ButtonState> = _buttonState

    private val _extrinsicEvent = SingleLiveEvent<Boolean>()
    val extrinsicEvent: LiveData<Boolean> = _extrinsicEvent

    private var balanceFrom = BigDecimal.ZERO
    private var networkFee = BigDecimal.ZERO
    private var fromAmount = BigDecimal.ZERO
    private var toAmount = BigDecimal.ZERO
    private var slippage = 0f
    private var selectedPercent: Double = 0.0
    private var poolData: PoolData? = null

    fun setBundleArgs(
        firstToken: Token,
        firstAmount: BigDecimal,
        secondToken: Token,
        secondAmount: BigDecimal,
        slippage: Float,
        percent: Double,
    ) {
        _fromToken.value = firstToken
        fromAmount = firstAmount
        _fromAssetAmount.value = numbersFormatter.formatBigDecimal(firstAmount, ASSET_PRECISION)
        _toToken.value = secondToken
        toAmount = secondAmount
        _toAssetAmount.value = numbersFormatter.formatBigDecimal(secondAmount, ASSET_PRECISION)
        this.slippage = slippage
        selectedPercent = percent
        _descriptionTextLiveData.value =
            resourceManager.getString(R.string.remove_pool_confirmation_description)
                .format(slippage)

        calcData(firstToken, secondToken)
    }

    fun nextBtnClicked() {
        viewModelScope.launch {
            _fromToken.value?.let { fromToken ->
                _toToken.value?.let { toToken ->
                    poolData?.let { poolData ->
                        val firstAmountMin = calculateMinAmount(fromAmount, slippage.toDouble())
                        val secondAmountMin = calculateMinAmount(toAmount, slippage.toDouble())
                        val desired = poolData.poolProvidersBalance.multiply(BigDecimal.valueOf(selectedPercent / 100))

                        var result = false
                        try {
                            result = polkaswapInteractor.removeLiquidity(
                                fromToken,
                                toToken,
                                desired,
                                firstAmountMin,
                                secondAmountMin,
                                networkFee
                            )
                        } catch (t: Throwable) {
                            onError(t)
                        } finally {
                            delay(500)
                            _extrinsicEvent.value = result
                            router.returnToPolkaswap()
                        }
                    }
                }
            }
        }
    }

    private fun calcData(firstToken: Token, secondToken: Token) {
        viewModelScope.launch {
            networkFee = polkaswapInteractor.fetchRemoveLiquidityNetworkFee(
                firstToken,
                secondToken
            )

            polkaswapInteractor.subscribePoolsCache()
                .collectLatest {
                    val poolData = it.first { it.token.id == _toToken.value?.id }
                    val newPoolShare = estimateRemovingShareOfPool(
                        toAmount,
                        poolData.secondPooled,
                        poolData.secondReserves
                    )

                    val newPoolShareText = if (newPoolShare >= BigDecimal.ZERO) {
                        "${numbersFormatter.formatBigDecimal(newPoolShare, ASSET_PRECISION)}%"
                    } else {
                        ""
                    }

                    val firstPerSecondTitle = "${firstToken.symbol}/${secondToken.symbol}"
                    val firstPerSecond = numbersFormatter.formatBigDecimal(
                        calculateTokenPerTokenRate(fromAmount, toAmount),
                        ASSET_PRECISION
                    )

                    val secondPerFirstTitle = "${secondToken.symbol}/${firstToken.symbol}"
                    val secondPerFirst = numbersFormatter.formatBigDecimal(
                        calculateTokenPerTokenRate(toAmount, fromAmount),
                        ASSET_PRECISION
                    )

                    this@RemoveLiquidityConfirmationViewModel.poolData = poolData
                    checkErrors()

                    _poolDetailsLiveDetails.value = LiquidityTableDetails(
                        firstToken.symbol,
                        numbersFormatter.formatBigDecimal(poolData.xorPooled, ASSET_PRECISION).decimalPartSized(),
                        secondToken.symbol,
                        numbersFormatter.formatBigDecimal(poolData.secondPooled, ASSET_PRECISION).decimalPartSized(),
                        firstPerSecondTitle,
                        firstPerSecond.decimalPartSized(),
                        secondPerFirstTitle,
                        secondPerFirst.decimalPartSized(),
                        resourceManager.getString(R.string.pool_share_title),
                        newPoolShareText,
                        resourceManager.getString(R.string.polkaswap_sbapy),
                        if (poolData.strategicBonusApy != null) "${numbersFormatter.formatBigDecimal(
                            poolData.strategicBonusApy!!,
                            ASSET_PRECISION
                        )}%" else "",
                        resourceManager.getString(R.string.polkaswap_network_fee),
                        numbersFormatter.formatBigDecimal(networkFee, ASSET_PRECISION).decimalPartSized()
                    )
                }
        }

        viewModelScope.launch {
            walletInteractor.subscribeActiveAssetsOfCurAccount()
                .catch { onError(it) }
                .distinctUntilChanged()
                .collectLatest {
                    balanceFrom =
                        it.first { it.token.id == SubstrateOptionsProvider.feeAssetId }.balance.transferable
                    checkErrors()
                }
        }
    }

    private fun checkErrors() {
        poolData?.let { poolData ->
            _fromToken.value?.let {
                val (text, enabled) = when {
                    balanceFrom < networkFee -> {
                        resourceManager.getString(R.string.polkaswap_insufficient_balance)
                            .format(it.symbol) to false
                    }
                    fromAmount > poolData.xorPooled -> {
                        resourceManager.getString(R.string.polkaswap_not_enough_tokens_in_pool) to false
                    }
                    else -> {
                        resourceManager.getString(R.string.common_confirm) to true
                    }
                }

                _buttonState.value = _buttonState.value.copy(
                    text = text,
                    enabled = enabled
                )
            }
        }
    }
}
