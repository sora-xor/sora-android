/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_ethereum_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthereumCredentials
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
    fun setUp() = runTest {
        //given(soraPreferences.getString(PREFS_ETH_REGISTER_STATE)).willReturn(ethRegisterState)
        prefsEthereumDatasource = PrefsEthereumDatasource(encryptedPreferences, soraPreferences)
    }

    @Test
    fun `save eth private key called`() = runTest {
        prefsEthereumDatasource.saveEthereumCredentials(credentials)

        verify(encryptedPreferences).putEncryptedString(
            PREFS_ETH_PRIVATE,
            credentials.privateKey.toString()
        )
    }

    @Test
    fun `retrieve eth private key called`() = runTest {
        given(encryptedPreferences.getDecryptedString(PREFS_ETH_PRIVATE)).willReturn("1")

        val actualCredentials = prefsEthereumDatasource.retrieveEthereumCredentials()

        assertEquals(actualCredentials, credentials)
    }

    @Test
    fun `save val address called`() = runTest {
        val valAddressKey = "prefs_val_address"

        prefsEthereumDatasource.saveVALAddress(address)

        verify(soraPreferences).putString(valAddressKey, address)
    }
}
