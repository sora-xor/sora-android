/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.app

import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import jp.co.soramitsu.common.presentation.viewmodel.SoraViewModelFactory

@Module
abstract class ViewModelModule {

    @Binds
    abstract fun bindViewModelFactory(factory: SoraViewModelFactory): ViewModelProvider.Factory
}