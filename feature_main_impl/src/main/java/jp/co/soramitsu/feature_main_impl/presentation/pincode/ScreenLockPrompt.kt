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
 * Android 11 and later: `BiometricPrompt` with [AUTHENTICATORS] (the device credential alone),
 * and only a success the prompt reports as the device credential counts ([accepts]).
 *
 * Android 8 to 10, platform limit: no API of those versions asks for the screen lock alone.
 * `BiometricPrompt` cannot take the device credential alone there, and Keyguard's own
 * confirmation screen ([keyguardIntent], as `WalletGenerationRecoveryActivity` uses) may accept
 * the phone's fingerprint as well (Android 9 and 10 offer it on that screen and answer
 * `RESULT_OK` either way). Its success is therefore named for what it is,
 * [SCREEN_LOCK_OR_SYSTEM_BIOMETRIC_PRE_R], and is accepted on those versions only: it is the
 * system's own confirmation, and asking for the recovery phrase instead would lock out owners who
 * no longer have it (before this check nothing was asked at all). Android 11 and later never
 * accept it.
 */
object ScreenLockPrompt {

    /** The only authenticator these prompts allow: the screen lock, no biometric. */
    const val AUTHENTICATORS = BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Keyguard's confirmation succeeded (Android 8 to 10): the phone's screen lock, or a
     * fingerprint the system accepted on that screen; the platform does not say which. Not a
     * `BiometricPrompt` result type, so it can never be mistaken for the device credential.
     */
    const val SCREEN_LOCK_OR_SYSTEM_BIOMETRIC_PRE_R = -0x5C10

    /** True where `BiometricPrompt` can ask for the device credential alone (Android 11+). */
    fun usesBiometricPrompt(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= Build.VERSION_CODES.R

    /**
     * True when [authenticationType] is the system's confirmation this phone's version can give:
     * on Android 11 and later the device credential alone (never a biometric, never Keyguard's
     * result), on Android 8 to 10 Keyguard's confirmation (see the platform limit above).
     */
    fun accepts(authenticationType: Int, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        if (usesBiometricPrompt(sdkInt)) {
            authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL
        } else {
            authenticationType == SCREEN_LOCK_OR_SYSTEM_BIOMETRIC_PRE_R
        }

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
