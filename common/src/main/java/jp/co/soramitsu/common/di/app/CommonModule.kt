/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.app

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.AppVersionProviderImpl
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.resourses.ResourceManagerImpl
import javax.inject.Singleton

@Module
class CommonModule {

    @Singleton
    @Provides
    fun provideResourceManager(resourceManager: ResourceManagerImpl): ResourceManager = resourceManager

    @Singleton
    @Provides
    fun provideAppVersionProvider(appVersionProvider: AppVersionProviderImpl): AppVersionProvider = appVersionProvider

    @Singleton
    @Provides
    fun providesPushHandler(): PushHandler = PushHandler()

    @Singleton
    @Provides
    fun provideHealthChecker(): HealthChecker = HealthChecker()
}