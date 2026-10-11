package jp.co.soramitsu.common.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * "Remove all accounts" on the real preference store (a DataStore file): the key sets that stay
 * are marked, in the same edit, as needing their own recovery phrase. No key value of a kept set
 * is changed, moved or removed.
 */
class SoraPreferencesRemoveAllTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun preferences(): SoraPreferences = SoraPreferences(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(folder.root, "sora_prefs_datastore.preferences_pb")
        }
    )

    private val credentialPrefixes = WalletPreferenceKeys.encryptedCredentialPrefixes

    private suspend fun SoraPreferences.storeKeySet(suffix: String, tag: String): Map<String, String> {
        val values = credentialPrefixes.associate { prefix -> prefix + suffix to "$tag-$prefix-ciphertext" }
        values.forEach { (key, value) -> putString(key, value) }
        return values
    }

    private suspend fun SoraPreferences.removeAll(vararg accounts: String) {
        val stringFields = accounts.flatMap(WalletPreferenceKeys::scopedStringKeys).toSet()
        val booleanFields = accounts.flatMap(WalletPreferenceKeys::scopedBooleanKeys).toSet()
        val hashes =
            previewWalletDeletion(stringFields, booleanFields, selectedAddress = "", clearAll = true)
        commitWalletDeletion(stringFields, booleanFields, "", true, hashes.before, hashes.after)
        assertTrue(walletDeletionPreferencesMatch(stringFields, booleanFields, "", true, hashes.after))
        // The journal applies the same edit again after a crash: it changes nothing.
        commitWalletDeletion(stringFields, booleanFields, "", true, hashes.before, hashes.after)
        assertTrue(walletDeletionPreferencesMatch(stringFields, booleanFields, "", true, hashes.after))
    }

    @Test
    fun `key sets that stay after Remove all accounts are marked and keep every byte`() = runBlocking {
        val prefs = preferences()
        prefs.storeKeySet("cnAccount", "account")
        val leftover = prefs.storeKeySet("cnLeftover", "leftover")
        val releaseEra = prefs.storeKeySet("", "release-era")
        prefs.putString(WalletPreferenceKeys.LEGACY_ADDRESS, "cnForgottenIn38x")
        prefs.putString(WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS, "cnAccount")
        prefs.putString(WalletPreferenceKeys.REGISTRATION_STATE, "REGISTRATION_FINISHED")
        prefs.putString(WalletPreferenceKeys.PIN_CODE, "pin-ciphertext")
        prefs.putString("some_setting", "value")
        assertEquals(emptySet<String>(), prefs.retainedKeySetsNeedingPhrase())

        prefs.removeAll("cnAccount")

        // The removed account, the PIN and the settings are gone.
        credentialPrefixes.forEach { assertEquals("", prefs.getString(it + "cnAccount")) }
        assertEquals("", prefs.getString(WalletPreferenceKeys.PIN_CODE))
        assertEquals("", prefs.getString(WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS))
        assertEquals("", prefs.getString("some_setting"))
        // What no account used stays, byte for byte.
        (leftover + releaseEra).forEach { (key, value) -> assertEquals(value, prefs.getString(key)) }
        assertEquals("cnForgottenIn38x", prefs.getString(WalletPreferenceKeys.LEGACY_ADDRESS))
        // And from now on only its own recovery phrase brings it back.
        assertEquals(
            setOf("cnLeftover", WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID),
            prefs.retainedKeySetsNeedingPhrase(),
        )
    }

    @Test
    fun `a later Remove all accounts keeps the earlier marks`() = runBlocking {
        val prefs = preferences()
        prefs.storeKeySet("cnFirst", "first")
        prefs.storeKeySet("cnLeftover", "leftover")
        prefs.removeAll("cnFirst")
        assertEquals(setOf("cnLeftover"), prefs.retainedKeySetsNeedingPhrase())

        // The next person sets the phone up; another key set is left behind by then.
        prefs.storeKeySet("cnSecond", "second")
        prefs.storeKeySet("cnLater", "later")
        prefs.removeAll("cnSecond")

        assertEquals(setOf("cnLeftover", "cnLater"), prefs.retainedKeySetsNeedingPhrase())
    }

    @Test
    fun `nothing is marked when nothing stays`() = runBlocking {
        val prefs = preferences()
        prefs.storeKeySet("cnOnly", "only")
        prefs.putString(WalletPreferenceKeys.PIN_CODE, "pin-ciphertext")

        prefs.removeAll("cnOnly")

        assertEquals(emptySet<String>(), prefs.retainedKeySetsNeedingPhrase())
        assertEquals("", prefs.getString(WalletPreferenceKeys.RETAINED_NEEDS_PHRASE))
    }

    @Test
    fun `removing one of several accounts marks nothing - the PIN still protects what stays`() = runBlocking {
        val prefs = preferences()
        prefs.storeKeySet("cnRemoved", "removed")
        val kept = prefs.storeKeySet("cnKept", "kept")
        val leftover = prefs.storeKeySet("cnLeftover", "leftover")
        prefs.putString(WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS, "cnRemoved")
        prefs.putString(WalletPreferenceKeys.PIN_CODE, "pin-ciphertext")
        val stringFields = WalletPreferenceKeys.scopedStringKeys("cnRemoved").toSet()
        val booleanFields = WalletPreferenceKeys.scopedBooleanKeys("cnRemoved").toSet()
        val hashes = prefs.previewWalletDeletion(stringFields, booleanFields, "cnKept", clearAll = false)

        prefs.commitWalletDeletion(stringFields, booleanFields, "cnKept", false, hashes.before, hashes.after)

        assertEquals(emptySet<String>(), prefs.retainedKeySetsNeedingPhrase())
        assertEquals("pin-ciphertext", prefs.getString(WalletPreferenceKeys.PIN_CODE))
        (kept + leftover).forEach { (key, value) -> assertEquals(value, prefs.getString(key)) }
    }

    @Test
    fun `key sets that are not an account's are marked without touching a key value`() = runBlocking {
        // What UserRepositoryImpl.savePin asks for when a PIN is set for the first time.
        val prefs = preferences()
        val account = prefs.storeKeySet("cnAccount", "account")
        val abandoned = prefs.storeKeySet("cnAbandonedCreate", "abandoned")
        val releaseEra = prefs.storeKeySet("", "release-era")

        prefs.markKeySetsNeedingPhrase(exceptIds = setOf("cnAccount"))

        assertEquals(
            setOf("cnAbandonedCreate", WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID),
            prefs.retainedKeySetsNeedingPhrase(),
        )
        (account + abandoned + releaseEra).forEach { (key, value) -> assertEquals(value, prefs.getString(key)) }

        // Earlier marks stay; the release-era set of a listed account is left out when asked.
        prefs.storeKeySet("cnLater", "later")
        prefs.markKeySetsNeedingPhrase(
            exceptIds = setOf("cnAccount", "cnLater", WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID),
        )
        assertEquals(
            setOf("cnAbandonedCreate", WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID),
            prefs.retainedKeySetsNeedingPhrase(),
        )
    }

    @Test
    fun `with nothing to mark nothing is written`() = runBlocking {
        val prefs = preferences()
        prefs.storeKeySet("cnAccount", "account")

        prefs.markKeySetsNeedingPhrase(exceptIds = setOf("cnAccount"))

        assertEquals("", prefs.getString(WalletPreferenceKeys.RETAINED_NEEDS_PHRASE))
    }
}
