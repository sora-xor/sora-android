/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.app.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.main.MainFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.AllProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.CompletedProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.FavoriteProjectsFragment
import jp.co.soramitsu.feature_main_impl.presentation.main.projects.VotedProjectsFragment

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

        @BindsInstance
        fun withRouter(router: MainRouter): Builder

        fun build(): ProjectsComponent
    }

    fun inject(mainFragment: MainFragment)

    fun inject(allProjectsFragment: AllProjectsFragment)

    fun inject(favoriteProjectsFragment: FavoriteProjectsFragment)

    fun inject(votedProjectsFragment: VotedProjectsFragment)

    fun inject(completedProjectsFragment: CompletedProjectsFragment)
}