package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FanSlice
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.TiltState
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.tiltLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry of the sky in dp (portfolio-view SPEC §3.1). On Android the sky starts below the status
 * bar, so its top row sits where the home's usual top bar does ([TOP_ROW] down) and the sky is that
 * much taller. The SORA planet sits at [CENTER_Y]. Its ring is a flat band seen from just above, so
 * on screen it is widest at the sides and thinnest in front and behind, and every segment keeps its
 * share of the band's area.
 */
internal object SkyGeometry {
    val TOP_ROW: Dp = 24.dp
    val HEIGHT: Dp = 238.dp
    val CENTER_Y: Dp = 152.dp
    val PLANET_RADIUS: Dp = 46.dp

    /** The ring in its own plane: the band's centre line and width, and the two orbit lines. */
    val RING_RADIUS: Dp = 128.dp
    val RING_WIDTH: Dp = 13.dp
    val ORBIT_OUTER: Dp = 150.dp
    val ORBIT_INNER: Dp = 106.dp

    /** How far beyond the band, on screen, a tap still picks a segment. */
    val TAP_SLOP: Dp = 12.dp

    /** The ring plane at rest: its height over its width, and its roll (negative: right side up). */
    const val ASPECT = 0.31f
    const val ROLL_DEG = -10f

    /** How much more the plane opens and rolls at full deflection. */
    const val ASPECT_TILT = 0.07f
    const val ROLL_TILT_DEG = 6f

    /** Gap between two segments, in degrees of the ring's own circle. */
    const val GAP_DEG = 2.4f
    const val RAYS = 100
    const val DEG_PER_RAY = 360f / RAYS
    const val SELECTED_WIDTH = 1.35f
    const val DIMMED = 0.26f

    /** Seconds per lap of the light that runs round the ring. */
    const val COMET_LAP_S = 9f
    const val INTRO_MS = 1100

    /** Depths at full deflection, x then y (y is 0.65 × x). The planet and its ring move as one. */
    const val STARS = 3f
    const val NEBULA = 10f
    const val SYSTEM = 14f
    const val Y_RATIO = 0.65f
}

/** Key used for the Other slice of the fan. */
internal const val OTHER_KEY = "__other__"

/**
 * The ring plane on screen at one deflection. Angles are degrees of the ring's own circle: 0 at the
 * right, 90 in front (towards the viewer, below the planet's centre), growing clockwise.
 */
internal class RingPose(val center: Offset, val radius: Float, val aspect: Float, val rollDeg: Float) {
    private val cosRoll = cos(rollDeg * PI / 180).toFloat()
    private val sinRoll = sin(rollDeg * PI / 180).toFloat()

    /** The point of the plane's circle of radius [r] at [deg], on screen. */
    fun point(deg: Float, r: Float = radius): Offset {
        val t = deg * PI.toFloat() / 180f
        val x = r * cos(t)
        val y = r * aspect * sin(t)
        return Offset(center.x + x * cosRoll - y * sinRoll, center.y + x * sinRoll + y * cosRoll)
    }

    /** A screen point in the ring's own plane (roll undone, height restored). */
    fun toPlane(p: Offset): Offset {
        val dx = p.x - center.x
        val dy = p.y - center.y
        return Offset(dx * cosRoll + dy * sinRoll, (dy * cosRoll - dx * sinRoll) / aspect)
    }

    companion object {
        fun at(center: Offset, radius: Float, x: Float, y: Float): RingPose = RingPose(
            center = center,
            radius = radius,
            aspect = SkyGeometry.ASPECT + SkyGeometry.ASPECT_TILT * y,
            rollDeg = SkyGeometry.ROLL_DEG + SkyGeometry.ROLL_TILT_DEG * x,
        )
    }
}

/** One slice of the fan on the ring: its start and sweep in degrees. */
internal data class RingArc(val slice: FanSlice, val start: Float, val sweep: Float)

/**
 * Where the ring starts. The largest slice is centred behind the planet, so the stretch of ring the
 * planet hides always falls inside a slice that still shows on both sides of it; a small slice can
 * never disappear behind the planet, and the small slices come round to the front.
 */
internal fun ringStart(fan: List<FanSlice>): Float {
    val largest = fan.indices.maxByOrNull { fan[it].rays } ?: return 90f
    val before = fan.take(largest).sumOf { it.rays }
    return 270f - (before + fan[largest].rays / 2f) * SkyGeometry.DEG_PER_RAY
}

