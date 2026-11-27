package jp.co.soramitsu.common.ethereum

import java.math.BigInteger

/**
 * Strict hex for Ethereum values. Unlike the lenient wallet readers, nothing is skipped: an
 * optional `0x`, then an even number of hex digits, or the value is refused.
 */
object EthHex {

    private val HEX = Regex("^(0x|0X)?[0-9a-fA-F]*$")

    fun decode(text: String): ByteArray {
        require(HEX.matches(text)) { "ETH_HEX_INVALID" }
        val digits = text.removePrefix("0x").removePrefix("0X")
        require(digits.length % 2 == 0) { "ETH_HEX_INVALID" }
        return ByteArray(digits.length / 2) { index ->
            ((Character.digit(digits[index * 2], 16) shl 4) or Character.digit(digits[index * 2 + 1], 16)).toByte()
        }
    }

    /** [decode], and the value must be exactly [size] bytes long. */
    fun decodeFixed(text: String, size: Int): ByteArray {
        val bytes = decode(text)
        require(bytes.size == size) { "ETH_HEX_LENGTH_INVALID" }
        return bytes
    }

    fun encode(bytes: ByteArray, withPrefix: Boolean = true): String {
        val out = StringBuilder(bytes.size * 2 + 2)
        if (withPrefix) out.append("0x")
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            out.append(DIGITS[value ushr 4]).append(DIGITS[value and 0x0f])
        }
        return out.toString()
    }

    /** A JSON-RPC quantity (`0x` + hex digits without leading zeros, `0x0` for zero). */
    fun quantity(value: BigInteger): String {
        require(value.signum() >= 0) { "ETH_QUANTITY_NEGATIVE" }
        return "0x" + value.toString(16)
    }

    /** Reads a JSON-RPC quantity. Leading zeros are tolerated; an empty `0x` is zero. */
    fun parseQuantity(text: String): BigInteger {
        require(text.startsWith("0x") || text.startsWith("0X")) { "ETH_QUANTITY_INVALID" }
        val digits = text.substring(2)
        require(digits.length <= 64 && digits.all { Character.digit(it, 16) >= 0 }) { "ETH_QUANTITY_INVALID" }
        return if (digits.isEmpty()) BigInteger.ZERO else BigInteger(digits, 16)
    }

    /** Unsigned big-endian bytes of [value], left-padded with zeros to [size]. */
    fun toFixed(value: BigInteger, size: Int): ByteArray {
        require(value.signum() >= 0) { "ETH_VALUE_NEGATIVE" }
        val raw = value.toByteArray()
        val stripped = if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(stripped.size <= size) { "ETH_VALUE_TOO_LARGE" }
        val out = ByteArray(size)
        stripped.copyInto(out, size - stripped.size)
        return out
    }

    private const val DIGITS = "0123456789abcdef"
}
