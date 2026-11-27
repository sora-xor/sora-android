package jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.cryptor

import jp.co.soramitsu.xcrypto.encryption.xsalsa20poly1305.XSalsa20Poly1305Cryptor
import jp.co.soramitsu.xcrypto.keygenerator.DecryptionKeyGenerationResult
import jp.co.soramitsu.xcrypto.keygenerator.EncryptionKeyGenerationResult
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.JsonCryptor
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.JsonTypeDecoder
import jp.co.soramitsu.xsubstrate.encrypt.json.coders.type.JsonTypeEncoder

object XSalsa20Poly1305Cryptor : JsonCryptor {
    override fun decrypt(keyGenerationResult: JsonTypeDecoder.KeyGenerationResult): ByteArray? {
        return XSalsa20Poly1305Cryptor.decrypt(
            DecryptionKeyGenerationResult(
                keyGenerationResult.secret,
                keyGenerationResult.encryptedData,
            ),
        )
    }

    override fun encrypt(
        keyGenerationResult: JsonTypeEncoder.KeyGenerationResult,
        data: ByteArray,
    ): ByteArray {
        return XSalsa20Poly1305Cryptor.encrypt(
            EncryptionKeyGenerationResult(keyGenerationResult.encryptingPrefix, keyGenerationResult.encryptionKey),
            data,
        )
    }
}
