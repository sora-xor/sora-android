/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_impl.di

import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_di.holder.FeatureApiHolder
import jp.co.soramitsu.core_di.holder.FeatureContainer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val networkDependencies = DaggerNetworkComponent_NetworkDependenciesComponent.builder()
            .commonApi(getFeature(CommonApi::class.java))
            .build()
        return DaggerNetworkComponent.builder()
            .networkDependencies(networkDependencies)
            .build()
    }
}