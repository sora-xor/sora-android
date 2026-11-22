package jp.co.soramitsu.ui_core.lit

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One world, one light, two colours. The app is white and SORA red: a washi page, sheets of frosted glass lying over
 * it, soft controls raised from or let into those sheets, and cut red glass for the one thing a screen is for.
 * Everything is lit by the same window at the top left: light gathers on the near edges, shade falls to the bottom
 * right. The materials are rendered per pixel once and cached, so nothing here costs an offscreen pass while the
 * page scrolls.
 */
@Immutable
data class LitPalette(
    val dark: Boolean,
    val page: Color,
    /** Frosted glass over the page, as one flat colour: for surfaces with no sheet of their own. */
    val surface: Color,
    /** A raised pad, where it faces the light and where it turns away. */
    val padLit: Color,
    val padShade: Color,
    val wellFloor: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    /** A ruled line between rows, and the lit line just under it. */
    val rule: Color,
    val ruleLight: Color,
    /** The boundary of something that takes input. */
    val outline: Color,
    /** The red of cut glass, a tone deeper than the SORA mark so white type reads on it. */
    val red: Color,
    val redDeep: Color,
    val redBright: Color,
    /** The red as ink: glyphs and labels on white. */
    val redText: Color,
    /** The white set into red: glyphs and labels on it. */
    val inlay: Color,
    /** A real edge on sheets under the system's high-contrast setting: depth alone is not enough there. */
    val contrastEdge: Color? = null,
)

/** White paper and glass, sumi ink, SORA red. */
val LitDay = LitPalette(
    dark = false,
    page = Color(0xFFECE9E3),
    surface = Color(0xFFF6F4F0),
    padLit = Color(0xFFFDFCFA),
    padShade = Color(0xFFEFEDE8),
    wellFloor = Color(0xFFE4E1DA),
    ink = Color(0xFF232220),
    inkSecondary = Color(0xFF4C4A45),
    inkTertiary = Color(0xFF66625A),
    rule = Color(0x1A232220),
    ruleLight = Color(0xCCFFFFFF),
    outline = Color(0xFF817B70),
    red = Color(0xFFDA2530),
    redDeep = Color(0xFF9E1620),
    redBright = Color(0xFFF2403F),
    redText = Color(0xFFC21C26),
    inlay = Color(0xFFFFFAF2),
)

/** By night the same things are matte and warm charcoal: nothing glows, edges catch a tenth of the light. */
val LitNight = LitPalette(
    dark = true,
    page = Color(0xFF242220),
    surface = Color(0xFF302C2A),
    padLit = Color(0xFF3C3834),
    padShade = Color(0xFF34302C),
    wellFloor = Color(0xFF292624),
    ink = Color(0xFFF8F4EE),
    inkSecondary = Color(0xFFCEC6BB),
    inkTertiary = Color(0xFFA39A8E),
    rule = Color(0x47000000),
    ruleLight = Color(0x12FFFFFF),
    outline = Color(0xFFA39A8E),
    red = Color(0xFFBE382A),
    redDeep = Color(0xFF8F1C12),
    redBright = Color(0xFFE2452C),
    redText = Color(0xFFF18E79),
    inlay = Color(0xFFFEF9F4),
)

val LocalLitPalette = staticCompositionLocalOf { LitDay }

/** Corners: a sheet of glass, and a control on it. */
object Lit {
    val SheetRadius = 18.dp
    val ControlRadius = 12.dp
}

/**
 * The key light, moved by how the phone is held. While a screen that allows motion is in front, the rig reads the
 * phone's tilt and every surface answers: sheets of glass slide their sheen and shift their shadows, glints run
 * along their edges, the mica print on the page catches the light. Whatever posture the phone is held in becomes
 * "straight" within a few seconds, so the light rests where it was designed and moves only when the phone does. A
 * hot phone leaves the light still; the screens themselves keep away under Remove animations, power saving and on
 * slower phones. The position is read only while drawing, so a moving light never recomposes anything.
 */
object LitLightRig : SensorEventListener {
    private val xState = mutableFloatStateOf(0f)
    private val yState = mutableFloatStateOf(0f)

    /** Both axes run -1...1. */
    val x: Float get() = xState.floatValue
    val y: Float get() = yState.floatValue

