package jp.co.soramitsu.sora.neon

import jp.co.soramitsu.sora.testutil.NativeDisplayCapture

import android.content.ComponentName
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.util.TypedValue
import android.os.Build
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airbnb.lottie.LottieAnimationView
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.SoraApp
import jp.co.soramitsu.sora.splash.presentation.SplashActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in native startup check, restricted to the disposable unfunded release-media wallet. */
@RunWith(AndroidJUnit4::class)
class SplashAnimationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as SoraApp
    private var previousReduceEffects: Boolean? = null
    private val opened = mutableListOf<Activity>()
    private val output get() = File(context.getExternalFilesDir(null), "splash-motion").apply { mkdirs() }

    @Before fun requirePublicFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("soraPublicFixture") == "true")
        val accounts = runBlocking { AppDatabase.get(context).accountDao().getAccounts() }
        check(accounts.single().substrateAddress == "cnT65V7vJnPkR2Y2AxXwV1Bpcbab7vNaHGd3ZxXjrTpPuxvj2")
        instrumentation.runOnMainSync { HardwareRenderer.setDrawingEnabled(true) }
        previousReduceEffects = app.darkThemeManager.reduceEffectsFlow.value
    }

    @After fun restorePreference() {
        opened.forEach { activity -> instrumentation.runOnMainSync { activity.finish() } }
        previousReduceEffects?.let { runBlocking { app.darkThemeManager.setReduceEffects(it) } }
    }

    @Test fun launchWindowIsNeutralBeforeTheSavedThemeLoads() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, SplashActivity::class.java), 0)
        assertEquals(R.style.SplashTheme, info.theme)
        for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), info.theme)
            val attributes = mutableListOf(android.R.attr.windowBackground, android.R.attr.colorBackground)
            if (Build.VERSION.SDK_INT >= 31) attributes += android.R.attr.windowSplashScreenBackground
            for (attribute in attributes) {
                val value = TypedValue()
                assertTrue(themed.theme.resolveAttribute(attribute, value, true))
                assertEquals("OS launch background must not depend on system appearance", 0xff06060b.toInt(), value.data)
            }
        }
    }

    @Test fun readyStartupUsesThePlatformAppropriateIntroduction() {
        runBlocking { app.darkThemeManager.setReduceEffects(false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            launchAndMeasure("system-splash", pauseRenderer = false) { animation, _ ->
                instrumentation.runOnMainSync {
                    assertEquals(android.view.View.GONE, animation.visibility)
                    assertTrue("System splash must not be followed by a second animation", !animation.isAnimating)
                }
            }
            return
        }
        val elapsed = launchAndMeasure("normal", pauseRenderer = false) { animation, notNavigated ->
            Thread.sleep(650)
            var first = 0f
            instrumentation.runOnMainSync { first = animation.progress }
            assertTrue("Introduction must be moving, not skipped", first > 0.05f && first < 0.8f)
            assertTrue("Fast readiness must not navigate during the introduction", notNavigated())
            capture("normal-intro-first")
            Thread.sleep(150)
            var second = 0f
            instrumentation.runOnMainSync { second = animation.progress }
            assertTrue("Original animation must continue through its introduction", second > first)
            if (notNavigated()) capture("normal-intro-second")
        }
        assertTrue("Fast verification must not cut the animation to 500 ms", elapsed >= 2200)
    }

    @Test fun reducedEffectsBypassesSplashMotion() {
        runBlocking { app.darkThemeManager.setReduceEffects(true) }
        launchAndMeasure("reduced", pauseRenderer = false) { animation, _ ->
            instrumentation.runOnMainSync {
                assertTrue("Reduced effects must not play splash motion", !animation.isAnimating)
                assertTrue("Reduced effects must show the static exit frame", animation.progress >= 0.89f)
            }
        }
    }

    @Test fun pausedRendererCannotHoldStartupIndefinitely() {
        runBlocking { app.darkThemeManager.setReduceEffects(false) }
        val elapsed = launchAndMeasure("renderer-fallback", pauseRenderer = true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            assertTrue("The paused legacy renderer should exercise the bounded fallback", elapsed >= 3500)
        }
    }

    private fun launchAndMeasure(
        name: String,
        pauseRenderer: Boolean,
        inspect: ((LottieAnimationView, () -> Boolean) -> Unit)? = null,
    ): Long {
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        val start = SystemClock.elapsedRealtime()
        try {
            ActivityScenario.launch(SplashActivity::class.java).use { scenario ->
                if (inspect != null || pauseRenderer) {
                    lateinit var animation: LottieAnimationView
                    scenario.onActivity { activity ->
                        animation = activity.findViewById(R.id.animation_view)
                        if (pauseRenderer) animation.pauseAnimation()
                    }
                    inspect?.invoke(animation) { monitor.hits == 0 }
                }
                val next = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 6500)) {
                    "Verified startup did not finish within the bounded animation window"
                }
                opened += next
                val elapsed = SystemClock.elapsedRealtime() - start
                assertTrue("Animation must not hold startup indefinitely", elapsed < 6500)
                File(output, "$name.json").writeText(JSONObject().apply {
                    put("elapsedMillis", elapsed)
                    put("nextActivity", next.javaClass.name)
                    put("reduceEffects", app.darkThemeManager.reduceEffectsFlow.value)
                    put("rendererPausedByTest", pauseRenderer)
                }.toString(2))
                return elapsed
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun capture(name: String) {
        val bitmap = NativeDisplayCapture.capture()
        try {
            File(output, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Native capture PNG could not be written" }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
