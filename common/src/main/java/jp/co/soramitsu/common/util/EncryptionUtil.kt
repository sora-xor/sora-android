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

package jp.co.soramitsu.common.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import java.io.File
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.AEADBadTagException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal
import org.bouncycastle.util.Arrays
import org.bouncycastle.util.encoders.Base64

class EncryptionUtil(
    private val context: Context
) {

    companion object {
        private const val RSA = "RSA"
        private const val AES = "AES"
        private const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val KEY_ALIAS = "key_alias"
        private const val BLOCK_SIZE = 16
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
        private const val AUTHENTICATED_ENVELOPE_PREFIX = "v2:"
        private const val AES_KEY_LENGTH = 256
        private const val DATABASE_NAME = "app.db"
        private const val MAX_PREFERENCES_INSPECTION_BYTES = 8 * 1_024 * 1_024L
        private val WALLET_PREFERENCE_MARKERS =
            WalletPreferenceKeys.encryptedCredentialPrefixes

        private var privateKey: PrivateKey? = null
        private var publicKey: PublicKey? = null

        private const val SECRET_KEY = "secret_key"
        private val secureRandom = SecureRandom()
        private var keyStore: KeyStore? = null
    }

    @Volatile
    private var initializationFailure: Throwable? = null

    init {
        initKeystore()
    }

    private fun getPreferenceAesKey(): Key {
        initializationFailure?.let {
            throw KeyMaterialUnavailableException("Android Keystore key is unavailable", it)
        }

        val preferences = context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE)
        val encryptedKey = preferences.getString(SECRET_KEY, "").orEmpty()
        val secretKey: SecretKey
        if (encryptedKey.isEmpty()) {
            if (hasExistingWalletMaterial()) {
                throw KeyMaterialUnavailableException(
                    "Wrapped wallet key is missing while existing wallet material remains"
                )
            }
            val keyGenerator = KeyGenerator.getInstance(AES)
            keyGenerator.init(AES_KEY_LENGTH, secureRandom)
            secretKey = keyGenerator.generateKey()
            val encodedKey = secretKey.encoded
            val wrappedKey = try {
                encryptRsa(encodedKey)
            } finally {
                encodedKey.fill(0)
            }
            if (wrappedKey.isEmpty()) {
                throw KeyMaterialUnavailableException("Unable to wrap the wallet encryption key")
            }
            if (!preferences.edit().putString(SECRET_KEY, wrappedKey).commit()) {
                throw KeyMaterialUnavailableException(
                    "Unable to persist the wrapped wallet encryption key"
                )
            }
        } else {
            val key = decryptRsa(encryptedKey)
            secretKey = try {
                SecretKeySpec(key, 0, key.size, AES)
            } finally {
                key.fill(0)
            }
        }
        return secretKey
    }

    private fun initKeystore() {
        try {
            keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER)
            keyStore!!.load(null)

            if (keyStore!!.getKey(KEY_ALIAS, null) == null) {
                val hasWrappedWalletKey = context
                    .getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE)
                    .getString(SECRET_KEY, "")
                    .isNullOrEmpty()
                    .not()
                if (hasWrappedWalletKey || hasExistingWalletMaterial()) {
                    throw KeyMaterialUnavailableException(
                        "Keystore alias is missing while encrypted wallet material still exists"
                    )
                }
                createKeys()
            }

            privateKey = keyStore!!.getKey(KEY_ALIAS, null) as PrivateKey
            publicKey = keyStore!!.getCertificate(KEY_ALIAS).publicKey
        } catch (e: Exception) {
            initializationFailure = e
        }
    }

    private fun createKeys() {
        val startDate = Calendar.getInstance()
        val endDate = Calendar.getInstance()
        endDate.add(Calendar.YEAR, 25)

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setCertificateSubject(X500Principal("CN=Sora"))
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(startDate.time)
            .setCertificateNotAfter(endDate.time)
            .build()
        val keyPairGenerator = KeyPairGenerator.getInstance(RSA, KEY_STORE_PROVIDER)
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    fun encrypt(cleartext: String?): String {
        if (cleartext.isNullOrEmpty()) return ""

        val key = getPreferenceAesKey().encoded
        return try {
            encryptAuthenticated(key, cleartext)
        } finally {
            key.fill(0)
        }
    }

    /**
     * Legacy helper retained for callers that provide their own key. New wallet records use
     * authenticated envelopes through [encrypt].
     */
    fun encrypt(key: ByteArray, cleartext: String): String {
        val clear = cleartext.toByteArray(Charsets.UTF_8)
        return try {
            val result = encryptLegacy(key, clear)
            try {
                Base64.toBase64String(result)
            } finally {
                result.fill(0)
            }
        } finally {
            clear.fill(0)
        }
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        val key = getPreferenceAesKey().encoded
        return try {
            if (encryptedBase64.startsWith(AUTHENTICATED_ENVELOPE_PREFIX)) {
                decryptAuthenticated(
                    key,
                    encryptedBase64.removePrefix(AUTHENTICATED_ENVELOPE_PREFIX)
                )
            } else {
                decrypt(key, encryptedBase64)
            }
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(key: ByteArray, encryptedBase64: String): String {
        try {
            val encrypted = Base64.decode(encryptedBase64)
            val result = decryptLegacy(key, encrypted)
            return try {
                String(result, Charsets.UTF_8)
            } finally {
                result.fill(0)
            }
        } catch (e: Exception) {
            throw WalletDecryptionException("Unable to decrypt legacy wallet material", e)
        }
    }

    private fun encryptAuthenticated(key: ByteArray, cleartext: String): String {
        try {
            val iv = ByteArray(GCM_IV_SIZE).also(secureRandom::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, AES),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            val clear = cleartext.toByteArray(Charsets.UTF_8)
            val ciphertext = try {
                cipher.doFinal(clear)
            } finally {
                clear.fill(0)
            }
            val envelope = iv + ciphertext
            return try {
                AUTHENTICATED_ENVELOPE_PREFIX + Base64.toBase64String(envelope)
            } finally {
                ciphertext.fill(0)
                envelope.fill(0)
                iv.fill(0)
            }
        } catch (e: Exception) {
            throw WalletEncryptionException("Unable to encrypt wallet material", e)
        }
    }

    private fun decryptAuthenticated(key: ByteArray, encodedEnvelope: String): String {
        try {
            val envelope = Base64.decode(encodedEnvelope)
            if (envelope.size <= GCM_IV_SIZE) {
                throw WalletDecryptionException("Authenticated wallet envelope is truncated")
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, AES),
                GCMParameterSpec(GCM_TAG_BITS, envelope.copyOfRange(0, GCM_IV_SIZE))
            )
            val ciphertext = envelope.copyOfRange(GCM_IV_SIZE, envelope.size)
            val clear = try {
                cipher.doFinal(ciphertext)
            } finally {
                ciphertext.fill(0)
                envelope.fill(0)
            }
            return try {
                String(clear, Charsets.UTF_8)
            } finally {
                clear.fill(0)
            }
        } catch (e: AEADBadTagException) {
            throw WalletDecryptionException("Authenticated wallet envelope failed verification", e)
        } catch (e: WalletDecryptionException) {
            throw e
        } catch (e: Exception) {
            throw WalletDecryptionException("Unable to decrypt authenticated wallet material", e)
        }
    }

    @Throws(Exception::class)
    private fun encryptLegacy(key: ByteArray, clear: ByteArray): ByteArray {
        val skeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, IvParameterSpec(generateIVBytes()), secureRandom)
        return Arrays.concatenate(cipher.iv, cipher.doFinal(clear))
    }

    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        BadPaddingException::class,
        IllegalBlockSizeException::class
    )
    private fun decryptLegacy(key: ByteArray, encrypted: ByteArray): ByteArray {
        if (encrypted.size <= BLOCK_SIZE) {
            throw WalletDecryptionException("Legacy wallet envelope is truncated")
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
            IvParameterSpec(Arrays.copyOfRange(encrypted, 0, BLOCK_SIZE)), secureRandom
        )
        return cipher.doFinal(Arrays.copyOfRange(encrypted, BLOCK_SIZE, encrypted.size))
    }

    private fun encryptRsa(input: ByteArray): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            Base64.toBase64String(cipher.doFinal(input))
        } catch (error: Exception) {
            throw WalletEncryptionException("Unable to wrap the wallet encryption key", error)
        }
    }

    private fun decryptRsa(encrypted: String): ByteArray {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            cipher.doFinal(Base64.decode(encrypted))
        } catch (error: Exception) {
            throw KeyMaterialUnavailableException(
                "Unable to unwrap the existing wallet encryption key",
                error,
            )
        }
    }

    private fun generateIVBytes(): ByteArray {
        val ivBytes = ByteArray(BLOCK_SIZE)
        secureRandom.nextBytes(ivBytes)
        return ivBytes
    }

    /**
     * A missing key is safe only for a genuinely empty install. Existing accounts or credential
     * preference keys force recovery instead of allowing a new key to make the old wallet
     * undecryptable.
     */
    private fun hasExistingWalletMaterial(): Boolean {
        val database = context.getDatabasePath(DATABASE_NAME)
        if (database.isFile && database.length() > 0L) {
            val hasAccounts = runCatching {
                SQLiteDatabase.openDatabase(
                    database.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { sqlite ->
                    val accountsTableExists = sqlite.rawQuery(
                        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'accounts' LIMIT 1",
                        null,
                    ).use { it.moveToFirst() }
                    accountsTableExists && sqlite.rawQuery(
                        "SELECT 1 FROM accounts LIMIT 1",
                        null,
                    ).use { it.moveToFirst() }
                }
            }.getOrElse {
                // An unreadable non-empty production database is recovery material, not permission
                // to create a replacement encryption key.
                return true
            }
            if (hasAccounts) return true
        }

        val dataDirectory = File(context.applicationInfo.dataDir)
        val dataStore = File(
            context.filesDir,
            "datastore/sora_prefs_datastore.preferences_pb",
        )
        val sharedPreferences = File(
            dataDirectory,
            "shared_prefs/sora_prefs.xml",
        )
        val sharedPreferencesBackup = File(
            dataDirectory,
            "shared_prefs/sora_prefs.xml.bak",
        )
        // Once DataStore has been published it is authoritative. Before then,
        // SharedPreferences restores `.bak` over the main file on its next load,
        // so inspecting only the main XML could silently ignore recoverable
        // credentials and allow a replacement encryption key to be generated.
        val authoritativePreferences = when {
            dataStore.exists() -> dataStore
            sharedPreferencesBackup.exists() -> sharedPreferencesBackup
            else -> sharedPreferences
        }
        return authoritativePreferences.containsWalletPreferenceMarker()
    }

    private fun File.containsWalletPreferenceMarker(): Boolean {
        if (!isFile || length() == 0L) return false
        if (length() > MAX_PREFERENCES_INSPECTION_BYTES) return true
        val value = readBytes()
        return try {
            WALLET_PREFERENCE_MARKERS.any { marker ->
                value.indexOf(marker.toByteArray(Charsets.UTF_8)) >= 0
            }
        } finally {
            value.fill(0)
        }
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        if (needle.size > size) return -1
        for (start in 0..size - needle.size) {
            var match = true
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) {
                    match = false
                    break
                }
            }
            if (match) return start
        }
        return -1
    }
}

class KeyMaterialUnavailableException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class WalletEncryptionException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class WalletDecryptionException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
