package jp.co.soramitsu.xsubstrate.scale.datatype

import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import java.io.ByteArrayOutputStream

fun <T> DataType<T>.toByteArray(value: T): ByteArray {
    val stream = ByteArrayOutputStream()
    val writer = ScaleCodecWriter(stream)

    write(writer, value)

    return stream.toByteArray()
}
