package jp.co.soramitsu.ui_core.sacred

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.ui_core.lit.LitLightRig
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The size the launch scene is drawn at for a view of a given size: about one and a half pixels to the dp, as on the
 * iPhone, which is sharp enough for the rings and light on the GPU. The system stretches it to the view.
 */
internal class TenmonFrame(viewWidth: Int, viewHeight: Int, density: Float) {
    /** Scene pixels to one of the view's. */
    val scale: Float = (PIXELS_PER_DP / max(1f, density)).coerceAtMost(1f)
    val width: Int = max(1, (viewWidth * scale).roundToInt())
    val height: Int = max(1, (viewHeight * scale).roundToInt())
    val pixelsPerDp: Float = density * scale

    /**
     * The scene on this frame. With a [landing] (in the view's pixels) the mark sets down there and stays; without
     * one it stays in the middle and gives way with the rings.
     */
    fun scene(landing: TenmonFlight.Landing?): TenmonScene {
        val page = TenmonFlight.PAGE
        return TenmonScene(
            width = width.toFloat(),
            height = height.toFloat(),
            pixelsPerDp = pixelsPerDp,
            landingX = landing?.let { it.x * scale } ?: (width / 2f),
            landingY = landing?.let { it.y * scale } ?: (height / 2f),
            landingRadius = landing?.let { it.radius * scale } ?: TenmonScene.restRadius(width.toFloat(), height.toFloat()),
            pageRed = Color.red(page) / 255f,
            pageGreen = Color.green(page) / 255f,
            pageBlue = Color.blue(page) / 255f,
            keepsMark = landing != null,
        )
    }

    private companion object {
        const val PIXELS_PER_DP = 1.5f
    }
}

/** Draws a frame of the launch scene with its shader (the generated [TENMON_SHADER]). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class TenmonTracer {
    /** Throws a [RuntimeException] on a device that will not build the shader. */
    private val shader = RuntimeShader(TENMON_SHADER)
    private val paint = Paint().apply { shader = this@TenmonTracer.shader }

    /** Over [width] by [height] of [canvas]: the scene is stretched from the size its numbers were worked out for. */
    fun draw(canvas: Canvas, width: Float, height: Float, uniforms: TenmonScene.Uniforms) {
        shader.setFloatUniform("view", uniforms.view)
        shader.setFloatUniform("flight", uniforms.flight)
        shader.setFloatUniform("lens", uniforms.lens)
        shader.setFloatUniform("state", uniforms.state)
        shader.setFloatUniform("page", uniforms.page)
        canvas.withScale(width / uniforms.view[0], height / uniforms.view[1], 0f, 0f) { drawPaint(paint) }
    }
}

/**
 * The launch scene at rest in plain paint: the mark on its seven rings, in pearl white without the shell's colours.
 * What a phone shows that does not play the traced scene: an older Android, one saving power or asked to remove
 * animations, or one whose GPU would not draw it. Nothing in it moves.
 */
