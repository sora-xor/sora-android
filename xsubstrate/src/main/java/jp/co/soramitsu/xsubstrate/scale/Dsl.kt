@file:Suppress("unused", "EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.xsubstrate.scale

import java.math.BigInteger
import jp.co.soramitsu.xsubstrate.scale.datatype.DataType
import jp.co.soramitsu.xsubstrate.scale.datatype.EnumType
import jp.co.soramitsu.xsubstrate.scale.datatype.boolean
import jp.co.soramitsu.xsubstrate.scale.datatype.byte
import jp.co.soramitsu.xsubstrate.scale.datatype.byteArray
import jp.co.soramitsu.xsubstrate.scale.datatype.byteArraySized
import jp.co.soramitsu.xsubstrate.scale.datatype.compactInt
import jp.co.soramitsu.xsubstrate.scale.datatype.list
import jp.co.soramitsu.xsubstrate.scale.datatype.long
import jp.co.soramitsu.xsubstrate.scale.datatype.scalable
import jp.co.soramitsu.xsubstrate.scale.datatype.string
import jp.co.soramitsu.xsubstrate.scale.datatype.tuple
import jp.co.soramitsu.xsubstrate.scale.datatype.uint128
import jp.co.soramitsu.xsubstrate.scale.datatype.uint16
import jp.co.soramitsu.xsubstrate.scale.datatype.uint32
import jp.co.soramitsu.xsubstrate.scale.datatype.uint64
import jp.co.soramitsu.xsubstrate.scale.datatype.uint8
import jp.co.soramitsu.xsubstrate.scale.datatype.union
import kotlin.reflect.KClass

typealias StructBuilder<SCHEMA> = (EncodableStruct<SCHEMA>) -> Unit

operator fun <S : Schema<S>> S.invoke(block: StructBuilder<S>? = null): EncodableStruct<S> {
    val struct = EncodableStruct(this)

    block?.invoke(struct)

    return struct
}

fun <S : Schema<S>> S.string(default: String? = null) = NonNullFieldDelegate(string, this, default)

fun <S : Schema<S>> S.uint8(default: UByte? = null) = NonNullFieldDelegate(uint8, this, default)

fun <S : Schema<S>> S.uint32(default: UInt? = null) = NonNullFieldDelegate(uint32, this, default)

fun <S : Schema<S>> S.uint16(default: Int? = null) = NonNullFieldDelegate(uint16, this, default)

fun <S : Schema<S>> S.uint128(default: BigInteger? = null) = NonNullFieldDelegate(uint128, this, default)

fun <S : Schema<S>> S.bool(default: Boolean? = null) = NonNullFieldDelegate(boolean, this, default)

fun <S : Schema<S>> S.uint64(default: BigInteger? = null) = NonNullFieldDelegate(uint64, this, default)

fun <S : Schema<S>, T : Schema<T>> S.schema(
    schema: T,
    default: EncodableStruct<T>? = null,
) = NonNullFieldDelegate(scalable(schema), this, default)

fun <S : Schema<S>, T, D : DataType<T>> S.vector(
    type: D,
    default: List<T>? = null,
) = NonNullFieldDelegate(list(type), this, default)

fun <S : Schema<S>, T : Schema<T>> S.vector(
    schema: T,
    default: List<EncodableStruct<T>>? = null,
) = NonNullFieldDelegate(list(scalable(schema)), this, default)

fun <S : Schema<S>> S.byte(default: Byte? = null) = NonNullFieldDelegate(byte, this, default)

fun <S : Schema<S>> S.compactInt(default: BigInteger? = null) = NonNullFieldDelegate(compactInt, this, default)

fun <S : Schema<S>> S.sizedByteArray(
    length: Int,
    default: ByteArray? = null,
): NonNullFieldDelegate<S, ByteArray> {
    if (default != null) {
        require(length == default.size)
    }

    return NonNullFieldDelegate(byteArraySized(length), this, default)
}

fun <S : Schema<S>, A, B> S.pair(
    first: DataType<A>,
    second: DataType<B>,
    default: Pair<A, B>? = null,
) = NonNullFieldDelegate(tuple(first, second), this, default)

fun <S : Schema<S>> S.byteArray(default: ByteArray? = null): NonNullFieldDelegate<S, ByteArray> {
    return NonNullFieldDelegate(byteArray, this, default)
}

fun <S : Schema<S>> S.long(default: Long? = null) = NonNullFieldDelegate(long, this, default)

fun <S : Schema<S>> S.enum(
    vararg types: DataType<*>,
    default: Any? = null,
) = NonNullFieldDelegate(union(types), this, default)

fun <S : Schema<S>, E : Enum<E>> S.enum(
    enumClass: KClass<E>,
    default: E? = null,
) = NonNullFieldDelegate(EnumType(enumClass.java), this, default)

fun <S : Schema<S>, T> S.custom(
    type: DataType<T>,
    default: T? = null,
) = NonNullFieldDelegate(type, this, default)
