package jp.co.soramitsu.ui_core.component.text

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** No text is set smaller than this to make it fit. */
private const val SMALLEST_FITTED_SP = 11f

/**
 * A text's own size first, then one scaled pixel smaller at a time, at most [steps] times and never under
 * [SMALLEST_FITTED_SP] or [floor]. The own size is tried exactly as given, so a text that fits is set as if nothing
 * here existed (stepping up from the smallest size in pixels, as the library's own rule does, can stop one size short
 * of it at some densities).
 *
 * With [fewestLines] a text that may take several lines is first set smaller to take as few as it can at the
 * smallest size, and only then wraps: the largest size that needs no more lines than the smallest one is taken. An
 * amount that fits on one line a little smaller is set on one line, not on two at its own size.
 */
private class FitDown(
    private val size: TextUnit,
    private val steps: Int,
    private val floor: Float,
    private val fewestLines: Boolean,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var candidate = size.value
        val smallest = minOf(size.value, maxOf(SMALLEST_FITTED_SP, size.value - steps, floor))
        // The lines the text needs at its smallest, worked out only when one line will not do.
        var fewest = 0
        while (candidate > smallest) {
            val layout = performLayout(constraints, text, candidate.sp)
            if (!layout.overflows()) {
                if (!fewestLines || layout.lineCount <= 1) return candidate.sp
                if (fewest == 0) fewest = performLayout(constraints, text, smallest.sp).lineCount
                if (layout.lineCount <= fewest) return candidate.sp
            }
            candidate = maxOf(smallest, candidate - 1f)
        }
        return candidate.sp
    }

    override fun equals(other: Any?): Boolean =
        other is FitDown && other.size == size && other.steps == steps && other.floor == floor &&
            other.fewestLines == fewestLines

    override fun hashCode(): Int = ((size.hashCode() * 31 + steps) * 31 + floor.hashCode()) * 31 + fewestLines.hashCode()
}

/** Whether a laid-out text lost anything: drawn outside its bounds, or cut with an ellipsis. */
private fun TextLayoutResult.overflows(): Boolean = when (layoutInput.overflow) {
    TextOverflow.Ellipsis, TextOverflow.StartEllipsis, TextOverflow.MiddleEllipsis ->
        lineCount > 0 && isLineEllipsized(lineCount - 1)
    else -> didOverflowWidth || didOverflowHeight
}

/**
 * The rule for a text of this size, or none when the size is not in scaled pixels or already at the smallest. [floor]
 * (in scaled pixels) keeps a figure above the line set under it.
 */
internal fun fitDown(
    size: TextUnit,
    steps: Int,
    floor: TextUnit = TextUnit.Unspecified,
    fewestLines: Boolean = false,
): TextAutoSize? {
    val least = if (floor.isSpecified && floor.isSp) floor.value else 0f
    return if (size.isSpecified && size.isSp && size.value > maxOf(SMALLEST_FITTED_SP, least) && steps > 0) {
        FitDown(size, steps, least, fewestLines)
    } else {
        null
    }
}

/**
 * A figure or a label that has to stay whole on one line: an amount, a change, a name with its unit. Where the
 * line is too narrow for it (a 320 dp screen, larger text) it is set smaller, a scaled pixel at a time down to
 * [steps] sizes (never under [smallest]), and only then cut with an ellipsis. With more than one line allowed it is
 * set smaller first and wraps only when even its smallest size needs more than one line (an amount given
 * `Int.MAX_VALUE` lines is never cut). Colours come from [style] or the text's own spans.
 */
@Composable
fun FittedText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    steps: Int = 4,
    smallest: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        autoSize = fitDown(style.fontSize, steps, smallest, fewestLines = maxLines > 1),
    )
}

/**
 * A short label, as on a segment or a tab: it keeps to one line by being set smaller, down to [steps] sizes. A label
 * too long even then goes back to its own size and wraps; it is never cut. The choice is made again whenever the
 * width the label is offered changes.
 */
@Composable
fun FittedLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    steps: Int = 4,
) {
    // The width at which one line did not hold the label even at its smallest, or none. The decision and the width it
    // was made at come from the same layout, so it cannot be undone by a size report that arrives after it.
    var wrapWidth by remember(text, style) { mutableStateOf<Int?>(null) }
    val wraps = wrapWidth != null
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        onTextLayout = { layout ->
            val offered = layout.layoutInput.constraints.maxWidth
            when {
                wrapWidth == null -> if (layout.hasVisualOverflow) wrapWidth = offered
                // A new width (a rotation, a resized window) may leave room for one line again: try it.
                offered != wrapWidth -> wrapWidth = null
            }
        },
        overflow = TextOverflow.Clip,
        softWrap = wraps,
        maxLines = if (wraps) Int.MAX_VALUE else 1,
        autoSize = if (wraps) null else fitDown(style.fontSize, steps),
    )
}
