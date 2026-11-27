package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FanSlice
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.TiltState
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.layerOffset
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.tiltLayer
import jp.co.soramitsu.ui_core.lit.LitHeroFrame
import jp.co.soramitsu.ui_core.lit.LitHeroPerformance
import jp.co.soramitsu.ui_core.lit.LitHeroRenderer
import jp.co.soramitsu.ui_core.lit.LitHeroSegment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch

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

    /** Over this much the sky fades in from the page colour the status bar above it keeps. */
    val TOP_FADE: Dp = 72.dp

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

    const val INTRO_MS = 1100

    /** Scrolling: the planet lags by this share, so the card slides over it. */
    const val SCROLL_LAG = 0.24f

    /** Pulled past the top by this many dp, the planet has swollen by [SWELL]. */
    const val SWELL_PULL = 140f
    const val SWELL = 0.14f

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
 * of band per ray, coloured by the entity's slot, its back half passing behind the planet. It draws
 * itself in once; after that only the tilt moves these layers. [overlay] (scope chip and buttons)
 * never moves. The ring is decorative for accessibility; the legend chips are the real buttons.
 */
@Composable
internal fun PortfolioSky(
    fan: List<FanSlice>,
    colorOf: (String?) -> Color,
    highlighted: String?,
    tilt: TiltState,
    onSliceTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    heroVisible: Boolean = true,
    covered: Boolean = false,
    reading: Boolean = false,
    scrollDp: () -> Float = { 0f },
    pullDp: () -> Float = { 0f },
    capture: GraphicsLayer? = null,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val dark = isDarkTheme
    val fx = LocalFx.current
    val page = MaterialTheme.neon.canvas
    val glyphForLit = remember { soraGlyphPath() }
    val litHero = remember { LitHeroRenderer(glyphForLit.asAndroidPath(), SORA_GLYPH_VIEWPORT) }
    // The sky fades out at its bottom edge into the page behind the content.
    val background = remember(dark, litHero) {
        if (litHero.isAvailable) {
            SolidColor(Color.Transparent)
        } else {
            val top = if (dark) PortfolioPalette.nightTop else PortfolioPalette.dawnTop
            val bottom = if (dark) PortfolioPalette.nightBottom else PortfolioPalette.dawnBottom
            Brush.verticalGradient(0f to top, 0.72f to bottom, 1f to bottom.copy(alpha = 0f))
        }
    }
    val intro = rememberRingIntro(fan.map { it.key }, animate = !fx.reduceMotion)
    val comet = rememberComet(
        running = litHero.isAvailable && heroVisible && !covered && fx.litMotion && !LitHeroPerformance.isSlow,
        reading = reading,
    )
    val bloom = rememberBloom(reading, enabled = litHero.isAvailable && !fx.reduceMotion)
    LaunchedEffect(LitHeroPerformance.isSlow) {
        if (LitHeroPerformance.isSlow) LitHeroPerformance.restThenRetry()
    }
    // Reduce Motion: the highlight only fades in and out.
    val highlight by animateFloatAsState(if (highlighted != null) 1f else 0f, tween(220), label = "ringHighlight")
    val arcs = remember(fan) { ringArcs(fan) }
    val glyph = remember { soraGlyphPath() }
    val currentFan by rememberUpdatedState(fan)
    val currentTap by rememberUpdatedState(onSliceTap)
    val currentScroll by rememberUpdatedState(scrollDp)
    Box(
        modifier
            .fillMaxWidth()
            .height(SkyGeometry.HEIGHT)
            .background(background)
    ) {
        if (litHero.isAvailable) {
            // The ray-traced scene: planet, ring and what they cast on the page, all in one pass.
            Canvas(
                Modifier
                    .fillMaxSize()
                    // The scene is also kept as a picture of its own, for the glass it passes behind.
                    .then(
                        if (capture != null) {
                            Modifier.drawWithContent {
                                capture.record { this@drawWithContent.drawContent() }
                                drawLayer(capture)
                            }
                        } else {
                            Modifier
                        },
                    )
                    // Scrolling moves the finished picture: the shader runs only when the scene itself changes.
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        translationY = (scrollDp() * SkyGeometry.SCROLL_LAG).dp.toPx()
                        val swell = 1f + min(pullDp(), SkyGeometry.SWELL_PULL) / SkyGeometry.SWELL_PULL * SkyGeometry.SWELL
                        scaleX = swell
                        scaleY = swell
                        transformOrigin = TransformOrigin(0.5f, SkyGeometry.CENTER_Y / SkyGeometry.HEIGHT)
                    }
                    .semantics { hideFromAccessibility() }
            ) {
                if (!heroVisible) return@Canvas
                val shift = layerOffset(
                    tilt.x.toDouble(),
                    tilt.y.toDouble(),
                    SkyGeometry.SYSTEM.toDouble(),
                    (SkyGeometry.SYSTEM * SkyGeometry.Y_RATIO).toDouble(),
                    1.0,
                )
                val pose = RingPose.at(Offset.Zero, 1f, tilt.x, tilt.y)
                val radians = (PI / 180f).toFloat()
                val gap = if (arcs.size > 1) SkyGeometry.GAP_DEG else 0f
                val reveal = intro.value
                litHero.draw(
                    this,
                    LitHeroFrame(
                        center = Offset(
                            size.width / density / 2f + shift.dx.toFloat(),
                            SkyGeometry.CENTER_Y.value + shift.dy.toFloat(),
                        ),
                        planetRadius = SkyGeometry.PLANET_RADIUS.value,
                        ringRadius = SkyGeometry.RING_RADIUS.value,
                        ringWidth = SkyGeometry.RING_WIDTH.value,
                        ringThickness = 3.2f,
                        outerOrbit = SkyGeometry.ORBIT_OUTER.value,
                        aspect = pose.aspect,
                        rollRadians = pose.rollDeg * radians,
                        tiltX = tilt.x,
                        tiltY = tilt.y,
                        reveal = reveal,
                        cometAngle = comet.angle,
                        cometPower = comet.power,
                        bottomFade = SkyGeometry.HEIGHT.value * 0.24f,
                        dark = dark,
                        ringStart = (arcs.firstOrNull()?.start ?: 0f) * radians,
                        segments = arcs.map { arc ->
                            val key = arc.slice.key ?: OTHER_KEY
                            val selected = highlighted == key
                            LitHeroSegment(
                                start = (arc.start + gap / 2f) * radians,
                                sweep = (arc.sweep - gap).coerceAtLeast(0.6f) * radians,
                                color = colorOf(arc.slice.key),
                                strength = if (selected) 1f else 1f - (1f - SkyGeometry.DIMMED) * highlight,
                                widthScale = if (selected) 1f + (SkyGeometry.SELECTED_WIDTH - 1f) * highlight else 1f,
                                lift = if (selected) highlight else 0f,
                            )
                        },
                        bloom = bloom.value,
                    ),
                )
            }
        }
        // Nebula and the planet's halo: x only.
        if (!litHero.isAvailable) Canvas(
            Modifier
                .fillMaxSize()
                .tiltLayer(tilt, SkyGeometry.NEBULA, 0f)
                .semantics { hideFromAccessibility() }
        ) {
            drawNebula(dark, Offset(size.width / 2f, SkyGeometry.CENTER_Y.toPx()))
        }
        if (dark && !litHero.isAvailable) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .tiltLayer(tilt, SkyGeometry.STARS, SkyGeometry.STARS * SkyGeometry.Y_RATIO)
                    .semantics { hideFromAccessibility() }
            ) {
                drawStars()
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .tiltLayer(tilt, SkyGeometry.SYSTEM, SkyGeometry.SYSTEM * SkyGeometry.Y_RATIO)
                .semantics { hideFromAccessibility() }
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        // The ring is where it is drawn: lower than its rest, once the page has scrolled.
                        val lag = if (litHero.isAvailable) (currentScroll() * SkyGeometry.SCROLL_LAG).dp.toPx() else 0f
                        val pose = RingPose.at(
                            Offset(size.width / 2f, SkyGeometry.CENTER_Y.toPx() + lag),
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
            if (litHero.isAvailable) return@Canvas
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
            )
            drawRingHalf(pose, ring, front = false)
            drawPlanet(pose.center, SkyGeometry.PLANET_RADIUS.toPx(), glyph, dark, tilt.x, tilt.y)
            drawRingHalf(pose, ring, front = true)
        }
        if (!litHero.isAvailable) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SkyGeometry.TOP_FADE)
                    .background(Brush.verticalGradient(listOf(page, page.copy(alpha = 0f))))
            )
        }
        overlay()
    }
}

