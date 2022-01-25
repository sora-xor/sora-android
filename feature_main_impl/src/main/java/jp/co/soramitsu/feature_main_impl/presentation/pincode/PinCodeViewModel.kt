/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PinCodeViewModel(
    private val interactor: PinCodeInteractor,
    private val mainRouter: MainRouter,
    private val progress: WithProgress,
    private val deviceVibrator: DeviceVibrator,
    private val maxPinCodeLength: Int
) : BaseViewModel(), WithProgress by progress {

    companion object {
        private const val COMPLETE_PIN_CODE_DELAY: Long = 12
    }

    private lateinit var action: PinCodeAction
    private var tempCode = ""
    private var isBiometryEnabled = false
    private var needsMigration: Boolean? = null
    private var isChanging = false

    private val inputCodeLiveData = MutableLiveData<String>()

    val backButtonVisibilityLiveData = MutableLiveData<Boolean>()
    val toolbarTitleResLiveData = MutableLiveData<Int>()
    val wrongPinCodeEventLiveData = SingleLiveEvent<Unit>()
    val showFingerPrintEventLiveData = SingleLiveEvent<Boolean>()
    val startFingerprintScannerEventLiveData = SingleLiveEvent<Boolean>()
    val fingerPrintDialogVisibilityLiveData = MutableLiveData<Boolean>()
    val fingerPrintAutFailedLiveData = SingleLiveEvent<Unit>()
    val fingerPrintErrorLiveData = SingleLiveEvent<String>()
    val pinCodeProgressLiveData = MediatorLiveData<Int>()
    val deleteButtonVisibilityLiveData = MediatorLiveData<Boolean>()

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

    private val _logoutEvent = SingleLiveEvent<Unit>()
    val logoutEvent: LiveData<Unit> = _logoutEvent

    private val _biometryInitialDialogEvent = SingleLiveEvent<Unit>()
    val biometryInitialDialogEvent: LiveData<Unit> = _biometryInitialDialogEvent

    private val _resetApplicationEvent = SingleLiveEvent<Unit>()
    val resetApplicationEvent: LiveData<Unit> = _resetApplicationEvent

    init {
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

    fun startAuth(pinCodeAction: PinCodeAction) {
        viewModelScope.launch {
            needsMigration = interactor.needsMigration()
        }
        viewModelScope.launch {
            action = pinCodeAction
            when (action) {
                PinCodeAction.CREATE_PIN_CODE -> {
                    _logoVisibilityLiveData.value = false
                    toolbarTitleResLiveData.value = R.string.pincode_set_your_pin_code
                    backButtonVisibilityLiveData.value = false
                }
                PinCodeAction.OPEN_PASSPHRASE, PinCodeAction.LOGOUT -> {
                    _logoVisibilityLiveData.value = false
                    toolbarTitleResLiveData.value = R.string.pincode_enter_pin_code
                    showFingerPrintEventLiveData.value = isBiometryEnabled
                    backButtonVisibilityLiveData.value = true
                }
                PinCodeAction.CHANGE_PIN_CODE -> {
                    _logoVisibilityLiveData.value = false
                    toolbarTitleResLiveData.value = R.string.pincode_enter_current_pin_code
                    showFingerPrintEventLiveData.value = isBiometryEnabled
                    backButtonVisibilityLiveData.value = true
                }
                PinCodeAction.TIMEOUT_CHECK -> {
                    try {
                        if (interactor.isCodeSet()) {
                            _logoVisibilityLiveData.value = true
                            toolbarTitleResLiveData.value = R.string.pincode_enter_pin_code
                            showFingerPrintEventLiveData.value = isBiometryEnabled
                            backButtonVisibilityLiveData.value = false
                        } else {
                            _logoVisibilityLiveData.value = false
                            toolbarTitleResLiveData.value =
                                R.string.pincode_set_your_pin_code
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

    fun pinCodeNumberClicked(pinCodeNumber: String) {
        inputCodeLiveData.value?.let { inputCode ->
            if (inputCode.length >= maxPinCodeLength) {
                return
            }
            val newCode = inputCode + pinCodeNumber
            inputCodeLiveData.value = newCode
            if (newCode.length == maxPinCodeLength) {
                pinCodeEntered(newCode)
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
                            if (isChanging) R.string.pincode_confirm_new_pin_code else R.string.pincode_confirm_your_pin_code
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
                if (isChanging) R.string.pincode_enter_new_pin_code else R.string.pincode_set_your_pin_code
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
            when (action) {
                PinCodeAction.OPEN_PASSPHRASE -> {
                    mainRouter.popBackStack()
                    mainRouter.showPassphrase()
                }
                PinCodeAction.LOGOUT -> {
                    _logoutEvent.trigger()
                }
                PinCodeAction.TIMEOUT_CHECK, PinCodeAction.CREATE_PIN_CODE -> {
                    proceed()
                }
                PinCodeAction.CHANGE_PIN_CODE -> {
                    action = PinCodeAction.CREATE_PIN_CODE
                    isChanging = true
                    isBiometryEnabled = false
                    needsMigration = false
                    showFingerPrintEventLiveData.value = isBiometryEnabled
                    toolbarTitleResLiveData.value = R.string.pincode_enter_new_pin_code
                    inputCodeLiveData.value = ""
                }
            }
        } else {
            inputCodeLiveData.value = ""
            wrongPinCodeEventLiveData.trigger()
            deviceVibrator.makeShortVibration()
        }
    }

    fun backPressed() {
        if (PinCodeAction.CREATE_PIN_CODE == action) {
            if (tempCode.isEmpty()) {
                if (isChanging) {
                    mainRouter.popBackStack()
                } else {
                    _closeAppLiveData.trigger()
                }
            } else {
                tempCode = ""
                inputCodeLiveData.value = ""
                backButtonVisibilityLiveData.value = false
                toolbarTitleResLiveData.value = R.string.pincode_set_your_pin_code
            }
        } else {
            if (PinCodeAction.TIMEOUT_CHECK == action) {
                _closeAppLiveData.trigger()
            } else {
                mainRouter.popBackStack()
            }
        }
    }

    fun onResume() {
        if (action != PinCodeAction.CREATE_PIN_CODE) {
            startFingerprintScannerEventLiveData.value = isBiometryEnabled
        }
    }

    fun onAuthenticationError(errString: String) {
        fingerPrintErrorLiveData.value = errString
    }

    fun onAuthenticationSucceeded() {
        when (action) {
            PinCodeAction.OPEN_PASSPHRASE -> {
                mainRouter.popBackStack()
                mainRouter.showPassphrase()
            }
            PinCodeAction.LOGOUT -> {
                _logoutEvent.trigger()
            }
            PinCodeAction.CHANGE_PIN_CODE -> {
                action = PinCodeAction.CREATE_PIN_CODE
                isChanging = true
                isBiometryEnabled = false
                showFingerPrintEventLiveData.value = isBiometryEnabled
                toolbarTitleResLiveData.value = R.string.pincode_enter_new_pin_code
                inputCodeLiveData.value = ""
            }
            else -> {
                proceed()
            }
        }
    }

    fun onAuthenticationFailed() {
        fingerPrintAutFailedLiveData.trigger()
        deviceVibrator.makeShortVibration()
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
            interactor.resetUser()
            progress.hideProgress()
            _resetApplicationEvent.trigger()
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

    fun canceledFromPrompt() {
        _fingerPrintCanceledFromPromptEvent.trigger()
    }
}
