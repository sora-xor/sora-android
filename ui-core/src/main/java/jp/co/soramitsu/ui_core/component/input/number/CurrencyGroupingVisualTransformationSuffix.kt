package jp.co.soramitsu.ui_core.component.input.number

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormatSymbols

class CurrencyGroupingVisualTransformationSuffix(
    private val decimalFormatSymbols: DecimalFormatSymbols,
    private val suffix: Char,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val before = text.text.substringBefore(decimalFormatSymbols.decimalSeparator)
        val after = text.text.substringAfter(decimalFormatSymbols.decimalSeparator, "")
        val formatted = before.reversed().chunked(3)
            .joinToString(decimalFormatSymbols.groupingSeparator.toString()).reversed()
        val transformed = buildString {
            append(formatted)
            if (text.text.contains(decimalFormatSymbols.decimalSeparator)) {
                append(decimalFormatSymbols.decimalSeparator)
            }
            if (after.isNotEmpty()) {
                append(after)
            }
            append(suffix)
        }

        return TransformedText(
            text = AnnotatedString(
                text = transformed,
                spanStyles = text.spanStyles,
                paragraphStyles = text.paragraphStyles
            ),
            offsetMapping = CurrencyGroupingOffsetMapping(
                unmaskedText = text.toString(),
                maskedText = transformed,
                gs = decimalFormatSymbols.groupingSeparator,
                ds = decimalFormatSymbols.decimalSeparator,
            ),
        )
    }
}
