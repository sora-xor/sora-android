package jp.co.soramitsu.sora.platform

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.*
import jp.co.soramitsu.common.util.*
import jp.co.soramitsu.common.util.ext.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformWindowBehaviorTest {
    @org.junit.After fun releaseFrameworkMocks() { unmockkAll(); clearAllMocks() }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

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
}
