package jp.co.soramitsu.feature_main_impl.presentation.polkaswap.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.polkaswap.PolkaSwapFragment

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
}
