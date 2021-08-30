package jp.co.soramitsu.feature_main_impl.presentation.detail.referendum.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.detail.referendum.DetailReferendumViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class DetailReferendumModule {
    @Provides
    @IntoMap
    @ViewModelKey(DetailReferendumViewModel::class)
    fun provideViewModel(
        interactor: MainInteractor,
        router: MainRouter,
        referendumId: String,
        numbersFormatter: NumbersFormatter,
        resourceManager: ResourceManager
    ): ViewModel {
        return DetailReferendumViewModel(
            interactor,
            referendumId,
            router,
            numbersFormatter,
            resourceManager
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): DetailReferendumViewModel {
        return ViewModelProvider(
            fragment,
            viewModelFactory
        ).get(DetailReferendumViewModel::class.java)
    }
}
