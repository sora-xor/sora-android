package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

interface UserRepository {

    fun getRegistrationState(): OnboardingState

    suspend fun savePin(pin: String)

    fun retrievePin(): String

    suspend fun saveRegistrationState(onboardingState: OnboardingState)

    suspend fun clearUserData()

    suspend fun getAppVersion(): String

    fun saveParentInviteCode(inviteCode: String)

    suspend fun getParentInviteCode(): String

    suspend fun getAvailableLanguages(): Pair<List<Language>, String>

    suspend fun changeLanguage(language: String): String

    suspend fun getInvitationLink(): String

    suspend fun getSelectedLanguage(): Language

    suspend fun setBiometryEnabled(isEnabled: Boolean)

    suspend fun isBiometryEnabled(): Boolean

    suspend fun setBiometryAvailable(biometryAvailable: Boolean)

    suspend fun isBiometryAvailable(): Boolean

    suspend fun saveAccountName(accountName: String)

    suspend fun getAccountName(): String

    suspend fun saveNeedsMigration(it: Boolean)

    suspend fun needsMigration(): Boolean

    suspend fun saveIsMigrationFetched(it: Boolean)

    suspend fun isMigrationFetched(): Boolean
}
