package jp.co.soramitsu.xcrypto.encryption.xsalsa20poly1305

import jp.co.soramitsu.xcrypto.keygenerator.DecryptionKeyGenerationResult
import jp.co.soramitsu.xcrypto.keygenerator.EncryptionKeyGenerationResult
import jp.co.soramitsu.xcrypto.util.copyBytes

object XSalsa20Poly1305Cryptor {
    private const val nonceOffset = 0
    private const val nonceSize = 24

    private const val dataOffset = nonceOffset + nonceSize

    fun decrypt(keyGenerationResult: DecryptionKeyGenerationResult): ByteArray? {
        val byteData = keyGenerationResult.encryptedData

        val nonce = byteData.copyBytes(0, nonceSize)
        val encryptedData = byteData.copyOfRange(dataOffset, byteData.size)

        val secret = SecretBox(keyGenerationResult.secret).open(nonce, encryptedData)

        // SecretBox returns empty array if key is not correct
        return if (secret.isEmpty()) null else secret
    }

    fun encrypt(keyGenerationResult: EncryptionKeyGenerationResult, data: ByteArray): ByteArray {
        val secretBox = SecretBox(keyGenerationResult.encryptionKey)
        val nonce = secretBox.nonce(data)

        val secret = secretBox.seal(nonce, data)

        return keyGenerationResult.encryptionPrefix + nonce + secret
    }
}
