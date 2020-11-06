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
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.confirmation.TransactionConfirmationViewModel
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import java.math.BigDecimal
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
        walletInteractor: WalletInteractor,
        ethereumInteractor: EthereumInteractor,
        router: WalletRouter,
        progress: WithProgress,
        resourceManager: ResourceManager,
        numbersFormatter: NumbersFormatter,
        textFormatter: TextFormatter,
        @Named("partialAmount") partialAmount: BigDecimal,
        @Named("amount") amount: BigDecimal,
        @Named("minerFee") minerFee: BigDecimal,
        @Named("transactionFee") transactionFee: BigDecimal,
        @Named("description") description: String,
        @Named("peerFullName") peerFullName: String,
        @Named("peerId") peerId: String,
        transferType: TransferType
    ): ViewModel {
        return TransactionConfirmationViewModel(walletInteractor, ethereumInteractor, router, progress, resourceManager,
            numbersFormatter, textFormatter, partialAmount, amount, minerFee, transactionFee, description, peerFullName, peerId, transferType)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): TransactionConfirmationViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(TransactionConfirmationViewModel::class.java)
    }
}