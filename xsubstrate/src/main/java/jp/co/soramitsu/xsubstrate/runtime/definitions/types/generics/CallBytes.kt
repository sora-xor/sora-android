package jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.Primitive
import jp.co.soramitsu.xsubstrate.scale.datatype.byteArraySized

object CallBytes : Primitive<String>("CallBytes") {
    @Suppress("NotImplementedDeclaration")
    override fun decode(
        scaleCodecReader: ScaleCodecReader,
        runtime: RuntimeSnapshot,
    ): String {
        throw NotImplementedError() // the same as in polkascan implementation
    }

    override fun encode(
        scaleCodecWriter: ScaleCodecWriter,
        runtime: RuntimeSnapshot,
        value: String,
    ) {
        val bytes = value.fromHex()

        byteArraySized(bytes.size).write(scaleCodecWriter, bytes)
    }

    override fun isValidInstance(instance: Any?): Boolean {
        return instance is String
    }
}
