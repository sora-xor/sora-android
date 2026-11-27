package jp.co.soramitsu.sora.neon

import jp.co.soramitsu.sora.testutil.NativeDisplayCapture

import android.content.ComponentName
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.util.TypedValue
import android.os.Build
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.os.SystemClock
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.splash.presentation.SplashActivity
import jp.co.soramitsu.ui_core.sacred.TenmonFlight
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
    private val opened = mutableListOf<Activity>()
    private val output get() = File(context.getExternalFilesDir(null), "splash-motion").apply { mkdirs() }

    @Before fun requirePublicFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("soraPublicFixture") == "true")
        val accounts = runBlocking { AppDatabase.get(context).accountDao().getAccounts() }
        check(accounts.single().substrateAddress == "cnT65V7vJnPkR2Y2AxXwV1Bpcbab7vNaHGd3ZxXjrTpPuxvj2")
        instrumentation.runOnMainSync { HardwareRenderer.setDrawingEnabled(true) }
    }

    @After fun closeOpenedActivities() {
        opened.forEach { activity -> instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun launchWindowIsTheLightLaunchPageBeforeTheSavedThemeLoads() {
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
                assertEquals(
                    "OS launch background must be the launch scene's page, whatever the system appearance",
                    TenmonFlight.PAGE, value.data,
                )
            }
        }
    }

    @Test fun readyStartupPlaysTheLaunchSceneAndGoesOnWhileItPlays() {
        val elapsed = launchAndMeasure("launch-scene") { scene, notNavigated ->
            instrumentation.runOnMainSync {
                assertEquals(android.view.View.VISIBLE, scene.visibility)
                assertEquals("The launch scene fills the startup screen", 1, scene.childCount)
            }
            if (notNavigated()) capture("launch-scene-startup")
        }
        assertTrue("The launch scene must not hold startup", elapsed < 6500)
    }

    @Test fun startupWithNoLaunchSceneStillOpensTheWallet() {
        launchAndMeasure("no-launch-scene") { scene, _ ->
            instrumentation.runOnMainSync { TenmonFlight.end(scene) }
        }
    }

    private fun launchAndMeasure(
        name: String,
        inspect: ((ViewGroup, () -> Boolean) -> Unit)? = null,
    ): Long {
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        // The launch scene never holds the start: a verified wallet is routed as soon as its checks answer, possibly
        // before the scenario could reach the startup screen. So the scene's host is taken as the screen starts,
        // before any route can be delivered.
        var madeScene: ViewGroup? = null
        val application = context.applicationContext as Application
        val made = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity !is SplashActivity || madeScene != null) return
                madeScene = activity.findViewById(R.id.launchScene)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(made)
        val start = SystemClock.elapsedRealtime()
        try {
            ActivityScenario.launch(SplashActivity::class.java).use { scenario ->
                if (inspect != null) {
                    val scene = requireNotNull(madeScene) { "The startup screen was not made" }
                    inspect.invoke(scene) { monitor.hits == 0 }
                }
                val next = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 6500)) {
                    "Verified startup did not finish within its bound"
                }
                opened += next
                val elapsed = SystemClock.elapsedRealtime() - start
                assertTrue("The launch scene must not hold startup indefinitely", elapsed < 6500)
                File(output, "$name.json").writeText(JSONObject().apply {
                    put("elapsedMillis", elapsed)
                    put("nextActivity", next.javaClass.name)
                }.toString(2))
                return elapsed
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(made)
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
