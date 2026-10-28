package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.view.Choreographer
import kotlin.math.sqrt

/**
 * Android glue for the portfolio sky and the token orbit, ported from the portfolio dashboard
 * reference (reference/android/TiltParallaxController.kt). It reads the game rotation vector (no
 * magnetometer, no drift toward north), turns it into the rotation since the resting pose, smooths
 * it and reports a deflection in [-1, 1] on each frame. Compose reads the result in the draw phase
 * (graphicsLayer), so nothing recomposes; labels, rows and balances never move.
 *
 * No sensor or Choreographer callback runs while the gate is closed: surface off (flag), animations
 * disabled (Remove animations), battery saver, activity not started, or the surface scrolled off
 * screen. When the gate closes, the layers glide back to rest and the sensor stops.
 * [onDeflection] runs each frame while the layers move, and once more at rest.
 */
class TiltParallaxController(
    context: Context,
    private val surfaceEnabled: () -> Boolean,
    private val onDeflection: (x: Float, y: Float) -> Unit,
) : SensorEventListener, Choreographer.FrameCallback {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val rotationSensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val baseline = TiltBaseline()
    private val smooth = Smooth2D(TiltParallaxConstants.PHONE_SMOOTH_TAU_MS)
    private val choreographer = Choreographer.getInstance()
    private var reference: Quat? = null
    private var started = false
    private var heroVisible = false
    private var listening = false
    private var framePosted = false
    private var lastFrameNanos = 0L
    private var pinned: Deflection? = null

    private val gate: ParallaxGate
        get() = ParallaxGate(
            fullEffects = surfaceEnabled(),
            reduceMotion = !ValueAnimator.areAnimatorsEnabled(),
            lowPower = power.isPowerSaveMode,
            hidden = !started,
            heroVisible = heroVisible,
        )

    /** Call from the lifecycle: ON_START / ON_STOP. */
    fun start() {
        started = true
        refresh()
    }

    fun stop() {
        started = false
        refresh()
    }

    /** Call when the surface scrolls into or out of view. */
    fun setHeroVisible(visible: Boolean) {
        if (heroVisible == visible) return
        heroVisible = visible
        refresh()
    }

    /**
     * Debug builds only: replaces the sensor with a fixed deflection so emulators can show the
     * moved state. The gate still applies; null returns to the sensor.
     */
    fun setPinnedDeflection(deflection: Deflection?) {
        pinned = deflection
        if (deflection == null) {
            smooth.tx = 0.0
            smooth.ty = 0.0
        }
        refresh()
    }

    /** Call when the flag, animation scale or battery saver changes. */
    fun refresh() {
        val pin = pinned
        if (gate.isOpen) {
            if (pin != null) {
                stopListening()
                smooth.tx = pin.x
                smooth.ty = pin.y
                postFrame()
            } else if (!listening && rotationSensor != null) {
                reference = null
                baseline.reset()
                listening = sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        } else {
            stopListening()
            smooth.tx = 0.0
            smooth.ty = 0.0
            postFrame() // glide back to the resting pose, then stop
        }
    }

    private fun stopListening() {
        if (listening) {
            sensors.unregisterListener(this)
            listening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (pinned != null) return
        val v = event.values
        val w = if (v.size > 3) v[3].toDouble() else sqrt(maxOf(0.0, 1.0 - v[0] * v[0] - v[1] * v[1] - v[2] * v[2]))
        val q = Quat(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), w)
        val ref = reference ?: run {
            reference = q
            return
        }
        val (xDeg, yDeg) = viewerTilt(ref, q)
        val d = baseline.normalise(xDeg, yDeg, event.timestamp / 1_000_000.0)
        smooth.tx = d.x
        smooth.ty = d.y
        postFrame()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun postFrame() {
        if (framePosted) return
        framePosted = true
        choreographer.postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        framePosted = false
        // The first frame of a run assumes 16 ms; later frames use the real interval, capped at 64 ms.
        val dtMs = if (lastFrameNanos == 0L) 16.0 else minOf(64.0, (frameTimeNanos - lastFrameNanos) / 1_000_000.0)
        val settled = smooth.step(dtMs)
        onDeflection(smooth.x.toFloat(), smooth.y.toFloat())
        if (settled) {
            lastFrameNanos = 0L
        } else {
            lastFrameNanos = frameTimeNanos
            postFrame()
        }
    }

    /** Releases the sensor and any pending frame (the composable left the composition). */
    fun dispose() {
        stopListening()
        if (framePosted) {
            choreographer.removeFrameCallback(this)
            framePosted = false
        }
        lastFrameNanos = 0L
    }
}
