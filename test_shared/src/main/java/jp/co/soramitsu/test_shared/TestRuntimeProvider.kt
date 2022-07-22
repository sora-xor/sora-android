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
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder

object TestRuntimeProvider {

    fun buildRuntime(networkName: String): RuntimeSnapshot {
        val runtimeMetadataReader = buildRawMetadata(networkName)
        val typeRegistry = buildRegistry(networkName)

        val metadata = VersionedRuntimeBuilder.buildMetadata(runtimeMetadataReader, typeRegistry)

        return RuntimeSnapshot(typeRegistry, metadata)
    }

    private fun buildRawMetadata(networkName: String) =
        getFileContentFromResources("${networkName}_metadata").run {
            RuntimeMetadataReader.read(this)
        }

    private fun buildRegistry(networkName: String): TypeRegistry {
        val gson = Gson()
        val reader = JsonReader(getResourceReader("default.json"))
        val soraReader = JsonReader(getResourceReader("$networkName.json"))

        val tree = gson.fromJson<TypeDefinitionsTree>(reader, TypeDefinitionsTree::class.java)
        val soraTree =
            gson.fromJson<TypeDefinitionsTree>(soraReader, TypeDefinitionsTree::class.java)

        val defaultTypeRegistry =
            TypeDefinitionParser.parseBaseDefinitions(tree, v13Preset()).typePreset
        val networkParsed = TypeDefinitionParser.parseNetworkVersioning(
            soraTree,
            defaultTypeRegistry
        )

        return TypeRegistry(
            types = networkParsed.typePreset,
            dynamicTypeResolver = DynamicTypeResolver(
                DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension
            )
        )
    }
}
