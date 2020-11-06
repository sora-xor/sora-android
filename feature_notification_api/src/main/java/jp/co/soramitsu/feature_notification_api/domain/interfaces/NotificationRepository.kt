package jp.co.soramitsu.feature_notification_api.domain.interfaces

import io.reactivex.Completable

interface NotificationRepository {

    fun updatePushTokenIfNeeded(): Completable

    fun saveDeviceToken(notificationToken: String)
}