/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val interactor: SplashInteractor,
) : BaseViewModel() {

    private val _runtimeInitiated = MutableLiveData<Boolean>()
    val runtimeInitiated: LiveData<Boolean> = _runtimeInitiated

    val showMainScreen = SingleLiveEvent<Unit>()
    val showOnBoardingScreen = SingleLiveEvent<OnboardingState>()
    val showOnBoardingScreenViaInviteLink = SingleLiveEvent<Unit>()
    val showMainScreenFromInviteLink = SingleLiveEvent<Unit>()

    init {
        viewModelScope.launch {
            tryCatch {
                delay(500)
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
                    showMainScreen.trigger()
                }
                OnboardingState.INITIAL -> {
                    showOnBoardingScreen.value = state
                }
            }
        }
    }

    fun handleDeepLink(invitationCode: String) {
        viewModelScope.launch {
            val state = interactor.getRegistrationState()
            interactor.saveInviteCode(invitationCode)

            if (OnboardingState.INITIAL == state) {
                showOnBoardingScreenViaInviteLink.trigger()
            } else {
                showMainScreenFromInviteLink.trigger()
            }
        }
    }
}
