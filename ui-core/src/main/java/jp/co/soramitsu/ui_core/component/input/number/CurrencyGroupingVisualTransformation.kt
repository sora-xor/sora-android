package jp.co.soramitsu.ui_core.component.input.number

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.lang.StringBuilder
import java.text.DecimalFormatSymbols
import java.util.LinkedList

class CurrencyGroupingVisualTransformation(
    private val decimalFormatSymbols: DecimalFormatSymbols,
    private val suffix: String = ""
) : VisualTransformation {

    /* We want to track inserted decoration indices(decimal-dot, minus-sign not considered) */
    private val groupingSeparatorsInsertionIndices = LinkedList<Int>()

    /*
        We want to track all offsets as ranges of insertion
        from which we'll be able to define how to cursor correctly
    */
    private val insertionRanges = LinkedList<IntRange>().apply {
        addFirst(IntRange(0, 0))
    }

    private val floatingValueOffsetMapper = RangeBasedOffsetMapping()

    /**
     * Floating number representation is used for better control:
     *  @url - https://www.geeksforgeeks.org/introduction-of-floating-point-representation/
     */
    override fun filter(text: AnnotatedString): TransformedText {
        insertionRanges.clear()
        groupingSeparatorsInsertionIndices.clear()

        val transformedValue =
            with(StringBuilder(text.text)) {
                /*
                    Before inserting grouping separators we need to find an exponent part
                    where we can add such decorations
                */
                val (_, exponentRange, mantissaRange) = indexOf(
                    decimalFormatSymbols.decimalSeparator.toString()
                ).let { index ->
                    if (index == 0 || isEmpty())
                        return@with ""

                    val decimalSeparatorIndex = if (index == -1) length else index

                    val signIndex = indexOf(decimalFormatSymbols.minusSign.toString())

                    val exponentPart = IntRange(signIndex + 1, decimalSeparatorIndex - 1)

                    val mantissaPart = IntRange(decimalSeparatorIndex, length)

                    Triple(
                        first = signIndex, // if -1 then number is positive
                        second = exponentPart,
                        third = mantissaPart
                    )
                }

                /* Insertion of grouping separators: 12345 -> 12,345; grouping is done by 3 */
                for (insertionIndex in exponentRange.reversed() step 3) {
                    // do not insert on last position like this: 12345 -> 12,345,
                    if (insertionIndex == exponentRange.last)
                        continue

                    // save index of insertion for further use
                    groupingSeparatorsInsertionIndices.addFirst(insertionIndex)
                }

                groupingSeparatorsInsertionIndices.forEachIndexed { index, insertionIndex ->
                    val sumOfPreviousDecorations = index + 1 // add 1, since indices start with 0
                    val actualInsertionIndex = insertionIndex + sumOfPreviousDecorations

                    insert(actualInsertionIndex, decimalFormatSymbols.groupingSeparator)

                    /*
                        If we end up with a groupingSep then we need to jump over it:
                        12|,345 -> 12,3|45, inputField cursor is denoted as |
                        So, if actualInsertionIndex of grouping separator is: 2,
                        then, in the transformed string, we want to jump
                        right after grouping separator, so when user is actually at position
                        of inserted grouping separator, we make it seem that there is nothing
                        So, we add 1 to actualInsertionIndex, which position behind of
                        grouping separator, and then we add to this value additional 1
                        to represent length of grouping separator length
                    */
                    IntRange(
                        start = actualInsertionIndex + 1,
                        endInclusive = actualInsertionIndex + 2
                    ).apply { insertionRanges.add(this) }
                }

                /*
                    Now, we need to add a suffix respecting all other insertion
                    happened previously
                */
                if (suffix.isNotBlank()) {
                    val actualInsertionIndex = mantissaRange.last
                        .plus(groupingSeparatorsInsertionIndices.size)

                    insert(actualInsertionIndex, suffix)

                    IntRange(
                        start = actualInsertionIndex,
                        endInclusive = actualInsertionIndex + suffix.length
                    ).apply { insertionRanges.add(this) }
                }

                return@with this.toString()
            }

        floatingValueOffsetMapper.setTransformedText(transformedValue)
        floatingValueOffsetMapper.setDependencyData(insertionRanges)

        return TransformedText(
            text = AnnotatedString(
                text = transformedValue,
                spanStyles = text.spanStyles,
                paragraphStyles = text.paragraphStyles
            ),
            offsetMapping = floatingValueOffsetMapper
        )
    }
}
