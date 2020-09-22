package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.WalletViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class WalletModule {

    @Provides
    @IntoMap
    @ViewModelKey(WalletViewModel::class)
    fun provideViewModel(
        ethInteractor: EthereumInteractor,
        walletInteractor: WalletInteractor,
        router: WalletRouter,
        pushHandler: PushHandler,
        numbersFormatter: NumbersFormatter,
        transactionMappers: TransactionMappers,
        resourceManager: ResourceManager,
        clipboardManager: ClipboardManager
    ): ViewModel {
        return WalletViewModel(ethInteractor, walletInteractor, router, numbersFormatter, resourceManager, clipboardManager, transactionMappers, pushHandler)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): WalletViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(WalletViewModel::class.java)
    }
}