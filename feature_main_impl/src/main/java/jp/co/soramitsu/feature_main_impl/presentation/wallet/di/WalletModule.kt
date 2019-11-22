/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.wallet.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.app.ViewModelKey
import jp.co.soramitsu.common.di.app.ViewModelModule
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_impl.domain.WalletInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.wallet.WalletViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class WalletModule {

    @Provides
    @IntoMap
    @ViewModelKey(WalletViewModel::class)
    fun provideViewModel(walletInteractor: WalletInteractor, router: MainRouter, pushHandler: PushHandler, numbersFormatter: NumbersFormatter): ViewModel {
        return WalletViewModel(walletInteractor, router, numbersFormatter, pushHandler)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): WalletViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(WalletViewModel::class.java)
    }
}