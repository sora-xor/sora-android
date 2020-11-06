/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InformationFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val informationFeatureDependencies = DaggerInformationFeatureComponent_InformationFeatureDependenciesComponent.builder()
            .networkApi(networkApi())
            .commonApi(commonApi())
            .build()
        return DaggerInformationFeatureComponent.builder()
            .informationFeatureDependencies(informationFeatureDependencies)
            .build()
    }
}