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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavOptionsBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.lazyAsync
import jp.co.soramitsu.common.util.ext.orZero
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
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
import kotlinx.coroutines.FlowPreview
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
            referralScreenState = referralScreenState.copy(
                referralInvitationsCardState = referralScreenState.referralInvitationsCardState.copy(
                    referrals = value
                )
            )
            field = value
        }

    private val _shareLinkEvent = SingleLiveEvent<String>()
    val shareLinkEvent: LiveData<String> = _shareLinkEvent

    internal var referralScreenState by mutableStateOf(emptyState)
        private set

    private var referrer: String? = null

    private val singleTopTrue: NavOptionsBuilder.() -> Unit = {
        launchSingleTop = true
    }

    override fun startScreen(): String = ReferralFeatureRoutes.WELCOME_PROGRESS

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.referral_toolbar_title,
        )

        interactor.observeReferrerBalance()
            .onStart {
                extrinsicFee = interactor.calcBondFee()
                setReferrerFee = interactor.getSetReferrerFee()
            }
            .catch { onError(it) }
            .debounce(500)
            .distinctUntilChanged()
            .onEach {
                referrerBalance = it.orZero()
                val feeToken = feeToken()
                val invitationsCount = calcInvitationsCount()
                referralScreenState =
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
                        referralScreenState.copy(
                            referralInvitationsCardState = referralScreenState.referralInvitationsCardState.copy(
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
                    _navEvent.value =
                        if (referralScreenState.isInitialized()) REFERRAL_PROGRAM to singleTopTrue else WELCOME_PAGE to singleTopTrue
                }
            }
            .launchIn(viewModelScope)

        interactor.observeMyReferrer()
            .catch { onError(it) }
            .distinctUntilChanged()
            .onEach { newValue ->
                referrer = newValue.ifEmpty { null }
                referralScreenState =
                    referralScreenState.copy(common = referralScreenState.common.copy(referrer = referrer))
            }
            .launchIn(viewModelScope)

        interactor.observeReferrals()
            .catch { onError(it) }
            .debounce(1000)
            .distinctUntilChanged()
            .onEach {
                interactor.updateReferrals()
            }
            .launchIn(viewModelScope)

        assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)
            .catch { onError(it) }
            .distinctUntilChanged()
            .onEach { asset ->
                xorBalance = asset.balance.transferable
                reCalcOnChange(currentDestination)
            }
            .launchIn(viewModelScope)

        interactor.getReferrals()
            .catch { onError(it) }
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
            .launchIn(viewModelScope)
    }

    fun onShareLink() {
        _shareLinkEvent.value =
            referralScreenState.referralInvitationsCardState.wrappedTextState.text
    }

    fun onBondMinus() {
        if (bondInvitationsCount > 1) {
            viewModelScope.launch {
                bondInvitationsCount--
                reCalcOnChange(BOND_XOR)
            }
        }
    }

    fun onBondPlus() {
        viewModelScope.launch {
            bondInvitationsCount++
            reCalcOnChange(BOND_XOR)
        }
    }

    fun onBondValueChange(value: Int) {
        val realValue = getRealValue(value)
        if (realValue >= 0) {
            viewModelScope.launch {
                bondInvitationsCount = realValue
                reCalcOnChange(BOND_XOR)
            }
        }
    }

    fun onUnbondMinus() {
        if (bondInvitationsCount > 1) {
            viewModelScope.launch {
                bondInvitationsCount--
                reCalcOnChange(UNBOND_XOR)
            }
        }
    }

    fun onUnbondPlus() {
        if (bondInvitationsCount < calcInvitationsCount()) {
            viewModelScope.launch {
                bondInvitationsCount++
                reCalcOnChange(UNBOND_XOR)
            }
        }
    }

    fun onUnbondValueChange(value: Int) {
        val realValue = getRealValue(value)
        if (realValue >= 0 && realValue <= calcInvitationsCount()) {
            viewModelScope.launch {
                bondInvitationsCount = realValue
                reCalcOnChange(UNBOND_XOR)
            }
        }
    }

    fun onBondButtonClick() {
        viewModelScope.launch {
            tryCatch {
                referralScreenState =
                    referralScreenState.copy(common = referralScreenState.common.copy(progress = true))
                val result = interactor.observeBond(calcInvitationsAmount(bondInvitationsCount))
                assetsRouter.showTxDetails(result)
                _navEvent.value = REFERRAL_PROGRAM to singleTopTrue
            }
        }
    }

    fun onUnbondButtonClick() {
        viewModelScope.launch {
            tryCatch {
                referralScreenState =
                    referralScreenState.copy(common = referralScreenState.common.copy(progress = true))
                val result = interactor.observeUnbond(
                    calcInvitationsAmount(bondInvitationsCount),
                )

                assetsRouter.showTxDetails(result)
                _navEvent.value = REFERRAL_PROGRAM to singleTopTrue
            }
        }
    }

    fun onActivateLinkClick() {
        viewModelScope.launch {
            tryCatch {
                val input =
                    referralScreenState.referrerInputState.value.text
                referralScreenState =
                    referralScreenState.copy(common = referralScreenState.common.copy(progress = true))
                val referrerOk = interactor.isLinkOrAddressOk(input)
                val result = interactor.observeSetReferrer(referrerOk.second)

                assetsRouter.showTxDetails(result)
                _navEvent.value =
                    if (referralScreenState.isInitialized()) REFERRAL_PROGRAM to singleTopTrue else WELCOME_PAGE to singleTopTrue
            }
        }
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
        referralScreenState =
            referralScreenState.copy(
                common = referralScreenState.common.copy(
                    progress = false,
                    activate = buttonActiveValidation
                ),
                bondState = referralScreenState.bondState.copy(
                    invitationsCount = bondInvitationsCount,
                    invitationsAmount = amount,
                    balance = feeToken.formatBalance(xorBalance)
                )
            )

        updateTransactionReminderWarningVisibility()
    }

    private suspend fun updateTransactionReminderWarningVisibility() {
        val result = assetsInteractor.isEnoughXorLeftAfterTransaction(
            primaryToken = feeToken(),
            primaryTokenAmount = calcInvitationsAmount(referralScreenState.bondState.invitationsCount),
            secondaryToken = null,
            secondaryTokenAmount = null,
            networkFeeInXor = extrinsicFee.orZero()
        )

        referralScreenState = referralScreenState.copy(
            bondState = referralScreenState.bondState.copy(
                shouldTransactionReminderInsufficientWarningBeShown = result,
                transactionFeeToken = feeToken().symbol
            )
        )
    }

    private fun calcInvitationsCount(): Int {
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
        viewModelScope.launch {
            referralScreenState = referralScreenState.copy(
                common = referralScreenState.common.copy(
                    activate = interactor.isLinkOrAddressOk(
                        textValue.text
                    ).first
                ),
                referrerInputState = referralScreenState.referrerInputState.copy(
                    value = textValue
                ),
            )
        }
    }

    fun openReferrerInput() {
        referralScreenState = referralScreenState.copy(
            referrerInputState = referralScreenState.referrerInputState.copy(
                value = TextFieldValue()
            ),
        )
        viewModelScope.launch {
            reCalcOnChange(REFERRER_INPUT)
        }
    }

    fun openBond() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            bondInvitationsCount = 1
            reCalcOnChange(UNBOND_XOR)
        }
    }

    override fun onCurrentDestinationChanged(curDest: String) {
        toggleToolbarTitle(curDest)
    }
}
