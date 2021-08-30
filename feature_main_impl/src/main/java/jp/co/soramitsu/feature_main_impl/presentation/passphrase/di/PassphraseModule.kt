package jp.co.soramitsu.feature_main_impl.presentation.passphrase.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithPreloaderImpl
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.passphrase.PassphraseViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class PassphraseModule {

    @Provides
    fun providePreloader(withPreloader: WithPreloaderImpl): WithPreloader = withPreloader

    @Provides
    @IntoMap
    @ViewModelKey(PassphraseViewModel::class)
    fun provideViewModel(interactor: MainInteractor, preloader: WithPreloader): ViewModel {
        return PassphraseViewModel(interactor, preloader)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): PassphraseViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(PassphraseViewModel::class.java)
    }
}
