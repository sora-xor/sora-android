package jp.co.soramitsu.feature_main_impl.presentation.staking.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_impl.presentation.staking.StakingViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class StakingModule {

    @Provides
    @IntoMap
    @ViewModelKey(StakingViewModel::class)
    fun provideViewModel(): ViewModel {
        return StakingViewModel()
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): StakingViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(StakingViewModel::class.java)
    }
}
