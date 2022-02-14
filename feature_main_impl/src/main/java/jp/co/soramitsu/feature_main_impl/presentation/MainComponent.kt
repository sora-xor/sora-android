/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.appcompat.app.AppCompatActivity
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope

@Subcomponent(
    modules = [
        MainModule::class
    ]
)
@ScreenScope
interface MainComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withActivity(activity: AppCompatActivity): Builder

        fun build(): MainComponent
    }

    fun inject(mainActivity: MainActivity)
    fun inject(updateDialog: FlexibleUpdateDialog)
}
