package jp.co.soramitsu.xcrypto.hash.xxhash

import java.nio.ByteBuffer
import java.nio.ByteOrder
import net.jpountz.xxhash.XXHash64

class XXHash(
    hashLengthBits: Int,
    private val xxHash64: XXHash64
) {
    private val xxhash64InitialLength = 64
    init {
        require(hashLengthBits % xxhash64InitialLength == 0)
    }

    private val hashLengthBytes = hashLengthBits / Byte.SIZE_BITS

    private val timesToRepeat = hashLengthBits / xxhash64InitialLength

    fun hash(byteArray: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(hashLengthBytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        (0 until timesToRepeat).map {
            xxHash64.hash(byteArray, seed = it.toLong())
        }.onEach(buffer::putLong)

        return buffer.array()
    }
}
