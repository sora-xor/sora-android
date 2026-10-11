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
import io.mockk.unmockkObject
import io.mockk.runs
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.core_db.WalletRecoveryKind
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialField
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.StoredCredential
import jp.co.soramitsu.feature_account_impl.domain.keys.FakeStoredKeyCrypto
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.SeedFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
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
    lateinit var sora2AddressCodec: Sora2AddressCodec

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

        // The stored-secret rules themselves are covered with fakes instead of mocks in
        // CredentialsRepositoryStoredSecretsTest.
        credentialsRepository = CredentialsRepositoryImpl(
            datasource,
            cryptoAssistant,
            sora2AddressCodec,
            jsonAccountsEncoder,
            FakeStoredKeyCrypto(),
        )
    }

    @After
    fun releaseStaticMocks() {
        // Static mocks would otherwise leak into every test class run later in the same JVM.
        unmockkObject(MnemonicCreator, SubstrateKeypairFactory, FirebaseWrapper, SubstrateSeedFactory)
    }

    @Test
    fun `unreadable own phrase shows the release-era phrase of the same wallet`() = runTest {
        // 2.4.0 copied the unsuffixed keys and phrase to address-scoped values; the scoped copy
        // no longer decrypts, the release-era copy does and its keys derive this address.
        val releaseEraKeys = Sr25519Keypair(ByteArray(64) { 1 }, ByteArray(32) { 2 }, ByteArray(32) { 3 })
        whenever(datasource.retrieveMnemonic("address"))
            .thenThrow(WalletDecryptionException("WALLET_DECRYPTION_FAILED"))
        whenever(datasource.retrieveKeys("")).thenReturn(releaseEraKeys)
        whenever(sora2AddressCodec.toSoraAddressOrNull(releaseEraKeys.publicKey))
            .thenReturn("address")
        whenever(datasource.retrieveMnemonic("")).thenReturn("release era words")

        assertEquals(
            "release era words",
            credentialsRepository.retrieveMnemonic(SoraAccount("address", "name")),
        )
        verify(datasource, never()).saveMnemonic(any(), any())
    }

    @Test
    fun `unreadable own phrase is reported when no release-era copy belongs to the wallet`() =
        runTest {
            val otherKeys = Sr25519Keypair(ByteArray(64) { 4 }, ByteArray(32) { 5 }, ByteArray(32) { 6 })
            whenever(datasource.retrieveMnemonic("address"))
                .thenThrow(WalletDecryptionException("WALLET_DECRYPTION_FAILED"))
            whenever(datasource.retrieveKeys("")).thenReturn(otherKeys)
            whenever(sora2AddressCodec.toSoraAddressOrNull(otherKeys.publicKey))
                .thenReturn("another-address")

            val error = runCatching {
                credentialsRepository.retrieveMnemonic(SoraAccount("address", "name"))
            }.exceptionOrNull()

            assertTrue(error is WalletDecryptionException)
            verify(datasource, never()).retrieveMnemonic("")
        }

    @Test
    fun `is mnemonic valid returns false`() = runTest {
        val mnemonic = "mnemonic2"
        every { MnemonicCreator.fromWords(any()) } throws IllegalArgumentException()
        assertFalse(credentialsRepository.isMnemonicValid(mnemonic))
    }

    @Test
    fun `missing mnemonic never synthesizes or persists a seed`() = runTest {
        val account = SoraAccount("watch-address", "Watch")
        whenever(datasource.retrieveSeed(account.substrateAddress)).thenReturn("")
        whenever(datasource.retrieveMnemonic(account.substrateAddress)).thenReturn("")
        whenever(datasource.retrieveKeys("")).thenReturn(null)
        whenever(datasource.isExplicitWatchOnly(account.substrateAddress)).thenReturn(false)

        val error = runCatching {
            credentialsRepository.retrieveSeed(account)
        }.exceptionOrNull()

        assertTrue(
            error?.message?.contains(
                "MNEMONIC_NOT_AVAILABLE_FOR_SEED_DERIVATION"
            ) == true
        )
        verify(datasource, never()).saveSeed(any(), any())
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
    fun `a phone that cannot unlock its stored keys refuses the commit and records recovery`() =
        runTest {
            val mnemonic =
                "airport wish wish loan width country acoustic country ceiling good enact penalty"
            val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), mnemonic.toByteArray())
            every { MnemonicCreator.fromWords(mnemonic) } returns mnmnc
            stubDraftKeypair()
            whenever(datasource.readStoredCredential(any(), eq(fooaddress)))
                .thenThrow(KeyMaterialUnavailableException("alias missing", transient = false))

            val draft = credentialsRepository.draftFromMnemonic(mnemonic, "Restored")
            val error = runCatching { credentialsRepository.commitDraft(draft) }.exceptionOrNull()

            assertEquals(
                ResponseCode.WALLET_KEYS_UNAVAILABLE,
                (error as? SoraException)?.errorResponseCode,
            )
            assertEquals(
                WalletRecoveryKind.KEYS_UNAVAILABLE,
                WalletUpgradeBackup.recoveryOptions()?.kind,
            )
            assertEquals(true, WalletUpgradeBackup.recoveryOptions()?.canSetUpAgain)
            verify(datasource, never()).writeCredentialsIfAbsent(any(), any())
            WalletUpgradeBackup.noteMigrationVerified()
        }

    @Test
    fun `no phrase is created or derived while wallet changes are disabled`() = runTest {
        WalletRecoveryCapabilityGate.enterInspectedRecovery()
        try {
            listOf<suspend () -> Unit>(
                { credentialsRepository.createDraft("New") },
                { credentialsRepository.draftFromMnemonic("words", "Imported") },
                { credentialsRepository.draftFromRawSeed("00", "Seed") },
            ).forEach { start ->
                val error = runCatching { start() }.exceptionOrNull()
                assertEquals("WALLET_RECOVERY_READ_ONLY", error?.message)
            }
        } finally {
            WalletRecoveryCapabilityGate.enterNormal()
        }
    }

    private val privateKey = ByteArray(64) { 3 }
    private val publicKey = ByteArray(32) { 4 }
    private val nonce = ByteArray(32) { 5 }

    private suspend fun stubDraftKeypair() {
        whenever(datasource.isExplicitWatchOnly(fooaddress)).thenReturn(false)
        // The draft's key pair comes from the stand-in crypto; whatever it derives is "fooaddress".
        whenever(sora2AddressCodec.toSoraAddressOrNull(any())).thenReturn(fooaddress)
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
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

    @Test
    fun `explicit watch only marker is account scoped`() = runTest {
        val account = SoraAccount("watch-address", "Watch")
        whenever(datasource.isExplicitWatchOnly(account.substrateAddress)).thenReturn(true)

        assertTrue(credentialsRepository.isExplicitWatchOnly(account))

        credentialsRepository.setExplicitWatchOnly(account, false)
        verify(datasource).setExplicitWatchOnly(account.substrateAddress, false)
    }

    @Test
    fun `legacy empty suffix is read only when its public key matches requested account`() =
        runTest {
            val account = SoraAccount("legacy-address", "Legacy")
            val mnemonic =
                "airport wish wish loan width country acoustic country ceiling good enact penalty"
            val publicKey = ByteArray(32) { it.toByte() }
            whenever(datasource.retrieveMnemonic(account.substrateAddress)).thenReturn("")
            whenever(datasource.retrieveKeys("")).thenReturn(keypair)
            whenever(datasource.retrieveMnemonic("")).thenReturn(mnemonic)
            whenever(keypair.publicKey).thenReturn(publicKey)
            whenever(keypair.privateKey).thenReturn(ByteArray(64))
            whenever(keypair.nonce).thenReturn(ByteArray(32))
            whenever(sora2AddressCodec.toSoraAddressOrNull(publicKey))
                .thenReturn(account.substrateAddress)

            assertEquals(mnemonic, credentialsRepository.retrieveMnemonic(account))
            verify(datasource).retrieveMnemonic("")
        }

    @Test
    fun `legacy empty suffix is rejected when its public key belongs to another account`() =
        runTest {
            val account = SoraAccount("requested-address", "Requested")
            val publicKey = ByteArray(32) { it.toByte() }
            val privateKey = ByteArray(64) { 1 }
            val nonce = ByteArray(32) { 2 }
            whenever(datasource.retrieveKeys(account.substrateAddress)).thenReturn(null)
            whenever(datasource.retrieveKeys("")).thenReturn(keypair)
            whenever(keypair.publicKey).thenReturn(publicKey)
            whenever(keypair.privateKey).thenReturn(privateKey)
            whenever(keypair.nonce).thenReturn(nonce)
            whenever(sora2AddressCodec.toSoraAddressOrNull(publicKey))
                .thenReturn("different-address")

            val result = runCatching { credentialsRepository.retrieveKeyPair(account) }

            assertTrue(result.isFailure)
            assertTrue(privateKey.all { it == 0.toByte() })
            assertTrue(nonce.all { it == 0.toByte() })
        }
}
