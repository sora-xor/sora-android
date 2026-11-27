package jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import java.math.BigInteger
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.scale.datatype.compactInt

class Compact(name: String) : NumberType(name) {
    override fun decode(
        scaleCodecReader: ScaleCodecReader,
        runtime: RuntimeSnapshot,
    ): BigInteger {
        return compactInt.read(scaleCodecReader)
    }

    override fun encode(
        scaleCodecWriter: ScaleCodecWriter,
        runtime: RuntimeSnapshot,
        value: BigInteger,
    ) {
        return compactInt.write(scaleCodecWriter, value)
    }
}
