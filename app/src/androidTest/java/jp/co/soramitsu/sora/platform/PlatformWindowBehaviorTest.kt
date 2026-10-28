package jp.co.soramitsu.sora.platform

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.*
import jp.co.soramitsu.common.util.*
import jp.co.soramitsu.common.util.ext.*
import jp.co.soramitsu.common.presentation.view.ViewAnimations
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformWindowBehaviorTest {
    @org.junit.After fun releaseFrameworkMocks() { unmockkAll(); clearAllMocks() }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Test fun keyboardListenerOnlyEmitsVisibilityTransitionsAndReleaseDetaches() = main {
        var visibleBottom = 1000
        val view = object : View(context) { override fun getWindowVisibleDisplayFrame(out: Rect) { out.set(0, 0, 500, visibleBottom) } }
        view.layout(0, 0, 500, 1000)
        val calls = mutableListOf<String>()
        val helper = KeyboardHelper(view)
        helper.setKeyboardListener(object : KeyboardHelper.KeyboardListener {
            override fun onKeyboardShow() { calls += "show" }
            override fun onKeyboardHide() { calls += "hide" }
        })
        assertFalse(helper.isVisible)
        view.viewTreeObserver.dispatchOnGlobalLayout(); assertFalse(helper.isKeyboardShowing)
        visibleBottom = 600; view.viewTreeObserver.dispatchOnGlobalLayout(); view.viewTreeObserver.dispatchOnGlobalLayout()
        assertTrue(helper.isKeyboardShowing); assertEquals(listOf("show"), calls)
        visibleBottom = 1000; view.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals(listOf("show", "hide"), calls)
        helper.release(); visibleBottom = 600; view.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals(listOf("show", "hide"), calls)
    }

    @Test fun screenshotProtectionUpdatesAndReattachesOnlyOnFlagChange() {
        mockkObject(BuildUtils)
        try {
            every { BuildUtils.isPlayMarket() } returns true
            androidx.test.core.app.ActivityScenario.launch(androidx.activity.ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    var attached = 0; var detached = 0
                    val observer = object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(view: View) { attached++ }
                        override fun onViewDetachedFromWindow(view: View) { detached++ }
                    }
                    decor.addOnAttachStateChangeListener(observer)
                    val helper = ScreenshotBlockHelper(activity)
                    helper.disableScreenshoting(); val detachedAfterDisable = detached; helper.disableScreenshoting(); assertEquals(detachedAfterDisable, detached)
                    assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                    helper.enableScreenshoting(); val detachedAfterEnable = detached; helper.enableScreenshoting(); assertEquals(detachedAfterEnable, detached)
                    assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                    assertTrue(detached >= 1)
                    every { BuildUtils.isPlayMarket() } returns false
                    helper.disableScreenshoting(); assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                    decor.removeOnAttachStateChangeListener(observer)
                }
            }
        } finally { unmockkObject(BuildUtils) }
    }

    @Test fun compatibilityPackageLookupBuildsHomeIntentAndDiagnosticOsName() = main {
        val manager = mockk<PackageManager>()
        val resolved = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "fixture.launcher" } }
        val info = PackageInfo().apply { versionName = "1.2" }
        every { manager.resolveActivity(any<Intent>(), any<PackageManager.ResolveInfoFlags>()) } returns resolved
        every { manager.getPackageInfo("fixture.launcher", any<PackageManager.PackageInfoFlags>()) } returns info
        val activity = object : Activity() { override fun getPackageManager() = manager; override fun getResources() = context.resources }
        assertEquals("fixture.launcher 1.2", activity.getOsName())
        assertTrue(activity.getSize().second > 0); assertTrue(activity.getSize().third > 0)
        verify { manager.resolveActivity(match { it.action == Intent.ACTION_MAIN && it.hasCategory(Intent.CATEGORY_HOME) }, any<PackageManager.ResolveInfoFlags>()) }
        every { manager.resolveActivity(any<Intent>(), any<PackageManager.ResolveInfoFlags>()) } returns null
        assertEquals("- -", activity.getOsName())
    }

    @Test fun receiverCleanupToleratesAlreadyUnregisteredReceiver() = main {
        var calls = 0
        val activity = object : Activity() {
            override fun unregisterReceiver(receiver: BroadcastReceiver) {
                calls++
                if (calls > 1) throw IllegalArgumentException("not registered")
            }
        }
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) = Unit }
        activity.unregisterReceiverIfNeeded(receiver)
        activity.unregisterReceiverIfNeeded(receiver)
        assertEquals(2, calls)
    }

    @Test fun loaderRotationIsAnIndependentInfiniteAnimation() = main {
        val first = ViewAnimations.rotateAnimation; val second = ViewAnimations.rotateAnimation
        assertNotSame(first, second); assertEquals(1, first.animations.size)
        assertEquals(1600L, first.animations.single().duration)
        assertEquals(android.view.animation.Animation.INFINITE, first.animations.single().repeatCount)
    }
}
