package jp.co.soramitsu.sora.platform

import android.text.style.RelativeSizeSpan
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.util.ext.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class AmountTextRenderingContractTest {
    @Test fun fractionalAmountSpanStopsBeforeItsTickerWithoutChangingExactText() {
        val value = "123.000000000000000001 XOR".decimalPartSized(ticker = "XOR")
        assertEquals("123.000000000000000001 XOR", value.toString())
        val span = value.getSpans(0, value.length, RelativeSizeSpan::class.java).single()
        assertEquals(3, value.getSpanStart(span)); assertEquals(value.toString().indexOf("XOR"), value.getSpanEnd(span))
        assertEquals(0.7f, span.sizeChange)
    }
    @Test fun localizedDecimalSeparatorAndMissingTickerUseTheExpectedFractionRange() {
        val localized = "42,125".decimalPartSized(",", "XOR")
        val span = localized.getSpans(0, localized.length, RelativeSizeSpan::class.java).single()
        assertEquals(2, localized.getSpanStart(span)); assertEquals(6, localized.getSpanEnd(span))
        val integer = "42 XOR".decimalPartSized()
        assertEquals("42 XOR", integer.toString()); assertTrue(integer.getSpans(0, integer.length, RelativeSizeSpan::class.java).isEmpty())
    }
    @Test fun underliningTargetsOnlyTheFirstLiteralOccurrenceAndIgnoresMissingText() {
        val result = underlineSubstring("Backup wallet backup", "wallet")
        assertEquals("Backup wallet backup", result.text)
        val span = result.spanStyles.single()
        assertEquals(7, span.start); assertEquals(13, span.end); assertEquals(TextDecoration.Underline, span.item.textDecoration)
        assertTrue(underlineSubstring("Unchanged", "absent").spanStyles.isEmpty())
    }
    @Test fun invalidLegacyHighlightInstructionsPreserveVisibleTextWithoutClickHandlers() {
        for (value in listOf("Only %%one delimiter", "%%first%% and %%second%%")) {
            val result = value.highlightWords(emptyList(), emptyList())
            assertEquals(value, result.toString()); assertTrue(result.getSpans(0, result.length, android.text.style.ClickableSpan::class.java).isEmpty())
        }
    }
    @Test fun groupedAmountParsingPreservesAllFractionalDigitsAndRejectsIncompleteInput() {
        assertEquals(BigDecimal("1234.000000000000000001"), "1 234.000000000000000001".getBigDecimal())
        assertEquals(BigDecimal("1234.50"), "1,234.50".getBigDecimal(','))
        assertNull((null as String?).getBigDecimal()); assertNull("".getBigDecimal()); assertNull(".5".getBigDecimal())
        assertTrue(runCatching { "not an amount".getBigDecimal() }.exceptionOrNull() is NumberFormatException)
    }
}
