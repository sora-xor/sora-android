package jp.co.soramitsu.feature_notification_api.domain.interfaces

interface NotificationDatasource {

    fun saveIsPushTokenUpdateNeeded(updateNeeded: Boolean)

    fun savePushToken(notificationToken: String)
}
