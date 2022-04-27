/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add.confirm.ConfirmAddLiquidityFragment
import javax.inject.Named

@Subcomponent(
    modules = [
        ConfirmAddLiquidityModule::class
    ]
)
@ScreenScope
interface ConfirmAddLiquidityComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withTokenFrom(@Named("tokenFrom") tokenFrom: Token): Builder

        @BindsInstance
        fun withTokenTo(@Named("tokenTo") tokenTo: Token): Builder

        @BindsInstance
        fun withLiquidityDetails(@Named("liquidityDetails") liquidityDetails: LiquidityDetails): Builder

        @BindsInstance
        fun withSlippageTolerance(@Named("slippageTolerance") slippageTolerance: Float): Builder

        fun build(): ConfirmAddLiquidityComponent
    }

    fun inject(confirmAddLiquidityFragment: ConfirmAddLiquidityFragment)
}
