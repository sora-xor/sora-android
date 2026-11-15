package jp.co.soramitsu.ui_core.sacred

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.atomic.AtomicBoolean
import jp.co.soramitsu.ui_core.lit.LitDay
import jp.co.soramitsu.ui_core.lit.LitNight
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Plays the flight through the gate of heaven ([TenmonScene], drawn by the generated [TENMON_SHADER]) once, over
 * whatever is below it, then fades and removes itself.
 *
 * Frames are drawn on a thread of their own into a surface of their own, which the system shows without the main
 * thread's help, so the flight keeps its pace while the main thread builds the first screen. It ends on the clock
 * whether or not a frame was drawn. A tap ends it at once.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class TenmonView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    /** The page day breaks into. Set before the view is shown. */
    var pageColor: Int = LitDay.page.toArgb()

    private var drawing: Drawing? = null
    private var finished = false

    /** Set by the drawing thread once day has broken: the surface shows nothing but the page. */
    @Volatile
    private var arrived = false

    init {
        // Over the window, not behind a hole in it: it covers everything until it leaves.
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.RGBX_8888)
        holder.addCallback(this)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        // A tap ends it.
        isClickable = true
        setOnClickListener { finish(fadeMillis = 120) }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (finished || drawing != null || width <= 0 || height <= 0) return
        // About one and a half pixels to the dp, as on the iPhone: sharp enough for the lines, light on the GPU.
        // The system stretches the smaller surface to the view.
        val density = resources.displayMetrics.density
        val scale = (PIXELS_PER_DP / max(1f, density)).coerceAtMost(1f)
        val surfaceWidth = max(1, (width * scale).roundToInt())
        val surfaceHeight = max(1, (height * scale).roundToInt())
        holder.setFixedSize(surfaceWidth, surfaceHeight)
        drawing = Drawing(holder, surfaceWidth, surfaceHeight, density * scale).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Nothing may draw into the surface once this returns.
        drawing?.stopAndJoin()
        drawing = null
        post { finish(fadeMillis = 0) }
    }

    /**
     * Leaves its host, fades the host out over the first screen, and puts the host back as it was: gone, clear
     * and untouchable. Safe to call more than once.
     *
     * A surface does not fade with its view, so the surface goes first and the host fades in its place: in the
     * page's colour if day had broken (the surface's last frame was the page), else in the launch dark.
     */
    fun finish(fadeMillis: Long) {
        if (finished) return
        finished = true
        drawing?.stopAndJoin()
        drawing = null
        val host = parent as? ViewGroup ?: return
        host.setBackgroundColor(if (arrived) pageColor else TenmonFlight.NIGHT)
        host.removeView(this)
        val leave = Runnable {
            host.visibility = View.GONE
            host.background = null
            host.alpha = 1f
            host.isClickable = false
        }
        if (fadeMillis <= 0 || !host.isAttachedToWindow) {
            leave.run()
        } else {
            host.animate().alpha(0f).setDuration(fadeMillis).withEndAction(leave).start()
        }
    }

    private inner class Drawing(
        private val holder: SurfaceHolder,
        private val width: Int,
        private val height: Int,
        private val pixelsPerDp: Float,
    ) : Thread("sacred-tenmon") {
        @Volatile
        private var running = true
        private var frames = 0

        fun stopAndJoin() {
            running = false
            if (currentThread() !== this) runCatching { join(250) }
        }

        override fun run() {
            val shader = try {
                RuntimeShader(TENMON_SHADER)
            } catch (broken: RuntimeException) {
                // A shader this device will not build: no flight.
                Log.w(TAG, "The launch scene's shader did not build", broken)
                post { finish(fadeMillis = 0) }
                return
            }
            val scene = TenmonScene(
                width = width.toFloat(),
                height = height.toFloat(),
                pixelsPerDp = pixelsPerDp,
                landingX = width / 2f,
                landingY = height * 0.30f,
                landingRadius = 32f * pixelsPerDp,
                pageRed = Color.red(pageColor) / 255f,
                pageGreen = Color.green(pageColor) / 255f,
                pageBlue = Color.blue(pageColor) / 255f,
                keepsPlanet = false,
            )
            val paint = Paint().apply { this.shader = shader }
            fun draw(uniforms: TenmonScene.Uniforms) {
                val canvas = holder.lockHardwareCanvas() ?: return
                try {
                    // The scene is worked out for the surface it is given, whatever size that is just now.
                    val fit = canvas.width / uniforms.view[0]
                    shader.setFloatUniform("view", uniforms.view[0] * fit, canvas.height.toFloat(), uniforms.view[2], uniforms.view[3] * fit)
                    shader.setFloatUniform("flight", uniforms.flight)
                    shader.setFloatUniform("lens", uniforms.lens[0] * fit, uniforms.lens[1] * canvas.height / uniforms.view[1], uniforms.lens[2] * fit, uniforms.lens[3])
                    shader.setFloatUniform("state", uniforms.state)
                    shader.setFloatUniform("page", uniforms.page)
                    canvas.drawPaint(paint)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            try {
                // The first frame costs the GPU its build of the scene. It is drawn before the clock starts, at
                // the very start of the flight, where the scene is still the launch dark; the second returns
                // only once the first is on screen. A phone that needs too long for it flies next time, when
                // the build is already in its cache, and uncovers the first screen now.
                val warmStart = SystemClock.elapsedRealtimeNanos()
                repeat(2) { draw(scene.uniforms(TenmonScene.Phase.INTRO, 0f, 0f, 0f)) }
                val warmMillis = (SystemClock.elapsedRealtimeNanos() - warmStart) / 1_000_000
                if (warmMillis > WARM_UP_LIMIT_MILLIS) {
                    Log.d(TAG, "The launch scene took $warmMillis ms to build; it flies next time")
                    post { finish(fadeMillis = 180) }
                    return
                }
                val start = SystemClock.elapsedRealtimeNanos()
                var next = start
                while (running) {
                    val seconds = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000_000f
                    val uniforms = if (seconds < TenmonScene.INTRO_SECONDS) {
                        scene.uniforms(TenmonScene.Phase.INTRO, seconds / TenmonScene.INTRO_SECONDS, seconds, 0f)
                    } else {
                        val progress = (seconds - TenmonScene.INTRO_SECONDS) / TenmonScene.OUTRO_SECONDS
                        scene.uniforms(TenmonScene.Phase.OUTRO, progress, seconds, 0f)
                    }
                    draw(uniforms)
                    frames += 1
                    if (seconds >= TenmonScene.INTRO_SECONDS + TenmonScene.OUTRO_SECONDS) {
                        arrived = true
                        Log.d(TAG, "The launch scene drew $frames frames after $warmMillis ms of building")
                        post { finish(fadeMillis = 180) }
                        break
                    }
                    // Sixty frames a second at most.
                    next = max(next + FRAME_NANOS, SystemClock.elapsedRealtimeNanos() + 1_000_000)
                    val wait = next - SystemClock.elapsedRealtimeNanos()
                    if (wait > 0) sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                }
            } catch (gone: RuntimeException) {
                // The surface went away mid-frame, or the device would not draw it: the first screen is uncovered.
                Log.w(TAG, "The launch scene stopped drawing", gone)
                post { finish(fadeMillis = 180) }
            } catch (interrupted: InterruptedException) {
                // Asked to stop.
            }
        }
    }

    private companion object {
        const val TAG = "SoraTenmon"
        const val PIXELS_PER_DP = 1.5f
        const val FRAME_NANOS = 16_666_667L

        /** How long the scene's first build may take before the flight is left for the next start. */
        const val WARM_UP_LIMIT_MILLIS = 1200L
    }
}

/** Starts the launch flight ([TenmonView]) where the device and its settings fly it. */
object TenmonFlight {
    /** The launch window's own dark, which the flight rises out of. */
    const val NIGHT = 0xFF06060B.toInt()

    /** However slow the phone, the first screen is uncovered by then. */
    private const val LONGEST_MILLIS = 5000L

    private val flown = AtomicBoolean(false)

    /**
     * The flight, once for each start of the app, over the whole window of [activity]: its first screen is
     * uncovered as day breaks into the page (the day page, or the night one in dark mode). Later activities of
     * the same process, and devices or settings that do not [fly], get nothing.
     */
    fun launchOnce(activity: Activity, fly: Boolean): Boolean {
        if (!flown.compareAndSet(false, true)) return false
        val night = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return false
        val host = FrameLayout(activity)
        host.visibility = View.GONE
        content.addView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val started = launch(host, (if (night) LitNight else LitDay).page.toArgb(), fly)
        if (!started) content.removeView(host)
        return started
    }

    /**
     * Starts the flight over everything under [host] if [fly] and this Android can draw it (13 or later).
     * Returns whether it started. Until the first frame the host shows the launch window's dark, so the screen
     * below never flashes through; when the flight is over the host is gone and clear again.
     */
    fun launch(host: ViewGroup, pageColor: Int, fly: Boolean): Boolean {
        if (!fly || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val flight = TenmonView(host.context)
        flight.pageColor = pageColor
        host.setBackgroundColor(NIGHT)
        host.alpha = 1f
        host.visibility = View.VISIBLE
        // Nothing below is touched through the flight.
        host.isClickable = true
        host.addView(
            flight,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        host.postDelayed({ flight.finish(fadeMillis = 180) }, LONGEST_MILLIS)
        return true
    }
}
