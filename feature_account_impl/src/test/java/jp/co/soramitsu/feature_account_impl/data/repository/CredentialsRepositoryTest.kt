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

package jp.co.soramitsu.feature_account_impl.data.repository

import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.SeedFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class CredentialsRepositoryTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var credentialsRepository: CredentialsRepositoryImpl

    @Mock
    lateinit var datasource: CredentialsDatasource

    @Mock
    lateinit var cryptoAssistant: CryptoAssistant

    @Mock
    lateinit var runtimeManager: RuntimeManager

    @Mock
    lateinit var soraConfigManager: SoraConfigManager

    @Mock
    lateinit var jsonAccountsEncoder: JsonAccountsEncoder

    @Mock
    lateinit var keypair: Sr25519Keypair

    private val fooaddress = "fooaddress"

    private val mn = Mnemonic("words", List(12) { _ -> "word" }, ByteArray(16) { i -> i.toByte() })

    @Before
    fun setup() {
        mockkObject(MnemonicCreator)
        mockkObject(SubstrateKeypairFactory)
        mockkObject(FirebaseWrapper)
        mockkObject(SubstrateSeedFactory)
        every {
            SubstrateSeedFactory.deriveSeed(
                any(),
                any()
            )
        } returns SeedFactory.Result(
            ByteArray(64) { i -> i.toByte() },
            mn,
        )
        every { SubstrateKeypairFactory.generate(any(), any()) } returns keypair
        every { SubstrateKeypairFactory.generate(any(), any(), any()) } returns keypair
        every { FirebaseWrapper.log("Keys were created") } just runs

        credentialsRepository = CredentialsRepositoryImpl(
            datasource,
            cryptoAssistant,
            runtimeManager,
            jsonAccountsEncoder,
            soraConfigManager,
        )
    }

    @Test
    fun `derive seed check`() = runTest {
        whenever(datasource.retrieveSeed("address")).thenReturn("")
        whenever(datasource.retrieveMnemonic("address")).thenReturn("mnemonic mnemonic mnemonic mnemonic mnemonic mnemonic")
        every { MnemonicCreator.fromWords(any()) } returns mn
        val seed = credentialsRepository.retrieveSeed(SoraAccount("address", "name"))
        assertEquals(64, seed.length)
    }

    @Test
    fun `is mnemonic valid returns true`() = runTest {
        val mnemonic = "mnemonic"
        every { MnemonicCreator.fromWords(any()) } returns Mnemonic(
            "",
            emptyList(),
            ByteArray(1) { 1 }
        )
        assertTrue(credentialsRepository.isMnemonicValid(mnemonic))
    }

    @Test
    fun `is mnemonic valid returns false`() = runTest {
        val mnemonic = "mnemonic2"
        every { MnemonicCreator.fromWords(any()) } throws IllegalArgumentException()
        assertFalse(credentialsRepository.isMnemonicValid(mnemonic))
    }

    @Test
    fun `is raw seed valid returns true`() = runTest {
        val rawSeed = "0xcf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf"
        assertTrue(credentialsRepository.isRawSeedValid(rawSeed))
    }

    @Test
    fun `is raw seed valid returns false`() = runTest {
        val rawSeed = "0xzf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf"
        assertFalse(credentialsRepository.isRawSeedValid(rawSeed))
    }

    @Test
    fun `generate user credentials called`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val entropy = mnemonic.toByteArray()
        val publicKey = "publicKey".toByteArray()
        whenever(keypair.publicKey).thenReturn(publicKey)
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), entropy)
        every { MnemonicCreator.randomMnemonic(any()) } returns mnmnc
        whenever(runtimeManager.toSoraAddress(any())).thenReturn(fooaddress)
        whenever(datasource.retrieveKeys(any())).thenReturn(null)

        credentialsRepository.generateUserCredentials("")

        verify(datasource).saveKeys(keypair, fooaddress)
        verify(datasource).saveMnemonic(mnemonic, fooaddress)
    }

    @Test
    fun `restore user credentials from mnemonic called`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val entropy = mnemonic.toByteArray()
        val publicKeyBytes = "publicKeyBytespublicKeyBytespublicKeyBytes".toByteArray()
        whenever(keypair.publicKey).thenReturn(publicKeyBytes)
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), entropy)
        every { MnemonicCreator.fromWords(mnemonic) } returns mnmnc
        whenever(runtimeManager.toSoraAddress(any())).thenReturn(fooaddress)
        whenever(datasource.retrieveKeys(any())).thenReturn(null)

        credentialsRepository.restoreUserCredentialsFromMnemonic(mnemonic, "")
        verify(datasource).saveMnemonic(mnemonic, fooaddress)
    }

    @Test
    fun `restore user credentials from seed called`() = runTest {
        val rawSeed = "0xcf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf"
        val publicKeyBytes = "publicKeyBytespublicKeyBytespublicKeyBytes".toByteArray()
        whenever(keypair.publicKey).thenReturn(publicKeyBytes)
        whenever(runtimeManager.toSoraAddress(any())).thenReturn(fooaddress)
        whenever(datasource.retrieveKeys(any())).thenReturn(null)

        credentialsRepository.restoreUserCredentialsFromRawSeed(rawSeed, "")
        verify(datasource).saveKeys(keypair, fooaddress)
    }

    @Test
    fun `save mnemonic called`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        credentialsRepository.saveMnemonic(mnemonic, SoraAccount("", ""))
        verify(datasource).saveMnemonic(mnemonic, "")
    }

    @Test
    fun `retrieve mnemonic called`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        whenever(datasource.retrieveMnemonic("")).thenReturn(mnemonic)
        assertEquals(credentialsRepository.retrieveMnemonic(SoraAccount("", "")), mnemonic)
    }

    @Test
    fun `retrieve keypair called`() = runTest {
        whenever(datasource.retrieveKeys("")).thenReturn(keypair)
        assertEquals(keypair, credentialsRepository.retrieveKeyPair(SoraAccount("", "")))
    }
}
