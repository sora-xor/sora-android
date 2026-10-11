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
import java.security.MessageDigest
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_account_api.domain.model.WalletDraft
import jp.co.soramitsu.feature_account_api.domain.model.WalletRepairAvailableException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.xcrypto.encryption.Keypair
import kotlinx.coroutines.flow.Flow

class MultiaccountInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val fileManager: FileManager,
    private val runtimeManager: RuntimeManager,
    private val walletRepository: WalletRepository,
) {

    private companion object {
        const val MULTIPLE_ACCOUNT_COUNT = 2
    }

    suspend fun anyBackupStarted() {
        walletRepository.updateCardVisibilityOnCardsHub(CardHubType.BACKUP.hubName, false)
    }

    suspend fun isMnemonicValid(mnemonic: String) = credentialsRepository.isMnemonicValid(mnemonic)

    suspend fun isRawSeedValid(rawSeed: String) = credentialsRepository.isRawSeedValid(rawSeed)

    suspend fun accountExists(address: String) = userRepository.accountExists(address)

    suspend fun continueRecoverFlow(soraAccount: SoraAccount) {
        insertAndSetCurAccount(soraAccount, false)
        userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    /**
     * Imports a recovery phrase as one step under the wallet lock. No outcome asks the person to
     * remove an account.
     * - An account with this address that can sign is reported as already here
     *   (`ACCOUNT_ALREADY_IMPORTED`); nothing is touched.
     * - Keys stored for this address whose signing key proves the address (with or without an
     *   account row, for example after the database was lost) are adopted unchanged, and the
     *   account is added or made usable again.
     * - Missing key values are filled in (add-only) when everything already stored agrees.
     * - When a stored value differs or cannot be read and the stored key does not prove the
     *   address, nothing is written and [WalletRepairAvailableException] offers the confirmed
     *   repair ([repairFromMnemonic]).
     */
    suspend fun importFromMnemonic(input: String, accountName: String): SoraAccount =
        importDraft { credentialsRepository.draftFromMnemonic(input, accountName) }

    suspend fun importFromRawSeed(input: String, accountName: String): SoraAccount {
        val soraAccount = importDraft {
            credentialsRepository.draftFromRawSeed(input, accountName)
        }
        userRepository.saveNeedsMigration(false, soraAccount)
        userRepository.saveIsMigrationFetched(true, soraAccount)
        return soraAccount
    }

    /**
     * "Repair this account with its recovery phrase": the person confirmed (with the PIN when one
     * is set) that the values stored for this phrase's address are replaced by the ones the
     * phrase derives. A copy of the old values is kept on the device first. The account row,
     * its name and every other account stay as they are.
     */
    suspend fun repairFromMnemonic(input: String, accountName: String, pin: String): SoraAccount =
        repairDraft(pin) { credentialsRepository.draftFromMnemonic(input, accountName) }

    suspend fun repairFromRawSeed(input: String, accountName: String, pin: String): SoraAccount {
        val soraAccount = repairDraft(pin) {
            credentialsRepository.draftFromRawSeed(input, accountName)
        }
        userRepository.saveNeedsMigration(false, soraAccount)
        userRepository.saveIsMigrationFetched(true, soraAccount)
        return soraAccount
    }

    private suspend fun importDraft(createDraft: suspend () -> WalletDraft): SoraAccount =
        WalletMutationCoordinator.withLock {
            val draft = createDraft()
            try {
                val exists = requireNotAlreadyUsable(draft)
                try {
                    credentialsRepository.commitDraft(draft)
                } catch (error: SoraException) {
                    if (error.errorResponseCode == ResponseCode.ACCOUNT_STORAGE_CONFLICT) {
                        throw WalletRepairAvailableException(
                            address = draft.address,
                            accountExists = exists,
                            pinRequired = storedPin().isNotEmpty(),
                        )
                    }
                    throw error
                }
                finishImport(draft, exists)
            } finally {
                draft.destroy()
            }
        }

    private suspend fun repairDraft(
        pin: String,
        createDraft: suspend () -> WalletDraft,
    ): SoraAccount = WalletMutationCoordinator.withLock {
        val stored = storedPin()
        if (
            stored.isNotEmpty() &&
            !MessageDigest.isEqual(stored.toByteArray(), pin.toByteArray())
        ) {
            throw SoraException.businessError(ResponseCode.WRONG_PIN_CODE)
        }
        val draft = createDraft()
        try {
            val exists = requireNotAlreadyUsable(draft)
            credentialsRepository.repairWithDraft(draft)
            finishImport(draft, exists)
        } finally {
            draft.destroy()
        }
    }

    /** True when an account row exists (read-only); throws when that account can already sign. */
    private suspend fun requireNotAlreadyUsable(draft: WalletDraft): Boolean {
        if (!userRepository.accountExists(draft.address)) return false
        if (draft.address !in userRepository.unavailableWalletIds()) {
            // The stored signing key proves the account: the wallet is already here.
            throw SoraException.businessError(ResponseCode.ACCOUNT_ALREADY_IMPORTED)
        }
        return true
    }

    private suspend fun finishImport(draft: WalletDraft, accountExists: Boolean): SoraAccount {
        if (!accountExists) {
            val soraAccount = SoraAccount(draft.address, draft.accountName)
            continueRecoverFlow(soraAccount)
            return soraAccount
        }
        val repaired = userRepository.completeUnavailableWallet(draft.address)
        userRepository.setCurSoraAccount(repaired)
        userRepository.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
        return repaired
    }

    /** The PIN set on this phone, or "" when none is set or it cannot be read. */
    private suspend fun storedPin(): String = try {
        userRepository.retrievePin()
    } catch (_: WalletDecryptionException) {
        ""
    }

    /** A new wallet held in memory until [createUserFromDraft]; backing out stores nothing. */
    suspend fun createDraft(accountName: String): WalletDraft =
        credentialsRepository.createDraft(accountName)

    /** Stores the draft's keys and adds its account in one step under the wallet lock. */
    suspend fun createUserFromDraft(draft: WalletDraft): SoraAccount =
        WalletMutationCoordinator.withLock {
            check(!userRepository.accountExists(draft.address)) { "WALLET_DRAFT_ALREADY_ACCOUNT" }
            val soraAccount = credentialsRepository.commitDraft(draft)
            createUser(soraAccount)
            saveRegistrationStateFinished()
            soraAccount
        }

    suspend fun generateDraftJson(draft: WalletDraft, password: String): String =
        credentialsRepository.generateJsonForDraft(draft, password)

    /**
     * The recovery phrase to show or back up for an account: the stored phrase's words joined by
     * single spaces, and only when that phrase re-derives the account's address. "" otherwise.
     */
    suspend fun getMnemonic(soraAccount: SoraAccount? = null): String =
        getBackupMaterial(soraAccount ?: userRepository.getCurSoraAccount())
            .phraseWords.joinToString(" ")

    /** What may be shown as a backup of [soraAccount] (only material that re-derives it). */
    suspend fun getBackupMaterial(soraAccount: SoraAccount): WalletBackupMaterial =
        credentialsRepository.retrieveBackupMaterial(soraAccount)

    suspend fun getBackupMaterial(address: String): WalletBackupMaterial =
        getBackupMaterial(userRepository.getSoraAccount(address))

    private suspend fun insertAndSetCurAccount(soraAccount: SoraAccount, newAccount: Boolean) {
        userRepository.insertSoraAccount(soraAccount, newAccount)
        userRepository.setCurSoraAccount(soraAccount)
    }

    suspend fun getMnemonic(address: String): String =
        getMnemonic(userRepository.getSoraAccount(address))

    /**
     * The 32-byte seed that re-derives the account ("" when neither the stored seed nor the
     * stored phrase does). The first call also caches it where no seed is stored yet.
     */
    suspend fun getSeed(address: String): String {
        val account = userRepository.getSoraAccount(address)
        if (!credentialsRepository.retrieveBackupMaterial(account).hasSeed) return ""
        return credentialsRepository.retrieveSeed(account)
    }

    suspend fun getKeypair(address: String): Keypair {
        return credentialsRepository.retrieveKeyPair(userRepository.getSoraAccount(address))
    }

    suspend fun createUser(soraAccount: SoraAccount) {
        insertAndSetCurAccount(soraAccount, true)
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

        return fileManager.writeExternalCacheText(
            "$filename.json",
            credentialsRepository.generateJson(accounts, password)
        )
    }

    suspend fun generateSubstrateJsonString(accounts: List<SoraAccount>, password: String) =
        credentialsRepository.generateJson(accounts, password)

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

    fun isAddressValid(address: String): Boolean = runtimeManager.isAddressOk(address)

    fun convertPassphraseToSeed(passphrase: String) = credentialsRepository.convertPassphraseToSeed(passphrase)
}