internal class TenmonStill {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RIM
    }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mark = soraMark().asAndroidPath()

    fun draw(canvas: Canvas, width: Float, height: Float, density: Float) {
        val unit = TenmonScene.restRadius(width, height)
        val middleX = width / 2f
        val middleY = height / 2f
        // One ring stands a step above the next: its shadow falls down and to the right of it.
        val step = unit * TenmonScene.RING_STEP
        edge.strokeWidth = 0.5f * density
        fill.color = PEARL
        for (index in TenmonScene.RING_COUNT - 1 downTo 0) {
            val radius = unit * TenmonScene.ringRadius(index)
            shadow(canvas, middleX + step * 0.63f, middleY + step * 0.83f, radius, step * 1.1f, 0.2f)
            canvas.drawCircle(middleX, middleY, radius, fill)
            canvas.drawCircle(middleX, middleY, radius, edge)
        }
        shadow(canvas, middleX + step * 0.45f, middleY + step * 0.6f, unit, step * 0.8f, 0.3f)
        drawMark(canvas, middleX, middleY, unit)
    }

    /** The mark alone, flat, centred on [x], [y] with [radius]: the red disc with 天 in white, as a screen draws it. */
    fun drawMark(canvas: Canvas, x: Float, y: Float, radius: Float) {
        fill.color = PlanetRed.toArgb()
        canvas.drawCircle(x, y, radius, fill)
        // 天 on its 22-unit disc.
        fill.color = Color.WHITE
        canvas.withTranslation(x - radius, y - radius) {
            scale(radius * 2f / 22f, radius * 2f / 22f)
            drawPath(mark, fill)
        }
    }

    /** The soft shadow of a disc of [radius]: full inside it, gone [blur] beyond its edge. */
    private fun shadow(canvas: Canvas, x: Float, y: Float, radius: Float, blur: Float, strength: Float) {
        val reach = radius + blur
        val dark = Color.argb((strength * 255f).roundToInt(), Color.red(SHADOW), Color.green(SHADOW), Color.blue(SHADOW))
        shade.shader = RadialGradient(
            x, y, reach,
            intArrayOf(dark, dark, SHADOW and 0x00FFFFFF),
            floatArrayOf(0f, max(0f, radius - blur) / reach, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, reach, shade)
    }

    private companion object {
        const val PEARL = 0xFFF1F0EE.toInt()
        const val RIM = 0xD9FFFFFF.toInt()
        const val SHADOW = 0xFF3A2F2B.toInt()
    }
}

/**
 * One frame of the launch scene standing still in the window itself, under the scene's own surface: what is seen
 * before that surface has its first frame, and after it has gone. It shows the bare page, the plain scene at rest
 * ([TenmonStill]), or a frame of the traced scene. It never builds the shader itself: the traced frame is drawn with
 * the one a playing scene built on its own thread ([sharedTracer]); until there is one, the plain picture nearest to it
 * stands in.
 */
internal class TenmonCover(context: Context) : View(context) {
    private var rests = false
    private var moment: TenmonTimeline.Moment? = null
    private var landing: TenmonFlight.Landing? = null
    private var still: TenmonStill? = null

    init {
        // Drawn once into a layer: the window's other frames only lay that picture down again.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** The bare page, as the launch window showed it. */
    fun showPage() {
        rests = false
        moment = null
        invalidate()
    }

    /** The plain scene at rest. */
    fun showRest() {
        rests = true
        moment = null
        invalidate()
    }

    /** The scene as it stands at [moment], with the mark set down at [landing] (in this view's pixels) on its way out. */
    fun showFrame(moment: TenmonTimeline.Moment, landing: TenmonFlight.Landing?) {
        rests = false
        this.moment = moment
        this.landing = landing
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(TenmonFlight.PAGE)
        if (width <= 0 || height <= 0) return
        val moment = moment
        val density = resources.displayMetrics.density
        if (rests) {
            plain().draw(canvas, width.toFloat(), height.toFloat(), density)
            return
        }
        if (moment == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && traces(canvas, moment)) return
        when {
            // The way out is over: the mark where the screen shows its own, or nothing if it gave way too.
            moment.phase == TenmonScene.Phase.OUTRO && moment.progress >= 1f ->
                landing?.let { plain().drawMark(canvas, it.x, it.y, it.radius) }
            // Still coming down: the page, as the launch window showed it.
            moment.phase == TenmonScene.Phase.INTRO -> Unit
            else -> plain().draw(canvas, width.toFloat(), height.toFloat(), density)
        }
    }

    private fun plain(): TenmonStill = still ?: TenmonStill().also { still = it }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun traces(canvas: Canvas, moment: TenmonTimeline.Moment): Boolean {
        val built = sharedTracer as? TenmonTracer ?: return false
        val scene = TenmonFrame(width, height, resources.displayMetrics.density).scene(landing)
        built.draw(canvas, width.toFloat(), height.toFloat(), scene.uniforms(moment.phase, moment.progress, moment.clock, moment.waited))
        return true
    }

    companion object {
        /**
         * The shader the covers draw their traced frames with, built off the main thread by the first scene that plays
         * ([TenmonView]); a [TenmonTracer] once there is one. Only the main thread draws with it.
         */
        @Volatile
        internal var sharedTracer: Any? = null
    }
}

/**
 * Plays the launch scene ([TenmonScene], drawn by [TenmonTracer]) as the process's [TenmonTimeline] tells it.
 *
 * Frames are drawn on a thread of their own into a surface of their own, which the system shows without the main
 * thread's help, so the scene keeps its pace while the main thread starts the wallet and builds the first screen.
 * The surface lies over its window; until its first frame is drawn the window shows through, where a [TenmonCover]
 * stands. The first view to draw starts the clock; a later one joins it where it stands. Only Android 13 and later
 * draw it: on an older one the view gives up at once.
 */
internal class TenmonView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    /** The clock the scene runs on. Set before the view is shown. */
    var timeline: TenmonTimeline = TenmonTimeline()

