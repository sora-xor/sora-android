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

package jp.co.soramitsu.sora.di.app.modules

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.common.account.AccountOptionsRouter
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.sora.navigation.Navigator

@InstallIn(SingletonComponent::class)
@Module
class NavigationModule {

    @Singleton
    @Provides
    fun provideNavigator(): Navigator = Navigator()

    @Singleton
    @Provides
    fun provideMainRouter(navigator: Navigator): MainRouter = navigator

    @Singleton
    @Provides
    fun provideWalletRouter(navigator: Navigator): WalletRouter = navigator

    @Singleton
    @Provides
    fun providePolkaswapRouter(navigator: Navigator): PolkaswapRouter = navigator

    @Singleton
    @Provides
    fun provideAssetsRouter(navigator: Navigator): AssetsRouter = navigator

    @Singleton
    @Provides
    fun provideReferralRouter(navigator: Navigator): ReferralRouter = navigator

    @Singleton
    @Provides
    fun provideSelectNodeRouter(navigator: Navigator): SelectNodeRouter = navigator

    @Singleton
    @Provides
    fun provideAccountOptionsRouter(navigator: Navigator): AccountOptionsRouter = navigator
}
