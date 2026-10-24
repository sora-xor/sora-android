package jp.co.soramitsu.feature_account_impl.domain.keys

import java.math.BigInteger

/**
 * The sr25519 public key of a stored private key, computed directly from the stored scalar.
 *
 * Every release stored the private key exactly as the native library returned it
 * (`schnorrkel::Keypair::to_bytes`): 32 bytes, the secret scalar in canonical little-endian form
 * (below the group order). Its public key is that scalar times the Ristretto base point, in the
 * compressed Ristretto encoding (RFC 9496, section 4.3.2). The packaged native library has no
 * call for this (it only derives a public key from a 32-byte *seed*, which hashes its input), so
 * the multiplication is done here. Nothing is fed into a seed factory.
 *
 * This is used only to judge stored keys: it reads a private key and returns the public key it
 * belongs to. It never signs, and nothing it computes is stored. The check runs before any
 * native call, so the native library only ever receives a scalar and a public key that are
 * well-formed and belong together.
 *
 * `BigInteger` arithmetic is not constant-time and its intermediate values cannot be wiped. The
 * same private key is already held as a hex string by the preference layer on every read, so
 * this adds no new exposure inside the app's own process.
 */
object Sr25519PublicKeyDerivation {
    const val KEY_BYTES = 32

    private val ZERO = BigInteger.ZERO
    private val ONE = BigInteger.ONE
    private val TWO = BigInteger.valueOf(2)

    /** The field prime 2^255 - 19. */
    private val P = ONE.shiftLeft(255) - BigInteger.valueOf(19)

    /** The order of the Ristretto group, 2^252 + 27742317777372353535851937790883648493. */
    private val GROUP_ORDER =
        ONE.shiftLeft(252) + BigInteger("27742317777372353535851937790883648493")

    /** Edwards curve constant d = -121665 / 121666. */
    private val D = (BigInteger.valueOf(-121665) * BigInteger.valueOf(121666).modInverse(P)).mod(P)
    private val D2 = (D * TWO).mod(P)

    /** A square root of -1: 2^((p - 1) / 4). */
    private val SQRT_M1 = TWO.modPow((P - ONE).shiftRight(2), P)

    /** 1 / sqrt(a - d) with a = -1 (RFC 9496 `INVSQRT_A_MINUS_D`). */
    private val INVSQRT_A_MINUS_D = inverseSquareRoot((P - ONE - D).mod(P))

    /** The Ed25519 base point: y = 4/5, x the non-negative root. Extended coordinates. */
    private val BASE: Point = run {
        val y = (BigInteger.valueOf(4) * BigInteger.valueOf(5).modInverse(P)).mod(P)
        val y2 = (y * y).mod(P)
        // x^2 = (y^2 - 1) / (d y^2 + 1)
        val x = sqrtRatio((y2 - ONE).mod(P), (D * y2 + ONE).mod(P))
        Point(x, y, ONE, (x * y).mod(P))
    }
    private val IDENTITY = Point(ZERO, ONE, ONE, ZERO)

    private class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    /**
     * True when [privateKey] is a well-formed stored sr25519 private key: exactly 32 bytes that
     * encode a non-zero scalar below the group order. Anything else was never a key the released
     * apps could sign with (their native library rejects it).
     */
    fun isCanonicalScalar(privateKey: ByteArray): Boolean {
        if (privateKey.size != KEY_BYTES) return false
        val scalar = scalarOf(privateKey)
        return scalar.signum() > 0 && scalar < GROUP_ORDER
    }

    /**
     * The 32-byte public key that belongs to [privateKey], or null when [privateKey] is not a
     * well-formed private key (see [isCanonicalScalar]).
     */
    fun publicKeyOrNull(privateKey: ByteArray): ByteArray? {
        if (privateKey.size != KEY_BYTES) return null
        val scalar = scalarOf(privateKey)
        if (scalar.signum() <= 0 || scalar >= GROUP_ORDER) return null
        // The group order is below 2^253. Double and add, doing the addition on every step.
        var result = IDENTITY
        for (bit in GROUP_ORDER.bitLength() - 1 downTo 0) {
            result = add(result, result)
            val withBase = add(result, BASE)
            if (scalar.testBit(bit)) result = withBase
        }
        return encode(result)
    }

