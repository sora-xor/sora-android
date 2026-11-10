package jp.co.soramitsu.xsubstrate.scale.datatype

import io.emeraldpay.polkaj.scale.ScaleReader
import io.emeraldpay.polkaj.scale.ScaleWriter

abstract class DataType<T> : ScaleReader<T>, ScaleWriter<T> {
    abstract fun conformsType(value: Any?): Boolean
}
