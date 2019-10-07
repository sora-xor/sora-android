/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.delegate.WithProgressImpl
import jp.co.soramitsu.common.di.app.ViewModelKey
import jp.co.soramitsu.common.di.app.ViewModelModule
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.personaldataedit.PersonalDataEditViewModel

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class PersonalDataEditModule {

    @Provides
    fun provideProgress(): WithProgress {
        return WithProgressImpl()
    }

    @Provides
    @IntoMap
    @ViewModelKey(PersonalDataEditViewModel::class)
    fun provideViewModel(interactor: MainInteractor, router: MainRouter, progress: WithProgress): ViewModel {
        return PersonalDataEditViewModel(interactor, router, progress)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): PersonalDataEditViewModel {
        return ViewModelProviders.of(fragment, viewModelFactory).get(PersonalDataEditViewModel::class.java)
    }
}