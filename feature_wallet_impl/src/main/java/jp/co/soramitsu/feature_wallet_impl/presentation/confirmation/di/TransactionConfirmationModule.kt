package jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithPreloaderImpl
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.TransactionConfirmationViewModel
import javax.inject.Named

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class TransactionConfirmationModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    fun providePreloader(): WithPreloader {
        return WithPreloaderImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(TransactionConfirmationViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
        progress: WithProgress,
        resourceManager: ResourceManager,
        numbersFormatter: NumbersFormatter,
        @Named("amount") amount: Double,
        @Named("fee") fee: Double,
        @Named("description") description: String,
        @Named("ethAddress") ethAddress: String,
        @Named("recipientFullName") recipientFullName: String,
        @Named("recipientId") recipientId: String,
        @Named("notaryAddress") notaryAddress: String,
        @Named("feeAddress") feeAddress: String
    ): ViewModel {
        return TransactionConfirmationViewModel(interactor, router, progress, resourceManager,
            numbersFormatter, amount, fee, description, ethAddress, recipientFullName, recipientId, notaryAddress, feeAddress)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): TransactionConfirmationViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(TransactionConfirmationViewModel::class.java)
    }
}