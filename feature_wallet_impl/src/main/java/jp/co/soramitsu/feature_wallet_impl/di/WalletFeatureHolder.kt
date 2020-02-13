/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.core_di.holder.FeatureApiHolder
import jp.co.soramitsu.core_di.holder.FeatureContainer
import jp.co.soramitsu.core_network_api.di.NetworkApi
import jp.co.soramitsu.feature_did_api.di.DidFeatureApi
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer,
    private val walletRouter: WalletRouter
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val walletFeatureDependencies = DaggerWalletFeatureComponent_WalletFeatureDependenciesComponent.builder()
            .commonApi(getFeature(CommonApi::class.java))
            .networkApi(getFeature(NetworkApi::class.java))
            .dbApi(getFeature(DbApi::class.java))
            .didFeatureApi(getFeature(DidFeatureApi::class.java))
            .build()
        return DaggerWalletFeatureComponent.builder()
            .withDependencies(walletFeatureDependencies)
            .router(walletRouter)
            .build()
    }
}