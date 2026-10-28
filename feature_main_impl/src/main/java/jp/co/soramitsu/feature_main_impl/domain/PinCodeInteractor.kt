/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_main_impl.domain

import javax.inject.Inject
import jp.co.soramitsu.common.account.RecoveryPhrase
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionPreview
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionResult
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** See [PinCodeInteractor.walletRemovalBriefing]. */
class WalletRemovalBriefing(
    val accounts: List<Account>,
    /**
     * Every account on the phone is removed. That also removes the PIN and the custom nodes,
     * and the app starts again from the welcome screen.
     */
    val removesEverything: Boolean,
    /**
     * Complete key sets that no account uses and that stay on the phone when everything is
     * removed. Afterwards only their own recovery phrase brings them back.
     */
    val retainedKeySets: Int,
    /**
     * How many of [retainedKeySets] can still be restored now with the PIN ("Wallets found on
     * this phone"). Removing everything removes the PIN and turns them into "recovery phrase
     * only", so the person is told and asked before that happens.
     */
    val restorableKeySets: Int = 0,
) {
    class Account(
        val address: String,
        val name: String,
        /** The stored recovery phrase when it re-derives this account; otherwise empty. */
        val phraseWords: List<String>,
        /** The raw seed that re-derives this account, when no phrase does; otherwise "". */
        val seedHex: String,
        /** The stored key signs, so the encrypted JSON export can be made before removal. */
        val canExportJson: Boolean,
    ) {
        /** Something stored on this phone re-derives this account and is shown first. */
        val hasBackupToShow: Boolean get() = phraseWords.isNotEmpty() || seedHex.isNotEmpty()
    }
}

class PinCodeInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val walletRepository: WalletRepository
) {

    companion object {
        const val PINCODE_LENGTH = 6
        const val OLD_PINCODE_LENGTH = 4
        private const val LEGACY_SORA_MNEMONIC_WORD_COUNT = 15
    }

    suspend fun savePin(pin: String) {
        return userRepository.savePin(pin)
    }

    suspend fun checkPin(code: String) = userRepository.retrievePin() == code

    suspend fun isCodeSet(): Boolean {
        return userRepository.retrievePin().isNotEmpty()
    }

    suspend fun createWalletDeletionPreview(
        targetWalletIds: List<String>,
    ): WalletDeletionPreview =
        userRepository.createWalletDeletionPreview(targetWalletIds)

    suspend fun confirmAndExecuteWalletDeletion(
        preview: WalletDeletionPreview,
    ): WalletDeletionResult =
        userRepository.confirmAndExecuteWalletDeletion(preview)

    suspend fun walletDeletionFailureCode(): String? =
        userRepository.walletDeletionFailureCode()

    /**
     * True while every one of [addresses] is still an account on this phone. Asked after a
     * removal that ended in an error, to learn whether anything was removed. (Reading the list
     * first finishes a removal that was already journaled.)
     */
    suspend fun areListed(addresses: List<String>): Boolean {
        val listed = userRepository.soraAccountsList().map { it.substrateAddress }.toSet()
        return listed.containsAll(addresses)
    }

    /**
     * What the person is shown before an account is removed from this phone: for each account
     * the backup that re-derives it (its stored recovery phrase, else its stored raw seed; a
     * stored value that derives another wallet is never offered as its backup), whether it can
     * still be exported as encrypted JSON, and what stays on the phone afterwards. Reads only;
     * nothing is removed here.
     *
     * Judging the stored secrets decrypts them and derives keys, so it runs off the caller's
     * thread.
     */
    suspend fun walletRemovalBriefing(addresses: List<String>): WalletRemovalBriefing {
        // The checks a removal must pass anyway (wallet changes allowed, no unfinished
        // transaction, no removal in progress, the keys readable) are run now, read-only, so a
        // removal that cannot happen says so before the person reveals and writes down a
        // phrase for it. The preview made here is not used; confirming makes its own.
        userRepository.createWalletDeletionPreview(addresses.distinct())
        val listed = userRepository.soraAccountsList()
        val targets = addresses.distinct().map { address ->
            listed.firstOrNull { it.substrateAddress == address }
                ?: error("WALLET_DELETION_TARGET_INVALID")
        }
        val accounts = withContext(Dispatchers.Default) {
            targets.map { account ->
                val material = credentialsRepository.retrieveBackupMaterial(account)
                WalletRemovalBriefing.Account(
                    address = account.substrateAddress,
                    name = account.accountName,
                    phraseWords = material.phraseWords,
                    // The seed is the backup only where no phrase is one.
                    seedHex = if (material.hasPhrase) "" else material.seedHex,
                    canExportJson = material.canSign,
                )
            }
        }
        val removesEverything =
            listed.map { it.substrateAddress }.toSet() == targets.map { it.substrateAddress }.toSet()
        val retained = if (removesEverything) {
            try {
                userRepository.retainedWallets()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Not known right now. The screen then says, without a number, that wallet keys
                // which no account uses are not removed.
                emptyList()
            }
        } else {
            emptyList()
        }
        return WalletRemovalBriefing(
            accounts = accounts,
            removesEverything = removesEverything,
            retainedKeySets = retained.size,
            restorableKeySets = retained.count { !it.needsRecoveryPhrase },
        )
    }

    suspend fun setBiometryAvailable(isBiometryAvailable: Boolean) {
        userRepository.setBiometryAvailable(isBiometryAvailable)
    }

    suspend fun isBiometryAvailable(): Boolean {
        return userRepository.isBiometryAvailable()
    }

    suspend fun isBiometryEnabled(): Boolean {
        return userRepository.isBiometryEnabled()
    }

    /**
     * True only when [needsMigration] has to ask the network: an account not checked before whose
     * phrase has the legacy 15 words. Reads only.
     */
    suspend fun migrationCheckNeedsNetwork(): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        if (userRepository.isMigrationFetched(soraAccount)) return false
        val retainedWordCount =
            RecoveryPhrase.wordCount(credentialsRepository.retrieveMnemonic(soraAccount))
        return retainedWordCount == LEGACY_SORA_MNEMONIC_WORD_COUNT
    }

    suspend fun needsMigration(): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val isFetched = userRepository.isMigrationFetched(soraAccount)
        return if (isFetched) {
            userRepository.needsMigration(soraAccount)
        } else {
            // Counted from the parsed phrase, so the separators it was stored with do not matter.
            val retainedWordCount =
                RecoveryPhrase.wordCount(credentialsRepository.retrieveMnemonic(soraAccount))
            val needs = if (retainedWordCount == LEGACY_SORA_MNEMONIC_WORD_COUNT) {
                val irohaData = credentialsRepository.getIrohaData(soraAccount)
                walletRepository.needsMigration(irohaData.address)
            } else {
                false
            }
            userRepository.saveNeedsMigration(needs, soraAccount)
            userRepository.saveIsMigrationFetched(true, soraAccount)
            userRepository.needsMigration(soraAccount)
        }
    }

    suspend fun setBiometryEnabled(isEnabled: Boolean) {
        userRepository.setBiometryEnabled(isEnabled)
    }

    suspend fun isPincodeUpdateNeeded(): Boolean {
        val pinLength = userRepository.retrievePin().length
        return pinLength > 0 && pinLength != PINCODE_LENGTH
    }

    suspend fun saveTriesUsed(triesUsed: Int) {
        userRepository.savePinTriesUsed(triesUsed)
    }

    suspend fun retrieveTriesUsed() = userRepository.retrievePinTriesUsed()

    suspend fun retrieveTimerStartedTimestamp() = userRepository.retrieveTimerStartedTimestamp()

    suspend fun resetTriesUsed() {
        userRepository.resetTriesUsed()
    }

    suspend fun resetTimerStartedTimestamp() {
        userRepository.resetTimerStartedTimestamp()
    }

    suspend fun saveTimerStartedTimestamp(timestamp: Long) {
        userRepository.saveTimerStartedTimestamp(timestamp)
    }
}
