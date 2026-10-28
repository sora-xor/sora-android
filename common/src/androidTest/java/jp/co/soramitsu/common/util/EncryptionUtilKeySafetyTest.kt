package jp.co.soramitsu.common.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device checks for the Keystore rules in EncryptionUtil: a missing alias is never replaced while
 * wallet material may exist, an unreadable wallet database is never deleted while that is checked,
 * and a busy Keystore is retried instead of being remembered as a permanent failure.
 *
 * Runs only in the isolated library test package, whose Keystore namespace and files belong to the
 * test APK and never to an installed wallet.
 */
@RunWith(AndroidJUnit4::class)
class EncryptionUtilKeySafetyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun isolate() {
        assertTrue(context.packageName.endsWith(".test"))
        clearSandbox()
    }

    @After
    fun cleanUp() {
        clearSandbox()
    }

    @Test
    fun unreadableWalletDatabaseWithoutAliasIsKeptAndNoKeyIsCreated() {
        val database = context.getDatabasePath(DATABASE_NAME)
        assertTrue(database.parentFile!!.isDirectory || database.parentFile!!.mkdirs())
        val recoveryBytes = ByteArray(4_096) { index -> (index * 31 + 17).toByte() }
        database.writeBytes(recoveryBytes)

        val util = EncryptionUtil(context)
        val failure = runCatching { util.encrypt("must-not-be-encrypted") }.exceptionOrNull()

        assertTrue(failure is KeyMaterialUnavailableException)
        assertTrue((failure as KeyMaterialUnavailableException).transient)
        assertTrue(WalletStorageFailures.isTransient(failure))
        assertTrue(database.isFile)
        assertArrayEquals(recoveryBytes, database.readBytes())
        assertFalse(androidKeyStore().containsAlias(KEY_ALIAS))
        assertEquals(null, wrappedKeyPreferences().getString(SECRET_KEY, null))
    }

    @Test
    fun wrappedKeyWithoutAliasIsNeverReplaced() {
        assertTrue(
            wrappedKeyPreferences().edit()
                .putString(SECRET_KEY, "retained-wrapped-wallet-key")
                .commit()
        )

        val failure = runCatching { EncryptionUtil(context).encrypt("value") }
            .exceptionOrNull()

        assertTrue(failure is KeyMaterialUnavailableException)
        assertTrue((failure as KeyMaterialUnavailableException).transient)
        assertFalse(androidKeyStore().containsAlias(KEY_ALIAS))
        assertEquals(
            "retained-wrapped-wallet-key",
            wrappedKeyPreferences().getString(SECRET_KEY, null),
        )
    }

    @Test
    fun busyKeystoreIsRetriedAndNeverRemembered() {
        var remainingFailures = 5
        var probes = 0
        val util = EncryptionUtil(
            context = context,
            keyStoreFactory = {
                probes += 1
                if (remainingFailures > 0) {
                    remainingFailures -= 1
                    throw KeyStoreException("Keystore is busy")
                }
                KeyStore.getInstance(KEY_STORE_PROVIDER)
            },
            retryDelaysMillis = listOf(0L, 0L, 0L),
            sleeper = {},
        )

        // The constructor warm-up probes once; one call retries three times before reporting.
        val failure = runCatching { util.encrypt("value") }.exceptionOrNull()
        assertTrue(failure is KeyMaterialUnavailableException)
        assertTrue((failure as KeyMaterialUnavailableException).transient)
        assertTrue(WalletStorageFailures.isTransient(failure))
        assertEquals(4, probes)

        // The next call probes again (no sticky failure) and succeeds once the Keystore answers.
        val encrypted = util.encrypt("value")
        assertEquals("value", util.decrypt(encrypted))
        assertTrue(androidKeyStore().containsAlias(KEY_ALIAS))

        // A second instance reads the same alias and wrapped key.
        assertEquals("value", EncryptionUtil(context).decrypt(encrypted))
    }

    @Test
    fun keystoreFailureWhileUnwrappingIsRetriedInTheSameCall() {
        val encrypted = EncryptionUtil(context).encrypt("value")
        var unwrapFailures = 2
        val delays = mutableListOf<Long>()
        val util = EncryptionUtil(
            context = context,
            sleeper = { delays += it },
            rsaDecryptor = { key, wrapped ->
                if (unwrapFailures > 0) {
                    unwrapFailures -= 1
                    // What the Keystore reports for a busy backend or a pruned operation handle.
                    throw keystoreOperationFailure()
                }
                Cipher.getInstance(RSA_TRANSFORMATION).run {
                    init(Cipher.DECRYPT_MODE, key)
                    doFinal(wrapped)
                }
            },
        )

        assertEquals("value", util.decrypt(encrypted))
        assertEquals(0, unwrapFailures)
        assertEquals(listOf(150L, 500L), delays)
    }

    @Test
    fun persistentKeystoreFailureWhileUnwrappingIsTransientAndNeverRemembered() {
        val encrypted = EncryptionUtil(context).encrypt("value")
        var failing = true
        var attempts = 0
        val util = EncryptionUtil(
            context = context,
            retryDelaysMillis = listOf(0L, 0L, 0L),
            sleeper = {},
            rsaDecryptor = { key, wrapped ->
                attempts += 1
                if (failing) throw keystoreOperationFailure()
                Cipher.getInstance(RSA_TRANSFORMATION).run {
                    init(Cipher.DECRYPT_MODE, key)
                    doFinal(wrapped)
                }
            },
        )

        val failure = runCatching { util.decrypt(encrypted) }.exceptionOrNull()
        assertTrue(failure is KeyMaterialUnavailableException)
        assertTrue((failure as KeyMaterialUnavailableException).transient)
        assertTrue(WalletStorageFailures.isTransient(failure))
        assertEquals(3, attempts)

        failing = false
        assertEquals("value", util.decrypt(encrypted))
    }

    @Test
    fun wrappedKeyThatDoesNotBelongToTheAliasIsPermanentAndNotRetried() {
        val encrypted = EncryptionUtil(context).encrypt("value")
        var attempts = 0
        val util = EncryptionUtil(
            context = context,
            sleeper = {},
            rsaDecryptor = { _, _ ->
                attempts += 1
                throw BadPaddingException("wrapped key belongs to another alias")
            },
        )

        val failure = runCatching { util.decrypt(encrypted) }.exceptionOrNull()
        assertTrue(failure is KeyMaterialUnavailableException)
        assertFalse((failure as KeyMaterialUnavailableException).transient)
        assertEquals(1, attempts)
    }

    @Test
    fun unwrapFailureClassification() {
        assertFalse(EncryptionUtil.isPermanentUnwrapFailure(keystoreOperationFailure()))
        assertFalse(EncryptionUtil.isPermanentUnwrapFailure(java.security.ProviderException("busy")))
        assertFalse(
            EncryptionUtil.isPermanentUnwrapFailure(
                java.security.InvalidKeyException("Keystore operation failed")
            )
        )
        // The pre-Android 12 Keystore reports a wrong input length without a cause.
        assertTrue(EncryptionUtil.isPermanentUnwrapFailure(IllegalBlockSizeException()))
        assertTrue(EncryptionUtil.isPermanentUnwrapFailure(BadPaddingException()))
        assertTrue(
            EncryptionUtil.isPermanentUnwrapFailure(
                android.security.keystore.KeyPermanentlyInvalidatedException()
            )
        )
    }

    @Test
    fun keyMaterialProbeOnANewInstallTouchesNothing() {
        // Nothing stored: nothing to prove, and no alias or wrapped key is created by the probe.
        EncryptionUtil(context, keyStoreFactory = { throw KeyStoreException("busy") })
            .requireKeyMaterialReadable()
        assertEquals(null, wrappedKeyPreferences().getString(SECRET_KEY, null))
    }

    @Test
    fun keyMaterialProbeReportsAMissingAliasForStoredDataWithoutCreatingOne() {
        val encrypted = EncryptionUtil(context).encrypt("value")
        val wrapped = wrappedKeyPreferences().getString(SECRET_KEY, null)
        // A phone-to-phone transfer copies the files but never the Keystore key.
        androidKeyStore().deleteEntry(KEY_ALIAS)

        val failure = runCatching { EncryptionUtil(context).requireKeyMaterialReadable() }
            .exceptionOrNull()

        assertTrue(failure is KeyMaterialUnavailableException)
        assertTrue((failure as KeyMaterialUnavailableException).transient)
        assertFalse(androidKeyStore().containsAlias(KEY_ALIAS))
        assertEquals(wrapped, wrappedKeyPreferences().getString(SECRET_KEY, null))
        assertTrue(encrypted.isNotEmpty())
    }

    @Test
    fun preservedGenerationsUseDifferentAliasesAndBothCiphertextsRemainReadable() {
        val old = EncryptionUtil(context).encrypt("original wallet")
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs/key_alias.xml")
        val oldBytes = prefs.readBytes()
        val preserved = File(context.noBackupFilesDir, "wallet-quarantine-test/shared_prefs/key_alias.xml")
        preserved.parentFile!!.mkdirs()
        preserved.writeBytes(oldBytes)
        assertTrue(wrappedKeyPreferences().edit().clear().commit())

        val fresh = EncryptionUtil(context).encrypt("new wallet")
        val freshAlias = wrappedKeyPreferences().getString("wrapping_alias", null)
        assertTrue(freshAlias?.startsWith("sora_wallet_") == true)
        assertTrue(androidKeyStore().containsAlias(KEY_ALIAS))
        assertEquals("new wallet", EncryptionUtil(context).decrypt(fresh))
        val newBytes = prefs.readBytes()

        // A new process reads the original generation's on-disk metadata, even if the
        // SharedPreferences object cached a different generation in this test process.
        prefs.writeBytes(oldBytes)
        assertEquals("original wallet", EncryptionUtil(context).decrypt(old))
        prefs.writeBytes(newBytes)
        assertEquals("new wallet", EncryptionUtil(context).decrypt(fresh))
    }

    @Test
    fun cachedEmptyWrappedKeyPreferencesNeverCauseAReplacementKey() {
        val encrypted = EncryptionUtil(context).encrypt("retained")
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs/key_alias.xml")
        val bytes = prefs.readBytes()
        assertTrue(wrappedKeyPreferences().edit().clear().commit())
        prefs.writeBytes(bytes)
        assertEquals("retained", EncryptionUtil(context).decrypt(encrypted))
        assertArrayEquals(bytes, prefs.readBytes())
    }

    @Test
    fun missingAliasMustBeObservedInAnotherProcessBeforeItIsPermanent() {
        EncryptionUtil(context).encrypt("value")
        androidKeyStore().deleteEntry(KEY_ALIAS)
        val first = runCatching { EncryptionUtil(context).requireKeyMaterialReadable() }.exceptionOrNull()
        assertTrue((first as KeyMaterialUnavailableException).transient)
        File(context.noBackupFilesDir, "wallet-key-observation").writeText("MISSING_ALIAS\n00000000-0000-0000-0000-000000000001")
        val before = File(context.noBackupFilesDir, "wallet-key-observation").readBytes()
        val repeated = runCatching { EncryptionUtil(context, readOnlyInspection = true).requireKeyMaterialReadable() }.exceptionOrNull()
        assertFalse((repeated as KeyMaterialUnavailableException).transient)
        assertArrayEquals(before, File(context.noBackupFilesDir, "wallet-key-observation").readBytes())
        assertFalse(androidKeyStore().containsAlias(KEY_ALIAS))
    }

    @Test
    fun nullProviderKeyIsRetriedAndOriginalCiphertextRecoversWithoutReplacingAnyKey() {
        val encrypted = EncryptionUtil(context).encrypt("original signing seed")
        val originalPublic = androidKeyStore().getCertificate(KEY_ALIAS).publicKey.encoded
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs/key_alias.xml")
        val originalMetadata = prefs.readBytes()
        var remainingNulls = 4 // constructor probe plus all three attempts in the first call
        val store = nullReturningStore { remainingNulls-- > 0 }
        val util = EncryptionUtil(context, keyStoreFactory = { store }, retryDelaysMillis = listOf(0L, 0L, 0L), sleeper = {})
        val failure = runCatching { util.decrypt(encrypted) }.exceptionOrNull()
        assertTrue((failure as KeyMaterialUnavailableException).transient)
        assertArrayEquals(originalMetadata, prefs.readBytes())
        assertArrayEquals(originalPublic, androidKeyStore().getCertificate(KEY_ALIAS).publicKey.encoded)
        assertEquals("original signing seed", util.decrypt(encrypted))
        assertArrayEquals(originalMetadata, prefs.readBytes())
    }

    @Test
    fun malformedWrappedMetadataCannotBeTreatedAsEmptyCachedPreferences() {
        val file = File(context.applicationInfo.dataDir, "shared_prefs/key_alias.xml")
        file.parentFile!!.mkdirs()
        val original = "<map><int name=\"secret_key\" value=\"123\" /></map>".toByteArray()
        file.writeBytes(original)
        val failure = runCatching { EncryptionUtil(context).encrypt("new") }.exceptionOrNull()
        assertTrue((failure as KeyMaterialUnavailableException).transient)
        assertArrayEquals(original, file.readBytes())
        assertFalse(androidKeyStore().containsAlias(KEY_ALIAS))
    }

    @Test
    fun legacyCallerKeysRoundTripUtf8WithFreshIvsWithoutCreatingWalletKeys() {
        val util = EncryptionUtil(context)
        val key = ByteArray(32) { (it + 1).toByte() }
        val original = key.copyOf()
        for (clear in listOf("", "ascii", "日本語 🔑 \u0000 retained text")) {
            val first = util.encrypt(key, clear)
            val second = util.encrypt(key, clear)
            assertFalse(first == second)
            assertEquals(clear, util.decrypt(key, first))
            assertEquals(clear, util.decrypt(key, second))
        }
        assertArrayEquals(original, key)
        assertEquals(null, wrappedKeyPreferences().getString(SECRET_KEY, null))
        assertFalse(androidKeyStore().containsAlias(KEY_ALIAS))
    }

    @Test
    fun authenticatedEnvelopeRejectsTruncationMalformedEncodingAndTampering() {
        val util = EncryptionUtil(context)
        val encrypted = util.encrypt("synthetic fixture secret")
        val separator = encrypted.lastIndexOf(':')
        assertTrue(separator >= 0)
        val prefix = encrypted.substring(0, separator + 1)
        val raw = org.bouncycastle.util.encoders.Base64.decode(encrypted.substring(separator + 1))
        for (bad in listOf(prefix, prefix + "!invalid!", prefix + org.bouncycastle.util.encoders.Base64.toBase64String(ByteArray(12)),
            prefix + org.bouncycastle.util.encoders.Base64.toBase64String(raw.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }))) {
            assertTrue(runCatching { util.decrypt(bad) }.exceptionOrNull() is WalletDecryptionException)
        }
        assertEquals("synthetic fixture secret", util.decrypt(encrypted))
    }

    @Test
    fun legacyTruncatedAndWrongKeyEnvelopesFailWithoutReplacingCallerKey() {
        val util = EncryptionUtil(context)
        val key = ByteArray(32) { 7 }
        for (raw in listOf(ByteArray(0), ByteArray(16), ByteArray(17))) {
            assertTrue(runCatching { util.decrypt(key, org.bouncycastle.util.encoders.Base64.toBase64String(raw)) }.exceptionOrNull() is WalletDecryptionException)
        }
        val encrypted = util.encrypt(key, "fixture")
        assertTrue(runCatching { util.decrypt(ByteArray(32) { 8 }, encrypted) }.exceptionOrNull() is WalletDecryptionException)
        assertArrayEquals(ByteArray(32) { 7 }, key)
    }

    private fun nullReturningStore(returnNull: () -> Boolean): KeyStore {
        val delegate = androidKeyStore()
        val spi = object : java.security.KeyStoreSpi() {
            override fun engineGetKey(alias: String, password: CharArray?) = if (returnNull()) null else delegate.getKey(alias, password)
            override fun engineGetCertificate(alias: String) = delegate.getCertificate(alias)
            override fun engineGetCertificateChain(alias: String) = delegate.getCertificateChain(alias)
            override fun engineGetCreationDate(alias: String) = delegate.getCreationDate(alias)
            override fun engineAliases() = delegate.aliases()
            override fun engineContainsAlias(alias: String) = delegate.containsAlias(alias)
            override fun engineSize() = delegate.size()
            override fun engineIsKeyEntry(alias: String) = delegate.isKeyEntry(alias)
            override fun engineIsCertificateEntry(alias: String) = delegate.isCertificateEntry(alias)
            override fun engineGetCertificateAlias(certificate: java.security.cert.Certificate) = delegate.getCertificateAlias(certificate)
            override fun engineLoad(stream: java.io.InputStream?, password: CharArray?) = Unit
            override fun engineStore(stream: java.io.OutputStream?, password: CharArray?) = error("read-only test store")
            override fun engineDeleteEntry(alias: String) = error("read-only test store")
            override fun engineSetCertificateEntry(alias: String, certificate: java.security.cert.Certificate) = error("read-only test store")
            override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<java.security.cert.Certificate>?) = error("read-only test store")
            override fun engineSetKeyEntry(alias: String, key: java.security.Key, password: CharArray?, chain: Array<java.security.cert.Certificate>?) = error("read-only test store")
        }
        return object : KeyStore(spi, object : java.security.Provider("Faulting", 1.0, "Read-only key fault fixture") {}, "Faulting") {}
    }

    /**
     * The Keystore wraps operation failures in IllegalBlockSizeException with an
     * android.security.KeyStoreException cause, a class apps cannot construct. A real instance is
     * allocated without its constructor so the classification sees exactly that class.
     */
    private fun keystoreOperationFailure(): IllegalBlockSizeException {
        val cause = runCatching {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
                .get(null)
            unsafeClass.getMethod("allocateInstance", Class::class.java)
                .invoke(unsafe, android.security.KeyStoreException::class.java) as Throwable
        }.getOrElse { java.security.ProviderException("Keystore operation failed") }
        return IllegalBlockSizeException().apply { initCause(cause) }
    }

    private fun androidKeyStore(): KeyStore =
        KeyStore.getInstance(KEY_STORE_PROVIDER).apply { load(null) }

    private fun wrappedKeyPreferences() =
        context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE)

    private fun clearSandbox() {
        val store = androidKeyStore()
        store.aliases().toList().filter { it == KEY_ALIAS || it.startsWith("sora_wallet_") }.forEach(store::deleteEntry)
        context.noBackupFilesDir.listFiles().orEmpty().filter { it.name.startsWith("wallet-") || it.name.startsWith(".wallet-") }.forEach { it.deleteRecursively() }
        assertTrue(wrappedKeyPreferences().edit().clear().commit())
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        )
        val database = context.getDatabasePath(DATABASE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File("${database.path}$it").delete() }
        File(context.filesDir, "datastore/sora_prefs_datastore.preferences_pb").delete()
        File(context.applicationInfo.dataDir, "shared_prefs/$PREFERENCES_NAME.xml.bak").delete()
        File(context.applicationInfo.dataDir, "shared_prefs/key_alias.xml").delete()
        File(context.applicationInfo.dataDir, "shared_prefs/key_alias.xml.bak").delete()
    }

    private companion object {
        const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "key_alias"
        const val SECRET_KEY = "secret_key"
        const val DATABASE_NAME = "app.db"
        const val PREFERENCES_NAME = "sora_prefs"
        const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    }
}
