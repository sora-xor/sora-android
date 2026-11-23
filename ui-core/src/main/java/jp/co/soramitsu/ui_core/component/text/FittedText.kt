package jp.co.soramitsu.ui_core.component.text

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * From [size] down by [steps] scaled pixels, one at a time: a text that fits is set at exactly its own size. Null
 * when the size cannot come down that far and stay readable.
 */
internal fun fitDown(size: TextUnit, steps: Int): TextAutoSize? =
    if (size.isSpecified && size.isSp && size.value - steps >= 9f) {
        TextAutoSize.StepBased(minFontSize = (size.value - steps).sp, maxFontSize = size, stepSize = 1.sp)
    } else {
        null
    }

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
 * too long even then goes back to its own size and wraps; it is never cut.
 */
@Composable
fun FittedLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    steps: Int = 4,
) {
    var wraps by remember(text, style) { mutableStateOf(false) }
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        onTextLayout = { if (!wraps && it.hasVisualOverflow) wraps = true },
        overflow = TextOverflow.Clip,
        softWrap = wraps,
        maxLines = if (wraps) Int.MAX_VALUE else 1,
        autoSize = if (wraps) null else fitDown(style.fontSize, steps),
    )
}
