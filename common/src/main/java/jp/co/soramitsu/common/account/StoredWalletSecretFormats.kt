package jp.co.soramitsu.common.account

import java.security.MessageDigest
import jp.co.soramitsu.xcrypto.seed.MnemonicCreator
import jp.co.soramitsu.xcrypto.seed.SeedCreator

/**
 * A recovery phrase as stored or typed, parsed once.
 *
 * Old releases stored the phrase text exactly as it was typed (2.3.x: lower-cased, but commas,
 * tabs, line breaks and repeated spaces were kept). The released apps always derived keys through
 * the library parser, which normalises the text (NFKD) and splits on `[\s,]+`. So the parsed word
 * list, never the raw text, is what identifies a phrase: its word count, its comparison with
 * another phrase and every derivation use [words]. The stored text itself is never rewritten.
 */
class RecoveryPhrase private constructor(
    /** The BIP-39 words in order. */
    val words: List<String>,
    private val entropy: ByteArray,
) {
    /** The words joined by single spaces: the form every released version generated. */
    val text: String get() = words.joinToString(" ")

    val wordCount: Int get() = words.size

    /** SORA2 (Substrate) mini-secret: the first 32 bytes of [fullSeed]. The caller wipes it. */
    fun miniSeed(): ByteArray {
        val full = fullSeed()
        return try {
            full.copyOfRange(0, MINI_SEED_BYTES)
        } finally {
            full.fill(0)
        }
    }

    /**
     * The whole 64-byte PBKDF2-HMAC-SHA512 output (2048 rounds, salt "mnemonic") of the phrase
     * entropy. Releases 3.6.0.0 to 3.8.2.0 cached this uncropped value as the account's "seed".
     * The caller wipes it.
     */
    fun fullSeed(): ByteArray = SeedCreator.deriveSeed(entropy, null)

    fun sameWordsAs(other: RecoveryPhrase): Boolean = words == other.words

    companion object {
        const val MINI_SEED_BYTES = 32
        const val FULL_SEED_BYTES = 64

        /** Every BIP-39 length. SORA2 accepts all of them; Nexus derivation only 12 and 24. */
        val WORD_COUNTS = setOf(12, 15, 18, 21, 24)

        /**
         * Parses [text] with the library parser. Null when it is blank or not a valid BIP-39
         * phrase (unknown word, wrong length or wrong checksum).
         */
        fun parseOrNull(text: String): RecoveryPhrase? {
            if (text.isBlank()) return null
            val parsed = runCatching { MnemonicCreator.fromWords(text) }.getOrNull() ?: return null
            val words = parsed.wordList.filter(String::isNotEmpty)
            if (words.size !in WORD_COUNTS) return null
            return RecoveryPhrase(words, parsed.entropy)
        }

        /** The number of words of a valid phrase, or 0 when [text] is not one. */
        fun wordCount(text: String): Int = parseOrNull(text)?.wordCount ?: 0

        /** Both texts are valid phrases with the same words (separators and spacing ignored). */
        fun sameWords(left: String, right: String): Boolean {
            val first = parseOrNull(left) ?: return false
            val second = parseOrNull(right) ?: return false
            return first.sameWordsAs(second)
        }
    }
}

/** Reading of stored hex values (keys and seeds) and the stored-seed compatibility rule. */
object StoredSecretHex {

    /**
     * Decodes a stored hex value the way every released version read it. An optional `0x` or
     * `0X` prefix and both letter cases are accepted, and spaces, tabs and line breaks anywhere
     * in the text are skipped: the released reader (BouncyCastle `Hex.decode`, through the
     * library's `fromHex`) skipped them too, and 3.8.x stored a typed raw seed exactly as it was
     * typed, so a seed pasted with a line break in the middle is stored with that line break.
     * Null when what remains is not hex (only `0-9`, `a-f`, `A-F`, an even number of digits).
     */
    fun decodeOrNull(text: String): ByteArray? {
        val trimmed = text.trim()
        val start = if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) 2 else 0
        var digits = 0
        for (index in start until trimmed.length) {
            val char = trimmed[index]
            when {
                isSkipped(char) -> Unit
                nibble(char) < 0 -> return null
                else -> digits += 1
            }
        }
        if (digits % 2 != 0) return null
        val out = ByteArray(digits / 2)
        var position = 0
        var high = -1
        for (index in start until trimmed.length) {
            val char = trimmed[index]
            if (isSkipped(char)) continue
            val value = nibble(char)
            if (high < 0) {
                high = value
            } else {
                out[position] = ((high shl 4) or value).toByte()
                position += 1
                high = -1
            }
        }
        return out
    }

    /** The characters the released hex reader ignored between digits. */
    private fun isSkipped(char: Char): Boolean =
        char == ' ' || char == '\t' || char == '\r' || char == '\n'

    private fun nibble(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }

    /** Decodes like [decodeOrNull]; an empty array for a blank or undecodable value. */
    fun decodeOrEmpty(text: String): ByteArray = decodeOrNull(text) ?: ByteArray(0)

    fun encode(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            append(HEX[(byte.toInt() ushr 4) and 0x0f])
            append(HEX[byte.toInt() and 0x0f])
        }
    }

    /** Both values decode to the same bytes (prefix and letter case ignored). */
    fun sameBytes(left: String, right: String): Boolean {
        val first = decodeOrNull(left) ?: return false
        val second = decodeOrNull(right)
        return try {
            second != null && MessageDigest.isEqual(first, second)
        } finally {
            first.fill(0)
            second?.fill(0)
        }
    }

    /**
     * The canonical 32-byte SORA2 seed that [storedSeed] stands for when it belongs to [phrase]:
     * the stored value is either the phrase's 32-byte seed, or the full 64-byte derivation of the
     * same phrase (all 64 bytes are compared, so arbitrary trailing bytes are not accepted).
     * Null when the stored seed is not this phrase's seed. The caller wipes the result.
     */
    fun canonicalSeedOf(storedSeed: ByteArray, phrase: RecoveryPhrase): ByteArray? {
        val full = phrase.fullSeed()
        return try {
            val matches = when (storedSeed.size) {
                RecoveryPhrase.MINI_SEED_BYTES -> MessageDigest.isEqual(
                    storedSeed,
                    full.copyOfRange(0, RecoveryPhrase.MINI_SEED_BYTES),
                )
                RecoveryPhrase.FULL_SEED_BYTES -> MessageDigest.isEqual(storedSeed, full)
                else -> false
            }
            if (matches) full.copyOfRange(0, RecoveryPhrase.MINI_SEED_BYTES) else null
        } finally {
            full.fill(0)
        }
    }

    /** [storedSeed] (hex text) is the seed of [phrase] in its 32-byte or full 64-byte form. */
    fun seedBelongsToPhrase(storedSeed: String, phrase: RecoveryPhrase): Boolean {
        val bytes = decodeOrNull(storedSeed) ?: return false
        val canonical = try {
            canonicalSeedOf(bytes, phrase)
        } finally {
            bytes.fill(0)
        }
        canonical?.fill(0)
        return canonical != null
    }

    private const val HEX = "0123456789abcdef"
}
