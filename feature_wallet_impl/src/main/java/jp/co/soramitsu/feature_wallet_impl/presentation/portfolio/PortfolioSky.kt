package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Geometry of the sky in dp (portfolio-view SPEC §3.1: about 220 tall, the flower about 150 from
 * the top). On Android the sky starts below the status bar, so its top row sits where the home's
 * usual top bar does ([TOP_ROW] down) and the sky is that much taller.
 */
internal object SkyGeometry {
    val TOP_ROW: Dp = 24.dp
    val HEIGHT: Dp = 238.dp
    val CENTER_Y: Dp = 166.dp
    val FLOWER: Dp = 76.dp
    val R0: Dp = 52.dp
    val R1: Dp = 108.dp
    val RAY_WIDTH: Dp = 1.7.dp
    val LIFT: Dp = 6.dp
    const val SPAN_DEG = 180.0
    const val RAYS = 100

    /** Depths at full deflection, x then y (y is 0.65 × x). */
    const val STARS = 3f
    const val FAN = 7f
    const val SHEEN = 7f
    const val FLOWER_DEPTH = 22f
    const val FLOWER_ROTATION = 1.4f
    const val GLOW = 10f
    const val Y_RATIO = 0.65f

    /** Ray i's direction in degrees from straight up, left to right across the arc. */
    fun rayAngle(index: Int): Double = -SPAN_DEG / 2 + (index + 0.5) * SPAN_DEG / RAYS
}

/** Key used for the Other slice of the fan. */
internal const val OTHER_KEY = "__other__"

/**
 * The hero sky: night (dark) or dawn (light) gradient, a soft magenta glow, a few stars, the
 * 100-ray allocation fan and its sheen, and the Polkaswap flower. Only these layers move with the
 * tilt; [overlay] (scope chip and buttons) never moves. The fan is decorative for accessibility;
 * the legend chips are the real buttons.
 */