/** The fan laid round the ring, one ray per 3.6 degrees, in the fan's order from [ringStart]. */
internal fun ringArcs(fan: List<FanSlice>): List<RingArc> {
    var start = ringStart(fan)
    return fan.map { slice ->
        val sweep = slice.rays * SkyGeometry.DEG_PER_RAY
        RingArc(slice, start, sweep).also { start += sweep }
    }
}

/**
 * The slice under a tap: on the band or within [slop] of it on screen, and not on the half of the
 * ring that runs behind the planet's disc. The planet itself takes no taps.
 */
internal fun ringSliceAt(
    fan: List<FanSlice>,
    pose: RingPose,
    tap: Offset,
    band: Float,
    slop: Float,
    planetRadius: Float,
): FanSlice? {
    if (fan.isEmpty()) return null
    val q = pose.toPlane(tap)
    val r = hypot(q.x, q.y)
    if (r == 0f) return null
    val deg = ((atan2(q.y, q.x) * 180f / PI.toFloat()) + 360f) % 360f
    val t = deg * PI.toFloat() / 180f
    // Screen length of one plane unit along the radius at this angle: the plane is flattened.
    val radial = sqrt(cos(t) * cos(t) + pose.aspect * pose.aspect * sin(t) * sin(t))
    if (abs(r - pose.radius) > band / 2f + slop / radial) return null
    if (sin(t) < 0f && (tap - pose.center).getDistance() < planetRadius) return null
    val index = floor(((deg - ringStart(fan)) % 360f + 360f) % 360f / SkyGeometry.DEG_PER_RAY).toInt()
    return sliceAt(fan, index.coerceAtMost(SkyGeometry.RAYS - 1))
}

/**
 * The hero sky: night (dark) or dawn (light) gradient, a nebula with the planet's halo, twinkling
 * stars, and the SORA planet with the allocation ring round it. The ring is the chart: 3.6 degrees
 * of band per ray, coloured by the entity's slot, its back half passing behind the planet. A light
 * runs round it while the sky is on screen. Only these layers move with the tilt; [overlay] (scope
 * chip and buttons) never moves. The ring is decorative for accessibility; the legend chips are the
 * real buttons.
 */
@Composable
internal fun PortfolioSky(
    fan: List<FanSlice>,
    colorOf: (String?) -> Color,
    highlighted: String?,
    tilt: TiltState,
    onSliceTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val dark = isDarkTheme
    val fx = LocalFx.current
    val loops = active && fx.loops
    val glows = fx.glows
    // The sky fades out at its bottom edge into the app's aurora background behind the content.
    val background = remember(dark) {
        val top = if (dark) PortfolioPalette.nightTop else PortfolioPalette.dawnTop
        val bottom = if (dark) PortfolioPalette.nightBottom else PortfolioPalette.dawnBottom
        Brush.verticalGradient(0f to top, 0.72f to bottom, 1f to bottom.copy(alpha = 0f))
    }
    val clock = rememberSkyClock(loops)
    val intro = rememberRingIntro(fan.map { it.key }, animate = !fx.reduceMotion)
    // Reduce Motion: the highlight only fades in and out.
    val highlight by animateFloatAsState(if (highlighted != null) 1f else 0f, tween(220), label = "ringHighlight")
    val arcs = remember(fan) { ringArcs(fan) }
    val glyph = remember { soraGlyphPath() }
    val currentFan by rememberUpdatedState(fan)
    val currentTap by rememberUpdatedState(onSliceTap)
    Box(
        modifier
            .fillMaxWidth()
            .height(SkyGeometry.HEIGHT)
            .background(background)
    ) {
        // Nebula and the planet's halo: x only. The halo breathes while the sky is on screen.
        Canvas(
            Modifier
                .fillMaxSize()
                .tiltLayer(tilt, SkyGeometry.NEBULA, 0f)
                .semantics { hideFromAccessibility() }
        ) {
            val breath = if (loops) 0.88f + 0.12f * sin(clock.value * 2f * PI.toFloat() / 6f) else 1f
            drawNebula(dark, Offset(size.width / 2f, SkyGeometry.CENTER_Y.toPx()), breath)
        }
        if (dark) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .tiltLayer(tilt, SkyGeometry.STARS, SkyGeometry.STARS * SkyGeometry.Y_RATIO)
                    .semantics { hideFromAccessibility() }
            ) {
                drawStars(if (loops) clock.value else null)
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .tiltLayer(tilt, SkyGeometry.SYSTEM, SkyGeometry.SYSTEM * SkyGeometry.Y_RATIO)
                .semantics { hideFromAccessibility() }
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val pose = RingPose.at(
                            Offset(size.width / 2f, SkyGeometry.CENTER_Y.toPx()),
                            SkyGeometry.RING_RADIUS.toPx(),
                            tilt.x,
                            tilt.y,
                        )
                        ringSliceAt(
                            currentFan,
                            pose,
                            position,
                            SkyGeometry.RING_WIDTH.toPx(),
                            SkyGeometry.TAP_SLOP.toPx(),
                            SkyGeometry.PLANET_RADIUS.toPx(),
                        )?.let { currentTap(it.key ?: OTHER_KEY) }
                    }
                }
        ) {
            val pose = RingPose.at(
                Offset(size.width / 2f, SkyGeometry.CENTER_Y.toPx()),
                SkyGeometry.RING_RADIUS.toPx(),
                tilt.x,
                tilt.y,
            )
            val ring = RingStyle(
                arcs = arcs,
                colorOf = colorOf,
                highlighted = highlighted,
                highlight = highlight,
                reveal = 360f * intro.value,
                band = SkyGeometry.RING_WIDTH.toPx(),
                dark = dark,
                glows = glows,
                comet = if (loops) cometAngle(clock.value) else null,
                glint = 90f - 50f * tilt.x,
            )
            drawRingHalf(pose, ring, front = false)
            ring.comet?.let { drawCometBead(pose, it, front = false) }
            drawPlanet(pose.center, SkyGeometry.PLANET_RADIUS.toPx(), glyph, dark, glows, tilt.x, tilt.y)
            drawRingHalf(pose, ring, front = true)
            ring.comet?.let { drawCometBead(pose, it, front = true) }
        }
        overlay()
    }
}

