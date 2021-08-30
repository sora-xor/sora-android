package jp.co.soramitsu.feature_main_impl.presentation.version.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_impl.presentation.version.UnsupportedVersionViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class UnsupportedVersionModule {

    @Provides
    @IntoMap
    @ViewModelKey(UnsupportedVersionViewModel::class)
    fun provideViewModel(): ViewModel {
        return UnsupportedVersionViewModel()
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): UnsupportedVersionViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(UnsupportedVersionViewModel::class.java)
    }
}
