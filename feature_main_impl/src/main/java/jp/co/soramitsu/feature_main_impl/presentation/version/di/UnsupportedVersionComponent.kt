/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.version.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.version.UnsupportedVersionFragment

@Subcomponent(
    modules = [
        UnsupportedVersionModule::class
    ]
)
@ScreenScope
interface UnsupportedVersionComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): UnsupportedVersionComponent
    }

    fun inject(unsupportedVersionFragment: UnsupportedVersionFragment)
}
