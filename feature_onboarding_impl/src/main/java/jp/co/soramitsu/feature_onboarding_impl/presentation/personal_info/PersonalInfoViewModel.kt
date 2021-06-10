/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info

import androidx.lifecycle.LiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class PersonalInfoViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    private val _screenshotAlertDialogEvent = SingleLiveEvent<Unit>()
    val screenshotAlertDialogEvent: LiveData<Unit> = _screenshotAlertDialogEvent

    fun register(accountName: String) {
        disposables.add(
            interactor.createUser(accountName)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .doFinally { hideProgress() }
                .subscribe(
                    {
                        _screenshotAlertDialogEvent.trigger()
                    },
                    {
                        onError(it)
                    }
                )
        )
    }

    fun backButtonClick() {
        router.onBackButtonPressed()
    }

    fun screenshotAlertOkClicked() {
        router.showMnemonic()
    }

    fun showTermsScreen() {
        router.showTermsScreen()
    }

    fun showPrivacyScreen() {
        router.showPrivacyScreen()
    }
}
