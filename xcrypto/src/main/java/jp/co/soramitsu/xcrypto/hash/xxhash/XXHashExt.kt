package jp.co.soramitsu.xcrypto.hash.xxhash

import java.nio.ByteBuffer
import java.nio.ByteOrder
import net.jpountz.xxhash.XXHash64
import net.jpountz.xxhash.XXHashFactory

val xxHash64: XXHash64 = XXHashFactory.safeInstance().hash64()
val xxHash128 = XXHash(128, xxHash64)
val xxHash256 = XXHash(256, xxHash64)

fun ByteArray.xxHash64() = xxHash64.hash(this)

fun ByteArray.xxHash64Concat() = xxHash64.hashConcat(this)
fun ByteArray.xxHash128() = xxHash128.hash(this)
fun ByteArray.xxHash256() = xxHash256.hash(this)

fun XXHash64.hash(bytes: ByteArray, seed: Long = 0) = hash(bytes, 0, bytes.size, seed)

fun XXHash64.hashConcat(bytes: ByteArray): ByteArray {
    val hashBytes = ByteBuffer.allocate(Long.SIZE_BYTES + bytes.size)
    hashBytes.order(ByteOrder.LITTLE_ENDIAN)

    hashBytes.putLong(hash(bytes))
    hashBytes.put(bytes)

    return hashBytes.array()
}
