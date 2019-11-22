/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.di

import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_di.holder.FeatureApiHolder
import jp.co.soramitsu.core_di.holder.FeatureContainer
import jp.co.soramitsu.core_network_api.di.NetworkApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_did_api.di.DidFeatureApi
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val onboardingFeatureDependencies = DaggerOnboardingFeatureComponent_OnboardingFeatureDependenciesComponent.builder()
            .accountFeatureApi(getFeature(AccountFeatureApi::class.java))
            .didFeatureApi(getFeature(DidFeatureApi::class.java))
            .mainFeatureApi(getFeature(MainFeatureApi::class.java))
            .walletFeatureApi(getFeature(WalletFeatureApi::class.java))
            .networkApi(getFeature(NetworkApi::class.java))
            .commonApi(getFeature(CommonApi::class.java))
            .build()
        return DaggerOnboardingFeatureComponent.builder()
            .onboardingFeatureDependencies(onboardingFeatureDependencies)
            .build()
    }
}