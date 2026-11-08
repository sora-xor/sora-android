package jp.co.soramitsu.xcrypto.keygenerator

import java.security.SecureRandom
import java.util.Random
import jp.co.soramitsu.xcrypto.util.asLittleEndianBytes
import jp.co.soramitsu.xcrypto.util.asLittleEndianInt
import jp.co.soramitsu.xcrypto.util.copyBytes
import kotlin.math.pow
import org.bouncycastle.crypto.generators.SCrypt

object ScryptKeyGenerator {

    private const val SaltOffset = 0
    private const val SaltSize = 32

    private const val NOffset = SaltOffset + SaltSize
    private const val NSize = 4

    private const val POffset = NOffset + NSize
    private const val PSize = 4

    private const val ROffset = POffset + PSize
    private const val RSize = 4

    private val N = 2.0.pow(15.0).toInt()
    private const val P = 1
    private const val R = 8

    private const val ScryptKeySize = 32
    private val random: Random = SecureRandom()
    fun generate(encrypted: ByteArray, password: ByteArray): DecryptionKeyGenerationResult {
        val salt = encrypted.copyBytes(SaltOffset, SaltSize)
        val N = encrypted.copyBytes(NOffset, NSize).asLittleEndianInt()
        val p = encrypted.copyBytes(POffset, PSize).asLittleEndianInt()
        val r = encrypted.copyBytes(ROffset, RSize).asLittleEndianInt()

        val encryptionSecret = SCrypt.generate(password, salt, N, r, p, ScryptKeySize)

        return DecryptionKeyGenerationResult(
            secret = encryptionSecret,
            encryptedData = encrypted.copyOfRange(ROffset + RSize, encrypted.size)
        )
    }

    fun generate(password: ByteArray): EncryptionKeyGenerationResult {
        val salt = generateSalt()

        val encryptionKey = SCrypt.generate(password, salt, N, R, P, ScryptKeySize)

        return EncryptionKeyGenerationResult(
            encryptionPrefix = salt + N.asLittleEndianBytes() + P.asLittleEndianBytes() +
                R.asLittleEndianBytes(),
            encryptionKey = encryptionKey
        )
    }

    private fun generateSalt(): ByteArray {
        val bytes = ByteArray(SaltSize)

        random.nextBytes(bytes)

        return bytes
    }
}
