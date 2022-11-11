/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.view.ViewHelper
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model.ButtonState
import jp.co.soramitsu.sora.substrate.models.WithDesired
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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

@FlowPreview
@ExperimentalCoroutinesApi
class ConfirmAddLiquidityViewModel @AssistedInject constructor(
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val poolsManager: PoolsManager,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    @Assisted("tokenFrom") private val tokenFrom: Token,
    @Assisted("tokenTo") private val tokenTo: Token,
    @Assisted private val slippageTolerance: Float,
    @Assisted private var liquidityDetails: LiquidityDetails
) : BaseViewModel() {

    @AssistedFactory
    interface ConfirmAddLiquidityViewModelFactory {
        fun create(
            @Assisted("tokenFrom") tokenFrom: Token,
            @Assisted("tokenTo") tokenTo: Token,
            slippageTolerance: Float,
            liquidityDetails: LiquidityDetails
        ): ConfirmAddLiquidityViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: ConfirmAddLiquidityViewModelFactory,
            tokenFrom: Token,
            tokenTo: Token,
            slippageTolerance: Float,
            liquidityDetails: LiquidityDetails,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(tokenFrom, tokenTo, slippageTolerance, liquidityDetails) as T
            }
        }

        private const val SHARE_OF_POOL_FORMAT = "%s%%"
        private const val DEFAULT_PRECISION = 8
    }

    private val onChangedProperty = SuspendableProperty<Boolean>(1)

    private val _shareOfPool: MutableLiveData<String> = MutableLiveData(
        SHARE_OF_POOL_FORMAT.format(
            numbersFormatter.formatBigDecimal(
                liquidityDetails.shareOfPool,
                DEFAULT_PRECISION
            )
        )
    )
    val shareOfPool: LiveData<String> = _shareOfPool

    private val _firstDeposit: MutableLiveData<String> = MutableLiveData(
        numbersFormatter.formatBigDecimal(
            liquidityDetails.baseAmount,
            DEFAULT_PRECISION
        )
    )
    val firstDeposit: LiveData<String> = _firstDeposit

    private val _secondDeposit: MutableLiveData<String> = MutableLiveData(
        numbersFormatter.formatBigDecimal(
            liquidityDetails.targetAmount,
            DEFAULT_PRECISION
        )
    )
    val secondDeposit: LiveData<String> = _secondDeposit

    private val _firstPerSecond: MutableLiveData<String> = MutableLiveData(
        numbersFormatter.formatBigDecimal(
            liquidityDetails.perFirst,
            DEFAULT_PRECISION
        )
    )
    val firstPerSecond: LiveData<String> = _firstPerSecond

    private val _secondPerFirst: MutableLiveData<String> = MutableLiveData(
        numbersFormatter.formatBigDecimal(
            liquidityDetails.perSecond,
            DEFAULT_PRECISION
        )
    )
    val secondPerFirst: LiveData<String> = _secondPerFirst

    private val _strategicBonusAPY: MutableLiveData<String> = MutableLiveData()
    val strategicBonusAPY: LiveData<String> = _strategicBonusAPY

    private val _buttonState: MutableStateFlow<ButtonState> = MutableStateFlow(
        ButtonState(
            text = resourceManager.getString(R.string.common_confirm),
            enabled = true
        )
    )
    val buttonState: StateFlow<ButtonState> = _buttonState

    private val _extrinsicEvent = SingleLiveEvent<Boolean>()
    val extrinsicEvent: LiveData<Boolean> = _extrinsicEvent

    private var amountFrom: BigDecimal = liquidityDetails.baseAmount
    private var amountTo: BigDecimal = liquidityDetails.targetAmount

    private var balanceFrom: BigDecimal = BigDecimal.ZERO
    private var balanceTo: BigDecimal = BigDecimal.ZERO

    private var liquidityData: LiquidityData = LiquidityData()
    private var networkFee: BigDecimal = BigDecimal.ZERO

    private var pairEnabled: Boolean = liquidityDetails.pairEnabled
    private var pairPresented: Boolean = liquidityDetails.pairPresented

    init {
        poolsManager.bind()
        polkaswapInteractor.subscribeReservesCache(tokenFrom.id, tokenTo.id)
            .distinctUntilChanged()
            .debounce(500)
            .catch {
                onError(it)
            }
            .onEach { liquidityData ->
                if (liquidityData == null) {
                    fetchPoolData()
                } else {
                    this.liquidityData = liquidityData
                }

                onChangedProperty.set(false)
            }
            .launchIn(viewModelScope)

        onChangedProperty.observe()
            .debounce(ViewHelper.debounce)
            .catch {
                onError(it)
            }
            .onEach {
                recalculateData()
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            polkaswapInteractor.getPoolStrategicBonusAPY(tokenTo.id)?.let { strategicBonusAPY ->
                _strategicBonusAPY.value = numbersFormatter.format(
                    strategicBonusAPY,
                    DEFAULT_PRECISION
                )
            }
        }

        subscribeToAssets()
        subscribePoolChanges(tokenFrom.id, tokenTo.id)
    }

    private fun subscribeToAssets() {
        viewModelScope.launch {
            walletInteractor.subscribeActiveAssetsOfCurAccount()
                .catch { onError(it) }
                .distinctUntilChanged()
                .collectLatest { assets ->
                    assets.find { it.token.id == SubstrateOptionsProvider.feeAssetId }
                        ?.let { asset ->
                            balanceFrom = asset.balance.transferable
                            onChangedProperty.set(false)
                        }
                    assets.find { it.token.id == tokenTo.id }?.let { asset ->
                        balanceTo = asset.balance.transferable
                        onChangedProperty.set(false)
                    }
                }
        }
    }

    private fun subscribePoolChanges(tokenFromId: String, tokenToId: String) {
        viewModelScope.launch {
            polkaswapInteractor.isPairEnabled(tokenFromId, tokenToId)
                .distinctUntilChanged()
                .collectLatest {
                    pairEnabled = it
                    onChangedProperty.set(false)
                }
        }

        viewModelScope.launch {
            polkaswapInteractor.isPairPresentedInNetwork(tokenFromId, tokenToId)
                .distinctUntilChanged()
                .collectLatest {
                    pairPresented = it
                    onChangedProperty.set(false)
                }
        }
    }

    private fun fetchPoolData() {
        viewModelScope.launch {
            liquidityData =
                polkaswapInteractor.getLiquidityData(tokenFrom, tokenTo, pairEnabled, pairPresented)
        }
    }

    private suspend fun recalculateData() {
        if (liquidityData.secondReserves == BigDecimal.ZERO &&
            liquidityData.firstReserves == BigDecimal.ZERO
        ) {
            return
        }

        setButtonLoading(loading = true)

        tryCatchFinally(
            finally = { setButtonLoading(loading = false) },
            block = {
                liquidityDetails = polkaswapInteractor.calcLiquidityDetails(
                    tokenFrom,
                    tokenTo,
                    liquidityData.firstReserves,
                    liquidityData.secondReserves,
                    liquidityData.secondPooled,
                    amountFrom,
                    amountTo,
                    WithDesired.INPUT,
                    slippageTolerance,
                    pairEnabled,
                    pairPresented
                )

                networkFee =
                    polkaswapInteractor.fetchAddLiquidityNetworkFee(
                        tokenFrom,
                        tokenTo,
                        amountFrom,
                        amountTo,
                        pairEnabled,
                        pairPresented,
                        slippageTolerance
                    )

                updateState()
            }
        )
    }

    private fun updateState() {
        amountFrom = liquidityDetails.baseAmount
        amountTo = liquidityDetails.targetAmount
        _secondDeposit.value = numbersFormatter.formatBigDecimal(
            amountTo,
            DEFAULT_PRECISION
        )

        _firstPerSecond.value = numbersFormatter.formatBigDecimal(
            liquidityDetails.perFirst,
            DEFAULT_PRECISION
        )

        _secondPerFirst.value = numbersFormatter.formatBigDecimal(
            liquidityDetails.perSecond,
            DEFAULT_PRECISION
        )

        _shareOfPool.value = SHARE_OF_POOL_FORMAT.format(
            numbersFormatter.formatBigDecimal(
                liquidityDetails.shareOfPool,
                DEFAULT_PRECISION
            )
        )

        updateButtonState()
    }

    private fun setButtonLoading(loading: Boolean) {
        _buttonState.value = buttonState.value.copy(
            loading = loading,
            enabled = !loading
        )
    }

    private fun updateButtonState() {
        val balanceCheckSucceeded = polkaswapInteractor.checkLiquidityBalance(
            balanceFrom,
            amountFrom,
            balanceTo,
            amountTo,
            networkFee
        )

        val buttonText = if (balanceCheckSucceeded) {
            resourceManager.getString(R.string.common_confirm)
        } else {
            resourceManager.getString(R.string.common_insufficient_balance)
        }

        _buttonState.value = _buttonState.value.copy(
            text = buttonText,
            loading = false,
            enabled = balanceCheckSucceeded
        )
    }

    fun onConfirm() {
        viewModelScope.launch {
            setButtonLoading(loading = true)

            tryCatchFinally(
                finally = { setButtonLoading(loading = false) },
                block = {
                    val succeed = polkaswapInteractor.observeAddLiquidity(
                        tokenFrom,
                        tokenTo,
                        amountFrom,
                        amountTo,
                        pairEnabled,
                        pairPresented,
                        slippageTolerance
                    )

                    _extrinsicEvent.value = succeed
                    router.returnToPolkaswap()
                }
            )
        }
    }

    override fun onCleared() {
        poolsManager.unbind()
        super.onCleared()
    }
}