    /** Where the mark lands, in this view's pixels. Unset, it gives way with the rings. */
    @Volatile
    var landing: TenmonFlight.Landing? = null

    /** Whether the phone's tilt leans the stack and moves the lamp. */
    @Volatile
    var tilts = false

    /** On the main thread: the device would not draw the scene, or took too long to build it. */
    var onFailed: (() -> Unit)? = null

    /** On the main thread: the way out has run its time and its last frame is drawn. */
    var onOver: (() -> Unit)? = null

    private val posted = AtomicInteger()

    /** Held while a frame is drawn into the surface: once the surface is going, no frame is begun. */
    private val frameLock = Any()
    private var drawing: Drawing? = null
    private var stopped = false
    private val buildWatch = Runnable { if (drawing?.hasDrawn != true) fail() }

    /**
     * Frames that have really been drawn: a frame counts once the one after it has been handed over, which the
     * renderer accepts only when it has finished the one before.
     */
    val framesCompleted: Int get() = max(0, posted.get() - 1)

    init {
        // Over the window, not behind a hole in it: nothing shows black before the first frame.
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.RGBX_8888)
        holder.addCallback(this)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A surface that never comes, or never takes a frame, gives way to the plain scene.
        if (!stopped) watch()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(buildWatch)
        super.onDetachedFromWindow()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (stopped || drawing != null || width <= 0 || height <= 0) return
        val frame = TenmonFrame(width, height, resources.displayMetrics.density)
        holder.setFixedSize(frame.width, frame.height)
        drawing = Drawing(holder, frame).also { it.start() }
        // Each surface has the same time for its first frame. A phone that needs too long for the scene's first build
        // shows the plain scene now, and has the build in its cache the next time.
        watch()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        removeCallbacks(buildWatch)
        // Nothing may draw into the surface once this returns: the frame being drawn is waited for, and no other begins.
        drawing?.halt()
        synchronized(frameLock) { drawing = null }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val drawing = drawing ?: return
        if (width <= 0 || height <= 0) return
        // The window was resized under the scene (a phone turned or unfolded): it is drawn for the new size.
        val frame = TenmonFrame(width, height, resources.displayMetrics.density)
        holder.setFixedSize(frame.width, frame.height)
        drawing.frame = frame
    }

    private fun watch() {
        removeCallbacks(buildWatch)
        postDelayed(buildWatch, warmUpLimitMillis())
    }

    /**
     * How long the scene's first build may take before the plain scene is shown instead. A debuggable build told to
     * be patient (`adb shell setprop log.tag.SoraTenmonPatient DEBUG`) waits far longer, so the traced scene can be
     * filmed on a slow emulator.
     */
    private fun warmUpLimitMillis(): Long {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return if (debuggable && Log.isLoggable(PATIENT_TAG, Log.DEBUG)) PATIENT_LIMIT_MILLIS else WARM_UP_LIMIT_MILLIS
    }

    /** Draws no more after the frame it is on. The view can then be taken out of its window. */
    fun stop() {
        stopped = true
        removeCallbacks(buildWatch)
        // The worker is kept until its surface goes, which waits for the frame it is on.
        drawing?.halt()
    }

    private fun fail() {
        if (stopped) return
        stop()
        onFailed?.invoke()
    }

    private inner class Drawing(private val holder: SurfaceHolder, @Volatile var frame: TenmonFrame) : Thread("sacred-tenmon") {
        @Volatile
        private var running = true

        /** Whether this surface has had a frame drawn into it. */
        @Volatile
        var hasDrawn = false
            private set

        /** Draws no more after the frame it is on; does not wait for it. */
        fun halt() {
            running = false
        }

        override fun run() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                post { fail() }
                return
            }
            val tracer = try {
                TenmonTracer()
            } catch (broken: RuntimeException) {
                Log.w(TAG, "The launch scene's shader did not build", broken)
                if (running) post { fail() }
                return
            }
            // The covers draw their still frames with a shader of their own, built here rather than on the main thread.
            if (TenmonCover.sharedTracer == null) {
                TenmonCover.sharedTracer = try {
                    TenmonTracer()
                } catch (broken: RuntimeException) {
                    null
                }
            }
            try {
                if (!timeline.isStarted) {
                    // The first frame costs the GPU its build of the scene. It is drawn before the clock starts,
                    // where the scene is still the bare page the launch window showed; the second is accepted only
                    // once the first is drawn.
                    val warmStart = SystemClock.elapsedRealtimeNanos()
                    repeat(2) { draw(tracer, timeline.at(SystemClock.elapsedRealtimeNanos())) }
                    if (!running) return
                    val warmMillis = (SystemClock.elapsedRealtimeNanos() - warmStart) / 1_000_000
                    if (timeline.start(SystemClock.elapsedRealtimeNanos())) {
                        Log.d(TAG, "The launch scene starts after $warmMillis ms of building")
                    }
                }
                var next = SystemClock.elapsedRealtimeNanos()
                while (running) {
                    val moment = timeline.at(SystemClock.elapsedRealtimeNanos())
                    if (!draw(tracer, moment)) break
                    if (moment.over) {
                        Log.d(TAG, "The launch scene drew ${posted.get()} frames here")
                        post { if (running) onOver?.invoke() }
                        break
                    }
                    // Sixty frames a second at most.
                    next = max(next + FRAME_NANOS, SystemClock.elapsedRealtimeNanos() + 1_000_000)
                    val wait = next - SystemClock.elapsedRealtimeNanos()
                    if (wait > 0) sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                }
            } catch (gone: RuntimeException) {
                // The surface went away mid-frame, or the device would not draw it.
                if (running) {
                    Log.w(TAG, "The launch scene stopped drawing", gone)
                    post { fail() }
                }
            } catch (interrupted: InterruptedException) {
                // Asked to stop.
            }
        }

        private var scene: TenmonScene? = null
        private var sceneFrame: TenmonFrame? = null
        private var sceneLanding: TenmonFlight.Landing? = null

        /** Draws [moment] into the surface; false once the surface is going and nothing more may be drawn. */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun draw(tracer: TenmonTracer, moment: TenmonTimeline.Moment): Boolean {
            val frame = frame
            val landing = landing
            val scene = scene.takeIf { sceneFrame === frame && sceneLanding === landing }
                ?: frame.scene(landing).also {
                    scene = it
                    sceneFrame = frame
                    sceneLanding = landing
                }
            // The light rig is read here and nowhere stored: it rests at nought while no screen is under it.
            val tiltX = if (tilts) LitLightRig.x else 0f
            val tiltY = if (tilts) LitLightRig.y else 0f
            val uniforms = scene.uniforms(moment.phase, moment.progress, moment.clock, moment.waited, tiltX, tiltY)
            synchronized(frameLock) {
                if (!running) return false
                val canvas = holder.lockHardwareCanvas() ?: return running
                try {
                    // Whatever size the surface is just now, the scene fills it.
                    tracer.draw(canvas, canvas.width.toFloat(), canvas.height.toFloat(), uniforms)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            hasDrawn = true
            posted.incrementAndGet()
            return true
        }
    }

    private companion object {
        const val TAG = "SoraTenmon"
        const val FRAME_NANOS = 16_666_667L

        const val WARM_UP_LIMIT_MILLIS = 1200L
        const val PATIENT_TAG = "SoraTenmonPatient"
        const val PATIENT_LIMIT_MILLIS = 30_000L
    }
}

