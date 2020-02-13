package jp.co.soramitsu.feature_wallet_impl.presentation.details.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.presentation.details.TransactionDetailsFragment
import javax.inject.Named

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
        fun withRecipientId(@Named("recipientId") recipientId: String): Builder

        @BindsInstance
        fun withRecipientFullName(@Named("recipientFullName") recipientFullName: String): Builder

        @BindsInstance
        fun withTransactionId(@Named("transactionId") transactionId: String): Builder

        @BindsInstance
        fun withStatus(@Named("status") status: String): Builder

        @BindsInstance
        fun withIsFromList(@Named("isFromList") isFromList: Boolean): Builder

        @BindsInstance
        fun withTransactionType(transcationType: Transaction.Type): Builder

        @BindsInstance
        fun withDate(date: Long): Builder

        @BindsInstance
        fun withAmount(@Named("amount") amount: Double): Builder

        @BindsInstance
        fun withTotalAmount(@Named("totalAmount") totalAmount: Double): Builder

        @BindsInstance
        fun withFee(@Named("fee") totalAmount: Double): Builder

        @BindsInstance
        fun withDescription(@Named("description") description: String): Builder

        fun build(): TransactionDetailsComponent
    }

    fun inject(transactionDetailsFragment: TransactionDetailsFragment)
}