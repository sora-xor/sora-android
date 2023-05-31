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

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

class PrefsUserDatasource(
    private val soraPreferences: SoraPreferences,
    private val encryptedPreferences: EncryptedPreferences
) : UserDatasource {

    companion object {
        private const val PREFS_PIN_CODE = "user_pin_code"
        private const val PREFS_REGISTRATION_STATE = "registration_state"

        private const val KEY_PIN_TRIES = "key_pin_tries"
        private const val KEY_PIN_START_TIMESTAMP = "key_pin_start_timestamp"

        private const val KEY_ACCOUNT_NAME = "key_account_name"
        private const val KEY_PARENT_INVITE_CODE = "invite_code"
        private const val KEY_BIOMETRY_AVAILABLE = "biometry_available"
        private const val KEY_BIOMETRY_ENABLED = "biometry_enabled"
        private const val KEY_NEEDS_MIGRATION = "needs_migration"
        private const val KEY_IS_MIGRATION_FETCHED = "is_migration_fetched"
        private const val KEY_CUR_ACCOUNT_ADDRESS = "cur_account_address"
    }

    override suspend fun getCurAccountAddress(): String =
        soraPreferences.getString(KEY_CUR_ACCOUNT_ADDRESS)

    override suspend fun setCurAccountAddress(accountAddress: String) =
        soraPreferences.putString(KEY_CUR_ACCOUNT_ADDRESS, accountAddress)

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

    override suspend fun clearAllData() {
        soraPreferences.clearAll()
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
