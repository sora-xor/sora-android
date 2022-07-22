/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_multiaccount_impl.MultiaccountRouterImpl
import jp.co.soramitsu.feature_multiaccount_impl.MultiaccountStarterImpl
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter

@Module
@InstallIn(ActivityRetainedComponent::class)
class MultiaccountFeatureModule {

    @ActivityRetainedScoped
    @Provides
    fun provideMultiaccountStarter(): MultiaccountStarter = MultiaccountStarterImpl()

    @ActivityRetainedScoped
    @Provides
    fun provideMultiaccountRouter(): MultiaccountRouter = MultiaccountRouterImpl()
}
