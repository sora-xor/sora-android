/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationDatasource
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import jp.co.soramitsu.feature_notification_impl.data.repository.NotificationRepositoryImpl
import jp.co.soramitsu.feature_notification_impl.data.repository.datasource.PrefsNotificationDatasource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NotificationFeatureModule {

    @Provides
    @Singleton
    fun provideNotificationRepository(
        notificationDatasource: NotificationDatasource
    ): NotificationRepository =
        NotificationRepositoryImpl(notificationDatasource)

    @Provides
    @Singleton
    fun provideNotificationDatasource(
        sp: SoraPreferences,
        ep: EncryptedPreferences
    ): NotificationDatasource = PrefsNotificationDatasource(sp, ep)
}
