/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_impl.di

import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.feature_information_api.di.InformationFeatureApi
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        InformationFeatureDependencies::class
    ],
    modules = [
        InformationFeatureModule::class
    ]
)
abstract class InformationFeatureComponent : InformationFeatureApi {

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class
        ]
    )
    internal interface InformationFeatureDependenciesComponent : InformationFeatureDependencies
}