/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaSwapViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swap.SwapViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class PolkaSwapModule {

    @Provides
    @IntoMap
    @ViewModelKey(PolkaSwapViewModel::class)
    fun provideViewModel(
        router: WalletRouter,
        polkaswapInteractor: PolkaswapInteractor,
        walletInteractor: WalletInteractor,
    ): ViewModel {
        return PolkaSwapViewModel(router, polkaswapInteractor)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): PolkaSwapViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(PolkaSwapViewModel::class.java)
    }

    @Provides
    @IntoMap
    @ViewModelKey(SwapViewModel::class)
    fun provideSwapViewModel(
        router: WalletRouter,
        interactor: WalletInteractor,
        polkaswapInteractor: PolkaswapInteractor,
        nf: NumbersFormatter,
        rm: ResourceManager,
    ): ViewModel {
        return SwapViewModel(router, interactor, polkaswapInteractor, nf, rm)
    }

    @Provides
    fun provideSwapViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SwapViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(SwapViewModel::class.java)
    }
}
