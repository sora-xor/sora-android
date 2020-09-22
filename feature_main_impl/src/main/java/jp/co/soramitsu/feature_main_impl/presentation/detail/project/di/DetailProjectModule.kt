package jp.co.soramitsu.feature_main_impl.presentation.detail.project.di

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
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.detail.project.DetailProjectViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class DetailProjectModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    fun withPreloader(): WithPreloader {
        return WithPreloaderImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(DetailProjectViewModel::class)
    fun provideViewModel(
        interactor: MainInteractor,
        preloader: WithPreloader,
        router: MainRouter,
        projectId: String,
        numbersFormatter: NumbersFormatter,
        resourceManager: ResourceManager
    ): ViewModel {
        return DetailProjectViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): DetailProjectViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(DetailProjectViewModel::class.java)
    }
}