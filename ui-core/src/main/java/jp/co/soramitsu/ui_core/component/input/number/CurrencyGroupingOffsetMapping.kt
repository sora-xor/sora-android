package jp.co.soramitsu.ui_core.component.input.number

import androidx.compose.ui.text.input.OffsetMapping

internal class CurrencyGroupingOffsetMapping(
    private val unmaskedText: String,
    private val maskedText: String,
    private val gs: Char,
    private val ds: Char,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val gsc = maskedText.count { it == gs }
        var index = unmaskedText.indexOf(ds).let {
            if (it == -1) unmaskedText.lastIndex else it
        }
        var result = gsc
        while ((index - 3 > offset) && result > 0) {
            result--
            index -= 3
        }
        return result + offset
    }

    override fun transformedToOriginal(offset: Int): Int {
        val endIndex = offset.coerceAtMost(maskedText.length)
        return if (endIndex > 0) {
            val gsc = maskedText.substring(0, endIndex)
                .count { it == gs }
            (offset - gsc).coerceAtMost(unmaskedText.length)
        } else {
            0
        }
    }
}
