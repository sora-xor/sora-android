/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_api.domain.interfaces

import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials

interface EthereumDatasource {

    suspend fun saveVALAddress(address: String)

    suspend fun saveEthereumCredentials(ethereumCredentials: EthereumCredentials)

    suspend fun retrieveEthereumCredentials(): EthereumCredentials?

    suspend fun getEthRegisterState(): EthRegisterState

    suspend fun saveEthRegisterState(state: EthRegisterState)
}
