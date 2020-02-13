/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.di

import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_di.holder.FeatureApiHolder
import jp.co.soramitsu.core_di.holder.FeatureContainer
import jp.co.soramitsu.core_network_api.di.NetworkApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_did_api.di.DidFeatureApi
import jp.co.soramitsu.feature_information_api.di.InformationFeatureApi
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_project_api.di.ProjectFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer,
    private val mainRouter: MainRouter
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val mainFeatureDependencies = DaggerMainFeatureComponent_MainFeatureDependenciesComponent.builder()
            .accountFeatureApi(getFeature(AccountFeatureApi::class.java))
            .walletFeatureApi(getFeature(WalletFeatureApi::class.java))
            .commonApi(getFeature(CommonApi::class.java))
            .informationFeatureApi(getFeature(InformationFeatureApi::class.java))
            .didFeatureApi(getFeature(DidFeatureApi::class.java))
            .projectFeatureApi(getFeature(ProjectFeatureApi::class.java))
            .networkApi(getFeature(NetworkApi::class.java))
            .build()
        return DaggerMainFeatureComponent.builder()
            .withDependencies(mainFeatureDependencies)
            .navigator(mainRouter)
            .build()
    }
}