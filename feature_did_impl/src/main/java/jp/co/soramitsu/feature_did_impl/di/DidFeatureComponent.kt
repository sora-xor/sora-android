/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_did_impl.di

import dagger.Component
import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_network_api.di.NetworkApi
import jp.co.soramitsu.feature_did_api.di.DidFeatureApi
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        DidFeatureDependencies::class
    ],
    modules = [
        DidFeatureModule::class
    ]
)
abstract class DidFeatureComponent : DidFeatureApi {

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class
        ]
    )
    interface DidFeatureDependenciesComponent : DidFeatureDependencies
}
