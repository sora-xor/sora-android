/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.os.CountDownTimer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.SnackBarState
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class PinCodeViewModel @Inject constructor(
    private val interactor: PinCodeInteractor,
    private val mainInteractor: MainInteractor,
    private val mainRouter: MainRouter,
    private val resourceManager: ResourceManager,
    private val selectNodeRouter: SelectNodeRouter,
    private val progress: WithProgress,
    private val deviceVibrator: DeviceVibrator,
) : BaseViewModel(), WithProgress by progress {

    @Suppress("UNCHECKED_CAST")
    companion object {
        private const val COMPLETE_PIN_CODE_DELAY: Long = 12
    }

    internal var state by mutableStateOf(
        PinCodeScreenState(
            maxDotsCount = PinCodeInteractor.PINCODE_LENGTH,
            toolbarTitleString = ""
        )
    )
        private set

    private lateinit var action: PinCodeAction
    private var tempCode = ""
    private var isBiometryEnabled = false
    private var isPincodeUpdateNeeded: Boolean = false
    private var isChanging = false
    private var triesUsed = 0
    private var buttonsDisabled = false
    private var timerStartedTimeStamp = 0L

    private var inputedCode = ""

    val showFingerPrintEventLiveData = SingleLiveEvent<Boolean>()
    val startFingerprintScannerEventLiveData = SingleLiveEvent<Boolean>()
    val fingerPrintDialogVisibilityLiveData = MutableLiveData<Boolean>()
    val fingerPrintErrorLiveData = SingleLiveEvent<String>()

    private val _closeAppLiveData = SingleLiveEvent<Unit>()
    val closeAppLiveData: LiveData<Unit> = _closeAppLiveData

    private val _pincodeLengthInfoAlertLiveData = SingleLiveEvent<Unit>()
    val pincodeLengthInfoAlertLiveData: LiveData<Unit> = _pincodeLengthInfoAlertLiveData

    private val _fingerPrintCanceledFromPromptEvent = SingleLiveEvent<Unit>()
    val fingerPrintCanceledFromPromptEvent: LiveData<Unit> = _fingerPrintCanceledFromPromptEvent

    private val _checkInviteLiveData = SingleLiveEvent<Unit>()
    val checkInviteLiveData: LiveData<Unit> = _checkInviteLiveData

    private val _pincodeChangedEvent = SingleLiveEvent<Unit>()
    val pincodeChangedEvent: LiveData<Unit> = _pincodeChangedEvent

    private val _logoutEvent = SingleLiveEvent<String>()
    val logoutEvent: LiveData<String> = _logoutEvent

    private val _biometryInitialDialogEvent = SingleLiveEvent<Unit>()
    val biometryInitialDialogEvent: LiveData<Unit> = _biometryInitialDialogEvent

    private val _resetApplicationEvent = SingleLiveEvent<Unit>()
    val resetApplicationEvent: LiveData<Unit> = _resetApplicationEvent

    private val _switchAccountEvent = SingleLiveEvent<Unit>()
    val switchAccountEvent: LiveData<Unit> = _switchAccountEvent

    lateinit var addresses: List<String>

    init {
        viewModelScope.launch {
            triesUsed = interactor.retrieveTriesUsed()
            timerStartedTimeStamp = interactor.retrieveTimerStartedTimestamp()

            if (timerStartedTimeStamp > 0L) {
                startTimer()

                if (!buttonsDisabled) {
                    interactor.resetTimerStartedTimestamp()
                    timerStartedTimeStamp = 0
                }
            }
        }

        viewModelScope.launch {
            tryCatch {
                isBiometryEnabled = interactor.isBiometryEnabled()
            }
        }
    }

    private fun startTimer() {
        buttonsDisabled = true
        showFingerPrintEventLiveData.value = false
        createIdleTimer().start()
    }

    fun startAuth(pinCodeAction: PinCodeAction) {
        viewModelScope.launch {
            action = pinCodeAction
            when (action) {
                PinCodeAction.CREATE_PIN_CODE -> {
                    state = state.copy(
                        toolbarTitleString = resourceManager.getString(R.string.pincode_set_your_pin_code),
                        isBackButtonVisible = false
                    )
                }
                PinCodeAction.OPEN_PASSPHRASE,
                PinCodeAction.OPEN_SEED,
                PinCodeAction.OPEN_JSON,
                PinCodeAction.LOGOUT,
                PinCodeAction.CUSTOM_NODE,
                PinCodeAction.SELECT_NODE -> {
                    state = state.copy(
                        toolbarTitleString = resourceManager.getString(R.string.pincode_enter_pin_code),
                        isBiometricButtonVisible = true
                    )
                    showFingerPrintEventLiveData.value = isBiometryEnabled
                }
                PinCodeAction.CHANGE_PIN_CODE -> {
                    isPincodeUpdateNeeded = interactor.isPincodeUpdateNeeded()
                    val maxDotsLength = if (isPincodeUpdateNeeded) {
                        PinCodeInteractor.OLD_PINCODE_LENGTH
                    } else {
                        PinCodeInteractor.PINCODE_LENGTH
                    }
                    state = state.copy(
                        maxDotsCount = maxDotsLength,
                        toolbarTitleString = resourceManager.getString(R.string.pincode_enter_current_pin_code),
                        isBackButtonVisible = true
                    )

                    showFingerPrintEventLiveData.value = isBiometryEnabled
                }
                PinCodeAction.TIMEOUT_CHECK -> {
                    try {
                        if (interactor.isCodeSet()) {
                            state = state.copy(
                                toolbarTitleString = resourceManager.getString(R.string.pincode_enter_pin_code),
                                isBackButtonVisible = false
                            )
                            showFingerPrintEventLiveData.value = isBiometryEnabled
                        } else {
                            state = state.copy(
                                toolbarTitleString = resourceManager.getString(R.string.pincode_set_your_pin_code),
                                isBackButtonVisible = false
                            )
                            showFingerPrintEventLiveData.value = isBiometryEnabled
                            action = PinCodeAction.CREATE_PIN_CODE
                        }
                    } catch (t: Throwable) {
                        onError(t)
                        action = PinCodeAction.CREATE_PIN_CODE
                    }
                }
            }
        }
    }

    fun pinCodeNumberClicked(
        pinCodeNumber: String,
    ) {
        if (!buttonsDisabled) {
            if (inputedCode.length >= state.maxDotsCount) {
                return
            }
            inputedCode += pinCodeNumber
            state = state.copy(
                checkedDotsCount = inputedCode.length,
                isBackButtonVisible = inputedCode.isNotEmpty()
            )
            if (inputedCode.length == state.maxDotsCount) {
                pinCodeEntered()
            }
        }
    }

    fun pinCodeDeleteClicked() {
        if (inputedCode.isEmpty()) {
            return
        }
        inputedCode = inputedCode.substring(0, inputedCode.length - 1)
        state = state.copy(
            checkedDotsCount = inputedCode.length,
            isBackButtonVisible = inputedCode.isNotEmpty()
        )
    }

    fun onWrongPinAnimationEnd() {
        state = state.copy(enableShakeAnimation = false)
    }

    private fun pinCodeEntered() {
        viewModelScope.launch {
            tryCatch {
                delay(COMPLETE_PIN_CODE_DELAY)
                if (PinCodeAction.CREATE_PIN_CODE == action) {
                    if (tempCode.isEmpty()) {
                        tempCode = inputedCode
                        state = state.copy(
                            toolbarTitleString = resourceManager.getString(
                                if (isChanging) R.string.pincode_confirm_new_pin_code else R.string.pincode_confirm_your_pin_code
                            ),
                            isBackButtonVisible = false,
                            checkedDotsCount = 0
                        )
                        inputedCode = ""
                    } else {
                        pinCodeEnterComplete()
                    }
                } else {
                    checkPinCode()
                }
            }
        }
    }

    private fun pinCodeEnterComplete() {
        if (tempCode == inputedCode) {
            registerPinCode()
        } else {
            tempCode = ""
            inputedCode = ""
            state = state.copy(
                toolbarTitleString = resourceManager.getString(
                    if (isChanging) R.string.pincode_enter_new_pin_code else R.string.pincode_set_your_pin_code
                ),
                isBackButtonVisible = false,
                checkedDotsCount = 0,
                enableShakeAnimation = true
            )
            deviceVibrator.makeShortVibration()
        }
    }

    private fun registerPinCode() {
        viewModelScope.launch {
            tryCatch {
                interactor.savePin(inputedCode)
                if (isChanging) {
                    _pincodeChangedEvent.trigger()
                    mainRouter.popBackStack()
                } else {
                    val available = interactor.isBiometryAvailable()
                    if (available) {
                        _biometryInitialDialogEvent.trigger()
                    } else {
                        proceed()
                    }
                }
            }
        }
    }

    private suspend fun checkPinCode() {
        val result = interactor.checkPin(inputedCode)

        if (result) {
            processSuccessfullAuth()
        } else {
            inputedCode = ""
            state = state.copy(
                isBackButtonVisible = false,
                checkedDotsCount = 0,
                enableShakeAnimation = true
            )
            deviceVibrator.makeShortVibration()
            proccessFailedAuthIdleTimer(true)
        }
    }

    override fun onBackPressed() {
        if (PinCodeAction.CREATE_PIN_CODE == action) {
            if (tempCode.isEmpty()) {
                if (isChanging && !isPincodeUpdateNeeded) {
                    mainRouter.popBackStack()
                } else {
                    _closeAppLiveData.trigger()
                }
            } else {
                tempCode = ""
                inputedCode = ""

                state = state.copy(
                    toolbarTitleString = resourceManager.getString(R.string.pincode_set_your_pin_code),
                    isBackButtonVisible = false,
                    checkedDotsCount = 0
                )
            }
        } else {
            if (PinCodeAction.TIMEOUT_CHECK == action || isPincodeUpdateNeeded) {
                _closeAppLiveData.trigger()
            } else {
                mainRouter.popBackStack()
            }
        }
    }

    fun onResume() {
        if (action != PinCodeAction.CREATE_PIN_CODE && !buttonsDisabled) {
            startFingerprintScannerEventLiveData.value = isBiometryEnabled
        }
    }

    fun onAuthenticationError(errString: String) {
        fingerPrintErrorLiveData.value = errString
    }

    fun onAuthenticationSucceeded() {
        processSuccessfullAuth()
    }

    private fun proccessFailedAuthIdleTimer(countAttempts: Boolean) {
        viewModelScope.launch {
            if (countAttempts) triesUsed++
            interactor.saveTriesUsed(triesUsed)

            if (triesUsed >= 3) {
                startTimer()
            } else {
                if (triesUsed == 2) {
                    snackBarLiveData.value = SnackBarState(resourceManager.getString(R.string.pincode_one_try_left))
                }
            }
        }
    }

    private fun processSuccessfullAuth() {
        triesUsed = 0
        viewModelScope.launch {
            interactor.resetTriesUsed()
            interactor.resetTimerStartedTimestamp()

            when (action) {
                PinCodeAction.OPEN_PASSPHRASE -> {
                    mainRouter.popBackStack()
                    if (this@PinCodeViewModel::addresses.isInitialized) {
                        mainRouter.showBackupPassphrase(addresses.first())
                    }
                }
                PinCodeAction.OPEN_JSON -> {
                    mainRouter.popBackStack()
                    if (this@PinCodeViewModel::addresses.isInitialized) {
                        mainRouter.showBackupJson(addresses)
                    }
                }
                PinCodeAction.OPEN_SEED -> {
                    mainRouter.popBackStack()
                    if (this@PinCodeViewModel::addresses.isInitialized) {
                        mainRouter.showBackupSeed(addresses.first())
                    }
                }
                PinCodeAction.LOGOUT -> {
                    logout()
                }
                PinCodeAction.CHANGE_PIN_CODE -> {
                    changePinProcessing()
                }
                PinCodeAction.CUSTOM_NODE,
                PinCodeAction.SELECT_NODE -> {
                    selectNodeRouter.returnFromPinCodeCheck()
                }
                else -> {
                    proceed()
                }
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            val multiAccount = mainInteractor.getSoraAccountsCount() > 1

            if (multiAccount) {
                _logoutEvent.value = resourceManager.getString(R.string.logout_dialog_body)
            } else {
                _logoutEvent.value =
                    resourceManager.getString(R.string.logout_dialog_body) + resourceManager.getString(
                        R.string.logout_remove_nodes_body
                    )
            }
        }
    }

    fun onAuthenticationFailed() {
        deviceVibrator.makeShortVibration()
        proccessFailedAuthIdleTimer(false)
    }

    fun setBiometryAvailable(isAuthReady: Boolean) {
        viewModelScope.launch {
            tryCatch {
                interactor.setBiometryAvailable(isAuthReady)
            }
        }
    }

    fun logoutOkPressed() {
        viewModelScope.launch {
            progress.showProgress()
            if (mainInteractor.getSoraAccountsCount() == 1) {
                fullLogout()
            } else {
                logoutWithSwitchAccount()
            }
            progress.hideProgress()
        }
    }

    private suspend fun fullLogout() {
        interactor.fullLogout()
        _resetApplicationEvent.trigger()
    }

    private suspend fun logoutWithSwitchAccount() {
        if (this::addresses.isInitialized && addresses.size == 1) {
            val currentAddress = mainInteractor.getCurUserAddress()
            interactor.clearAccountData(addresses.first())
            if (currentAddress == addresses.first()) {
                mainInteractor.getSoraAccountsList().firstOrNull()?.let { account ->
                    mainInteractor.setCurSoraAccount(account)
                }
            }
            _switchAccountEvent.trigger()
        }
    }

    fun biometryDialogYesClicked() {
        viewModelScope.launch {
            tryCatch {
                interactor.setBiometryEnabled(true)
                proceed()
            }
        }
    }

    fun biometryDialogNoClicked() {
        viewModelScope.launch {
            tryCatch {
                interactor.setBiometryEnabled(false)
                proceed()
            }
        }
    }

    private fun proceed() {
        viewModelScope.launch {
            if (interactor.needsMigration()) {
                mainRouter.showClaim()
            } else {
                mainRouter.popBackStack()
            }
        }
    }

    private fun changePinProcessing() {
        viewModelScope.launch {
            action = PinCodeAction.CREATE_PIN_CODE
            if (state.maxDotsCount != PinCodeInteractor.PINCODE_LENGTH) {
                _pincodeLengthInfoAlertLiveData.trigger()
                state = state.copy(maxDotsCount = PinCodeInteractor.PINCODE_LENGTH, isLengthInfoAlertVisible = true)
            }
            isChanging = true
            isBiometryEnabled = false
            showFingerPrintEventLiveData.value = isBiometryEnabled
            state =
                state.copy(
                    toolbarTitleString = resourceManager.getString(R.string.pincode_enter_new_pin_code),
                    checkedDotsCount = 0
                )
            inputedCode = ""
        }
    }

    fun canceledFromPrompt() {
        _fingerPrintCanceledFromPromptEvent.trigger()
    }

    fun popBackToAccountList() {
        mainRouter.popBackStackToAccountList()
    }

    private fun getMillisFromCounter(counter: Int): Long {
        return when (counter) {
            3 -> 1
            4 -> 5
            5 -> 15
            in 6..Int.MAX_VALUE -> 30
            else -> 0
        } * 60 * 1000L
    }

    private fun createIdleTimer(): CountDownTimer {
        val currentTime = Date().time
        val time = if (timerStartedTimeStamp == 0L) {
            viewModelScope.launch { interactor.saveTimerStartedTimestamp(currentTime) }
            getMillisFromCounter(triesUsed)
        } else {
            getMillisFromCounter(triesUsed) - (currentTime - timerStartedTimeStamp)
        }

        return object : CountDownTimer(time, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val allSecondsLeft = millisUntilFinished / 1000
                val minutesLeft = allSecondsLeft / 60
                val secondsLeftWithoutMinutes = allSecondsLeft - minutesLeft * 60

                val minutesString = "$minutesLeft ${resourceManager.getString(R.string.common_min)}"
                val secondsString =
                    "$secondsLeftWithoutMinutes ${resourceManager.getString(R.string.common_sec)}"
                state = state.copy(
                    toolbarTitleString = resourceManager.getString(
                        R.string.pin_locked_for_title,
                        "$minutesString $secondsString"
                    )
                )
            }

            override fun onFinish() {
                buttonsDisabled = false
                showFingerPrintEventLiveData.value = true
                viewModelScope.launch { interactor.resetTimerStartedTimestamp() }
                state =
                    state.copy(toolbarTitleString = resourceManager.getString(R.string.pincode_enter_pin_code))
            }
        }
    }

    fun changeFingerPrintButtonVisibility(isVisible: Boolean) {
        state = state.copy(isBiometricButtonVisible = isVisible)
    }

    fun setNewLengthPinCodeClicked() {
        state = state.copy(isLengthInfoAlertVisible = false, isBackButtonVisible = false)
    }
}
