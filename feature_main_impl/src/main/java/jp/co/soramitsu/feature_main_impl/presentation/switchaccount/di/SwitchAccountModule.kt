/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.switchaccount.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.switchaccount.SwitchAccountViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class SwitchAccountModule {
    @Provides
    @IntoMap
    @ViewModelKey(SwitchAccountViewModel::class)
    fun provideViewModel(
        interactor: MainInteractor,
        router: MainRouter,
        avatarGenerator: AccountAvatarGenerator
    ): ViewModel {
        return SwitchAccountViewModel(interactor, router, avatarGenerator)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SwitchAccountViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory)
            .get(SwitchAccountViewModel::class.java)
    }
}
