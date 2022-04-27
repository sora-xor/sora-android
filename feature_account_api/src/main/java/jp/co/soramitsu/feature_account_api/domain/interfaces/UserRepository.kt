/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    suspend fun getSoraAccountsCount(): Int

    suspend fun initCurSoraAccount()

    suspend fun getCurSoraAccount(): SoraAccount

    suspend fun setCurSoraAccount(soraAccount: SoraAccount)

    suspend fun setCurSoraAccount(accountAddress: String)

    fun flowCurSoraAccount(): Flow<SoraAccount>

    fun flowSoraAccountsList(): Flow<List<SoraAccount>>

    suspend fun insertSoraAccount(soraAccount: SoraAccount)

    suspend fun updateAccountName(soraAccount: SoraAccount, newName: String)

    suspend fun getRegistrationState(): OnboardingState

    suspend fun savePin(pin: String)

    suspend fun retrievePin(): String

    suspend fun saveRegistrationState(onboardingState: OnboardingState)

    suspend fun clearUserData()

    suspend fun getAppVersion(): String

    suspend fun saveParentInviteCode(inviteCode: String)

    suspend fun getParentInviteCode(): String

    suspend fun getAvailableLanguages(): Pair<List<Language>, String>

    suspend fun changeLanguage(language: String): String

    suspend fun getInvitationLink(): String

    suspend fun getSelectedLanguage(): Language

    suspend fun setBiometryEnabled(isEnabled: Boolean)

    suspend fun isBiometryEnabled(): Boolean

    suspend fun setBiometryAvailable(biometryAvailable: Boolean)

    suspend fun isBiometryAvailable(): Boolean

    suspend fun getAccountNameForMigration(): String

    suspend fun saveNeedsMigration(it: Boolean, soraAccount: SoraAccount)

    suspend fun needsMigration(soraAccount: SoraAccount): Boolean

    suspend fun saveIsMigrationFetched(it: Boolean, soraAccount: SoraAccount)

    suspend fun isMigrationFetched(soraAccount: SoraAccount): Boolean
}
