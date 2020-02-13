package jp.co.soramitsu.feature_wallet_impl.presentation.details.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.details.TransactionDetailsViewModel
import javax.inject.Named

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class TransactionDetailsModule {

    @Provides
    @IntoMap
    @ViewModelKey(TransactionDetailsViewModel::class)
    fun provideViewModel(
        walletRouter: WalletRouter,
        resourceManager: ResourceManager,
        numbersFormatter: NumbersFormatter,
        dateTimeFormatter: DateTimeFormatter,
        @Named("recipientId") recipientId: String,
        @Named("recipientFullName") recipientFullName: String,
        @Named("isFromList") isFromList: Boolean,
        transactionType: Transaction.Type,
        @Named("transactionId") transactionId: String,
        @Named("status") status: String,
        date: Long,
        @Named("amount") amount: Double,
        @Named("totalAmount") totalAmount: Double,
        @Named("fee") fee: Double,
        @Named("description") description: String
    ): ViewModel {
        return TransactionDetailsViewModel(
            walletRouter,
            resourceManager,
            numbersFormatter,
            dateTimeFormatter,
            recipientId,
            recipientFullName,
            isFromList,
            transactionType,
            transactionId,
            status,
            date,
            amount,
            totalAmount,
            fee,
            description
        )
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): TransactionDetailsViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(TransactionDetailsViewModel::class.java)
    }
}