    private var hosts = 0
    private var sensors: SensorManager? = null
    private var listening = false
    private var restX: Float? = null
    private var restY: Float? = null
    private var lastTickNanos = 0L
    private var watchesHeat = false

    /** Places the light. The rig calls this itself from the phone's tilt. */
    fun set(x: Float, y: Float) {
        xState.floatValue = x
        yState.floatValue = y
    }

    /** A screen that allows motion came to the front. Call on the main thread; pair with [leave]. */
    fun enter(context: Context) {
        hosts += 1
        refresh(context.applicationContext)
    }

    /** That screen left the front, or stopped allowing motion. */
    fun leave(context: Context) {
        hosts = (hosts - 1).coerceAtLeast(0)
        refresh(context.applicationContext)
    }

    private fun isHot(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE

    private fun refresh(context: Context) {
        if (!watchesHeat && hosts > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            watchesHeat = true
            context.getSystemService(PowerManager::class.java)?.addThermalStatusListener { refresh(context) }
        }
        val wanted = hosts > 0 && !isHot(context)
        if (wanted && !listening) {
            val manager = sensors ?: context.getSystemService(SensorManager::class.java)?.also { sensors = it } ?: return
            val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            restX = null
            restY = null
            lastTickNanos = 0L
            listening = manager.registerListener(this, sensor, TICK_MICROS)
        } else if (!wanted) {
            if (listening) {
                sensors?.unregisterListener(this)
                listening = false
            }
            // The light rests where it was designed.
            set(0f, 0f)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.timestamp - lastTickNanos < TICK_MICROS * 900L) return
        lastTickNanos = event.timestamp
        // Towards the ground, in the screen's axes, in g.
        val gx = -event.values[0] / SensorManager.GRAVITY_EARTH
        val gy = -event.values[1] / SensorManager.GRAVITY_EARTH
        // The posture the phone is held in drifts to "straight" in a few seconds.
        val baseX = restX ?: gx
        val baseY = restY ?: gy
        restX = baseX + (gx - baseX) * 0.012f
        restY = baseY + (gy - baseY) * 0.012f
        val targetX = ((gx - baseX) * 3.4f).coerceIn(-1f, 1f)
        val targetY = ((baseY - gy) * 3.4f).coerceIn(-1f, 1f)
        val nextX = x + (targetX - x) * 0.22f
        val nextY = y + (targetY - y) * 0.22f
        // A phone lying still draws no frames.
        if (abs(nextX - x) > 0.003f || abs(nextY - y) > 0.003f) set(nextX, nextY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Thirty readings a second. */
    private const val TICK_MICROS = 33_333
}

/**
 * Depth: things nearer the eye move further when the phone tilts. [depth] is the travel at full tilt; left out, a
 * sheet travels 4 dp and a small thing 2 dp.
 */
fun Modifier.litParallax(depth: Dp? = null): Modifier = graphicsLayer {
    val travel = (depth ?: if (size.height < 80.dp.toPx()) 2.dp else 4.dp).toPx()
    translationX = LitLightRig.x * travel
    translationY = LitLightRig.y * travel
}

/** The press every soft control shares: set down quickly, let up without a bounce. */
fun Modifier.litPress(pressed: Boolean, scale: Float = 0.97f, motion: Boolean = true): Modifier = composed {
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 70 else 140, easing = LinearOutSlowInEasing),
        label = "litPress",
    )
    graphicsLayer {
        val s = if (motion) 1f - (1f - scale) * press else 1f
        scaleX = s
        scaleY = s
        translationY = press * 1.dp.toPx()
    }
}

/** Rises into place and fades in, once, [delayMillis] after it is first seen. */
fun Modifier.litRise(motion: Boolean, distance: Dp = 12.dp, delayMillis: Int = 0): Modifier = composed {
    val move = remember { Animatable(if (motion) 0f else 1f) }
    val fade = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            delay(delayMillis.toLong())
            launch { fade.animateTo(1f, tween(260)) }
            move.animateTo(1f, tween(340, easing = CubicBezierEasing(0.16f, 0.84f, 0.3f, 1f)))
        }
    }
    graphicsLayer {
        translationY = (1f - move.value) * distance.toPx()
        alpha = fade.value
    }
}

