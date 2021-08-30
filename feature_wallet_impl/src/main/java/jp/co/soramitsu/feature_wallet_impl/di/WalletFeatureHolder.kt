/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
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
            .accountFeatureApi(getFeature(AccountFeatureApi::class.java))
            .commonApi(commonApi())
            .networkApi(networkApi())
            .dbApi(getFeature(DbApi::class.java))
            .ethereumFeatureApi(getFeature(EthereumFeatureApi::class.java))
            .build()

        return DaggerWalletFeatureComponent.builder()
            .withDependencies(walletFeatureDependencies)
            .router(walletRouter)
            .build()
    }
}
