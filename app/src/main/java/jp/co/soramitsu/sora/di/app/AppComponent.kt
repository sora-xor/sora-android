/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.sora.SoraApp
import javax.inject.Singleton

@Component(
    modules = [
        AppModule::class,
        NavigationModule::class,
        ComponentHolderModule::class,
        FeatureManagerModule::class
    ]
)
@Singleton
interface AppComponent {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: SoraApp): Builder

        @BindsInstance
        fun contextManager(contextManager: ContextManager): Builder

        fun build(): AppComponent
    }

    fun inject(app: SoraApp)
}