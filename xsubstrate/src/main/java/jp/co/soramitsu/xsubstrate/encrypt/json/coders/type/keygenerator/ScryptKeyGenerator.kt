package jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.keygenerator

import jp.co.soramitsu.xcrypto.keygenerator.ScryptKeyGenerator
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.JsonEncryptionKeyGenerator
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.JsonTypeDecoder
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.JsonTypeEncoder

object ScryptKeyGenerator : JsonEncryptionKeyGenerator {
    override fun generate(
        encrypted: ByteArray,
        password: ByteArray,
    ): JsonTypeDecoder.KeyGenerationResult {
        val result = ScryptKeyGenerator.generate(encrypted, password)
        return JsonTypeDecoder.KeyGenerationResult(
            encryptedData = result.encryptedData,
            secret = result.secret,
        )
    }

    override fun generate(password: ByteArray): JsonTypeEncoder.KeyGenerationResult {
        val result = ScryptKeyGenerator.generate(password)
        return JsonTypeEncoder.KeyGenerationResult(
            encryptingPrefix = result.encryptionPrefix,
            encryptionKey = result.encryptionKey,
        )
    }
}
