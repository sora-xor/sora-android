package jp.co.soramitsu.ui_core.sacred

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The flight through the gate of heaven (天門), as the numbers the shader is given each frame.
 *
 * The camera comes down a corridor of torii, stops in front of the last seal (the diviner's board, whose inner
 * plate turns while it waits), then goes through to the planet as day breaks into the page. The iOS app computes
 * the same numbers (SacredTenmonScene.swift).
 */
class TenmonScene(
    /** The surface's size in pixels, and how many of them make a dp. */
    val width: Float,
    val height: Float,
    val pixelsPerDp: Float,
    /** Where the planet lands and its radius there, in pixels. */
    val landingX: Float,
    val landingY: Float,
    landingRadius: Float,
    /** The page day breaks into, as sRGB 0..1. */
    val pageRed: Float,
    val pageGreen: Float,
    val pageBlue: Float,
    /** Whether the planet stays when everything else has given way to the page. */
    val keepsPlanet: Boolean,
) {
    enum class Phase { INTRO, HOLD, OUTRO }

    class Uniforms(
        val view: FloatArray,
        val flight: FloatArray,
        val lens: FloatArray,
        val state: FloatArray,
        val page: FloatArray,
    )

    val landingRadius: Float = max(1f, landingRadius)

    /** Lens length in pixels: a wide lens, so the gates rush past. */
    val focal: Float get() = 1.15f * width

    /** Where the camera ends: as far from the planet as shows it at its landing size. */
    val endZ: Float get() = PLANET_Z - focal * PLANET_RADIUS / landingRadius

    /**
     * @param progress 0..1 through the phase (unused while holding)
     * @param clock seconds since the scene first drew; the stars and the armillary run on it
     * @param turn the board's inner plate, in radians
     */
    fun uniforms(phase: Phase, progress: Float, clock: Float, turn: Float): Uniforms {
        val t = unit(progress)
        val middleX = width / 2f
        val middleY = height / 2f
        val view = floatArrayOf(width, height, clock, pixelsPerDp)
        val page = floatArrayOf(pageRed, pageGreen, pageBlue, if (keepsPlanet) 0f else 1f)
        return when (phase) {
            Phase.INTRO -> {
                val settle = 1f - easeOut(t)
                Uniforms(
                    view,
                    floatArrayOf(HOLD_Z * easeInOut(t), EYE_HEIGHT, 0.05f * settle, 0.035f * settle),
                    floatArrayOf(middleX, middleY, focal, -0.05f * settle),
                    floatArrayOf(unit(t / 0.22f), 0f, turn, 0f),
                    page,
                )
            }
            Phase.HOLD -> Uniforms(
                view,
                floatArrayOf(HOLD_Z, EYE_HEIGHT, 0f, 0f),
                floatArrayOf(middleX, middleY, focal, 0f),
                floatArrayOf(1f, 0f, turn, 0f),
                page,
            )
            Phase.OUTRO -> {
                val move = easeInOut(t)
                Uniforms(
                    view,
                    floatArrayOf(HOLD_Z + (endZ - HOLD_Z) * move, EYE_HEIGHT, 0f, 0f),
                    floatArrayOf(middleX + (landingX - middleX) * move, middleY + (landingY - middleY) * move, focal, 0f),
                    floatArrayOf(1f, easeOut(unit((t - 0.15f) / 0.7f)), turn, smooth(0.62f, 1f, t)),
                    page,
                )
            }
        }
    }

    companion object {
        // The scene's measures, in metres. They match the constants in the shader.
        const val PLANET_Z = 58f
        const val PLANET_RADIUS = 1.25f

        /** Where the camera waits: in front of the last two gates and the board between them. */
        const val HOLD_Z = 30f
        const val EYE_HEIGHT = 1.6f

        const val INTRO_SECONDS = 1.0f

        /** 110 ms stepped four times by the golden ratio. */
        const val OUTRO_SECONDS = 0.754f

        /** The board's inner plate has twelve marks; it moves on by one each this many seconds. */
        const val HOUR_SECONDS = 1.45f
        private const val HOUR = (PI / 6).toFloat()

        fun unit(value: Float): Float = min(1f, max(0f, value))

        fun easeInOut(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

        fun easeOut(t: Float): Float = 1f - (1f - t).pow(3)

        fun smooth(from: Float, to: Float, value: Float): Float {
            val t = unit((value - from) / (to - from))
            return t * t * (3f - 2f * t)
        }

        /** The inner plate's turn after waiting [seconds]. */
        fun turn(seconds: Float): Float = max(0f, seconds) / HOUR_SECONDS * HOUR

        /** The turn during the way through: the plate runs on to its next mark and stops there. */
        fun settledTurn(turn: Float, progress: Float): Float {
            val target = ceil(turn / HOUR) * HOUR
            return turn + (target - turn) * easeOut(unit(progress / 0.4f))
        }
    }
}
