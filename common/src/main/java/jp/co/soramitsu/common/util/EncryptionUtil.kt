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

package jp.co.soramitsu.common.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import java.io.File
import java.math.BigInteger
import java.util.UUID
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import android.util.Xml
import java.io.FileInputStream
import org.xmlpull.v1.XmlPullParser
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.util.Calendar
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal
import org.bouncycastle.util.Arrays
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.util.encoders.DecoderException

/**
 * Encrypts wallet preference values with an AES key that is wrapped by the Android Keystore RSA
 * alias `key_alias` (unchanged since SORA 2.3).
 *
 * The Keystore can be briefly unavailable (busy daemon, device still unlocking, low memory, an
 * operation handle that the Keystore pruned). Such failures are reported as transient
 * [KeyMaterialUnavailableException]s and are never remembered: the alias probe and the unwrap of
 * the AES key run together in one short bounded retry, and every later call starts again, so a
 * process recovers as soon as the Keystore does. A missing provider key is transient until the
 * same observation recurs in a fresh process; a key that provably cannot unwrap is non-transient. In that state no new key is ever created while wallet
 * material exists, because a new key would make the existing encrypted wallet data unreadable.
 */
class EncryptionUtil @JvmOverloads constructor(
    private val context: Context,
    private val keyStoreFactory: () -> KeyStore = { KeyStore.getInstance(KEY_STORE_PROVIDER) },
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    /** Unwraps the stored AES key with the alias's private key (a test seam for Keystore faults). */
    private val rsaDecryptor: (PrivateKey, ByteArray) -> ByteArray = ::decryptRsaWithKeystore,
    private val readOnlyInspection: Boolean = false,
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

        /** First attempt immediately, then two short waits; at most about 0.65 s in total. */
        private val DEFAULT_RETRY_DELAYS_MILLIS = listOf(0L, 150L, 500L)

        private const val SECRET_KEY = "secret_key"
        private const val WRAPPING_ALIAS = "wrapping_alias"
        private val processObservation = UUID.randomUUID().toString()
        private val secureRandom = SecureRandom()

        /** Serializes creation of the wrapped AES key across every instance in the process. */
        private val wrappedKeyCreationLock = Any()

        /**
         * A wrapped key whose first write to disk failed (for example the disk was full). The
         * in-memory SharedPreferences still hold it, so nothing may be encrypted with it until it
         * has been written; otherwise the next start would find encrypted wallet data without the
         * key that unlocks it.
         */
        @Volatile
        private var unpersistedWrappedKey: String? = null

        private fun decryptRsaWithKeystore(privateKey: PrivateKey, wrapped: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            return cipher.doFinal(wrapped)
        }

        /**
         * True when unwrapping failed because of the stored bytes or the alias itself, so trying
         * again cannot help:
         * - a padding failure (the Keystore reports a wrapped key that does not belong to this
         *   alias as `BadPaddingException`),
         * - a permanently invalidated alias,
         * - an `IllegalBlockSizeException` without a cause (the pre-Android 12 Keystore reports a
         *   wrong input length that way).
         *
         * Everything else is a Keystore operation failure and is transient. In particular the
         * Keystore wraps a busy backend, a system error or a pruned operation handle in
         * `IllegalBlockSizeException` with an `android.security.KeyStoreException` cause.
         */
        @JvmStatic
        fun isPermanentUnwrapFailure(error: Throwable): Boolean = when (error) {
            is BadPaddingException -> true
            is KeyPermanentlyInvalidatedException -> true
            is IllegalBlockSizeException -> error.cause == null
            else -> false
        }
    }

    private class KeystoreKeys(
        val privateKey: PrivateKey,
        val publicKey: PublicKey,
        val alias: String,
    )

    @Volatile
    private var keystoreKeys: KeystoreKeys? = null

    init {
        // Warm the alias once for a fresh install. A failure here is deliberately not remembered;
        // the next encrypt/decrypt probes the Keystore again.
        runCatching { ensureKeystoreKeys(attempts = 1, allowCreation = false) }
    }

    @Synchronized
    private fun ensureKeystoreKeys(attempts: Int = retryDelaysMillis.size, allowCreation: Boolean = true): KeystoreKeys {
        val selectedAlias = wrappedKeyMetadata()[WRAPPING_ALIAS] ?: KEY_ALIAS
        keystoreKeys?.takeIf { it.alias == selectedAlias && (!allowCreation || hasWrappedWalletKey()) }?.let { return it }
        var lastFailure: KeyMaterialUnavailableException? = null
        for (attempt in 0 until attempts.coerceIn(1, retryDelaysMillis.size.coerceAtLeast(1))) {
            val delay = retryDelaysMillis.getOrElse(attempt) { 0L }
            if (delay > 0L) sleeper(delay)
            try {
                return probeKeystore(allowCreation).also { keystoreKeys = it }
            } catch (error: KeyMaterialUnavailableException) {
                lastFailure = error
                if (!error.transient) throw error
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun probeKeystore(allowCreation: Boolean): KeystoreKeys {
        var alias = wrappedKeyMetadata()[WRAPPING_ALIAS] ?: KEY_ALIAS
        val keyStore = try {
            keyStoreFactory().also { it.load(null) }
        } catch (error: Exception) {
            throw KeyMaterialUnavailableException(
                "Android Keystore is temporarily unavailable",
                error,
                transient = true,
            )
        }
        if (allowCreation && wrappedKeyMetadata()[WRAPPING_ALIAS] == null && !hasWrappedWalletKey() &&
            !hasExistingWalletMaterial() && retainedAliasExists(alias)) {
            alias = "sora_wallet_" + UUID.randomUUID().toString()
            if (!keyPreferencesForWrite().edit().putString(WRAPPING_ALIAS, alias).commit()) {
                throw KeyMaterialUnavailableException("Unable to persist wallet alias", transient = true)
            }
        }
        val existingKey = try {
            keyStore.getKey(alias, null)
        } catch (error: Exception) {
            throw KeyMaterialUnavailableException(
                "Android Keystore alias could not be read",
                error,
                transient = true,
            )
        }
        if (existingKey == null) {
            // Never create a replacement alias while wrapped or encrypted wallet material remains.
            // Re-checked on every probe: the answer depends only on files and is cheap.
            if (hasWrappedWalletKey() || hasExistingWalletMaterial()) {
                throw observedUnavailable("MISSING_ALIAS")
            }
            if (!allowCreation) throw KeyMaterialUnavailableException("No wallet alias to inspect", transient = true)
            // getKey(null) is not proof that an alias can safely be replaced (older providers can
            // temporarily return null). A preserved generation may also still own that alias.
            if (keyStore.containsAlias(alias) || retainedAliasExists(alias)) {
                alias = "sora_wallet_" + UUID.randomUUID().toString()
                val preferences = keyPreferencesForWrite()
                if (!preferences.edit().putString(WRAPPING_ALIAS, alias).commit()) {
                    throw KeyMaterialUnavailableException("Unable to persist wallet alias", transient = true)
                }
            }
            try {
                createKeys(alias)
            } catch (error: Exception) {
                throw KeyMaterialUnavailableException(
                    "Android Keystore could not create the wallet alias",
                    error,
                    transient = true,
                )
            }
        }
        return try {
            val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            val publicKey = keyStore.getCertificate(alias)?.publicKey
            if (privateKey == null || publicKey == null) {
                throw KeyMaterialUnavailableException(
                    "Android Keystore alias is incomplete",
                    transient = true,
                )
            }
            KeystoreKeys(privateKey, publicKey, alias)
        } catch (error: KeyMaterialUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw KeyMaterialUnavailableException(
                "Android Keystore alias could not be read",
                error,
                transient = true,
            )
        }
    }

    private fun keyPreferencesForWrite(): android.content.SharedPreferences = try {
        context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE)
    } catch (error: Exception) {
        throw KeyMaterialUnavailableException("Wallet key preferences temporarily unavailable", error, transient = true)
    }

    private fun hasWrappedWalletKey(): Boolean = !wrappedKeyMetadata()[SECRET_KEY].isNullOrEmpty()

    /** Reads the authoritative bytes, including SharedPreferences' pending .bak recovery.
     * A cached empty SharedPreferences object never proves that the wrapped key is missing. */
    private fun wrappedKeyMetadata(root: File = File(context.applicationInfo.dataDir), exact: File? = null): Map<String, String> {
        val directory = File(root, "shared_prefs")
        val backup = File(directory, "key_alias.xml.bak")
        val original = File(directory, "key_alias.xml")
        fun exists(file: File): Boolean = try { Os.lstat(file.path); true } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) false else throw error
        }
        try {
            val file = exact ?: if (exists(backup)) backup else original
            if (!exists(file)) return emptyMap()
            val descriptor = Os.open(file.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            val bytes = FileInputStream(descriptor).use { input ->
                val stat = Os.fstat(descriptor)
                check(OsConstants.S_ISREG(stat.st_mode) && stat.st_size in 1..MAX_PREFERENCES_INSPECTION_BYTES)
                input.readBytes().also { check(it.size.toLong() == stat.st_size) }
            }
            try {
                val parser = Xml.newPullParser()
                parser.setInput(bytes.inputStream(), "UTF-8")
                val result = mutableMapOf<String, String>()
                var rootSeen = false
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        if (parser.depth == 1) { check(parser.name == "map"); rootSeen = true }
                        else if (parser.depth == 2) {
                            val key = requireNotNull(parser.getAttributeValue(null, "name"))
                            check(key !in result)
                            check(parser.name == "string")
                            result[key] = parser.nextText()
                        }
                    }
                    parser.next()
                }
                check(rootSeen)
                result[WRAPPING_ALIAS]?.let { check(it.matches(Regex("[A-Za-z0-9_-]{1,120}"))) }
                return result
            } finally { bytes.fill(0) }
        } catch (error: KeyMaterialUnavailableException) { throw error }
        catch (error: Exception) {
            throw observedUnavailable("WRAPPED_METADATA_UNREADABLE")
        }
    }

    /** Scan every retained/partial generation and backup, including .bak, before creating an
     * alias. Unreadable inventory is never treated as an empty inventory. */
    private fun retainedAliasExists(alias: String): Boolean {
        fun inspect(directory: File, depth: Int): Boolean {
            if (depth > 12) throw KeyMaterialUnavailableException("Retained key inventory unavailable", transient = true)
            val children = directory.listFiles() ?: throw KeyMaterialUnavailableException("Retained key inventory unavailable", transient = true)
            for (file in children) {
                val stat = Os.lstat(file.path)
                if (OsConstants.S_ISLNK(stat.st_mode)) throw KeyMaterialUnavailableException("Retained key inventory unavailable", transient = true)
                if (OsConstants.S_ISDIR(stat.st_mode)) {
                    if (file.name == "shared_prefs") {
                        // A main XML and its .bak can each retain a different generation.
                        for (candidate in listOf(File(file, "key_alias.xml"), File(file, "key_alias.xml.bak"))) {
                            if (candidate.exists()) {
                                val recorded = wrappedKeyMetadata(exact = candidate)[WRAPPING_ALIAS] ?: KEY_ALIAS
                                if (recorded == alias) return true
                            }
                        }
                    }
                    if (inspect(file, depth + 1)) return true
                }
            }
            return false
        }
        return inspect(context.noBackupFilesDir, 0)
    }

    // A missing provider key is retried within this process and must recur in a later process
    // before it is reported as permanent. A read-only cold-start recheck never changes the file.
    private fun observedUnavailable(reason: String, record: Boolean = true): KeyMaterialUnavailableException {
        val file = File(context.noBackupFilesDir, "wallet-key-observation")
        val prior = runCatching { file.readLines() }.getOrNull()
        val repeated = prior?.size == 2 && prior[0] == reason && prior[1].matches(Regex("[a-f0-9-]{36}")) && prior[1] != processObservation
        if (record && !readOnlyInspection && !repeated) runCatching {
            val pending = File(file.parentFile, ".wallet-key-observation.tmp")
            java.io.FileOutputStream(pending).use { it.write("$reason\n$processObservation".toByteArray()); it.fd.sync() }
            Os.rename(pending.path, file.path)
            val directory = Os.open(context.noBackupFilesDir.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            try { Os.fsync(directory) } finally { Os.close(directory) }
        }
        return KeyMaterialUnavailableException("Wallet key unavailable: $reason", transient = !repeated)
    }

    /**
     * Probes the alias and runs [block] with it, retrying both together on a transient Keystore
     * failure (at most [retryDelaysMillis].size attempts). A non-transient failure is reported at
     * once.
     */
    private fun <T> withKeystoreRetry(allowCreation: Boolean = true, block: (KeystoreKeys) -> T): T {
        var lastFailure: KeyMaterialUnavailableException? = null
        val attempts = retryDelaysMillis.size.coerceAtLeast(1)
        for (attempt in 0 until attempts) {
            val delay = retryDelaysMillis.getOrElse(attempt) { 0L }
            if (delay > 0L) sleeper(delay)
            try {
                return block(ensureKeystoreKeys(attempts = 1, allowCreation = allowCreation))
            } catch (error: KeyMaterialUnavailableException) {
                lastFailure = error
                if (!error.transient) throw error
                // Probe the Keystore again on the next attempt.
                keystoreKeys = null
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun getPreferenceAesKey(): Key = withKeystoreRetry(block = ::loadOrCreatePreferenceAesKey)

    private fun loadOrCreatePreferenceAesKey(keys: KeystoreKeys): Key {
        unpersistedWrappedKey?.let { requireWrappedKeyPersisted(keyPreferencesForWrite(), it) }
        val encryptedKey = wrappedKeyMetadata()[SECRET_KEY].orEmpty()
        if (encryptedKey.isNotEmpty()) return unwrapAesKey(encryptedKey, keys)
        val preferences = keyPreferencesForWrite()
        synchronized(wrappedKeyCreationLock) {
            // Another caller may have created the wrapped key while this one waited. Never replace
            // a wrapped key: values already encrypted with it would become unreadable.
            val current = wrappedKeyMetadata()[SECRET_KEY].orEmpty()
            if (current.isNotEmpty()) {
                requireWrappedKeyPersisted(preferences, current)
                return unwrapAesKey(current, keys)
            }
            if (hasExistingWalletMaterial()) {
                throw observedUnavailable("MISSING_WRAPPED_KEY")
            }
            val keyGenerator = KeyGenerator.getInstance(AES)
            keyGenerator.init(AES_KEY_LENGTH, secureRandom)
            val secretKey: SecretKey = keyGenerator.generateKey()
            val encodedKey = secretKey.encoded
            val wrappedKey = try {
                encryptRsa(encodedKey, keys)
            } finally {
                encodedKey.fill(0)
            }
            if (wrappedKey.isEmpty()) {
                throw KeyMaterialUnavailableException(
                    "Unable to wrap the wallet encryption key",
                    transient = true,
                )
            }
            if (!preferences.edit().putString(SECRET_KEY, wrappedKey).commit()) {
                unpersistedWrappedKey = wrappedKey
                throw KeyMaterialUnavailableException(
                    "Unable to persist the wrapped wallet encryption key",
                    transient = true,
                )
            }
            return secretKey
        }
    }

    /**
     * Writes a wrapped key again when its first write failed. Until that succeeds nothing is
     * encrypted with it. Rewriting the same value changes nothing that was ever on disk.
     */
    private fun requireWrappedKeyPersisted(
        preferences: android.content.SharedPreferences,
        wrappedKey: String,
    ) {
        if (unpersistedWrappedKey != wrappedKey) return
        synchronized(wrappedKeyCreationLock) {
            if (unpersistedWrappedKey != wrappedKey) return
            if (!preferences.edit().putString(SECRET_KEY, wrappedKey).commit()) {
                throw KeyMaterialUnavailableException(
                    "Unable to persist the wrapped wallet encryption key",
                    transient = true,
                )
            }
            unpersistedWrappedKey = null
        }
    }

    /**
     * Proves that the wallet key material already stored on this device can be unlocked, without
     * creating, replacing or writing any key. A missing alias must be observed in another process
     * before it is classified as permanent. A wrapped key that provably cannot be unwrapped fails
     * with a non-transient [KeyMaterialUnavailableException] while wallet material exists (for example after a phone-to-phone transfer, which copies
     * app files but never Keystore keys); in that state new keys could not be stored either.
     */
    fun requireKeyMaterialReadable() {
        val metadata = wrappedKeyMetadata()
        if (
            metadata[SECRET_KEY].isNullOrEmpty() &&
            !hasExistingWalletMaterial()
        ) {
            // A new install: nothing is stored, so there is nothing to prove (and no Keystore
            // call that a busy Keystore could fail).
            return
        }
        withKeystoreRetry(allowCreation = false) { keys ->
            val wrappedKey = wrappedKeyMetadata()[SECRET_KEY].orEmpty()
            if (wrappedKey.isEmpty()) {
                throw observedUnavailable("MISSING_WRAPPED_KEY")
            }
            val key = unwrapAesKey(wrappedKey, keys)
            key.encoded?.fill(0)
            if (!readOnlyInspection) runCatching { File(context.noBackupFilesDir, "wallet-key-observation").delete() }
        }
    }

    private fun unwrapAesKey(encryptedKey: String, keys: KeystoreKeys): Key {
        val key = decryptRsa(encryptedKey, keys)
        return try {
            SecretKeySpec(key, 0, key.size, AES)
        } finally {
            key.fill(0)
        }
    }

    private fun createKeys(alias: String) {
        val startDate = Calendar.getInstance()
        val endDate = Calendar.getInstance()
        endDate.add(Calendar.YEAR, 25)

        val spec = KeyGenParameterSpec.Builder(
            alias,
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

    private fun encryptRsa(input: ByteArray, keys: KeystoreKeys): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keys.publicKey)
            Base64.toBase64String(cipher.doFinal(input))
        } catch (error: Exception) {
            keystoreKeys = null
            throw KeyMaterialUnavailableException(
                "Unable to wrap the wallet encryption key",
                error,
                transient = true,
            )
        }
    }

    private fun decryptRsa(encrypted: String, keys: KeystoreKeys): ByteArray {
        val wrapped = try {
            Base64.decode(encrypted)
        } catch (error: DecoderException) {
            throw KeyMaterialUnavailableException(
                "The wrapped wallet encryption key is malformed",
                error,
                transient = false,
            )
        }
        try {
            // A wrapped key of the wrong length can never be unwrapped by this alias. Checking it
            // here keeps the Keystore's own length error out of the transient classification.
            val modulusBytes = (keys.publicKey as? RSAPublicKey)?.modulus?.bitLength()
                ?.let { bits -> (bits + 7) / 8 }
            if (modulusBytes != null && wrapped.size != modulusBytes) {
                throw KeyMaterialUnavailableException(
                    "The wrapped wallet encryption key does not match the Keystore alias",
                    transient = false,
                )
            }
            return try {
                rsaDecryptor(keys.privateKey, wrapped)
            } catch (error: Exception) {
                val permanent = isPermanentUnwrapFailure(error)
                val knownTemporary = generateSequence<Throwable>(error) { it.cause }.take(8).any {
                    it.javaClass.name == "android.security.KeyStoreException" || it is java.security.ProviderException
                }
                if (!permanent && !knownTemporary) throw observedUnavailable("UNWRAP_UNCLASSIFIED")
                throw KeyMaterialUnavailableException(
                    "Unable to unwrap the existing wallet encryption key",
                    error,
                    transient = !permanent,
                )
            }
        } finally {
            wrapped.fill(0)
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
        val databaseStat = try { Os.lstat(database.path) } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) null else throw KeyMaterialUnavailableException("Wallet database cannot be inspected", error, transient = true)
        }
        if (databaseStat != null && (!OsConstants.S_ISREG(databaseStat.st_mode) || databaseStat.st_size > 0L)) {
            val hasAccounts = runCatching {
                // Android's default error handler deletes a database it considers corrupt, even
                // for a read-only open. An unreadable wallet database is recovery material.
                SQLiteDatabase.openDatabase(
                    database.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ) { _ ->
                    throw SQLiteDatabaseCorruptException("WALLET_DB_FAIL_CLOSED")
                }.use { sqlite ->
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
            dataStore.walletPathExists() -> dataStore
            sharedPreferencesBackup.walletPathExists() -> sharedPreferencesBackup
            else -> sharedPreferences
        }
        return authoritativePreferences.containsWalletPreferenceMarker()
    }

    private fun File.walletPathExists(): Boolean = try {
        Os.lstat(path)
        true
    } catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) false else
            throw KeyMaterialUnavailableException("Wallet preferences cannot be inspected", error, transient = true)
    }

    private fun File.containsWalletPreferenceMarker(): Boolean {
        if (!walletPathExists()) return false
        return try {
            val descriptor = Os.open(path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            FileInputStream(descriptor).use { input ->
                val stat = Os.fstat(descriptor)
                if (!OsConstants.S_ISREG(stat.st_mode)) throw KeyMaterialUnavailableException("Wallet preferences cannot be inspected", transient = true)
                if (stat.st_size > MAX_PREFERENCES_INSPECTION_BYTES) return true
                val value = input.readBytes()
                try {
                    check(value.size.toLong() == stat.st_size)
                    WALLET_PREFERENCE_MARKERS.any { marker ->
                        value.indexOf(marker.toByteArray(Charsets.UTF_8)) >= 0
                    }
                } finally { value.fill(0) }
            }
        } catch (error: KeyMaterialUnavailableException) { throw error }
        catch (error: Exception) {
            throw KeyMaterialUnavailableException("Wallet preferences cannot be inspected", error, transient = true)
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

/**
 * The Android Keystore alias or the wrapped AES key cannot be used.
 *
 * [transient] is true when the Keystore itself failed (for example it was busy or still starting)
 * and a later attempt may succeed. Callers must never persist a transient failure as a recovery
 * state. A non-transient failure means the alias is missing or no longer matches the wrapped key.
 */
class KeyMaterialUnavailableException(
    message: String,
    cause: Throwable? = null,
    val transient: Boolean = false,
) : IllegalStateException(message, cause)

class WalletEncryptionException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class WalletDecryptionException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
