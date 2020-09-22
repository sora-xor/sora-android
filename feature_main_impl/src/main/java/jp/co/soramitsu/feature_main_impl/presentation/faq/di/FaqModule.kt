/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.faq.di

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
import jp.co.soramitsu.feature_main_impl.presentation.faq.FaqViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class FaqModule {

    @Provides
    @IntoMap
    @ViewModelKey(FaqViewModel::class)
    fun provideViewModel(router: MainRouter): ViewModel {
        return FaqViewModel(router)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): FaqViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(FaqViewModel::class.java)
    }
}