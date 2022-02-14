/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.credentials.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import jp.co.soramitsu.common.data.network.substrate.toSoraAddress
import jp.co.soramitsu.common.domain.credentials.CredentialsDatasource
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.deriveSeed32
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.SeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.verify
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import java.security.KeyPair
import java.security.PublicKey

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class CredentialsRepositoryTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var credentialsRepository: CredentialsRepositoryImpl

    private val datasource = mock(CredentialsDatasource::class.java)
    private val cryptoAssistant = mock(CryptoAssistant::class.java)

    @Before
    fun setup() {
        credentialsRepository = CredentialsRepositoryImpl(datasource, cryptoAssistant)
    }

    @Test
    fun `is mnemonic valid returns true`() = runBlockingTest {
        val mnemonic = "mnemonic"
        mockkObject(MnemonicCreator)
        every { MnemonicCreator.fromWords(any()) } returns Mnemonic(
            "",
            emptyList(),
            ByteArray(1) { 1 })
        assertTrue(credentialsRepository.isMnemonicValid(mnemonic))
    }

    @Test
    fun `is mnemonic valid returns false`() = runBlockingTest {
        val mnemonic = "mnemonic2"
        mockkObject(MnemonicCreator)
        every { MnemonicCreator.fromWords(any()) } throws IllegalArgumentException()
        assertFalse(credentialsRepository.isMnemonicValid(mnemonic))
    }

    @Test
    fun `generate user credentials called`() = runBlockingTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val entropy = mnemonic.toByteArray()
        val seed = "seed".toByteArray()
        val keypair = mock(Sr25519Keypair::class.java)
        val derivationResult = mock(SeedFactory.Result::class.java)
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), entropy)
        mockkObject(MnemonicCreator)
        every { MnemonicCreator.randomMnemonic(any()) } returns mnmnc
        mockkObject(SubstrateKeypairFactory)
        every { SubstrateKeypairFactory.generate(any(), any()) } returns keypair
        mockkStatic(SubstrateSeedFactory::deriveSeed32)
        mockkStatic(ByteArray::toSoraAddress)
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log("Keys were created") } returns Unit
        every { SubstrateSeedFactory.deriveSeed32(any(), any()) } returns derivationResult
        every { keypair.publicKey.toSoraAddress() } returns "fooaddress"

        credentialsRepository.generateUserCredentials()

        verify(datasource).saveKeys(keypair)
        verify(datasource).saveMnemonic(mnemonic)
    }

    @Test
    fun `restore user credentials called`() = runBlockingTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val entropy = mnemonic.toByteArray()
        val seed = "seed".toByteArray()
        val publicKeyBytes = "publicKeyBytespublicKeyBytespublicKeyBytes".toByteArray()
        val irohaKeypair = mock(KeyPair::class.java)
        val irohaAddress = "did_sora_7075626c69634b657942@sora"
        val message = irohaAddress + Hex.toHexString(publicKeyBytes)
        val signature = irohaAddress.toByteArray()
        val keypair = mock(Sr25519Keypair::class.java)
        val publicKey = mock(PublicKey::class.java)
        val derivationResult = mock(SeedFactory.Result::class.java)
        val mnmnc = Mnemonic(mnemonic, mnemonic.split(" "), entropy)
        mockkObject(MnemonicCreator)
        every { MnemonicCreator.fromWords(mnemonic) } returns mnmnc
        mockkObject(SubstrateKeypairFactory)
        every { SubstrateKeypairFactory.generate(any(), any()) } returns keypair
        given(publicKey.encoded).willReturn(publicKeyBytes)
        given(irohaKeypair.public).willReturn(publicKey)
        mockkStatic(SubstrateSeedFactory::deriveSeed32)
        every { SubstrateSeedFactory.deriveSeed32(any(), any()) } returns derivationResult
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log("Keys were created") } returns Unit
        mockkStatic(ByteArray::toSoraAddress)
        every { keypair.publicKey.toSoraAddress() } returns "fooaddress"

        given(
            cryptoAssistant.generateScryptSeedForEd25519(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull()
            )
        ).willReturn(seed)
        given(cryptoAssistant.generateEd25519Keys(seed)).willReturn(irohaKeypair)
        given(
            cryptoAssistant.signEd25519(
                message.toByteArray(),
                irohaKeypair
            )
        ).willReturn(signature)

        credentialsRepository.restoreUserCredentials(mnemonic)

        verify(datasource).saveMnemonic(mnemonic)
        verify(cryptoAssistant).signEd25519(message.toByteArray(), irohaKeypair)
        verify(datasource).saveIrohaKeys(irohaKeypair)
        verify(datasource).saveIrohaAddress(irohaAddress)
        verify(datasource).saveSignature(signature)
    }

    @Test
    fun `save mnemonic called`() = runBlockingTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"

        credentialsRepository.saveMnemonic(mnemonic)

        verify(datasource).saveMnemonic(mnemonic)
    }

    @Test
    fun `retrieve mnemonic called`() = runBlockingTest {
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        given(datasource.retrieveMnemonic()).willReturn(mnemonic)

        assertEquals(credentialsRepository.retrieveMnemonic(), mnemonic)
    }

    @Test
    fun `retrieve keypair called`() = runBlockingTest {
        val keypair = mock(Sr25519Keypair::class.java)
        given(datasource.retrieveKeys()).willReturn(keypair)

        assertEquals(keypair, credentialsRepository.retrieveKeyPair())
    }

    @Test
    fun `retrieve iroha keypair called`() = runBlockingTest {
        val keypair = mock(KeyPair::class.java)
        given(datasource.retrieveIrohaKeys()).willReturn(keypair)

        assertEquals(credentialsRepository.retrieveIrohaKeyPair(), keypair)
    }

    @Test
    fun `get iroha address called`() = runBlockingTest {
        val irohaAddress = "did_sora_7075626c69634b657942@sora"
        given(datasource.getIrohaAddress()).willReturn(irohaAddress)

        assertEquals(credentialsRepository.getIrohaAddress(), irohaAddress)
    }

    @Test
    fun `get iroha address if empty called`() = runBlockingTest {
        val irohaAddress = "did_sora_7075626c69634b657942@sora"
        val mnemonic =
            "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val seed = "seed".toByteArray()
        val publicKeyBytes = "publicKeyBytespublicKeyBytespublicKeyBytes".toByteArray()
        val irohaKeypair = mock(KeyPair::class.java)
        val message = irohaAddress + Hex.toHexString(publicKeyBytes)
        val signature = irohaAddress.toByteArray()
        val keypair = mock(Sr25519Keypair::class.java)
        val publicKey = mock(PublicKey::class.java)
        given(publicKey.encoded).willReturn(publicKeyBytes)
        given(irohaKeypair.public).willReturn(publicKey)
        given(
            cryptoAssistant.generateScryptSeedForEd25519(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull()
            )
        ).willReturn(seed)
        given(cryptoAssistant.generateEd25519Keys(seed)).willReturn(irohaKeypair)
        given(
            cryptoAssistant.signEd25519(
                message.toByteArray(),
                irohaKeypair
            )
        ).willReturn(signature)
        given(datasource.getIrohaAddress()).willReturn("", irohaAddress)
        given(datasource.retrieveMnemonic()).willReturn(mnemonic)
        mockkStatic(ByteArray::toSoraAddress)
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log("Keys were created") } returns Unit
        every { keypair.publicKey.toSoraAddress() } returns "fo oaddress"

        assertEquals(credentialsRepository.getIrohaAddress(), irohaAddress)
    }

    @Test
    fun `get claim signature called`() = runBlockingTest {
        val signature = "did_sora_7075626c69634b657942@sora".toByteArray()
        val signatureHex = Hex.toHexString(signature)
        given(datasource.retrieveSignature()).willReturn(signature)

        assertEquals(credentialsRepository.getClaimSignature(), signatureHex)
    }
}