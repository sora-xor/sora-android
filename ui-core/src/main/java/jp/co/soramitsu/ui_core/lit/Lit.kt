package jp.co.soramitsu.ui_core.lit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One world, one light. Every surface is lit by the same rig: a window at the top left (the key), the room as
 * fill, and the SORA planet as a coloured light. Shadows fall to the bottom right, the top-left edges catch the
 * light, wells are shaded from their top-left lip, and glossy things mirror the same window.
 */
@Immutable
data class LitPalette(
    val dark: Boolean,
    val page: Color,
    val plateLit: Color,
    val plateDeep: Color,
    val surface: Color,
    val wellFloor: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val shadow: Color,
    val contact: Color,
    val halo: Color,
    val haloAlpha: Float,
    val shadowStrength: Float,
    /** How strongly edges catch the light. */
    val edgeLight: Float,
    val edgeShade: Float,
    val engraveDark: Color,
    val engraveLight: Color,
    val pageLight: Color,
    val pageShade: Color,
    /** A real edge on plates under the system's high-contrast setting: depth alone is not enough there. */
    val outline: Color? = null,
) {
    companion object {
        /** The key light in the page's plane (towards the light), unit length. */
        const val KEY_X = -0.6f
        const val KEY_Y = -0.8f
    }
}

/** Warm porcelain, a little lighter where a plate faces the light. */
val LitDay = LitPalette(
    dark = false,
    page = Color(0xFFE8E2DE),
    plateLit = Color(0xFFFAF7F5),
    plateDeep = Color(0xFFF0EBE7),
    surface = Color(0xFFF6F2EF),
    wellFloor = Color(0xFFE7E1DD),
    ink = Color(0xFF2A1A1C),
    inkSecondary = Color(0xFF6F5D5E),
    inkTertiary = Color(0xFF8A7675),
    shadow = Color(0xFF6B4A40),
    contact = Color(0xFF3C2A26),
    halo = Color.White,
    haloAlpha = 0.95f,
    shadowStrength = 1f,
    edgeLight = 1f,
    edgeShade = 1f,
    engraveDark = Color(0x296B4B40),
    engraveLight = Color(0xD9FFFFFF),
    pageLight = Color(0x57FFFFFF),
    pageShade = Color(0x1A6B4A40),
)

/** The night palette keeps the classic plum, as enamel: the same light, far less of it. */
val LitNight = LitPalette(
    dark = true,
    page = Color(0xFF3D1F54),
    plateLit = Color(0xFF653A80),
    plateDeep = Color(0xFF522A6A),
    surface = Color(0xFF5B3173),
    wellFloor = Color(0xFF341A49),
    ink = Color(0xFFFCFCFC),
    inkSecondary = Color(0xFFD3B0CA),
    inkTertiary = Color(0xFFAB80B5),
    shadow = Color(0xFF12061C),
    contact = Color(0xFF0B0312),
    halo = Color(0xFFD9B6F0),
    haloAlpha = 0.20f,
    shadowStrength = 1.7f,
    edgeLight = 0.32f,
    edgeShade = 1.5f,
    engraveDark = Color(0x47120620),
    engraveLight = Color(0x1FFFFFFF),
    pageLight = Color(0x14FFFFFF),
    pageShade = Color(0x33120620),
)

val LocalLitPalette = staticCompositionLocalOf { LitDay }

/**
 * The key light, nudged by how the phone is held. Whoever owns the motion sensor (the Wallet home's parallax, which
 * runs only while the hero is on screen and stops for Reduce Motion and power saving) feeds it here. It is read
 * only while drawing, so a moving light never recomposes anything.
 */
object LitLightRig {
    private val xState = mutableFloatStateOf(0f)
    private val yState = mutableFloatStateOf(0f)

    val x: Float get() = xState.floatValue
    val y: Float get() = yState.floatValue

    fun set(x: Float, y: Float) {
        xState.floatValue = x
        yState.floatValue = y
    }
}

