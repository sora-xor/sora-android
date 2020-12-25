package jp.co.soramitsu.feature_ethereum_impl.data.mappers

import jp.co.soramitsu.feature_ethereum_impl.data.network.model.EthereumConfig
import jp.co.soramitsu.feature_ethereum_impl.data.network.response.EthRemoteConfigResponse

class EthereumConfigMapper {

    fun mapConfigResponse(response: EthRemoteConfigResponse) =
        EthereumConfig(
            scanUrl = response.scanUrl + "tx/",
            userName = response.username,
            password = response.password,
            url = response.url,
            masterContract = response.master)
}