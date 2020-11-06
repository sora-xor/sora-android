/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import javax.inject.Inject

class EventFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val eventDependencies = DaggerEventComponent_EventDependenciesComponent.builder()
            .networkApi(networkApi())
            .ethereumFeatureApi(getFeature(EthereumFeatureApi::class.java))
            .walletFeatureApi(getFeature(WalletFeatureApi::class.java))
            .didFeatureApi(didApi())
            .commonApi(commonApi())
            .build()
        return DaggerEventComponent.builder()
            .eventDependencies(eventDependencies)
            .build()
    }
}