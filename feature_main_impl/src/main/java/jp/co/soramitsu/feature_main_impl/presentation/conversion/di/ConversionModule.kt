/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.conversion.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithPreloaderImpl
import jp.co.soramitsu.common.di.app.ViewModelKey
import jp.co.soramitsu.common.di.app.ViewModelModule
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.conversion.ConversionViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ConversionModule {

    @Provides
    fun withPreloader(): WithPreloader {
        return WithPreloaderImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(ConversionViewModel::class)
    fun provideViewModel(interactor: MainInteractor, preloader: WithPreloader, router: MainRouter): ViewModel {
        return ConversionViewModel(interactor, preloader, router)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ConversionViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(ConversionViewModel::class.java)
    }
}