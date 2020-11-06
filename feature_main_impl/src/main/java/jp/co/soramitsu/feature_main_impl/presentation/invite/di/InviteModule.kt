/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.invite.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.InvitationInteractor
import jp.co.soramitsu.feature_main_impl.presentation.invite.InviteViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class InviteModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(InviteViewModel::class)
    fun provideViewModel(
        interactor: InvitationInteractor,
        router: MainRouter,
        progress: WithProgress,
        deviceParamsProvider: DeviceParamsProvider,
        invitationHandler: InvitationHandler,
        timerWrapper: TimerWrapper,
        resourceManager: ResourceManager
    ): ViewModel {
        return InviteViewModel(interactor, router, progress, deviceParamsProvider, invitationHandler, timerWrapper, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): InviteViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(InviteViewModel::class.java)
    }

    @Provides
    fun provideTimerWrapper(): TimerWrapper = TimerWrapper()
}