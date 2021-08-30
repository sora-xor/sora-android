package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

interface UserDatasource {

    fun savePin(pin: String)

    fun retrievePin(): String

    fun saveRegistrationState(onboardingState: OnboardingState)

    fun retrieveRegistratrionState(): OnboardingState

    fun clearUserData()

    fun saveParentInviteCode(inviteCode: String)

    fun getParentInviteCode(): String

    fun getCurrentLanguage(): String

    fun changeLanguage(language: String)

    fun setBiometryEnabled(isEnabled: Boolean)

    fun isBiometryEnabled(): Boolean

    fun setBiometryAvailable(isAvailable: Boolean)

    fun isBiometryAvailable(): Boolean

    fun saveAccountName(accountName: String)

    fun getAccountName(): String

    fun saveNeedsMigration(it: Boolean)

    fun needsMigration(): Boolean

    fun isMigrationStatusFetched(): Boolean

    fun saveIsMigrationFetched(it: Boolean)
}
