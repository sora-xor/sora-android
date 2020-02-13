package jp.co.soramitsu.feature_wallet_impl.presentation.send.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferAmountFragment
import java.math.BigDecimal
import javax.inject.Named

@Subcomponent(
    modules = [
        TransferAmountModule::class
    ]
)
@ScreenScope
interface TransferAmountComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withFragment(fragment: Fragment): Builder

        @BindsInstance
        fun withRecipientId(@Named("recipientId") recipientId: String): Builder

        @BindsInstance
        fun withRecipientFullName(@Named("recipientFullName") recipientFullName: String): Builder

        @BindsInstance
        fun withInitialAmount(initialAmount: BigDecimal): Builder

        fun build(): TransferAmountComponent
    }

    fun inject(transferAmountFragment: TransferAmountFragment)
}