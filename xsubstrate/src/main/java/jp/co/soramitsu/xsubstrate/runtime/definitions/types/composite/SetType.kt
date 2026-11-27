package jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import java.math.BigInteger
import jp.co.soramitsu.xcrypto.util.isPositive
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.TypeReference
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.NumberType

class SetType(
    name: String,
    valueTypeReference: TypeReference,
    val valueList: LinkedHashMap<String, BigInteger>,
) : WrapperType<Set<String>>(name, valueTypeReference) {
    override fun decode(
        scaleCodecReader: ScaleCodecReader,
        runtime: RuntimeSnapshot,
    ): Set<String> {
        val valueType = typeReference.requireValue()
        require(valueType is NumberType)

        val value = valueType.decode(scaleCodecReader, runtime)

        return valueList.mapNotNullTo(mutableSetOf()) { (name, mask) ->
            if (value.and(mask).isPositive()) {
                name
            } else {
                null
            }
        }
    }

    override fun encode(
        scaleCodecWriter: ScaleCodecWriter,
        runtime: RuntimeSnapshot,
        value: Set<String>,
    ) {
        val valueType = typeReference.requireValue()
        require(valueType is NumberType)

        val folded =
            valueList.entries.fold(BigInteger.ZERO) { acc, (name, mask) ->
                if (name in value) {
                    acc + mask
                } else {
                    acc
                }
            }

        valueType.encode(scaleCodecWriter, runtime, folded)
    }

    override fun isValidInstance(instance: Any?): Boolean {
        return instance is Set<*> && instance.all { item -> item in valueList }
    }

    operator fun get(key: String) = valueList[key]
}
