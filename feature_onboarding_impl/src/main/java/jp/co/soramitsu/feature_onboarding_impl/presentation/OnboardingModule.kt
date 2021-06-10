/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class OnboardingModule {

    @Provides
    @IntoMap
    @ViewModelKey(OnboardingViewModel::class)
    fun provideViewModel(invitationHandler: InvitationHandler, runtimeManager: RuntimeManager): ViewModel {
        return OnboardingViewModel(invitationHandler, runtimeManager)
    }

    @Provides
    fun provideViewModelCreator(activity: AppCompatActivity, viewModelFactory: ViewModelProvider.Factory): OnboardingViewModel {
        return ViewModelProviders.of(activity, viewModelFactory).get(OnboardingViewModel::class.java)
    }
}
