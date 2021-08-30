package jp.co.soramitsu.feature_wallet_impl.presentation.details.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.details.ExtrinsicDetailsViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class TransactionDetailsModule {

    @Provides
    @IntoMap
    @ViewModelKey(ExtrinsicDetailsViewModel::class)
    fun provideViewModel(
        walletInteractor: WalletInteractor,
        ethereumInteractor: EthereumInteractor,
        walletRouter: WalletRouter,
        resourceManager: ResourceManager,
        numbersFormatter: NumbersFormatter,
        textFormatter: TextFormatter,
        dateTimeFormatter: DateTimeFormatter,
        txHash: String,
        clipboardManager: ClipboardManager
    ): ViewModel {
        return ExtrinsicDetailsViewModel(
            txHash,
            walletInteractor,
            walletRouter,
            numbersFormatter,
            dateTimeFormatter,
            resourceManager,
            clipboardManager,
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ExtrinsicDetailsViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory)
            .get(ExtrinsicDetailsViewModel::class.java)
    }
}
