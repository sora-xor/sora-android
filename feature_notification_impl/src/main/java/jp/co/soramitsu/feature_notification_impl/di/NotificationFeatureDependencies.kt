package jp.co.soramitsu.feature_notification_impl.di

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.NetworkApiCreator

interface NotificationFeatureDependencies {

    fun encryptedPreferences(): EncryptedPreferences

    fun preferences(): Preferences

    fun networkApiCreator(): NetworkApiCreator
}