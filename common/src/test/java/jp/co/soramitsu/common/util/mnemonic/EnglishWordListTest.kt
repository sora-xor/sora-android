package jp.co.soramitsu.common.util.mnemonic

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/** BIP-39 indices are part of recovery compatibility; even reordered words change a seed. */
class EnglishWordListTest {
    @Test fun `English words match the published BIP-39 list byte for byte`() {
        val bytes = (EnglishWordList.words.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        // https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt
        assertEquals(2048, EnglishWordList.words.size)
        assertEquals("2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda", digest)
    }
}
