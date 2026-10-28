/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter.model.ClaimScreenState
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClaimDemeterViewModel @AssistedInject constructor(
    private val walletInteractor: WalletInteractor,
    private val assetsRouter: AssetsRouter,
    private val assetsInteractor: AssetsInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    @Assisted("id1") private val token1Id: String,
    @Assisted("id2") private val token2Id: String,
    @Assisted("id3") private val token3Id: String,
    @Assisted("amount") private val claimAmount: BigDecimal,
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedClaimDemeterViewModelFactory {
        fun create(
            @Assisted("id1") id1: String,
            @Assisted("id2") id2: String,
            @Assisted("id3") id3: String,
            @Assisted("amount") claimAmount: BigDecimal,
        ): ClaimDemeterViewModel
    }

    private val _state = MutableStateFlow(
        ClaimScreenState(
            StringTriple(token1Id, token2Id, token3Id),
            DEFAULT_ICON_URI,
            "",
            "",
            "",
            false,
            false,
        )
    )
    val state = _state.asStateFlow()

    private var networkFee: BigDecimal = BigDecimal.ZERO
    private var ids = StringTriple(token1Id, token2Id, token3Id)

    /**
     * A tapped Claim is on its way and reports its own result. Set before the coroutine starts,
     * so a second tap is ignored whatever the button shows at that moment (it stays clickable
     * while its progress mark fades in). Without this a second tap started a second coroutine;
     * the duplicate guard stopped it from signing, but it cleared the progress mark and asked
     * "send again anyway?" about a claim sent a moment ago.
     */
    private var claimInFlight = false

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = resourceManager.getString(R.string.claim_rewards),
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
        viewModelScope.launch {
            try {
                loadClaim()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                onLoadFailed(t)
            }
        }
    }

    private suspend fun loadClaim() {
        run {
            val feeToken = walletInteractor.getFeeToken()
            networkFee = demeterFarmingInteractor.calcClaimDemeterRewards(ids)
            val xorBalance = assetsInteractor.getXorBalance(feeToken.precision).transferable

            demeterFarmingInteractor.getFarmedPool(ids)?.let { farmPool ->
                _state.value = _state.value.copy(
                    tokenIcon = farmPool.tokenReward.iconUri(),
                    amountTitle = "${
                        numbersFormatter.formatBigDecimal(farmPool.amountReward, 3)
                    } ${farmPool.tokenReward.symbol}",
                    fiatAmountTitle = farmPool.tokenReward.printFiat(farmPool.amount, numbersFormatter),
                    networkFeeText = "$networkFee ${feeToken.symbol}",
                    isButtonActive = xorBalance >= networkFee,
                    isLoading = false
                )
            }
        }
    }

    fun onClaim() = claim(confirmedDuplicate = false)

    /**
     * Claims the rewards and shows exactly what is known afterwards. A transaction that reached
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
    private fun claim(confirmedDuplicate: Boolean) {
        // One is on its way, or one reached the network and its page has not opened yet.
        if (claimInFlight || resultHeld) return
        claimInFlight = true
        viewModelScope.launch {
            try {
                sendAndShowResult(confirmedDuplicate)
            } finally {
                claimInFlight = false
            }
        }
    }

    private suspend fun sendAndShowResult(confirmedDuplicate: Boolean) {
        _state.value = _state.value.copy(isButtonLoading = true)
        var outcome: TransactionOutcome? = null
        var notSent: Throwable? = null
        try {
            outcome = demeterFarmingInteractor.claimDemeterRewards(
                ids = ids,
                amount = claimAmount,
                networkFee = networkFee,
                confirmedDuplicate = confirmedDuplicate,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: DuplicateWaitingTransactionException) {
            _state.value = _state.value.copy(isButtonLoading = false)
            askBeforeSendingAgain(resourceManager) { claim(confirmedDuplicate = true) }
            return
        } catch (t: Throwable) {
            notSent = t
        }
        _state.value = _state.value.copy(isButtonLoading = false)
        when (outcome) {
            is TransactionOutcome.Confirmed,
            is TransactionOutcome.Waiting,
            is TransactionOutcome.FailedOnChain -> {
                // This screen is replaced by the transaction's page and claims nothing more.
                _state.value = _state.value.copy(isButtonActive = false)
                openTransactionPage(outcome.txHash)
            }
            is TransactionOutcome.RefusedByNode ->
                showTransactionRefused(resourceManager, outcome)
            null -> showTransactionNotSent(resourceManager, checkNotNull(notSent))
        }
    }

    /**
     * Opens the claim's transaction page in place of this screen, only while this screen is in
     * front. A result that arrives while the app is in the background, or while the PIN lock is
     * on top, is held and the page opens on the next resume; nothing is navigated before that.
     */
    private fun openTransactionPage(txHash: String) = showWhenInFront {
        assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_CLAIM)
    }
}
