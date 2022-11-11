/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.view.pincode.DotsProgressView
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

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

    private lateinit var action: PinCodeAction
    private var tempCode = ""
    private var isBiometryEnabled = false
    private var needsMigration: Boolean? = null
    private var isPincodeUpdateNeeded: Boolean = false
    private var isChanging = false
    private var triesUsed = 0
    private var buttonsDisabled = false
    private var timerStartedTimeStamp = 0L

    private val inputCodeLiveData = MutableLiveData<String>()

    val backButtonVisibilityLiveData = MutableLiveData<Boolean>()
    val toolbarTitleResLiveData = MutableLiveData<String>()
    val wrongPinCodeEventLiveData = SingleLiveEvent<Unit>()
    val showFingerPrintEventLiveData = SingleLiveEvent<Boolean>()
    val startFingerprintScannerEventLiveData = SingleLiveEvent<Boolean>()
    val fingerPrintDialogVisibilityLiveData = MutableLiveData<Boolean>()
    val fingerPrintAutFailedLiveData = SingleLiveEvent<Unit>()
    val fingerPrintErrorLiveData = SingleLiveEvent<String>()
    val pinCodeProgressLiveData = MediatorLiveData<Int>()
    val deleteButtonVisibilityLiveData = MediatorLiveData<Boolean>()
    val showTriesLeftSnackbar = SingleLiveEvent<String>()

    private val _closeAppLiveData = SingleLiveEvent<Unit>()
    val closeAppLiveData: LiveData<Unit> = _closeAppLiveData

    private val _fingerPrintCanceledFromPromptEvent = SingleLiveEvent<Unit>()
    val fingerPrintCanceledFromPromptEvent: LiveData<Unit> = _fingerPrintCanceledFromPromptEvent

    private val _checkInviteLiveData = SingleLiveEvent<Unit>()
    val checkInviteLiveData: LiveData<Unit> = _checkInviteLiveData

    private val _pincodeChangedEvent = SingleLiveEvent<Unit>()
    val pincodeChangedEvent: LiveData<Unit> = _pincodeChangedEvent

    private val _logoVisibilityLiveData = MutableLiveData<Boolean>()
    val logoVisibilityLiveData: LiveData<Boolean> = _logoVisibilityLiveData

    private val _logoutEvent = SingleLiveEvent<String>()
    val logoutEvent: LiveData<String> = _logoutEvent

    private val _biometryInitialDialogEvent = SingleLiveEvent<Unit>()
    val biometryInitialDialogEvent: LiveData<Unit> = _biometryInitialDialogEvent

    private val _resetApplicationEvent = SingleLiveEvent<Unit>()
    val resetApplicationEvent: LiveData<Unit> = _resetApplicationEvent

    private val _currentPincodeLength = MutableLiveData<Int>()
    val currentPincodeLength: LiveData<Int> = _currentPincodeLength

    private val _switchAccountEvent = SingleLiveEvent<Unit>()
    val switchAccountEvent: LiveData<Unit> = _switchAccountEvent

    var isReturningFromInfo = false
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

        pinCodeProgressLiveData.addSource(inputCodeLiveData) {
            pinCodeProgressLiveData.value = it.length
        }

        deleteButtonVisibilityLiveData.addSource(inputCodeLiveData) {
            deleteButtonVisibilityLiveData.setValueIfNew(it.isNotEmpty())
        }

        inputCodeLiveData.value = ""
    }

    private fun startTimer() {
        buttonsDisabled = true
        showFingerPrintEventLiveData.value = false
        createIdleTimer().start()
    }

    fun startAuth(pinCodeAction: PinCodeAction) {
        viewModelScope.launch {
            needsMigration = interactor.needsMigration()
        }
        viewModelScope.launch {
            action = pinCodeAction
            when (action) {
                PinCodeAction.CREATE_PIN_CODE -> {
                    _logoVisibilityLiveData.value = false
                    toolbarTitleResLiveData.value =
                        resourceManager.getString(R.string.pincode_set_your_pin_code)
                    backButtonVisibilityLiveData.value = false
                }
                PinCodeAction.OPEN_PASSPHRASE,
                PinCodeAction.OPEN_SEED,
                PinCodeAction.OPEN_JSON,
                PinCodeAction.LOGOUT,
                PinCodeAction.CUSTOM_NODE,
                PinCodeAction.SELECT_NODE -> {
                    _logoVisibilityLiveData.value = false
                    toolbarTitleResLiveData.value =
                        resourceManager.getString(R.string.pincode_enter_pin_code)
                    showFingerPrintEventLiveData.value = isBiometryEnabled
                    backButtonVisibilityLiveData.value = true
                }
                PinCodeAction.CHANGE_PIN_CODE -> {
                    isPincodeUpdateNeeded = interactor.isPincodeUpdateNeeded()
                    if (isPincodeUpdateNeeded) {
                        _currentPincodeLength.value = DotsProgressView.OLD_PINCODE_LENGTH
                    }
                    _logoVisibilityLiveData.value = false
                    toolbarTitleResLiveData.value =
                        resourceManager.getString(R.string.pincode_enter_current_pin_code)
                    showFingerPrintEventLiveData.value = isBiometryEnabled
                    backButtonVisibilityLiveData.value = true
                }
                PinCodeAction.TIMEOUT_CHECK -> {
                    try {
                        if (interactor.isCodeSet()) {
                            _logoVisibilityLiveData.value = true
                            toolbarTitleResLiveData.value =
                                resourceManager.getString(R.string.pincode_enter_pin_code)
                            showFingerPrintEventLiveData.value = isBiometryEnabled
                            backButtonVisibilityLiveData.value = false
                        } else {
                            _logoVisibilityLiveData.value = false
                            toolbarTitleResLiveData.value =
                                resourceManager.getString(R.string.pincode_set_your_pin_code)
                            backButtonVisibilityLiveData.value = false
                            action = PinCodeAction.CREATE_PIN_CODE
                        }
                    } catch (t: Throwable) {
                        onError(t)
                        _logoVisibilityLiveData.value = false
                        action = PinCodeAction.CREATE_PIN_CODE
                    }
                }
            }
        }
    }

    fun pinCodeNumberClicked(
        pinCodeNumber: String,
        maxProgress: Int = DotsProgressView.PINCODE_LENGTH
    ) {
        if (!buttonsDisabled) {
            inputCodeLiveData.value?.let { inputCode ->
                if (inputCode.length >= maxProgress) {
                    return
                }
                val newCode = inputCode + pinCodeNumber
                inputCodeLiveData.value = newCode
                if (newCode.length == maxProgress) {
                    pinCodeEntered(newCode)
                }
            }
        }
    }

    fun pinCodeDeleteClicked() {
        inputCodeLiveData.value?.let { inputCode ->
            if (inputCode.isEmpty()) {
                return
            }
            inputCodeLiveData.value = inputCode.substring(0, inputCode.length - 1)
        }
    }

    private fun pinCodeEntered(pin: String) {
        viewModelScope.launch {
            tryCatch {
                delay(COMPLETE_PIN_CODE_DELAY)
                if (PinCodeAction.CREATE_PIN_CODE == action) {
                    if (tempCode.isEmpty()) {
                        tempCode = pin
                        inputCodeLiveData.value = ""
                        toolbarTitleResLiveData.value =
                            resourceManager.getString(if (isChanging) R.string.pincode_confirm_new_pin_code else R.string.pincode_confirm_your_pin_code)
                        backButtonVisibilityLiveData.value = true
                    } else {
                        pinCodeEnterComplete(pin)
                    }
                } else {
                    checkPinCode(pin)
                }
            }
        }
    }

    private fun pinCodeEnterComplete(pinCode: String) {
        if (tempCode == pinCode) {
            registerPinCode(pinCode)
        } else {
            tempCode = ""
            inputCodeLiveData.value = ""
            toolbarTitleResLiveData.value =
                resourceManager.getString(if (isChanging) R.string.pincode_enter_new_pin_code else R.string.pincode_set_your_pin_code)
            backButtonVisibilityLiveData.value = false
            wrongPinCodeEventLiveData.trigger()
            deviceVibrator.makeShortVibration()
        }
    }

    private fun registerPinCode(code: String) {
        viewModelScope.launch {
            tryCatch {
                interactor.savePin(code)
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

    private suspend fun checkPinCode(code: String) {
        val result = interactor.checkPin(code)

        if (result) {
            processSuccessfullAuth()
        } else {
            inputCodeLiveData.value = ""
            wrongPinCodeEventLiveData.trigger()
            deviceVibrator.makeShortVibration()

            proccessFailedAuthIdleTimer()
        }
    }

    fun backPressed() {
        if (PinCodeAction.CREATE_PIN_CODE == action) {
            if (tempCode.isEmpty()) {
                if (isChanging && _currentPincodeLength.value == null) {
                    mainRouter.popBackStack()
                } else {
                    _closeAppLiveData.trigger()
                }
            } else {
                tempCode = ""
                inputCodeLiveData.value = ""
                backButtonVisibilityLiveData.value = false
                toolbarTitleResLiveData.value =
                    resourceManager.getString(R.string.pincode_set_your_pin_code)
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

    private fun proccessFailedAuthIdleTimer() {
        viewModelScope.launch {
            triesUsed++
            interactor.saveTriesUsed(triesUsed)

            if (triesUsed >= 3) {
                startTimer()
            } else {
                if (triesUsed == 2) {
                    showTriesLeftSnackbar.value =
                        resourceManager.getString(R.string.pincode_one_try_left)
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
                _logoutEvent.value = resourceManager.getString(R.string.logout_dialog_body) + resourceManager.getString(R.string.logout_remove_nodes_body)
            }
        }
    }

    fun onAuthenticationFailed() {
        fingerPrintAutFailedLiveData.trigger()
        deviceVibrator.makeShortVibration()
        proccessFailedAuthIdleTimer()
    }

    fun setBiometryAvailable(isAuthReady: Boolean) {
        viewModelScope.launch {
            tryCatch {
                interactor.setBiometryAvailable(isAuthReady)
            }
        }
    }

    fun logoutOkPressed() {
        progress.showProgress()
        viewModelScope.launch {
            if (mainInteractor.getSoraAccountsCount() == 1) {
                fullLogout()
            } else {
                logoutWithSwitchAccount()
            }
        }
    }

    private suspend fun fullLogout() {
        interactor.resetUser()
        progress.hideProgress()
        _resetApplicationEvent.trigger()
    }

    private suspend fun logoutWithSwitchAccount() {
        if (this::addresses.isInitialized) {
            val currentAddress = mainInteractor.getCurUserAddress()
            interactor.clearAccountData(addresses.first())
            if (currentAddress == addresses.first()) {
                mainInteractor.getSoraAccountsList().firstOrNull()?.let { account ->
                    mainInteractor.setCurSoraAccount(account.substrateAddress)
                }
            }

            progress.hideProgress()
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
        needsMigration?.let {
            if (it) {
                mainRouter.showClaim()
            } else {
                mainRouter.popBackStack()
            }
        }
    }

    private fun changePinProcessing() {
        viewModelScope.launch {
            action = PinCodeAction.CREATE_PIN_CODE
            if (currentPincodeLength.value == null) {
                needsMigration = false
            } else {
                if (currentPincodeLength.value != DotsProgressView.PINCODE_LENGTH) {
                    if (!isReturningFromInfo) {
                        mainRouter.showPinLengthInfo()
                        isReturningFromInfo = true
                        delay(COMPLETE_PIN_CODE_DELAY)
                    }

                    _currentPincodeLength.value = DotsProgressView.PINCODE_LENGTH
                }
            }
            isChanging = true
            isBiometryEnabled = false
            showFingerPrintEventLiveData.value = isBiometryEnabled
            toolbarTitleResLiveData.value =
                resourceManager.getString(R.string.pincode_enter_new_pin_code)
            inputCodeLiveData.value = ""
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
                toolbarTitleResLiveData.value = resourceManager.getString(
                    R.string.pin_locked_for_title,
                    "$minutesString $secondsString"
                )
            }

            override fun onFinish() {
                buttonsDisabled = false
                showFingerPrintEventLiveData.value = true
                viewModelScope.launch { interactor.resetTimerStartedTimestamp() }
                toolbarTitleResLiveData.value =
                    resourceManager.getString(R.string.pincode_enter_pin_code)
            }
        }
    }
}
