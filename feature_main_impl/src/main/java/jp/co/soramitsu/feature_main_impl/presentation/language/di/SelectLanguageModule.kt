package jp.co.soramitsu.feature_main_impl.presentation.language.di

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
import jp.co.soramitsu.feature_main_impl.presentation.language.SelectLanguageViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class SelectLanguageModule {

    @Provides
    @IntoMap
    @ViewModelKey(SelectLanguageViewModel::class)
    fun provideViewModel(interactor: MainInteractor, router: MainRouter, resourceManager: ResourceManager): ViewModel {
        return SelectLanguageViewModel(interactor, router, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): SelectLanguageViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(SelectLanguageViewModel::class.java)
    }
}