/** Seconds since the sky started looping (wraps hourly); frozen while [running] is false. */
@Composable
private fun rememberSkyClock(running: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val offset = seconds.floatValue
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { now ->
                seconds.floatValue = (offset + (now - start) / 1000f) % 3600f
            }
        }
    }
    return seconds
}

/** The ring draws itself in, once per set of slices (first data, a new lens). */
@Composable
private fun rememberRingIntro(keys: List<String?>, animate: Boolean): State<Float> {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(keys, animate) {
        if (keys.isEmpty() || !animate) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(SkyGeometry.INTRO_MS, easing = FastOutSlowInEasing))
        }
    }
    return progress.asState()
}

/** The comet's angle: it laps the ring from the front, clockwise. */
private fun cometAngle(seconds: Float): Float = 90f + 360f * ((seconds / SkyGeometry.COMET_LAP_S) % 1f)

private class RingStyle(
    val arcs: List<RingArc>,
    val colorOf: (String?) -> Color,
    val highlighted: String?,
    val highlight: Float,
    val reveal: Float,
    val band: Float,
    val dark: Boolean,
    val glows: Boolean,
    val comet: Float?,
    val glint: Float,
)

/**
 * One half of the ring plane: the back half (behind the planet, fading towards the far edge) or the
 * front half. Drawn in the plane itself, so the band flattens with it.
 */
