/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.web3j.crypto.Credentials

@RunWith(MockitoJUnitRunner::class)
class PrefsEthereumDatasourceTest {

    companion object {
        private const val PREFS_ETH_PRIVATE = "prefs_eth_private_key"
        private const val PREFS_XOR_ADDRESS = "prefs_xor_address_key"
        private const val PREFS_ETH_REGISTER_STATE = "prefs_eth_register_state"
    }

    @Mock private lateinit var encryptedPreferences: EncryptedPreferences
    @Mock private lateinit var preferences: Preferences

    private lateinit var prefsEthereumDatasource: PrefsEthereumDatasource

    private val credentials = EthereumCredentials(Credentials.create("1").ecKeyPair.privateKey)
    private val address = "xorAddress"
    private val ethRegisterState = EthRegisterState.State.NONE.toString()

    @Before fun setUp() {
        given(preferences.getString(PREFS_ETH_REGISTER_STATE)).willReturn(ethRegisterState)
        prefsEthereumDatasource = PrefsEthereumDatasource(encryptedPreferences, preferences)
    }

    @Test fun `save eth private key called`() {
        prefsEthereumDatasource.saveEthereumCredentials(credentials)

        verify(encryptedPreferences).putEncryptedString(PREFS_ETH_PRIVATE, credentials.privateKey.toString())
    }

    @Test fun `retrieve eth private key called`() {
        given(encryptedPreferences.getDecryptedString(PREFS_ETH_PRIVATE)).willReturn("1")

        val actualCredentials = prefsEthereumDatasource.retrieveEthereumCredentials()

        assertEquals(actualCredentials, credentials)
    }
}