package jp.co.soramitsu.ui_core.component.button

import androidx.compose.foundation.text.BasicText
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt
import jp.co.soramitsu.ui_core.component.text.fitDown
import jp.co.soramitsu.ui_core.theme.customTypography

/** How many sizes a label may come down, a scaled pixel at a time, before it is cut short. */
private const val LABEL_SHRINK_STEPS = 4

/**
 * A button's label, the same on every button. It is set in the button's own type. Where the button is too narrow
 * for it (a small phone, two buttons side by side, a long word, larger text) it first takes half of the space at
 * its ends ([buttonLabelEnds]); then it is set smaller, a scaled pixel at a time, down to [LABEL_SHRINK_STEPS]
 * sizes; only then is it cut short, with an ellipsis rather than mid-letter.
 */
@Composable
fun ButtonLabel(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.customTypography.buttonM,
) {
    val ink = if (color.isSpecified) color else LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = ink, textAlign = TextAlign.Center)),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        autoSize = fitDown(style.fontSize, LABEL_SHRINK_STEPS),
    )
}

/**
 * The space at the two ends of a button's label: [start] and [end] where there is room, and down to half of each
 * where the label would otherwise not fit. Put it straight on the [ButtonLabel].
 */
fun Modifier.buttonLabelEnds(start: Dp, end: Dp): Modifier = layout { measurable, constraints ->
    val startPx = start.roundToPx()
    val full = startPx + end.roundToPx()
    val least = full / 2
    val placeable = measurable.measure(constraints.offset(horizontal = -least).copy(minWidth = 0))
    val room = if (constraints.hasBoundedWidth) constraints.maxWidth - placeable.width else full
    val ends = room.coerceIn(least, full)
    val width = (placeable.width + ends).coerceIn(constraints.minWidth, constraints.maxWidth)
    val left = if (full == 0) 0 else ((width - placeable.width) * (startPx.toFloat() / full)).roundToInt()
    layout(width, placeable.height) { placeable.placeRelative(left, 0) }
}
