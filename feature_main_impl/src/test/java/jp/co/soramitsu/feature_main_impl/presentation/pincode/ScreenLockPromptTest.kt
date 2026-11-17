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

    @Test
    fun `only a success of the screen lock itself counts`() {
        assertTrue(ScreenLockPrompt.isScreenLock(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL))
        assertTrue(ScreenLockPrompt.isScreenLock(ScreenLockPrompt.KEYGUARD_CONFIRMED))
        assertFalse(ScreenLockPrompt.isScreenLock(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC))
        assertFalse(ScreenLockPrompt.isScreenLock(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN))
    }
}
