package jp.co.soramitsu.feature_ethereum_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.web3j.crypto.Credentials

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PrefsEthereumDatasourceTest {

    companion object {
        private const val PREFS_ETH_PRIVATE = "prefs_eth_private_key"
        private const val PREFS_XOR_ADDRESS = "prefs_xor_address_key"
        private const val PREFS_ETH_REGISTER_STATE = "prefs_eth_register_state"
    }

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var encryptedPreferences: EncryptedPreferences

    @Mock
    private lateinit var preferences: Preferences

    private lateinit var prefsEthereumDatasource: PrefsEthereumDatasource

    private val credentials = EthereumCredentials(Credentials.create("1").ecKeyPair.privateKey)
    private val address = "valAddress"
    private val ethRegisterState = EthRegisterState.State.NONE.toString()

    @Before
    fun setUp() {
        given(preferences.getString(PREFS_ETH_REGISTER_STATE)).willReturn(ethRegisterState)
        prefsEthereumDatasource = PrefsEthereumDatasource(encryptedPreferences, preferences)
    }

    @Test
    fun `save eth private key called`() {
        prefsEthereumDatasource.saveEthereumCredentials(credentials)

        verify(encryptedPreferences).putEncryptedString(
            PREFS_ETH_PRIVATE,
            credentials.privateKey.toString()
        )
    }

    @Test
    fun `retrieve eth private key called`() {
        given(encryptedPreferences.getDecryptedString(PREFS_ETH_PRIVATE)).willReturn("1")

        val actualCredentials = prefsEthereumDatasource.retrieveEthereumCredentials()

        assertEquals(actualCredentials, credentials)
    }

    @Test
    fun `get eth registered state called`() {
        val ethTxHashKey = "prefs_eth_register_transaction_hash"
        val ethRegistrationStateKey = "prefs_eth_register_state"
        val txHash = "txHash"
        val stateStr = EthRegisterState.State.IN_PROGRESS.toString()
        val expected = EthRegisterState(EthRegisterState.State.IN_PROGRESS, txHash)

        given(preferences.getNullableString(ethTxHashKey)).willReturn(txHash)
        given(preferences.getString(ethRegistrationStateKey)).willReturn(stateStr)

        val actual = prefsEthereumDatasource.getEthRegisterState()

        assertEquals(expected, actual)
    }

    @Test
    fun `get eth registered state called with empty state`() {
        val actual = prefsEthereumDatasource.getEthRegisterState()

        assertEquals(EthRegisterState(EthRegisterState.State.NONE, null), actual)
    }

    @Test
    fun `save eth registered state called`() = runBlockingTest {
        val ethTxHashKey = "prefs_eth_register_transaction_hash"
        val ethRegistrationStateKey = "prefs_eth_register_state"
        val txHash = "txHash"
        val state = EthRegisterState(EthRegisterState.State.IN_PROGRESS, txHash)

        val value = prefsEthereumDatasource.observeEthRegisterState().first()
        assertEquals(EthRegisterState.State.NONE, value)

        prefsEthereumDatasource.saveEthRegisterState(state)

        val nvalue = prefsEthereumDatasource.observeEthRegisterState().first()
        assertEquals(state.state, nvalue)

        verify(preferences).putString(ethTxHashKey, txHash)
        verify(preferences).putString(ethRegistrationStateKey, state.state.toString())
    }

    @Test
    fun `save val address called`() {
        val valAddressKey = "prefs_val_address"

        prefsEthereumDatasource.saveVALAddress(address)

        verify(preferences).putString(valAddressKey, address)
    }

    @Test
    fun `retrieve val address called`() {
        val valAddressKey = "prefs_val_address"
        given(preferences.getString(valAddressKey)).willReturn(address)

        val actual = prefsEthereumDatasource.retrieveVALAddress()

        assertEquals(address, actual)
    }
}