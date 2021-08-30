package jp.co.soramitsu.feature_notification_api.domain.interfaces

interface NotificationRepository {

    fun saveDeviceToken(notificationToken: String)
}
