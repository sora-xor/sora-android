/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter

class PinCodeViewModel(
    private val interactor: PinCodeInteractor,
    private val router: MainRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    private lateinit var action: PinCodeAction
    private var tempCode = ""

    val backButtonVisibilityLiveData = MutableLiveData<Boolean>()
    val repeatPinCodeEventLiveData = MutableLiveData<Event<Unit>>()
    val resetPinCodeEventLiveData = MutableLiveData<Event<Unit>>()
    val setPinCodeEventLiveData = MutableLiveData<Event<Unit>>()
    val wrongPinCodeEventLiveData = MutableLiveData<Event<Unit>>()
    val checkPinCodeEventLiveData = MutableLiveData<Event<Unit>>()
    val startFingerprintScannerEventLiveData = MutableLiveData<Event<Unit>>()

    fun pinCodeEntered(pin: String) {
        if (PinCodeAction.CREATE_PIN_CODE == action) {
            if (tempCode.isEmpty()) {
                tempCode = pin
                repeatPinCodeEventLiveData.value = Event(Unit)
                backButtonVisibilityLiveData.value = true
            } else {
                pinCodeEnterComplete(pin)
            }
        } else {
            checkPinCode(pin)
        }
    }

    private fun pinCodeEnterComplete(pinCode: String) {
        if (tempCode == pinCode) {
            registerPinCode(pinCode)
        } else {
            tempCode = ""
            resetPinCodeEventLiveData.value = Event(Unit)
            setPinCodeEventLiveData.value = Event(Unit)
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
                    if (it.message != null && ResponseCode.DID_NOT_FOUND == ResponseCode.valueOf(it.message!!)) {
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

    fun fingerprintSuccess() {
        if (PinCodeAction.OPEN_PASSPHRASE == action) {
            router.hidePinCode()
            router.showPassphrase()
        } else {
            checkUser()
        }
    }

    fun onActivityCreated(pinCodeAction: PinCodeAction) {
        action = pinCodeAction
        when (action) {
            PinCodeAction.CREATE_PIN_CODE -> {
                setPinCodeEventLiveData.value = Event(Unit)
            }
            PinCodeAction.OPEN_PASSPHRASE -> {
                checkPinCodeEventLiveData.value = Event(Unit)
                backButtonVisibilityLiveData.value = true
            }
            PinCodeAction.TIMEOUT_CHECK -> {
                disposables.add(
                    interactor.isCodeSet()
                        .subscribe({
                            if (it) {
                                checkPinCodeEventLiveData.value = Event(Unit)
                                backButtonVisibilityLiveData.value = false
                            } else {
                                action = PinCodeAction.CREATE_PIN_CODE
                                setPinCodeEventLiveData.value = Event(Unit)
                            }
                        }, {
                            onError(it)
                            action = PinCodeAction.CREATE_PIN_CODE
                            setPinCodeEventLiveData.value = Event(Unit)
                        })
                )
            }
        }
        router.hideBottomView()
    }

    fun backPressed() {
        if (PinCodeAction.CREATE_PIN_CODE == action) {
            if (tempCode.isEmpty()) {
                router.closeApp()
            } else {
                tempCode = ""
                resetPinCodeEventLiveData.value = Event(Unit)
                setPinCodeEventLiveData.value = Event(Unit)
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
}