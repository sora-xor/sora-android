package jp.co.soramitsu.feature_ethereum_impl.di

import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.common.di.api.DidFeatureApi
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_ethereum_impl.presentation.di.EthereumServiceComponent
import jp.co.soramitsu.feature_ethereum_impl.presentation.polling.di.EthereumStatusPollingServiceComponent
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

    abstract fun ethereumServiceComponentBuilder(): EthereumServiceComponent.Builder

    abstract fun ethereumStatusPollingServiceComponentBuilder(): EthereumStatusPollingServiceComponent.Builder

    @Component(
        dependencies = [
            CommonApi::class,
            NetworkApi::class,
            DidFeatureApi::class,
            DbApi::class
        ]
    )
    interface EthereumFeatureDependenciesComponent : EthereumFeatureDependencies
}
