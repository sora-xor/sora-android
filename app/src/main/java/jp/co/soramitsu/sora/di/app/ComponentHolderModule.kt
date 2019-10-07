/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app

import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.common.di.app.CommonHolder
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.core_db.di.DbHolder
import jp.co.soramitsu.core_di.holder.FeatureApiHolder
import jp.co.soramitsu.core_di.holder.FeatureContainer
import jp.co.soramitsu.core_network_api.di.NetworkApi
import jp.co.soramitsu.core_network_impl.di.NetworkHolder
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureHolder
import jp.co.soramitsu.feature_did_api.di.DidFeatureApi
import jp.co.soramitsu.feature_did_impl.di.DidFeatureHolder
import jp.co.soramitsu.feature_information_api.di.InformationFeatureApi
import jp.co.soramitsu.feature_information_impl.di.InformationFeatureHolder
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.di.MainFeatureHolder
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureHolder
import jp.co.soramitsu.feature_project_api.di.ProjectFeatureApi
import jp.co.soramitsu.feature_project_impl.di.ProjectFeatureHolder
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
    @ClassKey(CommonApi::class)
    @IntoMap
    fun provideCommonHolder(commonHolder: CommonHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(NetworkApi::class)
    @IntoMap
    fun provideNetworkHolder(networkHolder: NetworkHolder): FeatureApiHolder

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
    @ClassKey(DidFeatureApi::class)
    @IntoMap
    fun provideDidFeature(didFeatureHolder: DidFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(ProjectFeatureApi::class)
    @IntoMap
    fun provideProjectFeature(projectFeatureHolder: ProjectFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(WalletFeatureApi::class)
    @IntoMap
    fun provideWalletFeature(walletFeatureHolder: WalletFeatureHolder): FeatureApiHolder

    @Singleton
    @Binds
    @ClassKey(InformationFeatureApi::class)
    @IntoMap
    fun provideInformationFeature(informationFeatureHolder: InformationFeatureHolder): FeatureApiHolder

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
}