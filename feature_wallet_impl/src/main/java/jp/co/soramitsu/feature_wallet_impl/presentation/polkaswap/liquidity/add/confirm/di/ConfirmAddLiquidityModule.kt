/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add.confirm.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add.confirm.ConfirmAddLiquidityViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Named

@ExperimentalCoroutinesApi
@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ConfirmAddLiquidityModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmAddLiquidityViewModel::class)
    fun provideConfirmAddLiquidityViewModel(
        router: WalletRouter,
        walletInteractor: WalletInteractor,
        polkaswapInteractor: PolkaswapInteractor,
        poolsManager: PoolsManager,
        numbersFormatter: NumbersFormatter,
        resourceManager: ResourceManager,
        @Named("tokenFrom") tokenFrom: Token,
        @Named("tokenTo") tokenTo: Token,
        @Named("slippageTolerance") slippageTolerance: Float,
        @Named("liquidityDetails") liquidityDetails: LiquidityDetails
    ): ViewModel =
        ConfirmAddLiquidityViewModel(
            router,
            walletInteractor,
            polkaswapInteractor,
            poolsManager,
            numbersFormatter,
            resourceManager,
            tokenFrom,
            tokenTo,
            slippageTolerance,
            liquidityDetails
        )

    @Provides
    fun provideConfirmAddLiquidityViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ConfirmAddLiquidityViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory)
            .get(ConfirmAddLiquidityViewModel::class.java)
    }
}
