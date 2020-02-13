package jp.co.soramitsu.feature_wallet_impl.presentation.withdraw.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.withdraw.WithdrawalAmountFragment

@Subcomponent(
    modules = [
        WithdrawalAmountModule::class
    ]
)
@ScreenScope
interface WithdrawalAmountComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        fun build(): WithdrawalAmountComponent
    }

    fun inject(withdrawalAmountFragment: WithdrawalAmountFragment)
}