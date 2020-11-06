package jp.co.soramitsu.sora.splash.domain

import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import javax.inject.Inject

class SplashInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val didRepository: DidRepository
) {

    fun getRegistrationState() = userRepository.getRegistrationState()

    fun restoreAuth() {
        didRepository.restoreAuth()
    }

    fun saveRegistrationState(onboardingState: OnboardingState) {
        userRepository.saveRegistrationState(onboardingState)
    }

    fun saveInviteCode(inviteCode: String) {
        userRepository.saveParentInviteCode(inviteCode)
    }
}