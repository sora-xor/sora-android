/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.main.MainFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.BaseVotableFragment

@Subcomponent(
    modules = [
        ProjectsModule::class
    ]
)
@ScreenScope
interface ProjectsComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): ProjectsComponent
    }

    fun inject(mainFragment: MainFragment)

    fun inject(votableFragment: BaseVotableFragment)
}