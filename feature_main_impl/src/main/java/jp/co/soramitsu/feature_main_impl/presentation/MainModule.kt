/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class MainModule {

    @Provides
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    fun provideViewModel(
        healthChecker: HealthChecker,
        interactor: MainInteractor,
        deviceParamsProvider: DeviceParamsProvider,
        progress: WithProgress,
        invitationHandler: InvitationHandler
    ): ViewModel {
        return MainViewModel(healthChecker, interactor, deviceParamsProvider, progress, invitationHandler)
    }

    @Provides
    fun provideViewModelCreator(activity: AppCompatActivity, viewModelFactory: ViewModelProvider.Factory): MainViewModel {
        return ViewModelProviders.of(activity, viewModelFactory).get(MainViewModel::class.java)
    }

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }
}