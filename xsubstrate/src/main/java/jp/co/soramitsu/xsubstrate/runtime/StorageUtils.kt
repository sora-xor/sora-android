package jp.co.soramitsu.xsubstrate.runtime

import jp.co.soramitsu.xcrypto.hash.blake2.blake2b128Concat
import jp.co.soramitsu.xcrypto.hash.xxhash.xxHash128
import jp.co.soramitsu.xcrypto.hash.xxhash.xxHash64Concat
import jp.co.soramitsu.xcrypto.util.toHexString

typealias HashFunction = (ByteArray) -> ByteArray

enum class IdentifierHasher(val hasher: HashFunction) {
    Blake2b128concat({ it.blake2b128Concat() }),
    TwoX64Concat({ it.xxHash64Concat() }),
}

class Identifier(
    value: ByteArray,
    identifierHasher: IdentifierHasher,
) {
    val id = identifierHasher.hasher(value)
}

object StorageUtils {
    fun createStorageKey(
        service: Service<*>,
        identifier: Identifier?,
    ): String {
        val moduleNameBytes = service.module.id.toByteArray()
        val serviceNameBytes = service.id.toByteArray()

        var keyBytes = moduleNameBytes.xxHash128() + serviceNameBytes.xxHash128()

        identifier?.let { keyBytes += it.id }

        return keyBytes.toHexString(withPrefix = true)
    }
}