/**
 * The launch scene in a window: the traced scene on its own surface ([TenmonView]) where the device plays it, over
 * a still ([TenmonCover]) that stands in the window itself. Where the device does not play it, the still alone shows
 * the plain scene at rest. The phone's tilt is read only while the traced scene is attached and seen.
 */
internal class TenmonStage(context: Context) : FrameLayout(context) {
    val cover = TenmonCover(context)

    /** The traced scene, while it plays here. */
    var scene: TenmonView? = null
        private set

    /** The traced scene would not draw here; the plain scene at rest has taken its place. */
    var onGaveUp: (() -> Unit)? = null

    private var underLight = false

    init {
        setBackgroundColor(TenmonFlight.PAGE)
        // The page casts no shadow when it is raised over the window's other views.
        outlineProvider = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Shows the plain scene at rest, and plays nothing. */
    fun showRest() {
        dropScene()
        cover.showRest()
    }

    /**
     * Plays the traced scene on [timeline]. Until its surface has a frame the window shows the scene as it stands at
     * [first], or the bare page.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun play(timeline: TenmonTimeline, first: TenmonTimeline.Moment?) {
        if (scene != null) return
        if (first != null) cover.showFrame(first, null) else cover.showPage()
        val view = TenmonView(context)
        view.timeline = timeline
        view.tilts = true
        view.onFailed = { giveUp() }
        scene = view
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        seekLight()
    }

    /**
     * Stops the traced scene where it waits, with nothing moving, and shows it in the window itself, so whatever the
     * screen puts over the scene is seen.
     */
    fun rest() {
        val view = scene ?: return
        cover.showFrame(RESTING, null)
        scene = null
        view.stop()
        seekLight()
        // The still is on screen first, a frame later the surface over it goes.
        postOnAnimation { postOnAnimation { removeView(view) } }
    }

    /** Takes the traced scene's surface away. The still under it is what stays. */
    fun dropScene() {
        val view = scene ?: return
        scene = null
        view.stop()
        removeView(view)
        seekLight()
    }

    private fun giveUp() {
        if (scene == null) return
        dropScene()
        cover.showRest()
        onGaveUp?.invoke()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        seekLight()
    }

    override fun onDetachedFromWindow() {
        seekLight(attached = false)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        seekLight(visibility = visibility)
    }

    /** Under the moving light while the traced scene is attached and seen, and not a moment longer. */
    private fun seekLight(attached: Boolean = isAttachedToWindow, visibility: Int = windowVisibility) {
        val wanted = scene != null && attached && visibility == VISIBLE
        if (wanted == underLight) return
        underLight = wanted
        if (wanted) LitLightRig.enter(context) else LitLightRig.leave(context)
    }

    private companion object {
        /** The rings lying spread, as the wait begins. */
        val RESTING = TenmonTimeline.Moment(TenmonScene.Phase.HOLD, 0f, 0f, 0f, false)
    }
}
