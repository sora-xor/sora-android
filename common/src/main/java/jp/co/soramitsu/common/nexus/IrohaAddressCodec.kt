package jp.co.soramitsu.common.nexus

/**
 * Canonical single-key Ed25519 I105 codec. It accepts only canonical literals and can therefore
 * be used as the send-screen network boundary.
 */
object IrohaAddressCodec {
    private const val MAX_DISCRIMINANT = 0x3fff
    private const val CHECKSUM_LENGTH = 6
    private const val BASE = 105
    private const val MAX_ADDRESS_CHARACTERS = 160
    private const val MAX_ADDRESS_BYTES = 512
    private const val BECH32M_CONST = 0x2bc830a3
    private const val HRP = "snx"
    private const val ED25519_KEY_LENGTH = 32
    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val poem = listOf(
        '\uff72', '\uff9b', '\uff8a', '\uff86', '\uff8e', '\uff8d', '\uff84', '\uff81',
        '\uff98', '\uff87', '\uff99', '\uff66', '\uff9c', '\uff76', '\uff96', '\uff80',
        '\uff9a', '\uff7f', '\uff82', '\uff88', '\uff85', '\uff97', '\uff91', '\uff73',
        '\u30f0', '\uff89', '\uff75', '\uff78', '\uff94', '\uff8f', '\uff79', '\uff8c',
        '\uff7a', '\uff74', '\uff83', '\uff71', '\uff7b', '\uff77', '\uff95', '\uff92',
        '\uff90', '\uff7c', '\u30f1', '\uff8b', '\uff93', '\uff7e', '\uff7d',
    )
    private val alphabet = BASE58.toList() + poem
    private val digitTable = alphabet.withIndex().associate { it.value to it.index }
    private val generators = intArrayOf(
        0x3b6a57b2,
        0x26508e6d,
        0x1ea119fa,
        0x3d4233dd,
        0x2a1462b3,
    )

    data class Parsed(
        val chainDiscriminant: Int,
        val canonicalHex: String,
        val publicKeyHex: String,
        val address: String,
    )

    enum class ErrorCode {
        INVALID_PUBLIC_KEY,
        INVALID_DISCRIMINANT,
        INVALID_SENTINEL,
        INVALID_CHARACTER,
        INVALID_CHECKSUM,
        INVALID_CANONICAL_PAYLOAD,
        NETWORK_MISMATCH,
        NON_CANONICAL_ADDRESS,
    }

    class AddressException(
        val code: ErrorCode,
        cause: Throwable? = null,
    ) : IllegalArgumentException(code.name, cause)

    fun encode(publicKey: ByteArray, chainDiscriminant: Int): String {
        if (publicKey.size != ED25519_KEY_LENGTH) throw AddressException(ErrorCode.INVALID_PUBLIC_KEY)
        val canonical = byteArrayOf(0x02, 0x00, 0x01, ED25519_KEY_LENGTH.toByte()) + publicKey
        return encodeCanonical(canonical, checkedDiscriminant(chainDiscriminant))
    }

    fun parse(address: String, expectedDiscriminant: Int? = null): Parsed {
        if (
            address.isEmpty() ||
            address.length > MAX_ADDRESS_CHARACTERS ||
            address.encodeToByteArray().size > MAX_ADDRESS_BYTES ||
            address != address.trim()
        ) {
            throw AddressException(ErrorCode.NON_CANONICAL_ADDRESS)
        }
        val (discriminant, payload) = decode(address)
        if (expectedDiscriminant != null && discriminant != checkedDiscriminant(expectedDiscriminant)) {
            throw AddressException(ErrorCode.NETWORK_MISMATCH)
        }
        val publicKey = decodeCanonicalPublicKey(payload)
        val canonicalAddress = encodeCanonical(payload, discriminant)
        if (canonicalAddress != address) throw AddressException(ErrorCode.NON_CANONICAL_ADDRESS)

        return Parsed(
            chainDiscriminant = discriminant,
            canonicalHex = payload.toHex(prefix = true),
            publicKeyHex = publicKey.toHex(prefix = false),
            address = canonicalAddress,
        )
    }

    fun isValid(address: String, expectedDiscriminant: Int): Boolean =
        runCatching { parse(address, expectedDiscriminant) }.isSuccess

    private fun checkedDiscriminant(value: Int): Int {
        if (value !in 0..MAX_DISCRIMINANT) throw AddressException(ErrorCode.INVALID_DISCRIMINANT)
        return value
    }

    private fun sentinel(discriminant: Int): String = when (discriminant) {
        NexusNetworks.minamoto.chainDiscriminant -> "sora"
        NexusNetworks.taira.chainDiscriminant -> "test"
        0 -> "dev"
        else -> "n$discriminant"
    }

    private fun readDiscriminant(address: String): Int {
        return when {
            address.startsWith("sora") -> NexusNetworks.minamoto.chainDiscriminant
            address.startsWith("test") -> NexusNetworks.taira.chainDiscriminant
            address.startsWith("dev") -> 0
            address.startsWith("n") -> address.drop(1)
                .take(5)
                .takeWhile(Char::isDigit)
                .toIntOrNull()
                ?.takeIf { it <= MAX_DISCRIMINANT }
                ?: throw AddressException(ErrorCode.INVALID_SENTINEL)
            else -> throw AddressException(ErrorCode.INVALID_SENTINEL)
        }
    }

