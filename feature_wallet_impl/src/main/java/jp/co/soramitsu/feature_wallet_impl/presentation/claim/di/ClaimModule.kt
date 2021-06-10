package jp.co.soramitsu.feature_wallet_impl.presentation.claim.di

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
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.claim.ClaimViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ClaimModule {

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
    @ViewModelKey(ClaimViewModel::class)
    fun provideViewModel(
        walletInteractor: WalletInteractor,
        walletRouter: WalletRouter,
        resourceManager: ResourceManager
    ): ViewModel {
        return ClaimViewModel(walletRouter, walletInteractor, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ClaimViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(ClaimViewModel::class.java)
    }
}
