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
        assertFalse((failure as KeyMaterialUnavailableException).transient)
        assertFalse(WalletStorageFailures.isTransient(failure))
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
        assertFalse((failure as KeyMaterialUnavailableException).transient)
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
        assertFalse((failure as KeyMaterialUnavailableException).transient)
        assertFalse(androidKeyStore().containsAlias(KEY_ALIAS))
        assertEquals(wrapped, wrappedKeyPreferences().getString(SECRET_KEY, null))
        assertTrue(encrypted.isNotEmpty())
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
        androidKeyStore().deleteEntry(KEY_ALIAS)
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
