package jp.co.soramitsu.ui_core.lit

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * How wide things are, on every screen. A screen is one column of content: it has the same margin at both sides, it
 * never grows wider than a page that is easy to read, and its buttons and inputs fill it. On a phone the column is
 * the screen less its margins; on a tablet, a phone on its side or a split window it stays [ContentMaxWidth] wide in
 * the middle, and the page carries on behind it.
 */
object LitLayout {
    /** Between the side of the screen (or of the column) and its content. */
    val ScreenMargin = 16.dp

    /** The widest a screen's column of content gets. */
    val ContentMaxWidth = 640.dp

    /** The height of a screen's main action, and of the buttons that stand with it. */
    val ActionHeight = 56.dp

    /** The height of a field that takes typing. */
    val InputHeight = 56.dp
}

/**
 * The screen's column: what this modifier wraps is at most [max] wide and stands in the middle of the width it was
 * given. Given no more than [max] (every phone held upright) it changes nothing.
 */
fun Modifier.litContentWidth(max: Dp = LitLayout.ContentMaxWidth): Modifier = layout { measurable, constraints ->
    val cap = max.roundToPx()
    if (!constraints.hasBoundedWidth || constraints.maxWidth <= cap) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val placeable = measurable.measure(constraints.copy(minWidth = min(constraints.minWidth, cap), maxWidth = cap))
        // Asked to fill the width, it fills it and its content stands in the middle.
        val width = maxOf(placeable.width, constraints.minWidth)
        layout(width, placeable.height) { placeable.place((width - placeable.width) / 2, 0) }
    }
}
