/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.di

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
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add.AddLiquidityViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove.RemoveLiquidityViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove.confirmation.RemoveLiquidityConfirmationViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@Module(
    includes = [
        ViewModelModule::class
    ]
)
class LiquidityModule {

    @Provides
    @IntoMap
    @ViewModelKey(AddLiquidityViewModel::class)
    fun provideAddLiquidityViewModel(
        router: WalletRouter,
        walletInteractor: WalletInteractor,
        polkaswapInteractor: PolkaswapInteractor,
        poolsManager: PoolsManager,
        numbersFormatter: NumbersFormatter,
        resourceManager: ResourceManager,
    ): ViewModel =
        AddLiquidityViewModel(
            router,
            walletInteractor,
            polkaswapInteractor,
            poolsManager,
            numbersFormatter,
            resourceManager
        )

    @Provides
    fun provideAddLiquidityViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): AddLiquidityViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(AddLiquidityViewModel::class.java)
    }

    @Provides
    @IntoMap
    @ViewModelKey(RemoveLiquidityViewModel::class)
    fun provideRemoveLiquidityViewModel(
        router: WalletRouter,
        interactor: WalletInteractor,
        polkaswapInteractor: PolkaswapInteractor,
        numbersFormatter: NumbersFormatter,
        resourceManager: ResourceManager,
    ): ViewModel =
        RemoveLiquidityViewModel(
            router,
            interactor,
            polkaswapInteractor,
            numbersFormatter,
            resourceManager
        )

    @Provides
    fun provideRemoveLiquidityViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): RemoveLiquidityViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(RemoveLiquidityViewModel::class.java)
    }

    @Provides
    @IntoMap
    @ViewModelKey(RemoveLiquidityConfirmationViewModel::class)
    fun provideRemoveLiquidityConfirmationViewModel(
        router: WalletRouter,
        interactor: WalletInteractor,
        polkaswapInteractor: PolkaswapInteractor,
        numbersFormatter: NumbersFormatter,
        resourceManager: ResourceManager,
    ): ViewModel =
        RemoveLiquidityConfirmationViewModel(
            router,
            interactor,
            polkaswapInteractor,
            numbersFormatter,
            resourceManager
        )

    @Provides
    fun provideRemoveLiquidityConfirmationViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): RemoveLiquidityConfirmationViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(RemoveLiquidityConfirmationViewModel::class.java)
    }
}
