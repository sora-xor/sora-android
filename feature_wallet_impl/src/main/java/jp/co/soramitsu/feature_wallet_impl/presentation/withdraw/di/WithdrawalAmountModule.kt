/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.withdraw.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.withdraw.WithdrawalAmountViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class WithdrawalAmountModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(WithdrawalAmountViewModel::class)
    fun provideViewModel(interactor: WalletInteractor, router: WalletRouter, progress: WithProgress, numbersFormatter: NumbersFormatter): ViewModel {
        return WithdrawalAmountViewModel(interactor, router, progress, numbersFormatter)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): WithdrawalAmountViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(WithdrawalAmountViewModel::class.java)
    }
}