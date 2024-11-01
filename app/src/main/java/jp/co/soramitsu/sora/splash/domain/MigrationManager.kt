/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
                userRepository.insertSoraAccount(soraAccount, false)
                userRepository.setCurSoraAccount(soraAccount)
                FirebaseWrapper.log("Migration ma done")
            }
        }
        return true
    }
}
