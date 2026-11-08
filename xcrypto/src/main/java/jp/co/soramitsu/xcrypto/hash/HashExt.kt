package jp.co.soramitsu.xcrypto.hash

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.jcajce.provider.digest.Keccak

fun ByteArray.keccak256(): ByteArray {
    val digest = Keccak.Digest256()

    return digest.digest(this)
}

fun ByteArray.md5(): ByteArray {
    val hasher = MessageDigest.getInstance("MD5")

    return hasher.digest(this)
}

fun ByteArray.hmacSHA256(secret: ByteArray) = hmac(secret, "HmacSHA256")
fun ByteArray.hmacSHA512(secret: ByteArray) = hmac(secret, "HmacSHA512")

private fun ByteArray.hmac(secret: ByteArray, shaAlgorithm: String): ByteArray {
    val chiper: Mac = Mac.getInstance(shaAlgorithm)
    val secretKeySpec = SecretKeySpec(secret, shaAlgorithm)
    chiper.init(secretKeySpec)

    return chiper.doFinal(this)
}
