package jp.co.soramitsu.feature_ethereum_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumDatasource
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.math.BigInteger
import javax.inject.Inject

class PrefsEthereumDatasource @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val preferences: Preferences
) : EthereumDatasource {

    companion object {
        private const val PREFS_ETH_PRIVATE = "prefs_eth_private_key"
        private const val PREFS_ETH_REGISTER_TRANSACTION_HASH = "prefs_eth_register_transaction_hash"
        private const val PREFS_ETH_REGISTER_STATE = "prefs_eth_register_state"
        private const val PREFS_VAL_ADDRESS = "prefs_val_address"
    }

    private val ethRegisterStateSubject = MutableStateFlow<EthRegisterState.State?>(null)

    init {
        val currentEthRegisterState = getEthRegisterState()
        ethRegisterStateSubject.value = currentEthRegisterState.state
    }

    override fun retrieveVALAddress(): String {
        return preferences.getString(PREFS_VAL_ADDRESS)
    }

    override fun saveVALAddress(address: String) {
        preferences.putString(PREFS_VAL_ADDRESS, address)
    }

    override fun saveEthereumCredentials(ethereumCredentials: EthereumCredentials) {
        encryptedPreferences.putEncryptedString(PREFS_ETH_PRIVATE, ethereumCredentials.privateKey.toString())
    }

    override fun retrieveEthereumCredentials(): EthereumCredentials? {
        val privateString = encryptedPreferences.getDecryptedString(PREFS_ETH_PRIVATE)

        return if (privateString.isEmpty()) {
            null
        } else {
            EthereumCredentials(BigInteger(privateString))
        }
    }

    override fun getEthRegisterState(): EthRegisterState {
        val stateStr = preferences.getString(PREFS_ETH_REGISTER_STATE)
        val transactionHash = preferences.getNullableString(PREFS_ETH_REGISTER_TRANSACTION_HASH)
        return if (stateStr.isEmpty()) {
            EthRegisterState(EthRegisterState.State.NONE, transactionHash)
        } else {
            val state = EthRegisterState.State.valueOf(stateStr)
            EthRegisterState(state, transactionHash)
        }
    }

    override fun saveEthRegisterState(state: EthRegisterState) {
        ethRegisterStateSubject.value = state.state
        state.transactionHash?.let { preferences.putString(PREFS_ETH_REGISTER_TRANSACTION_HASH, it) }
        preferences.putString(PREFS_ETH_REGISTER_STATE, state.state.toString())
    }

    override fun observeEthRegisterState(): Flow<EthRegisterState.State> {
        return ethRegisterStateSubject.asStateFlow().filterNotNull()
    }
}
