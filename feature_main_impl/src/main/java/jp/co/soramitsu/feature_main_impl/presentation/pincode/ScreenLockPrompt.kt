package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

/**
 * The phone's own screen lock (its PIN, pattern or password) in the system's prompt, where that
 * screen lock is the proof that the phone's owner is here: a new app PIN over one that cannot be
 * read, and the recovery phrase in Safe Mode when no app PIN is stored. A fingerprint or face is
 * never accepted in its place: it is not the screen lock, and it may belong to someone the owner
 * once enrolled.
 *
 * Android 11 and later: `BiometricPrompt` with [AUTHENTICATORS] (the device credential alone).
 * Android 8 to 10 cannot ask `BiometricPrompt` for the device credential alone; there Keyguard's
 * own confirmation screen is used ([keyguardIntent]), as `WalletGenerationRecoveryActivity` does.
 */
object ScreenLockPrompt {

    /** The only authenticator these prompts allow: the screen lock, no biometric. */
    const val AUTHENTICATORS = BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** What Keyguard's confirmation reports as when it succeeds (Android 8 to 10). */
    const val KEYGUARD_CONFIRMED = BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL

    /** True where `BiometricPrompt` can ask for the device credential alone (Android 11+). */
    fun usesBiometricPrompt(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= Build.VERSION_CODES.R

    /** True only for a success that came from the screen lock itself, never from a biometric. */
    fun isScreenLock(authenticationType: Int): Boolean =
        authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL

    /** The prompt of Android 11 and later. */
    fun promptInfo(title: CharSequence, description: CharSequence?): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (description != null) setDescription(description) }
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

    /** Keyguard's confirmation screen (Android 8 to 10); null when the phone has no screen lock. */
    @Suppress("DEPRECATION")
    fun keyguardIntent(context: Context, title: CharSequence, description: CharSequence): Intent? =
        runCatching {
            context.getSystemService(KeyguardManager::class.java)
                ?.createConfirmDeviceCredentialIntent(title, description)
        }.getOrNull()

    /** True when the phone has a screen lock these prompts can ask for. */
    fun isAvailable(context: Context): Boolean =
        runCatching { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }
            .getOrDefault(false)
}
