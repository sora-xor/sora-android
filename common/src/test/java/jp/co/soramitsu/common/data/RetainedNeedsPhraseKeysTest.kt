package jp.co.soramitsu.common.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The marker for key sets that "Remove all accounts" leaves on the phone. */
class RetainedNeedsPhraseKeysTest {

    @Test
    fun `a credential key names its key set`() {
        assertEquals("cnAlice", WalletPreferenceKeys.retainedKeySetId("prefs_priv_keycnAlice"))
        assertEquals("cnAlice", WalletPreferenceKeys.retainedKeySetId("prefs_mnemoniccnAlice"))
        assertEquals("cnAlice", WalletPreferenceKeys.retainedKeySetId("prefs_seedcnAlice"))
        assertEquals(
            WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID,
            WalletPreferenceKeys.retainedKeySetId("prefs_pub_key"),
        )
    }

    @Test
    fun `keys that hold no secret name no key set`() {
        assertNull(WalletPreferenceKeys.retainedKeySetId("prefs_address_pure"))
        assertNull(WalletPreferenceKeys.retainedKeySetId("wallet.watchOnly.cnAlice"))
        assertNull(WalletPreferenceKeys.retainedKeySetId("user_pin_code"))
    }

    @Test
    fun `the marker survives encoding`() {
        val ids = setOf("cnBob", WalletPreferenceKeys.RETAINED_UNSUFFIXED_ID, "cnAlice")

        val encoded = WalletPreferenceKeys.encodeRetainedNeedsPhrase(ids)

        assertEquals("cnAlice,cnBob,unsuffixed", encoded)
        assertEquals(ids, WalletPreferenceKeys.decodeRetainedNeedsPhrase(encoded))
        assertEquals(emptySet<String>(), WalletPreferenceKeys.decodeRetainedNeedsPhrase(null))
        assertEquals(emptySet<String>(), WalletPreferenceKeys.decodeRetainedNeedsPhrase(""))
    }

    @Test
    fun `the marker is not key material and not part of the wallet-state hash`() {
        val marker = WalletPreferenceKeys.RETAINED_NEEDS_PHRASE

        assertFalse(WalletPreferenceKeys.isRetainedKeyMaterial(marker))
        assertFalse(WalletPreferenceKeys.isWalletStateKey(marker))
        assertNull(WalletPreferenceKeys.retainedKeySetId(marker))
        // It is the one thing besides key material that Remove all accounts leaves.
        assertTrue(WalletPreferenceKeys.isKeptByRemoveAll(marker))
        assertTrue(WalletPreferenceKeys.isKeptByRemoveAll("prefs_priv_keycnAlice"))
        assertFalse(WalletPreferenceKeys.isKeptByRemoveAll("user_pin_code"))
    }
}
