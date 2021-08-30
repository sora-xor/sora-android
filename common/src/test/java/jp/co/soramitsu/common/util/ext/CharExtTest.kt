package jp.co.soramitsu.common.util.ext

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharExtTest {

    @Test
    fun `validating valid chars`() {
        val input = arrayOf('a', 'ф','1', '3', '\u0308')

        for (char in input) {
            assertTrue(char.isValidNameChar())
        }
    }

    @Test
    fun `validating invalid chars`() {
        val input = arrayOf('-', ',', '.', '+')

        for (char in input) {
            assertFalse(char.isValidNameChar())
        }
    }

}