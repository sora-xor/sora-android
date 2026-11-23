package jp.co.soramitsu.ui_core.component.text

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
 * [SMALLEST_FITTED_SP]. The own size is tried exactly as given, so a text that fits is set as if nothing here
 * existed (stepping up from the smallest size in pixels, as the library's own rule does, can stop one size short
 * of it at some densities).
 */
private class FitDown(private val size: TextUnit, private val steps: Int) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var candidate = size.value
        val smallest = maxOf(SMALLEST_FITTED_SP, size.value - steps)
        while (candidate > smallest) {
            if (!performLayout(constraints, text, candidate.sp).overflows()) return candidate.sp
            candidate = maxOf(smallest, candidate - 1f)
        }
        return candidate.sp
    }

    override fun equals(other: Any?): Boolean = other is FitDown && other.size == size && other.steps == steps

    override fun hashCode(): Int = size.hashCode() * 31 + steps
}

/** Whether a laid-out text lost anything: drawn outside its bounds, or cut with an ellipsis. */
private fun TextLayoutResult.overflows(): Boolean = when (layoutInput.overflow) {
    TextOverflow.Ellipsis, TextOverflow.StartEllipsis, TextOverflow.MiddleEllipsis ->
        lineCount > 0 && isLineEllipsized(lineCount - 1)
    else -> didOverflowWidth || didOverflowHeight
}

/** The rule for a text of this size, or none when the size is not in scaled pixels or already at the smallest. */
internal fun fitDown(size: TextUnit, steps: Int): TextAutoSize? =
    if (size.isSpecified && size.isSp && size.value > SMALLEST_FITTED_SP && steps > 0) FitDown(size, steps) else null

/**
 * A figure or a label that has to stay whole on one line: an amount, a change, a name with its unit. Where the
 * line is too narrow for it (a 320 dp screen, larger text) it is set smaller, a scaled pixel at a time down to
 * [steps] sizes, and only then cut with an ellipsis. Colours come from [style] or the text's own spans.
 */
@Composable
fun FittedText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    steps: Int = 4,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        autoSize = fitDown(style.fontSize, steps),
    )
}

/**
 * A short label, as on a segment or a tab: it keeps to one line by being set smaller, down to [steps] sizes. A label
 * too long even then goes back to its own size and wraps; it is never cut. The choice is made again whenever the
 * label's width changes.
 */
@Composable
fun FittedLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    steps: Int = 4,
) {
    var wraps by remember(text, style) { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(-1) }
    BasicText(
        text = text,
        // A new width (a rotation, a resized window) may leave room for one line again.
        modifier = modifier.onSizeChanged {
            if (it.width != width) {
                width = it.width
                wraps = false
            }
        },
        style = style,
        onTextLayout = { if (!wraps && it.hasVisualOverflow) wraps = true },
        overflow = TextOverflow.Clip,
        softWrap = wraps,
        maxLines = if (wraps) Int.MAX_VALUE else 1,
        autoSize = if (wraps) null else fitDown(style.fontSize, steps),
    )
}
