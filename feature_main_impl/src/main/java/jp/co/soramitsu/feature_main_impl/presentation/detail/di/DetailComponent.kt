/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.app.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.detail.DetailFragment

@Subcomponent(
    modules = [
        DetailModule::class
    ]
)
@ScreenScope
interface DetailComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withProjectId(projectId: String): Builder

        @BindsInstance
        fun withRouter(router: MainRouter): Builder

        fun build(): DetailComponent
    }

    fun inject(detailFragment: DetailFragment)
}