private fun DrawScope.drawRingHalf(pose: RingPose, ring: RingStyle, front: Boolean) {
    val c = pose.center
    val far = 4 * size.maxDimension
    withTransform({
        rotate(pose.rollDeg, c)
        scale(1f, pose.aspect, c)
        if (front) clipRect(-far, c.y, far, far) else clipRect(-far, -far, far, c.y)
    }) {
        val line = if (ring.dark) Color.White else PortfolioPalette.dawnInk
        drawCircle(
            line.copy(alpha = if (ring.dark) 0.16f else 0.12f),
            radius = SkyGeometry.ORBIT_OUTER.toPx(),
            center = c,
            style = Stroke(1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 9.dp.toPx()))),
        )
        drawCircle(line.copy(alpha = if (ring.dark) 0.09f else 0.07f), SkyGeometry.ORBIT_INNER.toPx(), c, style = Stroke(1.2.dp.toPx()))
        drawCircle(line.copy(alpha = if (ring.dark) 0.07f else 0.06f), pose.radius, c, style = Stroke(ring.band))

        val topLeft = Offset(c.x - pose.radius, c.y - pose.radius)
        val box = Size(pose.radius * 2, pose.radius * 2)
        val revealEnd = (ring.arcs.firstOrNull()?.start ?: 0f) + ring.reveal
        val gap = if (ring.arcs.size > 1) SkyGeometry.GAP_DEG else 0f
        ring.arcs.forEach { arc ->
            val sweep = min(arc.sweep - gap, revealEnd - arc.start - gap / 2f)
            if (sweep <= 0f) return@forEach
            val key = arc.slice.key ?: OTHER_KEY
            val selected = ring.highlighted == key
            val color = ring.colorOf(arc.slice.key)
            val alpha = if (selected) 1f else 1f - (1f - SkyGeometry.DIMMED) * ring.highlight
            val width = ring.band * if (selected) 1f + (SkyGeometry.SELECTED_WIDTH - 1f) * ring.highlight else 1f
            val start = arc.start + gap / 2f
            if (selected && ring.glows && ring.highlight > 0f) {
                drawArc(color.copy(alpha = 0.3f * ring.highlight), start, sweep, false, topLeft, box, style = Stroke(width * 1.9f))
            }
            val brush = if (front) {
                SolidColor(color.copy(alpha = alpha))
            } else {
                // The far edge of the ring is in the planet's shadow.
                Brush.verticalGradient(
                    0f to color.copy(alpha = alpha * 0.6f),
                    1f to color.copy(alpha = alpha),
                    startY = c.y - pose.radius,
                    endY = c.y,
                )
            }
            drawArc(brush, start, sweep, false, topLeft, box, style = Stroke(width, cap = StrokeCap.Butt))
        }
        // The light running round the band: brightest at its head.
        ring.comet?.let { head ->
            listOf(34f to 0.10f, 20f to 0.14f, 9f to 0.22f).forEach { (span, a) ->
                drawArc(Color.White.copy(alpha = a), head - span, span, false, topLeft, box, style = Stroke(ring.band))
            }
        }
        // A glint on the near side of the band, sliding with the tilt.
        if (front && ring.glows && ring.arcs.isNotEmpty()) {
            listOf(28f to 0.06f, 15f to 0.09f, 6f to 0.13f).forEach { (span, a) ->
                drawArc(Color.White.copy(alpha = a), ring.glint - span / 2f, span, false, topLeft, box, style = Stroke(ring.band))
            }
        }
    }
}

/** The comet's head: a round bead of light (not flattened), drawn on the half it is in. */
private fun DrawScope.drawCometBead(pose: RingPose, deg: Float, front: Boolean) {
    val inFront = sin(deg * PI.toFloat() / 180f) >= 0f
    if (inFront != front) return
    val p = pose.point(deg)
    val glow = 10.dp.toPx()
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.9f),
            0.35f to PortfolioPalette.soraGlow.copy(alpha = 0.45f),
            1f to Color.Transparent,
            center = p,
            radius = glow,
        ),
        radius = glow,
        center = p,
    )
    drawCircle(Color.White, radius = 2.2.dp.toPx(), center = p)
}

/**
 * The SORA planet: the brand's red disc with the 天 mark, shaded as a sphere lit from the top left,
 * a specular highlight that slides against the tilt, and a cool rim on the shadow side.
 */
private fun DrawScope.drawPlanet(c: Offset, r: Float, glyph: Path, dark: Boolean, glows: Boolean, tx: Float, ty: Float) {
    if (glows) {
        val atmosphere = r * 1.3f
        drawCircle(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.72f to Color.Transparent,
                0.79f to PortfolioPalette.soraRed.copy(alpha = if (dark) 0.6f else 0.32f),
                1f to Color.Transparent,
                center = c,
                radius = atmosphere,
            ),
            radius = atmosphere,
            center = c,
        )
    }
    val light = Offset(c.x - r * 0.34f, c.y - r * 0.38f)
    drawCircle(
        Brush.radialGradient(
            0f to PortfolioPalette.soraLight,
            0.42f to PortfolioPalette.soraRed,
            0.84f to PortfolioPalette.soraDeep,
            1f to PortfolioPalette.soraEdge,
            center = light,
            radius = r * 1.62f,
        ),
        radius = r,
        center = c,
    )
    // The gloss sits under the mark, so the 天 stays crisp.
    clipPath(Path().apply { addOval(Rect(c, r)) }) {
        val h = Offset(c.x - r * 0.4f - tx * r * 0.14f, c.y - r * 0.5f - ty * r * 0.1f)
        withTransform({
            rotate(-32f, h)
            scale(1f, 0.58f, h)
        }) {
            drawCircle(
                Brush.radialGradient(0f to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent, center = h, radius = r * 0.5f),
                radius = r * 0.5f,
                center = h,
            )
        }
    }
    // The mark is printed on the sphere, so the shade below turns it away with the body.
    withTransform({
        translate(c.x - r, c.y - r)
        scale(2f * r / SORA_GLYPH_VIEWPORT, 2f * r / SORA_GLYPH_VIEWPORT, Offset.Zero)
    }) {
        drawPath(glyph, Color.White)
    }
    drawCircle(
        Brush.radialGradient(
            0f to Color.Transparent,
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.34f),
            center = light,
            radius = r * 1.62f,
        ),
        radius = r,
        center = c,
    )
    val rim = if (dark) PortfolioPalette.rimNight else Color.White.copy(alpha = 0.85f)
    drawCircle(
        Brush.linearGradient(
            0.4f to Color.Transparent,
            1f to rim,
            start = Offset(c.x - r, c.y - r),
            end = Offset(c.x + r, c.y + r),
        ),
        radius = r - 0.75.dp.toPx(),
        center = c,
        style = Stroke(1.5.dp.toPx()),
    )
}

