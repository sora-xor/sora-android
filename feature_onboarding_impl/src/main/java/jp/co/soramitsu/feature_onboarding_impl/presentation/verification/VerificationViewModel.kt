/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.verification

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.common.util.ext.parseOtpCode
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class VerificationViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val progress: WithProgress,
    val timerWrapper: TimerWrapper
) : BaseViewModel(), WithProgress by progress {

    companion object {
        private const val SMS_CODE_LENGTH = 4

        const val SMS_CONSENT_REQUEST = 2
    }

    val timerLiveData = MutableLiveData<Pair<Int, Int>>()
    val timerFinishedLiveData = MutableLiveData<Event<Unit>>()

    val smsCodeStartActivityForResult = MutableLiveData<Intent>()
    val smsCodeAutofillLiveData = MutableLiveData<String>()

    val enableButtonLiveData = MutableLiveData<Boolean>()
    val resetCodeLiveData = MutableLiveData<Event<Unit>>()

    private var countryIso = ""

    fun onVerify(code: String) {
        if (code.trim().isEmpty()) {
            onError(SoraException.businessError(ResponseCode.SMS_CODE_NOT_CORRECT))
            enableButtonLiveData.value = true
        } else {
            disposables.add(
                interactor.verifySmsCode(code)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { progress.showProgress() }
                    .subscribe({
                        progress.hideProgress()
                        timerWrapper.cancel()
                        resetCodeLiveData.value = Event(Unit)
                        router.showPersonalInfo(countryIso)
                    }, {
                        progress.hideProgress()
                        enableButtonLiveData.value = true
                        onError(it)
                    })
            )
        }
    }

    fun requestNewCode() {
        disposables.add(
            interactor.requestNewCode()
                .observeOn(AndroidSchedulers.mainThread())
                .compose(progressCompose())
                .subscribe({
                    setTimer(it)
                }, {
                    onError(it)
                })
        )
    }

    fun backPressed() {
        disposables.add(
            interactor.changePersonalData()
                .subscribe({
                    router.onBackButtonPressed()
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun codeEntered(code: String) {
        if (code.length == SMS_CODE_LENGTH) {
            onVerify(code)
        }
    }

    fun setTimer(blockingTime: Int) {
        if (blockingTime != 0) {
            timerWrapper.setTimerCallbacks({ minutes, seconds ->
                timerLiveData.value = Pair(minutes, seconds)
            }, {
                timerFinishedLiveData.value = Event(Unit)
            })

            timerWrapper.start(blockingTime.toLong() * 1000, 1000)
        }
    }

    fun setCountryIso(iso: String) {
        countryIso = iso
    }

    fun onSmsReceive(intent: Intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
            val extras = intent.extras
            val smsRetrieverStatus = extras?.get(SmsRetriever.EXTRA_STATUS) as Status

            when (smsRetrieverStatus.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                    try {
                        smsCodeStartActivityForResult.value = consentIntent
                    } catch (e: ActivityNotFoundException) {}
                }
                CommonStatusCodes.TIMEOUT -> {}
            }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SMS_CONSENT_REQUEST ->
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)

                    smsCodeAutofillLiveData.value = message.parseOtpCode()
                }
        }
    }
}