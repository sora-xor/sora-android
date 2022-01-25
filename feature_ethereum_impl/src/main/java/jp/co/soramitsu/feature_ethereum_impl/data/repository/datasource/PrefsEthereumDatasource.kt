/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumDatasource
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import java.math.BigInteger
import javax.inject.Inject

class PrefsEthereumDatasource @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val soraPreferences: SoraPreferences
) : EthereumDatasource {

    companion object {
        private const val PREFS_ETH_PRIVATE = "prefs_eth_private_key"
        private const val PREFS_ETH_REGISTER_TRANSACTION_HASH = "prefs_eth_register_transaction_hash"
        private const val PREFS_ETH_REGISTER_STATE = "prefs_eth_register_state"
        private const val PREFS_VAL_ADDRESS = "prefs_val_address"
    }

    override suspend fun saveVALAddress(address: String) {
        soraPreferences.putString(PREFS_VAL_ADDRESS, address)
    }

    override suspend fun saveEthereumCredentials(ethereumCredentials: EthereumCredentials) {
        encryptedPreferences.putEncryptedString(PREFS_ETH_PRIVATE, ethereumCredentials.privateKey.toString())
    }

    override suspend fun retrieveEthereumCredentials(): EthereumCredentials? {
        val privateString = encryptedPreferences.getDecryptedString(PREFS_ETH_PRIVATE)

        return if (privateString.isEmpty()) {
            null
        } else {
            EthereumCredentials(BigInteger(privateString))
        }
    }

    override suspend fun getEthRegisterState(): EthRegisterState {
        val stateStr = soraPreferences.getString(PREFS_ETH_REGISTER_STATE)
        val transactionHash = "soraPreferences.getNullableString(PREFS_ETH_REGISTER_TRANSACTION_HASH)"
        return if (stateStr.isEmpty()) {
            EthRegisterState(EthRegisterState.State.NONE, transactionHash)
        } else {
            val state = EthRegisterState.State.valueOf(stateStr)
            EthRegisterState(state, transactionHash)
        }
    }

    override suspend fun saveEthRegisterState(state: EthRegisterState) {
        state.transactionHash?.let { soraPreferences.putString(PREFS_ETH_REGISTER_TRANSACTION_HASH, it) }
        soraPreferences.putString(PREFS_ETH_REGISTER_STATE, state.state.toString())
    }
}
