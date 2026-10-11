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
import jp.co.soramitsu.feature_account_api.domain.model.RepairPinLockedException
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_account_api.domain.model.WalletDraft
import jp.co.soramitsu.feature_account_api.domain.model.WalletRepairAvailableException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.xcrypto.encryption.Keypair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MultiaccountInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val fileManager: FileManager,
    private val runtimeManager: RuntimeManager,
    private val walletRepository: WalletRepository,
) {

    companion object {
        private const val MULTIPLE_ACCOUNT_COUNT = 2
        private const val MINUTE_MILLIS = 60_000L

        /**
         * How long PIN entry is locked after [triesUsed] wrong entries in a row. The same steps
         * as the PIN screen (`PinCodeViewModel`): 3 wrong entries lock for 1 minute, then 5, 15
         * and 30 minutes.
         */
        fun pinLockMillis(triesUsed: Int): Long = when {
            triesUsed >= 6 -> 30 * MINUTE_MILLIS
            triesUsed == 5 -> 15 * MINUTE_MILLIS
            triesUsed == 4 -> 5 * MINUTE_MILLIS
            triesUsed == 3 -> MINUTE_MILLIS
            else -> 0L
        }
    }

    /** Wall-clock time, as the PIN screen's lock timer uses it. Replaced in tests. */
    internal var now: () -> Long = System::currentTimeMillis

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
     *   (`ACCOUNT_ALREADY_IMPORTED`); its keys are never touched. When no phrase is stored for
     *   it (for example a wallet restored from a backup file), the phrase just entered is kept
     *   as its backup, add-only, and that is reported
     *   (`ACCOUNT_ALREADY_IMPORTED_BACKUP_SAVED`).
     * - Keys stored for this address whose signing key proves the address (with or without an
     *   account row, for example after the database was lost) are adopted unchanged, and the
     *   account is added or made usable again.
     * - Missing key values are filled in (add-only) when everything already stored agrees.
     * - When a stored value differs or cannot be read and the stored key does not prove the
     *   address, nothing is written and [WalletRepairAvailableException] offers the confirmed
     *   repair ([repairFromMnemonic]).
     *
     * Deriving and proving keys is CPU work and never runs on the caller's thread.
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
     * "Repair this account with its recovery phrase": the person confirmed that the values
     * stored for this phrase's address are replaced by the ones the phrase derives. The
     * confirmation is the PIN when one is set ([pin]); when a PIN is stored but cannot be read,
     * it is the phone's screen lock, which the screen checks before it passes
     * [screenLockConfirmed]. A copy of the old values is kept on the device first. The account
     * row, its name and every other account stay as they are.
     *
     * Wrong PINs are counted by the PIN screen's own persistent counter and lock timer
     * ([RepairPinLockedException] once locked), so this is not a way around the PIN lockout.
     */
    suspend fun repairFromMnemonic(
        input: String,
        accountName: String,
        pin: String,
        screenLockConfirmed: Boolean = false,
    ): SoraAccount = repairDraft(pin, screenLockConfirmed) {
        credentialsRepository.draftFromMnemonic(input, accountName)
    }

    suspend fun repairFromRawSeed(
        input: String,
        accountName: String,
        pin: String,
        screenLockConfirmed: Boolean = false,
    ): SoraAccount {
        val soraAccount = repairDraft(pin, screenLockConfirmed) {
            credentialsRepository.draftFromRawSeed(input, accountName)
        }
        userRepository.saveNeedsMigration(false, soraAccount)
        userRepository.saveIsMigrationFetched(true, soraAccount)
        return soraAccount
    }

    /** True when [json] has the shape of an encrypted JSON export this app can restore. */
    suspend fun isJsonBackupValid(json: String): Boolean =
        credentialsRepository.isJsonBackupValid(json)

    /** One account of a backup file that the confirmed repair can bring back. */
    data class RepairCandidate(
        val address: String,
        /** The name this account has in the file (the account on the phone keeps its own). */
        val name: String,
        /** An account with this address is listed (read-only); otherwise only its keys are stored. */
        val accountExists: Boolean,
    )

    /** What importing an encrypted JSON export did. */
    class JsonImportResult(
        /** The accounts that were added or made usable again, in file order. */
        val imported: List<SoraAccount>,
        /** Accounts of a batch file that were skipped because they can already sign here. */
        val alreadyHere: Int,
        /**
         * Accounts of a batch file that were skipped because other keys, which cannot be used,
         * are stored for them on this phone. Nothing was changed for them. The same file repairs
         * them after one confirmation ([repairFromJsonBatch]).
         */
        val needRepair: List<RepairCandidate> = emptyList(),
        /** How that repair is confirmed: with the PIN ... */
        val repairPinRequired: Boolean = false,
        /** ... or, when the stored PIN cannot be read, with the phone's screen lock. */
        val repairScreenLockRequired: Boolean = false,
    )

    /**
     * Restores accounts from the encrypted JSON export this app writes (Account details, Export
     * JSON; also the JSON kept in a Google backup). The key pair in the file is the wallet: the
     * public key is computed from its private key and gives the SORA address.
     *
     * A one-account file behaves exactly like [importFromMnemonic]: "already imported", adopted,
     * filled in, or [WalletRepairAvailableException] ([repairFromJson]). A batch file adds every
     * account that can be added without replacing anything and reports the rest in the result;
     * it is `ACCOUNT_ALREADY_IMPORTED` only when every account in it is already usable here.
     *
     * The file is opened (scrypt, about a second per account) off the caller's thread and
     * before the wallet lock is taken.
     */
    suspend fun importFromJson(
        json: String,
        password: String,
        accountName: String,
        expectedAddress: String? = null,
    ): JsonImportResult = keyWork {
        val drafts = credentialsRepository.draftsFromJson(json, password, accountName)
        try {
            WalletMutationCoordinator.withLock {
                if (expectedAddress != null && drafts.none { it.address == expectedAddress }) {
                    throw SoraException.businessError(ResponseCode.JSON_BACKUP_INVALID)
                }
                val wanted =
                    drafts.filter { expectedAddress == null || it.address == expectedAddress }
                if (wanted.size == 1) {
                    return@withLock JsonImportResult(
                        imported = listOf(importJsonDraft(wanted.single())),
                        alreadyHere = 0,
                    )
                }
                val imported = mutableListOf<SoraAccount>()
                val needRepair = mutableListOf<RepairCandidate>()
                var alreadyHere = 0
                var pinRequired = false
                var screenLockRequired = false
                wanted.forEach { draft ->
                    try {
                        imported += importJsonDraft(draft)
                    } catch (offer: WalletRepairAvailableException) {
                        needRepair += RepairCandidate(
                            address = draft.address,
                            name = draft.accountName,
                            accountExists = offer.accountExists,
                        )
                        pinRequired = offer.pinRequired
                        screenLockRequired = offer.screenLockRequired
                    } catch (error: SoraException) {
                        if (error.errorResponseCode != ResponseCode.ACCOUNT_ALREADY_IMPORTED) {
                            throw error
                        }
                        alreadyHere += 1
                    }
                }
                if (imported.isEmpty() && needRepair.isEmpty()) {
                    throw SoraException.businessError(ResponseCode.ACCOUNT_ALREADY_IMPORTED)
                }
                JsonImportResult(
                    imported = imported,
                    alreadyHere = alreadyHere,
                    needRepair = needRepair,
                    repairPinRequired = pinRequired,
                    repairScreenLockRequired = screenLockRequired,
                )
            }
        } finally {
            drafts.forEach(WalletDraft::destroy)
        }
    }

    /** The confirmed repair ([repairFromMnemonic]) from a one-account encrypted JSON export. */
    suspend fun repairFromJson(
        json: String,
        password: String,
        accountName: String,
        pin: String,
        expectedAddress: String? = null,
        screenLockConfirmed: Boolean = false,
    ): SoraAccount = keyWork {
        // The confirmation is checked first: a wrong PIN never costs a key derivation.
        WalletMutationCoordinator.withLock { requireRepairConfirmed(pin, screenLockConfirmed) }
        val drafts = credentialsRepository.draftsFromJson(json, password, accountName)
        try {
            WalletMutationCoordinator.withLock {
                val draft = drafts
                    .singleOrNull { expectedAddress == null || it.address == expectedAddress }
                    ?: throw SoraException.businessError(ResponseCode.JSON_BACKUP_INVALID)
                repairJsonDraft(draft)
            }
        } finally {
            drafts.forEach(WalletDraft::destroy)
        }
    }

    /**
     * The confirmed repair of several accounts of one batch backup file: the accounts
     * [importFromJson] reported in [JsonImportResult.needRepair], after one confirmation that
     * named them. Each account is repaired exactly like [repairFromJson] (a copy of the old
     * values is kept first; only that account's stored values are replaced). An account that can
     * sign by now is left as it is. Returns the repaired accounts.
     */
    suspend fun repairFromJsonBatch(
        json: String,
        password: String,
        accountName: String,
        pin: String,
        addresses: List<String>,
        screenLockConfirmed: Boolean = false,
    ): List<SoraAccount> = keyWork {
        WalletMutationCoordinator.withLock { requireRepairConfirmed(pin, screenLockConfirmed) }
        val drafts = credentialsRepository.draftsFromJson(json, password, accountName)
        try {
            WalletMutationCoordinator.withLock {
                // Every named account must be in the file before anything is replaced.
                val wanted = addresses.distinct().map { address ->
                    drafts.singleOrNull { it.address == address }
                        ?: throw SoraException.businessError(ResponseCode.JSON_BACKUP_INVALID)
                }
                wanted.mapNotNull { draft ->
                    try {
                        repairJsonDraft(draft)
                    } catch (error: SoraException) {
                        if (error.errorResponseCode != ResponseCode.ACCOUNT_ALREADY_IMPORTED) {
                            throw error
                        }
                        null
                    }
                }
            }
        } finally {
            drafts.forEach(WalletDraft::destroy)
        }
    }

    private suspend fun repairJsonDraft(draft: WalletDraft): SoraAccount {
        val exists = requireNotAlreadyUsable(draft)
        credentialsRepository.repairWithDraft(draft)
        val account = finishImport(draft, exists)
        if (!exists) markWalletWithoutPhrase(account)
        return account
    }

    private suspend fun importJsonDraft(draft: WalletDraft): SoraAccount {
        val existed = userRepository.accountExists(draft.address)
        val account = commitPreparedDraft(draft)
        if (!existed) markWalletWithoutPhrase(account)
        return account
    }

    /**
     * A wallet that arrives as a key pair has no phrase, so there is no SORA v1 identity to look
     * up. (An account that was already listed keeps what was recorded for it.)
     */
    private suspend fun markWalletWithoutPhrase(account: SoraAccount) {
        userRepository.saveNeedsMigration(false, account)
        userRepository.saveIsMigrationFetched(true, account)
    }

    /**
     * Key derivation (PBKDF2, sr25519), opening or writing a backup file (scrypt) and the
     * stored-key proof are CPU work. They never run on the caller's thread, which is the main
     * thread for every screen that imports, repairs or exports.
     */
    private suspend fun <T> keyWork(block: suspend () -> T): T =
        withContext(Dispatchers.Default) { block() }

    private suspend fun importDraft(createDraft: suspend () -> WalletDraft): SoraAccount =
        keyWork {
            WalletMutationCoordinator.withLock {
                val draft = createDraft()
                try {
                    commitPreparedDraft(draft)
                } finally {
                    draft.destroy()
                }
            }
        }

    /** Stores [draft] and adds or completes its account. The caller destroys the draft. */
    private suspend fun commitPreparedDraft(draft: WalletDraft): SoraAccount {
        val exists = requireNotAlreadyUsable(draft)
        try {
            credentialsRepository.commitDraft(draft)
        } catch (error: SoraException) {
            if (error.errorResponseCode == ResponseCode.ACCOUNT_STORAGE_CONFLICT) {
                val pin = storedPin()
                throw WalletRepairAvailableException(
                    address = draft.address,
                    accountExists = exists,
                    pinRequired = pin is StoredPin.Set,
                    screenLockRequired = pin is StoredPin.Unreadable,
                )
            }
            throw error
        }
        return finishImport(draft, exists)
    }

    private suspend fun repairDraft(
        pin: String,
        screenLockConfirmed: Boolean,
        createDraft: suspend () -> WalletDraft,
    ): SoraAccount = keyWork {
        WalletMutationCoordinator.withLock {
            requireRepairConfirmed(pin, screenLockConfirmed)
            val draft = createDraft()
            try {
                val exists = requireNotAlreadyUsable(draft)
                credentialsRepository.repairWithDraft(draft)
                finishImport(draft, exists)
            } finally {
                draft.destroy()
            }
        }
    }

    /** The PIN stored on this phone, as far as it can be known. */
    private sealed interface StoredPin {
        /** No PIN is set (for example before the first wallet is finished). */
        data object None : StoredPin

        /** A PIN is stored but cannot be decrypted, so nothing can be compared with it. */
        data object Unreadable : StoredPin

        class Set(val value: String) : StoredPin
    }

    /**
     * A PIN that cannot be decrypted is "set but unavailable", never "no PIN": the damage that
     * made an account's keys unreadable can have reached the PIN value too. A Keystore or
     * storage failure is not an answer and is rethrown.
     */
    private suspend fun storedPin(): StoredPin = try {
        userRepository.retrievePin().let { if (it.isEmpty()) StoredPin.None else StoredPin.Set(it) }
    } catch (_: WalletDecryptionException) {
        StoredPin.Unreadable
    }

    /**
     * The repair replaces stored key values, so it is confirmed first:
     * - with the PIN when one is set. A wrong PIN is counted by the same persistent counter and
     *   lock timer as the PIN screen (3 wrong entries lock PIN entry for 1 minute, then 5, 15
     *   and 30), and a correct one resets them, exactly as on the PIN screen;
     * - with the phone's screen lock when a PIN is stored but cannot be read;
     * - by the dialog's explicit confirmation alone only when no PIN is set on this phone.
     */
    private suspend fun requireRepairConfirmed(pin: String, screenLockConfirmed: Boolean) {
        when (val stored = storedPin()) {
            StoredPin.None -> Unit
            StoredPin.Unreadable -> if (!screenLockConfirmed) {
                throw SoraException.businessError(ResponseCode.REPAIR_NEEDS_SCREEN_LOCK)
            }
            is StoredPin.Set -> {
                val locked = pinLockRemainingMillis()
                if (locked > 0) throw RepairPinLockedException(locked)
                if (MessageDigest.isEqual(stored.value.toByteArray(), pin.toByteArray())) {
                    userRepository.resetTriesUsed()
                    userRepository.resetTimerStartedTimestamp()
                    return
                }
                val tries = userRepository.retrievePinTriesUsed() + 1
                userRepository.savePinTriesUsed(tries)
                val lock = pinLockMillis(tries)
                if (lock > 0) {
                    userRepository.saveTimerStartedTimestamp(now())
                    throw RepairPinLockedException(lock)
                }
                throw SoraException.businessError(ResponseCode.WRONG_PIN_CODE)
            }
        }
    }

    /** How long PIN entry is still locked by the PIN screen's timer; 0 when it is not. */
    private suspend fun pinLockRemainingMillis(): Long {
        val startedAt = userRepository.retrieveTimerStartedTimestamp()
        if (startedAt <= 0L) return 0L
        val step = pinLockMillis(userRepository.retrievePinTriesUsed())
        val elapsed = now() - startedAt
        // A clock that was set back never shortens the lock; it lasts at most its own step.
        return if (elapsed < 0L) step else (step - elapsed).coerceAtLeast(0L)
    }

    /**
     * The SORA address a recovery phrase derives, or null when it is not a valid phrase. Nothing
     * is written; used to pick, from a backup that holds several forms of a wallet, the one that
     * restores the account the backup names.
     */
    suspend fun addressOfMnemonic(mnemonic: String): String? = addressOfDraft {
        credentialsRepository.draftFromMnemonic(mnemonic, "")
    }

    /** The SORA address a raw seed derives, or null when it is not a valid seed. */
    suspend fun addressOfRawSeed(rawSeed: String): String? = addressOfDraft {
        credentialsRepository.draftFromRawSeed(rawSeed, "")
    }

    private suspend fun addressOfDraft(createDraft: suspend () -> WalletDraft): String? = keyWork {
        val draft = try {
            createDraft()
        } catch (_: SoraException) {
            return@keyWork null
        }
        try {
            draft.address
        } finally {
            draft.destroy()
        }
    }

    /**
     * True when an account row exists and is read-only; false when there is no account row.
     * Throws when that account can already sign: the wallet is already here and its keys are
     * never touched. When no phrase (or seed) is stored for such an account, the one just
     * entered is kept as its backup first (add-only), so it can be shown and backed up and a
     * 12- or 24-word phrase gets its SORA 3 accounts.
     */
    private suspend fun requireNotAlreadyUsable(draft: WalletDraft): Boolean {
        if (!userRepository.accountExists(draft.address)) return false
        if (draft.address !in userRepository.unavailableWalletIds()) {
            if (credentialsRepository.addMissingBackup(draft)) {
                // The value is stored; recording what it proves is repeated by the next start
                // when it cannot be done now.
                try {
                    userRepository.refreshVerifiedWallet(draft.address)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Nothing to undo: the account signs as before.
                }
                throw SoraException.businessError(
                    ResponseCode.ACCOUNT_ALREADY_IMPORTED_BACKUP_SAVED
                )
            }
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
        keyWork { credentialsRepository.generateJsonForDraft(draft, password) }

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

    /** Writes the encrypted JSON export (scrypt) off the caller's thread. */
    suspend fun getJsonFileUri(addresses: List<String>, password: String): Uri = keyWork {
        val accounts = addresses.map { userRepository.getSoraAccount(it) }

        val filename = if (addresses.size == 1) addresses.first() else "batch_exported_sora_accounts"

        fileManager.writeExternalCacheText(
            "$filename.json",
            credentialsRepository.generateJson(accounts, password)
        )
    }

    suspend fun generateSubstrateJsonString(accounts: List<SoraAccount>, password: String) =
        keyWork { credentialsRepository.generateJson(accounts, password) }

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
