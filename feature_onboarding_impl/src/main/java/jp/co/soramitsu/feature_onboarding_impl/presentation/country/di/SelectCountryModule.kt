/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.country.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithPreloaderImpl
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.country.SelectCountryViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class SelectCountryModule {

    @Provides
    @IntoMap
    @ViewModelKey(SelectCountryViewModel::class)
    fun provideViewModel(interactor: OnboardingInteractor, router: OnboardingRouter, preloader: WithPreloader): ViewModel {
        return SelectCountryViewModel(interactor, router, preloader)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): SelectCountryViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(SelectCountryViewModel::class.java)
    }

    @Provides
    fun provideProgress(withPreloader: WithPreloaderImpl): WithPreloader = withPreloader
}