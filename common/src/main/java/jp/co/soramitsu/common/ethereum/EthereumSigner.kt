package jp.co.soramitsu.common.ethereum

import java.math.BigInteger
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.math.ec.ECAlgorithms
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.FixedPointCombMultiplier

/** The secp256k1 curve as Ethereum uses it. */
object Secp256k1 {

    private val parameters: X9ECParameters = CustomNamedCurves.getByName("secp256k1")

    val domain = ECDomainParameters(parameters.curve, parameters.g, parameters.n, parameters.h)

    val order: BigInteger get() = domain.n

    private val halfOrder: BigInteger = domain.n.shiftRight(1)

    fun isValidPrivateKey(privateKey: ByteArray): Boolean {
        if (privateKey.size != 32) return false
        val d = BigInteger(1, privateKey)
        return d.signum() > 0 && d < order
    }

    fun publicPoint(privateKey: ByteArray): ECPoint {
        require(isValidPrivateKey(privateKey)) { "SECP256K1_PRIVATE_KEY_INVALID" }
        return FixedPointCombMultiplier().multiply(domain.g, BigInteger(1, privateKey)).normalize()
    }

    /** `0x04 ‖ X ‖ Y`, 65 bytes. */
    fun publicKeyUncompressed(privateKey: ByteArray): ByteArray = publicPoint(privateKey).getEncoded(false)

    /** `0x02|0x03 ‖ X`, 33 bytes. */
    fun publicKeyCompressed(privateKey: ByteArray): ByteArray = publicPoint(privateKey).getEncoded(true)

    internal fun isLowS(s: BigInteger): Boolean = s <= halfOrder

    internal fun normalizeS(s: BigInteger): BigInteger = if (isLowS(s)) s else order - s

    internal fun decodePoint(encoded: ByteArray): ECPoint = domain.curve.decodePoint(encoded)
}

/** An Ethereum ECDSA signature: 32-byte r and s (s in the lower half of the order) and yParity. */
class EthSignature(r: ByteArray, s: ByteArray, val yParity: Int) {
    val r: ByteArray = r.copyOf()
    val s: ByteArray = s.copyOf()

    init {
        require(r.size == 32 && s.size == 32) { "ETH_SIGNATURE_LENGTH_INVALID" }
        require(yParity == 0 || yParity == 1) { "ETH_SIGNATURE_PARITY_INVALID" }
    }

    val rValue: BigInteger get() = BigInteger(1, r)
    val sValue: BigInteger get() = BigInteger(1, s)
}

/**
 * secp256k1 signing for Ethereum transactions: deterministic nonces (RFC 6979 with HMAC-SHA256,
 * BouncyCastle's [HMacDSAKCalculator]), `s` normalised to the lower half of the order (EIP-2) and
 * the recovery parity found by recovering the public key, never guessed. The same inputs always
 * give the same signature, which is what lets the golden vectors pin it byte for byte.
 */
object EthereumSigner {

    fun sign(hash: ByteArray, privateKey: ByteArray): EthSignature {
        require(hash.size == 32) { "ETH_SIGNING_HASH_INVALID" }
        require(Secp256k1.isValidPrivateKey(privateKey)) { "SECP256K1_PRIVATE_KEY_INVALID" }
        val d = BigInteger(1, privateKey)
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(d, Secp256k1.domain))
        val components = signer.generateSignature(hash)
        val r = components[0]
        val s = Secp256k1.normalizeS(components[1])
        val expected = Secp256k1.publicPoint(privateKey)
        val parity = (0..1).firstOrNull { candidate ->
            recoverPoint(hash, r, s, candidate)?.equals(expected) == true
        } ?: error("ETH_SIGNATURE_RECOVERY_FAILED")
        return EthSignature(EthHex.toFixed(r, 32), EthHex.toFixed(s, 32), parity)
    }

    /** The uncompressed public key (65 bytes) that made [signature] over [hash], or null. */
    fun recoverPublicKey(hash: ByteArray, signature: EthSignature): ByteArray? =
        recoverPoint(hash, signature.rValue, signature.sValue, signature.yParity)?.getEncoded(false)

    /** SEC 1 v2 §4.1.6 for the two parities whose x coordinate is r itself. */
    private fun recoverPoint(hash: ByteArray, r: BigInteger, s: BigInteger, parity: Int): ECPoint? {
        val n = Secp256k1.order
        if (r.signum() <= 0 || r >= n || s.signum() <= 0 || s >= n) return null
        val prime = Secp256k1.domain.curve.field.characteristic
        if (r >= prime) return null
        val encoded = ByteArray(33)
        encoded[0] = if (parity == 1) 0x03 else 0x02
        EthHex.toFixed(r, 32).copyInto(encoded, 1)
        val point = try {
            Secp256k1.decodePoint(encoded)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (!point.multiply(n).isInfinity) return null
        val e = BigInteger(1, hash)
        val rInverse = r.modInverse(n)
        val eFactor = e.negate().mod(n).multiply(rInverse).mod(n)
        val sFactor = s.multiply(rInverse).mod(n)
        val q = ECAlgorithms.sumOfTwoMultiplies(Secp256k1.domain.g, eFactor, point, sFactor).normalize()
        return if (q.isInfinity) null else q
    }
}
