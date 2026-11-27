package jp.co.soramitsu.sora.platform

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.account.WalletStartupPass
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A start straight into the wallet's main screen in a process that never showed the startup
 * screen. Android does that when the task comes back from Recents after an update, or is
 * restored after the process was killed. The update rule: such a start runs the same checks as
 * a start from the launcher, so it is sent to the startup screen before anything of the wallet
 * is built.
 *
 * The launch of the startup screen is caught here and not carried out: this test proves where
 * the main screen sends the person, with no wallet on the device being touched.
 */
@RunWith(AndroidJUnit4::class)
class ColdMainEntryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val launcherEntry = "jp.co.soramitsu.sora.splash.presentation.SplashActivity"
    private var passBefore = false
    private val startupLaunches = mutableListOf<Intent>()

    /** Catches every start of the launcher entry from inside the app and holds it back. */
    private val startupMonitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.component?.className != launcherEntry) return null
            synchronized(startupLaunches) { startupLaunches += Intent(intent) }
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }
    private val mainMonitor = Instrumentation.ActivityMonitor(MainActivity::class.java.name, null, false)

    @Before fun setup() {
        passBefore = WalletStartupPass.isGranted()
        instrumentation.addMonitor(startupMonitor)
        instrumentation.addMonitor(mainMonitor)
    }

    @After fun cleanup() {
        instrumentation.removeMonitor(startupMonitor)
        instrumentation.removeMonitor(mainMonitor)
        if (passBefore) WalletStartupPass.grant() else WalletStartupPass.revokeForTest()
    }

    private fun startMainAsRecentsDoes(action: String? = null): MainActivity {
        // The task's own intent: the main screen by its component, in a task of its own.
        val intent = Intent(target, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setAction(action)
        target.startActivity(intent)
        val main = mainMonitor.waitForActivityWithTimeout(30_000) as MainActivity?
        assertNotNull("the main screen was never created", main)
        return main!!
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!condition()) {
            assertTrue(what, SystemClock.elapsedRealtime() < deadline)
            instrumentation.waitForIdleSync()
            SystemClock.sleep(50)
        }
    }

    @Test fun aStartStraightIntoTheMainScreenInANewProcessGoesToTheStartupScreenFirst() {
        WalletStartupPass.revokeForTest()

        val main = startMainAsRecentsDoes()

        waitFor("the startup screen was not opened") { synchronized(startupLaunches) { startupLaunches.size == 1 } }
        waitFor("the main screen stayed") { main.isFinishing || main.isDestroyed }
        val launch = synchronized(startupLaunches) { startupLaunches.single() }
        // The published launcher entry: the same start as from the launcher. It opens in the task
        // the person came back to, which a pinned app can neither leave nor clear.
        assertEquals(target.packageName, launch.component!!.packageName)
        assertEquals(Intent.ACTION_MAIN, launch.action)
        assertTrue(launch.hasCategory(Intent.CATEGORY_LAUNCHER))
        val leavesTheTask = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(0, launch.flags and leavesTheTask)
        // Nothing of the wallet was built: no view, no screen, so nothing read the database.
        instrumentation.runOnMainSync {
            assertNull(main.findViewById<View>(jp.co.soramitsu.feature_main_impl.R.id.fragmentNavHostMain))
            assertTrue(main.supportFragmentManager.fragments.isEmpty())
        }
        // Being sent on is not a pass: only the startup screen gives it.
        assertTrue(!WalletStartupPass.isGranted())
    }

    @Test fun aRestartActionKeptInTheTaskCannotSkipTheChecksOrThePinInANewProcess() {
        WalletStartupPass.revokeForTest()

        // The theme switch restarts the main screen with this action and no PIN. A task that
        // kept it must not open the wallet that way in a new process.
        val main = startMainAsRecentsDoes(MainActivity.ACTION_DARK_THEME_TOGGLED)

        waitFor("the startup screen was not opened") { synchronized(startupLaunches) { startupLaunches.size == 1 } }
        waitFor("the main screen stayed") { main.isFinishing || main.isDestroyed }
        val launch = synchronized(startupLaunches) { startupLaunches.single() }
        assertEquals(Intent.ACTION_MAIN, launch.action)
        instrumentation.runOnMainSync { assertTrue(main.supportFragmentManager.fragments.isEmpty()) }
    }
}
