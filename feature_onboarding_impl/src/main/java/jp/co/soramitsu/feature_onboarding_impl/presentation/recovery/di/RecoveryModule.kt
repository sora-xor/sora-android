/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.recovery.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.di.app.ViewModelKey
import jp.co.soramitsu.common.di.app.ViewModelModule
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.recovery.RecoveryViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class RecoveryModule {

    @Provides
    @IntoMap
    @ViewModelKey(RecoveryViewModel::class)
    fun provideViewModel(interactor: OnboardingInteractor, router: OnboardingRouter, progress: WithProgress): ViewModel {
        return RecoveryViewModel(interactor, router, progress)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): RecoveryViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(RecoveryViewModel::class.java)
    }

    @Provides
    fun provideProgress(withProgress: WithProgressImpl): WithProgress = withProgress
}