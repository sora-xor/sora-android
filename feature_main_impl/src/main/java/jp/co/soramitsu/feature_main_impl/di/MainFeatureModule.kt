/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.ASSETS_HUB_NAME
import jp.co.soramitsu.common.domain.SingleFeatureStorageManager
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_main_impl.MainStarterImpl
import jp.co.soramitsu.feature_main_impl.domain.subs.BalanceFeatureStorageManager

@Module
@InstallIn(SingletonComponent::class)
class MainFeatureModule {

    @Singleton
    @Provides
    fun provideOnboardingStarter(starter: MainStarterImpl): MainStarter = starter

    @Singleton
    @Provides
    @IntoMap
    @StringKey(ASSETS_HUB_NAME)
    fun provideBalanceFeatureStorageManager(balance: BalanceFeatureStorageManager): SingleFeatureStorageManager =
        balance
}
