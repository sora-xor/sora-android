/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swapconfirmation.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swapconfirmation.SwapConfirmationViewModel
import java.math.BigDecimal
import javax.inject.Named

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class SwapConfirmationModule {

    @Provides
    @IntoMap
    @ViewModelKey(SwapConfirmationViewModel::class)
    fun provideSwapConfirmationViewModel(
        router: WalletRouter,
        interactor: WalletInteractor,
        polkaswapInteractor: PolkaswapInteractor,
        nf: NumbersFormatter,
        rm: ResourceManager,
        @Named("inputToken") inputToken: Token,
        @Named("inputAmount") inputAmount: BigDecimal,
        @Named("outputToken") outputToken: Token,
        @Named("outputAmount") outputAmount: BigDecimal,
        desired: WithDesired,
        details: SwapDetails,
        @Named("feeToken") feeToken: Token,
        slippageTolerance: Float,
    ): ViewModel {
        return SwapConfirmationViewModel(
            router,
            interactor,
            polkaswapInteractor,
            nf,
            rm,
            inputToken,
            inputAmount,
            outputToken,
            outputAmount,
            desired,
            details,
            feeToken,
            slippageTolerance,
        )
    }

    @Provides
    fun provideSwapConfirmationViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SwapConfirmationViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory)
            .get(SwapConfirmationViewModel::class.java)
    }
}
