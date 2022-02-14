/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.credentials.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.domain.credentials.CredentialsDatasource
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import org.spongycastle.util.encoders.Hex
import java.security.KeyPair
import javax.inject.Inject

class PrefsCredentialsDatasource @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val soraPreferences: SoraPreferences,
) : CredentialsDatasource {

    companion object {
        private const val PREFS_PRIVATE_KEY = "prefs_priv_key"
        private const val PREFS_PUBLIC_KEY = "prefs_pub_key"
        private const val PREFS_IROHA_PRIVATE_KEY = "prefs_iroha_priv_key"
        private const val PREFS_IROHA_PUBLIC_KEY = "prefs_iroha_pub_key"
        private const val PREFS_KEY_NONCE = "prefs_key_nonce"
        private const val PREFS_MNEMONIC = "prefs_mnemonic"
        private const val PREFS_ADDRESS = "prefs_address_pure"
        private const val PREFS_IROHA_ADDRESS = "prefs_iroha_address"
        private const val PREFS_SIGNATURE = "prefs_signature"
    }

    override suspend fun saveAddress(address: String) {
        soraPreferences.putString(PREFS_ADDRESS, address)
    }

    override suspend fun getAddress(): String {
        return soraPreferences.getString(PREFS_ADDRESS)
    }

    override suspend fun saveKeys(keyPair: Sr25519Keypair) {
        encryptedPreferences.putEncryptedString(PREFS_PRIVATE_KEY, Hex.toHexString(keyPair.privateKey))
        encryptedPreferences.putEncryptedString(PREFS_PUBLIC_KEY, Hex.toHexString(keyPair.publicKey))
        encryptedPreferences.putEncryptedString(PREFS_KEY_NONCE, Hex.toHexString(keyPair.nonce))
    }

    override suspend fun retrieveKeys(): Sr25519Keypair? {
        val privateKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_PRIVATE_KEY))
        val publicKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_PUBLIC_KEY))
        val nonce = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_KEY_NONCE))

        return if (privateKeyBytes.isEmpty() || publicKeyBytes.isEmpty()) null else Sr25519Keypair(privateKeyBytes, publicKeyBytes, nonce)
    }

    override suspend fun saveMnemonic(mnemonic: String) {
        encryptedPreferences.putEncryptedString(PREFS_MNEMONIC, mnemonic)
    }

    override suspend fun retrieveMnemonic(): String {
        return encryptedPreferences.getDecryptedString(PREFS_MNEMONIC)
    }

    override suspend fun saveIrohaKeys(keyPair: KeyPair) {
        encryptedPreferences.putEncryptedString(PREFS_IROHA_PRIVATE_KEY, Hex.toHexString(keyPair.private.encoded))
        encryptedPreferences.putEncryptedString(PREFS_IROHA_PUBLIC_KEY, Hex.toHexString(keyPair.public.encoded))
    }

    override suspend fun retrieveIrohaKeys(): KeyPair? {
        val privateKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_IROHA_PRIVATE_KEY))
        val publicKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_IROHA_PUBLIC_KEY))
        return Ed25519Sha3.keyPairFromBytes(privateKeyBytes, publicKeyBytes)
    }

    override suspend fun saveIrohaAddress(address: String) {
        encryptedPreferences.putEncryptedString(PREFS_IROHA_ADDRESS, address)
    }

    override suspend fun getIrohaAddress(): String {
        return encryptedPreferences.getDecryptedString(PREFS_IROHA_ADDRESS)
    }

    override suspend fun saveSignature(signature: ByteArray) {
        encryptedPreferences.putEncryptedString(PREFS_SIGNATURE, Hex.toHexString(signature))
    }

    override suspend fun retrieveSignature(): ByteArray {
        return Hex.decode(encryptedPreferences.getDecryptedString(PREFS_SIGNATURE))
    }
}
