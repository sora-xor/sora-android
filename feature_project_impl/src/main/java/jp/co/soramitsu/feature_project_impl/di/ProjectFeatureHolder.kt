/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.di

import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.core_di.holder.FeatureApiHolder
import jp.co.soramitsu.core_di.holder.FeatureContainer
import jp.co.soramitsu.core_network_api.di.NetworkApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val projectFeatureDependencies = DaggerProjectFeatureComponent_ProjectFeatureDependenciesComponent.builder()
            .commonApi(getFeature(CommonApi::class.java))
            .networkApi(getFeature(NetworkApi::class.java))
            .dbApi(getFeature(DbApi::class.java))
            .build()
        return DaggerProjectFeatureComponent.builder()
            .projectFeatureDependencies(projectFeatureDependencies)
            .build()
    }
}