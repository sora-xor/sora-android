package jp.co.soramitsu.xsubstrate.runtime.definitions.registry

import jp.co.soramitsu.xsubstrate.runtime.definitions.types.Type

fun TypeRegistry.getOrThrow(definition: String): Type<*> {
    return get(definition) ?: error("Type $definition was not found.")
}
