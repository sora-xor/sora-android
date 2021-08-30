package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.di

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
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.MnemonicViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class MnemonicModule {

    @Provides
    @IntoMap
    @ViewModelKey(MnemonicViewModel::class)
    fun provideViewModel(interactor: OnboardingInteractor, router: OnboardingRouter, preloader: WithPreloader): ViewModel {
        return MnemonicViewModel(interactor, router, preloader)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): MnemonicViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(MnemonicViewModel::class.java)
    }

    @Provides
    fun provideProgress(withPreloader: WithPreloaderImpl): WithPreloader = withPreloader
}
