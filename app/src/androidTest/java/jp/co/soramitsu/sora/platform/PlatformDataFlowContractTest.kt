package jp.co.soramitsu.sora.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextPaint
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.inappupdate.InAppUpdateManager
import jp.co.soramitsu.common.util.AppStoreListing
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.SoraClickableSpan
import jp.co.soramitsu.common.util.ext.*
import jp.co.soramitsu.common.util.ext.map as mapWalletValue
import jp.co.soramitsu.common.vibration.DeviceVibrator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class PlatformDataFlowContractTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun storeLaunchFallsBackToTheSamePublicPackageWhenNoStoreAppExists() {
        compose.runOnUiThread {
            val intents = mutableListOf<Intent>()
            val context = object : ContextWrapper(compose.activity) {
                override fun startActivity(intent: Intent) {
                    intents += intent
                    if (intent.data!!.scheme == "market") throw ActivityNotFoundException()
                }
            }
            AppStoreListing.open(context)
            assertEquals(2, intents.size)
            assertEquals(AppStoreListing.storeUri(context.packageName), intents[0].data)
            assertEquals(AppStoreListing.webUri(context.packageName), intents[1].data)
            intents.forEach { assertEquals(Intent.ACTION_VIEW, it.action); assertTrue(it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0); assertNull(it.extras) }
        }
    }
    @Test fun availableStoreReceivesOneIntentWithoutOpeningTheBrowser() {
        compose.runOnUiThread {
            val intents = mutableListOf<Intent>()
            val context = object : ContextWrapper(compose.activity) { override fun startActivity(intent: Intent) { intents += intent } }
            AppStoreListing.open(context)
            assertEquals(listOf(AppStoreListing.storeUri(context.packageName)), intents.map { it.data })
        }
    }
    @Test fun liveDataOnlyNotifiesForNewValuesAndPreservesAnExistingInitialization() {
        compose.runOnUiThread {
            val source = MutableLiveData<String>(); val seen = mutableListOf<String>()
            val observer = Observer<String> { seen += it }
            source.observeForever(observer)
            try {
                source.setValueIfEmpty("initial"); source.setValueIfEmpty("ignored")
                source.setValueIfNew("initial"); source.setValueIfNew("updated"); source.setValueIfNew("updated")
                assertEquals(listOf("initial", "updated"), seen)
            } finally { source.removeObserver(observer) }
        }
    }
    @Test fun liveDataZipWaitsForBothSidesAndRecomputesFromTheLatestPair() {
        compose.runOnUiThread {
            val names = MutableLiveData<String>(); val counts = MutableLiveData<Int>()
            val zipped = names.zipWith(counts); val mapped = zipped.mapWalletValue { "${it.first}:${it.second}" }
            val seen = mutableListOf<String>(); val observer = Observer<String> { seen += it }
            mapped.observeForever(observer)
            try {
                names.value = "A"; assertTrue(seen.isEmpty())
                counts.value = 1; names.value = "B"; counts.value = 2
                assertEquals(listOf("A:1", "B:1", "B:2"), seen)
            } finally { mapped.removeObserver(observer) }
        }
    }
    @Test fun clickableSpanChangesOnlyUnderliningAndRoutesOneTap() {
        compose.runOnUiThread {
            var clicks = 0
            val span = SoraClickableSpan { clicks++ }
            val paint = TextPaint().apply { color = Color.MAGENTA; isUnderlineText = true; textSize = 24f }
            span.updateDrawState(paint)
            assertFalse(paint.isUnderlineText); assertEquals(Color.MAGENTA, paint.color); assertEquals(24f, paint.textSize)
            span.onClick(View(compose.activity)); assertEquals(1, clicks)
        }
    }
    @Test fun hapticRequestIsOneBoundedPulseWithoutTouchingTheDeviceMotor() {
        val vibrator = mockk<Vibrator>(relaxed = true)
        DeviceVibrator(vibrator).makeShortVibration()
        verify(exactly = 1) { vibrator.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
    @Test fun updateListenerDetachesWhenTheHostStops() {
        compose.runOnUiThread {
            val owner = object : InAppUpdateManager.UpdateManagerListener {
                val registry = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = registry
                override fun readyToShowFlexible(): Int? = error("No update request in this fixture")
                override fun askUserToInstall() = error("No installation authorized")
            }
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            val manager = InAppUpdateManager(compose.activity, mockk())
            runBlocking { manager.start(owner) }
            assertEquals(1, owner.registry.observerCount)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            assertEquals(0, owner.registry.observerCount)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
    @Test fun deviceParametersKeepTheirLocaleAndReportCurrentTimeWithoutRounding() {
        val zone = TimeZone.getTimeZone("Asia/Tokyo")
        val parameters = DeviceParamsProvider(1080, 1920, "Fixture model", "14", "ja", "JP", zone)
        val before = System.currentTimeMillis(); val observed = parameters.getCurrentTimeMillis(); val after = System.currentTimeMillis()
        assertTrue(observed in before..after); assertEquals("ja", parameters.language); assertEquals("JP", parameters.country)
        assertEquals(zone, parameters.timezone); assertEquals(1080, parameters.screenWidth); assertEquals(1920, parameters.screenHeight)
        assertEquals("Fixture model", parameters.model); assertEquals("14", parameters.osVersion)
    }
}
