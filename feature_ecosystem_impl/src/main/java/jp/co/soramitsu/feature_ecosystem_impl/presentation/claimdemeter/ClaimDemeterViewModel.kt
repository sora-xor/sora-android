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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.ConfirmDialogData
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
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
        val unreachable =
            NetworkFeeUnavailable.from(error) == NetworkFeeUnavailable.NETWORK_UNREACHABLE
        confirmDialogLiveData.setValue(
            ConfirmDialogData(
                title = resourceManager.getString(R.string.network_fee_unavailable_short),
                message = resourceManager.getString(
                    if (unreachable) {
                        R.string.tx_outcome_screen_needs_network
                    } else {
                        R.string.network_fee_unavailable
                    }
                ),
                confirmText = resourceManager.getString(R.string.common_try_again),
                dismissText = resourceManager.getString(R.string.common_close),
                onDismiss = { _navigationPop.trigger() },
                onConfirm = ::load,
            )
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
     */
    private fun claim(confirmedDuplicate: Boolean) {
        viewModelScope.launch {
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
                return@launch
            } catch (t: Throwable) {
                notSent = t
            }
            _state.value = _state.value.copy(isButtonLoading = false)
            when (outcome) {
                is TransactionOutcome.Confirmed,
                is TransactionOutcome.Waiting,
                is TransactionOutcome.FailedOnChain ->
                    assetsRouter.showTxDetails(outcome.txHash, true)
                is TransactionOutcome.RefusedByNode ->
                    showTransactionRefused(resourceManager, outcome)
                null -> showTransactionNotSent(resourceManager, checkNotNull(notSent))
            }
        }
    }
}
