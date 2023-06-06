/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.runtime

import com.google.gson.Gson
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.shared_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.shared_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.shared_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.shared_utils.runtime.definitions.registry.TypePreset
import jp.co.soramitsu.shared_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.shared_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.shared_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromByteArrayOrNull
import jp.co.soramitsu.shared_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.shared_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.shared_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.shared_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.executeAsync
import jp.co.soramitsu.shared_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.shared_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DEFAULT_TYPES_FILE = "default_types.json"
private const val SORA2_TYPES_FILE = "types_scalecodec_mobile.json"
private const val RUNTIME_METADATA_FILE = "sora2_metadata"
private const val RUNTIME_VERSION_PREF = "last_used_runtime_version"
private const val RUNTIME_VERSION_START = 1

@Singleton
class RuntimeManager @Inject constructor(
    private val fileManager: FileManager,
    private val gson: Gson,
    private val soraPreferences: SoraPreferences,
    private val socketService: SocketService,
    private val networkClient: SoramitsuNetworkClient,
    private val coroutineManager: CoroutineManager,
    private val soraConfigManager: SoraConfigManager,
) {

    private val mutex = Mutex()
    private var runtimeSnapshot: RuntimeSnapshot? = null
    private var prefix: Short = 69

    init {
        coroutineManager.applicationScope.launch {
            getRuntimeSnapshot()
        }
    }

    fun isAddressOk(address: String): Boolean =
        runCatching { address.toAccountId() }.getOrNull() != null &&
            SS58Encoder.extractAddressByte(address) == prefix

    fun toSoraAddress(byteArray: ByteArray): String = byteArray.toAddress(prefix)
    fun toSoraAddressOrNull(byteArray: ByteArray?): String? =
        runCatching { byteArray?.toAddress(prefix) }.getOrNull()

    suspend fun getRuntimeSnapshot(): RuntimeSnapshot {
        return runtimeSnapshot ?: mutex.withLock {
            runtimeSnapshot ?: createRuntimeSnapshot()
        }
    }

    suspend fun resetRuntimeVersion() {
        soraPreferences.putInt(RUNTIME_VERSION_PREF, 0)
    }

    private suspend fun createRuntimeSnapshot(): RuntimeSnapshot = withContext(coroutineManager.io) {
        var snapshot = initFromCache()
        snapshot = checkRuntimeVersion(snapshot)
        snapshot
    }

    private suspend fun initFromCache() =
        buildRuntimeSnapshot(
            getContentFromCache(RUNTIME_METADATA_FILE),
            MetadataSource.Cache(true),
            soraPreferences.getInt(
                RUNTIME_VERSION_PREF,
                RUNTIME_VERSION_START
            )
        )

    private fun getContentFromCache(fileName: String): String {
        val cache = fileManager.readInternalCacheFile(fileName)
        if (cache != null) return cache
        val asset = fileManager.readAssetFile(fileName)
        saveToCache(fileName, asset)
        return asset
    }

    private fun rawTypesToTree(raw: String) = gson.fromJson(raw, TypeDefinitionsTree::class.java)

    private suspend fun checkRuntimeVersion(snapshot: RuntimeSnapshot): RuntimeSnapshot {
        var result = snapshot
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
                result = buildRuntimeSnapshot(metadata, MetadataSource.SoraNet, runtimeVersion.specVersion)
                saveToCache(RUNTIME_METADATA_FILE, metadata)
                soraPreferences.putInt(RUNTIME_VERSION_PREF, runtimeVersion.specVersion)
            } catch (t: Throwable) {
                FirebaseWrapper.recordException(t)
            }
        }
        return result
    }

    private fun saveToCache(file: String, content: String) =
        fileManager.writeInternalCacheFile(file, content)

    private suspend fun buildRuntimeSnapshot(
        metadata: String,
        metadataSource: MetadataSource,
        runtimeVersion: Int,
    ): RuntimeSnapshot {
        val runtimeMetadataReader = RuntimeMetadataReader.read(metadata)
        val typeRegistry = when (metadataSource) {
            is MetadataSource.Cache -> {
                if (runtimeMetadataReader.metadataVersion < 14) {
                    val defaultTypesRaw =
                        getContentFromCache(DEFAULT_TYPES_FILE)
                    val sora2TypesRaw =
                        getContentFromCache(SORA2_TYPES_FILE)
                    buildTypeRegistry12(defaultTypesRaw, sora2TypesRaw, runtimeVersion)
                } else {
                    val sora2TypesRaw =
                        getContentFromCache(SORA2_TYPES_FILE)
                    buildTypeRegistry14(sora2TypesRaw, runtimeMetadataReader, runtimeVersion)
                }
            }
            is MetadataSource.SoraNet -> {
                val sora2Types = networkClient.get(soraConfigManager.getSubstrateTypesUrl())
                buildTypeRegistry14(sora2Types, runtimeMetadataReader, runtimeVersion).also {
                    saveToCache(SORA2_TYPES_FILE, sora2Types)
                }
            }
        }
        val runtimeMetadata =
            VersionedRuntimeBuilder.buildMetadata(runtimeMetadataReader, typeRegistry)
        val snapshot = RuntimeSnapshot(typeRegistry, runtimeMetadata)
        runtimeSnapshot = snapshot
        val valueConstant =
            snapshot.metadata.module(Pallete.SYSTEM.palletName).constants[Constants.SS58Prefix.constantName]
        prefix = (
            valueConstant?.type?.fromByteArrayOrNull(
                snapshot,
                valueConstant.value
            ) as? BigInteger
            )?.toShort() ?: 69
        FirebaseWrapper.log("Set Runtime Net ${metadataSource is MetadataSource.SoraNet}")
        return snapshot
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
            true,
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
            false,
        )
    }

    private fun buildTypeRegistryCommon(
        defaultTypePresetBuilder: () -> TypePreset,
        sora2TypesRaw: String,
        runtimeVersion: Int,
        dynamicTypeResolverBuilder: () -> DynamicTypeResolver,
        upto14: Boolean,
    ): TypeRegistry {
        val sora2TypeDefinitionsTree = rawTypesToTree(sora2TypesRaw)
        val sora2ParseResult = TypeDefinitionParser.parseNetworkVersioning(
            tree = sora2TypeDefinitionsTree,
            typePreset = defaultTypePresetBuilder.invoke(),
            currentRuntimeVersion = runtimeVersion,
            upto14 = upto14,
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