private fun DrawScope.drawStretched(stretch: LitBake.Stretch, left: Float, top: Float, width: Float, height: Float) {
    val image = stretch.image
    val scale = image.width / stretch.sideDp
    val cornerSrc = (stretch.cornerDp * scale).roundToInt()
    val iw = image.width
    val ih = image.height
    // A stretch whose corners are larger than half the target shrinks them to fit.
    val cornerDst = stretch.cornerDp * density * min(1f, min(width, height) / (2f * stretch.cornerDp * density))
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
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/** An image whose ends stay as baked and whose middle stretches along its length. */
private fun DrawScope.drawStretchedAcross(stretch: LitBake.Stretch, left: Float, top: Float, width: Float, height: Float) {
    val image = stretch.image
    val scale = image.width / stretch.sideDp
    val capSrc = (stretch.cornerDp * scale).roundToInt()
    val iw = image.width
    val capDst = (stretch.cornerDp * density).coerceAtMost(width / 2f)
    val xs = intArrayOf(0, capSrc, iw - capSrc, iw)
    val dx = floatArrayOf(left, left + capDst, left + width - capDst, left + width)
    val t = top.roundToInt()
    val dh = (top + height).roundToInt() - t
    for (column in 0..2) {
        val sw = xs[column + 1] - xs[column]
        val l = dx[column].roundToInt()
        val dw = dx[column + 1].roundToInt() - l
        if (sw <= 0 || dw <= 0) continue
        drawImage(
            image,
            srcOffset = IntOffset(xs[column], 0),
            srcSize = IntSize(sw, image.height),
            dstOffset = IntOffset(l, t),
            dstSize = IntSize(dw, dh),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** A fixed piece, the thing it shows centred at [center]. */
private fun DrawScope.drawPiece(image: ImageBitmap, center: Offset, widthPx: Float, heightPx: Float, alpha: Float = 1f) {
    drawImage(
        image,
        dstOffset = IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()),
        dstSize = IntSize(widthPx.roundToInt(), heightPx.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

/** Sizes are baked in half-dp steps, so a few sizes serve every surface. */
private fun step(dp: Float): Float = (dp * 2f).roundToInt() / 2f

/** What a surface is made of. */
private enum class LitMaterial { GLASS, PAD }

/** The milk of a glass sheet, thicker towards the window, and the window itself as two soft streaks across it. */
private val MILK = floatArrayOf(
    0f, 0.72f, 0.20f, 0.66f, 0.24f, 0.72f, 0.32f, 0.70f, 0.36f, 0.61f, 0.42f, 0.575f,
    0.44f, 0.615f, 0.48f, 0.61f, 0.50f, 0.56f, 0.60f, 0.54f, 1f, 0.54f,
)

private fun milkStops(alpha: (Float) -> Float): Array<Pair<Float, Color>> =
    Array(MILK.size / 2) { MILK[it * 2] to Color.White.copy(alpha = alpha(MILK[it * 2 + 1])) }

private val MILK_DAY = milkStops { it }

/** By night the sheet is matte: only the streaks show, faintly. */
private val MILK_NIGHT = milkStops { (it - 0.54f) * 0.22f }

/**
 * What this modifier wraps stands on a sheet of frosted glass, or on a soft raised pad of the surface's own white
 * when it stands low ([elevation] under 12 dp). Glass has its milk, the window lying across it as
 * streaks that slide when the phone tilts, a polished edge with glints that run along it, and the light shadow
 * glass casts. Baked and stretched, so it is safe in lists. It floats a little nearer the eye than the page, so it
 * moves a little when the phone tilts.
 *
 * [face] is a sheet of another colour, for one that means something. [bounce] is a coloured light falling on its top
 * edge from above. [sheen] is how far a band of fresh light has crossed the sheet, 0...1. [backdrop] draws what
 * passes behind a sheet of day glass, blurred, under its milk: for the few sheets something really does.
 */
fun Modifier.litPlate(
    radius: Dp,
    elevation: Dp = 16.dp,
    face: Color? = null,
    bounce: Color? = null,
    bounceStrength: Float = 1f,
    sheen: (() -> Float)? = null,
    backdrop: (DrawScope.() -> Unit)? = null,
): Modifier = composed {
    val palette = LocalLitPalette.current
    val clip = remember { Path() }
    litParallax().drawBehind {
        drawPlate(radius.toPx(), elevation.toPx(), null, face, bounce, bounceStrength, sheen, backdrop, palette, clip)
    }
}

/** A sheet or pad in the outline of [shape]; small things stand lower. */
fun Modifier.litPlate(shape: Shape, elevation: Dp? = null, face: Color? = null): Modifier = composed {
    val palette = LocalLitPalette.current
    val clip = remember { Path() }
    litParallax().drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        val stands = elevation ?: if (size.height < 80.dp.toPx()) 9.dp else 16.dp
        drawPlate(radius, stands.toPx(), null, face, null, 1f, null, null, palette, clip)
    }
}

private fun DrawScope.drawPlate(
    radius: Float,
    elevation: Float,
    material: LitMaterial?,
    face: Color?,
    bounce: Color?,
    bounceStrength: Float,
    sheen: (() -> Float)?,
    backdrop: (DrawScope.() -> Unit)?,
    palette: LitPalette,
    clip: Path,
) {
    val px = density
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val r = min(radius, min(w, h) / 2f)
    val rDp = step(r / px)
    val liftDp = step(elevation / px)
    val glass = (material ?: if (liftDp >= 12f) LitMaterial.GLASS else LitMaterial.PAD) == LitMaterial.GLASS
    val dark = palette.dark
    val lx = LitLightRig.x
    val ly = LitLightRig.y

    // The shadow shifts against the light.
    val cast = if (glass) LitBake.glassShadow(rDp, liftDp, px, dark) else LitBake.padShadow(rDp, liftDp, px, dark)
    val reach = elevation * (if (glass) 0.26f else 0.18f)
    val m = cast.marginDp * px
    drawStretched(cast, -m - lx * reach, -m - ly * reach, w + 2f * m, h + 2f * m)

    val corner = CornerRadius(r, r)
    val plain = glass && face == null && palette.contrastEdge == null
    if (backdrop != null && plain && !dark) {
        clip.reset()
        clip.addRoundRect(RoundRect(0f, 0f, w, h, corner))
        clipPath(clip) { backdrop() }
    }
    when {
        face != null -> drawRoundRect(face, cornerRadius = corner)
        !glass -> drawRoundRect(
            Brush.linearGradient(listOf(palette.padLit, palette.padShade), start = Offset.Zero, end = Offset(w, h)),
            cornerRadius = corner,
        )
        !plain -> drawRoundRect(palette.surface, cornerRadius = corner)
        else -> {
            if (dark) drawRoundRect(palette.surface, cornerRadius = corner)
            // The window's streaks slide over the glass.
            val slideX = lx * 0.22f * w
            val slideY = ly * 0.22f * h
            val stops = if (dark) MILK_NIGHT else MILK_DAY
            drawRoundRect(
                Brush.linearGradient(*stops, start = Offset(slideX, slideY), end = Offset(w + slideX, h + slideY)),
                cornerRadius = corner,
            )
        }
    }

    val sweep = sheen?.invoke() ?: 0f
    val lit = bounce != null && !dark
    if (lit || (sweep > 0f && sweep < 1f)) {
        clip.reset()
        clip.addRoundRect(RoundRect(0f, 0f, w, h, corner))
        clipPath(clip) {
            if (bounce != null && lit) {
                // A coloured light from above, centred on the top edge, wider than it is deep.
                val reachX = w * 0.62f
                val centre = Offset(w * (0.5f + lx * 0.10f), 0f)
                scale(1f, 0.42f, pivot = centre) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to bounce.copy(alpha = 0.16f * bounceStrength),
                            0.45f to bounce.copy(alpha = 0.05f * bounceStrength),
                            1f to bounce.copy(alpha = 0f),
                            center = centre,
                            radius = reachX,
                        ),
                        reachX,
                        centre,
                    )
                }
            }
            if (sweep > 0f && sweep < 1f) {
                // A band of light crosses the sheet once: fresh data has arrived.
                val shown = when {
                    sweep < 0.15f -> sweep / 0.15f
                    sweep < 0.8f -> 1f
                    else -> (1f - sweep) / 0.2f
                }
                val across = (-1.3f + 2.6f * sweep) * w
                val strength = 0.50f * shown * (if (dark) 0.22f else 1f)
                drawRect(
                    Brush.linearGradient(
                        0.36f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = strength),
                        0.64f to Color.White.copy(alpha = 0f),
                        start = Offset(across, h * 0.2f),
                        end = Offset(across + w, h * 0.8f),
                    ),
                )
            }
        }
    }

    val edge = if (glass) LitBake.glassEdge(rDp, px, dark) else LitBake.padEdge(rDp, px, dark)
    drawStretched(edge, 0f, 0f, w, h)

    if (plain) {
        // Glints run along the near edges as the light moves.
        val glint = Color.White.copy(alpha = if (dark) 0.14f else 1f)
        val clear = Color.White.copy(alpha = 0f)
        val line = 1.2.dp.toPx()
        val inset = 0.2.dp.toPx()
        val along = w - 2f * r
        if (along > 0f) {
            val at = (0.30f + lx * 0.42f).coerceIn(0.12f, 0.88f)
            drawRect(
                Brush.horizontalGradient(0f to clear, 0.5f to glint, 1f to clear, startX = r + (at - 0.16f) * along, endX = r + (at + 0.16f) * along),
                topLeft = Offset(r, inset),
                size = Size(along, line),
            )
        }
        val down = h - 2f * r
        if (down > 0f) {
            val at = (0.26f + ly * 0.42f).coerceIn(0.12f, 0.88f)
            drawRect(
                Brush.verticalGradient(0f to clear, 0.5f to glint, 1f to clear, startY = r + (at - 0.20f) * down, endY = r + (at + 0.20f) * down),
                topLeft = Offset(inset, r),
                size = Size(line, down),
            )
        }
    }
    palette.contrastEdge?.let { drawRoundRect(it, cornerRadius = corner, style = Stroke(1.5.dp.toPx())) }
}

/**
 * A soft pad in the outline of [shape] that is raised at rest and let into the surface while [pressed]: every soft
 * control's two states.
 */
fun Modifier.litPad(shape: Shape, elevation: Dp = 10.dp, pressed: Boolean = false): Modifier = composed {
    val palette = LocalLitPalette.current
    val clip = remember { Path() }
    litParallax(2.dp).drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        if (pressed) {
            drawWell(radius, 2.5.dp.toPx(), null, false, palette)
        } else {
            drawPlate(radius, elevation.toPx(), LitMaterial.PAD, null, null, 1f, null, null, palette, clip)
        }
    }
}

