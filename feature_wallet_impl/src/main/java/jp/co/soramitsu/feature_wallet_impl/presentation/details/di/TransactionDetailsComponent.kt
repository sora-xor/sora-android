package jp.co.soramitsu.feature_wallet_impl.presentation.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.details.ExtrinsicDetailsFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.details.SwapDetailsFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.details.TransactionDetailsFragment

@Subcomponent(
    modules = [
        TransactionDetailsModule::class
    ]
)
@ScreenScope
interface TransactionDetailsComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withTxHash(amount: String): Builder

        fun build(): TransactionDetailsComponent
    }

    fun inject(fragment: ExtrinsicDetailsFragment)

    fun inject(fragment: TransactionDetailsFragment)

    fun inject(fragment: SwapDetailsFragment)
}
