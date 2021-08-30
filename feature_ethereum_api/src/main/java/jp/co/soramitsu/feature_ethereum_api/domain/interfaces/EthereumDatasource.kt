package jp.co.soramitsu.feature_ethereum_api.domain.interfaces

import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import kotlinx.coroutines.flow.Flow

interface EthereumDatasource {

    fun saveVALAddress(address: String)

    fun retrieveVALAddress(): String

    fun saveEthereumCredentials(ethereumCredentials: EthereumCredentials)

    fun retrieveEthereumCredentials(): EthereumCredentials?

    fun getEthRegisterState(): EthRegisterState

    fun saveEthRegisterState(state: EthRegisterState)

    fun observeEthRegisterState(): Flow<EthRegisterState.State>
}
