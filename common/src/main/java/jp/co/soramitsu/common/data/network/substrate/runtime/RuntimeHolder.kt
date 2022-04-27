/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.substrate.runtime

import com.google.gson.Gson
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.data.network.substrate.Constants
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePreset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromByteArrayOrNull
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigInteger

object RuntimeHolder {
    private var runtimeSnapshot: RuntimeSnapshot? = null
    private var netRuntimeVersionChecked: Boolean = false
    private var metadataVersion: Int? = null

    @Throws(IllegalArgumentException::class)
    fun getRuntime(): RuntimeSnapshot = requireNotNull(runtimeSnapshot) { "Runtime is null" }
    @Throws(IllegalArgumentException::class)
    fun getMetadataVersion(): Int = requireNotNull(metadataVersion) { "MetadataVersion is null" }
    fun isInitialized() = runtimeSnapshot != null && netRuntimeVersionChecked
    var prefix: Short = 69

    internal fun setRuntime(snapshot: RuntimeSnapshot, snapshotFromSora: Boolean, metadata: Int) {
        runtimeSnapshot = snapshot
        metadataVersion = metadata
        FirebaseWrapper.log("Set Runtime $snapshotFromSora")
        val valueConstant =
            getRuntime().metadata.module(Pallete.SYSTEM.palletName).constants[Constants.SS58Prefix.constantName]
        prefix =
            (
            valueConstant?.type?.fromByteArrayOrNull(
                getRuntime(),
                valueConstant.value
            ) as? BigInteger
            )?.toShort()
            ?: 69
        if (snapshotFromSora) setNetRuntimeVersionChecked()
    }

    internal fun setNetRuntimeVersionChecked() {
        netRuntimeVersionChecked = true
    }
}

private const val DEFAULT_TYPES_FILE = "default_types.json"
private const val SORA2_TYPES_FILE = "types_scalecodec_mobile.json"
private const val RUNTIME_METADATA_FILE = "sora2_metadata"
private const val RUNTIME_VERSION_PREF = "last_used_runtime_version"
private const val SORA_VERSION_LAST_LAUNCH_PREF = "last_launch_version"
private const val RUNTIME_VERSION_START = 1