private fun DrawScope.drawStretched(stretch: LitBake.Stretch, left: Float, top: Float, width: Float, height: Float, alpha: Float = 1f) {
    val image = stretch.image
    val scale = image.width / stretch.sideDp
    val cornerSrc = (stretch.cornerDp * scale).roundToInt()
    val iw = image.width
    val ih = image.height
    // A stretch whose corners are larger than half the target shrinks them to fit.
    val fit = min(1f, min(width / (2f * cornerSrc), height / (2f * cornerSrc)))
    val cornerDst = (cornerSrc * fit)
    val xs = intArrayOf(0, cornerSrc, iw - cornerSrc, iw)
    val ys = intArrayOf(0, cornerSrc, ih - cornerSrc, ih)
    val dx = floatArrayOf(left, left + cornerDst, left + width - cornerDst, left + width)
    val dy = floatArrayOf(top, top + cornerDst, top + height - cornerDst, top + height)
    for (row in 0..2) {
        for (column in 0..2) {
            val sw = xs[column + 1] - xs[column]
            val sh = ys[row + 1] - ys[row]
            val l = dx[column].roundToInt()
            val t = dy[row].roundToInt()
            val dw = dx[column + 1].roundToInt() - l
            val dh = dy[row + 1].roundToInt() - t
            if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) continue
            drawImage(
                image,
                srcOffset = IntOffset(xs[column], ys[row]),
                srcSize = IntSize(sw, sh),
                dstOffset = IntOffset(l, t),
                dstSize = IntSize(dw, dh),
                alpha = alpha,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/** The whole image, centred at [center]. */
private fun DrawScope.drawCentred(image: ImageBitmap, center: Offset, sizePx: Float, alpha: Float = 1f) {
    val side = sizePx.roundToInt()
    drawImage(
        image,
        dstOffset = IntOffset((center.x - sizePx / 2f).roundToInt(), (center.y - sizePx / 2f).roundToInt()),
        dstSize = IntSize(side, side),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

/**
 * A raised porcelain plate behind whatever this modifier wraps: its shadows on the page, a face turned a little to
 * the light, a rounded lit edge and, when asked, [bounce] falling on its top edge from the planet above it and the
 * window's [sheen] sliding over the glaze as the phone turns. [sheenSweep] carries the sheen sideways, in plate
 * widths, for light crossing the plate. Baked and stretched, so it is safe in lists.
 */
fun Modifier.litPlate(
    radius: Dp,
    elevation: Dp = 16.dp,
    halo: Float = 1f,
    bounce: Color? = null,
    sheen: Boolean = false,
    faceTop: Color? = null,
    faceBottom: Color? = null,
    sheenSweep: (() -> Float)? = null,
): Modifier = composed {
    val palette = LocalLitPalette.current
    val grain = remember { ShaderBrush(ImageShader(LitBake.grain, TileMode.Repeated, TileMode.Repeated)) }
    drawBehind {
        drawPlate(radius.toPx(), elevation.toPx(), halo, bounce, sheen, sheenSweep, faceTop, faceBottom, palette, grain)
    }
}

/** A plate in the outline of [shape]; small things stand lower off the page. */
fun Modifier.litPlate(shape: Shape, elevation: Dp? = null, faceTop: Color? = null, faceBottom: Color? = null): Modifier = composed {
    val palette = LocalLitPalette.current
    val grain = remember { ShaderBrush(ImageShader(LitBake.grain, TileMode.Repeated, TileMode.Repeated)) }
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        val stands = elevation ?: if (size.height < 80.dp.toPx()) 8.dp else 16.dp
        drawPlate(radius, stands.toPx(), 1f, null, false, null, faceTop, faceBottom, palette, grain)
    }
}

/** The plate's rounded edge is this wide, in dp, for a corner of [radius] dp. */
internal fun plateBevel(radius: Float): Float = (radius * 0.34f).coerceIn(5f, 9f)

private fun DrawScope.drawPlate(
    radius: Float,
    elevation: Float,
    halo: Float,
    bounce: Color?,
    sheen: Boolean,
    sheenSweep: (() -> Float)?,
    faceTop: Color?,
    faceBottom: Color?,
    palette: LitPalette,
    grain: ShaderBrush,
) {
    val scale = density
    val w = size.width
    val h = size.height
    val r = min(radius, min(w, h) / 2f)
    val rDp = r / scale
    val shadow = LitBake.plateShadow(rDp, elevation / scale, halo, scale, palette)
    val reach = elevation * 0.16f
    val shiftX = LitLightRig.x * reach
    val shiftY = LitLightRig.y * reach
    val m = shadow.marginDp * scale
    drawStretched(shadow, -m + shiftX, -m + shiftY, w + 2f * m, h + 2f * m)
    val corner = CornerRadius(r, r)
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(faceTop ?: palette.plateLit, faceBottom ?: palette.plateDeep),
            start = Offset(w * 0.08f, 0f),
            end = Offset(w * 0.92f, h),
        ),
        size = size,
        cornerRadius = corner,
    )
    drawRoundRect(grain, size = size, cornerRadius = corner)
    if (bounce != null) {
        val rx = w * 0.62f
        drawRoundRect(
            brush = Brush.radialGradient(
                0f to bounce.copy(alpha = 0.18f),
                0.45f to bounce.copy(alpha = 0.055f),
                1f to bounce.copy(alpha = 0f),
                center = Offset(w * 0.5f, 0f),
                radius = rx * 0.62f,
            ),
            size = size,
            cornerRadius = corner,
        )
    }
    if (sheen) {
        val slide = LitLightRig.x * w * 0.52f + (sheenSweep?.invoke() ?: 0f) * w
        val down = LitLightRig.y * h * 0.5f
        val strength = if (palette.dark) 0.14f else 0.55f
        drawRoundRect(
            brush = Brush.linearGradient(
                0.34f to Color.White.copy(alpha = 0f),
                0.48f to Color.White.copy(alpha = strength),
                0.64f to Color.White.copy(alpha = 0f),
                start = Offset(-w * 0.6f + slide, h * 0.1f + down),
                end = Offset(w * 1.6f + slide, h * 0.9f + down),
            ),
            size = size,
            cornerRadius = corner,
        )
    }
    val edge = LitBake.plateEdge(rDp, plateBevel(rDp), scale, palette)
    drawStretched(edge, 0f, 0f, w, h)
    palette.outline?.let { drawRoundRect(it, size = size, cornerRadius = corner, style = Stroke(1.5.dp.toPx())) }
}

/** A recess pressed into a plate. [floor] tints it; by default it takes the palette's well colour. */
fun Modifier.litWell(radius: Dp, depth: Dp = 3.dp, floor: Color? = null): Modifier = composed {
    val palette = LocalLitPalette.current
    drawBehind {
        val scale = density
        val r = min(radius.toPx(), min(size.width, size.height) / 2f) / scale
        val well = LitBake.well(r, depth.toPx() / scale, scale, floor ?: palette.wellFloor, palette)
        val m = well.marginDp * scale
        drawStretched(well, -m, -m, size.width + 2f * m, size.height + 2f * m)
    }
}

/**
 * A round thing struck as a coin: its shadow on the plate below, a domed face and a turned rim over it. The blank
 * under the picture is porcelain, so a token whose icon has not loaded is a plain coin rather than a dark hole.
 */
fun Modifier.litCoin(): Modifier = composed {
    val blank = LocalLitPalette.current.plateLit
    drawWithContent {
        val scale = density
        val diameter = min(size.width, size.height) / scale
        val middle = Offset(size.width / 2f, size.height / 2f)
        val shadow = LitBake.coinShadow(diameter, scale)
        drawCentred(shadow.image, middle, shadow.sideDp * scale)
        drawCircle(blank, radius = diameter * scale / 2f, center = middle)
        drawContent()
        drawCentred(LitBake.coin(diameter, scale), middle, diameter * scale)
    }
}

/** Lacquer on a filled pill. The gloss goes over the fill; its own colour falls on the plate below it. */
fun Modifier.litLacquerShadow(color: Color, radius: Dp, strength: Float = 1f): Modifier = composed {
    val palette = LocalLitPalette.current
    val alpha = (if (palette.dark) 0.55f else 0.42f) * strength
    drawBehind {
        val r = min(radius.toPx(), size.height / 2f)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(
                8.dp.toPx(),
                2.5.dp.toPx(),
                5.dp.toPx(),
                androidx.compose.ui.graphics.Color(color.red, color.green, color.blue, alpha).toArgbInt(),
            )
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }
}

/** The gloss itself, drawn over the fill and under the label. */
fun Modifier.litLacquerGloss(radius: Dp): Modifier = drawBehind {
    val scale = density
    val h = size.height
    val r = min(radius.toPx(), h / 2f) / scale
    val stretch = LitBake.lacquer(h / scale, r, scale)
    drawStretchedHorizontally(stretch, size.width, h)
}

private fun DrawScope.drawStretchedHorizontally(stretch: LitBake.Stretch, width: Float, height: Float) {
    val image = stretch.image
    val pxPerDp = image.width / stretch.sideDp
    val cornerSrc = (stretch.cornerDp * pxPerDp).roundToInt()
    val iw = image.width
    val ih = image.height
    val xs = intArrayOf(0, cornerSrc, iw - cornerSrc, iw)
    val cornerDst = cornerSrc.toFloat().coerceAtMost(width / 2f)
    val dx = floatArrayOf(0f, cornerDst, width - cornerDst, width)
    for (column in 0..2) {
        val sw = xs[column + 1] - xs[column]
        val l = dx[column].roundToInt()
        val dw = dx[column + 1].roundToInt() - l
        if (sw <= 0 || dw <= 0) continue
        drawImage(
            image,
            srcOffset = IntOffset(xs[column], 0),
            srcSize = IntSize(sw, ih),
            dstOffset = IntOffset(l, 0),
            dstSize = IntSize(dw, ceil(height).toInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** Lacquer for a filled button of any shape: its own colour on the plate below it, the gloss over its face. */
fun Modifier.litLacquer(shape: Shape, color: Color): Modifier = composed {
    val palette = LocalLitPalette.current
    val alpha = if (palette.dark) 0.55f else 0.42f
    drawWithContent {
        val outline = shape.createOutline(size, layoutDirection, this)
        val r = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(8.dp.toPx(), 2.5.dp.toPx(), 5.dp.toPx(), Color(color.red, color.green, color.blue, alpha).toArgbInt())
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        drawContent()
        if (size.height > 0f) {
            val scale = density
            val stretch = LitBake.lacquer(size.height / scale, min(r, size.height / 2f) / scale, scale)
            drawStretchedHorizontally(stretch, size.width, size.height)
        }
    }
}

/** The page: porcelain, light falling off from the top left, a little grain. */
fun Modifier.litPage(): Modifier = composed {
    val palette = LocalLitPalette.current
    val grain = remember { ShaderBrush(ImageShader(LitBake.grain, TileMode.Repeated, TileMode.Repeated)) }
    drawBehind {
        drawRect(palette.page)
        drawRect(
            Brush.radialGradient(
                0f to palette.pageLight,
                0.55f to palette.pageLight.copy(alpha = 0f),
                1f to palette.pageShade,
                center = Offset(size.width * 0.1f, 0f),
                radius = max(size.width, size.height) * 1.1f,
            ),
        )
        drawRect(grain)
    }
}

/** A line cut in the glaze: a dark edge and, just under it, a lit one. */
@Composable
fun LitEngravedLine(modifier: Modifier = Modifier) {
    val palette = LocalLitPalette.current
    Canvas(modifier.fillMaxWidth().height(1.5.dp).clearAndSetSemantics { }) {
        val half = size.height / 2f
        drawRect(palette.engraveDark, size = Size(size.width, half))
        drawRect(palette.engraveLight, topLeft = Offset(0f, half), size = Size(size.width, half))
    }
}

/** A coloured status light. */
@Composable
fun LitLed(color: Color, modifier: Modifier = Modifier, diameter: Dp = 8.dp, alpha: () -> Float = { 1f }) {
    Canvas(modifier.size(diameter).clearAndSetSemantics { }) {
        val scale = density
        val led = LitBake.led(color, diameter.toPx() / scale, scale)
        drawCentred(led.image, Offset(size.width / 2f, size.height / 2f), led.sideDp * scale, alpha())
    }
}

/** A glass dome of a colour with its shadow, standing on its own. The canvas leaves room for the shadow. */
@Composable
fun LitDome(lit: Color, deep: Color, diameter: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(diameter + diameter * 0.46f).clearAndSetSemantics { }) {
        val scale = density
        val baked = LitBake.cabochon(lit, deep, diameter.toPx() / scale, scale)
        drawCentred(baked.image, Offset(size.width / 2f, size.height / 2f), baked.sideDp * scale)
    }
}

/**
 * A glass cabochon seated in a well pressed into the plate: the dome sinks into the well when pressed and pops up
 * in turn the first time it is seen ([enterDelayMillis], null for no entrance). Put the glyph in [content].
 */
@Composable
fun LitCabochon(
    lit: Color,
    deep: Color,
    pressed: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    dome: Dp = 50.dp,
    well: Dp = 62.dp,
    enterDelayMillis: Int? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(90) else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "domePress",
    )
    val enter = remember { Animatable(if (enterDelayMillis == null || reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enterDelayMillis != null && !reduceMotion) {
            kotlinx.coroutines.delay(enterDelayMillis.toLong())
            enter.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessLow))
        }
    }
    Box(
        modifier
            .size(well)
            .litWell(radius = well / 2, depth = 2.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(dome)
                .graphicsLayer {
                    val sink = if (reduceMotion) 1f else 1f - 0.07f * press
                    val pop = 0.2f + 0.8f * enter.value
                    scaleX = sink * pop
                    scaleY = sink * pop
                    alpha = (1f - 0.14f * press) * (enter.value * 4f).coerceAtMost(1f)
                }
                .drawBehind {
                    val scale = density
                    val baked = LitBake.cabochon(lit, deep, dome.toPx() / scale, scale)
                    drawCentred(baked.image, Offset(size.width / 2f, size.height / 2f), baked.sideDp * scale)
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(alpha, red, green, blue)

/**
 * What the first screens wear, baked ahead of them on a background thread, so their first frame does not pay for it.
 * Each call asks for what the matching draw code will: anything missed here is simply baked when first drawn.
 */
class LitPreload(private val scale: Float, dark: Boolean) {
    private val palette = if (dark) LitNight else LitDay

    private fun Dp.px(): Float = value * scale

    fun plate(radius: Dp, elevation: Dp, halo: Float = 1f) {
        val rDp = radius.px() / scale
        LitBake.plateShadow(rDp, elevation.px() / scale, halo, scale, palette)
        LitBake.plateEdge(rDp, plateBevel(rDp), scale, palette)
    }

    fun well(radius: Dp, depth: Dp) {
        LitBake.well(radius.px() / scale, depth.px() / scale, scale, palette.wellFloor, palette)
    }

    fun dome(lit: Color, deep: Color, diameter: Dp) {
        LitBake.cabochon(lit, deep, diameter.px() / scale, scale)
    }

    /** A coin is measured, so its size is whole pixels. */
    fun coin(diameter: Dp) {
        val measured = diameter.px().roundToInt() / scale
        LitBake.coinShadow(measured, scale)
        LitBake.coin(measured, scale)
    }

    fun lacquer(height: Dp, radius: Dp) {
        val measured = height.px().roundToInt().toFloat()
        LitBake.lacquer(measured / scale, min(radius.px(), measured / 2f) / scale, scale)
    }
}
