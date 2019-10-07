/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.transactionconfirmation.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithPreloaderImpl
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.di.app.ViewModelKey
import jp.co.soramitsu.common.di.app.ViewModelModule
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.transactionconfirmation.TransactionConfirmationViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class TransactionConfirmationModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    fun providePreloader(): WithPreloader {
        return WithPreloaderImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(TransactionConfirmationViewModel::class)
    fun provideViewModel(interactor: WalletInteractor, router: MainRouter, progress: WithProgress, resourceManager: ResourceManager): ViewModel {
        return TransactionConfirmationViewModel(interactor, router, progress, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): TransactionConfirmationViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(TransactionConfirmationViewModel::class.java)
    }
}