/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.add.AddLiquidityFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove.RemoveLiquidityFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.remove.confirmation.RemoveLiquidityConfirmationFragment

@Subcomponent(
    modules = [
        LiquidityModule::class
    ]
)
@ScreenScope
interface LiquidityComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): LiquidityComponent
    }

    fun inject(addLiquidityFragment: AddLiquidityFragment)

    fun inject(removeLiquidityFragment: RemoveLiquidityFragment)

    fun inject(removeLiquidityConfirmationFragment: RemoveLiquidityConfirmationFragment)
}
