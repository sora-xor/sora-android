/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.credentials.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.domain.credentials.CredentialsDatasource
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import org.spongycastle.util.encoders.Hex
import javax.inject.Inject

class PrefsCredentialsDatasource @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val soraPreferences: SoraPreferences,
) : CredentialsDatasource {

    companion object {
        private const val PREFS_PRIVATE_KEY = "prefs_priv_key"
        private const val PREFS_PUBLIC_KEY = "prefs_pub_key"
        private const val PREFS_KEY_NONCE = "prefs_key_nonce"
        private const val PREFS_MNEMONIC = "prefs_mnemonic"
        private const val PREFS_ADDRESS = "prefs_address_pure"
    }

    override suspend fun getAddress(): String {
        return soraPreferences.getString(PREFS_ADDRESS)
    }

    override suspend fun saveKeys(keyPair: Sr25519Keypair, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_PRIVATE_KEY + suffixAddress, Hex.toHexString(keyPair.privateKey))
        encryptedPreferences.putEncryptedString(PREFS_PUBLIC_KEY + suffixAddress, Hex.toHexString(keyPair.publicKey))
        encryptedPreferences.putEncryptedString(PREFS_KEY_NONCE + suffixAddress, Hex.toHexString(keyPair.nonce))
    }

    override suspend fun retrieveKeys(suffixAddress: String): Sr25519Keypair? {
        val privateKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_PRIVATE_KEY + suffixAddress))
        val publicKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_PUBLIC_KEY + suffixAddress))
        val nonce = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_KEY_NONCE + suffixAddress))

        return if (privateKeyBytes.isEmpty() || publicKeyBytes.isEmpty()) null else Sr25519Keypair(privateKeyBytes, publicKeyBytes, nonce)
    }

    override suspend fun saveMnemonic(mnemonic: String, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_MNEMONIC + suffixAddress, mnemonic)
    }

    override suspend fun retrieveMnemonic(suffixAddress: String): String {
        return encryptedPreferences.getDecryptedString(PREFS_MNEMONIC + suffixAddress)
    }
}
