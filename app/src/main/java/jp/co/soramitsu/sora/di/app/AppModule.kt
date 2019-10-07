/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app

import android.app.Application
import android.content.Context
import dagger.Binds
import dagger.Module
import jp.co.soramitsu.sora.SoraApp
import javax.inject.Singleton

@Module
interface AppModule {

    @Singleton
    @Binds
    fun provideApplication(application: SoraApp): Application

    @Singleton
    @Binds
    fun provideContext(application: SoraApp): Context
}