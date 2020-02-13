/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class PinCodeModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(PinCodeViewModel::class)
    fun provideViewModel(interactor: PinCodeInteractor, mainRouter: MainRouter, progress: WithProgress, maxPinCodeLength: Int): ViewModel {
        return PinCodeViewModel(interactor, mainRouter, progress, maxPinCodeLength)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): PinCodeViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(PinCodeViewModel::class.java)
    }
}