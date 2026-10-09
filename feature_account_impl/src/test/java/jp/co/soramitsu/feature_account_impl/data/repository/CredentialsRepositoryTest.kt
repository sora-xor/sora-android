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
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.json_decoder.JsonAccountsEncoder
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.core_db.WalletRecoveryKind
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialField
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.StoredCredential
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.xcrypto.seed.Mnemonic
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.SeedFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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

        credentialsRepository = CredentialsRepositoryImpl(
            datasource,
            cryptoAssistant,
            sora2AddressCodec,
            jsonAccountsEncoder,
        )
    }

    @Test
    fun `derive seed check`() = runTest {
        whenever(datasource.retrieveSeed("address")).thenReturn("")
        whenever(datasource.retrieveMnemonic("address")).thenReturn(
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        )
        every { MnemonicCreator.fromWords(any()) } returns mn
        val seed = credentialsRepository.retrieveSeed(SoraAccount("address", "name"))
        assertEquals(64, seed.length)
    }

    @Test
    fun `is mnemonic valid returns true`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
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
    fun `fifteen word phrase is accepted for SORA2 import and keeps its seed`() =
        runTest {
            val phrase = List(15) { index -> "legacy$index" }.joinToString(" ")
            val legacyMnemonic = Mnemonic(
                phrase,
                List(15) { index -> "legacy$index" },
                ByteArray(20) { index -> index.toByte() },
            )
            every { MnemonicCreator.fromWords(phrase) } returns legacyMnemonic
            whenever(datasource.retrieveSeed("legacy-address")).thenReturn("")
            whenever(datasource.retrieveMnemonic("legacy-address")).thenReturn(phrase)
            whenever(datasource.retrieveKeys("")).thenReturn(null)

            // 3.8.x accepted every BIP39 length for SORA2; SORA v1 phrases have 15 words.
            assertTrue(credentialsRepository.isMnemonicValid(phrase))
            assertEquals(64, credentialsRepository.convertPassphraseToSeed(phrase).length)
            assertEquals(
                64,
                credentialsRepository
                    .convertRetainedSoraPassphraseToSeed(phrase)
                    .length,
            )
            assertEquals(
                64,
                credentialsRepository.retrieveSeed(
                    SoraAccount("legacy-address", "Legacy")
                ).length,
            )
            verify(datasource, never()).saveSeed(any(), any())
        }

    @Test
    fun `retained eighteen and twenty one word wallets preserve seed export without writes`() =
        runTest {
          listOf(18, 21).forEach { wordCount ->
            val phrase = List(wordCount) { index -> "legacy$index" }.joinToString(" ")
            val legacyMnemonic = Mnemonic(
                phrase,
                List(wordCount) { index -> "legacy$index" },
                ByteArray(wordCount / 3 * 4) { index -> index.toByte() },
            )
            every { MnemonicCreator.fromWords(phrase) } returns legacyMnemonic
            whenever(datasource.retrieveSeed("legacy-address")).thenReturn("")
            whenever(datasource.retrieveMnemonic("legacy-address")).thenReturn(phrase)
            whenever(datasource.retrieveKeys("")).thenReturn(null)

            assertTrue(credentialsRepository.isMnemonicValid(phrase))
            assertEquals(64, credentialsRepository.convertPassphraseToSeed(phrase).length)
            assertEquals(
                64,
                credentialsRepository
                    .convertRetainedSoraPassphraseToSeed(phrase)
                    .length,
            )
            assertEquals(
                64,
                credentialsRepository.retrieveSeed(
                    SoraAccount("legacy-address", "Legacy")
                ).length,
            )
            verify(datasource, never()).saveSeed(any(), any())
          }
        }

    @Test
    fun `missing mnemonic never synthesizes or persists a seed`() = runTest {
        val account = SoraAccount("watch-address", "Watch")
        whenever(datasource.retrieveSeed(account.substrateAddress)).thenReturn("")
        whenever(datasource.retrieveMnemonic(account.substrateAddress)).thenReturn("")
        whenever(datasource.retrieveKeys("")).thenReturn(null)

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
    fun `multiaccount recovery export decodes every stored hex seed to raw bytes`() = runTest {
        val first = SoraAccount("first-address", "First")
        val second = SoraAccount("second-address", "Second")
        val firstSeed = "00".repeat(32)
        val secondSeed = "ff".repeat(32)
        whenever(datasource.retrieveSeed(first.substrateAddress)).thenReturn(firstSeed)
        whenever(datasource.retrieveSeed(second.substrateAddress)).thenReturn(secondSeed)
        whenever(datasource.retrieveKeys(first.substrateAddress)).thenReturn(keypair)
        whenever(datasource.retrieveKeys(second.substrateAddress)).thenReturn(keypair)
        whenever(
            jsonAccountsEncoder.generate(
                accounts = any(),
                password = any(),
                genesisHash = any(),
            )
        ).thenReturn("recovery-json")

        assertEquals(
            "recovery-json",
            credentialsRepository.generateJson(listOf(first, second), "password"),
        )

        val accounts = argumentCaptor<List<JsonAccountsEncoder.ExportAccount>>()
        verify(jsonAccountsEncoder).generate(
            accounts.capture(),
            eq("password"),
            eq(Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH),
        )
        assertEquals(
            listOf(first.accountName, second.accountName),
            accounts.firstValue.map { it.name },
        )
        assertArrayEquals(ByteArray(32), accounts.firstValue[0].seed)
        assertArrayEquals(ByteArray(32) { 0xff.toByte() }, accounts.firstValue[1].seed)
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
    fun `create draft stores nothing until it is committed`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), mnemonic.toByteArray())
        every { MnemonicCreator.randomMnemonic(any()) } returns mnmnc
        stubDraftKeypair()

        val draft = credentialsRepository.createDraft("Name")

        assertEquals(fooaddress, draft.address)
        verify(datasource, never()).writeCredentialsIfAbsent(any(), any())
        verify(datasource, never()).saveKeys(any(), any())
        verify(datasource, never()).saveMnemonic(any(), any())

        whenever(datasource.readStoredCredential(any(), eq(fooaddress)))
            .thenReturn(StoredCredential.Absent)
        whenever(datasource.retrieveKeys(fooaddress)).thenReturn(storedCopy())

        assertEquals(SoraAccount(fooaddress, "Name"), credentialsRepository.commitDraft(draft))

        val written = argumentCaptor<Map<CredentialField, String>>()
        verify(datasource).writeCredentialsIfAbsent(eq(fooaddress), written.capture())
        assertEquals(
            setOf(
                CredentialField.PRIVATE_KEY,
                CredentialField.PUBLIC_KEY,
                CredentialField.KEY_NONCE,
                CredentialField.MNEMONIC,
            ),
            written.firstValue.keys,
        )
        assertEquals(mnemonic, written.firstValue[CredentialField.MNEMONIC])
        verify(datasource, never()).saveKeys(any(), any())
    }

    @Test
    fun `imported phrase adopts identical retained keys without writing`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), mnemonic.toByteArray())
        every { MnemonicCreator.fromWords(mnemonic) } returns mnmnc
        every { FirebaseWrapper.log(any<String>()) } just runs
        stubDraftKeypair()
        whenever(datasource.readStoredCredential(eq(CredentialField.PRIVATE_KEY), eq(fooaddress)))
            .thenReturn(StoredCredential.Present(privateKey.toHex()))
        whenever(datasource.readStoredCredential(eq(CredentialField.PUBLIC_KEY), eq(fooaddress)))
            .thenReturn(StoredCredential.Present(publicKey.toHex().uppercase()))
        whenever(datasource.readStoredCredential(eq(CredentialField.KEY_NONCE), eq(fooaddress)))
            .thenReturn(StoredCredential.Present(nonce.toHex()))
        whenever(datasource.readStoredCredential(eq(CredentialField.MNEMONIC), eq(fooaddress)))
            .thenReturn(StoredCredential.Present("  $mnemonic "))
        whenever(datasource.readStoredCredential(eq(CredentialField.SEED), eq(fooaddress)))
            .thenReturn(StoredCredential.Absent)
        whenever(datasource.retrieveKeys(fooaddress)).thenReturn(storedCopy())

        val draft = credentialsRepository.draftFromMnemonic(mnemonic, "Restored")
        assertEquals(SoraAccount(fooaddress, "Restored"), credentialsRepository.commitDraft(draft))

        verify(datasource, never()).writeCredentialsIfAbsent(any(), any())
        verify(datasource, never()).saveKeys(any(), any())
        verify(datasource, never()).saveMnemonic(any(), any())
    }

    @Test
    fun `imported phrase never overwrites different stored keys`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), mnemonic.toByteArray())
        every { MnemonicCreator.fromWords(mnemonic) } returns mnmnc
        stubDraftKeypair()
        whenever(datasource.readStoredCredential(eq(CredentialField.PRIVATE_KEY), eq(fooaddress)))
            .thenReturn(StoredCredential.Present("ff".repeat(64)))

        val draft = credentialsRepository.draftFromMnemonic(mnemonic, "Restored")
        val error = runCatching { credentialsRepository.commitDraft(draft) }.exceptionOrNull()

        assertEquals(
            ResponseCode.ACCOUNT_STORAGE_CONFLICT,
            (error as? SoraException)?.errorResponseCode,
        )
        verify(datasource, never()).writeCredentialsIfAbsent(any(), any())
        verify(datasource, never()).saveKeys(any(), any())
    }

    @Test
    fun `unreadable stored key refuses the import and writes nothing`() = runTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), mnemonic.toByteArray())
        every { MnemonicCreator.fromWords(mnemonic) } returns mnmnc
        stubDraftKeypair()
        whenever(datasource.readStoredCredential(eq(CredentialField.PRIVATE_KEY), eq(fooaddress)))
            .thenReturn(StoredCredential.Unreadable)

        val draft = credentialsRepository.draftFromMnemonic(mnemonic, "Restored")
        val error = runCatching { credentialsRepository.commitDraft(draft) }.exceptionOrNull()

        assertEquals(
            ResponseCode.ACCOUNT_STORAGE_CONFLICT,
            (error as? SoraException)?.errorResponseCode,
        )
        verify(datasource, never()).writeCredentialsIfAbsent(any(), any())
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

    @Test
    fun `raw seed import writes the seed and keys only where absent`() = runTest {
        val rawSeed = "0xcf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf"
        stubDraftKeypair()
        whenever(datasource.readStoredCredential(any(), eq(fooaddress)))
            .thenReturn(StoredCredential.Absent)
        whenever(datasource.retrieveKeys(fooaddress)).thenReturn(storedCopy())

        val draft = credentialsRepository.draftFromRawSeed(rawSeed, "Seed")
        credentialsRepository.commitDraft(draft)

        val written = argumentCaptor<Map<CredentialField, String>>()
        verify(datasource).writeCredentialsIfAbsent(eq(fooaddress), written.capture())
        assertEquals(rawSeed.removePrefix("0x"), written.firstValue[CredentialField.SEED])
        assertFalse(CredentialField.MNEMONIC in written.firstValue)
    }

    private val privateKey = ByteArray(64) { 3 }
    private val publicKey = ByteArray(32) { 4 }
    private val nonce = ByteArray(32) { 5 }

    private suspend fun stubDraftKeypair() {
        whenever(datasource.isExplicitWatchOnly(fooaddress)).thenReturn(false)
        whenever(keypair.privateKey).thenReturn(privateKey)
        whenever(keypair.publicKey).thenReturn(publicKey)
        whenever(keypair.nonce).thenReturn(nonce)
        whenever(sora2AddressCodec.toSoraAddressOrNull(publicKey)).thenReturn(fooaddress)
    }

    private fun storedCopy() = Sr25519Keypair(privateKey.copyOf(), publicKey.copyOf(), nonce.copyOf())

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
