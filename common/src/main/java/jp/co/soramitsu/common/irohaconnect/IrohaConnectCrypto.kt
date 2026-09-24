package jp.co.soramitsu.common.irohaconnect

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object IrohaConnectEncoding {
    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun base64Decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun base64UrlDecode(value: String, expectedSize: Int? = null): ByteArray {
        require(value.isNotEmpty() && '=' !in value && value.matches(Regex("^[A-Za-z0-9_-]+$")))
        val decoded = Base64.getUrlDecoder().decode(value)
        require(base64Url(decoded) == value) { "NON_CANONICAL_BASE64URL" }
        if (expectedSize != null) require(decoded.size == expectedSize)
        return decoded
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun hexDecode(value: String): ByteArray {
        val normalized = value.removePrefix("0x").removePrefix("0X")
        require(normalized.length % 2 == 0 && normalized.matches(Regex("^[0-9a-fA-F]+$")))
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun percentEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    fun littleEndianU16(value: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()

    fun littleEndianU32(value: Long): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()

    fun littleEndianU64(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
}

data class IrohaConnectEphemeralKeys(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    fun destroy() = privateKey.fill(0)
}

data class IrohaConnectDirectionKeys(
    val appToWallet: ByteArray,
    val walletToApp: ByteArray,
) {
    fun destroy() {
        appToWallet.fill(0)
        walletToApp.fill(0)
    }
}

object IrohaConnectCrypto {
    private val random = SecureRandom()
    private val x25519Salt = "iroha:x25519:hkdf:v1".toByteArray()
    private val x25519Info = "iroha:x25519:session-key".toByteArray()
    private val sessionSaltPrefix = "iroha-connect|salt|".toByteArray()
    private val appInfo = "iroha-connect|k_app".toByteArray()
    private val walletInfo = "iroha-connect|k_wallet".toByteArray()

    fun blake2b256(input: ByteArray): ByteArray {
        val digest = Blake2bDigest(256)
        digest.update(input, 0, input.size)
        return ByteArray(32).also { digest.doFinal(it, 0) }
    }

    fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean = MessageDigest.isEqual(left, right)

    fun ephemeralX25519(): IrohaConnectEphemeralKeys {
        val privateKey = X25519PrivateKeyParameters(random)
        return IrohaConnectEphemeralKeys(
            privateKey = privateKey.encoded,
            publicKey = privateKey.generatePublicKey().encoded,
        )
    }

    fun deriveDirectionKeys(
        sid: ByteArray,
        appPublicKey: ByteArray,
        walletPrivateKey: ByteArray,
    ): IrohaConnectDirectionKeys {
        require(sid.size == 32 && appPublicKey.size == 32 && walletPrivateKey.size == 32)
        val sharedSecret = ByteArray(32)
        return try {
            try {
                X25519PrivateKeyParameters(walletPrivateKey, 0).generateSecret(
                    X25519PublicKeyParameters(appPublicKey, 0),
                    sharedSecret,
                    0,
                )
            } catch (error: RuntimeException) {
                throw IllegalArgumentException("Invalid IrohaConnect X25519 peer key", error)
            }
            require(sharedSecret.any { it != 0.toByte() }) { "Invalid IrohaConnect X25519 peer key" }
            val sessionKey = hkdf(sharedSecret, x25519Salt, x25519Info)
            try {
                val salt = blake2b256(sessionSaltPrefix + sid)
                try {
                    IrohaConnectDirectionKeys(
                        appToWallet = hkdf(sessionKey, salt, appInfo),
                        walletToApp = hkdf(sessionKey, salt, walletInfo),
                    )
                } finally {
                    salt.fill(0)
                }
            } finally {
                sessionKey.fill(0)
            }
        } finally {
            sharedSecret.fill(0)
        }
    }

    fun signEd25519(privateKeySeed: ByteArray, message: ByteArray): ByteArray {
        require(privateKeySeed.size == 32)
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKeySeed, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun publicEd25519(privateKeySeed: ByteArray): ByteArray {
        require(privateKeySeed.size == 32)
        return Ed25519PrivateKeyParameters(privateKeySeed, 0).generatePublicKey().encoded
    }

    fun verifyEd25519(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        return runCatching {
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature)
        }.getOrDefault(false)
    }

    fun encrypt(
        key: ByteArray,
        sequence: Long,
        associatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = transform(true, key, sequence, associatedData, plaintext)

    fun decrypt(
        key: ByteArray,
        sequence: Long,
        associatedData: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray = transform(false, key, sequence, associatedData, ciphertext)

    private fun transform(
        encrypt: Boolean,
        key: ByteArray,
        sequence: Long,
        associatedData: ByteArray,
        input: ByteArray,
    ): ByteArray {
        require(key.size == 32 && sequence >= 0)
        val nonce = ByteArray(12)
        IrohaConnectEncoding.littleEndianU64(sequence).copyInto(nonce, 4)
        return try {
            val cipher = ChaCha20Poly1305()
            cipher.init(encrypt, AEADParameters(KeyParameter(key), 128, nonce, associatedData))
            val output = ByteArray(cipher.getOutputSize(input.size))
            var length = cipher.processBytes(input, 0, input.size, output, 0)
            length += cipher.doFinal(output, length)
            output.copyOf(length).also { output.fill(0) }
        } finally {
            nonce.fill(0)
        }
    }

    private fun hkdf(input: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(input, salt, info))
        return ByteArray(32).also { generator.generateBytes(it, 0, it.size) }
    }
}
