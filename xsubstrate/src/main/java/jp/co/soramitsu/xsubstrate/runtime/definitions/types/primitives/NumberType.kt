package jp.co.soramitsu.xsubstrate.runtime.definitions.types.primitives

import java.math.BigInteger

abstract class NumberType(name: String) : Primitive<BigInteger>(name) {
    override fun isValidInstance(instance: Any?): Boolean {
        return instance is BigInteger
    }
}
