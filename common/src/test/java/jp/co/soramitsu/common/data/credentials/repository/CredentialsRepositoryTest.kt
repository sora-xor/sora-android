/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.credentials.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockkStatic
import jp.co.soramitsu.common.data.network.substrate.toSoraAddress
import jp.co.soramitsu.common.domain.credentials.CredentialsDatasource
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.bip39.MnemonicLength
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
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
import org.mockito.BDDMockito.*
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
    private val bip39 = mock(Bip39::class.java)
    private val keypairFactory = mock(KeypairFactory::class.java)

    @Before fun setup() {
        credentialsRepository = CredentialsRepositoryImpl(datasource, cryptoAssistant)
    }

    @Test
    fun `is mnemonic valid returns true`() = runBlockingTest {
        val mnemonic = "mnemonic"

        given(cryptoAssistant.bip39).willReturn(bip39)
        given(bip39.isMnemonicValid(mnemonic)).willReturn(true)

        assertTrue(credentialsRepository.isMnemonicValid(mnemonic))
    }

    @Test
    fun `is mnemonic valid returns false`() = runBlockingTest {
        val mnemonic = "mnemonic2"

        given(cryptoAssistant.bip39).willReturn(bip39)
        given(bip39.isMnemonicValid(mnemonic)).willReturn(false)

        assertFalse(credentialsRepository.isMnemonicValid(mnemonic))
    }

    @Test
    fun `generate user credentials called`() = runBlockingTest {
        val mnemonic = "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val entropy = mnemonic.toByteArray()
        val seed = "seed".toByteArray()
        val keypair = mock(Keypair::class.java)
        val fc = mock(FirebaseCrashlytics::class.java)

        given(cryptoAssistant.bip39).willReturn(bip39)
        given(cryptoAssistant.keyPairFactory).willReturn(keypairFactory)
        given(keypairFactory.generate(OptionsProvider.encryptionType, seed)).willReturn(keypair)
        given(bip39.generateMnemonic(MnemonicLength.TWELVE)).willReturn(mnemonic)
        given(bip39.generateEntropy(mnemonic)).willReturn(entropy)
        given(bip39.generateSeed(entropy, "")).willReturn(seed)

        mockkStatic(ByteArray::toSoraAddress)
        mockkStatic(FirebaseCrashlytics::getInstance)
        every { keypair.publicKey.toSoraAddress() } returns "fooaddress"
        every { FirebaseCrashlytics.getInstance() } returns fc
        credentialsRepository.generateUserCredentials()

        verify(bip39).generateMnemonic(MnemonicLength.TWELVE)
        verify(bip39).generateEntropy(mnemonic)
        verify(bip39).generateSeed(entropy, "")
        verify(keypairFactory).generate(OptionsProvider.encryptionType, seed)
        verify(datasource).saveKeys(keypair)
        verify(datasource).saveMnemonic(mnemonic)
    }

    @Test
    fun `restore user credentials called`() = runBlockingTest {
        val mnemonic = "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val entropy = mnemonic.toByteArray()
        val seed = "seed".toByteArray()
        val publicKeyBytes = "publicKeyBytespublicKeyBytespublicKeyBytes".toByteArray()
        val irohaKeypair = mock(KeyPair::class.java)
        val irohaAddress = "did_sora_7075626c69634b657942@sora"
        val message = irohaAddress + Hex.toHexString(publicKeyBytes)
        val signature = irohaAddress.toByteArray()
        val keypair = mock(Keypair::class.java)
        val publicKey = mock(PublicKey::class.java)
        given(publicKey.encoded).willReturn(publicKeyBytes)
        given(irohaKeypair.public).willReturn(publicKey)

        given(cryptoAssistant.bip39).willReturn(bip39)
        given(cryptoAssistant.keyPairFactory).willReturn(keypairFactory)
        given(keypairFactory.generate(OptionsProvider.encryptionType, seed)).willReturn(keypair)
        given(cryptoAssistant.generateScryptSeedForEd25519(anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull())).willReturn(seed)
        given(cryptoAssistant.generateEd25519Keys(seed)).willReturn(irohaKeypair)
        given(cryptoAssistant.signEd25519(message.toByteArray(charset("UTF-8")), irohaKeypair)).willReturn(signature)
        given(bip39.generateEntropy(mnemonic)).willReturn(entropy)
        given(bip39.generateSeed(entropy, "")).willReturn(seed)

        credentialsRepository.restoreUserCredentials(mnemonic)

        verify(bip39).generateEntropy(mnemonic)
        verify(bip39).generateSeed(entropy, "")
        verify(keypairFactory).generate(OptionsProvider.encryptionType, seed)
        verify(datasource).saveMnemonic(mnemonic)
        verify(cryptoAssistant).signEd25519(message.toByteArray(charset("UTF-8")), irohaKeypair)
        verify(datasource).saveIrohaKeys(irohaKeypair)
        verify(datasource).saveIrohaAddress(irohaAddress)
        verify(datasource).saveSignature(signature)
    }

    @Test
    fun `save mnemonic called`() = runBlockingTest {
        val mnemonic = "airport wish wish loan width country acoustic country ceiling good enact penalty"

        credentialsRepository.saveMnemonic(mnemonic)

        verify(datasource).saveMnemonic(mnemonic)
    }

    @Test
    fun `retrieve mnemonic called`() = runBlockingTest {
        val mnemonic = "airport wish wish loan width country acoustic country ceiling good enact penalty"
        given(datasource.retrieveMnemonic()).willReturn(mnemonic)

        assertEquals(credentialsRepository.retrieveMnemonic(), mnemonic)
    }

    @Test
    fun `retrieve keypair called`() = runBlockingTest {
        val keypair = mock(Keypair::class.java)
        given(datasource.retrieveKeys()).willReturn(keypair)

        assertEquals(credentialsRepository.retrieveKeyPair(), keypair)
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
        val mnemonic = "airport wish wish loan width country acoustic country ceiling good enact penalty"
        val entropy = mnemonic.toByteArray()
        val seed = "seed".toByteArray()
        val publicKeyBytes = "publicKeyBytespublicKeyBytespublicKeyBytes".toByteArray()
        val irohaKeypair = mock(KeyPair::class.java)
        val message = irohaAddress + Hex.toHexString(publicKeyBytes)
        val signature = irohaAddress.toByteArray()
        val keypair = mock(Keypair::class.java)
        val publicKey = mock(PublicKey::class.java)
        val fc = mock(FirebaseCrashlytics::class.java)
        given(publicKey.encoded).willReturn(publicKeyBytes)
        given(irohaKeypair.public).willReturn(publicKey)
        given(cryptoAssistant.bip39).willReturn(bip39)
        given(cryptoAssistant.keyPairFactory).willReturn(keypairFactory)
        given(keypairFactory.generate(OptionsProvider.encryptionType, seed)).willReturn(keypair)
        given(bip39.generateEntropy(mnemonic)).willReturn(entropy)
        given(bip39.generateSeed(entropy, "")).willReturn(seed)
        given(keypairFactory.generate(OptionsProvider.encryptionType, seed)).willReturn(keypair)
        given(cryptoAssistant.generateScryptSeedForEd25519(anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull())).willReturn(seed)
        given(cryptoAssistant.generateEd25519Keys(seed)).willReturn(irohaKeypair)
        given(cryptoAssistant.signEd25519(message.toByteArray(charset("UTF-8")), irohaKeypair)).willReturn(signature)
        given(datasource.getIrohaAddress()).willReturn("", irohaAddress)
        given(datasource.retrieveMnemonic()).willReturn(mnemonic)
        mockkStatic(ByteArray::toSoraAddress)
        mockkStatic(FirebaseCrashlytics::getInstance)
        every { keypair.publicKey.toSoraAddress() } returns "fo oaddress"
        every { FirebaseCrashlytics.getInstance() } returns fc

        assertEquals(credentialsRepository.getIrohaAddress(), irohaAddress)

        verify(bip39).generateEntropy(mnemonic)
    }

    @Test
    fun `get claim signature called`() = runBlockingTest {
        val signature = "did_sora_7075626c69634b657942@sora".toByteArray()
        val signatureHex = Hex.toHexString(signature)
        given(datasource.retrieveSignature()).willReturn(signature)

        assertEquals(credentialsRepository.getClaimSignature(), signatureHex)
    }
}