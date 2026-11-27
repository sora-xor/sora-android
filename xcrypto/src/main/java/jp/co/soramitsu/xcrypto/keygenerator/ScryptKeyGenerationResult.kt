package jp.co.soramitsu.xcrypto.keygenerator

data class EncryptionKeyGenerationResult(
    val encryptionPrefix: ByteArray,
    val encryptionKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptionKeyGenerationResult

        if (!encryptionPrefix.contentEquals(other.encryptionPrefix)) return false
        if (!encryptionKey.contentEquals(other.encryptionKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encryptionPrefix.contentHashCode()
        result = 31 * result + encryptionKey.contentHashCode()
        return result
    }
}

data class DecryptionKeyGenerationResult(
    val secret: ByteArray,
    val encryptedData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecryptionKeyGenerationResult

        if (!secret.contentEquals(other.secret)) return false
        return encryptedData.contentEquals(other.encryptedData)
    }

    override fun hashCode(): Int {
        var result = secret.contentHashCode()
        result = 31 * result + encryptedData.contentHashCode()
        return result
    }
}
