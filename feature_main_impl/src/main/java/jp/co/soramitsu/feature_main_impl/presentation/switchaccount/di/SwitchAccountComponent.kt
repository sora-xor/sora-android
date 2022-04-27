/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.switchaccount.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.switchaccount.SwitchAccountFragment

@Subcomponent(
    modules = [
        SwitchAccountModule::class
    ]
)
@ScreenScope
interface SwitchAccountComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): SwitchAccountComponent
    }

    fun inject(switchAccountFragment: SwitchAccountFragment)
}
