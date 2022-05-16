/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import jp.co.soramitsu.sora.splash.domain.SplashRouter
import kotlinx.coroutines.launch

class SplashViewModel(
    private val interactor: SplashInteractor,
    private val router: SplashRouter,
    private val runtimeManager: RuntimeManager,
) : BaseViewModel() {

    private val _runtimeInitiated = MutableLiveData<Boolean>()
    val runtimeInitiated: LiveData<Boolean> = _runtimeInitiated

    init {
        viewModelScope.launch {
            tryCatch {
                runtimeManager.start()
                _runtimeInitiated.value = true
            }
        }
        viewModelScope.launch {
            interactor.checkMigration()
        }
    }

    fun nextScreen() {
        viewModelScope.launch {
            val migrationDone = interactor.getMigrationDoneAsync().await()
            FirebaseWrapper.log("Splash next screen $migrationDone")
            when (val state = interactor.getRegistrationState()) {
                OnboardingState.REGISTRATION_FINISHED -> {
                    interactor.initCurSoraAccount()
                    router.showMainScreen()
                }
                OnboardingState.INITIAL -> {
                    router.showOnBoardingScreen(state)
                }
            }
        }
    }

    fun handleDeepLink(invitationCode: String) {
        viewModelScope.launch {
            val state = interactor.getRegistrationState()
            interactor.saveInviteCode(invitationCode)

            if (OnboardingState.INITIAL == state) {
                router.showOnBoardingScreenViaInviteLink()
            } else {
                router.showMainScreenFromInviteLink()
            }
        }
    }
}
