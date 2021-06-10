/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import javax.inject.Inject

class PrefsUserDatasource @Inject constructor(
    private val preferences: Preferences,
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

    override fun savePin(pin: String) {
        encryptedPreferences.putEncryptedString(PREFS_PIN_CODE, pin)
    }

    override fun retrievePin(): String {
        return encryptedPreferences.getDecryptedString(PREFS_PIN_CODE)
    }

    override fun saveRegistrationState(onboardingState: OnboardingState) {
        preferences.putString(PREFS_REGISTRATION_STATE, onboardingState.toString())
    }

    override fun retrieveRegistratrionState(): OnboardingState {
        val registrationStateString = preferences.getString(PREFS_REGISTRATION_STATE)
        return if (registrationStateString.isEmpty()) {
            OnboardingState.INITIAL
        } else {
            runCatching { OnboardingState.valueOf(registrationStateString) }.getOrDefault(OnboardingState.INITIAL)
        }
    }

    override fun clearUserData() {
        preferences.clearAll()
    }

    override fun saveParentInviteCode(inviteCode: String) {
        preferences.putString(KEY_PARENT_INVITE_CODE, inviteCode)
    }

    override fun getParentInviteCode(): String {
        return preferences.getString(KEY_PARENT_INVITE_CODE)
    }

    override fun getCurrentLanguage(): String {
        return preferences.getCurrentLanguage()
    }

    override fun changeLanguage(language: String) {
        preferences.saveCurrentLanguage(language)
    }

    override fun setBiometryEnabled(isEnabled: Boolean) {
        preferences.putBoolean(KEY_BIOMETRY_ENABLED, isEnabled)
    }

    override fun isBiometryEnabled(): Boolean {
        return preferences.getBoolean(KEY_BIOMETRY_ENABLED, true)
    }

    override fun setBiometryAvailable(isAvailable: Boolean) {
        preferences.putBoolean(KEY_BIOMETRY_AVAILABLE, isAvailable)
    }

    override fun isBiometryAvailable(): Boolean {
        return preferences.getBoolean(KEY_BIOMETRY_AVAILABLE, true)
    }

    override fun saveAccountName(accountName: String) {
        return preferences.putString(KEY_ACCOUNT_NAME, accountName)
    }

    override fun getAccountName(): String {
        return preferences.getString(KEY_ACCOUNT_NAME)
    }

    override fun saveNeedsMigration(it: Boolean) {
        preferences.putBoolean(KEY_NEEDS_MIGRATION, it)
    }

    override fun needsMigration(): Boolean {
        return preferences.getBoolean(KEY_NEEDS_MIGRATION)
    }

    override fun saveIsMigrationFetched(it: Boolean) {
        preferences.putBoolean(KEY_IS_MIGRATION_FETCHED, it)
    }

    override fun isMigrationStatusFetched(): Boolean {
        return preferences.getBoolean(KEY_IS_MIGRATION_FETCHED, false)
    }
}
