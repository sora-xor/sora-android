package jp.co.soramitsu.feature_notification_api.domain.interfaces

interface NotificationDatasource {

    fun saveIsPushTokenUpdateNeeded(updateNeeded: Boolean)

    fun isPushTokenUpdateNeeded(): Boolean

    fun retrievePushToken(): String

    fun savePushToken(notificationToken: String)
}