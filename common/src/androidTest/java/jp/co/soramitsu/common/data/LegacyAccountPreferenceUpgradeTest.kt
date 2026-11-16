package jp.co.soramitsu.common.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
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

    // SORA 2.4.x copied the release-era values under the address before it saved the account and
    // selection; a process stop in between left those copies, and every later start refused to
    // finish the promotion (LEGACY_ACCOUNT_SCOPED_CREDENTIAL_CONFLICT). Copies of the same address
    // are accepted now and kept as they are; another address or a watch-only marker still refuse.
    @Test fun interruptedReleaseCopiesOfTheSameAddressFinishThePromotion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.packageName.endsWith(".test"))
        // Its own file and DataStore: the process-wide one may already serve the test above.
        val file = File(context.filesDir, "datastore/legacy-promotion-copies.preferences_pb")
        file.delete()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = SoraPreferences(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        )
        try {
            promoteWithCopies(preferences)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            file.delete()
        }
    }

    private suspend fun promoteWithCopies(preferences: SoraPreferences) {
        val address = "verified-legacy-address"
        val values = listOf("prefs_priv_key", "prefs_pub_key", "prefs_key_nonce", "prefs_mnemonic")
            .flatMap { prefix -> listOf(prefix to "$prefix-ciphertext", prefix + address to "$prefix-copy") }
            .toMap()
        values.forEach { (key, value) -> preferences.putString(key, value) }
        preferences.putBoolean("needs_migration", true)
        // The interrupted run already copied this flag; the copy is kept, not overwritten.
        preferences.putBoolean("needs_migration$address", false)

        preferences.completeLegacyAccountUpgrade(address)

        assertEquals(address, preferences.getString("cur_account_address"))
        assertEquals(address, preferences.getString("prefs_address_pure"))
        assertEquals(false, preferences.getBoolean("needs_migration$address", true))
        values.forEach { (key, value) -> assertEquals(value, preferences.getString(key)) }

        // Values of another address still mean this is not the single release-era wallet.
        preferences.putString("cur_account_address", "")
        preferences.putString("prefs_mnemonic" + "other-address", "other-copy")
        assertTrue(runCatching { preferences.completeLegacyAccountUpgrade(address) }.isFailure)
        preferences.clear("prefs_mnemonic" + "other-address")
        // A watch-only marker contradicts the signing keys the caller proved.
        preferences.putBoolean("wallet.watchOnly.$address", true)
        assertTrue(runCatching { preferences.completeLegacyAccountUpgrade(address) }.isFailure)
        assertEquals("", preferences.getString("cur_account_address"))
    }
}
