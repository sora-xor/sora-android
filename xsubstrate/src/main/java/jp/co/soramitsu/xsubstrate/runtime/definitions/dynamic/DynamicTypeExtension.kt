package jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic

import jp.co.soramitsu.xsubstrate.runtime.definitions.types.Type
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.TypeReference

typealias TypeProvider = (typeDef: String) -> TypeReference

interface DynamicTypeExtension {
    fun createType(
        name: String,
        typeDef: String,
        typeProvider: TypeProvider,
    ): Type<*>?
}