fun Modifier.litPad(radius: Dp, elevation: Dp = 10.dp, pressed: Boolean = false): Modifier =
    litPad(RoundedCornerShape(radius), elevation, pressed)

/**
 * A pad let into the surface. [floor] tints it; by default it takes the palette's well colour. [ruled] adds a solid
 * boundary, for a well that takes input or counts something. Stretched from a baked corner, so any size costs nothing.
 */
fun Modifier.litWell(radius: Dp, depth: Dp = 2.5.dp, floor: Color? = null, ruled: Boolean = false): Modifier = composed {
    val palette = LocalLitPalette.current
    drawBehind { drawWell(radius.toPx(), depth.toPx(), floor, ruled, palette) }
}

private fun DrawScope.drawWell(radius: Float, depth: Float, floor: Color?, ruled: Boolean, palette: LitPalette) {
    val px = density
    val r = step(min(radius, min(size.width, size.height) / 2f) / px)
    val well = LitBake.well(r, depth / px, px, floor ?: palette.wellFloor, palette.outline.takeIf { ruled }, palette.dark)
    drawStretched(well, 0f, 0f, size.width, size.height)
}

/**
 * A filled button's face in cut glass of [color], behind whatever this modifier wraps. Its shadow and the light it
 * throws lie round it, and the window's reflection slides over its table as the phone tilts.
 */
