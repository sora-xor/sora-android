package jp.co.soramitsu.ui_core.lit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
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
 * offered, whether or not its parent asked it to fill that width (a `Box` does not). Offered no more than [max]
 * (every phone held upright) it changes nothing.
 */
fun Modifier.litContentWidth(max: Dp = LitLayout.ContentMaxWidth): Modifier = layout { measurable, constraints ->
    val cap = max.roundToPx()
    if (!constraints.hasBoundedWidth) {
        // Nothing to stand in the middle of: only the widest applies.
        val placeable = measurable.measure(constraints.copy(minWidth = min(constraints.minWidth, cap), maxWidth = cap))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else if (constraints.maxWidth <= cap) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val offered = constraints.maxWidth
        val placeable = measurable.measure(constraints.copy(minWidth = min(constraints.minWidth, cap), maxWidth = cap))
        layout(offered, placeable.height) { placeable.place((offered - placeable.width) / 2, 0) }
    }
}

/**
 * A name and its figures on one line, as a list row has them: [leading] (a picture) at the start, the figures at
 * their own width at the end and the name in what is left between them, with [below] (a second line that may wrap)
 * under the name. Where the name would not be whole beside the figures (a 320 dp screen, larger text, a long amount
 * or a long name), the figures stand on a line of their own under it, at the end, and may take the whole line: a name
 * is never cut short to make room for figures.
 *
 * [nameFits] says whether the name is whole at a width in pixels; by default, whether it fits on one line. Each slot
 * holds one element.
 */
@Composable
fun NameAndFigures(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    leadingGap: Dp = 12.dp,
    figuresGap: Dp = 8.dp,
    belowGap: Dp = 4.dp,
    lineGap: Dp = 4.dp,
    nameFits: ((width: Int, name: IntrinsicMeasurable) -> Boolean)? = null,
    below: @Composable () -> Unit = {},
    name: @Composable () -> Unit,
    figures: @Composable () -> Unit,
) {
    val policy = remember(leadingGap, figuresGap, belowGap, lineGap, nameFits) {
        NameAndFiguresPolicy(leadingGap, figuresGap, belowGap, lineGap, nameFits)
    }
    Layout(contents = listOf(leading, name, below, figures), modifier = modifier, measurePolicy = policy)
}

