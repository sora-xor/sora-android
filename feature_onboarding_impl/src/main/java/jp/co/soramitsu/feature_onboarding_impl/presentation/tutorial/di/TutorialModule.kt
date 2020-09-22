package jp.co.soramitsu.feature_onboarding_impl.presentation.tutorial.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.tutorial.TutorialViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class TutorialModule {

    @Provides
    @IntoMap
    @ViewModelKey(TutorialViewModel::class)
    fun provideViewModel(interactor: OnboardingInteractor, router: OnboardingRouter, progress: WithProgress): ViewModel {
        return TutorialViewModel(interactor, router, progress)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): TutorialViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(TutorialViewModel::class.java)
    }

    @Provides
    fun provideProgress(withProgress: WithProgressImpl): WithProgress = withProgress
}