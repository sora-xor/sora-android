package jp.co.soramitsu.feature_notification_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationDatasource
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import jp.co.soramitsu.feature_notification_impl.data.network.NotificationNetworkApi
import jp.co.soramitsu.feature_notification_impl.data.repository.NotificationRepositoryImpl
import jp.co.soramitsu.feature_notification_impl.data.repository.datasource.PrefsNotificationDatasource
import javax.inject.Singleton

@Module
class NotificationFeatureModule {

    @Provides
    @Singleton
    fun provideNotificationRepository(notificationRepository: NotificationRepositoryImpl): NotificationRepository = notificationRepository

    @Provides
    @Singleton
    fun provideNotificationNetworkApi(apiCreator: NetworkApiCreator): NotificationNetworkApi {
        return apiCreator.create(NotificationNetworkApi::class.java)
    }

    @Provides
    @Singleton
    fun provideNotificationDatasource(prefsNotificationDatasource: PrefsNotificationDatasource): NotificationDatasource = prefsNotificationDatasource
}