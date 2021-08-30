package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaSwapFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.PolkaSwapInfoFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool.PoolFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swap.SwapFragment

@Subcomponent(
    modules = [
        PolkaSwapModule::class
    ]
)
@ScreenScope
interface PolkaSwapComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): PolkaSwapComponent
    }

    fun inject(polkaSwapFragment: PolkaSwapFragment)

    fun inject(swapFragment: SwapFragment)

    fun inject(poolFragment: PoolFragment)

    fun inject(polkaswapInfo: PolkaSwapInfoFragment)
}
