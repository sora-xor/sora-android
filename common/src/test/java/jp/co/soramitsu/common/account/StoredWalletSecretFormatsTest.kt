package jp.co.soramitsu.common.account

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredWalletSecretFormatsTest {

    private val words = TWELVE.split(" ")

    @Test
    fun `a phrase is the same phrase whatever separated its words when it was stored`() {
        val canonical = requireNotNull(RecoveryPhrase.parseOrNull(TWELVE))
        val stored = listOf(
            words.joinToString(","), // 2.3.x: typed with commas, stored as typed
            words.joinToString(", "), // comma plus space
            words.joinToString(" ,"),
            words.joinToString("  "),
            words.joinToString("\t"),
            words.joinToString("\n"),
            "  $TWELVE \n",
            words.joinToString("　"), // ideographic space, normalised by NFKD
        )
        stored.forEach { text ->
            val parsed = requireNotNull(RecoveryPhrase.parseOrNull(text)) { "not parsed: [$text]" }
            assertEquals(words, parsed.words)
            assertEquals(12, parsed.wordCount)
            assertEquals(TWELVE, parsed.text)
            assertTrue(parsed.sameWordsAs(canonical))
            assertTrue(RecoveryPhrase.sameWords(text, TWELVE))
            assertEquals(12, RecoveryPhrase.wordCount(text))
            // The SORA2 seed comes from the parsed words, so it is the same for every form.
            assertEquals(TWELVE_SEED, StoredSecretHex.encode(parsed.miniSeed()))
        }
    }

    @Test
    fun `every BIP39 length parses and anything else does not`() {
        assertEquals(15, RecoveryPhrase.wordCount(FIFTEEN))
        assertEquals(24, RecoveryPhrase.wordCount(TWENTY_FOUR))
        assertEquals(24, RecoveryPhrase.wordCount(TWENTY_FOUR.replace(" ", ",")))
        assertNull(RecoveryPhrase.parseOrNull(""))
        assertNull(RecoveryPhrase.parseOrNull("   "))
        assertNull(RecoveryPhrase.parseOrNull(",,,"))
        // Wrong checksum, unknown word, and a count that is not a BIP-39 length.
        assertNull(RecoveryPhrase.parseOrNull(words.dropLast(1).plus("abandon").joinToString(" ")))
        assertNull(RecoveryPhrase.parseOrNull(words.dropLast(1).plus("notaword").joinToString(" ")))
        assertNull(RecoveryPhrase.parseOrNull(words.dropLast(1).joinToString(" ")))
        assertEquals(0, RecoveryPhrase.wordCount("abandon"))
        assertFalse(RecoveryPhrase.sameWords(TWELVE, TWENTY_FOUR))
        assertFalse(RecoveryPhrase.sameWords(TWELVE, "not a phrase"))
        assertFalse(RecoveryPhrase.sameWords("not a phrase", "not a phrase"))
    }

    @Test
    fun `the full seed is 64 bytes and the SORA2 seed is its first 32`() {
        val phrase = requireNotNull(RecoveryPhrase.parseOrNull(TWELVE))
        val full = phrase.fullSeed()
        assertEquals(64, full.size)
        assertEquals(TWELVE_SEED, StoredSecretHex.encode(full.copyOfRange(0, 32)))
        assertArrayEquals(full.copyOfRange(0, 32), phrase.miniSeed())
        // The second half is real derived material, not padding.
        assertFalse(full.copyOfRange(32, 64).all { it == 0.toByte() })
    }

    @Test
    fun `a stored seed belongs to a phrase as its 32 byte seed or its whole 64 byte derivation`() {
        val phrase = requireNotNull(RecoveryPhrase.parseOrNull(TWELVE))
        val full = phrase.fullSeed()
        val mini = phrase.miniSeed()

        assertArrayEquals(mini, StoredSecretHex.canonicalSeedOf(mini, phrase))
        // 3.6.0.0 to 3.8.2.0 cached the uncropped value: accepted, and read as its 32 bytes.
        assertArrayEquals(mini, StoredSecretHex.canonicalSeedOf(full, phrase))
        assertTrue(StoredSecretHex.seedBelongsToPhrase(StoredSecretHex.encode(full), phrase))
        assertTrue(StoredSecretHex.seedBelongsToPhrase("0X" + StoredSecretHex.encode(full).uppercase(), phrase))
        assertTrue(StoredSecretHex.seedBelongsToPhrase("0x" + TWELVE_SEED, phrase))

        // All 64 bytes are compared: a right first half with other trailing bytes is refused.
        val wrongTail = full.copyOf().also { it[63] = (it[63] + 1).toByte() }
        assertNull(StoredSecretHex.canonicalSeedOf(wrongTail, phrase))
        val zeroTail = mini + ByteArray(32)
        assertNull(StoredSecretHex.canonicalSeedOf(zeroTail, phrase))
        val wrongHead = full.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertNull(StoredSecretHex.canonicalSeedOf(wrongHead, phrase))
        // Other lengths are never a seed of the phrase, even as a prefix of it.
        assertNull(StoredSecretHex.canonicalSeedOf(full.copyOfRange(0, 48), phrase))
        assertNull(StoredSecretHex.canonicalSeedOf(full.copyOfRange(0, 31), phrase))
        assertNull(StoredSecretHex.canonicalSeedOf(ByteArray(0), phrase))
        // Another phrase's seed.
        val other = requireNotNull(RecoveryPhrase.parseOrNull(TWENTY_FOUR))
        assertNull(StoredSecretHex.canonicalSeedOf(other.miniSeed(), phrase))
        assertNull(StoredSecretHex.canonicalSeedOf(other.fullSeed(), phrase))
        assertFalse(StoredSecretHex.seedBelongsToPhrase("not hex", phrase))
        assertFalse(StoredSecretHex.seedBelongsToPhrase("", phrase))
    }

    @Test
    fun `stored hex reads the same with either case and an optional prefix`() {
        val bytes = byteArrayOf(0x00, 0x0a, 0x7f, 0x80.toByte(), 0xff.toByte())
        listOf("000a7f80ff", "000A7F80FF", "0x000a7f80ff", "0X000A7F80FF", " 0x000a7f80ff\n").forEach {
            assertArrayEquals(it, bytes, StoredSecretHex.decodeOrNull(it))
        }
        assertEquals("000a7f80ff", StoredSecretHex.encode(bytes))
        assertNotNull(StoredSecretHex.decodeOrNull(""))
        assertEquals(0, StoredSecretHex.decodeOrEmpty("").size)
        assertNull(StoredSecretHex.decodeOrNull("abc"))
        assertNull(StoredSecretHex.decodeOrNull("zz"))
        assertNull(StoredSecretHex.decodeOrNull("0x0x00"))
        assertEquals(0, StoredSecretHex.decodeOrEmpty("zz").size)
        assertTrue(StoredSecretHex.sameBytes("0XABCD", "abcd"))
        assertFalse(StoredSecretHex.sameBytes("abcd", "abce"))
        assertFalse(StoredSecretHex.sameBytes("abcd", "abcd00"))
        assertFalse(StoredSecretHex.sameBytes("zz", "zz"))
    }

    private companion object {
        const val TWELVE =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        /** Pinned SORA2 seed of [TWELVE] (the same vector as MigrationManagerSafetyTest). */
        const val TWELVE_SEED = "4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2"
        const val FIFTEEN =
            "adapt explain ecology dinner glare old unfair empty expect road off suggest dash " +
                "dolphin case"
        const val TWENTY_FOUR =
            "abandon amount liar amount expire adjust cage candy arch gather drum bullet absurd " +
                "math era live bid rhythm alien crouch range attend journey unaware"
    }
}
