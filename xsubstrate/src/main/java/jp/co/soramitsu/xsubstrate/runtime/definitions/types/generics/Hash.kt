package jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics

import jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives.FixedByteArray

val H160 = Hash(160)
val H256 = Hash(256)
val H512 = Hash(512)

class Hash(bits: Int) : FixedByteArray("H$bits", length = bits / 8) {
    init {
        require(bits % Byte.SIZE_BITS == 0)
    }
}
