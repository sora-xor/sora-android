package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.WalletViewModel

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
        walletInteractor: WalletInteractor,
        router: WalletRouter,
        pushHandler: PushHandler,
        numbersFormatter: NumbersFormatter,
        dateTimeFormatter: DateTimeFormatter,
        resourceManager: ResourceManager
    ): ViewModel {
        return WalletViewModel(walletInteractor, router, numbersFormatter, dateTimeFormatter, resourceManager, pushHandler)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): WalletViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(WalletViewModel::class.java)
    }
}