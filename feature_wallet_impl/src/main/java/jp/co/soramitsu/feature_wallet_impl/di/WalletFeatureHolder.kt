/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.AccountSettings
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer,
    private val walletRouter: WalletRouter,
    private val accountSettings: AccountSettings
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val walletFeatureDependencies = DaggerWalletFeatureComponent_WalletFeatureDependenciesComponent.builder()
            .commonApi(commonApi())
            .networkApi(networkApi())
            .didFeatureApi(didApi())
            .dbApi(getFeature(DbApi::class.java))
            .ethereumFeatureApi(getFeature(EthereumFeatureApi::class.java))
            .build()

        return DaggerWalletFeatureComponent.builder()
            .withDependencies(walletFeatureDependencies)
            .withAccountSettings(accountSettings)
            .router(walletRouter)
            .build()
    }
}