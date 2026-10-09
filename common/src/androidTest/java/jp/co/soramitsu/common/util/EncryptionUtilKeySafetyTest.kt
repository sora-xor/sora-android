package jp.co.soramitsu.common.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.security.KeyStoreException
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
    }
}
