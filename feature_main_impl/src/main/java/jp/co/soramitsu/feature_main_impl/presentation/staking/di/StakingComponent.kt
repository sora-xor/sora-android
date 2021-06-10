package jp.co.soramitsu.feature_main_impl.presentation.staking.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.staking.StakingFragment

@Subcomponent(
    modules = [
        StakingModule::class
    ]
)
@ScreenScope
interface StakingComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): StakingComponent
    }

    fun inject(stakingFragment: StakingFragment)
}
