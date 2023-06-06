/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.shared_utils.extensions.toHexString

import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class PrefsCredentialsDatasourceTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var encryptedPreferences: EncryptedPreferences

    @Mock
    private lateinit var soraPreferences: SoraPreferences

    private lateinit var prefsCredentialsDatasource: PrefsCredentialsDatasource

    @Before
    fun setUp() {
        prefsCredentialsDatasource =
            PrefsCredentialsDatasource(
                encryptedPreferences,
                soraPreferences
            )
    }

    @Test
    fun `save keys called`() = runTest {
        val address = "suffixaddress"
        val privateKeyString = "prefs_priv_key"
        val publicKeyString = "prefs_pub_key"
        val nonceKeyString = "prefs_key_nonce"
        val privateKey = "privKey".toByteArray()
        val publicKey = "pubKey".toByteArray()
        val nonce = "nonce".toByteArray()
        val keypair = Sr25519Keypair(privateKey, publicKey, nonce)

        prefsCredentialsDatasource.saveKeys(keypair, address)

        verify(encryptedPreferences).putEncryptedString(privateKeyString + address, privateKey.toHexString())
        verify(encryptedPreferences).putEncryptedString(publicKeyString + address, publicKey.toHexString())
        verify(encryptedPreferences).putEncryptedString(nonceKeyString + address, nonce.toHexString())
    }

    @Test
    fun `retrieve keys called`() = runTest {
        val address = "suffixaddress"
        val privateKeyString = "prefs_priv_key"
        val publicKeyString = "prefs_pub_key"
        val nonceKeyString = "prefs_key_nonce"
        val privateKey = "privKey".toByteArray()
        val publicKey = "pubKey".toByteArray()
        val nonce = "nonce".toByteArray()
        given(encryptedPreferences.getDecryptedString(privateKeyString + address)).willReturn(privateKey.toHexString())
        given(encryptedPreferences.getDecryptedString(publicKeyString + address)).willReturn(publicKey.toHexString())
        given(encryptedPreferences.getDecryptedString(nonceKeyString + address)).willReturn(nonce.toHexString())

        val keypair = prefsCredentialsDatasource.retrieveKeys(address)

        assertArrayEquals(publicKey, keypair?.publicKey)
        assertArrayEquals(privateKey, keypair?.privateKey)
        assertArrayEquals(nonce, keypair?.nonce)
    }

    @Test
    fun `retrieve keys called with no keys`() = runTest {
        val address = "suffixaddress"
        val privateKeyString = "prefs_priv_key"
        val publicKeyString = "prefs_pub_key"
        val nonceKeyString = "prefs_key_nonce"
        given(encryptedPreferences.getDecryptedString(privateKeyString + address)).willReturn("")
        given(encryptedPreferences.getDecryptedString(publicKeyString + address)).willReturn("")
        given(encryptedPreferences.getDecryptedString(nonceKeyString + address)).willReturn("")

        val keypair = prefsCredentialsDatasource.retrieveKeys(address)

        assertNull(keypair)
    }

    @Test
    fun `save mnemonic called`() = runTest {
        val keyMnemonic = "prefs_mnemonic"
        val mnemonic = "mnemonic"

        prefsCredentialsDatasource.saveMnemonic(mnemonic, "")

        verify(encryptedPreferences).putEncryptedString(keyMnemonic, mnemonic)
    }

    @Test
    fun `retrieve mnemonic called`() = runTest {
        val keyMnemonic = "prefs_mnemonic"
        val mnemonic = "mnemonic"
        given(encryptedPreferences.getDecryptedString(keyMnemonic)).willReturn(mnemonic)

        assertEquals(mnemonic, prefsCredentialsDatasource.retrieveMnemonic(""))
    }

    @Test
    fun `save seed called`() = runTest {
        val keySeed = "prefs_seed"
        val seed = "seed"

        prefsCredentialsDatasource.saveSeed(seed, "")

        verify(encryptedPreferences).putEncryptedString(keySeed, seed)
    }

    @Test
    fun `retrieve seed called`() = runTest {
        val keySeed = "prefs_seed"
        val seed = "seed"
        given(encryptedPreferences.getDecryptedString(keySeed)).willReturn(seed)

        assertEquals(seed, prefsCredentialsDatasource.retrieveSeed(""))
    }

    @Test
    fun `clear all data for address called`() = runTest {
        val address = "address"
        val keys = listOf("prefs_address_pureaddress", "prefs_priv_keyaddress", "prefs_pub_keyaddress", "prefs_key_nonceaddress", "prefs_mnemonicaddress", "prefs_seedaddress")

        prefsCredentialsDatasource.clearAllDataForAddress(address)

        verify(encryptedPreferences).clear(keys)
    }
}