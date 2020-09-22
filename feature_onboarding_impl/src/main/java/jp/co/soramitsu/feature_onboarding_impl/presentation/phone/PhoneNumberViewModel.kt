/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.phone

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class PhoneNumberViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    private var countryIso = ""

    private val _nextButtonEnableLiveData = MutableLiveData<Boolean>()
    val nextButtonEnableLiveData: LiveData<Boolean> = _nextButtonEnableLiveData

    fun onPhoneEntered(phoneCode: String, phoneNumber: String) {
        disposables.add(
            interactor.createUser("$phoneCode$phoneNumber")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .subscribe({
                    hideProgress()
                    if (it.alreadyVerified) {
                        router.showPersonalInfo(countryIso)
                    } else {
                        router.showVerification(countryIso, it.blockingTimeForSms)
                    }
                }, {
                    hideProgress()
                    onError(it)
                })
        )
    }

    fun backButtonClick() {
        router.onBackButtonPressed()
    }

    fun setCountryIso(iso: String) {
        countryIso = iso
    }

    fun onPhoneChanged(s: CharSequence) {
        _nextButtonEnableLiveData.value = s.length >= 3
    }
}