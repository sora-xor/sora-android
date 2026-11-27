package jp.co.soramitsu.common.account

import android.app.Activity
import androidx.annotation.VisibleForTesting

/**
 * Whether this app process has been through the startup screen: the upgrade preparation's
 * result was looked at, the wallet verification ran, and anything it found was handled there
 * (retried, repaired, opened read-only or shown). Granted by the startup screen at the moment
 * it opens the wallet or onboarding, and by nothing else. It lives in memory only, so every
 * new process starts without it.
 *
 * The wallet's main screen asks for it before it builds anything. Android can start that screen
 * directly in a new process (the task comes back from Recents after an update or after the
 * process was killed), and such a start goes to the startup screen first, like a start from
 * the launcher, so no start can skip the checks.
 */
object WalletStartupPass {

    @Volatile
    private var granted = false

    fun grant() {
        granted = true
    }

    fun isGranted(): Boolean = granted

    /**
     * Opens the startup screen (the published launcher entry) on top of [from], in the task the
     * person came back to; [from] then finishes. Neither a new task nor a cleared one: a pinned
     * app (screen pinning, lock task mode) may do neither, and the screen would stay empty. The
     * startup screen opens the wallet in a task of its own afterwards, as on every start.
     * False when it could not be opened (no launcher entry, which no installed build lacks).
     */
    fun openStartup(from: Activity): Boolean {
        val launch = from.packageManager.getLaunchIntentForPackage(from.packageName) ?: return false
        launch.flags = 0
        return runCatching { from.startActivity(launch) }.isSuccess
    }

    /** A new process, for a test of a start that did not come through the startup screen. */
    @VisibleForTesting
    fun revokeForTest() {
        granted = false
    }
}
