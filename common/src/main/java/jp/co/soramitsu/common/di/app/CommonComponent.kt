/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.app

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import javax.inject.Singleton

@Component(
    modules = [
        CommonModule::class
    ]
)
@Singleton
abstract class CommonComponent : CommonApi {

    @Component.Builder
    interface Builder {

        fun build(): CommonComponent

        @BindsInstance
        fun resourceManager(resourceManager: ResourceManager): Builder

        @BindsInstance
        fun languagesHolder(languagesHolder: LanguagesHolder): Builder

        @BindsInstance
        fun context(context: Context): Builder

        @BindsInstance
        fun contextManager(contextManager: ContextManager): Builder
    }
}