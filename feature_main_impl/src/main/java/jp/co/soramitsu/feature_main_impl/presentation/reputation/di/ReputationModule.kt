package jp.co.soramitsu.feature_main_impl.presentation.reputation.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.reputation.ReputationViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ReputationModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(ReputationViewModel::class)
    fun provideViewModel(interactor: MainInteractor, router: MainRouter, timerWrapper: TimerWrapper, resourceManager: ResourceManager, formatter: NumbersFormatter): ViewModel {
        return ReputationViewModel(interactor, router, timerWrapper, resourceManager, formatter)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ReputationViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(ReputationViewModel::class.java)
    }

    @Provides
    fun provideTimerWrapper(): TimerWrapper = TimerWrapper()
}