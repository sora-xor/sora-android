package jp.co.soramitsu.feature_main_impl.presentation.about.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.about.AboutViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class AboutModule {

    @Provides
    @IntoMap
    @ViewModelKey(AboutViewModel::class)
    fun provideViewModel(interactor: MainInteractor, router: MainRouter, resourceManager: ResourceManager): ViewModel {
        return AboutViewModel(interactor, router, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): AboutViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(AboutViewModel::class.java)
    }
}
