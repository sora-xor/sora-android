/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.receiveamount.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.app.ViewModelKey
import jp.co.soramitsu.common.di.app.ViewModelModule
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.receiveamount.ReceiveAmountViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ReceiveAmountModule {

    @Provides
    @IntoMap
    @ViewModelKey(ReceiveAmountViewModel::class)
    fun provideViewModel(interactor: WalletInteractor, router: MainRouter, resourceManager: ResourceManager, qrCodeGenerator: QrCodeGenerator): ViewModel {
        return ReceiveAmountViewModel(interactor, router, resourceManager, qrCodeGenerator)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ReceiveAmountViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(ReceiveAmountViewModel::class.java)
    }
}