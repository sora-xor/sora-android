/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.test_shared

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14

object RealRuntimeProvider {

    fun buildRuntime(networkName: String, suffix: String = ""): RuntimeSnapshot {
        val metadataRaw = buildRawMetadata(networkName, suffix)
        val parseResult = TypesParserV14.parse(
            lookup = metadataRaw.metadata[RuntimeMetadataSchemaV14.lookup],
            typePreset = v14Preset()
        )
        val nReader = JsonReader(getResourceReader("${networkName}$suffix.json"))
        val nTree =
            Gson().fromJson<TypeDefinitionsTree>(nReader, TypeDefinitionsTree::class.java)
        val networkParsed = TypeDefinitionParser.parseNetworkVersioning(
            tree = nTree,
            typePreset = parseResult.typePreset,
        )
        val typeRegistry = TypeRegistry(
            networkParsed.typePreset,
            DynamicTypeResolver.defaultCompoundResolver()
        )
        val metadata = VersionedRuntimeBuilder.buildMetadata(metadataRaw, typeRegistry)
        return RuntimeSnapshot(typeRegistry, metadata)
    }

    private fun buildRawMetadata(networkName: String, suffix: String = "") =
        getFileContentFromResources("${networkName}_metadata$suffix").run {
            RuntimeMetadataReader.read(this)
        }
}
