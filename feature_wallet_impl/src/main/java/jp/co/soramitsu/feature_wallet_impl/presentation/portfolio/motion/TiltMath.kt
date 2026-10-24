package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Pure parallax math (Kotlin stdlib only), ported from the portfolio dashboard reference
// (reference/android/TiltMath.kt). Every port reproduces reference/parallax-vectors.json within
// 1e-6; TiltMathTest checks this file against a copy of those vectors. Constants and formulas
// here are the specification: do not tune them in one place only.

object TiltParallaxConstants {
    const val TILT_RANGE_DEG = 18.0 // degrees from the resting pose that give full deflection
    const val BASELINE_TAU_MS = 2500.0 // how slowly the resting pose follows the real pose
    const val DEADZONE = 0.02
    const val PHONE_SMOOTH_TAU_MS = 110.0
    const val SETTLE_EPS = 0.0006
}

private fun clamp(v: Double, lo: Double, hi: Double): Double = min(hi, max(lo, v))

/** One exponential smoothing step toward the target. */
fun smoothStep(value: Double, target: Double, dtMs: Double, tauMs: Double): Double =
    value + (target - value) * (1 - exp(-dtMs / tauMs))

/**
 * Parallax moves only when every condition holds.
 *
 * @property fullEffects the surface may move at all (portfolio flag on)
 * @property reduceMotion animations are off (`!ValueAnimator.areAnimatorsEnabled()`)
 * @property lowPower battery saver is on (`PowerManager.isPowerSaveMode`)
 * @property hidden the activity is not started or the screen is off
 * @property heroVisible the parallax surface is on screen
 */
data class ParallaxGate(
    val fullEffects: Boolean,
    val reduceMotion: Boolean,
    val lowPower: Boolean,
    val hidden: Boolean,
    val heroVisible: Boolean,
) {
    val isOpen: Boolean
        get() = fullEffects && !reduceMotion && !lowPower && !hidden && heroVisible
}

data class Offset2(val dx: Double, val dy: Double)

/** Layer translation in dp: opposite to the input, nearer layers (larger depth) move more. */
fun layerOffset(x: Double, y: Double, depthX: Double, depthY: Double, scale: Double): Offset2 =
    Offset2(-x * depthX * scale, -y * depthY * scale)

data class Deflection(val x: Double, val y: Double)

/** Resting-pose tracker: a slow high-pass so a new holding angle becomes the new centre. */
class TiltBaseline(private val tauMs: Double = TiltParallaxConstants.BASELINE_TAU_MS) {
    private var restRoll = 0.0
    private var restPitch = 0.0
    private var started = false
    private var lastMs = 0.0

    fun reset() {
        started = false
    }

    /** Angles are viewer-oriented degrees (see [viewerTilt]); returns the deviation from the resting pose in [-1, 1]. */
    fun normalise(rollDeg: Double, pitchDeg: Double, nowMs: Double): Deflection {
        if (!started) {
            restRoll = rollDeg
            restPitch = pitchDeg
            lastMs = nowMs
            started = true
        }
        val dt = max(0.0, nowMs - lastMs)
        lastMs = nowMs
        val k = 1 - exp(-dt / tauMs)
        restRoll += (rollDeg - restRoll) * k
        restPitch += (pitchDeg - restPitch) * k
        fun dead(v: Double) = if (abs(v) < TiltParallaxConstants.DEADZONE) 0.0 else v
        return Deflection(
            dead(clamp((rollDeg - restRoll) / TiltParallaxConstants.TILT_RANGE_DEG, -1.0, 1.0)),
            dead(clamp((pitchDeg - restPitch) / TiltParallaxConstants.TILT_RANGE_DEG, -1.0, 1.0)),
        )
    }
}

/** Smoothed 2D value. The frame loop runs only while [step] has not returned true. */
class Smooth2D(private val tauMs: Double) {
    var x = 0.0
    var y = 0.0
    var tx = 0.0
    var ty = 0.0

    /** Advances by [dtMs] and returns true once the value has settled on the target. */
    fun step(dtMs: Double): Boolean {
        x = smoothStep(x, tx, dtMs, tauMs)
        y = smoothStep(y, ty, dtMs, tauMs)
        val settled = abs(tx - x) < TiltParallaxConstants.SETTLE_EPS &&
            abs(ty - y) < TiltParallaxConstants.SETTLE_EPS
        if (settled) {
            x = tx
            y = ty
        }
        return settled
    }
}

/** A sensor quaternion: x, y, z, w (device-to-world rotation; device axes x right, y up, z out of the screen). */
data class Quat(val x: Double, val y: Double, val z: Double, val w: Double)

/**
 * Viewer-oriented tilt in degrees: the rotation of the device since the resting pose [q0] about its own long axis (roll)
 * and short axis (pitch), from quaternions, so a phone held upright causes no Euler gimbal problem.
 * Sign: x > 0 when the screen has turned toward the user's left (the viewer sits to the right of the screen's axis),
 * y > 0 when the screen has tilted up (the viewer sits below it). Feed the result to [TiltBaseline.normalise].
 */
fun viewerTilt(q0: Quat, q: Quat): Pair<Double, Double> {
    val ax = -q0.x
    val ay = -q0.y
    val az = -q0.z
    val aw = q0.w // conjugate of q0
    var w = aw * q.w - ax * q.x - ay * q.y - az * q.z
    var x = aw * q.x + ax * q.w + ay * q.z - az * q.y
    var y = aw * q.y - ax * q.z + ay * q.w + az * q.x
    var z = aw * q.z + ax * q.y - ay * q.x + az * q.w
    if (w < 0) {
        w = -w
        x = -x
        y = -y
        z = -z
    }
    val n = sqrt(x * x + y * y + z * z)
    if (n < 1e-12) return 0.0 to 0.0
    val k = 2 * atan2(n, w) / n
    val deg = 180 / PI
    // Rotation about +y is roll and about +x is pitch; negate to viewer orientation.
    return (-(y * k) * deg) to (-(x * k) * deg)
}
