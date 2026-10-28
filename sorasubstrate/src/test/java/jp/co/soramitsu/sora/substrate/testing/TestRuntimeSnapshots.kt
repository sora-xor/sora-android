package jp.co.soramitsu.sora.substrate.testing

import com.google.gson.Gson
import java.io.File
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.xsubstrate.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.xsubstrate.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.xsubstrate.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RuntimeMetadataSchemaV14

/**
 * Runtime snapshots for decoding tests, built from real metadata the way
 * RuntimeManager.buildTypeRegistry14 builds them (V14 lookup plus the mobile type aliases).
 */
internal object TestRuntimeSnapshots {

    /**
     * SORA mainnet metadata of runtime 133 (transaction version 131), captured on 10 October 2026
     * with `state_getMetadata` at a finalized block: 623,616 bytes, SHA-256 of the raw bytes
     * `e7b2459c...2e17`, of the app's canonical hex form `ab22a2d4...c732`.
     */
    val mainnet133MetadataHex: String by lazy { resource("sora2_metadata_mainnet_133") }

    /** The type aliases packaged with the app; the remote config serves the same bytes. */
    val packagedTypes: String by lazy {
        repoFile("common/src/production/assets/types_scalecodec_mobile.json")
    }

    val mainnet133: RuntimeSnapshot by lazy {
        build(mainnet133MetadataHex, packagedTypes, runtimeVersion = 133)
    }

    /** An older SORA2 runtime whose balances still have `misc_frozen` and `fee_frozen`. */
    val oldBalancesLayout: RuntimeSnapshot by lazy {
        build(resource("sora2_metadata_dev"), resource("sora2_dev.json"), runtimeVersion = 35)
    }

    fun build(metadataHex: String, typesJson: String, runtimeVersion: Int): RuntimeSnapshot {
        val reader = RuntimeMetadataReader.read(metadataHex.trim())
        check(reader.metadataVersion >= 14) { "Only V14 metadata is supported here" }
        val parsed = TypeDefinitionParser.parseNetworkVersioning(
            tree = Gson().fromJson(typesJson, TypeDefinitionsTree::class.java),
            typePreset = TypesParserV14.parse(
                reader.metadata[RuntimeMetadataSchemaV14.lookup],
                v14Preset(),
            ).typePreset,
            currentRuntimeVersion = runtimeVersion,
            upto14 = false,
        )
        val registry = TypeRegistry(
            types = parsed.typePreset,
            dynamicTypeResolver = DynamicTypeResolver.defaultCompoundResolver(),
        )
        return RuntimeSnapshot(registry, VersionedRuntimeBuilder.buildMetadata(reader, registry))
    }

    fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "Missing $name" }
            .bufferedReader()
            .use { it.readText() }

    fun repoFile(path: String): String =
        listOf(File("../$path"), File(path)).first(File::isFile).readText()
}
