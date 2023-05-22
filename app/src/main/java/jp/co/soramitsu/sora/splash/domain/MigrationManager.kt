/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.domain

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

@Singleton
class MigrationManager @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository
) {

    suspend fun start(): Boolean {
        if (userRepository.getRegistrationState() == OnboardingState.REGISTRATION_FINISHED) {
            if (userRepository.getSoraAccountsCount() == 0) {
                val emptySoraAccount = SoraAccount("", "")
                val address = credentialsRepository.getAddressForMigration()
                val name = userRepository.getAccountNameForMigration()
                val soraAccount = SoraAccount(address, name)
                val needsIrohaMigration = userRepository.needsMigration(emptySoraAccount)
                val isIrohaFetched = userRepository.isMigrationFetched(emptySoraAccount)
                userRepository.saveNeedsMigration(needsIrohaMigration, soraAccount)
                userRepository.saveIsMigrationFetched(isIrohaFetched, soraAccount)
                val mnemonic = credentialsRepository.retrieveMnemonic(emptySoraAccount)
                credentialsRepository.saveMnemonic(mnemonic, soraAccount)
                val soraKeys = credentialsRepository.retrieveKeyPair(emptySoraAccount)
                credentialsRepository.saveKeyPair(soraKeys, soraAccount)
                userRepository.insertSoraAccount(soraAccount)
                userRepository.setCurSoraAccount(soraAccount)
                FirebaseWrapper.log("Migration ma done")
            }
        }
        userRepository.defaultGlobalCards()
        return true
    }
}
