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

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

class PrefsUserDatasource(
    private val soraPreferences: SoraPreferences,
    private val encryptedPreferences: EncryptedPreferences
) : UserDatasource {

    companion object {
        private const val PREFS_PIN_CODE = WalletPreferenceKeys.PIN_CODE
        private const val PREFS_REGISTRATION_STATE =
            WalletPreferenceKeys.REGISTRATION_STATE

        private const val KEY_PIN_TRIES = "key_pin_tries"
        private const val KEY_PIN_START_TIMESTAMP = "key_pin_start_timestamp"

        private const val KEY_ACCOUNT_NAME = "key_account_name"
        private const val KEY_PARENT_INVITE_CODE = "invite_code"
        private const val KEY_BIOMETRY_AVAILABLE = "biometry_available"
        private const val KEY_BIOMETRY_ENABLED = "biometry_enabled"
        private const val KEY_NEEDS_MIGRATION = WalletPreferenceKeys.NEEDS_MIGRATION
        private const val KEY_IS_MIGRATION_FETCHED =
            WalletPreferenceKeys.IS_MIGRATION_FETCHED
        private const val KEY_CUR_ACCOUNT_ADDRESS =
            WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS
    }

    override suspend fun getCurAccountAddress(): String =
        soraPreferences.getString(KEY_CUR_ACCOUNT_ADDRESS)

    override suspend fun setCurAccountAddress(accountAddress: String) =
        soraPreferences.putString(KEY_CUR_ACCOUNT_ADDRESS, accountAddress)

    override suspend fun completeLegacyAccountUpgrade(accountAddress: String) =
        soraPreferences.completeLegacyAccountUpgrade(accountAddress)

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
            runCatching { OnboardingState.valueOf(registrationStateString) }.getOrDefault(
                OnboardingState.INITIAL
            )
        }
    }

    override suspend fun saveParentInviteCode(inviteCode: String) {
        soraPreferences.putString(KEY_PARENT_INVITE_CODE, inviteCode)
    }

    override suspend fun getParentInviteCode(): String {
        return soraPreferences.getString(KEY_PARENT_INVITE_CODE)
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

    override suspend fun getAccountName(): String {
        return soraPreferences.getString(KEY_ACCOUNT_NAME)
    }

    override suspend fun saveNeedsMigration(it: Boolean, suffixAddress: String) {
        soraPreferences.putBoolean(KEY_NEEDS_MIGRATION + suffixAddress, it)
    }

    override suspend fun needsMigration(suffixAddress: String): Boolean {
        return soraPreferences.getBoolean(KEY_NEEDS_MIGRATION + suffixAddress)
    }

    override suspend fun saveIsMigrationFetched(it: Boolean, suffixAddress: String) {
        soraPreferences.putBoolean(KEY_IS_MIGRATION_FETCHED + suffixAddress, it)
    }

    override suspend fun isMigrationStatusFetched(suffixAddress: String): Boolean {
        return soraPreferences.getBoolean(KEY_IS_MIGRATION_FETCHED + suffixAddress, false)
    }

    override suspend fun savePinTriesUsed(triesUsed: Int) {
        soraPreferences.putInt(KEY_PIN_TRIES, triesUsed)
    }

    override suspend fun saveTimerStartedTimestamp(timer: Long) {
        soraPreferences.putLong(KEY_PIN_START_TIMESTAMP, timer)
    }

    override suspend fun retrievePinTriesUsed(): Int {
        return soraPreferences.getInt(KEY_PIN_TRIES, 0)
    }

    override suspend fun retrieveTimerStartedTimestamp(): Long {
        return soraPreferences.getLong(KEY_PIN_START_TIMESTAMP, 0)
    }

    override suspend fun resetPinTriesUsed() {
        soraPreferences.clear(KEY_PIN_TRIES)
    }

    override suspend fun resetTimerStartedTimestamp() {
        soraPreferences.clear(KEY_PIN_START_TIMESTAMP)
    }
}
