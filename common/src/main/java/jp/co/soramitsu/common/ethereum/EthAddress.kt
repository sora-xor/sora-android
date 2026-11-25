package jp.co.soramitsu.common.ethereum

import jp.co.soramitsu.xcrypto.hash.keccak256

/** Ethereum addresses: 20 bytes, shown with the EIP-55 mixed-case checksum. */
object EthAddress {

    const val SIZE = 20

    val ZERO: ByteArray get() = ByteArray(SIZE)

    private val SHAPE = Regex("^0x[0-9a-fA-F]{40}$")

    /** Last 20 bytes of keccak256 of the 64-byte public key (an uncompressed key without 0x04). */
    fun fromPublicKey(uncompressed: ByteArray): ByteArray {
        val body = when {
            uncompressed.size == 65 && uncompressed[0] == 0x04.toByte() -> uncompressed.copyOfRange(1, 65)
            uncompressed.size == 64 -> uncompressed
            else -> throw IllegalArgumentException("ETH_PUBLIC_KEY_INVALID")
        }
        return body.keccak256().copyOfRange(12, 32)
    }

    /** EIP-55: a hex letter is upper case where the matching nibble of keccak(lower hex) is ≥ 8. */
    fun checksum(address: ByteArray): String {
        require(address.size == SIZE) { "ETH_ADDRESS_LENGTH_INVALID" }
        val lower = EthHex.encode(address, withPrefix = false)
        val hash = lower.toByteArray(Charsets.US_ASCII).keccak256()
        val out = StringBuilder("0x")
        lower.forEachIndexed { index, char ->
            val nibble = (hash[index / 2].toInt() shr (if (index % 2 == 0) 4 else 0)) and 0x0f
            out.append(if (char.isLetter() && nibble >= 8) char.uppercaseChar() else char)
        }
        return out.toString()
    }

    fun checksum(address: String): String = checksum(requireNotNull(parse(address)) { "ETH_ADDRESS_INVALID" })

    /**
     * Reads an address typed or pasted by a person. Accepted: `0x` and 40 hex digits that are all
     * lower case, all upper case, or carry a correct EIP-55 checksum. A mixed-case address with a
     * wrong checksum is refused: it is a typo the checksum was made to catch.
     */
    fun parse(input: String): ByteArray? {
        val text = input.trim()
        if (!SHAPE.matches(text)) return null
        val digits = text.substring(2)
        val bytes = EthHex.decode(digits)
        val allLower = digits == digits.lowercase()
        val allUpper = digits == digits.uppercase()
        if (allLower || allUpper) return bytes
        return if (checksum(bytes) == text) bytes else null
    }

    fun isValid(input: String): Boolean = parse(input) != null

    fun equal(left: ByteArray, right: ByteArray): Boolean =
        left.size == SIZE && right.size == SIZE && left.contentEquals(right)
}
