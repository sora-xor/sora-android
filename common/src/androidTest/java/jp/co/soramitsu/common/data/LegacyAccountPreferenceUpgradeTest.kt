package jp.co.soramitsu.common.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyAccountPreferenceUpgradeTest {
    @Test fun legacyMetadataPublicationRetainsCiphertextAndFlagsAndRejectsOwnerConflicts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // This isolated test package runs once per instrumentation process; never erase a live
        // application's preferences or construct parallel DataStores for the same file.
        assertTrue(context.packageName.endsWith(".test"))
        context.getSharedPreferences("sora_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "datastore/sora_prefs_datastore.preferences_pb").delete()
        val preferences = SoraPreferences(context)
        val ciphertexts = mapOf(
            "prefs_priv_key" to "retained-private-ciphertext",
            "prefs_pub_key" to "retained-public-ciphertext",
            "prefs_key_nonce" to "retained-nonce-ciphertext",
            "prefs_mnemonic" to "retained-mnemonic-ciphertext",
            "prefs_seed" to "retained-seed-ciphertext",
        )
        ciphertexts.forEach { (key, value) -> preferences.putString(key, value) }
        preferences.putBoolean("needs_migration", true)
        preferences.putBoolean("is_migration_fetched", false)
        preferences.completeLegacyAccountUpgrade("verified-legacy-address")
        preferences.completeLegacyAccountUpgrade("verified-legacy-address")
        assertEquals("verified-legacy-address", preferences.getString("prefs_address_pure"))
        assertEquals("verified-legacy-address", preferences.getString("cur_account_address"))
        assertTrue(preferences.getBoolean("needs_migrationverified-legacy-address"))
        assertEquals(false, preferences.getBoolean("is_migration_fetchedverified-legacy-address", true))
        assertTrue(runCatching { preferences.completeLegacyAccountUpgrade("different-address") }.isFailure)
        assertEquals("verified-legacy-address", preferences.getString("cur_account_address"))
        ciphertexts.forEach { (key, value) -> assertEquals(value, preferences.getString(key)) }
    }
}
