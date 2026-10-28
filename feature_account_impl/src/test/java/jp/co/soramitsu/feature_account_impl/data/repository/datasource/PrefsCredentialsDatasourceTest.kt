/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `watch only state requires the explicit durable marker`() = runTest {
        val address = "address"
        given(soraPreferences.getBoolean("wallet.watchOnly.$address")).willReturn(true)

        assertTrue(prefsCredentialsDatasource.isExplicitWatchOnly(address))

        prefsCredentialsDatasource.setExplicitWatchOnly(address, false)
        verify(soraPreferences).putBoolean("wallet.watchOnly.$address", false)
    }

    @Test
    fun `wallet preference coverage delegates exact wallet set and selection`() = runTest {
        val walletIds = setOf("address", "second-address")

        prefsCredentialsDatasource.requireWalletPreferenceCoverage(
            walletIds = walletIds,
            selectedAddress = "second-address",
        )

        verify(soraPreferences).requireWalletPreferenceCoverage(
            walletIds,
            "second-address",
        )
    }

    @Test
    fun `wallet deletion preview delegates exact preference key set`() = runTest {
        val address = "address"
        val strings = setOf(
            "prefs_address_pureaddress",
            "prefs_priv_keyaddress",
            "prefs_pub_keyaddress",
            "prefs_key_nonceaddress",
            "prefs_mnemonicaddress",
            "prefs_seedaddress",
        )
        val booleans = setOf(
            "wallet.watchOnly.address",
            "needs_migrationaddress",
            "is_migration_fetchedaddress",
        )
        val hashes = WalletPreferenceIntegrity.Hashes(
            before = "1".repeat(64),
            after = "2".repeat(64),
        )
        given(
            soraPreferences.previewWalletDeletion(
                strings,
                booleans,
                "replacement",
                false,
            )
        ).willReturn(hashes)

        assertEquals(
            hashes,
            prefsCredentialsDatasource.previewWalletDeletionPreferences(
                walletIds = setOf(address),
                selectedAfter = "replacement",
                removeLegacyUnsuffixed = false,
                clearAll = false,
            ),
        )

        verify(soraPreferences).previewWalletDeletion(
            strings,
            booleans,
            "replacement",
            false,
        )
    }

    @Test
    fun `journaled deletion uses one edit and verifies exact result`() = runTest {
        val address = "address"
        val strings = setOf(
            "prefs_address_pureaddress",
            "prefs_priv_keyaddress",
            "prefs_pub_keyaddress",
            "prefs_key_nonceaddress",
            "prefs_mnemonicaddress",
            "prefs_seedaddress",
        )
        val booleans = setOf(
            "wallet.watchOnly.address",
            "needs_migrationaddress",
            "is_migration_fetchedaddress",
        )
        val beforeHash = "1".repeat(64)
        val afterHash = "2".repeat(64)
        given(
            soraPreferences.walletDeletionPreferencesMatch(
                strings,
                booleans,
                "replacement",
                false,
                afterHash,
            )
        ).willReturn(true)

        prefsCredentialsDatasource.commitWalletDeletionPreferences(
            walletIds = setOf(address),
            selectedAfter = "replacement",
            removeLegacyUnsuffixed = false,
            clearAll = false,
            expectedBeforeHash = beforeHash,
            expectedAfterHash = afterHash,
        )

        verify(soraPreferences).commitWalletDeletion(
            strings,
            booleans,
            "replacement",
            false,
            beforeHash,
            afterHash,
        )
        verify(soraPreferences).walletDeletionPreferencesMatch(
            strings,
            booleans,
            "replacement",
            false,
            afterHash,
        )
    }
}
