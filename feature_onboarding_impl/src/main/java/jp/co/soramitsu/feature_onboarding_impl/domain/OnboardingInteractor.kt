/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

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

    suspend fun getMnemonic(): String {
        val m = credentialsRepository.retrieveMnemonic(userRepository.getCurSoraAccount())
        return m.ifEmpty {
            throw SoraException.businessError(ResponseCode.GENERAL_ERROR)
        }
    }

    suspend fun isMnemonicValid(mnemonic: String): Boolean {
        return credentialsRepository.isMnemonicValid(mnemonic)
    }

    suspend fun runRecoverFlow(mnemonic: String, accountName: String) {
        val soraAccount = credentialsRepository.restoreUserCredentials(mnemonic, accountName)
        userRepository.insertSoraAccount(soraAccount)
        userRepository.setCurSoraAccount(soraAccount)
        getMnemonic()
        userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }
}
