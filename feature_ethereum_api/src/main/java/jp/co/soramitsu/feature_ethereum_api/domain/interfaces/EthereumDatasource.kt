package jp.co.soramitsu.feature_ethereum_api.domain.interfaces

import io.reactivex.Observable
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials

interface EthereumDatasource {

    fun saveXORAddress(address: String)

    fun retrieveXORAddress(): String

    fun saveEthereumCredentials(ethereumCredentials: EthereumCredentials)

    fun retrieveEthereumCredentials(): EthereumCredentials?

    fun getEthRegisterState(): EthRegisterState

    fun saveEthRegisterState(state: EthRegisterState)

    fun observeEthRegisterState(): Observable<EthRegisterState.State>
}