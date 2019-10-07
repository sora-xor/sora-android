/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.privacy.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.app.ViewModelKey
import jp.co.soramitsu.common.di.app.ViewModelModule
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.privacy.PrivacyViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class PrivacyModule {

    @Provides
    @IntoMap
    @ViewModelKey(PrivacyViewModel::class)
    fun provideViewModel(router: MainRouter): ViewModel {
        return PrivacyViewModel(router)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): PrivacyViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(PrivacyViewModel::class.java)
    }
}