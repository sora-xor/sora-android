/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint.FingerPrintListener
import java.util.concurrent.TimeUnit

class PinCodeViewModel(
    private val interactor: PinCodeInteractor,
    private val router: MainRouter,
    private val progress: WithProgress,
    private val maxPinCodeLength: Int
) : BaseViewModel(), WithProgress by progress, FingerPrintListener {

    companion object {
        private const val COMPLETE_PIN_CODE_DELAY: Long = 12
    }

    private lateinit var action: PinCodeAction
    private var tempCode = ""

    private val inputCodeLiveData = MutableLiveData<String>()

    val backButtonVisibilityLiveData = MutableLiveData<Boolean>()
    val toolbarTitleResLiveData = MutableLiveData<Int>()
    val wrongPinCodeEventLiveData = MutableLiveData<Event<Unit>>()
    val showFingerPrintEventLiveData = MutableLiveData<Event<Unit>>()
    val startFingerprintScannerEventLiveData = MutableLiveData<Event<Unit>>()
    val fingerPrintDialogVisibilityLiveData = MutableLiveData<Boolean>()
    val fingerPrintAutFailedLiveData = MutableLiveData<Event<Unit>>()
    val fingerPrintErrorLiveData = MutableLiveData<Event<String>>()
    val pinCodeProgressLiveData = MediatorLiveData<Int>()
    val deleteButtonVisibilityLiveData = MediatorLiveData<Boolean>()

    init {
        pinCodeProgressLiveData.addSource(inputCodeLiveData) {
            pinCodeProgressLiveData.value = it.length
        }

        deleteButtonVisibilityLiveData.addSource(inputCodeLiveData) {
            deleteButtonVisibilityLiveData.setValueIfNew(it.isNotEmpty())
        }

        inputCodeLiveData.value = ""
    }

    fun startAuth(pinCodeAction: PinCodeAction) {
        action = pinCodeAction
        when (action) {
            PinCodeAction.CREATE_PIN_CODE -> {
                toolbarTitleResLiveData.value = R.string.pincode_title
                backButtonVisibilityLiveData.value = false
            }
            PinCodeAction.OPEN_PASSPHRASE -> {
                toolbarTitleResLiveData.value = R.string.pincode_title_check
                showFingerPrintEventLiveData.value = Event(Unit)
                backButtonVisibilityLiveData.value = true
            }
            PinCodeAction.TIMEOUT_CHECK -> {
                disposables.add(
                    interactor.isCodeSet()
                        .subscribe({
                            if (it) {
                                toolbarTitleResLiveData.value = R.string.pincode_title_check
                                showFingerPrintEventLiveData.value = Event(Unit)
                                backButtonVisibilityLiveData.value = false
                            } else {
                                toolbarTitleResLiveData.value = R.string.pincode_title
                                backButtonVisibilityLiveData.value = false
                                action = PinCodeAction.CREATE_PIN_CODE
                            }
                        }, {
                            onError(it)
                            action = PinCodeAction.CREATE_PIN_CODE
                        })
                )
            }
        }
        router.hideBottomView()
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
        disposables.add(
            Completable.complete()
                .delay(COMPLETE_PIN_CODE_DELAY, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (PinCodeAction.CREATE_PIN_CODE == action) {
                        if (tempCode.isEmpty()) {
                            tempCode = pin
                            inputCodeLiveData.value = ""
                            toolbarTitleResLiveData.value = R.string.pincode_title2
                            backButtonVisibilityLiveData.value = true
                        } else {
                            pinCodeEnterComplete(pin)
                        }
                    } else {
                        checkPinCode(pin)
                    }
                }, {
                    logException(it)
                })
        )
    }

    private fun pinCodeEnterComplete(pinCode: String) {
        if (tempCode == pinCode) {
            registerPinCode(pinCode)
        } else {
            tempCode = ""
            inputCodeLiveData.value = ""
            toolbarTitleResLiveData.value = R.string.pincode_title
            backButtonVisibilityLiveData.value = false
            onError(R.string.pincode_repeat_error)
        }
    }

    private fun registerPinCode(code: String) {
        disposables.add(
            interactor.savePin(code)
                .subscribe({
                    router.hidePinCode()
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun checkPinCode(code: String) {
        disposables.add(
            interactor.checkPin(code)
                .subscribe({
                    if (PinCodeAction.OPEN_PASSPHRASE == action) {
                        router.hidePinCode()
                        router.showPassphrase()
                    } else {
                        checkUser()
                    }
                }, {
                    it.printStackTrace()
                    inputCodeLiveData.value = ""
                    wrongPinCodeEventLiveData.value = Event(Unit)
                })
        )
    }

    private fun checkUser() {
        disposables.add(
            interactor.runCheckUserFlow()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { progress.showProgress() }
                .subscribe({
                    progress.hideProgress()
                    if (it.supported) {
                        router.hidePinCode()
                    } else {
                        router.showUnsupportedScreen(it.downloadUrl)
                    }
                }, {
                    progress.hideProgress()
                    if (it is SoraException && it.kind == SoraException.Kind.BUSINESS && ResponseCode.DID_NOT_FOUND == it.errorResponseCode) {
                        resetUser()
                    } else {
                        onError(it)
                    }
                })
        )
    }

    private fun resetUser() {
        disposables.add(
            interactor.resetUser()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    router.restartApp()
                }, {
                    onError(it)
                })
        )
    }

    fun backPressed() {
        if (PinCodeAction.CREATE_PIN_CODE == action) {
            if (tempCode.isEmpty()) {
                router.closeApp()
            } else {
                tempCode = ""
                inputCodeLiveData.value = ""
                backButtonVisibilityLiveData.value = false
                toolbarTitleResLiveData.value = R.string.pincode_title
            }
        } else {
            if (PinCodeAction.TIMEOUT_CHECK == action) {
                router.closeApp()
            } else {
                router.hidePinCode()
            }
        }
    }

    fun onResume() {
        if (action != PinCodeAction.CREATE_PIN_CODE) {
            startFingerprintScannerEventLiveData.value = Event(Unit)
        }
    }

    override fun onFingerPrintSuccess() {
        if (PinCodeAction.OPEN_PASSPHRASE == action) {
            router.hidePinCode()
            router.showPassphrase()
        } else {
            checkUser()
        }
    }

    override fun onAuthFailed() {
        fingerPrintAutFailedLiveData.value = Event(Unit)
    }

    override fun onAuthenticationHelp(message: String) {
        fingerPrintErrorLiveData.value = Event(message)
    }

    override fun onAuthenticationError(message: String) {
        fingerPrintErrorLiveData.value = Event(message)
    }

    override fun showFingerPrintDialog() {
        fingerPrintDialogVisibilityLiveData.value = true
    }

    override fun hideFingerPrintDialog() {
        fingerPrintDialogVisibilityLiveData.value = false
    }
}