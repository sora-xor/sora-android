package jp.co.soramitsu.feature_wallet_impl.presentation.contacts.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.delegate.WithPreloaderImpl
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.ContactsViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.qr.QrCodeDecoder
import jp.co.soramitsu.feature_wallet_impl.presentation.util.EthereumAddressValidator

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ContactsModule {

    @Provides
    @IntoMap
    @ViewModelKey(ContactsViewModel::class)
    fun provideViewModel(interactor: WalletInteractor, ethereumInteractor: EthereumInteractor, router: WalletRouter, preloader: WithPreloader, qrCodeDecoder: QrCodeDecoder, resourceManager: ResourceManager, ethereumAddressValidator: EthereumAddressValidator, avatarGenerator: AccountAvatarGenerator): ViewModel {
        return ContactsViewModel(interactor, router, preloader, qrCodeDecoder, resourceManager, ethereumAddressValidator, ethereumInteractor, avatarGenerator)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ContactsViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(ContactsViewModel::class.java)
    }

    @Provides
    fun providePreloader(withPreloader: WithPreloaderImpl): WithPreloader = withPreloader
}
