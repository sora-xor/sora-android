/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app_feature

import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_di.holder.FeatureApiHolder
import jp.co.soramitsu.core_di.holder.FeatureContainer
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_did_api.di.DidFeatureApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val appFeatureDependencies = DaggerAppFeatureComponent_AppFeatureDependenciesComponent.builder()
            .accountFeatureApi(getFeature(AccountFeatureApi::class.java))
            .didFeatureApi(getFeature(DidFeatureApi::class.java))
            .commonApi(getFeature(CommonApi::class.java))
            .build()

        return DaggerAppFeatureComponent.builder()
            .appFeatureDependencies(appFeatureDependencies)
            .build()
    }
}