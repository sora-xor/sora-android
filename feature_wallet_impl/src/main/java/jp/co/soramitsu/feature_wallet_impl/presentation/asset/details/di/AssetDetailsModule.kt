package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.details.AssetDetailsViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import javax.inject.Named

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class AssetDetailsModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(AssetDetailsViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        numbersFormatter: NumbersFormatter,
        transactionMappers: TransactionMappers,
        avatarGenerator: AccountAvatarGenerator,
        clipboardManager: ClipboardManager,
        qrCodeDecoder: QrCodeDecoder,
        progress: WithProgress,
        @Named("assetId") assetId: String,
        router: WalletRouter
    ): ViewModel {
        return AssetDetailsViewModel(interactor, numbersFormatter, transactionMappers, avatarGenerator, clipboardManager, progress, assetId, qrCodeDecoder, router)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): AssetDetailsViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(AssetDetailsViewModel::class.java)
    }
}
