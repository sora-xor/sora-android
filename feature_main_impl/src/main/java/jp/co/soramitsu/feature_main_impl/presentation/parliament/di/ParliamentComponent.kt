package jp.co.soramitsu.feature_main_impl.presentation.parliament.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_main_impl.presentation.parliament.ParliamentFragment

@Subcomponent(
    modules = [
        ParliamentModule::class
    ]
)
@ScreenScope
interface ParliamentComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): ParliamentComponent
    }

    fun inject(stakingFragment: ParliamentFragment)
}
