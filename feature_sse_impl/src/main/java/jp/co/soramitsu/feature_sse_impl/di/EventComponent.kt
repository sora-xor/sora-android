package jp.co.soramitsu.feature_sse_impl.di

import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.common.di.api.DidFeatureApi
import jp.co.soramitsu.feature_ethereum_api.di.EthereumFeatureApi
import jp.co.soramitsu.feature_sse_api.di.EventFeatureApi
import jp.co.soramitsu.feature_sse_impl.presentation.EventService
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [
        EventDependencies::class
    ],
    modules = [
        EventModule::class
    ]
)
interface EventComponent : EventFeatureApi {

    fun inject(eventService: EventService)

    @Component(
        dependencies = [
            NetworkApi::class,
            EthereumFeatureApi::class,
            WalletFeatureApi::class,
            DidFeatureApi::class,
            CommonApi::class
        ]
    )
    interface EventDependenciesComponent : EventDependencies
}