fun Modifier.litCutGlass(color: Color, pressed: Boolean = false, radius: Dp = Lit.ControlRadius): Modifier = composed {
    val palette = LocalLitPalette.current
    val clip = remember { Path() }
    drawBehind {
        val scale = density
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawBehind
        val r = min(radius.toPx(), h / 2f)
        val heightDp = h / scale
        val rDp = step(r / scale)
        val bar = LitBake.cutGlassBar(heightDp, rDp, color, palette.inlay, pressed, scale, palette.dark)
        if (!pressed) LitBake.warmPressedBar(heightDp, rDp, color, palette.inlay, scale, palette.dark)
        val m = bar.marginDp * scale
        drawStretchedAcross(bar, -m, -m, w + 2f * m, h + 2f * m)
        clip.reset()
        clip.addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r)))
        clipPath(clip) {
            val slide = (LitLightRig.x * 0.55f - 0.22f) * w
            drawRect(
                Brush.linearGradient(
                    0.42f to Color.White.copy(alpha = 0f),
                    0.5f to Color.White.copy(alpha = 0.16f),
                    0.58f to Color.White.copy(alpha = 0f),
                    start = Offset(slide - w, h * 0.2f),
                    end = Offset(slide + 2f * w, h * 0.8f),
                ),
            )
        }
    }
}

