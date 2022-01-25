/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var soraPreferences: SoraPreferences

    private lateinit var prefsEthereumDatasource: PrefsEthereumDatasource

    private val credentials = EthereumCredentials(Credentials.create("1").ecKeyPair.privateKey)
    private val address = "valAddress"
    private val ethRegisterState = EthRegisterState.State.NONE.toString()

    @Before
    fun setUp() = runBlockingTest {
        //given(soraPreferences.getString(PREFS_ETH_REGISTER_STATE)).willReturn(ethRegisterState)
        prefsEthereumDatasource = PrefsEthereumDatasource(encryptedPreferences, soraPreferences)
    }

    @Test
    fun `save eth private key called`() = runBlockingTest {
        prefsEthereumDatasource.saveEthereumCredentials(credentials)

        verify(encryptedPreferences).putEncryptedString(
            PREFS_ETH_PRIVATE,
            credentials.privateKey.toString()
        )
    }

    @Test
    fun `retrieve eth private key called`() = runBlockingTest {
        given(encryptedPreferences.getDecryptedString(PREFS_ETH_PRIVATE)).willReturn("1")

        val actualCredentials = prefsEthereumDatasource.retrieveEthereumCredentials()

        assertEquals(actualCredentials, credentials)
    }

    @Test
    fun `save val address called`() = runBlockingTest {
        val valAddressKey = "prefs_val_address"

        prefsEthereumDatasource.saveVALAddress(address)

        verify(soraPreferences).putString(valAddressKey, address)
    }
}