package jp.co.soramitsu.feature_main_impl.presentation

/**
 * A restart from inside the unlocked wallet (new language, added account) skips the PIN once.
 *
 * The permission lives in this process and the next start uses it up. The intent's action alone
 * is not enough: the system keeps it in the task and delivers it again when the task comes back
 * from Recents or after process death, and that start has to ask for the PIN.
 */
internal object TrustedMainRestart {
    @Volatile
    private var granted: String? = null

    fun grant(action: String) {
        granted = action
    }

    fun consume(action: String?): Boolean {
        val expected = granted
        granted = null
        return expected != null && expected == action
    }
}
