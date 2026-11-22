package jp.co.soramitsu.ui_core.sacred

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The proportions the wallet's motion and composition are built on. The iOS app has the same (SacredGeometry). */
object SacredGeometry {
    /** 黄金比. Steps the timing ladder. */
    const val PHI = 1.618034f

    /** 白銀比 (大和比), the proportion of Hōryū-ji and of Japanese paper. Steps sizes. */
    const val YAMATO = 1.4142135f

    /** The ring's radius over the planet's, in the emblem. */
    const val RING_TO_PLANET = 2.05f

    /** Durations step by φ from 110 ms: 110, 178, 288, 466, 754, 1220 ms. */
    fun beatMillis(step: Int): Int = (110f * PHI.pow(step.coerceAtLeast(0))).roundToInt()

    /** Centres of the seed's six circles, from the top, clockwise on screen. Each has half the figure's radius. */
    fun seedCentre(index: Int, radius: Float): Offset {
        val angle = -PI / 2 + index * PI / 3
        return Offset((radius * 0.5 * cos(angle)).toFloat(), (radius * 0.5 * sin(angle)).toFloat())
    }

    /** Where on its own circle a seed circle is nearest the middle, in degrees: its stroke starts there. */
    fun seedStartDegrees(index: Int): Float = -90f + 60f * index + 180f
}

/** The gold of a mended bowl: the lit lines of the figures on a pale page. */
val SacredGold = Color(0xFFA87A2E)
private val PlanetRed = Color(0xFFE3232C)

/** 天 on its 22-unit disc: point for point the token's own mark. */
private fun soraMark(): Path = Path().apply {
    moveTo(5.8f, 20.7f)
    cubicTo(7.5f, 18.1f, 9.3f, 15.5f, 11.1f, 12.9f)
    lineTo(16.3f, 20.7f)
    cubicTo(16.6f, 20.6f, 16.8f, 20.4f, 17.1f, 20.2f)
    cubicTo(17.4f, 20f, 17.6f, 19.9f, 17.8f, 19.7f)
    cubicTo(15.9f, 16.8f, 13.9f, 13.9f, 12f, 11f)
    listOf(
        17.8f to 11f, 17.8f to 9.2f, 12f to 9.2f, 12f to 7.3f, 17.8f to 7.3f, 17.8f to 5.5f, 4.3f to 5.5f,
        4.3f to 7.3f, 10.1f to 7.3f, 10.1f to 9.2f, 4.3f to 9.2f, 4.3f to 11f, 10.1f to 11f, 4.3f to 19.7f,
    ).forEach { (x, y) -> lineTo(x, y) }
    cubicTo(4.5f, 19.9f, 4.7f, 20f, 5f, 20.2f)
    cubicTo(5.3f, 20.4f, 5.5f, 20.6f, 5.8f, 20.7f)
    close()
}

/** The SORA mark: the red disc with 天 in white, as the logo is. */
private fun DrawScope.planet(centre: Offset, radius: Float, mark: Path) {
    drawCircle(PlanetRed, radius, centre)
    translate(centre.x - radius, centre.y - radius) {
        scale(radius * 2f / 22f, pivot = Offset.Zero) { drawPath(mark, Color.White) }
    }
}

/**
 * The SORA mark in its ring of six circles. On the PIN screen the ring is the lock: each digit draws one more circle
 * out from behind the mark, and the last closes the rim. It only shows what the row of dots already says, so
 * it has no place in what a screen reader reads.
 *
 * @param share how much of the code is entered, 0..1
 * @param wrong the code was wrong: the whole ring shows in [wrongColor]
 */
@Composable
fun SacredEmblem(
    share: Float,
    wrong: Boolean,
    line: Color,
    wrongColor: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    lit: Color = SacredGold,
    reduceMotion: Boolean = false,
) {
    val drawn by animateFloatAsState(
        targetValue = if (wrong) 1f else share.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) snap() else tween(SacredGeometry.beatMillis(1), easing = LinearOutSlowInEasing),
        label = "sacredLock",
    )
    val rim by animateFloatAsState(
        targetValue = if (wrong || share >= 1f) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(SacredGeometry.beatMillis(2), easing = LinearOutSlowInEasing),
        label = "sacredRim",
    )
    val mark = remember { soraMark() }
    Canvas(modifier.size(diameter).clearAndSetSemantics { }) {
        val middle = Offset(size.width / 2f, size.height / 2f)
        val ring = size.minDimension / 2f - 2.dp.toPx()
        val planetRadius = ring / SacredGeometry.RING_TO_PLANET
        // A breath of the mark's red on the page round it.
        drawCircle(
            Brush.radialGradient(
                0.44f to PlanetRed.copy(alpha = 0.12f),
                1f to Color.Transparent,
                center = middle,
                radius = planetRadius * 2.1f,
            ),
            planetRadius * 2.1f,
            middle,
        )
        val thin = Stroke(0.8.dp.toPx())
        val faint = line.copy(alpha = 0.5f)
        drawCircle(faint, ring, middle, style = thin)
        val bright = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
        val colour = if (wrong) wrongColor else lit
        for (index in 0 until 6) {
            val centre = middle + SacredGeometry.seedCentre(index, ring)
            drawCircle(faint, ring / 2f, centre, style = thin)
            val sweep = (drawn * 6f - index).coerceIn(0f, 1f) * 360f
            if (sweep > 0f) {
                drawArc(
                    color = colour,
                    startAngle = SacredGeometry.seedStartDegrees(index),
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(centre.x - ring / 2f, centre.y - ring / 2f),
                    size = Size(ring, ring),
                    style = bright,
                )
            }
        }
        if (rim > 0f) {
            drawArc(colour, -90f, rim * 360f, false, Offset(middle.x - ring, middle.y - ring), Size(ring * 2f, ring * 2f), style = bright)
        }
        planet(middle, planetRadius, mark)
    }
}

