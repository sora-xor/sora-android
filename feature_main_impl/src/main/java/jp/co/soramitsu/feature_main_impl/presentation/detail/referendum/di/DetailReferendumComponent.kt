/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail.referendum.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.detail.referendum.DetailReferendumFragment

@Subcomponent(
    modules = [
        DetailReferendumModule::class
    ]
)
@ScreenScope
interface DetailReferendumComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withReferendumId(referendumId: String): Builder

        fun build(): DetailReferendumComponent
    }

    fun inject(fragment: DetailReferendumFragment)
}
