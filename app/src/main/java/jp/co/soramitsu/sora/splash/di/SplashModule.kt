package jp.co.soramitsu.sora.splash.di

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import jp.co.soramitsu.sora.splash.domain.SplashRouter
import jp.co.soramitsu.sora.splash.presentation.SplashViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class SplashModule {

    @Provides
    @IntoMap
    @ViewModelKey(SplashViewModel::class)
    fun provideViewModel(interactor: SplashInteractor, router: SplashRouter): ViewModel {
        return SplashViewModel(interactor, router)
    }

    @Provides
    fun provideViewModelCreator(activity: AppCompatActivity, viewModelFactory: ViewModelProvider.Factory): SplashViewModel {
        return ViewModelProviders.of(activity, viewModelFactory).get(SplashViewModel::class.java)
    }
}