/** What has become of something sent to the network. */
enum class SacredSealState { WORKING, SETTLED, BROKEN }

/**
 * 天円地方, the circle of heaven round the square of earth, as the state of something sent to the network.
 * In flight the circle is open and turning and the square stands on its corner. Settled, the circle closes and
 * the square comes down flat and is stamped, as a seal is set on a paid bill. Broken, the circle stays open
 * and still.
 *
 * @param loops false keeps the open circle still (reduced motion, power saving)
 */
@Composable
fun SacredSeal(
    state: SacredSealState,
    working: Color,
    settled: Color,
    broken: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 24.dp,
    mark: Color = Color.White,
    loops: Boolean = true,
) {
    val turn = if (state == SacredSealState.WORKING && loops) {
        val transition = rememberInfiniteTransition(label = "sacredSeal")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(SacredGeometry.beatMillis(5), easing = LinearEasing), RepeatMode.Restart),
            label = "sacredSealTurn",
        ).value
    } else {
        0f
    }
    // 1 when settled: the circle closed, the square flat and filled.
    val set by animateFloatAsState(
        targetValue = if (state == SacredSealState.SETTLED) 1f else 0f,
        animationSpec = if (loops) tween(SacredGeometry.beatMillis(2), easing = LinearOutSlowInEasing) else snap(),
        label = "sacredSealSet",
    )
    // The stamp comes down from a little above its size.
    val stamp = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (state == SacredSealState.SETTLED && loops) {
            stamp.snapTo(1.32f)
            stamp.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        } else {
            stamp.snapTo(1f)
        }
    }
    Canvas(modifier.size(diameter).clearAndSetSemantics { }) {
        val side = size.minDimension
        val middle = Offset(size.width / 2f, size.height / 2f)
        val width = maxOf(1.5.dp.toPx(), side * 0.06f)
        val radius = side / 2f - width / 2f - 0.5f
        val colour = when (state) {
            SacredSealState.WORKING -> working
            SacredSealState.SETTLED -> settled
            SacredSealState.BROKEN -> broken
        }
        val open = 0.82f + 0.18f * set
        rotate(if (state == SacredSealState.BROKEN) 36f else turn, middle) {
            drawArc(
                colour, -90f, 360f * open, false,
                Offset(middle.x - radius, middle.y - radius), Size(radius * 2f, radius * 2f),
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        // The square's corners stop short of the circle, so on its corner it still turns freely inside.
        val half = radius * 0.62f / SacredGeometry.YAMATO
        rotate(45f * (1f - set), middle) {
            scale(stamp.value, middle) {
                val corner = CornerRadius(half * 0.24f)
                val topLeft = Offset(middle.x - half, middle.y - half)
                val box = Size(half * 2f, half * 2f)
                if (set > 0f) drawRoundRect(colour.copy(alpha = colour.alpha * set), topLeft, box, corner, Fill)
                drawRoundRect(colour, topLeft, box, corner, Stroke(width * 0.8f))
            }
        }
        val line = Stroke(width * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (state == SacredSealState.SETTLED && set > 0.5f) {
            val tick = Path().apply {
                moveTo(middle.x - half * 0.50f, middle.y + half * 0.02f)
                lineTo(middle.x - half * 0.12f, middle.y + half * 0.40f)
                lineTo(middle.x + half * 0.52f, middle.y - half * 0.36f)
            }
            drawPath(tick, mark.copy(alpha = (set - 0.5f) * 2f), style = line)
        } else if (state == SacredSealState.BROKEN) {
            val reach = half * 0.34f
            drawLine(colour, Offset(middle.x - reach, middle.y - reach), Offset(middle.x + reach, middle.y + reach), line.width, StrokeCap.Round)
            drawLine(colour, Offset(middle.x + reach, middle.y - reach), Offset(middle.x - reach, middle.y + reach), line.width, StrokeCap.Round)
        }
    }
}