@Composable
internal fun PortfolioSky(
    fan: List<FanSlice>,
    colorOf: (String?) -> Color,
    highlighted: String?,
    tilt: TiltState,
    onSliceTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val dark = isDarkTheme
    // The sky fades out at its bottom edge into the app's aurora background behind the content.
    val background = remember(dark) {
        if (dark) {
            Brush.verticalGradient(
                0f to PortfolioPalette.nightTop,
                0.72f to PortfolioPalette.nightBottom,
                1f to PortfolioPalette.nightBottom.copy(alpha = 0f),
            )
        } else {
            Brush.verticalGradient(
                0f to PortfolioPalette.dawnTop,
                0.72f to PortfolioPalette.dawnBottom,
                1f to PortfolioPalette.dawnBottom.copy(alpha = 0f),
            )
        }
    }
    // Reduce Motion: the highlight only fades; rays do not lift.
    val reduceMotion = LocalFx.current.reduceMotion
    val highlight by animateFloatAsState(if (highlighted != null) 1f else 0f, tween(220), label = "fanHighlight")
    val currentFan by rememberUpdatedState(fan)
    val currentTap by rememberUpdatedState(onSliceTap)
    Box(
        modifier
            .fillMaxWidth()
            .height(SkyGeometry.HEIGHT)
            .background(background)
    ) {
        // Glow behind the flower: x only.
        Canvas(
            Modifier
                .fillMaxSize()
                .tiltLayer(tilt, SkyGeometry.GLOW, 0f)
                .semantics { hideFromAccessibility() }
        ) {
            val center = Offset(size.width / 2f, SkyGeometry.CENTER_Y.toPx())
            val radius = SkyGeometry.R1.toPx() * 1.55f
            val core = if (dark) 0.62f else 0.30f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to PortfolioPalette.polkaswap.copy(alpha = core),
                    0.42f to PortfolioPalette.polkaswapDeep.copy(alpha = core * 0.42f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        if (dark) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .tiltLayer(tilt, SkyGeometry.STARS, SkyGeometry.STARS * SkyGeometry.Y_RATIO)
                    .semantics { hideFromAccessibility() }
            ) {
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
                        color = Color(0xFFFFE6F2).copy(alpha = alpha),
                        radius = (if (i % 6 == 0) 1.3f else 0.8f) * density,
                        center = Offset(x, y),
                    )
                }
            }
        }
        if (fan.isNotEmpty()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .tiltLayer(tilt, SkyGeometry.FAN, SkyGeometry.FAN * SkyGeometry.Y_RATIO)
                    .semantics { hideFromAccessibility() }
                    .pointerInput(Unit) {
                        detectTapGestures { position ->
                            val center = Offset(size.width / 2f, SkyGeometry.CENTER_Y.toPx())
                            val dx = position.x - center.x
                            val dy = position.y - center.y
                            val radius = hypot(dx, dy)
                            if (radius < SkyGeometry.R0.toPx() - 8.dp.toPx() ||
                                radius > SkyGeometry.R1.toPx() + 18.dp.toPx() ||
                                dy > 4.dp.toPx()
                            ) {
                                return@detectTapGestures
                            }
                            val degrees = atan2(dx, -dy) * 180 / PI
                            val index = floor((degrees + SkyGeometry.SPAN_DEG / 2) / SkyGeometry.SPAN_DEG * SkyGeometry.RAYS).toInt()
                            sliceAt(currentFan, index)?.let { currentTap(it.key ?: OTHER_KEY) }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, SkyGeometry.CENTER_Y.toPx())
                val r0 = SkyGeometry.R0.toPx()
                val r1 = SkyGeometry.R1.toPx()
                val lift = if (reduceMotion) 0f else SkyGeometry.LIFT.toPx()
                var index = 0
                fan.forEach { slice ->
                    val key = slice.key ?: OTHER_KEY
                    val selected = highlighted == key
                    val color = colorOf(slice.key)
                    val alpha = when {
                        selected -> 0.92f + 0.08f * highlight
                        else -> 0.92f - (0.92f - 0.26f) * highlight
                    }
                    val shift = if (selected) lift * highlight else 0f
                    val width = SkyGeometry.RAY_WIDTH.toPx() * (if (selected) 1f + 0.45f * highlight else 1f)
                    repeat(slice.rays) {
                        val angle = SkyGeometry.rayAngle(index) * PI / 180
                        val dir = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                        val start = center + dir * (r0 + shift)
                        val end = center + dir * (r1 + shift)
                        val fadeEnd = start + (end - start) * 0.3f
                        drawLine(
                            brush = Brush.linearGradient(
                                listOf(color.copy(alpha = 0f), color.copy(alpha = alpha)),
                                start = start,
                                end = fadeEnd,
                            ),
                            start = start,
                            end = end,
                            strokeWidth = width,
                            cap = StrokeCap.Round,
                        )
                        index++
                    }
                }
            }
            // The sheen: a 26° window of light over a white copy of the fan. It slides across
            // with the tilt and sits off the arc at rest.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .tiltLayer(tilt, SkyGeometry.SHEEN, SkyGeometry.SHEEN * SkyGeometry.Y_RATIO)
                    .semantics { hideFromAccessibility() }
            ) {
                val x = tilt.x.toDouble()
                if (x == 0.0) return@Canvas
                val windowStart = if (x > 0) -116.0 + x * 232.0 else 116.0 + x * 232.0
                val center = Offset(size.width / 2f, SkyGeometry.CENTER_Y.toPx())
                val r0 = SkyGeometry.R0.toPx()
                val r1 = SkyGeometry.R1.toPx()
                val total = fan.sumOf { it.rays }
                for (i in 0 until total) {
                    val angle = SkyGeometry.rayAngle(i)
                    val d = angle - windowStart
                    val w = when {
                        d < 0 || d > 26 -> 0.0
                        d <= 12 -> d / 12
                        else -> (26 - d) / 14
                    }
                    if (w <= 0.0) continue
                    val rad = angle * PI / 180
                    val dir = Offset(sin(rad).toFloat(), -cos(rad).toFloat())
                    val start = center + dir * r0
                    val end = center + dir * r1
                    val alpha = (0.55 * w).toFloat() * if (dark) 1f else 0.7f
                    drawLine(
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = alpha)),
                            start = start,
                            end = start + (end - start) * 0.3f,
                        ),
                        start = start,
                        end = end,
                        strokeWidth = SkyGeometry.RAY_WIDTH.toPx(),
                        cap = StrokeCap.Round,
                        blendMode = BlendMode.Screen,
                    )
                }
            }
        }
        PolkaswapFlower(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = SkyGeometry.CENTER_Y - SkyGeometry.FLOWER / 2)
                .size(SkyGeometry.FLOWER)
                .tiltLayer(
                    tilt,
                    SkyGeometry.FLOWER_DEPTH,
                    SkyGeometry.FLOWER_DEPTH * SkyGeometry.Y_RATIO,
                    rotateDeg = SkyGeometry.FLOWER_ROTATION,
                )
                .semantics { hideFromAccessibility() }
        )
        overlay()
    }
}

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
