package jp.co.soramitsu.xcrypto.hash.blake2

import org.bouncycastle.jcajce.provider.digest.BCMessageDigest
import org.bouncycastle.jcajce.provider.digest.Blake2b

fun BCMessageDigest.hashConcat(bytes: ByteArray) = digest(bytes) + bytes

private val blake2bLock = Any()

private val blake2b256 = Blake2b.Blake2b256()

private val blake2b128 = Blake2b128()

private val blake2b512 = Blake2b.Blake2b512()

fun ByteArray.blake2b128(): ByteArray = withBlake2bLock { blake2b128.digest(this) }
fun ByteArray.blake2b256(): ByteArray = withBlake2bLock { blake2b256.digest(this) }
fun ByteArray.blake2b512(): ByteArray = withBlake2bLock { blake2b512.digest(this) }

fun ByteArray.blake2b128Concat() = withBlake2bLock { blake2b128.hashConcat(this) }

private inline fun <T : Any> withBlake2bLock(action: () -> T) = synchronized(blake2bLock, action)
