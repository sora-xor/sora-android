package jp.co.soramitsu.feature_notification_impl.di

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences

interface NotificationFeatureDependencies {

    fun encryptedPreferences(): EncryptedPreferences

    fun preferences(): Preferences
}
