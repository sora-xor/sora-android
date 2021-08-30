/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.polkaswap.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelKey
import jp.co.soramitsu.core_di.holder.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_main_impl.presentation.polkaswap.PolkaSwapViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class PolkaSwapModule {

    @Provides
    @IntoMap
    @ViewModelKey(PolkaSwapViewModel::class)
    fun provideViewModel(): ViewModel {
        return PolkaSwapViewModel()
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): PolkaSwapViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(PolkaSwapViewModel::class.java)
    }
}
