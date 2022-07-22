/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.domain

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import javax.inject.Inject

class MultiaccountInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository
) {

    private companion object {
        const val MULTIPLE_ACCOUNT_COUNT = 2
    }

    suspend fun isMnemonicValid(mnemonic: String) = credentialsRepository.isMnemonicValid(mnemonic)

    suspend fun isRawSeedValid(rawSeed: String) = credentialsRepository.isRawSeedValid(rawSeed)

    suspend fun continueRecoverFlow(soraAccount: SoraAccount) {
        userRepository.insertSoraAccount(soraAccount)
        userRepository.setCurSoraAccount(soraAccount)
        userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    suspend fun recoverSoraAccountFromMnemonic(input: String, accountName: String) = credentialsRepository.restoreUserCredentialsFromMnemonic(input, accountName)

    suspend fun recoverSoraAccountFromRawSeed(input: String, accountName: String): SoraAccount {
        val soraAccount = credentialsRepository.restoreUserCredentialsFromRawSeed(input, accountName)
        userRepository.saveNeedsMigration(false, soraAccount)
        userRepository.saveIsMigrationFetched(true, soraAccount)
        return soraAccount
    }

    suspend fun generateUserCredentials(accountName: String): SoraAccount {
        return credentialsRepository.generateUserCredentials(accountName)
    }

    suspend fun getMnemonic(soraAccount: SoraAccount? = null): String {
        return credentialsRepository.retrieveMnemonic(soraAccount ?: userRepository.getCurSoraAccount())
    }

    suspend fun createUser(soraAccount: SoraAccount) {
        userRepository.insertSoraAccount(soraAccount)
        userRepository.setCurSoraAccount(soraAccount)
        userRepository.saveRegistrationState(OnboardingState.INITIAL)
        userRepository.saveNeedsMigration(false, soraAccount)
        userRepository.saveIsMigrationFetched(true, soraAccount)
    }

    suspend fun saveRegistrationStateFinished() {
        userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    suspend fun isMultiAccount(): Boolean =
        userRepository.getSoraAccountsCount() >= MULTIPLE_ACCOUNT_COUNT
}
