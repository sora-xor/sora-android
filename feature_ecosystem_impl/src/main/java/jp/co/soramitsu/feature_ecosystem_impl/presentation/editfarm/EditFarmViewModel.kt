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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.Big100
import jp.co.soramitsu.androidfoundation.format.divideBy
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.demeter.domain.DemeterFarmingPool
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.model.EditFarmScreenState
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditFarmViewModel @AssistedInject constructor(
    private val poolsInteractor: PoolsInteractor,
    private val walletInteractor: WalletInteractor,
    private val assetsRouter: AssetsRouter,
    private val assetsInteractor: AssetsInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    @Assisted("id1") private val token1Id: String,
    @Assisted("id2") private val token2Id: String,
    @Assisted("id3") private val token3Id: String,
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedEditFarmViewModelFactory {
        fun create(
            @Assisted("id1") id1: String,
            @Assisted("id2") id2: String,
            @Assisted("id3") id3: String
        ): EditFarmViewModel
    }

    private val _state = MutableStateFlow(
        EditFarmScreenState(
            StringTriple(token1Id, token2Id, token3Id),
            "0%",
            0f,
            "1",
            "2",
            "0.1 XOR",
            "0.2 XOR",
        )
    )
    val state = _state.asStateFlow()

    private var poolData: CommonUserPoolData? = null
    private var farmedPool: DemeterFarmingPool? = null
    private var currentStackedPercent: Double = 0.0
    private var stakingAmount: BigDecimal = BigDecimal.ZERO
    private var transferableXorBalance: BigDecimal = BigDecimal.ZERO
    private var stakingNetworkFee: BigDecimal = BigDecimal.ZERO
    private var unStakingNetworkFee: BigDecimal = BigDecimal.ZERO
    private var feeAsset: Asset? = null
    private var stakeFee: BigDecimal = BigDecimal.ZERO

    /**
     * A tapped Confirm is on its way and reports its own result. Set before the coroutine starts,
     * so a second tap is ignored whatever the button shows at that moment (it stays clickable
     * while its progress mark fades in). Without this a second tap started a second coroutine;
     * the duplicate guard stopped it from signing, but it cleared the progress mark and asked
     * "send again anyway?" about a transaction sent a moment ago.
     */
    private var confirmInFlight = false

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = resourceManager.getString(R.string.edit_farm),
                navIcon = R.drawable.ic_cross_24,
                visibility = true,
                searchEnabled = false,
            ),
        )
        load()
    }

    /**
     * The screen is built from fees asked from the SORA network. While it cannot be reached the
     * user is told so, with Try again and Close. Nothing here may end the app.
     */
    private fun onLoadFailed(error: Throwable) {
        onError(error)
        showFeesUnavailable(
            resourceManager = resourceManager,
            error = error,
            onClose = { _navigationPop.trigger() },
            onRetry = ::load,
        )
    }

    private fun load() {
        val ids = StringTriple(token1Id, token2Id, token3Id)
        viewModelScope.launch {
            try {
                loadFarm(ids)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                onLoadFailed(t)
            }
        }
    }

    private suspend fun loadFarm(ids: StringTriple) {
        run {
            poolData = poolsInteractor.getPoolOfCurAccount(StringPair(ids.first, ids.second))
            feeAsset = assetsInteractor.getAssetOrThrow(SubstrateOptionsProvider.feeAssetId)
            transferableXorBalance = feeAsset?.balance?.transferable ?: BigDecimal.ZERO

            poolData?.let { poolData ->
                demeterFarmingInteractor.getFarmedBasicPool(ids)?.let { basicFarm ->
                    farmedPool = demeterFarmingInteractor.getFarmedPool(ids)

                    currentStackedPercent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                        farmedPool?.amount ?: BigDecimal.ZERO,
                        poolData.user.poolProvidersBalance,
                    )
                    stakeFee = basicFarm.fee.toBigDecimal().divideBy(Big100, 2)

                    recalcFuturePoolShareStacked()

                    stakingNetworkFee = demeterFarmingInteractor.calcDepositDemeterNetworkFee(ids)
                    unStakingNetworkFee = demeterFarmingInteractor.calcWithdrawDemeterNetworkFee(ids)

                    onSliderChange(currentStackedPercent / 100)
                    _state.value = _state.value.copy(
                        poolShareStaked = "${numbersFormatter.format(currentStackedPercent)}%",
                        fee = "${numbersFormatter.format(basicFarm.fee)}%",
                        networkFee = "$stakingNetworkFee ${feeAsset?.token?.symbol}",
                        isCardLoading = false,
                    )
                }
            }
        }
    }

    private fun recalcFuturePoolShareStacked() {
        poolData?.let { poolData ->
            val currentStakingAmount = farmedPool?.amount ?: BigDecimal.ZERO
            stakingAmount =
                ((poolData.user.poolProvidersBalance * _state.value.sliderProgressState.toBigDecimal()) - currentStakingAmount).abs()

            val feeAmount = stakingAmount * stakeFee
            val stakingAmountWithoutFee = stakingAmount - feeAmount
            var shareWillBe = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                stakingAmountWithoutFee + currentStakingAmount,
                poolData.user.poolProvidersBalance - feeAmount
            )

            if (_state.value.sliderProgressState * 100 < shareWillBe) {
                shareWillBe = (_state.value.sliderProgressState * 100).toDouble()
            }

            _state.value = _state.value.copy(
                poolShareStakedWillBe = "${numbersFormatter.format(shareWillBe)}%"
            )
        }
    }

    fun onConfirm() = confirm(confirmedDuplicate = false)

    /**
     * Stakes or unstakes and shows exactly what is known afterwards. A transaction that reached
     * the network (confirmed, failed on chain, or still waiting) opens its transaction page,
     * which carries the status and follows it. One that did not reach the network (refused by
     * the node, or not prepared) is explained in a dialog. The button stops spinning on every
     * path, and no failure may leave viewModelScope uncaught.
     *
     * The result arrives after a wait of up to two minutes, so the app can be in the background
     * or behind the PIN lock by then. The dialogs are delivered when the screen is shown again;
     * the transaction page opens when it is in front (see [openTransactionPage]), and until
     * then the button stays off.
     */
    private fun confirm(confirmedDuplicate: Boolean) {
        // One is on its way, or one reached the network and its page has not opened yet.
        if (confirmInFlight || resultHeld) return
        val current = _state.value
        if (stakingAmount <= BigDecimal.ZERO) return
        confirmInFlight = true
        viewModelScope.launch {
            try {
                sendAndShowResult(current, confirmedDuplicate)
            } finally {
                confirmInFlight = false
            }
        }
    }

    private suspend fun sendAndShowResult(
        current: EditFarmScreenState,
        confirmedDuplicate: Boolean,
    ) {
        _state.value = _state.value.copy(isButtonLoading = true)
        var outcome: TransactionOutcome? = null
        var notSent: Throwable? = null
        try {
            outcome = if (current.sliderProgressState * 100 > currentStackedPercent) {
                demeterFarmingInteractor.depositDemeterFarming(
                    current.farmIds,
                    stakingAmount,
                    stakingNetworkFee,
                    confirmedDuplicate,
                )
            } else {
                demeterFarmingInteractor.withdrawDemeterFarming(
                    current.farmIds,
                    stakingAmount,
                    unStakingNetworkFee,
                    confirmedDuplicate,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: DuplicateWaitingTransactionException) {
            _state.value = _state.value.copy(isButtonLoading = false)
            askBeforeSendingAgain(resourceManager) { confirm(confirmedDuplicate = true) }
            return
        } catch (t: Throwable) {
            notSent = t
        }
        _state.value = _state.value.copy(isButtonLoading = false)
        when (outcome) {
            is TransactionOutcome.Confirmed,
            is TransactionOutcome.Waiting,
            is TransactionOutcome.FailedOnChain -> {
                // This screen is replaced by the transaction's page and sends nothing more.
                _state.value = _state.value.copy(isButtonActive = false)
                openTransactionPage(outcome.txHash)
            }
            is TransactionOutcome.RefusedByNode ->
                showTransactionRefused(resourceManager, outcome)
            null -> showTransactionNotSent(resourceManager, checkNotNull(notSent))
        }
    }

    /**
     * Opens the transaction's page in place of this screen, only while this screen is in front.
     * A result that arrives while the app is in the background, or while the PIN lock is on
     * top, is held and the page opens on the next resume; nothing is navigated before that.
     */
    private fun openTransactionPage(txHash: String) = showWhenInFront {
        assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_STAKE)
    }

    fun onSliderChange(value: Double) {
        val percentage = value * 100

        val networkFee = if (percentage > currentStackedPercent) {
            stakingNetworkFee
        } else {
            unStakingNetworkFee
        }

        val fee = if (percentage > currentStackedPercent) {
            stakeFee * Big100
        } else {
            BigDecimal.ZERO
        }

        val isChanged = abs(percentage - currentStackedPercent) > 0.001

        _state.value = state.value.copy(
            sliderProgressState = value.toFloat(),
            fee = "${numbersFormatter.formatBigDecimal(fee)}%",
            networkFee = "$networkFee ${feeAsset?.token?.symbol}",
            isButtonActive = isChanged && transferableXorBalance >= networkFee && !resultHeld,
            percentageText = "${numbersFormatter.format(percentage)}%",
        )

        recalcFuturePoolShareStacked()
    }
}
