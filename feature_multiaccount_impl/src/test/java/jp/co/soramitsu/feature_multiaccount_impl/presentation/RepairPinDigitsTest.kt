package jp.co.soramitsu.feature_multiaccount_impl.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The PIN typed in the repair dialog. The app's PIN is stored as ASCII digits; a keyboard set to
 * another script types the same numbers in its own digits.
 */
class RepairPinDigitsTest {

    @Test
    fun `digits of any script are the same PIN`() {
        assertEquals("135790", asciiPinDigits("135790"))
        // Arabic-Indic, Extended Arabic-Indic (Persian) and Devanagari (Hindi) digits.
        assertEquals("135790", asciiPinDigits("١٣٥٧٩٠"))
        assertEquals("135790", asciiPinDigits("۱۳۵۷۹۰"))
        assertEquals("135790", asciiPinDigits("१३५७९०"))
        // Mixed input is still one PIN.
        assertEquals("135790", asciiPinDigits("13٥۷9०"))
    }

    @Test
    fun `everything that is not a digit is dropped and the length is bounded`() {
        assertEquals("1357", asciiPinDigits(" 1-3 5.7a\n"))
        assertEquals("", asciiPinDigits("abcdef"))
        assertEquals("", asciiPinDigits(""))
        assertEquals("123456789012", asciiPinDigits("1234567890123456"))
    }
}
