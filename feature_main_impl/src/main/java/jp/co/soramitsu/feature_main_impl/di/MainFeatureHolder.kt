/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_information_api.di.InformationFeatureApi
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_notification_api.di.NotificationFeatureApi
import jp.co.soramitsu.feature_votable_api.di.VotableFeatureApi
import jp.co.soramitsu.feature_sse_api.di.EventFeatureApi
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
            .notificationFeatureApi(getFeature(NotificationFeatureApi::class.java))
            .walletFeatureApi(getFeature(WalletFeatureApi::class.java))
            .informationFeatureApi(getFeature(InformationFeatureApi::class.java))
            .didFeatureApi(didApi())
            .ethereumFeatureApi(getFeature(EthereumFeatureApi::class.java))
            .votableFeatureApi(getFeature(VotableFeatureApi::class.java))
            .networkApi(networkApi())
            .eventFeatureApi(getFeature(EventFeatureApi::class.java))
            .commonApi(commonApi())
            .build()
        return DaggerMainFeatureComponent.builder()
            .withDependencies(mainFeatureDependencies)
            .navigator(mainRouter)
            .build()
    }
}