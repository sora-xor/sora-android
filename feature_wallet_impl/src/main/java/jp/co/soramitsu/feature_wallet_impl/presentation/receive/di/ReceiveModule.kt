/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.receive.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.ReceiveViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ReceiveModule {

    @Provides
    @IntoMap
    @ViewModelKey(ReceiveViewModel::class)
    fun provideViewModel(interactor: WalletInteractor, walletRouter: WalletRouter, resourceManager: ResourceManager, qrCodeGenerator: QrCodeGenerator): ViewModel {
        return ReceiveViewModel(interactor, walletRouter, resourceManager, qrCodeGenerator)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ReceiveViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(ReceiveViewModel::class.java)
    }
}