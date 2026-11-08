package jp.co.soramitsu.xcrypto.encryption.ecdsa

import java.math.BigInteger
import jp.co.soramitsu.xcrypto.util.toHexString
import org.bouncycastle.jce.ECNamedCurveTable
import org.web3j.crypto.Sign

object ECDSAUtils {

    fun compressedPublicKeyFromPrivate(privKey: BigInteger): ByteArray {
        val point = Sign.publicPointFromPrivate(privKey)
        return point.getEncoded(true)
    }

    @Suppress("MagicNumber")
    fun decompressedAsInt(compressedKey: ByteArray): BigInteger {
        val decompressedArray = decompressed(compressedKey)

        return (byteArrayOf(0x00) + decompressedArray).toHexString().toBigInteger(16)
    }

    fun decompressed(compressedKey: ByteArray): ByteArray {
        val spec = ECNamedCurveTable.getParameterSpec("secp256k1")
        val point = spec.curve.decodePoint(compressedKey)
        val x: ByteArray = point.xCoord.encoded
        val y: ByteArray = point.yCoord.encoded

        return x + y
    }
}

@Suppress("MagicNumber")
fun ECDSAUtils.derivePublicKey(privateKeyOrSeed: ByteArray): ByteArray {
    val privateKeyInt = BigInteger(privateKeyOrSeed.toHexString(), 16)

    return compressedPublicKeyFromPrivate(privateKeyInt)
}
