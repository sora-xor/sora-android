/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import dagger.Component
import jp.co.soramitsu.common.di.app.CommonApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.core_network_api.di.NetworkApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        WalletFeatureDependencies::class
    ],
    modules = [
        WalletFeatureModule::class
    ]
)
interface WalletFeatureComponent : WalletFeatureApi {

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class,
            DbApi::class
        ]
    )
    interface WalletFeatureDependenciesComponent : WalletFeatureDependencies
}