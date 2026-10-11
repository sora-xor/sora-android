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

package jp.co.soramitsu.feature_referral_impl.presentation

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.format.orZero
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.ConfirmDialogData
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.lazyAsync
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_referral_impl.domain.ReferralInteractor
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralFeatureRoutes.BOND_XOR
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralFeatureRoutes.REFERRAL_PROGRAM
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralFeatureRoutes.REFERRER_FILLED
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralFeatureRoutes.REFERRER_INPUT
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralFeatureRoutes.UNBOND_XOR
import jp.co.soramitsu.feature_referral_impl.presentation.ReferralFeatureRoutes.WELCOME_PAGE
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.component.wrappedtext.WrappedTextState
import kotlin.math.truncate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class ReferralViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val assetsRouter: AssetsRouter,
    private val interactor: ReferralInteractor,
    private val walletInteractor: WalletInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val router: MainRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val feeTokenAsync by viewModelScope.lazyAsync { walletInteractor.getFeeToken() }
    private suspend fun feeToken() = feeTokenAsync.await()

    private var currentEnteredReferrerLink: String? = null
    private var xorBalance: BigDecimal = BigDecimal.ZERO
    private var referrerBalance: BigDecimal = BigDecimal.ZERO
    private var extrinsicFee: BigDecimal = BigDecimal.ZERO
    private var setReferrerFee: BigDecimal = BigDecimal.ZERO
    private var bondInvitationsCount: Int = 1

    private var referralsState: ReferralsCardState = ReferralsCardState()
        set(value) {
            _referralScreenState.value = _referralScreenState.value.copy(
                referralInvitationsCardState = _referralScreenState.value.referralInvitationsCardState.copy(
                    referrals = value
                )
            )
            field = value
        }

    private val _shareLinkEvent = SingleLiveEvent<String>()
    val shareLinkEvent: LiveData<String> = _shareLinkEvent

    private val _referralScreenState = MutableStateFlow(emptyState)
    val referralScreenState = _referralScreenState.asStateFlow()

    private var referrer: String? = null
    private var programJob: Job? = null

    /**
     * A tapped bond, unbond or set-referrer is on its way and reports its own result. Set before
     * the coroutine starts, so a second tap is ignored whatever the button shows at that moment
     * (it stays clickable while its progress mark fades in). Without this a second tap started a
     * second coroutine; the duplicate guard stopped it from signing, but it cleared the progress
     * mark and asked "send again anyway?" about a transaction sent a moment ago.
     */
    private var submitInFlight = false

    override fun startScreen(): String = ReferralFeatureRoutes.WELCOME_PROGRESS

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.referral_toolbar_title,
        )
        loadProgram()
        observeAccountState()
    }

    /**
     * Loads the fees and the bonded balance the whole screen is built from. Both fees are asked
     * from the SORA network, so this fails while it cannot be reached. The failure is told to the
     * user with Try again and Close; it never ends the app and never leaves an endless spinner.
     */
    private fun loadProgram() {
        programJob?.cancel()
        programJob = interactor.observeReferrerBalance()
            .onStart {
                extrinsicFee = interactor.calcBondFee()
                setReferrerFee = interactor.getSetReferrerFee()
            }
            .debounce(500)
            .distinctUntilChanged()
            .onEach {
                referrerBalance = it.orZero()
                val feeToken = feeToken()
                val invitationsCount = calcInvitationsCount()
                _referralScreenState.value =
                    if (currentDestination == ReferralFeatureRoutes.WELCOME_PROGRESS) {
                        ReferralProgramState(
                            common = ReferralCommonState(
                                activate = true,
                                progress = false,
                                referrer = referrer,
                                referrerFee = feeToken.printAmountWithFee(
                                    setReferrerFee,
                                    numbersFormatter
                                ),
                                extrinsicFee = feeToken.formatBalance(extrinsicFee),
                                extrinsicFeeFiat = feeToken.printFiat(
                                    extrinsicFee,
                                    numbersFormatter
                                )
                            ),
                            bondState = calcBondState(feeToken),
                            referrerInputState = InputTextState(
                                label = resourceManager.getString(R.string.referral_referral_link)
                            ),
                            referralInvitationsCardState = ReferralInvitationsCardState(
                                if (invitationsCount > 0) {
                                    resourceManager.getString(R.string.referral_invitaion_link_title)
                                } else {
                                    resourceManager.getString(R.string.referral_no_available_invitations)
                                },
                                invitationsCount,
                                WrappedTextState(
                                    title = resourceManager.getString(R.string.referral_invitation_link),
                                    text = interactor.getInvitationLink(),
                                    trailingIcon = R.drawable.ic_share_24
                                ),
                                feeToken.formatBalance(referrerBalance),
                                referrals = referralsState,
                            )
                        )
                    } else {
                        _referralScreenState.value.copy(
                            referralInvitationsCardState = _referralScreenState.value.referralInvitationsCardState.copy(
                                title = if (invitationsCount > 0) {
                                    resourceManager.getString(R.string.referral_invitaion_link_title)
                                } else {
                                    resourceManager.getString(R.string.referral_no_available_invitations)
                                },
                                invitationsCount = invitationsCount,
                                bondedXorString = feeToken.formatBalance(referrerBalance)
                            )
                        )
                    }
                if (currentDestination == ReferralFeatureRoutes.WELCOME_PROGRESS) {
                    _navToStart.value = if (_referralScreenState.value.isInitialized()) REFERRAL_PROGRAM else WELCOME_PAGE
                }
            }
            // After onEach, so a failure while building the state is handled here as well.
            .catch { onProgramUnavailable(it) }
            .launchIn(viewModelScope)
    }

    private fun onProgramUnavailable(error: Throwable) {
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
                onDismiss = { router.popBackStack() },
                onConfirm = ::loadProgram,
            )
        )
    }

    /**
     * Every collector below handles failures of its own work: an exception thrown inside onEach
     * would otherwise leave viewModelScope uncaught and end the app (seen offline, when the fee
     * could not be read while the XOR balance arrived).
     */
    private fun observeAccountState() {
        interactor.observeMyReferrer()
            .distinctUntilChanged()
            .onEach { newValue ->
                referrer = newValue.ifEmpty { null }
                _referralScreenState.value =
                    _referralScreenState.value.copy(common = _referralScreenState.value.common.copy(referrer = referrer))
            }
            .catch { onError(it) }
            .launchIn(viewModelScope)

        interactor.observeReferrals()
            .debounce(1000)
            .distinctUntilChanged()
            .onEach {
                tryCatch { interactor.updateReferrals() }
            }
            .catch { onError(it) }
            .launchIn(viewModelScope)

        assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)
            .distinctUntilChanged()
            .onEach { asset ->
                xorBalance = asset?.balance?.transferable ?: BigDecimal.ZERO
                tryCatch { reCalcOnChange(currentDestination) }
            }
            .catch { onError(it) }
            .launchIn(viewModelScope)

        interactor.getReferrals()
            .onEach { referrals ->
                var totalAmount = BigDecimal.ZERO
                val feeToken = feeToken()
                referralsState = ReferralsCardState(
                    rewards = referrals.map {
                        totalAmount += it.xorAmount
                        ReferralModel(
                            it.address,
                            feeToken.formatBalance(it.xorAmount)
                        )
                    },
                    totalRewards = feeToken.formatBalance(totalAmount),
                    isExpanded = referralsState.isExpanded,
                )
            }
            .catch { onError(it) }
            .launchIn(viewModelScope)
    }

    /** Recalculations read fees; a failure leaves the screen as it is and never ends the app. */
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch { tryCatch(block) }
    }

    fun onShareLink() {
        _shareLinkEvent.value =
            _referralScreenState.value.referralInvitationsCardState.wrappedTextState.text
    }

    fun onBondMinus() {
        if (bondInvitationsCount > 1) {
            launchSafely {
                bondInvitationsCount--
                reCalcOnChange(BOND_XOR)
            }
        }
    }

    fun onBondPlus() {
        launchSafely {
            bondInvitationsCount++
            reCalcOnChange(BOND_XOR)
        }
    }

    fun onBondValueChange(value: Int) {
        val realValue = getRealValue(value)
        if (realValue >= 0) {
            launchSafely {
                bondInvitationsCount = realValue
                reCalcOnChange(BOND_XOR)
            }
        }
    }

    fun onUnbondMinus() {
        if (bondInvitationsCount > 1) {
            launchSafely {
                bondInvitationsCount--
                reCalcOnChange(UNBOND_XOR)
            }
        }
    }

    fun onUnbondPlus() {
        if (bondInvitationsCount < calcInvitationsCount()) {
            launchSafely {
                bondInvitationsCount++
                reCalcOnChange(UNBOND_XOR)
            }
        }
    }

    fun onUnbondValueChange(value: Int) {
        val realValue = getRealValue(value)
        if (realValue >= 0 && realValue <= calcInvitationsCount()) {
            launchSafely {
                bondInvitationsCount = realValue
                reCalcOnChange(UNBOND_XOR)
            }
        }
    }

    fun onBondButtonClick() = submit(REFERRAL_PROGRAM) { confirmedDuplicate ->
        interactor.observeBond(calcInvitationsAmount(bondInvitationsCount), confirmedDuplicate)
    }

    fun onUnbondButtonClick() = submit(REFERRAL_PROGRAM) { confirmedDuplicate ->
        interactor.observeUnbond(calcInvitationsAmount(bondInvitationsCount), confirmedDuplicate)
    }

    fun onActivateLinkClick() = submit(destinationAfter = null) { confirmedDuplicate ->
        val input = _referralScreenState.value.referrerInputState.value.text
        val referrerOk = interactor.isLinkOrAddressOk(input)
        interactor.observeSetReferrer(referrerOk.second, confirmedDuplicate)
    }

    /**
     * Sends one referral transaction and shows exactly what is known afterwards. A transaction
     * that reached the network (confirmed, failed on chain, or still waiting) opens its
     * transaction page, which carries the status and follows it. One that did not reach the
     * network (refused by the node, or not prepared) is explained in a dialog. The progress mark
     * is cleared on every path.
     *
     * The result arrives after a wait of up to two minutes, so the app can be in the background
     * or behind the PIN lock by then. The dialogs and the change of page inside this screen are
     * delivered when the screen is shown again; the transaction page opens when it is in front
     * (see [openTransactionPage]), and until then nothing more is sent from here.
     */
    private fun submit(
        destinationAfter: String?,
        confirmedDuplicate: Boolean = false,
        send: suspend (confirmedDuplicate: Boolean) -> TransactionOutcome,
    ) {
        // One is on its way, or one reached the network and its page has not opened yet.
        if (submitInFlight || resultHeld) return
        submitInFlight = true
        viewModelScope.launch {
            try {
                sendAndShowResult(destinationAfter, confirmedDuplicate, send)
            } finally {
                submitInFlight = false
            }
        }
    }

    private suspend fun sendAndShowResult(
        destinationAfter: String?,
        confirmedDuplicate: Boolean,
        send: suspend (confirmedDuplicate: Boolean) -> TransactionOutcome,
    ) {
        setProgress(true)
        var outcome: TransactionOutcome? = null
        var notSent: Throwable? = null
        try {
            outcome = send(confirmedDuplicate)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: DuplicateWaitingTransactionException) {
            setProgress(false)
            askBeforeSendingAgain(resourceManager) {
                submit(destinationAfter, confirmedDuplicate = true, send = send)
            }
            return
        } catch (t: Throwable) {
            notSent = t
        }
        setProgress(false)
        when (outcome) {
            is TransactionOutcome.Confirmed,
            is TransactionOutcome.Waiting,
            is TransactionOutcome.FailedOnChain -> {
                openTransactionPage(outcome.txHash)
                _navToStart.value = destinationAfter
                    ?: if (_referralScreenState.value.isInitialized()) {
                        REFERRAL_PROGRAM
                    } else {
                        WELCOME_PAGE
                    }
            }
            is TransactionOutcome.RefusedByNode ->
                showTransactionRefused(resourceManager, outcome)
            null -> showTransactionNotSent(resourceManager, checkNotNull(notSent))
        }
    }

    /**
     * Opens the transaction's page on top of this screen, only while this screen is in front. A
     * result that arrives while the app is in the background, or while the PIN lock is on top,
     * is held and the page opens on the next resume; nothing is navigated before that.
     */
    private fun openTransactionPage(txHash: String) = showWhenInFront {
        assetsRouter.showTxResult(txHash, TransactionScreen.REFERRAL)
    }

    private fun setProgress(progress: Boolean) {
        _referralScreenState.value = _referralScreenState.value.copy(
            common = _referralScreenState.value.common.copy(progress = progress)
        )
    }

    private suspend fun reCalcOnChange(route: String?) {
        if (route == null) return
        val feeToken = feeToken()
        val bondFee = interactor.calcBondFee()
        val buttonActiveValidation = when (route) {
            REFERRER_INPUT -> {
                interactor.isLinkOrAddressOk(currentEnteredReferrerLink.orEmpty()).first
            }

            BOND_XOR -> {
                validateFeePayment(bondFee + calcInvitationsAmount(bondInvitationsCount))
            }

            UNBOND_XOR -> {
                validateFeePayment(bondFee)
            }

            else -> true
        }

        val amount = calcInvitationsAmount(feeToken, bondInvitationsCount)
        _referralScreenState.value =
            _referralScreenState.value.copy(
                common = _referralScreenState.value.common.copy(
                    // A balance update during the wait for a result must not bring the button
                    // back: the progress mark stays until the transaction reported its result.
                    progress = submitInFlight,
                    activate = buttonActiveValidation
                ),
                bondState = _referralScreenState.value.bondState.copy(
                    invitationsCount = bondInvitationsCount,
                    invitationsAmount = amount,
                    balance = feeToken.formatBalance(xorBalance)
                )
            )

        updateTransactionReminderWarningVisibility(route == UNBOND_XOR)
    }

    private suspend fun updateTransactionReminderWarningVisibility(isUnbonding: Boolean) {
        val result = assetsInteractor.isNotEnoughXorLeftAfterTransaction(
            xorChange = calcInvitationsAmount(_referralScreenState.value.bondState.invitationsCount).let {
                if (isUnbonding) -it else it
            },
            networkFeeInXor = extrinsicFee.orZero(),
        )

        _referralScreenState.value = _referralScreenState.value.copy(
            bondState = _referralScreenState.value.bondState.copy(
                shouldTransactionReminderInsufficientWarningBeShown = result,
            )
        )
    }

    private fun calcInvitationsCount(): Int {
        // The fee is zero until it has been read; dividing by it would throw.
        if (setReferrerFee.signum() <= 0) return 0
        return truncate((referrerBalance / setReferrerFee).toDouble()).toInt()
    }

    private fun calcInvitationsAmount(count: Int): BigDecimal =
        setReferrerFee.multiply(count.toBigDecimal())

    private fun calcInvitationsAmount(feeToken: Token, count: Int): String {
        val amount = calcInvitationsAmount(count)
        return feeToken.printAmountWithFee(amount, numbersFormatter)
    }

    private fun Token.formatBalance(balance: BigDecimal): String =
        this.printBalance(
            balance = balance,
            nf = numbersFormatter,
            precision = AssetHolder.ROUNDING
        )

    private fun Token.printAmountWithFee(
        amount: BigDecimal,
        numbersFormatter: NumbersFormatter
    ): String {
        return "${this.printBalance(amount, numbersFormatter, AssetHolder.ROUNDING)} (${
            this.printFiat(
                amount,
                numbersFormatter
            )
        })"
    }

    private fun validateFeePayment(fee: BigDecimal): Boolean {
        return xorBalance > fee
    }

    private fun calcBondState(feeToken: Token): ReferralBondState {
        return ReferralBondState(
            balance = feeToken.formatBalance(xorBalance),
            invitationsCount = bondInvitationsCount,
            invitationsAmount = calcInvitationsAmount(feeToken, bondInvitationsCount)
        )
    }

    private fun getRealValue(value: Int) =
        if (bondInvitationsCount == 0)
            value.toString().removePrefix("0").removeSuffix("0").toIntOrNull() ?: 0
        else value

    fun toggleReferralsCard() {
        referralsState = referralsState.copy(isExpanded = referralsState.isExpanded.not())
    }

    private fun toggleToolbarTitle(route: String) {
        _toolbarState.value?.let {
            _toolbarState.value = it.copy(
                basic = it.basic.copy(
                    title = when (route) {
                        BOND_XOR -> R.string.referral_add_invitations_title
                        UNBOND_XOR -> R.string.wallet_unbonded
                        REFERRER_INPUT -> R.string.referral_enter_link_title
                        REFERRER_FILLED -> R.string.referral_your_referrer
                        else -> R.string.referral_toolbar_title
                    },
                ),
            )
        }
    }

    fun onReferrerInputChange(textValue: TextFieldValue) {
        currentEnteredReferrerLink = textValue.text
        launchSafely {
            _referralScreenState.value = _referralScreenState.value.copy(
                common = _referralScreenState.value.common.copy(
                    activate = interactor.isLinkOrAddressOk(
                        textValue.text
                    ).first
                ),
                referrerInputState = _referralScreenState.value.referrerInputState.copy(
                    value = textValue
                ),
            )
        }
    }

    fun openReferrerInput() {
        _referralScreenState.value = _referralScreenState.value.copy(
            referrerInputState = _referralScreenState.value.referrerInputState.copy(
                value = TextFieldValue()
            ),
        )
        launchSafely {
            reCalcOnChange(REFERRER_INPUT)
        }
    }

    fun openBond() {
        launchSafely {
            bondInvitationsCount = 1
            reCalcOnChange(BOND_XOR)
        }
    }

    override fun onNavIcon() {
        when (currentDestination) {
            "", REFERRAL_PROGRAM, WELCOME_PAGE -> {
                router.popBackStack()
            }

            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onBackPressed() {
        onNavIcon()
    }

    fun openUnbond() {
        launchSafely {
            bondInvitationsCount = 1
            reCalcOnChange(UNBOND_XOR)
        }
    }

    override fun onCurrentDestinationChanged(curDest: String) {
        toggleToolbarTitle(curDest)
    }
}
