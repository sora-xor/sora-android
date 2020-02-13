/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.activity.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.activity.ActivityFragment

@Subcomponent(
    modules = [
        ActivityFeedModule::class
    ]
)
@ScreenScope
interface ActivityFeedComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): ActivityFeedComponent
    }

    fun inject(activityFragment: ActivityFragment)
}