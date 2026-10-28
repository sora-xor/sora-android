/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