    private fun scalarOf(littleEndian: ByteArray): BigInteger {
        val bigEndian = littleEndian.reversedArray()
        return try {
            BigInteger(1, bigEndian)
        } finally {
            bigEndian.fill(0)
        }
    }

    /**
     * Unified addition on the twisted Edwards curve -x^2 + y^2 = 1 + d x^2 y^2 in extended
     * coordinates. The formula is complete for this curve, so it also doubles.
     */
    private fun add(p: Point, q: Point): Point {
        val a = ((p.y - p.x) * (q.y - q.x)).mod(P)
        val b = ((p.y + p.x) * (q.y + q.x)).mod(P)
        val c = (p.t * D2).mod(P).multiply(q.t).mod(P)
        val d = (p.z * TWO).mod(P).multiply(q.z).mod(P)
        val e = (b - a).mod(P)
        val f = (d - c).mod(P)
        val g = (d + c).mod(P)
        val h = (b + a).mod(P)
        return Point((e * f).mod(P), (g * h).mod(P), (f * g).mod(P), (e * h).mod(P))
    }

    /** Ristretto255 encoding of a point (RFC 9496, section 4.3.2). */
    private fun encode(point: Point): ByteArray {
        val u1 = ((point.z + point.y) * (point.z - point.y)).mod(P)
        val u2 = (point.x * point.y).mod(P)
        val invsqrt = inverseSquareRoot((u1 * u2).mod(P).multiply(u2).mod(P))
        val den1 = (invsqrt * u1).mod(P)
        val den2 = (invsqrt * u2).mod(P)
        val zInv = (den1 * den2).mod(P).multiply(point.t).mod(P)
        val rotate = isNegative((point.t * zInv).mod(P))
        val x = if (rotate) (point.y * SQRT_M1).mod(P) else point.x
        var y = if (rotate) (point.x * SQRT_M1).mod(P) else point.y
        val denInv = if (rotate) (den1 * INVSQRT_A_MINUS_D).mod(P) else den2
        if (isNegative((x * zInv).mod(P))) y = (P - y).mod(P)
        val s = absolute((denInv * (point.z - y)).mod(P))
        return littleEndian(s)
    }

    /** The non-negative square root of 1 / [v] (RFC 9496 `SQRT_RATIO_M1(1, v)`). */
    private fun inverseSquareRoot(v: BigInteger): BigInteger = sqrtRatio(ONE, v)

    /**
     * The non-negative square root of [u] / [v] when it is a square (RFC 9496 `SQRT_RATIO_M1`).
     * Every call in this file has a square ratio.
     */
    private fun sqrtRatio(u: BigInteger, v: BigInteger): BigInteger {
        val v3 = (v * v).mod(P).multiply(v).mod(P)
        val v7 = (v3 * v3).mod(P).multiply(v).mod(P)
        var r = (u * v3).mod(P)
            .multiply((u * v7).mod(P).modPow((P - BigInteger.valueOf(5)).shiftRight(3), P))
            .mod(P)
        val check = (v * r).mod(P).multiply(r).mod(P)
        val negU = (P - u).mod(P)
        val flipped = check == negU || check == (negU * SQRT_M1).mod(P)
        if (flipped) r = (r * SQRT_M1).mod(P)
        return absolute(r)
    }

    /** A field element is "negative" when its canonical encoding has the lowest bit set. */
    private fun isNegative(value: BigInteger): Boolean = value.testBit(0)

    private fun absolute(value: BigInteger): BigInteger =
        if (isNegative(value)) (P - value).mod(P) else value

    private fun littleEndian(value: BigInteger): ByteArray {
        val out = ByteArray(KEY_BYTES)
        val bigEndian = value.toByteArray()
        for (index in bigEndian.indices) {
            val target = bigEndian.size - 1 - index
            if (target < KEY_BYTES) out[target] = bigEndian[index]
        }
        return out
    }
}
