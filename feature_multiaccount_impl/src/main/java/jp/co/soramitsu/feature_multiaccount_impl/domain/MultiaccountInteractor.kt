/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.domain

import android.net.Uri
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
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