/** The coverage of [painter] drawn [glyph] large in the middle of a box, for an inlay. */
private fun glyphMask(painter: Painter, glyph: Size, box: Size, density: Density): LitMask {
    val width = ceil(box.width).toInt().coerceAtLeast(1)
    val height = ceil(box.height).toInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap), box) {
        translate((box.width - glyph.width) / 2f, (box.height - glyph.height) / 2f) {
            with(painter) { draw(glyph) }
        }
    }
    val pixels = bitmap.toPixelMap()
    return LitMask(width, height, density.density, FloatArray(width * height) { pixels[it % width, it / width].alpha })
}

/**
 * A piece of cut red glass with a glyph inlaid in white: the one loud thing where it stands. Pressing sets it down
 * on the surface; its pool of red light tightens under it. [glyphKey] names the glyph for the cache. The canvas is
 * the piece's own size; its shadow lies up to 16 dp outside it.
 */
@Composable
fun LitCutGlassPiece(
    size: DpSize,
    radius: Dp,
    glyph: Painter,
    glyphSize: DpSize,
    glyphKey: String,
    pressed: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLitPalette.current
    val clip = remember { Path() }
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val scale = density
        val w = this.size.width
        val h = this.size.height
        val r = min(radius.toPx(), min(w, h) / 2f)
        val mask = { glyphMask(glyph, Size(glyphSize.width.toPx(), glyphSize.height.toPx()), this.size, this) }
        val piece = LitBake.cutGlassPiece(w / scale, h / scale, r / scale, palette.red, palette.inlay, glyphKey, pressed, scale, palette.dark, mask)
        val m = piece.marginDp * scale
        val settle = if (pressed) 1.dp.toPx() else 0f
        translate(0f, settle) {
            drawPiece(piece.image, center, w + 2f * m, h + 2f * m)
            clip.reset()
            clip.addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r)))
            clipPath(clip) {
                // The window's reflection slides over the table as the phone tilts.
                val slide = (LitLightRig.x * 0.75f - 0.30f) * w
                drawRect(
                    Brush.linearGradient(
                        0.30f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.20f),
                        0.70f to Color.White.copy(alpha = 0f),
                        start = Offset(slide - w, h * 0.35f),
                        end = Offset(slide + 2f * w, h * 0.65f),
                    ),
                )
            }
        }
    }
}

/**
 * A bead of red glass in a PIN place. When [filled] turns true it settles into its place and, by day, the red it
 * throws flares once as it lands; the digit counts before this starts. [motion] off shows it at once.
 */
