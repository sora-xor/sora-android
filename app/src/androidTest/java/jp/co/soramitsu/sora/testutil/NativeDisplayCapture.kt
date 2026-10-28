package jp.co.soramitsu.sora.testutil

import android.graphics.Bitmap
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.graphics.HardwareRendererCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real display capture, including IME, system bars, dialogs and separate camera surfaces. */
internal object NativeDisplayCapture {
    private const val CAPTURE_DEADLINE_MILLIS = 10_000L

    fun capture(
        acquire: () -> Bitmap? = { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() },
    ): Bitmap {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Display capture must run on the test thread" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val drawingWasEnabled = HardwareRendererCompat.isDrawingEnabled()
        val deadline = SystemClock.uptimeMillis() + CAPTURE_DEADLINE_MILLIS
        var attempts = 0
        try {
            if (!drawingWasEnabled) {
                instrumentation.runOnMainSync { HardwareRendererCompat.setDrawingEnabled(true) }
            }
            while (SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    awaitCommittedDisplayWindows(deadline)
                }
                check(SystemClock.uptimeMillis() < deadline) { "Display capture deadline expired before acquisition" }
                attempts += 1
                // Android 14's ScreenCapture waits only 1s for the compositor callback.
                // A null result is an acquisition failure, not a failed UI/pixel assertion.
                // Retry only null, after another real committed frame, within this deadline.
                // Keep display capture: window-only PixelCopy would omit the IME/system layers.
                val bitmap = acquire()
                if (bitmap != null) return bitmap
            }
            error("Native display capture returned null $attempts times within ${CAPTURE_DEADLINE_MILLIS}ms")
        } finally {
            if (!drawingWasEnabled) {
                instrumentation.runOnMainSync { HardwareRendererCompat.setDrawingEnabled(false) }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun awaitCommittedDisplayWindows(deadline: Long) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val callbacks = mutableListOf<Pair<ViewTreeObserver, Runnable>>()
        lateinit var committed: CountDownLatch
        try {
            var registered = false
            while (!registered && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    // Stopped/background roots may remain registered but cannot commit a frame.
                    // The focused decor can belong to an activity or a dialog.
                    val windows = WindowInspector.getGlobalWindowViews().filter {
                        it.isAttachedToWindow && it.isShown && it.windowVisibility == View.VISIBLE &&
                            it.hasWindowFocus() && it.width > 0 && it.height > 0
                    }
                    // Window focus can arrive after layout/idle, or move into a newly opened
                    // dialog. Wait only for this readiness condition, never retry assertions.
                    if (windows.isEmpty()) return@runOnMainSync
                    check(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .any { !it.isFinishing && !it.isDestroyed }) { "Capture requires a resumed activity" }
                    check(windows.all { it.isHardwareAccelerated }) { "Capture requires hardware-rendered app windows" }
                    committed = CountDownLatch(windows.size)
                    windows.forEach { window ->
                        val observer = window.viewTreeObserver
                        check(observer.isAlive) { "Capture window observer is no longer alive" }
                        val callback = Runnable { committed.countDown() }
                        observer.registerFrameCommitCallback(callback)
                        callbacks += observer to callback
                        invalidateSubtree(window)
                    }
                    registered = true
                }
                if (!registered) awaitFocusTransitionFrame(deadline)
            }
            check(registered) { "No visible, attached, focused app window within ${CAPTURE_DEADLINE_MILLIS}ms" }
            val remaining = deadline - SystemClock.uptimeMillis()
            check(remaining > 0 && committed.await(remaining, TimeUnit.MILLISECONDS)) {
                "Focused app windows did not commit a native frame within ${CAPTURE_DEADLINE_MILLIS}ms"
            }
        } finally {
            instrumentation.runOnMainSync {
                callbacks.forEach { (observer, callback) ->
                    if (observer.isAlive) observer.unregisterFrameCommitCallback(callback)
                }
            }
        }
    }

    private fun awaitFocusTransitionFrame(deadline: Long) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val frame = CountDownLatch(1)
        val callback = Choreographer.FrameCallback { frame.countDown() }
        var choreographer: Choreographer? = null
        try {
            instrumentation.runOnMainSync {
                choreographer = Choreographer.getInstance()
                choreographer?.postFrameCallback(callback)
            }
            val remaining = deadline - SystemClock.uptimeMillis()
            check(remaining > 0 && frame.await(remaining, TimeUnit.MILLISECONDS)) {
                "No focused app window before the native capture deadline"
            }
        } finally {
            instrumentation.runOnMainSync { choreographer?.removeFrameCallback(callback) }
        }
    }

    private fun invalidateSubtree(view: View) {
        if (view is ViewRootForTest) view.invalidateDescendants()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) invalidateSubtree(view.getChildAt(index))
        }
        view.invalidate()
    }
}
