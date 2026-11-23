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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.packFloats
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
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
    /** Both axes in one value: moving the light is one write, and a surface that draws under it reads once. */
    private val light = mutableLongStateOf(packFloats(0f, 0f))

    /** Both axes run -1...1. */
    val x: Float get() = unpackFloat1(light.longValue)
    val y: Float get() = unpackFloat2(light.longValue)

    /** Both axes at once, for a surface that needs both: x is [unpackFloat1], y is [unpackFloat2]. */
    internal val packed: Long get() = light.longValue

    private var hosts = 0
    private var sensors: SensorManager? = null
    private var listening = false
    private var restX: Float? = null
    private var restY: Float? = null
    private var lastTickNanos = 0L
    private var watchesHeat = false

    /** Places the light. The rig calls this itself from the phone's tilt. */
    fun set(x: Float, y: Float) {
        light.longValue = packFloats(x, y)
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
        val now = light.longValue
        val x = unpackFloat1(now)
        val y = unpackFloat2(now)
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
 *
 * The thing is moved where it is drawn, inside a layer of its own that never changes: a tilt redraws that layer and
 * nothing else. (Moving the layer itself made Compose work out again where every node under it lies, for every
 * surface, thirty times a second.) Touches land where the thing is laid out, at most [depth] from where it shows.
 */
fun Modifier.litParallax(depth: Dp? = null): Modifier =
    graphicsLayer().then(LitParallaxElement(depth ?: Dp.Unspecified))

/** How far a surface of this height travels at full tilt, in pixels. */
private fun DrawScope.litTravel(depth: Dp): Float =
    (if (depth.isSpecified) depth else if (size.height < 80.dp.toPx()) 2.dp else 4.dp).toPx()

private data class LitParallaxElement(val depth: Dp) : ModifierNodeElement<LitParallaxNode>() {
    override fun create() = LitParallaxNode(depth)

    override fun update(node: LitParallaxNode) {
        node.depth = depth
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "litParallax"
    }
}

private class LitParallaxNode(var depth: Dp) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        val light = LitLightRig.packed
        val travel = litTravel(depth)
        val dx = unpackFloat1(light) * travel
        val dy = unpackFloat2(light) * travel
        drawContext.transform.translate(dx, dy)
        drawContent()
        drawContext.transform.translate(-dx, -dy)
    }
}