@Composable
fun LitBead(filled: Boolean, diameter: Dp, motion: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalLitPalette.current
    val landed = remember { Animatable(1f) }
    val flare = remember { Animatable(0f) }
    var was by remember { mutableStateOf(filled) }
    LaunchedEffect(filled) {
        val appeared = filled && !was
        was = filled
        if (appeared && motion) {
            landed.snapTo(0f)
            launch { landed.animateTo(1f, tween(110, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f))) }
            if (!palette.dark) {
                delay(70)
                flare.snapTo(0f)
                flare.animateTo(1f, tween(126))
                flare.animateTo(0f, tween(294))
            }
        }
    }
    Canvas(modifier.size(diameter).clearAndSetSemantics { }) {
        if (!filled) return@Canvas
        val scale = density
        val d = diameter.toPx()
        if (flare.value > 0f) {
            val reach = d * 1.7f
            drawCircle(
                Brush.radialGradient(listOf(palette.redBright.copy(alpha = 0.55f * flare.value), palette.redBright.copy(alpha = 0f)), center = center, radius = reach),
                reach,
                center,
            )
        }
        val bead = LitBake.bead(d / scale, scale, palette.redDeep, palette.redBright, palette.dark)
        val side = d + 2f * bead.marginDp * scale
        val fall = -d * 0.35f * (1f - landed.value)
        drawPiece(bead.image, Offset(center.x, center.y + fall), side, side, alpha = (landed.value * 110f / 80f).coerceAtMost(1f))
    }
}

/**
 * A round thing as a printed mark: its contact shadow on the sheet below and a die-cut edge over it. The blank under
 * the picture is white, so a token whose icon has not loaded is a plain mark rather than a hole over its shadow.
 */
fun Modifier.litCoin(): Modifier = composed {
    val palette = LocalLitPalette.current
    drawWithContent {
        val scale = density
        val diameter = min(size.width, size.height)
        val shadow = LitBake.coinShadow(diameter / scale, scale, palette.dark)
        val side = diameter + 2f * shadow.marginDp * scale
        drawPiece(shadow.image, center, side, side)
        drawCircle(palette.padLit, radius = diameter / 2f, center = center)
        drawContent()
        drawPiece(LitBake.coin(diameter / scale, scale, palette.dark), center, diameter, diameter)
    }
}

/** A dot of ink in a colour: status and legend marks. */
@Composable
fun LitDot(color: Color, modifier: Modifier = Modifier, diameter: Dp = 8.dp) {
    Canvas(modifier.size(diameter).clearAndSetSemantics { }) {
        val scale = density
        val dot = LitBake.dot(color, diameter.toPx() / scale, scale)
        val side = diameter.toPx() + 2f * dot.marginDp * scale
        drawPiece(dot.image, center, side, side)
    }
}

private fun tile(image: ImageBitmap, zoom: Float): BitmapShader =
    BitmapShader(image.asAndroidBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setScale(zoom, zoom) })
    }

/** The page's print, faded down the page. Built once per size, never while scrolling. */
private class PagePrint(private val pattern: BitmapShader) {
    private var height = -1f
    private var brush: ShaderBrush? = null

    fun brush(top: Float, bottom: Float): ShaderBrush {
        brush?.takeIf { height == bottom - top }?.let { return it }
        val span = bottom - top
        val fade = LinearGradient(
            0f,
            top,
            0f,
            bottom,
            intArrayOf(Color.Black.toArgb(), Color.Black.toArgb(), Color.Black.copy(alpha = 0.25f).toArgb(), 0, 0),
            floatArrayOf(0f, 0.04f, 0.26f, 0.44f, 1f),
            Shader.TileMode.CLAMP,
        )
        height = span
        return ShaderBrush(ComposeShader(pattern, fade, PorterDuff.Mode.DST_IN)).also { brush = it }
    }
}

/**
 * The page: washi, light falling across it from the top left, and a shippō print in mica that shows most near the
 * top. The print lies a little deeper than the page's content and catches the light as the phone tilts; a soft pool
 * of light moves over the page with it.
 */
