/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.model.ToolbarState
import jp.co.soramitsu.common.base.model.ToolbarType
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.printBalance
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.lazyAsync
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_referral_impl.R
import jp.co.soramitsu.feature_referral_impl.domain.ReferralInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.math.truncate

@OptIn(FlowPreview::class)
@HiltViewModel
class ReferralViewModel @Inject constructor(
    private val interactor: ReferralInteractor,
    private val walletInteractor: WalletInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val router: MainRouter,
    private val progress: WithProgress,
    resourceManager: ResourceManager
) : BaseViewModel(), WithProgress by progress {

    private val feeTokenAsync by viewModelScope.lazyAsync { walletInteractor.getFeeToken() }
    private suspend fun feeToken() = feeTokenAsync.await()

    private var currentEnteredReferrerLink: String? = null
    private var xorBalance: BigDecimal = BigDecimal.ZERO
    private var referrerBalance: BigDecimal = BigDecimal.ZERO
    private var setReferrerFee: BigDecimal = BigDecimal.ZERO
    private var bondInvitationsCount: Int = 1
    private var lastOpenedSheet: DetailedBottomSheet? = null

    private var referralsState: ReferralsCardModel = ReferralsCardModel()
        set(value) {
            _referralScreenState.value?.let {
                if (it.screen is ReferralProgramStateScreen.ReferralProgramData) {
                    _referralScreenState.value = it.copy(screen = it.screen.copy(referrals = value))
                }
            }
            field = value
        }

    private val _extrinsicEvent = SingleLiveEvent<Boolean>()
    val extrinsicEvent: LiveData<Boolean> = _extrinsicEvent

    private val _shareLinkEvent = SingleLiveEvent<String>()
    val shareLinkEvent: LiveData<String> = _shareLinkEvent

    private val _hideSheet =
        MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val hideSheet = _hideSheet.asSharedFlow()

    private val _referralScreenState = MutableLiveData<ReferralProgramState>()
    val referralScreenState: LiveData<ReferralProgramState> = _referralScreenState

    private var referrer: String? = null

    init {
        _toolbarState.value = ToolbarState(
            type = ToolbarType.SMALL,
            title = resourceManager.getString(R.string.referral_toolbar_title),
        )

        interactor.observeReferrerBalance()
            .onStart {
                progress.showProgress()
                setReferrerFee = interactor.getSetReferrerFee()
            }
            .catch { onError(it) }
            .debounce(500)
            .distinctUntilChanged()
            .onEach {
                referrerBalance = it ?: BigDecimal.ZERO
                val feeToken = feeToken()
                _referralScreenState.value = ReferralProgramState(
                    common = ReferrerState(
                        referrer = referrer,
                        activate = false,
                        referrerFee = feeToken.formatBalance(setReferrerFee),
                        extrinsicFee = feeToken.formatBalance(interactor.calcBondFee())
                    ),
                    bondState = calcBondState(feeToken),
                    screen = if (referrerBalance < setReferrerFee)
                        ReferralProgramStateScreen.Initial else
                        ReferralProgramStateScreen.ReferralProgramData(
                            invitations = calcInvitationsCount(),
                            bonded = feeToken.formatBalance(referrerBalance),
                            link = interactor.getInvitationLink(),
                            referrals = referralsState,
                        )
                )
                progress.hideProgress()
            }
            .launchIn(viewModelScope)

        interactor.observeMyReferrer()
            .catch { onError(it) }
            .distinctUntilChanged()
            .onEach { newValue ->
                referrer = newValue.ifEmpty { null }
                _referralScreenState.value?.let {
                    _referralScreenState.value =
                        it.copy(common = it.common.copy(referrer = referrer))
                }
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

        walletInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)
            .catch { onError(it) }
            .distinctUntilChanged()
            .onEach { asset ->
                xorBalance = asset.balance.transferable
                lastOpenedSheet?.let {
                    reCalcOnChange(it)
                }
            }
            .launchIn(viewModelScope)

        interactor.getReferrals()
            .catch { onError(it) }
            .onEach { referrals ->
                var totalAmount = BigDecimal.ZERO
                val feeToken = feeToken()
                referralsState = ReferralsCardModel(
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
        _referralScreenState.value?.let { data ->
            data.screen.safeCast<ReferralProgramStateScreen.ReferralProgramData>()?.let {
                _shareLinkEvent.value = it.link
            }
        }
    }

    fun onSheetOpen(state: DetailedBottomSheet) {
        lastOpenedSheet = state
        when (state) {
            DetailedBottomSheet.REQUEST_REFERRER -> {
                viewModelScope.launch {
                    reCalcOnChange(DetailedBottomSheet.REQUEST_REFERRER)
                }
            }
            DetailedBottomSheet.SHOW_REFERRER -> {}
            DetailedBottomSheet.BOND -> {
                viewModelScope.launch {
                    bondInvitationsCount = 1
                    reCalcOnChange(DetailedBottomSheet.BOND)
                }
            }
            DetailedBottomSheet.UNBOND -> {
                viewModelScope.launch {
                    bondInvitationsCount = 1
                    reCalcOnChange(DetailedBottomSheet.UNBOND)
                }
            }
        }
    }

    fun onBondMinus() {
        if (bondInvitationsCount > 1) {
            viewModelScope.launch {
                bondInvitationsCount--
                reCalcOnChange(DetailedBottomSheet.BOND)
            }
        }
    }

    fun onBondPlus() {
        viewModelScope.launch {
            bondInvitationsCount++
            reCalcOnChange(DetailedBottomSheet.BOND)
        }
    }

    fun onBondValueChange(value: Int) {
        val realValue = getRealValue(value)
        if (realValue >= 0) {
            viewModelScope.launch {
                bondInvitationsCount = realValue
                reCalcOnChange(DetailedBottomSheet.BOND)
            }
        }
    }

    fun onUnbondMinus() {
        if (bondInvitationsCount > 1) {
            viewModelScope.launch {
                bondInvitationsCount--
                reCalcOnChange(DetailedBottomSheet.UNBOND)
            }
        }
    }

    fun onUnbondPlus() {
        if (bondInvitationsCount < calcInvitationsCount()) {
            viewModelScope.launch {
                bondInvitationsCount++
                reCalcOnChange(DetailedBottomSheet.UNBOND)
            }
        }
    }

    fun onUnbondValueChange(value: Int) {
        val realValue = getRealValue(value)
        if (realValue >= 0 && realValue <= calcInvitationsCount()) {
            viewModelScope.launch {
                bondInvitationsCount = realValue
                reCalcOnChange(DetailedBottomSheet.UNBOND)
            }
        }
    }

    fun onLinkChange(link: String) {
        currentEnteredReferrerLink = link
        onSheetOpen(DetailedBottomSheet.REQUEST_REFERRER)
    }

    fun onBondButtonClick() {
        viewModelScope.launch {
            tryCatch {
                _referralScreenState.value?.let {
                    _referralScreenState.value = it.copy(common = it.common.copy(progress = true))
                }
                val feeToken = feeToken()
                val result = interactor.observeBond(
                    calcInvitationsAmount(bondInvitationsCount),
                )
                _extrinsicEvent.value = result
                _hideSheet.tryEmit(true)
                _referralScreenState.value?.let {
                    when (it.screen) {
                        is ReferralProgramStateScreen.Initial -> {
                            _referralScreenState.value = it.copy(
                                common = it.common.copy(activate = false, progress = false),
                                bondState = calcBondState(feeToken),
                                screen = ReferralProgramStateScreen.ReferralProgramData(
                                    invitations = calcInvitationsCount(),
                                    bonded = feeToken.formatBalance(referrerBalance),
                                    link = interactor.getInvitationLink(),
                                    referrals = referralsState,
                                )
                            )
                        }
                        is ReferralProgramStateScreen.ReferralProgramData -> {
                            _referralScreenState.value = it.copy(
                                common = it.common.copy(activate = false, progress = false),
                                screen = it.screen.copy(
                                    invitations = calcInvitationsCount(),
                                    bonded = feeToken.formatBalance(referrerBalance)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun onUnbondButtonClick() {
        viewModelScope.launch {
            tryCatch {
                _referralScreenState.value?.let {
                    _referralScreenState.value = it.copy(common = it.common.copy(progress = true))
                }
                val feeToken = feeToken()
                val result = interactor.observeUnbond(
                    calcInvitationsAmount(bondInvitationsCount),
                )
                _extrinsicEvent.value = result
                _hideSheet.tryEmit(true)
                _referralScreenState.value?.let {
                    if (it.screen is ReferralProgramStateScreen.ReferralProgramData) {
                        _referralScreenState.value = it.copy(
                            common = it.common.copy(activate = false, progress = false),
                            screen = it.screen.copy(
                                invitations = calcInvitationsCount(),
                                bonded = feeToken.formatBalance(referrerBalance)
                            )
                        )
                    }
                }
            }
        }
    }

    fun onActivateLinkClick(link: String) {
        viewModelScope.launch {
            tryCatch {
                _referralScreenState.value?.let {
                    _referralScreenState.value = it.copy(common = it.common.copy(progress = true))
                }
                val referrerOk = interactor.isLinkOk(link)
                if (referrerOk.first) {
                    val result = interactor.observeSetReferrer(referrerOk.second)
                    _extrinsicEvent.value = result
                    if (result) {
                        referrer = referrerOk.second
                    }
                } else {
                    _extrinsicEvent.value = false
                }
                _hideSheet.tryEmit(true)
                _referralScreenState.value?.let {
                    _referralScreenState.value =
                        it.copy(
                            common = it.common.copy(
                                referrer = referrer,
                                activate = false,
                                progress = false,
                            )
                        )
                }
            }
        }
    }

    private suspend fun reCalcOnChange(state: DetailedBottomSheet) {
        _referralScreenState.value?.let {
            val feeToken = feeToken()
            val bondFee = interactor.calcBondFee()
            val buttonActiveValidation = when (state) {
                DetailedBottomSheet.REQUEST_REFERRER -> {
                    interactor.isLinkOk(currentEnteredReferrerLink.orEmpty()).first
                }
                DetailedBottomSheet.SHOW_REFERRER -> {
                    true
                }
                DetailedBottomSheet.BOND -> {
                    validateFeePayment(bondFee + calcInvitationsAmount(bondInvitationsCount))
                }
                DetailedBottomSheet.UNBOND -> {
                    validateFeePayment(bondFee)
                }
            }
            val amount = calcInvitationsAmount(feeToken, bondInvitationsCount)
            _referralScreenState.value =
                it.copy(
                    common = it.common.copy(
                        progress = false,
                        activate = buttonActiveValidation
                    ),
                    bondState = it.bondState.copy(
                        invitationsCount = bondInvitationsCount,
                        invitationsAmount = amount,
                        balance = feeToken.formatBalance(xorBalance)
                    )
                )
        }
    }

    private fun calcInvitationsCount(): Int {
        return truncate((referrerBalance / setReferrerFee).toDouble()).toInt()
    }

    private fun calcInvitationsAmount(count: Int): BigDecimal =
        setReferrerFee.multiply(count.toBigDecimal())

    private fun calcInvitationsAmount(feeToken: Token, count: Int): String {
        val amount = calcInvitationsAmount(count)
        return feeToken.formatBalance(amount)
    }

    private fun Token.formatBalance(balance: BigDecimal): String =
        this.printBalance(
            balance = balance,
            nf = numbersFormatter,
            precision = 5,
        )

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
}
