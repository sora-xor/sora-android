/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app.modules

import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.core_db.di.DbHolder
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureHolder
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_ethereum_impl.di.EthereumFeatureHolder
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.di.MainFeatureHolder
import jp.co.soramitsu.feature_notification_api.di.NotificationFeatureApi
import jp.co.soramitsu.feature_notification_impl.di.NotificationFeatureHolder
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureHolder
import jp.co.soramitsu.feature_votable_api.di.VotableFeatureApi
import jp.co.soramitsu.feature_votable_impl.di.VotableFeatureHolder
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureHolder
import jp.co.soramitsu.sora.SoraApp
import jp.co.soramitsu.sora.di.app_feature.AppFeatureComponent
import jp.co.soramitsu.sora.di.app_feature.AppFeatureHolder
import javax.inject.Singleton

@Module
interface ComponentHolderModule {

    @Singleton
    @Binds
    fun provideFeatureContainer(application: SoraApp): FeatureContainer

    @Singleton
    @Binds
    @ClassKey(AppFeatureComponent::class)
    @IntoMap
    fun provideAppFeature(appFeatureHolder: AppFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(AccountFeatureApi::class)
    @IntoMap
    fun provideAccountFeature(accountFeatureHolder: AccountFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(EthereumFeatureApi::class)
    @IntoMap
    fun provideEthereumFeature(ethereumFeatureHolder: EthereumFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(VotableFeatureApi::class)
    @IntoMap
    fun provideVotableFeature(votableFeatureHolder: VotableFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(WalletFeatureApi::class)
    @IntoMap
    fun provideWalletFeature(walletFeatureHolder: WalletFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(OnboardingFeatureApi::class)
    @IntoMap
    fun provideOnboardingFeature(onboardingFeatureHolder: OnboardingFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(MainFeatureApi::class)
    @IntoMap
    fun provideMainFeature(mainFeatureHolder: MainFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(DbApi::class)
    @IntoMap
    fun provideDbFeature(dbHolder: DbHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(NotificationFeatureApi::class)
    @IntoMap
    fun provideNotificationFeature(notificationHolder: NotificationFeatureHolder): FeatureApiHolder
}
