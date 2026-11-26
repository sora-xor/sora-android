package jp.co.soramitsu.sora.splash

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.graphics.HardwareRendererCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.ui_core.sacred.TenmonFlight
import jp.co.soramitsu.ui_core.sacred.TenmonScene
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The launch scene on a real GPU: the generated shader builds and draws the mark on its rings, a phone that does not
 * play it gets the plain scene at rest, and the first screen is uncovered without losing a tap.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSceneTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var drawingWasEnabled = true

    @Before
    fun drawForReal() {
        // Instrumented processes may skip hardware drawing; these tests read what the GPU drew.
        drawingWasEnabled = HardwareRendererCompat.isDrawingEnabled()
        if (!drawingWasEnabled) instrumentation.runOnMainSync { HardwareRendererCompat.setDrawingEnabled(true) }
        // A loaded emulator may take seconds over the shader's first build: the scene waits for it here.
        shell("setprop log.tag.SoraTenmonPatient DEBUG")
    }

    @After
    fun restore() {
        shell("setprop log.tag.SoraTenmonPatient INFO")
        if (!drawingWasEnabled) instrumentation.runOnMainSync { HardwareRendererCompat.setDrawingEnabled(false) }
    }

    @Test
    fun theTracedSceneBuildsItsShaderAndDrawsTheMarkOnItsRings() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var host: FrameLayout
            var mode: TenmonFlight.Mode? = null
            scenario.onActivity { activity ->
                host = FrameLayout(activity)
                activity.setContentView(host)
                mode = TenmonFlight.begin(host, motion = true)
            }
            assumeTrue("a hot phone shows the plain scene", mode == TenmonFlight.Mode.TRACED)
            awaitFrames(host)
            // The rings lie spread a second after the first frame.
            SystemClock.sleep(1300)
            var surface: SurfaceView? = null
            scenario.onActivity {
                assertTrue("the scene is on for the first screen to carry on", TenmonFlight.isPlaying)
                surface = find(host)
            }
            val view = checkNotNull(surface) { "the traced scene draws on a surface of its own" }
            val shot = copy(view.width, view.height) { bitmap, done, handler -> PixelCopy.request(view, bitmap, done, handler) }
            try {
                assertMarkOnRings(shot, 0f, 0f, shot.width.toFloat(), shot.height.toFloat(), shell = true)
            } finally {
                shot.recycle()
            }
            scenario.onActivity {
                TenmonFlight.end(host)
                assertEquals(0, host.childCount)
                assertFalse(TenmonFlight.isPlaying)
            }
        }
    }

    @Test
    fun aPhoneThatDoesNotPlayItShowsThePlainSceneAtRest() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var host: FrameLayout
            lateinit var activity: ComponentActivity
            scenario.onActivity {
                activity = it
                host = FrameLayout(it)
                it.setContentView(host)
                // Power saving, or animations removed: the motion policy says no before the GPU is asked anything.
                assertEquals(TenmonFlight.Mode.STILL, TenmonFlight.begin(host, motion = false))
                assertFalse("nothing for the first screen to carry on", TenmonFlight.isPlaying)
                assertFalse(TenmonFlight.carryOn(it))
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            val place = IntArray(2)
            var width = 0
            var height = 0
            scenario.onActivity {
                assertEquals(0, TenmonFlight.framesDrawn(host))
                assertNull("no surface, no drawing thread, no sensor", find(host))
                host.getLocationInWindow(place)
                width = host.width
                height = host.height
            }
            val shot = copy(width, height) { bitmap, done, handler ->
                PixelCopy.request(activity.window, Rect(place[0], place[1], place[0] + width, place[1] + height), bitmap, done, handler)
            }
            try {
                assertMarkOnRings(shot, 0f, 0f, shot.width.toFloat(), shot.height.toFloat(), shell = false)
            } finally {
                shot.recycle()
            }
        }
    }

    @Test
    fun aTapOnTheSceneUncoversTheFirstScreenAtOnce() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val first = firstScreen(scenario)
            assumeTrue("a hot phone shows the plain scene", first.mode == TenmonFlight.Mode.TRACED)
            awaitFrames(first.host)
            scenario.onActivity { activity ->
                assertTrue(TenmonFlight.carryOn(activity))
                assertEquals("the scene covers the whole window", first.uncovered + 1, first.decor.childCount)
                assertFalse("carried on once", TenmonFlight.carryOn(activity))
                // The startup screen goes; the first screen's own copy of the scene plays on.
                TenmonFlight.end(first.host)
            }
            instrumentation.waitForIdleSync()
            // Covered, the first screen is not seen: a tap is the person saying they will not wait, and only that.
            tap(first.key)
            assertEquals("a covered key is not pressed", 0, first.taps.get())
            awaitUncovered(first)
            tap(first.key)
            assertEquals("the next tap is the screen's", 1, first.taps.get())
            scenario.onActivity { assertFalse(TenmonFlight.isPlaying) }
        }
    }

    @Test
    fun theFirstTapWhileTheMarkFliesOnReachesTheFirstScreen() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val first = firstScreen(scenario)
            assumeTrue("a hot phone shows the plain scene", first.mode == TenmonFlight.Mode.TRACED)
            // A few frames in, the rings are still spreading: the way out waits for them, and that wait is the window
            // in which the first tap is sent.
            awaitFrames(first.host)
            scenario.onActivity { activity ->
                assertTrue(TenmonFlight.carryOn(activity))
                TenmonFlight.end(first.host)
                // A screen with no mark of its own: the mark gives way with the rings.
                TenmonFlight.firstScreenShown(null)
            }
            awaitFlyingOn(first)
            scenario.onActivity { assertEquals("the scene still covers the screen", first.uncovered + 1, first.decor.childCount) }
            tap(first.key)
            assertEquals("the first tap goes through the scene as it gives way", 1, first.taps.get())
            awaitUncovered(first)
            tap(first.key)
            assertEquals(2, first.taps.get())
        }
    }

    @Test
    fun theMarkSetsDownWhereTheFirstScreenShowsIt() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val first = firstScreen(scenario)
            assumeTrue("a hot phone shows the plain scene", first.mode == TenmonFlight.Mode.TRACED)
            awaitFrames(first.host)
            var landingX = 0f
            var landingY = 0f
            var radius = 0f
            scenario.onActivity {
                assertTrue(TenmonFlight.carryOn(it))
                TenmonFlight.end(first.host)
                // Where a PIN screen shows its emblem: the upper third, about a twelfth of the width across.
                landingX = first.decor.width / 2f
                landingY = first.decor.height * 0.3f
                radius = first.decor.width / 12f
                TenmonFlight.firstScreenShown(TenmonFlight.Landing(landingX, landingY, radius))
            }
            awaitFlyingOn(first)
            // From the moment the mark flies on, the window itself (under the scene's surface, which a copy of the
            // window leaves out) holds what the scene ends on: the bare page, with the mark where the screen shows its
            // own. It is what fades over the screen once the surface has gone.
            instrumentation.waitForIdleSync()
            val shot = copy(first.decor.width, first.decor.height) { bitmap, done, handler ->
                PixelCopy.request(first.activity.window, bitmap, done, handler)
            }
            try {
                val mark = shot.getPixel((landingX - 0.8f * radius).roundToInt(), landingY.roundToInt())
                assertTrue("the mark is red where the screen shows it, was #${Integer.toHexString(mark)}", isRed(mark))
                val beside = shot.getPixel((landingX + 2f * radius).roundToInt(), landingY.roundToInt())
                assertTrue("the page beside the mark, was #${Integer.toHexString(beside)}", isPage(beside))
                val middle = shot.getPixel(shot.width / 2, shot.height / 2)
                assertTrue("the rings have sunk into the page, was #${Integer.toHexString(middle)}", isPage(middle))
            } finally {
                shot.recycle()
            }
            awaitUncovered(first)
        }
    }

    /** A first screen: one key that fills the window, under the startup screen's scene. */
    private class FirstScreen(
        val activity: ComponentActivity,
        val host: FrameLayout,
        val key: Button,
        val decor: ViewGroup,
        val uncovered: Int,
        val mode: TenmonFlight.Mode,
    ) {
        val taps = AtomicInteger()
    }

    private fun firstScreen(scenario: ActivityScenario<ComponentActivity>): FirstScreen {
        lateinit var made: FirstScreen
        scenario.onActivity { activity ->
            val root = FrameLayout(activity)
            val key = Button(activity)
            val host = FrameLayout(activity)
            root.addView(key, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            root.addView(host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            activity.setContentView(root)
            val decor = activity.window.decorView as ViewGroup
            val mode = TenmonFlight.begin(host, motion = true)
            made = FirstScreen(activity, host, key, decor, decor.childCount, mode)
            key.setOnClickListener { made.taps.incrementAndGet() }
        }
        return made
    }

    /** Until the scene over the first screen lets touches through: the mark has been told to fly on. */
    private fun awaitFlyingOn(first: FirstScreen) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        var through = false
        while (!through && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(16)
            instrumentation.runOnMainSync {
                val top = first.decor.getChildAt(first.decor.childCount - 1)
                through = first.decor.childCount == first.uncovered + 1 && !top.isClickable
            }
        }
        assertTrue("the mark flies on once the first screen has said where it shows it", through)
    }

    private fun awaitUncovered(first: FirstScreen) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        var gone = false
        while (!gone && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50)
            instrumentation.runOnMainSync { gone = first.decor.childCount == first.uncovered }
        }
        assertTrue("the scene takes itself out of the window", gone)
    }

    private fun awaitFrames(host: ViewGroup) {
        val deadline = SystemClock.uptimeMillis() + 60_000
        var frames = 0
        while (frames < 3 && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50)
            instrumentation.runOnMainSync { frames = TenmonFlight.framesDrawn(host) }
        }
        assertTrue("the shader must build and the GPU must draw frames of it (drew $frames)", frames >= 3)
    }

    /**
     * The scene at rest in [shot]: the red mark with 天 in white in the middle, pale rings round it, light to the
     * corners and nothing dark. With the [shell]'s colours the rings may carry a few hundredths of tint.
     */
    private fun assertMarkOnRings(shot: Bitmap, left: Float, top: Float, width: Float, height: Float, shell: Boolean) {
        val unit = TenmonScene.restRadius(width, height)
        val middleX = left + width / 2f
        val middleY = top + height / 2f
        fun pixel(dx: Float, dy: Float) = shot.getPixel((middleX + dx * unit).roundToInt(), (middleY + dy * unit).roundToInt())
        val glass = pixel(-0.8f, 0f)
        assertTrue("the mark is red, was #${Integer.toHexString(glass)}", isRed(glass))
        val stem = pixel(0f, -0.3f)
        assertTrue("天 is white, was #${Integer.toHexString(stem)}", minOf(Color.red(stem), Color.green(stem), Color.blue(stem)) > 215)
        // On the first, second and fourth rings, up and to the left, where no shadow falls.
        for ((dx, dy) in listOf(-1.25f to 0f, 0f to -1.3f, -1.85f to 0f, 0.5f to -3.45f)) {
            val ring = pixel(dx, dy)
            val low = minOf(Color.red(ring), Color.green(ring), Color.blue(ring))
            val high = maxOf(Color.red(ring), Color.green(ring), Color.blue(ring))
            assertTrue("a ring is pale, was #${Integer.toHexString(ring)}", low > 190)
            // Pearl is mostly white: a few hundredths of colour with the shell, none without.
            assertTrue("a ring is nearly white, was #${Integer.toHexString(ring)}", high - low < if (shell) 46 else 8)
        }
        val corner = shot.getPixel((left + width * 0.04f).roundToInt(), (top + height * 0.04f).roundToInt())
        assertTrue("light to the corners, was #${Integer.toHexString(corner)}", minOf(Color.red(corner), Color.green(corner), Color.blue(corner)) > 190)
    }

    private fun isRed(pixel: Int): Boolean = Color.red(pixel) > 170 && Color.green(pixel) < 110 && Color.blue(pixel) < 110

    private fun isPage(pixel: Int): Boolean =
        abs(Color.red(pixel) - Color.red(TenmonFlight.PAGE)) <= 8 &&
            abs(Color.green(pixel) - Color.green(TenmonFlight.PAGE)) <= 8 &&
            abs(Color.blue(pixel) - Color.blue(TenmonFlight.PAGE)) <= 8

    private fun find(view: View): SurfaceView? = when (view) {
        is SurfaceView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
        else -> null
    }

    /** Copies what is on screen into a bitmap of this size; retries a copy the GPU was not ready for. */
    private fun copy(width: Int, height: Int, request: (Bitmap, PixelCopy.OnPixelCopyFinishedListener, Handler) -> Unit): Bitmap {
        var status = -1
        repeat(5) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val copied = CountDownLatch(1)
            request(bitmap, { result ->
                status = result
                copied.countDown()
            }, Handler(Looper.getMainLooper()))
            if (copied.await(20, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS) return bitmap
            bitmap.recycle()
            SystemClock.sleep(200)
        }
        error("PixelCopy did not return a frame (status $status)")
    }

    private fun tap(view: View) {
        val place = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(place) }
        val x = place[0] + view.width / 2f
        val y = place[1] + view.height * 0.75f
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 40, MotionEvent.ACTION_UP, x, y, 0))
        instrumentation.waitForIdleSync()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
}
