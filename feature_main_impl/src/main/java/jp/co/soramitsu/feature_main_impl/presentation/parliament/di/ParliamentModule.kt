/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.parliament.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.parliament.ParliamentViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ParliamentModule {

    @Provides
    @IntoMap
    @ViewModelKey(ParliamentViewModel::class)
    fun provideViewModel(mainRouter: MainRouter): ViewModel {
        return ParliamentViewModel(mainRouter)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ParliamentViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(ParliamentViewModel::class.java)
    }
}
