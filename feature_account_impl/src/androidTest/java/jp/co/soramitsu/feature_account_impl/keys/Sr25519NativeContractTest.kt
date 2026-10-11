package jp.co.soramitsu.feature_account_impl.keys

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The behaviour of the packaged sr25519 library that `StoredWalletSecretsJudge` relies on when it
 * proves a stored key by signing a random challenge:
 * - malformed stored keys make the library throw a Java exception (they never abort the process),
 * - a signature only verifies under the public key that belongs to the private scalar, and
 * - the nonce half of the secret does not have to be the freshly derived one to sign.
 */
@RunWith(AndroidJUnit4::class)
class Sr25519NativeContractTest {
    private val message = "sora-wallet-native-contract".encodeToByteArray()
    private val pair = Sr25519JNI.keypairFromSeed(ByteArray(32) { (it * 3 + 1).toByte() })
    private val secret = pair.copyOfRange(0, 64)
    private val public = pair.copyOfRange(64, 96)

    @Test
    fun malformedKeysThrowInsteadOfCrashing() {
        val scalarAboveGroupOrder = ByteArray(32) { 0xff.toByte() } + pair.copyOfRange(32, 64)
        assertTrue(runCatching { Sr25519JNI.sign(public, scalarAboveGroupOrder, message) }.isFailure)
        // A key pair stored without its nonce (process killed between two preference edits).
        assertTrue(runCatching { Sr25519JNI.sign(public, pair.copyOfRange(0, 32), message) }.isFailure)
        val notAPoint = ByteArray(32) { 0xff.toByte() }
        assertTrue(runCatching { Sr25519JNI.sign(notAPoint, secret, message) }.isFailure)
        val signature = Sr25519JNI.sign(public, secret, message)
        assertTrue(runCatching { Sr25519JNI.verify(signature, message, notAPoint) }.isFailure)
        assertFalse(Sr25519JNI.verify(signature.copyOfRange(0, 10), message, public))
    }

    @Test
    fun signatureVerifiesOnlyUnderThePublicKeyOfThePrivateScalar() {
        val otherPublic = Sr25519JNI.keypairFromSeed(ByteArray(32) { 9 }).copyOfRange(64, 96)
        // Signed with this secret but labelled with another wallet's public key.
        val mislabelled = Sr25519JNI.sign(otherPublic, secret, message)
        assertFalse(Sr25519JNI.verify(mislabelled, message, otherPublic))
        assertFalse(Sr25519JNI.verify(mislabelled, message, public))
        assertTrue(Sr25519JNI.verify(Sr25519JNI.sign(public, secret, message), message, public))
    }

    @Test
    fun aDifferentNonceStillSigns() {
        val otherNonce = pair.copyOfRange(0, 32) + ByteArray(32) { 7 }
        assertTrue(Sr25519JNI.verify(Sr25519JNI.sign(public, otherNonce, message), message, public))
    }
}
