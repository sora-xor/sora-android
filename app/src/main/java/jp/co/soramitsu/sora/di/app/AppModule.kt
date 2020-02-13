/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app

import android.app.Application
import android.content.Context
import dagger.Binds
import dagger.Module
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.resourses.ResourceManagerImpl
import jp.co.soramitsu.sora.SoraApp
import javax.inject.Singleton

@Module
interface AppModule {

    @Singleton
    @Binds
    fun bindApplication(application: SoraApp): Application

    @Singleton
    @Binds
    fun bindContext(application: SoraApp): Context

    @Singleton
    @Binds
    fun bindResourceManager(resourceManager: ResourceManagerImpl): ResourceManager
}