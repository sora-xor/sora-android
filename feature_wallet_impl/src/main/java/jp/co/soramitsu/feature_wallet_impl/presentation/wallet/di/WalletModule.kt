/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithPreloaderImpl
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.WalletViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class WalletModule {

    @Provides
    @IntoMap
    @ViewModelKey(WalletViewModel::class)
    fun provideViewModel(
        walletInteractor: WalletInteractor,
        router: WalletRouter,
        preloader: WithPreloader,
        numbersFormatter: NumbersFormatter,
        transactionMappers: TransactionMappers,
        clipboardManager: ClipboardManager,
        qrCodeDecoder: QrCodeDecoder,
    ): ViewModel {
        return WalletViewModel(walletInteractor, router, preloader, numbersFormatter, clipboardManager, transactionMappers, qrCodeDecoder)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): WalletViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(WalletViewModel::class.java)
    }

    @Provides
    fun providePreloader(withPreloader: WithPreloaderImpl): WithPreloader = withPreloader
}
