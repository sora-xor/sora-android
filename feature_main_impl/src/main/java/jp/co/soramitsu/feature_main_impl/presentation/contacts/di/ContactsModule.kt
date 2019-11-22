/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.contacts.di

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
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeDecoder
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.contacts.ContactsViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ContactsModule {

    @Provides
    @IntoMap
    @ViewModelKey(ContactsViewModel::class)
    fun provideViewModel(interactor: WalletInteractor, router: MainRouter, preloader: WithPreloader, qrCodeDecoder: QrCodeDecoder, resourceManager: ResourceManager): ViewModel {
        return ContactsViewModel(interactor, router, preloader, qrCodeDecoder, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ContactsViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(ContactsViewModel::class.java)
    }

    @Provides
    fun providePreloader(withPreloader: WithPreloaderImpl): WithPreloader = withPreloader
}