fun Modifier.litPage(): Modifier = composed {
    val palette = LocalLitPalette.current
    val density = LocalDensity.current.density
    val paper = remember(palette.page, density) {
        val tileScale = min(density, 2f)
        ShaderBrush(tile(LitBake.washi(palette.page, PAPER_FIBRES, PAPER_FLECKS, paperStrength(palette.dark), PAPER_SEED, tileScale), density / tileScale))
    }
    val print = remember(palette.dark, density) {
        val tileScale = min(density, 2f)
        PagePrint(tile(LitBake.shippo(tileScale, palette.dark), density / tileScale))
    }
    drawBehind {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawBehind
        val dark = palette.dark
        val lx = LitLightRig.x
        val ly = LitLightRig.y
        val tilt = min(1f, sqrt(lx * lx + ly * ly))
        drawRect(paper)
        // The print lies deeper than the content: it moves the other way.
        val bleed = 12.dp.toPx()
        translate(-lx * 7.dp.toPx(), -ly * 7.dp.toPx()) {
            drawRect(print.brush(-bleed, h + bleed), topLeft = Offset(-bleed, -bleed), size = Size(w + 2f * bleed, h + 2f * bleed), alpha = 0.72f + 0.28f * tilt)
        }
        // Light falls off from the top left.
        val source = Offset(w * 0.1f, 0f)
        scale(1f, h / w, pivot = source) {
            // Drawn over the whole page: beyond the gradient's reach its last shade carries on.
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.04f else 0.30f),
                    0.55f to Color.White.copy(alpha = 0f),
                    1f to (if (dark) Color.Black.copy(alpha = 0.20f) else Color(0x144D3D29)),
                    center = source,
                    radius = w * 1.1f,
                ),
                size = Size(w, w),
            )
        }
        if (tilt > 0f) {
            val pool = Offset(w * (0.28f + 0.46f * lx), h * (0.20f + 0.30f * ly))
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = (if (dark) 0.05f else 0.34f) * tilt), Color.White.copy(alpha = 0f)), center = pool, radius = w * 0.75f),
                w * 0.75f,
                pool,
            )
        }
    }
}

private const val PAPER_FIBRES = 110
private const val PAPER_FLECKS = 5
private const val PAPER_SEED = 11

/** By night the paper is matte: its fibres only just show, or they would read as scratches on the charcoal. */
private fun paperStrength(dark: Boolean) = if (dark) 0.12f else 0.6f

/** A line ruled into the surface: a dark edge and, just under it, a lit one. */
@Composable
fun LitEngravedLine(modifier: Modifier = Modifier) {
    val palette = LocalLitPalette.current
    val line = with(LocalDensity.current) { 2f.toDp() }
    Canvas(modifier.fillMaxWidth().height(line * 2).clearAndSetSemantics { }) {
        val half = size.height / 2f
        drawRect(palette.rule, size = Size(size.width, half))
        drawRect(palette.ruleLight, topLeft = Offset(0f, half), size = Size(size.width, half))
    }
}

/**
 * What the first screens wear, baked ahead of them on a background thread, so their first frame does not pay for it.
 * Each call asks for what the matching draw code will: anything missed here is simply baked when first drawn.
 */
class LitPreload(private val scale: Float, dark: Boolean) {
    private val palette = if (dark) LitNight else LitDay

    fun page() {
        val tileScale = min(scale, 2f)
        LitBake.washi(palette.page, PAPER_FIBRES, PAPER_FLECKS, paperStrength(palette.dark), PAPER_SEED, tileScale)
        LitBake.shippo(tileScale, palette.dark)
    }

    /** A sheet of glass, or a pad when [elevation] is under 12 dp. */
    fun plate(radius: Dp, elevation: Dp) {
        if (elevation.value >= 12f) {
            LitBake.glassShadow(radius.value, elevation.value, scale, palette.dark)
            LitBake.glassEdge(radius.value, scale, palette.dark)
        } else {
            LitBake.padShadow(radius.value, elevation.value, scale, palette.dark)
            LitBake.padEdge(radius.value, scale, palette.dark)
        }
    }

    fun well(radius: Dp, depth: Dp, ruled: Boolean = false) {
        LitBake.well(radius.value, depth.value, scale, palette.wellFloor, palette.outline.takeIf { ruled }, palette.dark)
    }

    /** A button's cut glass, at rest and pressed. Its height is measured, so it is whole pixels. */
    fun cutGlass(height: Dp, radius: Dp = Lit.ControlRadius) {
        val measured = (height.value * scale).roundToInt() / scale
        val r = step(min(radius.value, measured / 2f))
        LitBake.cutGlassBar(measured, r, palette.red, palette.inlay, false, scale, palette.dark)
        LitBake.cutGlassBar(measured, r, palette.red, palette.inlay, true, scale, palette.dark)
    }

    /** A coin is measured, so its size is whole pixels. */
    fun coin(diameter: Dp) {
        val measured = (diameter.value * scale).roundToInt() / scale
        LitBake.coinShadow(measured, scale, palette.dark)
        LitBake.coin(measured, scale, palette.dark)
    }
}
