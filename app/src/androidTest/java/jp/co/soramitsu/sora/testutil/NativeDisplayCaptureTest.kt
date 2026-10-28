package jp.co.soramitsu.sora.testutil

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.graphics.HardwareRendererCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeDisplayCaptureTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var originalDrawing = false
    private val fixtureColor = Color.rgb(34, 68, 102)
    private val displayBounds = Rect()

    @Before fun showNativeWindow() {
        originalDrawing = HardwareRendererCompat.isDrawingEnabled()
        instrumentation.runOnMainSync { HardwareRendererCompat.setDrawingEnabled(true) }
        val ready = CountDownLatch(1)
        lateinit var content: FrameLayout
        var observer: ViewTreeObserver? = null
        val layoutListener = View.OnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            // The first layout callback precedes View's isLaidOut flag update.
            if (right > left && bottom > top && view.hasWindowFocus()) ready.countDown()
        }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused && content.isLaidOut) ready.countDown()
        }
        try {
            activity.scenario.onActivity { host ->
                content = FrameLayout(host).apply { setBackgroundColor(fixtureColor) }
                content.addOnLayoutChangeListener(layoutListener)
                observer = host.window.decorView.viewTreeObserver
                observer?.addOnWindowFocusChangeListener(focusListener)
                host.setContentView(content)
                host.window.decorView.invalidate()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    displayBounds.set(host.windowManager.currentWindowMetrics.bounds)
                }
                if (content.isLaidOut && content.hasWindowFocus()) ready.countDown()
            }
            assertTrue("The native capture fixture must be laid out and focused", ready.await(5, TimeUnit.SECONDS))
        } finally {
            activity.scenario.onActivity {
                content.removeOnLayoutChangeListener(layoutListener)
                observer?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focusListener)
            }
        }
    }

    @After fun restoreDrawing() {
        instrumentation.runOnMainSync { HardwareRendererCompat.setDrawingEnabled(originalDrawing) }
    }

    @Test fun nullAcquisitionThenRealDisplayCaptureKeepsFullDimensionsAndRenderedPixels() {
        instrumentation.runOnMainSync { HardwareRendererCompat.setDrawingEnabled(false) }
        var attempts = 0
        val bitmap = NativeDisplayCapture.capture {
            attempts += 1
            if (attempts == 1) null else instrumentation.uiAutomation.takeScreenshot()
        }
        try {
            assertTrue("The injected null acquisition must be retried", attempts >= 2)
            assertFalse("Capture must restore its entry drawing state", HardwareRendererCompat.isDrawingEnabled())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertEquals("Display capture must preserve full width", displayBounds.width(), bitmap.width)
                assertEquals("Display capture must preserve full height including system bars", displayBounds.height(), bitmap.height)
            }
            // Read back pixels from the real native capture; HardwareBitmap itself forbids getPixel.
            val pixels = requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
            try {
                assertEquals("Capture must contain the actual rendered fixture, not a blank fallback",
                    fixtureColor, pixels.getPixel(pixels.width / 2, pixels.height / 2))
            } finally {
                pixels.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun persistentNullAcquisitionFailsWithinTheBoundedDeadline() {
        instrumentation.runOnMainSync { HardwareRendererCompat.setDrawingEnabled(true) }
        var attempts = 0
        val started = SystemClock.uptimeMillis()
        assertThrows(IllegalStateException::class.java) {
            NativeDisplayCapture.capture { attempts += 1; null }
        }
        val elapsed = SystemClock.uptimeMillis() - started
        assertTrue("The real native-frame path must reach acquisition", attempts > 0)
        assertTrue("Persistent null acquisition must use the 10-second deadline: ${elapsed}ms", elapsed >= 9_500)
        assertTrue("Capture must not retry indefinitely: ${elapsed}ms", elapsed < 12_000)
        assertTrue("Capture must retain an enabled entry drawing state", HardwareRendererCompat.isDrawingEnabled())
    }

    @Test fun thrownCaptureAssertionPropagatesWithoutRetryAndRestoresDrawing() {
        instrumentation.runOnMainSync { HardwareRendererCompat.setDrawingEnabled(false) }
        val expected = AssertionError("capture assertion must not be retried")
        var attempts = 0
        val actual = assertThrows(AssertionError::class.java) {
            NativeDisplayCapture.capture { attempts += 1; throw expected }
        }
        assertSame(expected, actual)
        assertEquals(1, attempts)
        assertFalse("Failure must restore its entry drawing state", HardwareRendererCompat.isDrawingEnabled())
    }
}
