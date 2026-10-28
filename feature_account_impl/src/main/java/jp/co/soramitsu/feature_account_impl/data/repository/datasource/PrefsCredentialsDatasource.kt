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

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.common.account.StoredSecretHex
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialField
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.StoredCredential
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair

class PrefsCredentialsDatasource constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val soraPreferences: SoraPreferences,
) : CredentialsDatasource {

    companion object {
        private const val PREFS_PRIVATE_KEY = WalletPreferenceKeys.PRIVATE_KEY
        private const val PREFS_PUBLIC_KEY = WalletPreferenceKeys.PUBLIC_KEY
        private const val PREFS_KEY_NONCE = WalletPreferenceKeys.KEY_NONCE
        private const val PREFS_MNEMONIC = WalletPreferenceKeys.MNEMONIC
        private const val PREFS_SEED = WalletPreferenceKeys.SEED
        private const val PREFS_ADDRESS = WalletPreferenceKeys.LEGACY_ADDRESS
        private const val PREFS_WATCH_ONLY = WalletPreferenceKeys.WATCH_ONLY
    }

    override suspend fun getAddress(): String {
        return soraPreferences.getString(PREFS_ADDRESS)
    }

    override suspend fun saveKeys(keyPair: Sr25519Keypair, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_PRIVATE_KEY + suffixAddress, keyPair.privateKey.toHexString())
        encryptedPreferences.putEncryptedString(PREFS_PUBLIC_KEY + suffixAddress, keyPair.publicKey.toHexString())
        encryptedPreferences.putEncryptedString(PREFS_KEY_NONCE + suffixAddress, keyPair.nonce.toHexString())
    }

    override suspend fun retrieveKeys(suffixAddress: String): Sr25519Keypair? {
        val privateKeyBytes = storedKeyBytes(PREFS_PRIVATE_KEY + suffixAddress)
        val publicKeyBytes = storedKeyBytes(PREFS_PUBLIC_KEY + suffixAddress)
        val nonce = storedKeyBytes(PREFS_KEY_NONCE + suffixAddress)

        return if (privateKeyBytes.isEmpty() || publicKeyBytes.isEmpty()) null else Sr25519Keypair(
            privateKeyBytes,
            publicKeyBytes,
            nonce
        )
    }

    /**
     * A stored key value is hex. Either letter case and an optional `0x` or `0X` prefix name the
     * same bytes, so they are read the same way. A stored value that is not hex is a failure of
     * that value (never treated as "no key stored").
     */
    private suspend fun storedKeyBytes(field: String): ByteArray {
        val text = encryptedPreferences.getDecryptedString(field)
        return StoredSecretHex.decodeOrNull(text)
            ?: throw WalletDecryptionException("WALLET_KEY_VALUE_NOT_HEX")
    }

    override suspend fun saveMnemonic(mnemonic: String, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_MNEMONIC + suffixAddress, mnemonic)
    }

    override suspend fun retrieveMnemonic(suffixAddress: String): String {
        return encryptedPreferences.getDecryptedString(PREFS_MNEMONIC + suffixAddress)
    }

    override suspend fun saveSeed(seed: String, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_SEED + suffixAddress, seed)
    }

    override suspend fun retrieveSeed(suffixAddress: String): String {
        return encryptedPreferences.getDecryptedString(PREFS_SEED + suffixAddress)
    }

    override suspend fun readStoredCredential(
        field: CredentialField,
        address: String,
    ): StoredCredential {
        val key = field.preferenceKey(address)
        if (soraPreferences.getString(key).isEmpty()) return StoredCredential.Absent
        return try {
            val value = encryptedPreferences.getDecryptedString(key)
            if (value.isEmpty()) StoredCredential.Unreadable else StoredCredential.Present(value)
        } catch (_: WalletDecryptionException) {
            StoredCredential.Unreadable
        }
    }

    override suspend fun writeCredentialsIfAbsent(
        address: String,
        values: Map<CredentialField, String>,
    ) {
        require(address.isNotBlank()) { "WALLET_CREDENTIAL_ADDRESS_EMPTY" }
        if (values.isEmpty()) return
        val encrypted = values.entries.associate { (field, value) ->
            require(value.isNotEmpty()) { "WALLET_CREDENTIAL_VALUE_EMPTY" }
            field.preferenceKey(address) to encryptedPreferences.encrypt(value)
        }
        soraPreferences.putStringsIfAbsent(encrypted)
    }

    override suspend fun replaceCredentialsForRepair(
        address: String,
        values: Map<CredentialField, String>,
    ) {
        require(address.isNotBlank()) { "WALLET_CREDENTIAL_ADDRESS_EMPTY" }
        require(values.isNotEmpty()) { "WALLET_CREDENTIAL_VALUES_EMPTY" }
        val encrypted = values.entries.associate { (field, value) ->
            require(value.isNotEmpty()) { "WALLET_CREDENTIAL_VALUE_EMPTY" }
            field.preferenceKey(address) to encryptedPreferences.encrypt(value)
        }
        soraPreferences.replaceStringsForConfirmedRepair(encrypted)
    }

    private fun CredentialField.preferenceKey(address: String): String = when (this) {
        CredentialField.PRIVATE_KEY -> PREFS_PRIVATE_KEY
        CredentialField.PUBLIC_KEY -> PREFS_PUBLIC_KEY
        CredentialField.KEY_NONCE -> PREFS_KEY_NONCE
        CredentialField.MNEMONIC -> PREFS_MNEMONIC
        CredentialField.SEED -> PREFS_SEED
    } + address

    override suspend fun requireKeyMaterialReadable() {
        encryptedPreferences.requireKeyMaterialReadable()
    }

    override suspend fun isExplicitWatchOnly(suffixAddress: String): Boolean =
        soraPreferences.getBoolean(PREFS_WATCH_ONLY + suffixAddress)

    override suspend fun setExplicitWatchOnly(suffixAddress: String, watchOnly: Boolean) {
        soraPreferences.putBoolean(PREFS_WATCH_ONLY + suffixAddress, watchOnly)
    }

    override suspend fun retainedKeySetsNeedingPhrase(): Set<String> =
        soraPreferences.retainedKeySetsNeedingPhrase()

    override suspend fun markKeySetsNeedingPhrase(exceptIds: Set<String>) {
        soraPreferences.markKeySetsNeedingPhrase(exceptIds)
    }

    override suspend fun unmarkKeySetsNeedingPhrase(ids: Set<String>) {
        soraPreferences.unmarkKeySetsNeedingPhrase(ids)
    }

    override suspend fun requireWalletPreferenceCoverage(
        walletIds: Set<String>,
        selectedAddress: String,
        requiredCoverage: Set<String>,
    ) {
        soraPreferences.requireWalletPreferenceCoverage(
            walletIds = walletIds,
            selectedAddress = selectedAddress,
            requiredCoverage = requiredCoverage,
        )
    }

    override suspend fun previewWalletDeletionPreferences(
        walletIds: Set<String>,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
        clearAll: Boolean,
    ): WalletPreferenceIntegrity.Hashes {
        val stringFields = walletIds.flatMap { walletId ->
            WalletPreferenceKeys.scopedStringKeys(walletId)
        }
            .toMutableSet()
        if (removeLegacyUnsuffixed) {
            stringFields += WalletPreferenceKeys.legacyUnsuffixedStringKeys
        }
        val booleanFields = walletIds
            .flatMap { walletId ->
                WalletPreferenceKeys.scopedBooleanKeys(walletId)
            }
            .toSet()
        return soraPreferences.previewWalletDeletion(
            stringFields = stringFields,
            booleanFields = booleanFields,
            selectedAddress = selectedAfter,
            clearAll = clearAll,
        )
    }

    override suspend fun commitWalletDeletionPreferences(
        walletIds: Set<String>,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
        clearAll: Boolean,
        expectedBeforeHash: String,
        expectedAfterHash: String,
    ) {
        val stringFields = walletIds.flatMap { walletId ->
            WalletPreferenceKeys.scopedStringKeys(walletId)
        }
            .toMutableSet()
        if (removeLegacyUnsuffixed) {
            stringFields += WalletPreferenceKeys.legacyUnsuffixedStringKeys
        }
        val booleanFields = walletIds
            .flatMap { walletId ->
                WalletPreferenceKeys.scopedBooleanKeys(walletId)
            }
            .toSet()
        soraPreferences.commitWalletDeletion(
            stringFields = stringFields,
            booleanFields = booleanFields,
            selectedAddress = selectedAfter,
            clearAll = clearAll,
            expectedBeforeHash = expectedBeforeHash,
            expectedAfterHash = expectedAfterHash,
        )
        check(
            soraPreferences.walletDeletionPreferencesMatch(
                stringFields = stringFields,
                booleanFields = booleanFields,
                selectedAddress = selectedAfter,
                clearAll = clearAll,
                expectedAfterHash = expectedAfterHash,
            )
        ) { "WALLET_DELETION_PREFERENCES_MISMATCH" }
    }
}
