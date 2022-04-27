/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithPreloaderImpl
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import jp.co.soramitsu.feature_multiaccount_impl.presentation.mnemonic_confirmation.MnemonicConfirmationViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class MnemonicConfirmationModule {

    @Provides
    @IntoMap
    @ViewModelKey(MnemonicConfirmationViewModel::class)
    fun provideViewModel(
        interactor: MultiaccountInteractor,
        deviceVibrator: DeviceVibrator,
        router: MultiaccountRouter,
        preloader: WithPreloader
    ): ViewModel {
        return MnemonicConfirmationViewModel(interactor, deviceVibrator, router, preloader)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): MnemonicConfirmationViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory)
            .get(MnemonicConfirmationViewModel::class.java)
    }

    @Provides
    fun provideProgress(withPreloader: WithPreloaderImpl): WithPreloader = withPreloader
}