private class NameAndFiguresPolicy(
    private val leadingGap: Dp,
    private val figuresGap: Dp,
    private val belowGap: Dp,
    private val lineGap: Dp,
    private val nameFits: ((Int, IntrinsicMeasurable) -> Boolean)?,
) : MultiContentMeasurePolicy {

    private fun fits(width: Int, name: IntrinsicMeasurable): Boolean =
        nameFits?.invoke(width, name) ?: (name.maxIntrinsicWidth(Constraints.Infinity) <= width)

    /** The name's room beside figures [figuresWidth] wide, or null where it is not whole there. */
    private fun Density.besideRoom(width: Int, leadingSpace: Int, figuresWidth: Int?, name: IntrinsicMeasurable?): Int? {
        val room = width - leadingSpace - (figuresWidth?.let { it + figuresGap.roundToPx() } ?: 0)
        return when {
            figuresWidth == null || name == null -> room.coerceAtLeast(0)
            room > 0 && fits(room, name) -> room
            else -> null
        }
    }

    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val bounded = constraints.hasBoundedWidth
        val width = constraints.maxWidth
        val leading = measurables[0].firstOrNull()?.measure(Constraints(maxWidth = if (bounded) width else Constraints.Infinity))
        val leadingSpace = leading?.let { it.width + leadingGap.roundToPx() } ?: 0
        val name = measurables[1].firstOrNull()
        val below = measurables[2].firstOrNull()
        // The figures are measured once, offered the whole line: that is their width beside the name, and the room
        // they have on a line of their own.
        val figures = measurables[3].firstOrNull()?.measure(Constraints(maxWidth = if (bounded) width else Constraints.Infinity))
        if (!bounded) {
            // Nothing to fit into: everything at its own width, on one line.
            val n = name?.measure(Constraints())
            val b = below?.measure(Constraints())
            val nameWidth = maxOf(n?.width ?: 0, b?.width ?: 0)
            return place(constraints, leading, leadingSpace, n, b, figures, nameWidth, beside = true)
        }
        val room = besideRoom(width, leadingSpace, figures?.width, name)
        val nameWidth = room ?: (width - leadingSpace).coerceAtLeast(0)
        val n = name?.measure(Constraints.fixedWidth(nameWidth))
        val b = below?.measure(Constraints.fixedWidth(nameWidth))
        return place(constraints, leading, leadingSpace, n, b, figures, nameWidth, beside = room != null)
    }

    private fun MeasureScope.place(
        constraints: Constraints,
        leading: Placeable?,
        leadingSpace: Int,
        name: Placeable?,
        below: Placeable?,
        figures: Placeable?,
        nameWidth: Int,
        beside: Boolean,
    ): MeasureResult {
        val belowSpace = below?.takeIf { it.height > 0 }?.let { it.height + belowGap.roundToPx() } ?: 0
        val block = (name?.height ?: 0) + belowSpace
        val figuresSpace = figures?.let { if (beside) it.width + figuresGap.roundToPx() else 0 } ?: 0
        // The first line: the picture and the name (with what is under it), centred on each other. Beside, the
        // figures are on it too; under, they have a line of their own after it.
        val first = maxOf(block, leading?.height ?: 0, if (beside) figures?.height ?: 0 else 0)
        val content = if (beside || figures == null) first else first + lineGap.roundToPx() + figures.height
        val width = (leadingSpace + nameWidth + figuresSpace).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = content.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            val top = (height - content) / 2
            leading?.placeRelative(0, top + (first - leading.height) / 2)
            val blockTop = top + (first - block) / 2
            name?.placeRelative(leadingSpace, blockTop)
            below?.placeRelative(leadingSpace, blockTop + (name?.height ?: 0) + belowGap.roundToPx())
            figures?.placeRelative(
                width - figures.width,
                if (beside) top + (first - figures.height) / 2 else top + first + lineGap.roundToPx(),
            )
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int {
        val leading = measurables[0].sumOf { it.maxIntrinsicWidth(height) + leadingGap.roundToPx() }
        // Figures are never broken: their least is their whole width.
        val text = ((measurables[1] + measurables[2]).map { it.minIntrinsicWidth(height) } +
            measurables[3].map { it.maxIntrinsicWidth(height) }).maxOrNull() ?: 0
        return leading + text
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int {
        val leading = measurables[0].sumOf { it.maxIntrinsicWidth(height) + leadingGap.roundToPx() }
        val name = (measurables[1] + measurables[2]).maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0
        val figures = measurables[3].sumOf { it.maxIntrinsicWidth(height) + figuresGap.roundToPx() }
        return leading + name + figures
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        intrinsicHeight(measurables, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        intrinsicHeight(measurables, width)

    private fun IntrinsicMeasureScope.intrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int {
        val leading = measurables[0].firstOrNull()
        val leadingWidth = leading?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val leadingSpace = leading?.let { leadingWidth + leadingGap.roundToPx() } ?: 0
        val name = measurables[1].firstOrNull()
        val figures = measurables[3].firstOrNull()
        val figuresWidth = figures?.maxIntrinsicWidth(Constraints.Infinity)
        val room = if (width == Constraints.Infinity) width else besideRoom(width, leadingSpace, figuresWidth, name)
        val nameWidth = room ?: (width - leadingSpace).coerceAtLeast(0)
        val nameHeight = name?.maxIntrinsicHeight(nameWidth) ?: 0
        val below = measurables[2].sumOf { it.maxIntrinsicHeight(nameWidth).let { h -> if (h > 0) h + belowGap.roundToPx() else 0 } }
        val figuresHeight = figures?.maxIntrinsicHeight(if (width == Constraints.Infinity) width else minOf(width, figuresWidth ?: width)) ?: 0
        val first = maxOf(nameHeight + below, leading?.maxIntrinsicHeight(leadingWidth) ?: 0)
        return if (room != null) maxOf(first, figuresHeight)
        else first + (figures?.let { figuresHeight + lineGap.roundToPx() } ?: 0)
    }
}

/**
 * A picker and the field it goes with, on one line (a token and its amount): the picker at its own width, the field
 * in the rest. Where that would leave the field less than [fieldMin] (a 320 dp screen, larger text, a long symbol),
 * the field takes a line of its own under the picker, as wide as the line, instead of showing a few digits. With no
 * field (nothing chosen yet), the picker takes the whole line.
 */
@Composable
fun PickerAndField(
    fieldMin: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    lineGap: Dp = 4.dp,
    picker: @Composable () -> Unit,
    field: @Composable () -> Unit,
) {
    Layout(contents = listOf(picker, field), modifier = modifier) { slots, constraints ->
        val picker = slots[0].firstOrNull()
        val field = slots[1].firstOrNull()
        val bounded = constraints.hasBoundedWidth
        val width = constraints.maxWidth
        if (field == null) {
            val p = picker?.measure(if (bounded) Constraints(minWidth = width, maxWidth = width) else Constraints())
            val w = if (bounded) width else maxOf(p?.width ?: 0, constraints.minWidth)
            val h = (p?.height ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
            return@Layout layout(w, h) { p?.placeRelative(0, (h - p.height) / 2) }
        }
        val gapPx = if (picker != null) gap.roundToPx() else 0
        val pickerWidth = picker?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val room = if (bounded) width - pickerWidth - gapPx else Constraints.Infinity
        if (room >= fieldMin.roundToPx()) {
            val p = picker?.measure(Constraints(maxWidth = if (bounded) minOf(pickerWidth, width) else Constraints.Infinity))
            val f = field.measure(if (bounded) Constraints.fixedWidth(room) else Constraints())
            val w = if (bounded) width else maxOf((p?.width ?: 0) + gapPx + f.width, constraints.minWidth)
            val h = maxOf(p?.height ?: 0, f.height).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(w, h) {
                p?.placeRelative(0, (h - p.height) / 2)
                f.placeRelative(w - f.width, (h - f.height) / 2)
            }
        } else {
            val p = picker?.measure(Constraints(maxWidth = width))
            val f = field.measure(Constraints.fixedWidth(width))
            val top = p?.let { it.height + lineGap.roundToPx() } ?: 0
            val h = (top + f.height).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, h) {
                p?.placeRelative(0, 0)
                f.placeRelative(0, top)
            }
        }
    }
}
