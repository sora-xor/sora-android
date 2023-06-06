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

package jp.co.soramitsu.feature_multiaccount_impl.domain

import android.net.Uri
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import kotlinx.coroutines.flow.Flow

class MultiaccountInteractor @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val fileManager: FileManager,
) {

    private companion object {
        const val MULTIPLE_ACCOUNT_COUNT = 2
    }

    suspend fun isMnemonicValid(mnemonic: String) = credentialsRepository.isMnemonicValid(mnemonic)

    suspend fun isRawSeedValid(rawSeed: String) = credentialsRepository.isRawSeedValid(rawSeed)

    suspend fun continueRecoverFlow(soraAccount: SoraAccount, update: Boolean) {
        insertAndSetCurAccount(soraAccount, update)
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

    private suspend fun insertAndSetCurAccount(soraAccount: SoraAccount, update: Boolean) {
        userRepository.insertSoraAccount(soraAccount)
        userRepository.setCurSoraAccount(soraAccount)
        if (update) assetsInteractor.updateWhitelistBalances(false)
    }

    suspend fun getMnemonic(address: String): String {
        return credentialsRepository.retrieveMnemonic(userRepository.getSoraAccount(address))
    }

    suspend fun getSeed(address: String): String {
        return credentialsRepository.retrieveSeed(userRepository.getSoraAccount(address))
    }

    suspend fun getKeypair(address: String): Keypair {
        return credentialsRepository.retrieveKeyPair(userRepository.getSoraAccount(address))
    }

    suspend fun createUser(soraAccount: SoraAccount, update: Boolean) {
        insertAndSetCurAccount(soraAccount, update)
        userRepository.saveRegistrationState(OnboardingState.INITIAL)
        userRepository.saveNeedsMigration(false, soraAccount)
        userRepository.saveIsMigrationFetched(true, soraAccount)
    }

    suspend fun saveRegistrationStateFinished() {
        userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    suspend fun isMultiAccount(): Boolean =
        userRepository.getSoraAccountsCount() >= MULTIPLE_ACCOUNT_COUNT

    suspend fun getJsonFileUri(addresses: List<String>, password: String): Uri {
        val accounts = addresses.map { userRepository.getSoraAccount(it) }

        val filename = if (addresses.size == 1) addresses.first() else "batch_exported_sora_accounts"

        return fileManager.writeExternalCacheText("$filename.json", credentialsRepository.generateJson(accounts, password))
    }

    suspend fun getSoraAccount(address: String): SoraAccount = userRepository.getSoraAccount(address)

    suspend fun getCurrentSoraAccount(): SoraAccount = userRepository.getCurSoraAccount()

    fun flowCurSoraAccount(): Flow<SoraAccount> = userRepository.flowCurSoraAccount()

    fun flowSoraAccountsList(): Flow<List<SoraAccount>> = userRepository.flowSoraAccountsList()

    suspend fun setCurSoraAccount(account: SoraAccount) {
        userRepository.setCurSoraAccount(account)
    }

    suspend fun updateName(accountAddress: String, newName: String) {
        val account = getSoraAccount(address = accountAddress)
        userRepository.updateAccountName(account, newName)
    }
}