/** The press every soft control shares: set down quickly, let up without a bounce. */
fun Modifier.litPress(pressed: Boolean, scale: Float = 0.97f, motion: Boolean = true): Modifier = composed {
    if (!motion) return@composed this
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(70, easing = LinearOutSlowInEasing) else
            spring(dampingRatio = .76f, stiffness = Spring.StiffnessMedium),
        label = "litPress",
    )
    graphicsLayer {
        val s = 1f - (1f - scale) * press
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
    // Drawn on every tilt for every surface: the nine pieces' edges are worked out without arrays.
    val x1 = left.roundToInt()
    val x2 = (left + cornerDst).roundToInt()
    val x3 = (left + width - cornerDst).roundToInt()
    val x4 = (left + width).roundToInt()
    val y1 = top.roundToInt()
    val y2 = (top + cornerDst).roundToInt()
    val y3 = (top + height - cornerDst).roundToInt()
    val y4 = (top + height).roundToInt()
    for (row in 0..2) {
        val sy = if (row == 0) 0 else if (row == 1) cornerSrc else ih - cornerSrc
        val sh = (if (row == 0) cornerSrc else if (row == 1) ih - cornerSrc else ih) - sy
        val t = if (row == 0) y1 else if (row == 1) y2 else y3
        val dh = (if (row == 0) y2 else if (row == 1) y3 else y4) - t
        if (sh <= 0 || dh <= 0) continue
        for (column in 0..2) {
            val sx = if (column == 0) 0 else if (column == 1) cornerSrc else iw - cornerSrc
            val sw = (if (column == 0) cornerSrc else if (column == 1) iw - cornerSrc else iw) - sx
            val l = if (column == 0) x1 else if (column == 1) x2 else x3
            val dw = (if (column == 0) x2 else if (column == 1) x3 else x4) - l
            if (sw <= 0 || dw <= 0) continue
            drawImage(
                image,
                srcOffset = IntOffset(sx, sy),
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
    val x1 = left.roundToInt()
    val x2 = (left + capDst).roundToInt()
    val x3 = (left + width - capDst).roundToInt()
    val x4 = (left + width).roundToInt()
    val t = top.roundToInt()
    val dh = (top + height).roundToInt() - t
    for (column in 0..2) {
        val sx = if (column == 0) 0 else if (column == 1) capSrc else iw - capSrc
        val sw = (if (column == 0) capSrc else if (column == 1) iw - capSrc else iw) - sx
        val l = if (column == 0) x1 else if (column == 1) x2 else x3
        val dw = (if (column == 0) x2 else if (column == 1) x3 else x4) - l
        if (sw <= 0 || dw <= 0) continue
        drawImage(
            image,
            srcOffset = IntOffset(sx, 0),
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
): Modifier = graphicsLayer().then(
    LitPlateElement(
        radius = radius,
        shape = null,
        elevation = elevation,
        material = null,
        face = face,
        bounce = bounce,
        bounceStrength = bounceStrength,
        sheen = sheen,
        backdrop = backdrop,
        pressed = false,
        travel = Dp.Unspecified,
    ),
)

/** A sheet or pad in the outline of [shape]; small things stand lower. */
fun Modifier.litPlate(shape: Shape, elevation: Dp? = null, face: Color? = null): Modifier = graphicsLayer().then(
    LitPlateElement(
        radius = Dp.Unspecified,
        shape = shape,
        elevation = elevation ?: Dp.Unspecified,
        material = null,
        face = face,
        bounce = null,
        bounceStrength = 1f,
        sheen = null,
        backdrop = null,
        pressed = false,
        travel = Dp.Unspecified,
    ),
)

/**
 * A sheet of glass or a pad behind what the modifier wraps, moved by the light with everything on it. One node
 * draws it, in a layer of its own that never changes: a tilt redraws that layer, and what stands on a clipped sheet
 * (a layer of its own, further in) is not drawn again. The outline comes from [radius], or from [shape] when that is
 * unspecified; an unspecified [elevation] or [travel] goes by the surface's height.
 */
private data class LitPlateElement(
    val radius: Dp,
    val shape: Shape?,
    val elevation: Dp,
    val material: LitMaterial?,
    val face: Color?,
    val bounce: Color?,
    val bounceStrength: Float,
    val sheen: (() -> Float)?,
    val backdrop: (DrawScope.() -> Unit)?,
    val pressed: Boolean,
    val travel: Dp,
) : ModifierNodeElement<LitPlateNode>() {
    override fun create() = LitPlateNode(this)

    override fun update(node: LitPlateNode) {
        node.spec = this
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = if (material == LitMaterial.PAD) "litPad" else "litPlate"
    }
}

private class LitPlateNode(var spec: LitPlateElement) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val clip = Path()
    private val bakes = PlateBakes()
    private val well = WellBake()
    private val corner = ShapeCorner()

    override fun ContentDrawScope.draw() {
        val spec = spec
        // Read while drawing: a new palette (day, night, high contrast) reaches this node as a redraw.
        val palette = currentValueOf(LocalLitPalette)
        val light = LitLightRig.packed
        val lx = unpackFloat1(light)
        val ly = unpackFloat2(light)
        val travel = litTravel(spec.travel)
        val radius = if (spec.shape != null) corner.of(spec.shape, this) else spec.radius.toPx()
        drawContext.transform.translate(lx * travel, ly * travel)
        if (spec.pressed) {
            drawWell(radius, 2.5.dp.toPx(), null, false, palette, well)
        } else {
            val stands = if (spec.elevation.isSpecified) spec.elevation else if (size.height < 80.dp.toPx()) 9.dp else 16.dp
            drawPlate(
                radius, stands.toPx(), spec.material, spec.face, spec.bounce, spec.bounceStrength, spec.sheen,
                spec.backdrop, palette, clip, lx, ly, bakes,
            )
        }
        drawContent()
        drawContext.transform.translate(-lx * travel, -ly * travel)
    }
}

/** The corner radius of a [Shape]'s outline at one size, worked out once rather than on every draw. */
private class ShapeCorner {
    private var shape: Shape? = null
    private var size = Size.Unspecified
    private var direction = LayoutDirection.Ltr
    private var density = 0f
    private var fontScale = 0f
    private var radius = 0f

    /** For the corner shapes the app has (dp and percent corners). A shape that reads other state is not remembered. */
    fun of(shape: Shape, scope: DrawScope): Float {
        if (shape !== this.shape || scope.size != size || scope.layoutDirection != direction ||
            scope.density != density || scope.fontScale != fontScale
        ) {
            this.shape = shape
            size = scope.size
            direction = scope.layoutDirection
            density = scope.density
            fontScale = scope.fontScale
            val outline = shape.createOutline(scope.size, scope.layoutDirection, scope)
            radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        }
        return radius
    }
}

/**
 * A surface's baked shadow and edge, kept by the node that draws them. They are the same from one draw to the next,
 * so only a new size, scale or theme asks the bakery again (asking builds a text key each time).
 */
private class PlateBakes {
    private var radius = Float.NaN
    private var lift = Float.NaN
    private var scale = 0f
    private var dark = false
    private var glass = false
    var cast: LitBake.Stretch? = null
        private set
    var edge: LitBake.Stretch? = null
        private set

    fun update(radius: Float, lift: Float, scale: Float, dark: Boolean, glass: Boolean) {
        if (cast != null && radius == this.radius && lift == this.lift && scale == this.scale && dark == this.dark && glass == this.glass) return
        this.radius = radius
        this.lift = lift
        this.scale = scale
        this.dark = dark
        this.glass = glass
        cast = if (glass) LitBake.glassShadow(radius, lift, scale, dark) else LitBake.padShadow(radius, lift, scale, dark)
        edge = if (glass) LitBake.glassEdge(radius, scale, dark) else LitBake.padEdge(radius, scale, dark)
    }
}

/** A well's baked image, kept the same way. */
private class WellBake {
    private var radius = Float.NaN
    private var depth = Float.NaN
    private var scale = 0f
    private var floor = Color.Unspecified
    private var outline: Color? = null
    private var dark = false
    private var image: LitBake.Stretch? = null

    fun of(radius: Float, depth: Float, scale: Float, floor: Color, outline: Color?, dark: Boolean): LitBake.Stretch {
        image?.let {
            if (radius == this.radius && depth == this.depth && scale == this.scale && floor == this.floor &&
                outline == this.outline && dark == this.dark
            ) {
                return it
            }
        }
        this.radius = radius
        this.depth = depth
        this.scale = scale
        this.floor = floor
        this.outline = outline
        this.dark = dark
        return LitBake.well(radius, depth, scale, floor, outline, dark).also { image = it }
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
    lx: Float,
    ly: Float,
    bakes: PlateBakes,
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
    bakes.update(rDp, liftDp, px, dark, glass)

    // The shadow shifts against the light.
    val cast = bakes.cast ?: return
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

    bakes.edge?.let { drawStretched(it, 0f, 0f, w, h) }

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
fun Modifier.litPad(shape: Shape, elevation: Dp = 10.dp, pressed: Boolean = false): Modifier = graphicsLayer().then(
    LitPlateElement(
        radius = Dp.Unspecified,
        shape = shape,
        elevation = elevation,
        material = LitMaterial.PAD,
        face = null,
        bounce = null,
        bounceStrength = 1f,
        sheen = null,
        backdrop = null,
        pressed = pressed,
        travel = 2.dp,
    ),
)

fun Modifier.litPad(radius: Dp, elevation: Dp = 10.dp, pressed: Boolean = false): Modifier =
    litPad(RoundedCornerShape(radius), elevation, pressed)

/**
 * A pad let into the surface. [floor] tints it; by default it takes the palette's well colour. [ruled] adds a solid
 * boundary, for a well that takes input or counts something. Stretched from a baked corner, so any size costs nothing.
 */
fun Modifier.litWell(radius: Dp, depth: Dp = 2.5.dp, floor: Color? = null, ruled: Boolean = false): Modifier =
    this.then(LitWellElement(radius, depth, floor, ruled))

private data class LitWellElement(val radius: Dp, val depth: Dp, val floor: Color?, val ruled: Boolean) :
    ModifierNodeElement<LitWellNode>() {
    override fun create() = LitWellNode(this)

    override fun update(node: LitWellNode) {
        node.spec = this
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "litWell"
    }
}

private class LitWellNode(var spec: LitWellElement) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val bake = WellBake()

    override fun ContentDrawScope.draw() {
        val spec = spec
        drawWell(spec.radius.toPx(), spec.depth.toPx(), spec.floor, spec.ruled, currentValueOf(LocalLitPalette), bake)
        drawContent()
    }
}

private fun DrawScope.drawWell(radius: Float, depth: Float, floor: Color?, ruled: Boolean, palette: LitPalette, bake: WellBake) {
    val px = density
    val r = step(min(radius, min(size.width, size.height) / 2f) / px)
    val well = bake.of(r, depth / px, px, floor ?: palette.wellFloor, palette.outline.takeIf { ruled }, palette.dark)
    drawStretched(well, 0f, 0f, size.width, size.height)
}

/**
 * A filled button's face in cut glass of [color], behind whatever this modifier wraps. Its shadow and the light it
 * throws lie round it, and the window's reflection slides over its table as the phone tilts.
 */
fun Modifier.litCutGlass(color: Color, pressed: Boolean = false, radius: Dp = Lit.ControlRadius): Modifier =
    this.then(LitCutGlassElement(color, pressed, radius))

private data class LitCutGlassElement(val color: Color, val pressed: Boolean, val radius: Dp) :
    ModifierNodeElement<LitCutGlassNode>() {
    override fun create() = LitCutGlassNode(this)

    override fun update(node: LitCutGlassNode) {
        node.spec = this
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "litCutGlass"
    }
}

private class LitCutGlassNode(var spec: LitCutGlassElement) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val clip = Path()

    // The slab last drawn: the same until the button's size, state or theme changes.
    private var bar: LitBake.Stretch? = null
    private var barHeight = Float.NaN
    private var barRadius = Float.NaN
    private var barScale = 0f
    private var barColor = Color.Unspecified
    private var barInlay = Color.Unspecified
    private var barPressed = false
    private var barDark = false

    override fun ContentDrawScope.draw() {
        val spec = spec
        val palette = currentValueOf(LocalLitPalette)
        val scale = density
        val w = size.width
        val h = size.height
        if (w > 0f && h > 0f) {
            val r = min(spec.radius.toPx(), h / 2f)
            val heightDp = h / scale
            val rDp = step(r / scale)
            var slab = bar
            if (slab == null || heightDp != barHeight || rDp != barRadius || scale != barScale || spec.color != barColor ||
                palette.inlay != barInlay || spec.pressed != barPressed || palette.dark != barDark
            ) {
                slab = LitBake.cutGlassBar(heightDp, rDp, spec.color, palette.inlay, spec.pressed, scale, palette.dark)
                if (!spec.pressed) LitBake.warmPressedBar(heightDp, rDp, spec.color, palette.inlay, scale, palette.dark)
                bar = slab
                barHeight = heightDp
                barRadius = rDp
                barScale = scale
                barColor = spec.color
                barInlay = palette.inlay
                barPressed = spec.pressed
                barDark = palette.dark
            }
            val m = slab.marginDp * scale
            drawStretchedAcross(slab, -m, -m, w + 2f * m, h + 2f * m)
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
        drawContent()
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
fun Modifier.litCoin(): Modifier = this.then(LitCoinElement)

private object LitCoinElement : ModifierNodeElement<LitCoinNode>() {
    override fun create() = LitCoinNode()

    override fun update(node: LitCoinNode) = Unit

    override fun hashCode(): Int = 0x11C01

    override fun equals(other: Any?): Boolean = other === this

    override fun InspectorInfo.inspectableProperties() {
        name = "litCoin"
    }
}

private class LitCoinNode : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    // A list of tokens draws many coins of one size: each keeps what it drew last.
    private var diameter = Float.NaN
    private var scale = 0f
    private var dark = false
    private var shadow: LitBake.Piece? = null
    private var rim: ImageBitmap? = null

    override fun ContentDrawScope.draw() {
        val palette = currentValueOf(LocalLitPalette)
        val scale = density
        val diameter = min(size.width, size.height)
        if (shadow == null || diameter != this@LitCoinNode.diameter || scale != this@LitCoinNode.scale || palette.dark != dark) {
            this@LitCoinNode.diameter = diameter
            this@LitCoinNode.scale = scale
            dark = palette.dark
            shadow = LitBake.coinShadow(diameter / scale, scale, palette.dark)
            rim = LitBake.coin(diameter / scale, scale, palette.dark)
        }
        val cast = shadow
        if (cast != null) {
            val side = diameter + 2f * cast.marginDp * scale
            drawPiece(cast.image, center, side, side)
        }
        drawCircle(palette.padLit, radius = diameter / 2f, center = center)
        drawContent()
        rim?.let { drawPiece(it, center, diameter, diameter) }
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
fun Modifier.litPage(): Modifier = graphicsLayer().then(LitPageElement)

private object LitPageElement : ModifierNodeElement<LitPageNode>() {
    override fun create() = LitPageNode()

    override fun update(node: LitPageNode) = Unit

    override fun hashCode(): Int = 0x11DA6

    override fun equals(other: Any?): Boolean = other === this

    override fun InspectorInfo.inspectableProperties() {
        name = "litPage"
    }
}

/**
 * The page draws in a layer of its own: the light moving over it redraws the page alone, not everything that lies
 * straight on it.
 */
private class LitPageNode : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var paper: ShaderBrush? = null
    private var print: PagePrint? = null
    private var page = Color.Unspecified
    private var dark = false
    private var scale = 0f

    override fun ContentDrawScope.draw() {
        val palette = currentValueOf(LocalLitPalette)
        val px = density
        val w = size.width
        val h = size.height
        if (w > 0f && h > 0f) {
            if (paper == null || palette.page != page || palette.dark != dark || px != scale) {
                page = palette.page
                dark = palette.dark
                scale = px
                val tileScale = min(px, 2f)
                paper = ShaderBrush(tile(LitBake.washi(palette.page, PAPER_FIBRES, PAPER_FLECKS, paperStrength(palette.dark), PAPER_SEED, tileScale), px / tileScale))
                print = PagePrint(tile(LitBake.shippo(tileScale, palette.dark), px / tileScale))
            }
            drawPage(palette.dark, paper, print)
        }
        drawContent()
    }
}

private fun DrawScope.drawPage(dark: Boolean, paper: ShaderBrush?, print: PagePrint?) {
    val w = size.width
    val h = size.height
    val light = LitLightRig.packed
    val lx = unpackFloat1(light)
    val ly = unpackFloat2(light)
    val tilt = min(1f, sqrt(lx * lx + ly * ly))
    if (paper != null) drawRect(paper)
    // The print lies deeper than the content: it moves the other way.
    if (print != null) {
        val bleed = 12.dp.toPx()
        translate(-lx * 7.dp.toPx(), -ly * 7.dp.toPx()) {
            drawRect(print.brush(-bleed, h + bleed), topLeft = Offset(-bleed, -bleed), size = Size(w + 2f * bleed, h + 2f * bleed), alpha = 0.72f + 0.28f * tilt)
        }
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
