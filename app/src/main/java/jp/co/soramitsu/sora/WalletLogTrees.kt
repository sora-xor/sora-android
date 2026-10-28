package jp.co.soramitsu.sora

import android.util.Log
import timber.log.Timber

/** Release builds retain only the fixed recovery outcome codes, never exception data. */
internal fun installWalletLogTree(verbose: Boolean): Timber.Tree {
    val tree = if (verbose) {
        object : Timber.DebugTree() {
            override fun createStackElementTag(element: StackTraceElement): String =
                "SORA_LOG: ${Thread.currentThread().name}"
        }
    } else {
        object : Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority == Log.WARN && tag == "WalletRecoveryStartup" && t == null &&
                    (message == "NEXUS_PENDING_RECOVERY_SCHEDULING_FAILED" ||
                        message == "SORA2_PENDING_RECOVERY_SCHEDULING_FAILED")
                ) {
                    super.log(priority, tag, message, null)
                }
            }
        }
    }
    Timber.plant(tree)
    return tree
}