/** The light that laps the ring while the sky is on screen, faster while balances are being read. */
@Stable
private class CometState {
    var angle by mutableFloatStateOf((PI / 2).toFloat())
    var power by mutableFloatStateOf(0f)
}

@Composable
private fun rememberComet(running: Boolean, reading: Boolean): CometState {
    val state = remember { CometState() }
    val readingNow by rememberUpdatedState(reading)
    LaunchedEffect(running) {
        var last = 0L
        var stepped = 0L
        var due = 0L
        var started = 0L
        var late = 0
        while (running || state.power > 0.002f) {
            withFrameNanos { now ->
                if (started == 0L) started = now
                // Whatever else the first moments on screen are busy with is not the GPU's fault.
                if (running && last != 0L && now - started > COMET_SETTLE_NANOS) {
                    late = if (now - last > COMET_LATE_NANOS) late + 1 else (late - 1).coerceAtLeast(0)
                    if (late >= COMET_LATE_FRAMES) LitHeroPerformance.markSlow()
                }
                last = now
                // Thirty steps a second are plenty for the comet alone, whatever the screen's own rate: the scene
                // is traced again only on a step, so a 120 Hz screen costs no more than a 60 Hz one. The next step
                // is due a thirtieth of a second after the last one was due, not after it was taken, so a screen
                // whose frames do not divide into thirtieths (48 Hz) still gets thirty on average.
                if (stepped == 0L) {
                    stepped = now
                    due = now + COMET_STEP_NANOS
                } else if (now >= due - COMET_SLACK_NANOS) {
                    val dt = ((now - stepped) / 1_000_000_000f).coerceAtMost(0.1f)
                    stepped = now
                    due = if (now - due > COMET_STEP_NANOS) now + COMET_STEP_NANOS else due + COMET_STEP_NANOS
                    val lap = if (readingNow) COMET_LAP_READING else COMET_LAP
                    state.power += ((if (running) 1f else 0f) - state.power) * min(dt * 3.5f, 1f)
                    state.angle = (state.angle + dt * 2f * PI.toFloat() / lap) % (2f * PI.toFloat())
                }
            }
        }
        state.power = 0f
    }
    return state
}

