/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_impl.data.repository.datasource

import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationDatasource
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import javax.inject.Inject

class PrefsNotificationDatasource @Inject constructor(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences
) : NotificationDatasource {

    companion object {
        private const val PREFS_PUSH_UPDATE_NEEDED = "is_push_update_needed"
        private const val PREFS_PUSH_TOKEN = "device_token"
    }

    override fun saveIsPushTokenUpdateNeeded(updateNeeded: Boolean) {
        preferences.putBoolean(PREFS_PUSH_UPDATE_NEEDED, updateNeeded)
    }

    override fun isPushTokenUpdateNeeded(): Boolean {
        return preferences.getBoolean(PREFS_PUSH_UPDATE_NEEDED, false)
    }

    override fun retrievePushToken(): String {
        return encryptedPreferences.getDecryptedString(PREFS_PUSH_TOKEN)
    }

    override fun savePushToken(notificationToken: String) {
        encryptedPreferences.putEncryptedString(PREFS_PUSH_TOKEN, notificationToken)
    }
}