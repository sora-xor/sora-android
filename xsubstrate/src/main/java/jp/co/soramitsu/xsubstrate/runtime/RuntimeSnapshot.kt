package jp.co.soramitsu.xsubstrate.runtime

import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadata

typealias OverriddenConstantsMap = Map<String, Map<String, String>>

class RuntimeSnapshot(
    val typeRegistry: TypeRegistry,
    val metadata: RuntimeMetadata,
    val overrides: OverriddenConstantsMap? = null,
)
