/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.tutorial

import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class TutorialViewModel(
    private val router: OnboardingRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    fun onSignUpClicked() {
        router.showPersonalInfo()
//        disposables.add(
//            interactor.runRegisterFlow()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .doOnSubscribe { progress.showProgress() }
//                .subscribe(
//                    {
//                        progress.hideProgress()
//                        if (it.supported) {
//                            router.showPersonalInfo()
//                        } else {
//                            router.showUnsupportedScreen(it.downloadUrl)
//                        }
//                    },
//                    {
//                        progress.hideProgress()
//                        onError(it)
//                    }
//                )
//        )
    }

    fun onRecoveryClicked() {
        router.showRecovery()
//        disposables.add(
//            interactor.checkVersionIsSupported()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .doOnSubscribe { progress.showProgress() }
//                .subscribe(
//                    {
//                        progress.hideProgress()
//                        if (it.supported) {
//                            router.showRecovery()
//                        } else {
//                            router.showUnsupportedScreen(it.downloadUrl)
//                        }
//                    },
//                    {
//                        progress.hideProgress()
//                        onError(it)
//                    }
//                )
//        )
    }
}
