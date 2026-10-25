package jp.co.soramitsu.common.util

import com.google.gson.JsonParser
import java.math.BigInteger
import jp.co.soramitsu.xcrypto.encryption.ed25519.Ed25519
import jp.co.soramitsu.xcrypto.encryption.xsalsa20poly1305.XSalsa20Poly1305Cryptor
import jp.co.soramitsu.xcrypto.keygenerator.ScryptKeyGenerator
import org.junit.Assert.*
import org.junit.Test

class XcryptoSecurityCompatibilityTest {
    @Test
    fun `RFC8032 key derivation and deterministic signature remain exact`() {
        val key = Ed25519.deriveFromSeed(seed)
        assertArrayEquals(seed, key.seed)
        assertArrayEquals(seed, key.privateKey)
        assertArrayEquals(publicKey, key.publicKey)
        assertArrayEquals(signature, Ed25519.sign(byteArrayOf(), key.privateKey))
        assertTrue(Ed25519.verify(byteArrayOf(), signature, key.publicKey))
    }

    @Test
    fun `sixteen retained old SDK seeds keys and signatures remain byte identical`() {
        fixture.getAsJsonArray("vectors").forEach { element ->
            val vector = element.asJsonObject
            val key = Ed25519.deriveFromSeed(hex(vector["seed"].asString))
            val message = hex(vector["message"].asString)
            val signed = hex(vector["signature"].asString)
            assertArrayEquals(hex(vector["privateKey"].asString), key.privateKey)
            assertArrayEquals(hex(vector["publicKey"].asString), key.publicKey)
            assertArrayEquals(signed, Ed25519.sign(message, key.privateKey))
            assertTrue(Ed25519.verify(message, signed, key.publicKey))
        }
    }

    @Test
    fun `signature scalar malleability is rejected`() {
        val scalar = BigInteger(1, signature.copyOfRange(32, 64).reversedArray())
        val nonCanonical = littleEndian32(scalar.add(order))
        assertFalse(Ed25519.verify(byteArrayOf(), signature.copyOfRange(0, 32) + nonCanonical, publicKey))
        assertFalse(Ed25519.verify(byteArrayOf(), signature.copyOfRange(0, 32) + littleEndian32(order), publicKey))
        assertFalse(Ed25519.verify(byteArrayOf(), signature.copyOfRange(0, 32) + ByteArray(32) { -1 }, publicKey))
    }

    @Test
    fun `malformed noncanonical and small order points fail closed`() {
        val identity = ByteArray(32).also { it[0] = 1 }
        val nonCanonicalPoint = hex("edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f")
        assertFalse(Ed25519.verify(byteArrayOf(), identity + ByteArray(32), identity))
        assertFalse(Ed25519.verify(byteArrayOf(), ByteArray(64), ByteArray(32)))
        assertFalse(Ed25519.verify(byteArrayOf(), signature, nonCanonicalPoint))
        assertFalse(Ed25519.verify(byteArrayOf(), nonCanonicalPoint + signature.copyOfRange(32, 64), publicKey))
        assertFalse(Ed25519.verify(byteArrayOf(1), signature, publicKey))
        assertFalse(Ed25519.verify(byteArrayOf(), signature.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }, publicKey))
        for (size in listOf(0, 31, 63, 65, 128)) assertFalse(Ed25519.verify(byteArrayOf(), signature.copyOf(size), publicKey))
        for (size in listOf(0, 31, 33, 64)) assertFalse(Ed25519.verify(byteArrayOf(), signature, publicKey.copyOf(size)))
    }

    @Test
    fun `invalid seed and private key sizes are rejected`() {
        for (size in listOf(0, 31, 33, 64)) {
            assertThrows(IllegalArgumentException::class.java) { Ed25519.deriveFromSeed(ByteArray(size)) }
            assertThrows(IllegalArgumentException::class.java) { Ed25519.sign(byteArrayOf(), ByteArray(size)) }
        }
    }

    @Test
    fun `retained old encrypted backup decrypts and tampering is rejected`() {
        val backup = fixture.getAsJsonObject("backup")
        val encrypted = hex(backup["encrypted"].asString)
        val password = hex(backup["password"].asString)
        val decrypted = XSalsa20Poly1305Cryptor.decrypt(ScryptKeyGenerator.generate(encrypted, password))
        assertArrayEquals(hex(backup["plaintext"].asString), decrypted)
        val tampered = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertNull(XSalsa20Poly1305Cryptor.decrypt(ScryptKeyGenerator.generate(tampered, password)))
        assertNull(XSalsa20Poly1305Cryptor.decrypt(ScryptKeyGenerator.generate(encrypted, "wrong-public-password".toByteArray())))
    }

    @Test
    fun `new encrypted backup retains the old envelope layout and round trips`() {
        val backup = fixture.getAsJsonObject("backup")
        val password = hex(backup["password"].asString)
        val plaintext = hex(backup["plaintext"].asString)
        val encrypted = XSalsa20Poly1305Cryptor.encrypt(ScryptKeyGenerator.generate(password), plaintext)
        // 32-byte salt, three LE cost parameters, 24-byte nonce, 16-byte authenticator.
        assertEquals(32 + 12 + 24 + 16 + plaintext.size, encrypted.size)
        assertArrayEquals(plaintext, XSalsa20Poly1305Cryptor.decrypt(ScryptKeyGenerator.generate(encrypted, password)))
    }

    private val fixture = javaClass.classLoader!!.getResourceAsStream("legacy-xcrypto-v1.json")!!.bufferedReader().use {
        JsonParser.parseReader(it).asJsonObject
    }
    private val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
    private val publicKey = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
    private val signature = hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
    private val order = BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun littleEndian32(value: BigInteger) = value.toByteArray().reversedArray().copyOf(32)
}
