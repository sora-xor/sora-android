package jp.co.soramitsu.xsubstrate.runtime.metadata.builder

import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadataReader

interface RuntimeBuilder {
    fun buildMetadata(
        reader: RuntimeMetadataReader,
        typeRegistry: TypeRegistry,
    ): RuntimeMetadata
}

object VersionedRuntimeBuilder : RuntimeBuilder {
    @Suppress("UseIfInsteadOfWhen", "MagicNumber")
    override fun buildMetadata(
        reader: RuntimeMetadataReader,
        typeRegistry: TypeRegistry,
    ): RuntimeMetadata {
        return when (reader.metadataVersion) {
            14 -> V14RuntimeBuilder.buildMetadata(reader, typeRegistry)
            else -> V13RuntimeBuilder.buildMetadata(reader, typeRegistry)
        }
    }
}
