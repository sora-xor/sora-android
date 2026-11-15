package jp.co.soramitsu.ui_core.lit

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.core.graphics.createBitmap
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlinx.coroutines.delay

/** One arc of the hero's ring, in radians of the ring's own circle. */
class LitHeroSegment(
    val start: Float,
    val sweep: Float,
    val color: Color,
    val strength: Float = 1f,
    val widthScale: Float = 1f,
    val lift: Float = 0f,
)

/**
 * True while the phone cannot keep up with the hero's own motion: frames came late for over a second. The comet and
 * the tilt, the only things that ask for frames on their own, then rest; the scene still draws for everything that
 * means something (a new ring, a selection, a finished read). A busy moment passes, so they try again after a rest
 * that doubles each time it is needed.
 */
object LitHeroPerformance {
    var isSlow by mutableStateOf(false)
        private set
    private var restMillis = 30_000L

    fun markSlow() {
        isSlow = true
    }

    /** Called while [isSlow], from whatever is on screen to try again later. */
    suspend fun restThenRetry() {
        delay(restMillis)
        restMillis = (restMillis * 2L).coerceAtMost(600_000L)
        isSlow = false
    }
}

/** Everything the hero's shader reads for one frame. Lengths are in dp. */
class LitHeroFrame(
    val center: Offset,
    val planetRadius: Float,
    val ringRadius: Float,
    val ringWidth: Float,
    val ringThickness: Float,
    val outerOrbit: Float,
    val aspect: Float,
    val rollRadians: Float,
    val tiltX: Float,
    val tiltY: Float,
    val reveal: Float,
    val cometAngle: Float,
    val cometPower: Float,
    val bottomFade: Float,
    val dark: Boolean,
    val ringStart: Float,
    val segments: List<LitHeroSegment>,
    /** The flare when a read finishes: 1 at its height, dying away to 0. */
    val bloom: Float = 0f,
)

/**
 * The ray-traced hero on Android 13 and later. [isAvailable] is false below that or if the shader does not build on
 * this device; the caller keeps its plain drawing then.
 */
class LitHeroRenderer(markPath: Path, markViewport: Float) {
    private val state: Any? = shared(markPath, markViewport)

    val isAvailable: Boolean get() = state != null

    fun draw(scope: DrawScope, frame: LitHeroFrame) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (state as? Compiled)?.draw(scope, frame)
        }
    }

    companion object {
        private val lock = Any()
        private var tried = false
        private var compiled: Any? = null

        /** Builds the shader ahead of the first hero. Call it off the main thread. */
        fun preload(markPath: Path, markViewport: Float) {
            shared(markPath, markViewport)
        }

        /** One compiled shader for the process: uniforms are set just before each draw, and a draw keeps its own copy. */
        private fun shared(markPath: Path, markViewport: Float): Any? = synchronized(lock) {
            if (!tried) {
                tried = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    compiled = try {
                        Compiled(markPath, markViewport)
                    } catch (_: RuntimeException) {
                        null
                    }
                }
            }
            compiled
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private class Compiled(markPath: Path, markViewport: Float) {
        private val shader = RuntimeShader(LIT_HERO_SHADER)
        private val brush = ShaderBrush(shader)
        private val angles = FloatArray(24)
        private val colors = FloatArray(24)
        private val bounce = FloatArray(32)

        init {
            val side = 512
            val bitmap = createBitmap(side, side)
            val canvas = Canvas(bitmap)
            canvas.scale(side / markViewport, side / markViewport)
            canvas.drawPath(markPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
            val sampler = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            sampler.filterMode = BitmapShader.FILTER_MODE_LINEAR
            shader.setInputShader("mark", sampler)
            shader.setFloatUniform("markSide", side.toFloat())
        }

        private fun linear(v: Float) = v.pow(2.2f)

        fun draw(scope: DrawScope, frame: LitHeroFrame) {
            val density = scope.density
            shader.setFloatUniform("viewport", scope.size.width / density, scope.size.height / density, density, 0f)
            shader.setFloatUniform("center", frame.center.x, frame.center.y, frame.planetRadius, if (frame.dark) 1f else 0f)
            shader.setFloatUniform("ring", frame.ringRadius, frame.ringWidth, frame.ringThickness, frame.reveal)
            shader.setFloatUniform("pose", frame.aspect, frame.rollRadians, frame.tiltX, frame.tiltY)
            shader.setFloatUniform("comet", frame.cometAngle, frame.cometPower, frame.bottomFade, frame.bloom)
            val count = frame.segments.size.coerceAtMost(6)
            shader.setFloatUniform("ringInfo", frame.ringStart, count.toFloat(), 0.65f, frame.outerOrbit)
            angles.fill(0f)
            colors.fill(0f)
            bounce.fill(0f)
            for (index in 0 until count) {
                val segment = frame.segments[index]
                angles[index * 4] = segment.start
                angles[index * 4 + 1] = segment.sweep
                angles[index * 4 + 2] = segment.strength
                angles[index * 4 + 3] = segment.widthScale
                colors[index * 4] = linear(segment.color.red)
                colors[index * 4 + 1] = linear(segment.color.green)
                colors[index * 4 + 2] = linear(segment.color.blue)
                colors[index * 4 + 3] = segment.lift
            }
            val tau = (2.0 * PI).toFloat()
            for (sample in 0 until 8) {
                val angle = (sample + 0.5f) * tau / 8f
                for (index in 0 until count) {
                    val segment = frame.segments[index]
                    val raw = (angle - segment.start) / tau
                    val off = (raw - floor(raw)) * tau
                    if (off < segment.sweep) {
                        bounce[sample * 4] = colors[index * 4] * segment.strength
                        bounce[sample * 4 + 1] = colors[index * 4 + 1] * segment.strength
                        bounce[sample * 4 + 2] = colors[index * 4 + 2] * segment.strength
                        break
                    }
                }
            }
            shader.setFloatUniform("segmentAngles", angles)
            shader.setFloatUniform("segmentColors", colors)
            shader.setFloatUniform("bounce", bounce)
            scope.drawRect(brush)
        }
    }
}
