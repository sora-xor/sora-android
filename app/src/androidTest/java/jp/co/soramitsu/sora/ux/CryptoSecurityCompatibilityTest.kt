package jp.co.soramitsu.sora.ux

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.xcrypto.encryption.ed25519.Ed25519
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Public vectors only; exercises the classes and JNI library packaged in the APK. */
@RunWith(AndroidJUnit4::class)
class CryptoSecurityCompatibilityTest {
    @Test
    fun retainedSora2NativeDerivationAndSigningRemainCompatible() {
        val seed = hex("4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2")
        val expectedPublic = hex("66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972")
        val pair = Sr25519JNI.keypairFromSeed(seed)
        assertEquals(96, pair.size)
        val secret = pair.copyOfRange(0, 64)
        val publicKey = pair.copyOfRange(64, 96)
        assertArrayEquals(expectedPublic, publicKey)
        val message = "public Android crypto compatibility vector".toByteArray()
        val signature = Sr25519JNI.sign(publicKey, secret, message)
        assertTrue(Sr25519JNI.verify(signature, message, publicKey))
        assertFalse(Sr25519JNI.verify(signature, message + byteArrayOf(1), publicKey))
        assertArrayEquals(secret, Sr25519JNI.fromEd25519Bytes(Sr25519JNI.toEd25519Bytes(secret)))
    }

    @Test
    fun packagedEd25519UsesCurrentProviderAndRejectsMalleableSignatures() {
        assertTrue(BouncyCastleProvider().info.contains("1.86"))
        val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val expectedPublic = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val key = Ed25519.deriveFromSeed(seed)
        assertArrayEquals(expectedPublic, key.publicKey)
        val signature = Ed25519.sign(byteArrayOf(), key.privateKey)
        assertTrue(Ed25519.verify(byteArrayOf(), signature, key.publicKey))
        val order = java.math.BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)
        val scalar = java.math.BigInteger(1, signature.copyOfRange(32, 64).reversedArray())
        val malleable = signature.copyOfRange(0, 32) + scalar.add(order).toByteArray().reversedArray().copyOf(32)
        assertFalse(Ed25519.verify(byteArrayOf(), malleable, key.publicKey))
        assertFalse(Ed25519.verify(byteArrayOf(), ByteArray(64), ByteArray(32)))
    }

    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
