package jp.co.soramitsu.feature_main_impl.presentation.userverification.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.presentation.userverification.UserVerificationViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class UserVerificationModule {

    @Provides
    @IntoMap
    @ViewModelKey(UserVerificationViewModel::class)
    fun provideViewModel(pinCodeInteractor: PinCodeInteractor, mainRouter: MainRouter): ViewModel {
        return UserVerificationViewModel(mainRouter, pinCodeInteractor)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): UserVerificationViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(UserVerificationViewModel::class.java)
    }
}