/** Violet and cyan haze in the corners and the red halo behind the planet. */
private fun DrawScope.drawNebula(dark: Boolean, planet: Offset, breath: Float) {
    fun haze(color: Color, alpha: Float, center: Offset, radius: Float) = drawCircle(
        Brush.radialGradient(0f to color.copy(alpha = alpha), 1f to Color.Transparent, center = center, radius = radius),
        radius = radius,
        center = center,
    )
    haze(PortfolioPalette.hazeViolet, if (dark) 0.16f else 0.05f, Offset(size.width * 0.1f, size.height * 0.32f), size.width * 0.62f)
    haze(PortfolioPalette.hazeCyan, if (dark) 0.10f else 0.05f, Offset(size.width * 0.92f, size.height * 0.12f), size.width * 0.5f)
    val halo = 175.dp.toPx()
    val core = (if (dark) 0.5f else 0.22f) * breath
    drawCircle(
        Brush.radialGradient(
            0f to PortfolioPalette.soraGlow.copy(alpha = core),
            0.38f to PortfolioPalette.soraDeep.copy(alpha = core * 0.44f),
            1f to Color.Transparent,
            center = planet,
            radius = halo,
        ),
        radius = halo,
        center = planet,
    )
}

/** A deterministic speckle (the same on every launch), and a few brighter stars with a cross flare. */
private fun DrawScope.drawStars(seconds: Float?) {
    var seed = 11L
    fun rnd(): Float {
        seed = (seed * 9301 + 49297) % 233280
        return seed / 233280f
    }
    repeat(STAR_COUNT) { i ->
        val x = rnd() * size.width
        val y = rnd() * size.height * 0.82f
        val alpha = 0.22f + 0.34f * rnd()
        drawCircle(
            PortfolioPalette.starlight.copy(alpha = alpha),
            radius = (if (i % 6 == 0) 1.3f else 0.8f) * density,
            center = Offset(x, y),
        )
    }
    repeat(FLARE_COUNT) { i ->
        val p = Offset(rnd() * size.width, (0.08f + rnd() * 0.5f) * size.height)
        val phase = rnd()
        val twinkle = seconds?.let { 0.55f + 0.45f * sin(2f * PI.toFloat() * (it / (2.6f + i) + phase)) } ?: 0.8f
        val arm = (5f + 2f * (i % 2)) * density
        val color = PortfolioPalette.starlight.copy(alpha = 0.7f * twinkle)
        val flare = color.copy(alpha = 0.45f * twinkle)
        drawLine(flare, p - Offset(arm, 0f), p + Offset(arm, 0f), strokeWidth = 0.8f * density)
        drawLine(flare, p - Offset(0f, arm), p + Offset(0f, arm), strokeWidth = 0.8f * density)
        drawCircle(color, radius = 1.4f * density, center = p)
    }
}

/** The 天 mark of the SORA logo on a 22-unit disc (the XOR token icon's own outline). */
internal fun soraGlyphPath(): Path = PathParser().parsePathString(SORA_GLYPH).toPath()

internal const val SORA_GLYPH_VIEWPORT = 22f
private const val SORA_GLYPH =
    "M5.8,20.7c1.7,-2.6 3.5,-5.2 5.3,-7.8l5.2,7.8c0.3,-0.1 0.5,-0.3 0.8,-0.5s0.5,-0.3 0.7,-0.5" +
        "c-1.9,-2.9 -3.9,-5.8 -5.8,-8.7h5.8V9.2H12V7.3h5.8V5.5H4.3v1.8h5.8v1.9H4.3V11h5.8l-5.8,8.7" +
        "C4.5,19.9 4.7,20 5,20.2C5.3,20.4 5.5,20.6 5.8,20.7z"

private fun sliceAt(fan: List<FanSlice>, rayIndex: Int): FanSlice? {
    if (rayIndex < 0) return null
    var start = 0
    fan.forEach { slice ->
        if (rayIndex < start + slice.rays) return slice
        start += slice.rays
    }
    return null
}

private const val STAR_COUNT = 30
private const val FLARE_COUNT = 5