class RuntimeManager(
    private val fileManager: FileManager,
    private val gson: Gson,
    private val soraPreferences: SoraPreferences,
    private val socketService: SocketService,
    private val typesApi: SubstrateTypesApi,
) {

    private val mutex = Mutex()

    suspend fun start() {
        mutex.withLock {
            if (!RuntimeHolder.isInitialized()) {
                initFromCache()
                checkRuntimeVersion()
            }
        }
    }

    private suspend fun initFromCache() {
        val replaceCache = soraPreferences.getInt(SORA_VERSION_LAST_LAUNCH_PREF, 0).let { version ->
            (OptionsProvider.CURRENT_VERSION_CODE != version).also {
                if (it) soraPreferences.putInt(
                    SORA_VERSION_LAST_LAUNCH_PREF,
                    OptionsProvider.CURRENT_VERSION_CODE
                )
            }
        }
        buildRuntimeSnapshot(
            getContentFromCache(RUNTIME_METADATA_FILE, replaceCache),
            MetadataSource.Cache(replaceCache),
            soraPreferences.getInt(
                RUNTIME_VERSION_PREF,
                RUNTIME_VERSION_START
            )
        )
    }

    private fun getContentFromCache(fileName: String, replaceCache: Boolean = false): String {
        val cache = fileManager.readInternalCacheFile(fileName)
        if (cache != null && !replaceCache) return cache
        val asset = fileManager.readAssetFile(fileName)
        saveToCache(fileName, asset)
        return asset
    }

    private fun rawTypesToTree(raw: String) = gson.fromJson(raw, TypeDefinitionsTree::class.java)

    private suspend fun checkRuntimeVersion() {
        val runtimeVersion = socketService.executeAsync(
            request = RuntimeVersionRequest(),
            mapper = pojo<RuntimeVersion>().nonNull()
        )
        if (runtimeVersion.specVersion > soraPreferences.getInt(
                RUNTIME_VERSION_PREF,
                RUNTIME_VERSION_START
            )
        ) {
            FirebaseWrapper.log("New runtime version ${runtimeVersion.specVersion}")
            try {
                val metadata = socketService.executeAsync(
                    request = GetMetadataRequest,
                    mapper = pojo<String>().nonNull()
                )
                buildRuntimeSnapshot(metadata, MetadataSource.SoraNet, runtimeVersion.specVersion)
                saveToCache(RUNTIME_METADATA_FILE, metadata)
                soraPreferences.putInt(RUNTIME_VERSION_PREF, runtimeVersion.specVersion)
            } catch (t: Throwable) {
                FirebaseWrapper.recordException(t)
            }
        } else {
            RuntimeHolder.setNetRuntimeVersionChecked()
        }
    }

    private fun saveToCache(file: String, content: String) =
        fileManager.writeInternalCacheFile(file, content)

    private suspend fun buildRuntimeSnapshot(
        metadata: String,
        metadataSource: MetadataSource,
        runtimeVersion: Int,
    ) {
        val runtimeMetadataReader = RuntimeMetadataReader.read(metadata)
        val typeRegistry = when (metadataSource) {
            is MetadataSource.Cache -> {
                if (runtimeMetadataReader.metadataVersion < 14) {
                    val defaultTypesRaw =
                        getContentFromCache(DEFAULT_TYPES_FILE, metadataSource.replaceCache)
                    val sora2TypesRaw =
                        getContentFromCache(SORA2_TYPES_FILE, metadataSource.replaceCache)
                    buildTypeRegistry12(defaultTypesRaw, sora2TypesRaw, runtimeVersion)
                } else {
                    val sora2TypesRaw =
                        getContentFromCache(SORA2_TYPES_FILE, metadataSource.replaceCache)
                    buildTypeRegistry14(sora2TypesRaw, runtimeMetadataReader, runtimeVersion)
                }
            }
            is MetadataSource.SoraNet -> {
                if (runtimeMetadataReader.metadataVersion < 14) {
                    val defaultTypes = typesApi.getDefaultTypes()
                    val sora2Types = typesApi.getSora2Types()
                    buildTypeRegistry12(defaultTypes, sora2Types, runtimeVersion).also {
                        saveToCache(DEFAULT_TYPES_FILE, defaultTypes)
                        saveToCache(SORA2_TYPES_FILE, sora2Types)
                    }
                } else {
                    val sora2Types = typesApi.getSora2TypesMetadata14()
                    buildTypeRegistry14(sora2Types, runtimeMetadataReader, runtimeVersion).also {
                        saveToCache(SORA2_TYPES_FILE, sora2Types)
                    }
                }
            }
        }
        val runtimeMetadata =
            VersionedRuntimeBuilder.buildMetadata(runtimeMetadataReader, typeRegistry)
        val runtimeSnapshot = RuntimeSnapshot(typeRegistry, runtimeMetadata)
        RuntimeHolder.setRuntime(runtimeSnapshot, metadataSource is MetadataSource.SoraNet, runtimeMetadataReader.metadataVersion)
    }

    private fun buildTypeRegistry12(
        defaultRaw: String,
        sora2TypesRaw: String,
        runtimeVersion: Int
    ): TypeRegistry {
        return buildTypeRegistryCommon(
            {
                TypeDefinitionParser.parseBaseDefinitions(
                    rawTypesToTree(defaultRaw),
                    v13Preset()
                ).typePreset
            },
            sora2TypesRaw,
            runtimeVersion,
            { DynamicTypeResolver(DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension) },
        )
    }

    private fun buildTypeRegistry14(
        sora2Raw: String,
        runtimeMetadataReader: RuntimeMetadataReader,
        runtimeVersion: Int
    ): TypeRegistry {
        return buildTypeRegistryCommon(
            {
                TypesParserV14.parse(
                    runtimeMetadataReader.metadata[RuntimeMetadataSchemaV14.lookup],
                    v14Preset()
                ).typePreset
            },
            sora2Raw,
            runtimeVersion,
            { DynamicTypeResolver.defaultCompoundResolver() },
        )
    }

    private fun buildTypeRegistryCommon(
        defaultTypePresetBuilder: () -> TypePreset,
        sora2TypesRaw: String,
        runtimeVersion: Int,
        dynamicTypeResolverBuilder: () -> DynamicTypeResolver,
    ): TypeRegistry {
        val sora2TypeDefinitionsTree = rawTypesToTree(sora2TypesRaw)
        val sora2ParseResult = TypeDefinitionParser.parseNetworkVersioning(
            sora2TypeDefinitionsTree,
            defaultTypePresetBuilder.invoke(),
            runtimeVersion
        )
        if (sora2ParseResult.unknownTypes.isNotEmpty()) {
            FirebaseWrapper.log("BuildRuntimeSnapshot. ${sora2ParseResult.unknownTypes.size} unknown types are found")
        }
        return TypeRegistry(
            types = sora2ParseResult.typePreset,
            dynamicTypeResolver = dynamicTypeResolverBuilder.invoke()
        )
    }

    private sealed class MetadataSource {
        data class Cache(val replaceCache: Boolean) : MetadataSource()
        object SoraNet : MetadataSource()
    }
}
