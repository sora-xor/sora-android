package jp.co.soramitsu.xsubstrate.encrypt.junction

import java.nio.ByteBuffer
import java.nio.ByteOrder
import jp.co.soramitsu.xcrypto.hash.blake2.blake2b128
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.scale.datatype.string
import jp.co.soramitsu.xsubstrate.scale.datatype.toByteArray

private const val CHAINCODE_LENGTH = 32
private const val RAW_JUNCTION_BYTES_LENGTH = 8

object SubstrateJunctionDecoder : JunctionDecoder() {
    override fun decodeJunction(
        rawJunction: String,
        type: JunctionType,
    ): Junction {
        val chainCode = normalize(serialize(rawJunction))

        return Junction(type, chainCode)
    }

    private fun serialize(rawJunction: String): ByteArray {
        rawJunction.toLongOrNull()?.let {
            val bytes = ByteArray(RAW_JUNCTION_BYTES_LENGTH)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(it)

            return bytes
        }

        return runCatching {
            rawJunction.fromHex()
        }.getOrElse {
            string.toByteArray(rawJunction)
        }
    }

    private fun normalize(bytes: ByteArray): ByteArray =
        when {
            bytes.size < CHAINCODE_LENGTH ->
                ByteArray(CHAINCODE_LENGTH).apply {
                    bytes.copyInto(this)
                }
            bytes.size > CHAINCODE_LENGTH -> bytes.blake2b128()
            else -> bytes
        }
}
