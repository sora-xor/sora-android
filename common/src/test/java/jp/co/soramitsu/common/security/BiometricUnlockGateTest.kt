package jp.co.soramitsu.common.security

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class BiometricUnlockGateTest {
    private class Key : BiometricUnlockGate.EnrollmentKey {
        var approved = false
        var failOperation = false
        override fun approveAfterPin() { approved = true }
        override fun cipher(): Cipher {
            check(approved)
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                if (!failOperation) init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(32), "AES"))
            }
        }
    }

    @Test fun `missing or changed enrollment requires PIN and is never recreated by prepare`() {
        val key = Key()
        val gate = BiometricUnlockGate(key)
        assertFalse(gate.isAvailable())
        assertNull(gate.prepare())
        assertFalse(key.approved)
        gate.approveEnrollmentAfterPin()
        assertTrue(gate.isAvailable())
        key.approved = false // enrollment invalidates the Keystore key
        assertNull(gate.prepare())
        assertFalse(key.approved)
    }

    @Test fun `only the pending cipher is accepted and proof is consumed exactly once`() {
        val gate = BiometricUnlockGate(Key().apply { approved = true })
        assertFalse(gate.consume(null))
        val first = gate.prepare()
        val second = gate.prepare()
        assertFalse(gate.consume(first))
        assertFalse(gate.consume(second)) // even the rejected attempt consumes pending state
        val valid = gate.prepare()
        assertTrue(gate.consume(valid))
        assertFalse(gate.consume(valid))
    }

    @Test fun `cancellation rejects late success and failed cryptographic operation fails closed`() {
        val key = Key().apply { approved = true }
        val gate = BiometricUnlockGate(key)
        val canceled = gate.prepare()
        gate.cancel()
        assertFalse(gate.consume(canceled))
        key.failOperation = true
        assertFalse(gate.consume(gate.prepare()))
    }
}
