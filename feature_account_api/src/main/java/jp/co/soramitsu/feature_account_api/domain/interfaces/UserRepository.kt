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

package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
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
     */
    suspend fun adoptRetainedWallet(
        address: String,
        accountName: String,
        makeCurrent: Boolean = true,
    ): SoraAccount

    /**
     * Accounts whose keys are missing, incomplete or unreadable on this device. They stay listed
     * and show balances, but cannot sign; other accounts are unaffected.
     */
    suspend fun unavailableWalletIds(): Set<String>

    /**
     * The forward paths for an account in [unavailableWalletIds], or null when the account can
     * sign. Reads stored values only.
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
