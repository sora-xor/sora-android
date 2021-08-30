package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.di

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
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.AssetSettingsViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class AssetSettingsModule {

    @Provides
    @IntoMap
    @ViewModelKey(AssetSettingsViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        numbersFormatter: NumbersFormatter,
        resourceManager: ResourceManager,
        router: WalletRouter
    ): ViewModel {
        return AssetSettingsViewModel(interactor, numbersFormatter, resourceManager, router)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): AssetSettingsViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(AssetSettingsViewModel::class.java)
    }
}
