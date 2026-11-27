package jp.co.soramitsu.sora.splash

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
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
import org.junit.Assert.assertNotNull
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
                assertFalse("no traced scene for the first screen to carry on", TenmonFlight.isPlaying)
                assertTrue("the first screen carries the still picture", TenmonFlight.carriesOn)
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
    fun theStillPictureCoversTheFirstScreenUntilItIsReady() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var first: FirstScreen
            scenario.onActivity { first = makeFirstScreen(it, motion = false) }
            assertEquals(TenmonFlight.Mode.STILL, first.mode)
            scenario.onActivity { activity ->
                assertTrue(TenmonFlight.carryOn(activity))
                assertEquals("the still picture covers the whole window", first.uncovered + 1, first.decor.childCount)
                assertFalse("carried on once", TenmonFlight.carryOn(activity))
                assertFalse(TenmonFlight.carriesOn)
                TenmonFlight.end(first.host)
            }
            instrumentation.waitForIdleSync()
            // Before the first screen is ready nothing of it, or of what is behind it, takes a touch.
            tap(first.key)
            assertEquals("a covered key is not pressed", 0, first.taps.get())
            SystemClock.sleep(300)
            scenario.onActivity { assertEquals("still covered", first.uncovered + 1, first.decor.childCount) }
            scenario.onActivity { TenmonFlight.firstScreenShown(null) }
            awaitUncovered(first)
            tap(first.key)
            assertEquals("the first tap on the ready screen is the screen's", 1, first.taps.get())
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
            // The DOWN is sent in the very turn of the main thread that first sees the mark flying on over the screen
            // with its surface: no fade, no end of the way out in between.
            val down = SystemClock.uptimeMillis()
            var sent = false
            var uncovered = false
            val deadline = SystemClock.uptimeMillis() + 20_000
            while (!sent && !uncovered && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    uncovered = first.decor.childCount == first.uncovered
                    val top = first.decor.getChildAt(first.decor.childCount - 1)
                    if (!uncovered && TenmonFlight.isLeaving && find(top) != null) {
                        touchKey(first, MotionEvent.ACTION_DOWN, down)
                        assertNotNull("the surface is not taken away in the touch's way", find(top))
                        sent = true
                    }
                }
                if (!sent) SystemClock.sleep(4)
            }
            // A surface that drew nothing in time fades without its way out: not the interval this test is about.
            assumeTrue("the scene's surface drew no frame in time on this device, so it faded without flying on", sent || !uncovered)
            assertTrue("the mark flies on once the first screen has said where it shows it", sent)
            instrumentation.runOnMainSync { touchKey(first, MotionEvent.ACTION_UP, down) }
            instrumentation.waitForIdleSync()
            assertEquals("the first tap goes through the scene as it gives way", 1, first.taps.get())
            // That tap also said the person will not wait: the scene is over, and what it ends on fades (its surface
            // goes once its drawing thread has let go of it).
            scenario.onActivity { assertFalse("a tap ends the scene", TenmonFlight.isCovering) }
            awaitUncovered(first)
            tap(first.key)
            assertEquals(2, first.taps.get())
        }
    }

    @Test
    fun aTapOnTheCoveredSceneUncoversTheScreenTheMomentItIsReady() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val first = firstScreen(scenario)
            assumeTrue("a hot phone shows the plain scene", first.mode == TenmonFlight.Mode.TRACED)
            awaitFrames(first.host)
            scenario.onActivity { activity ->
                assertTrue(TenmonFlight.carryOn(activity))
                TenmonFlight.end(first.host)
            }
            instrumentation.waitForIdleSync()
            // Covered, the first screen is not there yet: the tap is kept from what is behind the scene, and only
            // says the person will not wait.
            tap(first.key)
            assertEquals("a covered key is not pressed", 0, first.taps.get())
            scenario.onActivity {
                assertEquals("still covered: the screen is not ready", first.uncovered + 1, first.decor.childCount)
                TenmonFlight.firstScreenShown(null)
            }
            // Ready: the scene does not play its way out. The moment it lets touches through, its surface is gone.
            val deadline = SystemClock.uptimeMillis() + 20_000
            var through = false
            var playsOn = false
            while (!through && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(16)
                instrumentation.runOnMainSync {
                    if (TenmonFlight.isLeaving) playsOn = true
                    through = first.decor.childCount == first.uncovered ||
                        !first.decor.getChildAt(first.decor.childCount - 1).isClickable
                }
            }
            assertTrue("the ready screen is let through", through)
            assertFalse("a scene asked not to wait does not play its way out", playsOn)
            awaitUncovered(first)
            tap(first.key)
            assertEquals("the next tap is the screen's", 1, first.taps.get())
        }
    }

    @Test
    fun theFirstTapTheMomentTheStillCoveredScreenIsReadyIsTheScreens() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var first: FirstScreen
            scenario.onActivity { first = makeFirstScreen(it, motion = false) }
            scenario.onActivity { activity ->
                assertTrue(TenmonFlight.carryOn(activity))
                TenmonFlight.end(first.host)
            }
            instrumentation.waitForIdleSync()
            // At once, in the same turn of the main thread as the word that the screen is ready, before the scene has
            // settled or gone: the screen is there, and the touch is its own.
            val down = SystemClock.uptimeMillis()
            instrumentation.runOnMainSync {
                TenmonFlight.firstScreenShown(null)
                assertEquals("the picture still stands over the ready screen", first.uncovered + 1, first.decor.childCount)
                touchKey(first, MotionEvent.ACTION_DOWN, down)
            }
            instrumentation.runOnMainSync { touchKey(first, MotionEvent.ACTION_UP, down) }
            instrumentation.waitForIdleSync()
            assertEquals("the first tap on the ready screen is never lost, and counted once", 1, first.taps.get())
            awaitUncovered(first)
        }
    }

    @Test
    fun theFirstTapTheMomentTheTracedCoveredScreenIsReadyIsTheScreens() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        var endedFirst = false
        var surfaceKept = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val first = firstScreen(scenario)
            assumeTrue("a hot phone shows the plain scene", first.mode == TenmonFlight.Mode.TRACED)
            awaitFrames(first.host)
            scenario.onActivity { activity ->
                assertTrue(TenmonFlight.carryOn(activity))
                TenmonFlight.end(first.host)
            }
            instrumentation.waitForIdleSync()
            val down = SystemClock.uptimeMillis()
            instrumentation.runOnMainSync {
                TenmonFlight.firstScreenShown(null)
                val top = first.decor.getChildAt(first.decor.childCount - 1)
                assertNotNull("the traced scene still covers the ready screen", find(top))
                touchKey(first, MotionEvent.ACTION_DOWN, down)
                // The surface goes after the touch has reached the key, never in its way (removing it waits for the
                // frame being drawn).
                assertNotNull("the surface is not taken away in the touch's way", find(top))
                // The touch said the person will not wait: the scene ends in the next turn. Its surface is not taken
                // away in that turn either, but once its drawing thread has let go of it (which this check, queued
                // before that thread can say so, always precedes): a touch queued behind the end never waits for a
                // frame being drawn.
                Handler(Looper.getMainLooper()).post {
                    endedFirst = !TenmonFlight.isCovering
                    surfaceKept = find(top) != null
                }
            }
            instrumentation.runOnMainSync { touchKey(first, MotionEvent.ACTION_UP, down) }
            instrumentation.waitForIdleSync()
            assertEquals("the first tap on the ready screen is never lost, and counted once", 1, first.taps.get())
            assertTrue("the tap ended the scene", endedFirst)
            assertTrue("the surface is not taken away in the turn that ends the scene", surfaceKept)
            awaitUncovered(first)
        }
    }

    @Test
    fun aTapOnTheStartupScreensSceneIsRememberedOverTheFirstScreen() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val first = firstScreen(scenario)
            assumeTrue("a hot phone shows the plain scene", first.mode == TenmonFlight.Mode.TRACED)
            awaitFrames(first.host)
            // While the wallet is checked: a finger on the rings, sent through the window's views on the main thread.
            tapInWindow(first, first.host)
            scenario.onActivity { activity ->
                assertTrue(TenmonFlight.carryOn(activity))
                TenmonFlight.end(first.host)
                assertEquals("still covered: the screen is not ready", first.uncovered + 1, first.decor.childCount)
                TenmonFlight.firstScreenShown(null)
            }
            // Ready: the scene does not settle or play its way out, as if the tap had been on it over this screen.
            instrumentation.runOnMainSync {
                assertFalse("a scene asked not to wait does not play its way out", TenmonFlight.isLeaving)
                assertFalse("a scene asked not to wait ends at once", TenmonFlight.isCovering)
            }
            awaitUncovered(first)
        }
    }

    @Test
    fun aCoveredScreenTakesNoKeyboardFocusUntilItIsReady() {
        // As for someone with a keyboard: not in touch mode.
        instrumentation.setInTouchMode(false)
        try {
            keyboardFocusUnderTheCover()
        } finally {
            instrumentation.setInTouchMode(true)
        }
    }

    private fun keyboardFocusUnderTheCover() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var first: FirstScreen
            var focusability = -1
            scenario.onActivity {
                first = makeFirstScreen(it, motion = false)
                focusability = it.findViewById<ViewGroup>(android.R.id.content).descendantFocusability
                first.key.isFocusableInTouchMode = true
                assertTrue("the key takes focus before anything covers it", first.key.requestFocus())
            }
            scenario.onActivity { activity ->
                assertTrue(TenmonFlight.carryOn(activity))
                TenmonFlight.end(first.host)
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                assertFalse("the covered key lost its focus", first.key.isFocused)
                assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, content.descendantFocusability)
                assertFalse("a keyboard cannot reach the covered key", first.key.requestFocus())
                TenmonFlight.firstScreenShown(null)
                assertEquals("given back as it was", focusability, content.descendantFocusability)
                assertTrue("the ready screen has the keyboard's focus again, with no Tab pressed", first.key.isFocused)
            }
            awaitUncovered(first)
        }
    }

    @Test
    fun aStartupTapNeverUncoversAScreenThatHasNotSaidItIsReady() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val first = graceWayOut(scenario, startupTap = true) { top ->
                // The longest wait for the screen's word is over: the scene plays its way out as it would without the
                // tap, and keeps the screen covered and its touches. A touch on the key now is the scene's.
                assertTrue("a screen that has not said it is ready takes no touch", top.isClickable)
                assertNotNull("the scene plays its way out, not cut short by the startup tap", find(top))
                touchKey(this, MotionEvent.ACTION_DOWN, SystemClock.uptimeMillis())
            }
            instrumentation.runOnMainSync { touchKey(first, MotionEvent.ACTION_UP, SystemClock.uptimeMillis()) }
            instrumentation.waitForIdleSync()
            assertEquals("a key under the way out is not pressed", 0, first.taps.get())
            awaitUncovered(first)
            tap(first.key)
            assertEquals(1, first.taps.get())
        }
    }

    @Test
    fun aStartupTapEndsTheWayOutTheMomentALateScreenIsReady() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val first = graceWayOut(scenario, startupTap = true) { top ->
                // The screen says it is ready during the way out, with no other touch: the startup tap alone says the
                // person will not wait. Touches are the screen's at once; the surface goes after this layout turn.
                TenmonFlight.firstScreenShown(null)
                assertFalse("the ready screen takes its touches at once", top.isClickable)
                assertNotNull("the surface is never taken away inside the screen's layout", find(top))
            }
            instrumentation.runOnMainSync {
                assertFalse("asked not to wait, the scene ends at once rather than playing its way out", TenmonFlight.isCovering)
            }
            awaitUncovered(first)
        }
    }

    @Test
    fun aLateScreenWithoutAStartupTapLetsTheWayOutPlayOn() {
        assumeTrue("the traced scene needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val first = graceWayOut(scenario, startupTap = false) { top ->
                TenmonFlight.firstScreenShown(null)
                assertFalse("the ready screen takes its touches at once", top.isClickable)
                assertTrue("nobody asked it not to wait: still on its way out", TenmonFlight.isLeaving)
            }
            instrumentation.runOnMainSync {
                assertTrue("the way out plays on after the screen's word", TenmonFlight.isLeaving)
                assertEquals("still on its way out", first.uncovered + 1, first.decor.childCount)
                assertNotNull(
                    "nobody asked it not to wait: the way out plays on",
                    find(first.decor.getChildAt(first.decor.childCount - 1)),
                )
            }
            awaitUncovered(first)
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
        scenario.onActivity { made = makeFirstScreen(it, motion = true) }
        return made
    }

    /** On the main thread: [activity] as a first screen, with the startup screen's scene begun over it. */
    private fun makeFirstScreen(activity: ComponentActivity, motion: Boolean): FirstScreen {
        val root = FrameLayout(activity)
        val key = Button(activity)
        val host = FrameLayout(activity)
        root.addView(key, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        activity.setContentView(root)
        val decor = activity.window.decorView as ViewGroup
        val mode = TenmonFlight.begin(host, motion)
        val made = FirstScreen(activity, host, key, decor, decor.childCount, mode)
        key.setOnClickListener { made.taps.incrementAndGet() }
        return made
    }

    /** Until the mark of the scene over the first screen flies on; the scene lets touches through from then at least. */
    private fun awaitFlyingOn(first: FirstScreen) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        var flying = false
        while (!flying && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(16)
            instrumentation.runOnMainSync {
                val top = first.decor.getChildAt(first.decor.childCount - 1)
                // Flying on, or (a surface that drew nothing in time) already fading without its way out.
                flying = first.decor.childCount == first.uncovered + 1 && !top.isClickable &&
                    (TenmonFlight.isLeaving || find(top) == null)
            }
        }
        assertTrue("the mark flies on once the first screen has said where it shows it", flying)
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

    /**
     * A traced scene carried over a first screen that does not say it is ready, after a tap on the startup screen's
     * rings when [startupTap]. [atWayOut] runs on the main thread in the very turn that first sees the way out the
     * longest wait starts (still covered, its surface there), with the covering stage.
     */
    private fun graceWayOut(
        scenario: ActivityScenario<ComponentActivity>,
        startupTap: Boolean,
        atWayOut: FirstScreen.(View) -> Unit,
    ): FirstScreen {
        val first = firstScreen(scenario)
        assumeTrue("a hot phone shows the plain scene", first.mode == TenmonFlight.Mode.TRACED)
        awaitFrames(first.host)
        if (startupTap) tapInWindow(first, first.host)
        scenario.onActivity { activity ->
            assertTrue(TenmonFlight.carryOn(activity))
            TenmonFlight.end(first.host)
        }
        val carried = SystemClock.uptimeMillis()
        val deadline = carried + 20_000
        var seen = false
        var uncovered = false
        while (!seen && !uncovered && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                uncovered = first.decor.childCount == first.uncovered
                // Only the longest wait (4.5 s) uncovers a screen that never says it is ready, with or without a tap
                // on the startup screen: an earlier uncover is a fault, not a renderer that drew too little.
                assertFalse(
                    "a screen that never said it is ready was uncovered before the longest wait",
                    uncovered && SystemClock.uptimeMillis() - carried < EARLIEST_GRACE_UNCOVER_MILLIS,
                )
                val top = first.decor.getChildAt(first.decor.childCount - 1)
                if (!uncovered && TenmonFlight.isLeaving && find(top) != null) {
                    first.atWayOut(top)
                    seen = true
                }
            }
            if (!seen) SystemClock.sleep(4)
        }
        assumeTrue("the scene's surface drew no frame in time on this device, so it faded without its way out", seen || !uncovered)
        assertTrue("the longest wait ends in the way out", seen)
        return first
    }

    /** A touch on [first]'s key sent through its window's views, on the main thread, in the caller's turn. */
    private fun touchKey(first: FirstScreen, action: Int, downTime: Long) = touchInWindow(first, first.key, action, downTime)

    /** A tap on [view] sent through [first]'s window's views from the main thread, then the main queue drained. */
    private fun tapInWindow(first: FirstScreen, view: View) {
        val down = SystemClock.uptimeMillis()
        instrumentation.runOnMainSync { touchInWindow(first, view, MotionEvent.ACTION_DOWN, down) }
        instrumentation.runOnMainSync { touchInWindow(first, view, MotionEvent.ACTION_UP, down) }
        instrumentation.waitForIdleSync()
    }

    private fun touchInWindow(first: FirstScreen, view: View, action: Int, downTime: Long) {
        val place = IntArray(2)
        view.getLocationInWindow(place)
        val x = place[0] + view.width / 2f
        val y = place[1] + view.height * 0.75f
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            first.decor.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
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

/** The longest wait uncovers a screen that never says it is ready 4.5 s after it was carried over: never sooner. */
private const val EARLIEST_GRACE_UNCOVER_MILLIS = 4_000L
