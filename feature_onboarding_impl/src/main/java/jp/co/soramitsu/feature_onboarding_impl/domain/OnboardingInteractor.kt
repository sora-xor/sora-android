package jp.co.soramitsu.feature_onboarding_impl.domain

import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import javax.inject.Inject

class OnboardingInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val ethereumRepository: EthereumRepository,
) {

    suspend fun saveRegistrationStateFinished() {
        userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    suspend fun getMnemonic(): String {
        val m = credentialsRepository.retrieveMnemonic()
        return if (m.isEmpty()) {
            throw SoraException.businessError(ResponseCode.GENERAL_ERROR)
        } else {
            m
        }
    }

    suspend fun isMnemonicValid(mnemonic: String): Boolean {
        return credentialsRepository.isMnemonicValid(mnemonic)
    }

    suspend fun runRecoverFlow(mnemonic: String, accountName: String) {
        credentialsRepository.restoreUserCredentials(mnemonic)
        getMnemonic()
        userRepository.saveAccountName(accountName)
        userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    suspend fun createUser(accountName: String) {
        credentialsRepository.generateUserCredentials()
        userRepository.saveAccountName(accountName)
        userRepository.saveRegistrationState(OnboardingState.INITIAL)
        userRepository.saveNeedsMigration(false)
        userRepository.saveIsMigrationFetched(true)
    }
}
