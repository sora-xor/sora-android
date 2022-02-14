/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import javax.inject.Inject

class PrefsUserDatasource @Inject constructor(
    private val soraPreferences: SoraPreferences,
    private val encryptedPreferences: EncryptedPreferences
) : UserDatasource {

    companion object {
        private const val PREFS_PIN_CODE = "user_pin_code"
        private const val PREFS_REGISTRATION_STATE = "registration_state"

        private const val KEY_ACCOUNT_NAME = "key_account_name"
        private const val KEY_PARENT_INVITE_CODE = "invite_code"
        private const val KEY_BIOMETRY_AVAILABLE = "biometry_available"
        private const val KEY_BIOMETRY_ENABLED = "biometry_enabled"
        private const val KEY_NEEDS_MIGRATION = "needs_migration"
        private const val KEY_IS_MIGRATION_FETCHED = "is_migration_fetched"
    }

    override suspend fun savePin(pin: String) {
        encryptedPreferences.putEncryptedString(PREFS_PIN_CODE, pin)
    }

    override suspend fun retrievePin(): String {
        return encryptedPreferences.getDecryptedString(PREFS_PIN_CODE)
    }

    override suspend fun saveRegistrationState(onboardingState: OnboardingState) {
        soraPreferences.putString(PREFS_REGISTRATION_STATE, onboardingState.toString())
    }

    override suspend fun retrieveRegistratrionState(): OnboardingState {
        val registrationStateString = soraPreferences.getString(PREFS_REGISTRATION_STATE)
        return if (registrationStateString.isEmpty()) {
            OnboardingState.INITIAL
        } else {
            runCatching { OnboardingState.valueOf(registrationStateString) }.getOrDefault(OnboardingState.INITIAL)
        }
    }

    override suspend fun clearUserData() {
        soraPreferences.clearAll()
    }

    override suspend fun saveParentInviteCode(inviteCode: String) {
        soraPreferences.putString(KEY_PARENT_INVITE_CODE, inviteCode)
    }

    override suspend fun getParentInviteCode(): String {
        return soraPreferences.getString(KEY_PARENT_INVITE_CODE)
    }

    override fun getCurrentLanguage(): String {
        return ContextManager.getInstance()?.getCurrentLanguage().orEmpty()
    }

    override fun changeLanguage(language: String) {
        ContextManager.getInstance()?.setCurrentLanguage(language)
    }

    override suspend fun setBiometryEnabled(isEnabled: Boolean) {
        soraPreferences.putBoolean(KEY_BIOMETRY_ENABLED, isEnabled)
    }

    override suspend fun isBiometryEnabled(): Boolean {
        return soraPreferences.getBoolean(KEY_BIOMETRY_ENABLED, true)
    }

    override suspend fun setBiometryAvailable(isAvailable: Boolean) {
        soraPreferences.putBoolean(KEY_BIOMETRY_AVAILABLE, isAvailable)
    }

    override suspend fun isBiometryAvailable(): Boolean {
        return soraPreferences.getBoolean(KEY_BIOMETRY_AVAILABLE, true)
    }

    override suspend fun saveAccountName(accountName: String) {
        return soraPreferences.putString(KEY_ACCOUNT_NAME, accountName)
    }

    override suspend fun getAccountName(): String {
        return soraPreferences.getString(KEY_ACCOUNT_NAME)
    }

    override suspend fun saveNeedsMigration(it: Boolean) {
        soraPreferences.putBoolean(KEY_NEEDS_MIGRATION, it)
    }

    override suspend fun needsMigration(): Boolean {
        return soraPreferences.getBoolean(KEY_NEEDS_MIGRATION)
    }

    override suspend fun saveIsMigrationFetched(it: Boolean) {
        soraPreferences.putBoolean(KEY_IS_MIGRATION_FETCHED, it)
    }

    override suspend fun isMigrationStatusFetched(): Boolean {
        return soraPreferences.getBoolean(KEY_IS_MIGRATION_FETCHED, false)
    }
}
