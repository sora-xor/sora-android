/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultiaccountFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer,
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val multiaccountFeatureDependencies = DaggerMultiaccountFeatureComponent_MultiaccountFeatureDependenciesComponent.builder()
            .accountFeatureApi(getFeature(AccountFeatureApi::class.java))
            .mainFeatureApi(getFeature(MainFeatureApi::class.java))
            .walletFeatureApi(getFeature(WalletFeatureApi::class.java))
            .networkApi(networkApi())
            .ethereumFeatureApi(getFeature(EthereumFeatureApi::class.java))
            .commonApi(commonApi())
            .build()
        return DaggerMultiaccountFeatureComponent.builder()
            .withDependencies(multiaccountFeatureDependencies)
            .build()
    }
}
