/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.app

import android.content.Context
import dagger.BindsInstance
import dagger.Component
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
        fun context(context: Context): Builder
    }
}