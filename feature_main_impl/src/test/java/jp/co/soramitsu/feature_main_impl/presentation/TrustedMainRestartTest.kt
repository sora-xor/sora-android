package jp.co.soramitsu.feature_main_impl.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrustedMainRestartTest {
    @Before
    fun clearPendingRestart() {
        TrustedMainRestart.consume(null)
    }

    @Test
    fun `a restart skips the PIN once and the same action from the task asks for it`() {
        TrustedMainRestart.grant(LANGUAGE)

        assertTrue(TrustedMainRestart.consume(LANGUAGE))
        assertFalse(TrustedMainRestart.consume(LANGUAGE))
    }

    @Test
    fun `an action delivered after process death has nothing granted and asks for the PIN`() {
        assertFalse(TrustedMainRestart.consume(LANGUAGE))
        assertFalse(TrustedMainRestart.consume(ACCOUNT_ADDED))
    }

    @Test
    fun `another start does not use a restart granted for a different action`() {
        TrustedMainRestart.grant(LANGUAGE)

        assertFalse(TrustedMainRestart.consume(ACCOUNT_ADDED))
        assertFalse(TrustedMainRestart.consume(LANGUAGE))
    }

    @Test
    fun `a normal start clears a restart that never arrived`() {
        TrustedMainRestart.grant(ACCOUNT_ADDED)

        assertFalse(TrustedMainRestart.consume(null))
        assertFalse(TrustedMainRestart.consume(ACCOUNT_ADDED))
    }

    private companion object {
        const val LANGUAGE = "language"
        const val ACCOUNT_ADDED = "account added"
    }
}
