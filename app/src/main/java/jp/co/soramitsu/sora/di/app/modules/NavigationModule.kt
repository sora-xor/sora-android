/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app.modules

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
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
}