    private fun encodeCanonical(payload: ByteArray, discriminant: Int): String {
        val digits = encodeBase(payload)
        val checksum = checksum(payload)
        return buildString {
            append(sentinel(discriminant))
            (digits + checksum).forEach { digit ->
                append(alphabet.getOrNull(digit) ?: throw AddressException(ErrorCode.INVALID_CHARACTER))
            }
        }
    }

    private fun decode(address: String): Pair<Int, ByteArray> {
        val discriminant = readDiscriminant(address)
        val prefix = sentinel(discriminant)
        if (!address.startsWith(prefix)) throw AddressException(ErrorCode.NON_CANONICAL_ADDRESS)
        val digits = address.drop(prefix.length).map {
            digitTable[it] ?: throw AddressException(ErrorCode.INVALID_CHARACTER)
        }
        if (digits.size <= CHECKSUM_LENGTH) throw AddressException(ErrorCode.INVALID_CANONICAL_PAYLOAD)
        val payloadDigits = digits.dropLast(CHECKSUM_LENGTH)
        val payload = decodeBase(payloadDigits)
        if (digits.takeLast(CHECKSUM_LENGTH) != checksum(payload)) {
            throw AddressException(ErrorCode.INVALID_CHECKSUM)
        }
        return discriminant to payload
    }

    private fun encodeBase(bytes: ByteArray): List<Int> {
        val value = bytes.map { it.toInt() and 0xff }.toMutableList()
        val leadingZeros = value.takeWhile { it == 0 }.size
        val result = mutableListOf<Int>()
        var start = leadingZeros
        while (start < value.size) {
            var remainder = 0
            for (index in start until value.size) {
                val accumulator = (remainder shl 8) or value[index]
                value[index] = accumulator / BASE
                remainder = accumulator % BASE
            }
            result += remainder
            while (start < value.size && value[start] == 0) start++
        }
        repeat(leadingZeros) { result += 0 }
        if (result.isEmpty()) result += 0
        return result.asReversed()
    }

    private fun decodeBase(input: List<Int>): ByteArray {
        val value = input.toMutableList()
        val leadingZeros = value.takeWhile { it == 0 }.size
        val result = mutableListOf<Int>()
        var start = leadingZeros
        while (start < value.size) {
            var remainder = 0
            for (index in start until value.size) {
                val digit = value[index]
                if (digit !in 0 until BASE) throw AddressException(ErrorCode.INVALID_CHARACTER)
                val accumulator = remainder * BASE + digit
                value[index] = accumulator / 256
                remainder = accumulator % 256
            }
            result += remainder
            while (start < value.size && value[start] == 0) start++
        }
        repeat(leadingZeros) { result += 0 }
        return result.asReversed().map(Int::toByte).toByteArray()
    }

    private fun checksum(payload: ByteArray): List<Int> {
        val values = expandHrp() + toBase32(payload) + List(CHECKSUM_LENGTH) { 0 }
        val polymod = polymod(values) xor BECH32M_CONST
        return List(CHECKSUM_LENGTH) { index ->
            (polymod ushr (5 * (CHECKSUM_LENGTH - 1 - index))) and 0x1f
        }
    }

    private fun toBase32(bytes: ByteArray): List<Int> {
        var accumulator = 0
        var bits = 0
        val result = mutableListOf<Int>()
        bytes.forEach {
            accumulator = ((accumulator shl 8) or (it.toInt() and 0xff)) and 0xfff
            bits += 8
            while (bits >= 5) {
                bits -= 5
                result += (accumulator ushr bits) and 0x1f
            }
        }
        if (bits > 0) result += (accumulator shl (5 - bits)) and 0x1f
        return result
    }

    private fun polymod(values: List<Int>): Int {
        var checksum = 1
        values.forEach { value ->
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            generators.forEachIndexed { index, generator ->
                if (((top ushr index) and 1) == 1) checksum = checksum xor generator
            }
        }
        return checksum
    }

    private fun expandHrp(): List<Int> =
        HRP.map { it.code ushr 5 } + listOf(0) + HRP.map { it.code and 31 }

    private fun decodeCanonicalPublicKey(payload: ByteArray): ByteArray {
        if (payload.size != 4 + ED25519_KEY_LENGTH) {
            throw AddressException(ErrorCode.INVALID_CANONICAL_PAYLOAD)
        }
        val header = payload[0].toInt() and 0xff
        val version = header ushr 5
        val addressClass = (header ushr 3) and 0b11
        val normalizationVersion = (header ushr 1) and 0b11
        val hasExtension = (header and 1) == 1
        if (
            version != 0 ||
            addressClass != 0 ||
            normalizationVersion != 1 ||
            hasExtension ||
            payload[1].toInt() != 0 ||
            payload[2].toInt() != 1 ||
            payload[3].toInt() != ED25519_KEY_LENGTH
        ) {
            throw AddressException(ErrorCode.INVALID_CANONICAL_PAYLOAD)
        }
        return payload.copyOfRange(4, payload.size)
    }

    private fun ByteArray.toHex(prefix: Boolean): String =
        joinToString(prefix = if (prefix) "0x" else "", separator = "") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
}