private const val COMET_LAP = 9f
private const val COMET_LAP_READING = 2.6f
private const val COMET_SETTLE_NANOS = 1_500_000_000L

/** A thirtieth of a second: every second frame at 60 Hz, every third at 90, every fourth at 120. */
private const val COMET_STEP_NANOS = 33_333_333L

/** A frame this close before a step is due takes it: frame times are not exact. */
private const val COMET_SLACK_NANOS = 1_500_000L

/** Under twenty frames a second for this many frames more than not: this phone's GPU cannot carry the comet. */
private const val COMET_LATE_NANOS = 50_000_000L
private const val COMET_LATE_FRAMES = 24

/** Runs [action] each time a read ends. It outlives the effect that noticed, so a new read does not freeze it midway. */
@Composable
internal fun OnReadFinished(reading: Boolean, action: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentAction by rememberUpdatedState(action)
    val was = remember { booleanArrayOf(reading) }
    LaunchedEffect(reading) {
        if (was[0] && !reading) scope.launch { currentAction() }
        was[0] = reading
    }
}

/** When a read finishes the scene flares once and dies away. */
@Composable
private fun rememberBloom(reading: Boolean, enabled: Boolean): State<Float> {
    val bloom = remember { Animatable(0f) }
    OnReadFinished(reading) {
        if (enabled) {
            bloom.snapTo(1f)
            bloom.animateTo(0f, tween(BLOOM_MS, easing = BLOOM_DECAY))
        }
    }
    return bloom.asState()
}

private const val BLOOM_MS = 1700
private val BLOOM_DECAY = Easing { t -> 1f - exp(-5.8f * t) }

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

private class RingStyle(
    val arcs: List<RingArc>,
    val colorOf: (String?) -> Color,
    val highlighted: String?,
    val highlight: Float,
    val reveal: Float,
    val band: Float,
    val dark: Boolean,
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
    }
}

/**
 * The SORA planet: the brand's red disc with the 天 mark, shaded as a sphere lit from the top left,
 * a specular highlight that slides against the tilt, and a cool rim on the shadow side.
 */
private fun DrawScope.drawPlanet(c: Offset, r: Float, glyph: Path, dark: Boolean, tx: Float, ty: Float) {
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
private fun DrawScope.drawNebula(dark: Boolean, planet: Offset) {
    fun haze(color: Color, alpha: Float, center: Offset, radius: Float) = drawCircle(
        Brush.radialGradient(0f to color.copy(alpha = alpha), 1f to Color.Transparent, center = center, radius = radius),
        radius = radius,
        center = center,
    )
    // The haze belongs to the night sky; the white page has none.
    if (dark) {
        haze(PortfolioPalette.hazeViolet, 0.16f, Offset(size.width * 0.1f, size.height * 0.32f), size.width * 0.62f)
        haze(PortfolioPalette.hazeCyan, 0.10f, Offset(size.width * 0.92f, size.height * 0.12f), size.width * 0.5f)
    }
    val halo = 175.dp.toPx()
    val core = if (dark) 0.5f else 0.12f
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
private fun DrawScope.drawStars() {
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
        val arm = (5f + 2f * (i % 2)) * density
        val flare = PortfolioPalette.starlight.copy(alpha = 0.26f)
        drawLine(flare, p - Offset(arm, 0f), p + Offset(arm, 0f), strokeWidth = 0.8f * density)
        drawLine(flare, p - Offset(0f, arm), p + Offset(0f, arm), strokeWidth = 0.8f * density)
        drawCircle(PortfolioPalette.starlight.copy(alpha = 0.56f), radius = 1.4f * density, center = p)
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
