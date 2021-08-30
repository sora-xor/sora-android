package jp.co.soramitsu.feature_wallet_impl.presentation.send.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferType
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferAmountViewModel
import javax.inject.Named

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class TransferAmountModule {

    @Provides
    @IntoMap
    @ViewModelKey(TransferAmountViewModel::class)
    fun provideViewModel(
        walletInteractor: WalletInteractor,
        router: WalletRouter,
        numbersFormatter: NumbersFormatter,
        @Named("recipientId") recipientId: String,
        @Named("assetId") assetId: String,
        @Named("recipientFullName") recipientFullName: String,
        transferType: TransferType,
        clipboardManager: ClipboardManager,
    ): ViewModel {
        return TransferAmountViewModel(
            walletInteractor, router, numbersFormatter,
            recipientId, assetId, recipientFullName, transferType, clipboardManager,
        )
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): TransferAmountViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(TransferAmountViewModel::class.java)
    }
}
