@file:Suppress("DEPRECATION")

package jp.co.soramitsu.sora.ux

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.osipxd.security.crypto.createEncrypted
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedPreferencesCompatibilityTest {
    @Test fun retainedEncryptedPreferencesReadUpdateAndReopenWithoutPlaintextStorage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "ux-retained-${System.nanoTime()}.preferences_pb")
        val key = stringPreferencesKey("ux_compat")
        // Legacy PreferencesProto: Preferences map field 1, entry key 1/value 2, string value 5.
        // Feed the old wire representation through the same EncryptedFile scheme used by PayWings.
        fun field(tag: Int, bytes: ByteArray) = byteArrayOf(tag.toByte(), bytes.size.toByte()) + bytes
        val retained = "retained-sample-value"
        val legacyBytes = field(10, field(10, "ux_compat".toByteArray()) + field(18, field(42, retained.toByteArray())))
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        fun encryptedFile() = EncryptedFile.Builder(file, context, masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
        encryptedFile().openFileOutput().use { it.write(legacyBytes) }
        val firstJob = SupervisorJob()
        val secondJob = SupervisorJob()
        try {
            val first = PreferenceDataStoreFactory.createEncrypted(scope = CoroutineScope(Dispatchers.IO + firstJob)) { encryptedFile() }
            assertEquals(retained, first.data.first()[key])
            first.edit { it[key] = "updated-sample-value" }
            firstJob.cancelAndJoin()
            val second = PreferenceDataStoreFactory.createEncrypted(scope = CoroutineScope(Dispatchers.IO + secondJob)) { encryptedFile() }
            assertEquals("updated-sample-value", second.data.first()[key])
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("updated-sample-value"))
        } finally {
            firstJob.cancelAndJoin(); secondJob.cancelAndJoin(); file.delete()
        }
    }
}
