/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.di

import jp.co.soramitsu.common.di.api.FeatureApiHolder
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.core_db.di.DbApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val accountFeatureDependencies = DaggerAccountFeatureComponent_AccountFeatureDependenciesComponent.builder()
            .networkApi(networkApi())
            .dbApi(getFeature(DbApi::class.java))
            .commonApi(commonApi())
            .networkApi(networkApi())
            .build()
        return DaggerAccountFeatureComponent.builder()
            .accountFeatureDependencies(accountFeatureDependencies)
            .build()
    }
}
