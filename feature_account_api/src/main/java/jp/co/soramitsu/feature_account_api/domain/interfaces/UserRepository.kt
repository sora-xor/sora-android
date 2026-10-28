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

package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.common.account.SelectedAccountSigning
import jp.co.soramitsu.feature_account_api.domain.model.RetainedWallet
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletOptions
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionPreview
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionResult
import jp.co.soramitsu.feature_account_api.domain.model.WalletMutationSnapshot
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    suspend fun getSoraAccountsCount(): Int

    suspend fun getCurSoraAccount(): SoraAccount

    /**
     * Holds the same lock used by account creation, selection, and explicit wallet deletion for
     * the entire [block]. The nullable selected-account snapshot is immutable for that boundary.
     * Callers must not invoke another locking [UserRepository] method from [block].
     */
    suspend fun <T> withWalletMutationLocked(
        block: suspend (WalletMutationSnapshot) -> T,
    ): T

    /**
     * Privileged copy-on-write migration boundary. Implementations must reject this call unless
     * normal startup or an explicit recovery-journal retry authorization is active.
     */
    suspend fun <T> withWalletMigrationLocked(
        block: suspend (WalletMutationSnapshot) -> T,
    ): T

    suspend fun setCurSoraAccount(soraAccount: SoraAccount)

    fun flowCurSoraAccount(): Flow<SoraAccount>

    fun flowSoraAccountsList(): Flow<List<SoraAccount>>

    suspend fun soraAccountsList(): List<SoraAccount>

    suspend fun insertSoraAccount(soraAccount: SoraAccount, newAccount: Boolean)

    suspend fun updateAccountName(soraAccount: SoraAccount, newName: String)

    suspend fun getRegistrationState(): OnboardingState

    suspend fun savePin(pin: String)

    suspend fun retrievePin(): String

    suspend fun saveRegistrationState(onboardingState: OnboardingState)

    suspend fun createWalletDeletionPreview(
        targetWalletIds: List<String>,
    ): WalletDeletionPreview

    suspend fun confirmAndExecuteWalletDeletion(
        preview: WalletDeletionPreview,
    ): WalletDeletionResult

    suspend fun resumePendingWalletDeletion()

    suspend fun walletDeletionFailureCode(): String?

    suspend fun defaultGlobalCards()

    suspend fun saveParentInviteCode(inviteCode: String)

    suspend fun getParentInviteCode(): String

    suspend fun getAvailableLanguages(): Pair<List<Language>, Int>

    suspend fun changeLanguage(language: String): String

    suspend fun setBiometryEnabled(isEnabled: Boolean)

    suspend fun isBiometryEnabled(): Boolean

    suspend fun setBiometryAvailable(biometryAvailable: Boolean)

    suspend fun isBiometryAvailable(): Boolean

    suspend fun getAccountNameForMigration(): String

    suspend fun saveNeedsMigration(it: Boolean, soraAccount: SoraAccount)

    suspend fun needsMigration(soraAccount: SoraAccount): Boolean

    suspend fun saveIsMigrationFetched(it: Boolean, soraAccount: SoraAccount)

    suspend fun isMigrationFetched(soraAccount: SoraAccount): Boolean

    suspend fun getSoraAccount(address: String): SoraAccount

    suspend fun savePinTriesUsed(triesUsed: Int)

    suspend fun saveTimerStartedTimestamp(timestamp: Long)

    suspend fun retrievePinTriesUsed(): Int

    suspend fun retrieveTimerStartedTimestamp(): Long

    suspend fun resetTriesUsed()

    suspend fun resetTimerStartedTimestamp()

    suspend fun accountExists(address: String): Boolean

    /**
     * Adds the account row for a key set that is already stored on this device without an account
     * (for example left by an unfinished create-account flow, or the release-era unsuffixed keys
     * of an account removed in an older version). The stored keys are verified to derive
     * [address] first; no key material is written. With [makeCurrent] false the current
     * selection is kept (when it still names an account).
     *
     * A key set that was left on the phone by "Remove all accounts" is refused
     * (`RETAINED_WALLET_NEEDS_RECOVERY_PHRASE`): the PIN that protected it is gone, so only its
     * own recovery phrase, raw seed or backup file brings it back (the import adopts the stored
     * keys unchanged).
     */
    suspend fun adoptRetainedWallet(
        address: String,
        accountName: String,
        makeCurrent: Boolean = true,
    ): SoraAccount

    /**
     * The person has just proven the wallet at [address] with its own recovery phrase, raw seed
     * or backup file, and the keys stored for it on this phone are that wallet's keys (the
     * import adopted, completed or repaired them). If that key set was marked as "needs its
     * recovery phrase" (left by "Remove all accounts", or on the phone before the first PIN),
     * the mark is taken off: it is the person's own wallet again, so Safe Mode may show its
     * phrase after their PIN and "Wallets found on this phone" may restore it after a database
     * loss. The release-era (unsuffixed) set is unmarked too when its keys derive [address].
     *
     * Writes only the marker; no key value is read into the marker, changed, moved or removed.
     * Never call it for a key set that was not proven this way (`adoptRetainedWallet` is not a
     * proof).
     */
    suspend fun keySetProvenByItsBackup(address: String)

    /**
     * Accounts whose keys are missing, incomplete or unreadable on this device. They stay listed
     * and show balances, but cannot sign; other accounts are unaffected.
     */
    suspend fun unavailableWalletIds(): Set<String>

    /**
     * The accounts to label as read-only, kept current: emits again when an account is added,
     * removed or repaired and after every start-up check. For labels; reads only.
     *
     * Empty while wallet changes are off for the whole app (read-only or recovery mode). In that
     * mode no account can sign, none can be repaired or removed, and accounts may not have been
     * checked at all (an upgrade whose first check was interrupted): the app-wide banner
     * explains it, and a per-account label would state something nobody has established.
     */
    fun flowUnavailableWalletIds(): Flow<Set<String>>

    /**
     * The selected account and whether it is read-only on this phone, kept current. For the
     * screens where the person signs; it has no side effect and never throws to its collector.
     */
    fun flowSelectedAccountSigning(): Flow<SelectedAccountSigning>

    /**
     * Complete key sets on this phone that no account uses, and whether each needs its own
     * recovery phrase to come back. Only in normal mode (adding an account is allowed there);
     * reads only.
     */
    suspend fun retainedWallets(): List<RetainedWallet>

    /**
     * The forward paths for an account in [unavailableWalletIds], or null when the account can
     * sign, or while wallet changes are off for the whole app (none of the paths works then;
     * see [flowUnavailableWalletIds]). Reads stored values only.
     */
    suspend fun unavailableWalletOptions(address: String): UnavailableWalletOptions?

    /**
     * Makes a read-only account usable again after its missing key values were added from its
     * recovery phrase (`CredentialsRepository.commitDraft`, add-only). Verifies the stored keys
     * exactly like the upgrade does and records the account as verified. Account rows and key
     * values are not changed here. Throws when the stored keys still do not verify.
     */
    suspend fun completeUnavailableWallet(address: String): SoraAccount

    /**
     * Records again what the stored values of an account that can sign prove, after a recovery
     * phrase or seed was added as its backup (`CredentialsRepository.addMissingBackup`): the
     * backup label, and the SORA 3 accounts of a 12- or 24-word phrase. The account stays
     * verified; account rows and key values are not changed. Does nothing for an account that
     * is not listed or is read-only. The next app start records the same facts in any case.
     */
    suspend fun refreshVerifiedWallet(address: String)
}
