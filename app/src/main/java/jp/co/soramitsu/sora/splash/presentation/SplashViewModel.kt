/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.presentation

import androidx.lifecycle.ViewModel
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import jp.co.soramitsu.sora.splash.domain.SplashRouter

class SplashViewModel(
    private val interactor: SplashInteractor,
    private val router: SplashRouter
) : ViewModel() {

    fun nextScreen() {
        when (val state = interactor.getRegistrationState()) {
            OnboardingState.REGISTRATION_FINISHED -> {
                interactor.restoreAuth()
                router.showMainScreen()
            }
            OnboardingState.PHONE_NUMBER_CONFIRMED -> {
                interactor.restoreAuth()
                router.showOnBoardingScreen(state)
            }
            OnboardingState.INITIAL -> {
                router.showOnBoardingScreen(state)
            }
            OnboardingState.PERSONAL_DATA_ENTERED -> {
                interactor.saveRegistrationState(OnboardingState.INITIAL)
                router.showOnBoardingScreen(state)
            }
            OnboardingState.SMS_REQUESTED -> {
                interactor.saveRegistrationState(OnboardingState.INITIAL)
                router.showOnBoardingScreen(state)
            }
            OnboardingState.PIN_CODE_SET -> {
                interactor.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
                interactor.restoreAuth()
                router.showMainScreen()
            }
        }
    }

    fun handleDeepLink(invitationCode: String) {
        val state = interactor.getRegistrationState()

        if (OnboardingState.INITIAL == state || OnboardingState.PHONE_NUMBER_CONFIRMED == state) {
            interactor.saveInviteCode(invitationCode)
            router.showOnBoardingScreen(state)
        } else {
            router.showMainScreenFromInviteLink()
        }
    }
}