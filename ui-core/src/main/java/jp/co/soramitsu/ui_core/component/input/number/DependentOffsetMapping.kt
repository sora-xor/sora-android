package jp.co.soramitsu.ui_core.component.input.number

import androidx.compose.ui.text.input.OffsetMapping

/**
 * [DependentOffsetMapping] is a descendant of [OffsetMapping]
 * that allows to explicitly define dependencies such as transformed text
 * that will be used while calculating offsets for
 * clients like BasicTextField VisualTransformation
 */
internal interface DependentOffsetMapping<DependencyData>: OffsetMapping {

    fun setTransformedText(text: String)

    fun setDependencyData(data: DependencyData)

}

/**
 * [RangeBasedOffsetMapping] is an implementation of [DependentOffsetMapping]
 * that determines offsetMapping for clients like BasicTextField that can utilize
 * movable cursor
 *
 * [RangeBasedOffsetMapping] has some optimization over straightforward implementation
 * of OffsetMapping (iteration over transformed, and untransformed strings to determine offset)
 * Main intention behind this optimization: cursor movement should not lag when
 * it tries to understand its next position offset to improve user experience
 *
 * In order to achieve this, we will reduce length of possible checkpoints from max possible
 * which is the length of transformed string (e.g., 123,456,789.12345, length would be 17 chars)
 * to just a length of a list of insertions that should be jump over by user (in string 123,456,789.12345,
 * length of a list of insertions would 3, due to 3 grouping separators in exponent part
 * that were inserted)
 *
 * String of length 17, or something similar is not a heavy operation for a CPU, but
 * its implementation with some extension functions, and not in-place list sorting, or mappings
 * as Internet bloggers can suggest can badly result on smoothness of cursor movement animation
 * In addition, length equal to 17, or similar to it is not always guaranteed but UX can be harmed
 */
internal class RangeBasedOffsetMapping : DependentOffsetMapping<List<IntRange>> {

    private var transformedText: String = ""

    /* List of ranges MUST be sorted in ASC order */
    private var jumpOverRanges: List<IntRange> = listOf(IntRange(0, 0))

    override fun setTransformedText(text: String) {
        transformedText = text
    }

    /**
     * @param data - is a list of IntRanges that MUST be provided in ASC order
     */
    override fun setDependencyData(data: List<IntRange>) {
        jumpOverRanges = data
    }

    /*
        Look for condition variables names to see main logic
     */
    override fun originalToTransformed(offset: Int): Int {
        var offsetToOffset = 0

        for (range in jumpOverRanges) {
            val isOffsetInPreviousRange = (range.first - offsetToOffset) > offset

            if (isOffsetInPreviousRange)
                break

            val isRangeEndEqualsEntireTextLength = range.last == transformedText.length

            if (isRangeEndEqualsEntireTextLength)
                return range.first

            val isOffsetReadyToBeJumped = (range.first - offsetToOffset) == offset

            if (isOffsetReadyToBeJumped)
                return range.last

            offsetToOffset += range.last - range.first
        }

        return offset + offsetToOffset
    }


    /*
        Look for condition variables names to see main logic
     */
    override fun transformedToOriginal(offset: Int): Int {
        var offsetToOffset = 0

        for (range in jumpOverRanges) {
            val isOffsetInPreviousRange = range.first > offset

            if (isOffsetInPreviousRange)
                break

            val isRangeEndEqualsEntireTextLength = range.last == transformedText.length

            if (isRangeEndEqualsEntireTextLength)
                return range.first - offsetToOffset

            val isOffsetInCurrentRange = range.first <= offset && range.last > offset

            if (isOffsetInCurrentRange)
                return range.first - offsetToOffset

            offsetToOffset += range.last - range.first
        }

        return offset - offsetToOffset
    }
}