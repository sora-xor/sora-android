/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_impl.di

import dagger.Component
import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_network_api.di.NetworkApi
import javax.inject.Singleton

@Component(
    modules = [
        NetworkModule::class
    ],
    dependencies = [
        NetworkDependencies::class
    ]
)
@Singleton
abstract class NetworkComponent : NetworkApi {

    @Component(
        dependencies = [
            CommonApi::class
        ]
    )
    interface NetworkDependenciesComponent : NetworkDependencies
}