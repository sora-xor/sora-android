package jp.co.soramitsu.sora.platform

import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.account.WalletStartupPass
import jp.co.soramitsu.feature_main_impl.MainStarterImpl
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A pinned task (screen pinning, lock task mode) can be neither left nor cleared. The screen
 * that opens the wallet (the startup screen, after the checks) does it through the production
 * entry, [MainStarterImpl.start], and that entry has to see the pin by itself: here the task
 * is really pinned (`am task lock`, as the shell can), nothing is handed in, and the test looks
 * at where the wallet's main screen ends up. Without the detection the start asks for a new,
 * cleared task, Android refuses it in a pinned task, and nothing opens.
 */
@RunWith(AndroidJUnit4::class)
class PinnedTaskEntryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private var passBefore = false
    private var host: Activity? = null
    private var main: Activity? = null
    private val mainMonitor = Instrumentation.ActivityMonitor(MainActivity::class.java.name, null, false)

    @Before fun setup() {
        passBefore = WalletStartupPass.isGranted()
        // The startup screen gives the pass before it opens the wallet.
        WalletStartupPass.grant()
        instrumentation.addMonitor(mainMonitor)
    }

    @After fun cleanup() {
        shell("am task lock stop")
        instrumentation.removeMonitor(mainMonitor)
        instrumentation.runOnMainSync {
            main?.finish()
            host?.finish()
        }
        if (!passBefore) WalletStartupPass.revokeForTest()
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun lockTaskModeState(): Int =
        target.getSystemService(ActivityManager::class.java).lockTaskModeState

    private fun waitFor(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (!condition()) {
            assertTrue(what, SystemClock.elapsedRealtime() < deadline)
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
    }

    private fun hasPinScreen(activity: Activity): Boolean {
        var found = false
        instrumentation.runOnMainSync {
            fun visit(fragments: List<Fragment>) {
                fragments.forEach { fragment ->
                    if (fragment is PincodeFragment) found = true else visit(fragment.childFragmentManager.fragments)
                }
            }
            visit((activity as FragmentActivity).supportFragmentManager.fragments)
        }
        return found
    }

    @Test fun aPinnedScreenOpensTheWalletInItsOwnTaskThroughTheProductionEntry() {
        // A screen of the app at the root of the app's task, as the startup screen is when the
        // pinned task comes back.
        val hostMonitor = instrumentation.addMonitor(OnboardingActivity::class.java.name, null, false)
        target.startActivity(
            Intent(target, OnboardingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        host = hostMonitor.waitForActivityWithTimeout(60_000)
        instrumentation.removeMonitor(hostMonitor)
        val opener = host
        assertNotNull("the screen that opens the wallet did not start", opener)
        val taskId = opener!!.taskId
        shell("am task lock $taskId")
        waitFor("the task was not pinned") { lockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE }

        instrumentation.runOnMainSync { MainStarterImpl().start(opener) }

        main = mainMonitor.waitForActivityWithTimeout(60_000)
        val wallet = main
        assertNotNull("the wallet's main screen did not open in the pinned task", wallet)
        // In the pinned task itself, and the screen that opened it closes: the task holds the wallet.
        assertEquals(taskId, wallet!!.taskId)
        waitFor("the screen that opened the wallet stayed") { opener.isFinishing || opener.isDestroyed }
        assertTrue(lockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE)
        // And the wallet asks for its PIN there, as on every start.
        waitFor("the PIN was not asked", timeoutMillis = 60_000) { hasPinScreen(wallet) }
    }
}
