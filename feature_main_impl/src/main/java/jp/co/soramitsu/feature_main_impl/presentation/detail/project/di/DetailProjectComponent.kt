/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail.project.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.detail.project.DetailProjectFragment

@Subcomponent(
    modules = [
        DetailProjectModule::class
    ]
)
@ScreenScope
interface DetailProjectComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withProjectId(projectId: String): Builder

        fun build(): DetailProjectComponent
    }

    fun inject(detailProjectFragment: DetailProjectFragment)
}