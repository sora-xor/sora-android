package jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic

import jp.co.soramitsu.xsubstrate.extensions.tryFindNonNull
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.extentsions.BoxExtension
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.extentsions.CompactExtension
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.extentsions.FixedArrayExtension
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.extentsions.HashMapExtension
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.extentsions.OptionExtension
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.extentsions.ResultTypeExtension
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.extentsions.TupleExtension
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.extentsions.VectorExtension
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.Type

class DynamicTypeResolver(
    val extensions: List<DynamicTypeExtension>,
) {
    constructor(vararg extensions: DynamicTypeExtension) : this(extensions.toList())

    fun createDynamicType(
        name: String,
        typeDef: String,
        innerTypeProvider: TypeProvider,
    ): Type<*>? {
        return extensions.tryFindNonNull { it.createType(name, typeDef, innerTypeProvider) }
    }

    companion object {
        val DEFAULT_COMPOUND_EXTENSIONS =
            listOf(
                VectorExtension,
                CompactExtension,
                OptionExtension,
                BoxExtension,
                TupleExtension,
                FixedArrayExtension,
                HashMapExtension,
                ResultTypeExtension,
            )

        fun defaultCompoundResolver(): DynamicTypeResolver {
            return DynamicTypeResolver(DEFAULT_COMPOUND_EXTENSIONS)
        }
    }
}
