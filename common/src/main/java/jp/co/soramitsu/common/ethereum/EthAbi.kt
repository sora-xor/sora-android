package jp.co.soramitsu.common.ethereum

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import jp.co.soramitsu.xcrypto.hash.keccak256

/**
 * The Solidity ABI for the few types the HASHI bridge contracts take: address, uint256, uint8,
 * bytes32 and dynamic arrays of uint8 and bytes32. Each call is written as its 4-byte selector
 * (keccak256 of the canonical signature) followed by the head and tail encoding of the arguments.
 */
object EthAbi {

    private const val WORD = 32

    private val UINT256_MAX: BigInteger = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)

    sealed class Value {
        abstract val type: String
        open val dynamic: Boolean get() = false

        class Address(address: ByteArray) : Value() {
            val bytes: ByteArray = address.copyOf()
            override val type = "address"

            init {
                require(address.size == EthAddress.SIZE) { "ABI_ADDRESS_INVALID" }
            }
        }

        class Uint(val value: BigInteger, val bits: Int = 256) : Value() {
            override val type = "uint$bits"

            init {
                require(bits in 8..256 && bits % 8 == 0) { "ABI_UINT_BITS_INVALID" }
                require(value.signum() >= 0 && value.bitLength() <= bits) { "ABI_UINT_RANGE_INVALID" }
            }
        }

        class Bytes32(bytes: ByteArray) : Value() {
            val bytes: ByteArray = bytes.copyOf()
            override val type = "bytes32"

            init {
                require(bytes.size == WORD) { "ABI_BYTES32_INVALID" }
            }
        }

        /** `T[]` of static elements of one type. */
        class StaticArray(val elementType: String, val elements: List<Value>) : Value() {
            override val type = "$elementType[]"
            override val dynamic = true

            init {
                require(elements.all { !it.dynamic && it.type == elementType }) { "ABI_ARRAY_ELEMENT_INVALID" }
            }
        }
    }

    fun selector(signature: String): ByteArray = signature.toByteArray(Charsets.US_ASCII).keccak256().copyOf(4)

    /** The canonical signature `name(type,…)` of [name] with [args]. */
    fun signatureOf(name: String, args: List<Value>): String = "$name(${args.joinToString(",") { it.type }})"

    fun encodeCall(name: String, args: List<Value>): ByteArray =
        selector(signatureOf(name, args)) + encodeArguments(args)

    fun encodeArguments(args: List<Value>): ByteArray {
        val head = ByteArrayOutputStream()
        val tail = ByteArrayOutputStream()
        val headSize = args.size * WORD
        args.forEach { arg ->
            if (arg.dynamic) {
                head.write(uintWord(BigInteger.valueOf((headSize + tail.size()).toLong())))
                tail.write(encodeDynamic(arg))
            } else {
                head.write(encodeStatic(arg))
            }
        }
        return head.toByteArray() + tail.toByteArray()
    }

    private fun encodeStatic(value: Value): ByteArray = when (value) {
        is Value.Address -> ByteArray(12) + value.bytes
        is Value.Uint -> uintWord(value.value)
        is Value.Bytes32 -> value.bytes.copyOf()
        is Value.StaticArray -> throw IllegalArgumentException("ABI_DYNAMIC_AS_STATIC")
    }

    private fun encodeDynamic(value: Value): ByteArray = when (value) {
        is Value.StaticArray -> {
            val out = ByteArrayOutputStream()
            out.write(uintWord(BigInteger.valueOf(value.elements.size.toLong())))
            value.elements.forEach { out.write(encodeStatic(it)) }
            out.toByteArray()
        }
        else -> throw IllegalArgumentException("ABI_STATIC_AS_DYNAMIC")
    }

    fun uintWord(value: BigInteger): ByteArray {
        require(value.signum() >= 0 && value <= UINT256_MAX) { "ABI_UINT_RANGE_INVALID" }
        return EthHex.toFixed(value, WORD)
    }

    // --- Results of eth_call -------------------------------------------------------------------

    /** A single `uint256` return value. */
    fun decodeUint(result: ByteArray): BigInteger {
        require(result.size == WORD) { "ABI_RESULT_LENGTH_INVALID" }
        return BigInteger(1, result)
    }

    /** A single `bool` return value: exactly 0 or 1 in one word. */
    fun decodeBool(result: ByteArray): Boolean = when (decodeUint(result)) {
        BigInteger.ZERO -> false
        BigInteger.ONE -> true
        else -> throw IllegalArgumentException("ABI_BOOL_INVALID")
    }

    /** A single `address` return value: 12 zero bytes, then the address. */
    fun decodeAddress(result: ByteArray): ByteArray {
        require(result.size == WORD) { "ABI_RESULT_LENGTH_INVALID" }
        require(result.copyOfRange(0, 12).all { it == 0.toByte() }) { "ABI_ADDRESS_INVALID" }
        return result.copyOfRange(12, WORD)
    }

    /** A single `uint8` return value (ERC-20 `decimals()`). */
    fun decodeUint8(result: ByteArray): Int {
        val value = decodeUint(result)
        require(value.bitLength() <= 8) { "ABI_UINT8_INVALID" }
        return value.toInt()
    }

    private val ERROR_SELECTOR = selector("Error(string)")

    /** [value] as an Int, or [ArithmeticException] when it does not fit (intValueExact needs API 31). */
    private fun smallInt(value: BigInteger): Int {
        if (value.bitLength() > 31) throw ArithmeticException("ABI_OFFSET_TOO_LARGE")
        return value.toInt()
    }

    /**
     * The message of a Solidity `revert("…")` / `require(…, "…")`: `Error(string)` data. Null when
     * the data is something else (a custom error, a panic or nothing).
     */
    fun decodeRevertReason(data: ByteArray): String? {
        if (data.size < 4 + 3 * WORD || !data.copyOfRange(0, 4).contentEquals(ERROR_SELECTOR)) return null
        val body = data.copyOfRange(4, data.size)
        return try {
            val offset = smallInt(BigInteger(1, body.copyOfRange(0, WORD)))
            val length = smallInt(BigInteger(1, body.copyOfRange(offset, offset + WORD)))
            if (length > 1024 || offset + WORD + length > body.size) return null
            String(body.copyOfRange(offset + WORD, offset + WORD + length), Charsets.UTF_8)
        } catch (_: ArithmeticException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
