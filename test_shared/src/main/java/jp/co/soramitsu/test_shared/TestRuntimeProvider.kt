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

object TestRuntimeProvider {

    fun buildRuntime(networkName: String): RuntimeSnapshot {
        val runtimeMetadataReader = buildRawMetadata(networkName)
        val typeRegistry = buildRegistry(networkName, runtimeMetadataReader)

        val metadata = VersionedRuntimeBuilder.buildMetadata(runtimeMetadataReader, typeRegistry)

        return RuntimeSnapshot(typeRegistry, metadata)
    }

    private fun buildRawMetadata(networkName: String) =
        getFileContentFromResources("${networkName}_metadata").run {
            RuntimeMetadataReader.read(this)
        }

    private fun buildRegistry(
        networkName: String,
        runtimeMetadataReader: RuntimeMetadataReader
    ): TypeRegistry {
        val gson = Gson()
        val soraReader = JsonReader(getResourceReader("$networkName.json"))

        val soraTree =
            gson.fromJson<TypeDefinitionsTree>(soraReader, TypeDefinitionsTree::class.java)

        val networkParsed = TypeDefinitionParser.parseNetworkVersioning(
            tree = soraTree,
            typePreset = TypesParserV14.parse(
                runtimeMetadataReader.metadata[RuntimeMetadataSchemaV14.lookup],
                v14Preset()
            ).typePreset,
            currentRuntimeVersion = 1,
            upto14 = false,
        )

        return TypeRegistry(
            types = networkParsed.typePreset,
            dynamicTypeResolver = DynamicTypeResolver.defaultCompoundResolver(),
        )
    }
}
