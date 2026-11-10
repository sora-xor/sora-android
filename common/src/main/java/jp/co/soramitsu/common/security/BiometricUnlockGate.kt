package jp.co.soramitsu.common.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject

/**
 * A biometric enrollment is trusted only after the owner has entered the wallet PIN, and a changed
 * one only after the owner has also said yes to it. Someone who knows the phone's passcode can add
 * a fingerprint, and the owner typing the PIN as usual must not approve it for them.
 */
class BiometricUnlockGate internal constructor(private val key: EnrollmentKey) {
    @Inject constructor() : this(AndroidEnrollmentKey())

    internal interface EnrollmentKey {
        fun exists(): Boolean
        fun approveAfterPin()
        fun cipher(): Cipher
    }

    private var pending: Cipher? = null

    /** A new PIN, or the owner's yes: the biometrics enrolled now may unlock. */
    fun approveEnrollmentAfterPin() {
        cancel()
        // Devices without strong biometrics continue to use their wallet PIN.
        runCatching { key.approveAfterPin() }
    }

    /**
     * After a typed PIN, when no enrollment was ever approved here: the first unlock after
     * updating from a version without this key. Anything that exists is left as it is.
     */
    fun approveFirstEnrollmentAfterPin() {
        cancel()
        // An unreadable Keystore approves nothing.
        if (runCatching { key.exists() }.getOrDefault(true)) return
        runCatching { key.approveAfterPin() }
    }

    /** An approved key that stopped working: biometrics were added or removed since. */
    fun enrollmentChanged(): Boolean =
        runCatching { key.exists() }.getOrDefault(false) && !isAvailable()

    fun isAvailable(): Boolean = runCatching { key.cipher() }.isSuccess

    fun prepare(): Cipher? {
        cancel()
        return runCatching { key.cipher() }.getOrNull()?.also { pending = it }
    }

    fun consume(cipher: Cipher?): Boolean {
        val expected = pending
        cancel()
        if (expected == null || cipher !== expected) return false
        // Force a Keystore operation: a success callback alone does not prove authentication.
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return runCatching { expected.doFinal(challenge); true }.getOrDefault(false)
    }

    fun cancel() { pending = null }
}

private class AndroidEnrollmentKey : BiometricUnlockGate.EnrollmentKey {
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun exists(): Boolean = store().containsAlias(ALIAS)

    override fun approveAfterPin() {
        val store = store()
        store.deleteEntry(ALIAS)
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(builder.build())
        }.generateKey()
    }

    override fun cipher(): Cipher {
        // Never create a missing or enrollment-invalidated key here. Only a typed PIN may do so.
        val key = store().getKey(ALIAS, null) as? SecretKey ?: error("PIN_REQUIRED")
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    private companion object { const val ALIAS = "sora_biometric_pin_approved_v1" }
}
