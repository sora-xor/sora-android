package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.AssetListViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class AssetListModule {

    @Provides
    @IntoMap
    @ViewModelKey(AssetListViewModel::class)
    fun provideViewModel2(
        interactor: WalletInteractor,
        ethInteractor: EthereumInteractor,
        nf: NumbersFormatter,
        walletRouter: WalletRouter,
        rm: ResourceManager,
        mode: AssetListMode
    ): ViewModel {
        return AssetListViewModel(interactor, ethInteractor, nf, walletRouter, rm, mode)
    }

    @Provides
    fun provideViewModelCreator2(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): AssetListViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(AssetListViewModel::class.java)
    }
}
