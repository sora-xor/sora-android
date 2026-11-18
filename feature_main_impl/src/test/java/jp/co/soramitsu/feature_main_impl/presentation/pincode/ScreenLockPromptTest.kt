package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Codex review 1: where the phone's screen lock is the proof of ownership (a new PIN over an
 * unreadable one, the recovery phrase in Safe Mode without an app PIN), the prompt allows the
 * screen lock alone; a fingerprint or face never stands in for it.
 */
class ScreenLockPromptTest {

    @Test
    fun `the prompt allows the screen lock alone and no biometric`() {
        assertEquals(Authenticators.DEVICE_CREDENTIAL, ScreenLockPrompt.AUTHENTICATORS)
        assertEquals(0, ScreenLockPrompt.AUTHENTICATORS and (Authenticators.BIOMETRIC_WEAK or Authenticators.BIOMETRIC_STRONG))
    }

    @Test
    fun `Android 11 and later use the system prompt and earlier phones Keyguard's confirmation`() {
        assertTrue(ScreenLockPrompt.usesBiometricPrompt(30))
        assertTrue(ScreenLockPrompt.usesBiometricPrompt(36))
        // BiometricPrompt cannot take the device credential alone before Android 11.
        assertFalse(ScreenLockPrompt.usesBiometricPrompt(29))
        assertFalse(ScreenLockPrompt.usesBiometricPrompt(26))
    }

    // Codex re-review 1: Android 11 and later accept the device credential alone. Neither a
    // biometric nor Keyguard's result (which may come from a fingerprint) ever counts there.
    @Test
    fun `Android 11 and later accept the device credential alone and never a biometric`() {
        listOf(30, 31, 34, 36).forEach { sdk ->
            assertTrue(ScreenLockPrompt.accepts(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL, sdk))
            assertFalse(ScreenLockPrompt.accepts(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC, sdk))
            assertFalse(ScreenLockPrompt.accepts(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN, sdk))
            assertFalse(ScreenLockPrompt.accepts(ScreenLockPrompt.SCREEN_LOCK_OR_SYSTEM_BIOMETRIC_PRE_R, sdk))
        }
    }

    // Platform limit, kept on purpose: Android 8 to 10 have no screen-lock-only API, so Keyguard's
    // confirmation (screen lock, or a fingerprint the system accepted) is what counts there, and
    // only that: a BiometricPrompt result of any kind does not.
    @Test
    fun `Android 8 to 10 accept Keyguard's confirmation only, named for what it is`() {
        listOf(26, 28, 29).forEach { sdk ->
            assertTrue(ScreenLockPrompt.accepts(ScreenLockPrompt.SCREEN_LOCK_OR_SYSTEM_BIOMETRIC_PRE_R, sdk))
            assertFalse(ScreenLockPrompt.accepts(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC, sdk))
            assertFalse(ScreenLockPrompt.accepts(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL, sdk))
        }
        // It is not a BiometricPrompt result type, so it can never pass as the device credential.
        assertFalse(
            ScreenLockPrompt.SCREEN_LOCK_OR_SYSTEM_BIOMETRIC_PRE_R in setOf(
                BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN,
                BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL,
                BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC,
            )
        )
    }
}
