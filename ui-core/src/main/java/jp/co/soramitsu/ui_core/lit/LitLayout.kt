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
 * or a long name), the figures stand on a line of their own under it, at the end: a name is never cut short to make
 * room for figures, and the figures are never cut at all.
 *
 * [nameFits] says whether the name is whole at a width in pixels; by default, whether it fits on one line.
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

    /** The room the name has beside the figures, or null where the figures go under it. */
    private fun Density.besideWidth(width: Int, slots: List<List<IntrinsicMeasurable>>): Int? {
        val name = slots[1].firstOrNull() ?: return width
        val figures = slots[3].firstOrNull() ?: return width
        val leading = slots[0].firstOrNull()?.let { it.maxIntrinsicWidth(Constraints.Infinity) + leadingGap.roundToPx() } ?: 0
        val room = width - leading - figuresGap.roundToPx() - figures.maxIntrinsicWidth(Constraints.Infinity)
        return room.takeIf { it > 0 && fits(it, name) }
    }

    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val leading = measurables[0].firstOrNull()?.measure(Constraints())
        val leadingSpace = leading?.let { it.width + leadingGap.roundToPx() } ?: 0
        val name = measurables[1].firstOrNull()
        val below = measurables[2].firstOrNull()
        val figures = measurables[3].firstOrNull()
        if (!constraints.hasBoundedWidth) {
            // Nothing to fit into: everything at its own width, on one line.
            val n = name?.measure(Constraints())
            val b = below?.measure(Constraints())
            val f = figures?.measure(Constraints())
            val nameWidth = maxOf(n?.width ?: 0, b?.width ?: 0)
            return place(constraints, leading, leadingSpace, n, b, f, nameWidth, beside = true)
        }
        val width = constraints.maxWidth
        val room = besideWidth(width, measurables)
        return if (room != null) {
            val f = figures?.measure(Constraints(maxWidth = width))
            val nameWidth = (width - leadingSpace - (f?.let { it.width + figuresGap.roundToPx() } ?: 0)).coerceAtLeast(0)
            val n = name?.measure(Constraints.fixedWidth(nameWidth))
            val b = below?.measure(Constraints.fixedWidth(nameWidth))
            place(constraints, leading, leadingSpace, n, b, f, nameWidth, beside = true)
        } else {
            val nameWidth = (width - leadingSpace).coerceAtLeast(0)
            val n = name?.measure(Constraints.fixedWidth(nameWidth))
            val b = below?.measure(Constraints.fixedWidth(nameWidth))
            val f = figures?.measure(Constraints(maxWidth = nameWidth))
            place(constraints, leading, leadingSpace, n, b, f, nameWidth, beside = false)
        }
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
        val text = if (beside) maxOf(block, figures?.height ?: 0) else block + (figures?.let { it.height + lineGap.roundToPx() } ?: 0)
        val width = (leadingSpace + nameWidth + figuresSpace).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = maxOf(text, leading?.height ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            val top = (height - text) / 2
            // The picture stands by the name, as the row's first line; beside, the row is one line.
            leading?.placeRelative(0, if (beside) (height - leading.height) / 2 else top + (block - leading.height) / 2)
            val blockTop = if (beside) (height - block) / 2 else top
            name?.placeRelative(leadingSpace, blockTop)
            below?.placeRelative(leadingSpace, blockTop + (name?.height ?: 0) + belowGap.roundToPx())
            figures?.placeRelative(
                width - figures.width,
                if (beside) (height - figures.height) / 2 else top + block + lineGap.roundToPx(),
            )
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int {
        val leading = measurables[0].sumOf { it.minIntrinsicWidth(height) + leadingGap.roundToPx() }
        val text = (measurables[1] + measurables[2] + measurables[3]).maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0
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
        val figures = measurables[3].firstOrNull()
        val room = if (width == Constraints.Infinity) width else besideWidth(width, measurables)
        val nameWidth = room ?: (width - leadingSpace).coerceAtLeast(0)
        val name = measurables[1].sumOf { it.maxIntrinsicHeight(nameWidth) }
        val below = measurables[2].sumOf { it.maxIntrinsicHeight(nameWidth).let { h -> if (h > 0) h + belowGap.roundToPx() else 0 } }
        val figuresHeight = figures?.maxIntrinsicHeight(if (room != null) Constraints.Infinity else nameWidth) ?: 0
        val text = if (room != null) maxOf(name + below, figuresHeight)
        else name + below + (figures?.let { figuresHeight + lineGap.roundToPx() } ?: 0)
        return maxOf(text, leading?.maxIntrinsicHeight(leadingWidth) ?: 0)
    }
}

/**
 * A picker and the field it goes with, on one line (a token and its amount): the picker at its own width, the field
 * in the rest. Where that would leave the field less than [fieldMin] (a 320 dp screen, larger text, a long symbol),
 * the field takes a line of its own under the picker, as wide as the line, instead of showing a few digits.
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
        val gapPx = gap.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val pickerWidth = picker?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val room = if (width == Constraints.Infinity) Constraints.Infinity else width - pickerWidth - gapPx
        if (field == null || room >= fieldMin.roundToPx()) {
            val p = picker?.measure(Constraints(maxWidth = minOf(pickerWidth, width)))
            val f = field?.let {
                it.measure(if (room == Constraints.Infinity) Constraints() else Constraints.fixedWidth(room.coerceAtLeast(0)))
            }
            val w = if (width == Constraints.Infinity) (p?.width ?: 0) + gapPx + (f?.width ?: 0) else width
            val h = maxOf(p?.height ?: 0, f?.height ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(w, h) {
                p?.placeRelative(0, (h - p.height) / 2)
                f?.placeRelative(w - f.width, (h - f.height) / 2)
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
