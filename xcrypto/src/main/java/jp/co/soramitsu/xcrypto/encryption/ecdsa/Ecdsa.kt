package jp.co.soramitsu.xcrypto.encryption.ecdsa

import java.math.BigInteger
import java.security.Security
import jp.co.soramitsu.xcrypto.encryption.KeypairWithSeed
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign

object Ecdsa {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    fun deriveFromSeed(seed: ByteArray): KeypairWithSeed {
        return KeypairWithSeed(
            seed = seed,
            privateKey = seed,
            publicKey = ECDSAUtils.derivePublicKey(privateKeyOrSeed = seed)
        )
    }

    @Suppress("MagicNumber")
    fun signer(
        message: ByteArray,
        privateKeyBytes: ByteArray,
        hasher: (ByteArray) -> ByteArray
    ): EcdsaSignature {
        val privateKey = BigInteger(Hex.toHexString(privateKeyBytes), 16)
        val publicKey = Sign.publicKeyFromPrivate(privateKey)

        val messageHash = hasher(message)

        val sign = Sign.signMessage(messageHash, ECKeyPair(privateKey, publicKey), false)

        return EcdsaSignature(v = sign.v, r = sign.r, s = sign.s)
    }

    data class EcdsaSignature(val v: ByteArray, val r: ByteArray, val s: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EcdsaSignature

            if (!v.contentEquals(other.v)) return false
            if (!r.contentEquals(other.r)) return false
            if (!s.contentEquals(other.s)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = v.contentHashCode()
            result = 31 * result + r.contentHashCode()
            result = 31 * result + s.contentHashCode()
            return result
        }
    }
}
