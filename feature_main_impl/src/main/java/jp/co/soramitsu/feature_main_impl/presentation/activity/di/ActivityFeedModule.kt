/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.activity.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithPreloaderImpl
import jp.co.soramitsu.common.di.app.ViewModelKey
import jp.co.soramitsu.common.di.app.ViewModelModule
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.activity.ActivityFeedViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class ActivityFeedModule {

    @Provides
    fun providePreloader(): WithPreloader {
        return WithPreloaderImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(ActivityFeedViewModel::class)
    fun provideViewModel(interactor: MainInteractor, router: MainRouter, preloader: WithPreloader, resourceManager: ResourceManager): ViewModel {
        return ActivityFeedViewModel(interactor, router, preloader, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ActivityFeedViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(ActivityFeedViewModel::class.java)
    }
}