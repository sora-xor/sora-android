package jp.co.soramitsu.feature_ethereum_impl.util

import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.feature_ethereum_impl.data.mappers.EthereumConfigMapper
import jp.co.soramitsu.feature_ethereum_impl.data.network.EthereumNetworkApi
import jp.co.soramitsu.feature_ethereum_impl.data.network.model.EthereumConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EthereumConfigProvider @Inject constructor(
    private val ethereumNetworkApi: EthereumNetworkApi,
    private val mapper: EthereumConfigMapper,
    private val healthChecker: HealthChecker
) {

    val config: EthereumConfig by lazy {
        val result = ethereumNetworkApi.getEthConfig().runCatching {
            mapper.mapConfigResponse(blockingGet())
        }.getOrElse {
            healthChecker.ethereumConfigError()
            throw it
        }
        healthChecker.ethrereumConfigOk()
        result
    }
}