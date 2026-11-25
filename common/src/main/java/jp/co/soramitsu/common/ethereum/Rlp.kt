package jp.co.soramitsu.common.ethereum

import java.io.ByteArrayOutputStream
import java.math.BigInteger

/**
 * Recursive Length Prefix encoding (Ethereum yellow paper, appendix B). Only encoding is needed:
 * the wallet writes its own transactions and never parses RLP from the network.
 */
object Rlp {

    sealed class Item {
        class Bytes(val value: ByteArray) : Item()
        class ItemList(val items: List<Item>) : Item()
    }

    fun bytes(value: ByteArray): Item = Item.Bytes(value)

    /** A scalar: big-endian with no leading zeros; zero is the empty string. */
    fun scalar(value: BigInteger): Item {
        require(value.signum() >= 0) { "RLP_SCALAR_NEGATIVE" }
        if (value.signum() == 0) return Item.Bytes(ByteArray(0))
        val raw = value.toByteArray()
        val stripped = if (raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        return Item.Bytes(stripped)
    }

    fun scalar(value: Long): Item = scalar(BigInteger.valueOf(value))

    fun list(vararg items: Item): Item = Item.ItemList(items.toList())

    fun list(items: List<Item>): Item = Item.ItemList(items)

    fun encode(item: Item): ByteArray = when (item) {
        is Item.Bytes -> encodeBytes(item.value)
        is Item.ItemList -> {
            val body = ByteArrayOutputStream()
            item.items.forEach { body.write(encode(it)) }
            withLength(body.toByteArray(), SHORT_LIST, LONG_LIST)
        }
    }

    private fun encodeBytes(value: ByteArray): ByteArray =
        if (value.size == 1 && (value[0].toInt() and 0xff) < SHORT_STRING) {
            value.copyOf()
        } else {
            withLength(value, SHORT_STRING, LONG_STRING)
        }

    private fun withLength(body: ByteArray, shortBase: Int, longBase: Int): ByteArray {
        val out = ByteArrayOutputStream()
        if (body.size <= 55) {
            out.write(shortBase + body.size)
        } else {
            val length = BigInteger.valueOf(body.size.toLong()).toByteArray().let {
                if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
            }
            out.write(longBase + length.size)
            out.write(length)
        }
        out.write(body)
        return out.toByteArray()
    }

    private const val SHORT_STRING = 0x80
    private const val LONG_STRING = 0xb7
    private const val SHORT_LIST = 0xc0
    private const val LONG_LIST = 0xf7
}
