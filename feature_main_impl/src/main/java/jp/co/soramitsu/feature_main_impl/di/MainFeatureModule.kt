/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_main_impl.MainStarterImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class MainFeatureModule {

    @Singleton
    @Provides
    fun provideOnboardingStarter(starter: MainStarterImpl): MainStarter = starter
}
