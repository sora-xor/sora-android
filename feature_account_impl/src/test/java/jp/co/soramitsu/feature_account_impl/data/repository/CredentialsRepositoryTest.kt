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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.SeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.security.KeyPair
import java.security.PublicKey

@ExperimentalCoroutinesApi
class CredentialsRepositoryTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    private lateinit var credentialsRepository: CredentialsRepositoryImpl

    @MockK
    lateinit var datasource: CredentialsDatasource

    @MockK
    lateinit var cryptoAssistant: CryptoAssistant

    @MockK
    lateinit var runtimeManager: RuntimeManager

    @MockK
    lateinit var soraConfigManager: SoraConfigManager

    @MockK
    lateinit var jsonAccountsEncoder: JsonAccountsEncoder

    @Before
    fun setup() {
        credentialsRepository =
            CredentialsRepositoryImpl(
                datasource,
                cryptoAssistant,
                runtimeManager,
                jsonAccountsEncoder,
                soraConfigManager,
            )
    }

    @Test
    fun `is mnemonic valid returns true`() = runTest {
        val mnemonic = "mnemonic"
        mockkObject(MnemonicCreator)
        every { MnemonicCreator.fromWords(any()) } returns Mnemonic(
            "",
            emptyList(),
            ByteArray(1) { 1 })
        assertTrue(credentialsRepository.isMnemonicValid(mnemonic))
    }

    @Test
    fun `is mnemonic valid returns false`() = runTest {
        val mnemonic = "mnemonic2"
        mockkObject(MnemonicCreator)
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
        val seed = "seed".toByteArray()
        val publicKey = "publicKey".toByteArray()
        val keypair = mockk<Sr25519Keypair>()
        every { keypair.publicKey } returns publicKey
        val derivationResult = mockk<SeedFactory.Result>()
        every { derivationResult.seed } returns seed
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), entropy)
        mockkObject(MnemonicCreator)
        every { MnemonicCreator.randomMnemonic(any()) } returns mnmnc
        mockkObject(SubstrateKeypairFactory)
        every { SubstrateKeypairFactory.generate(any(), any()) } returns keypair
        mockkStatic(SubstrateSeedFactory::deriveSeed32)
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log("Keys were created") } returns Unit
        every { SubstrateSeedFactory.deriveSeed32(any(), any()) } returns derivationResult
        every { runtimeManager.toSoraAddress(any()) } returns "fooaddress"
        coEvery { datasource.saveKeys(any(), any()) } returns Unit
        coEvery { datasource.retrieveKeys(any()) } returns null
        coEvery { datasource.saveMnemonic(any(), any()) } returns Unit

        credentialsRepository.generateUserCredentials("")

        coVerify { datasource.saveKeys(keypair, "fooaddress") }
        coVerify { datasource.saveMnemonic(mnemonic, "fooaddress") }
    }

    @Test
    fun `restore user credentials from mnemonic called`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val entropy = mnemonic.toByteArray()
        val seed = "seed".toByteArray()
        val publicKeyBytes = "publicKeyBytespublicKeyBytespublicKeyBytes".toByteArray()
        val irohaKeypair = mockk<KeyPair>()
        val irohaAddress = "did_sora_7075626c69634b657942@sora"
        val message = irohaAddress + publicKeyBytes.toHexString()
        val signature = irohaAddress.toByteArray()
        val keypair = mockk<Sr25519Keypair>()
        every { keypair.publicKey } returns publicKeyBytes
        every { runtimeManager.toSoraAddress(any()) } returns "fooaddress"
        val publicKey = mockk<PublicKey>()
        val derivationResult = mockk<SeedFactory.Result>()
        every { derivationResult.seed } returns seed
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), entropy)
        mockkObject(MnemonicCreator)
        every { MnemonicCreator.fromWords(mnemonic) } returns mnmnc
        mockkObject(SubstrateKeypairFactory)
        every { SubstrateKeypairFactory.generate(any(), any()) } returns keypair
        mockkStatic(SubstrateSeedFactory::deriveSeed32)
        every { SubstrateSeedFactory.deriveSeed32(any(), any()) } returns derivationResult
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log("Keys were created") } returns Unit
        every { runtimeManager.toSoraAddress(any()) } returns "fooaddress"
        coEvery { datasource.saveKeys(any(), any()) } returns Unit
        coEvery { datasource.saveMnemonic(any(), any()) } returns Unit
        coEvery { datasource.retrieveKeys(any()) } returns null

        credentialsRepository.restoreUserCredentialsFromMnemonic(mnemonic, "")

        coVerify { datasource.saveMnemonic(mnemonic, "fooaddress") }
    }

    @Test
    fun `restore user credentials from seed called`() = runTest {
        val rawSeed = "0xcf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf"
        val rawSeedBytes = rawSeed.fromHex()
        val publicKeyBytes = "publicKeyBytespublicKeyBytespublicKeyBytes".toByteArray()
        every { runtimeManager.toSoraAddress(any()) } returns "fooaddress"

        val keypair = mockk<Sr25519Keypair>()
        every { keypair.publicKey } returns publicKeyBytes
        mockkObject(SubstrateKeypairFactory)
        every {
            SubstrateKeypairFactory.generate(
                SubstrateOptionsProvider.encryptionType,
                rawSeedBytes,
                emptyList()
            )
        } returns keypair
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log("Keys were created") } returns Unit
        every { runtimeManager.toSoraAddress(any()) } returns "fooaddress"
        coEvery { datasource.saveKeys(any(), any()) } returns Unit
        coEvery { datasource.saveMnemonic(any(), any()) } returns Unit
        coEvery { datasource.saveSeed(any(), any()) } returns Unit
        coEvery { datasource.retrieveKeys(any()) } returns null

        credentialsRepository.restoreUserCredentialsFromRawSeed(rawSeed, "")

        coVerify { datasource.saveKeys(keypair, "fooaddress") }
    }

    @Test
    fun `save mnemonic called`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        coEvery { datasource.saveMnemonic(any(), any()) } returns Unit
        credentialsRepository.saveMnemonic(mnemonic, SoraAccount("", ""))
        coVerify { datasource.saveMnemonic(mnemonic, "") }
    }

    @Test
    fun `retrieve mnemonic called`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        coEvery { datasource.retrieveMnemonic("") } returns mnemonic

        assertEquals(credentialsRepository.retrieveMnemonic(SoraAccount("", "")), mnemonic)
    }

    @Test
    fun `retrieve keypair called`() = runTest {
        val keypair = mockk<Sr25519Keypair>()
        coEvery { datasource.retrieveKeys("") } returns keypair

        assertEquals(keypair, credentialsRepository.retrieveKeyPair(SoraAccount("", "")))
    }
}
