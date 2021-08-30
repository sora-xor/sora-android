package jp.co.soramitsu.feature_ethereum_impl.di

import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        EthereumFeatureDependencies::class
    ],
    modules = [
        EthereumFeatureModule::class
    ]
)
abstract class EthereumFeatureComponent : EthereumFeatureApi {

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class,
            DbApi::class
        ]
    )
    interface EthereumFeatureDependenciesComponent : EthereumFeatureDependencies
}
