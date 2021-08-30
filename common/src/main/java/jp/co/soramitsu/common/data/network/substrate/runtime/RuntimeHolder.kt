/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.substrate.runtime

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.orhanobut.logger.Logger
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.substrate.Constants
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.substratePreParsePreset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromByteArrayOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import java.io.File
import java.math.BigInteger

object RuntimeHolder {
    private var runtimeSnapshot: RuntimeSnapshot? = null
    private var netRuntimeVersionChecked: Boolean = false

    @Throws(IllegalArgumentException::class)
    fun getRuntime(): RuntimeSnapshot = requireNotNull(runtimeSnapshot) { "Runtime is null" }
    fun isInitialized() = runtimeSnapshot != null && netRuntimeVersionChecked
    var prefix: Byte = 69

    internal fun setRuntime(snapshot: RuntimeSnapshot, fromNet: Boolean) {
        runtimeSnapshot = snapshot
        FirebaseCrashlytics.getInstance().log("Set Runtime $fromNet")
        Logger.d("Runtime: set $fromNet")
        val valueConstant =
            getRuntime().metadata.module(Pallete.SYSTEM.palleteName).constants[Constants.SS58Prefix.constantName]
        prefix =
            (
            valueConstant?.type?.fromByteArrayOrNull(
                getRuntime(),
                valueConstant.value
            ) as? BigInteger
            )?.toByte()
            ?: 69
        if (fromNet) setNetRuntimeVersionChecked()
    }

    internal fun setNetRuntimeVersionChecked() {
        netRuntimeVersionChecked = true
    }
}

private const val DEFAULT_TYPES_FILE = "default_types.json"
private const val SORA2_TYPES_FILE = "sora2_types.json"
private const val RUNTIME_METADATA_FILE = "sora2_metadata"
private const val RUNTIME_VERSION_PREF = "last_used_runtime_version"
private const val SORA_VERSION_LAST_LAUNCH_PREF = "last_launch_version"
private const val RUNTIME_VERSION_START = 1

class RuntimeManager(
    private val fileManager: FileManager,
    private val gson: Gson,
    private val preferences: Preferences,
    private val socketService: SocketService,
    private val typesApi: SubstrateTypesApi,
) {

    @Synchronized
    suspend fun start() {
        Logger.d("Runtime: start")
        if (!RuntimeHolder.isInitialized()) {
            initFromCache()
            Logger.d("Runtime: init from cache")
            checkRuntimeVersion()
            Logger.d("Runtime: check runtime version")
        }
    }

    private fun initFromCache() {
        val replaceCache = preferences.getInt(SORA_VERSION_LAST_LAUNCH_PREF, 0).let { version ->
            (OptionsProvider.CURRENT_VERSION_CODE != version).also {
                Logger.d("Runtime: launch $version $it")
                if (it) preferences.putInt(
                    SORA_VERSION_LAST_LAUNCH_PREF,
                    OptionsProvider.CURRENT_VERSION_CODE
                )
            }
        }
        buildRuntimeSnapshot(
            getContentFromCache(RUNTIME_METADATA_FILE, replaceCache),
            getContentFromCache(DEFAULT_TYPES_FILE, replaceCache),
            getContentFromCache(SORA2_TYPES_FILE, replaceCache),
            false
        )
    }

    private fun getLastRuntimeVersion(): Int =
        if (preferences.contains(RUNTIME_VERSION_PREF)) {
            preferences.getInt(RUNTIME_VERSION_PREF, RUNTIME_VERSION_START)
        } else {
            preferences.putInt(RUNTIME_VERSION_PREF, RUNTIME_VERSION_START)
            RUNTIME_VERSION_START
        }

    private fun getContentFromCache(fileName: String, replaceCache: Boolean = false): String {
        val cache =
            File(fileManager.internalCacheDir, fileName).takeIf { it.exists() }?.run { readText() }
                ?.takeIf { it.isNotEmpty() }
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
        Logger.d("Runtime: spec ${runtimeVersion.specVersion}")
        if (runtimeVersion.specVersion > preferences.getInt(
                RUNTIME_VERSION_PREF,
                RUNTIME_VERSION_START
            )
        ) {
            FirebaseCrashlytics.getInstance()
                .log("New runtime version ${runtimeVersion.specVersion}")
            Logger.d("Runtime: get metadata")
            try {
                val metadata = socketService.executeAsync(
                    request = GetMetadataRequest,
                    mapper = pojo<String>().nonNull()
                )
                val defaultTypes = typesApi.getDefaultTypes()
                val sora2Types =
                    typesApi.getSora2Types(OptionsProvider.typesFilePath)
                preferences.putInt(RUNTIME_VERSION_PREF, runtimeVersion.specVersion)
                saveToCache(RUNTIME_METADATA_FILE, metadata)
                saveToCache(DEFAULT_TYPES_FILE, defaultTypes)
                saveToCache(SORA2_TYPES_FILE, sora2Types)
                buildRuntimeSnapshot(metadata, defaultTypes, sora2Types, true)
            } catch (t: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(t)
            }
        } else {
            RuntimeHolder.setNetRuntimeVersionChecked()
        }
    }

    private fun saveToCache(file: String, content: String) =
        File(fileManager.internalCacheDir, file).writeText(content)

    private fun buildRuntimeSnapshot(
        metadata: String,
        defaultTypesRaw: String,
        sora2TypesRaw: String,
        fromNet: Boolean,
    ) {
        val defaultTypes = rawTypesToTree(defaultTypesRaw)
        val sora2Types = rawTypesToTree(sora2TypesRaw)
        val runtimeMetadataSchema = RuntimeMetadataSchema.read(metadata)
        val types = TypeDefinitionParser.parseNetworkVersioning(
            sora2Types,
            TypeDefinitionParser.parseBaseDefinitions(
                defaultTypes,
                substratePreParsePreset()
            ).typePreset,
            getLastRuntimeVersion()
        )
        if (types.unknownTypes.isNotEmpty()) {
            FirebaseCrashlytics.getInstance()
                .log("BuildRuntimeSnapshot. ${types.unknownTypes.size} unknown types are found")
        }
        val typeRegistry = TypeRegistry(
            types = types.typePreset,
            dynamicTypeResolver = DynamicTypeResolver(DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension)
        )
        val runtimeMetadata = RuntimeMetadata(typeRegistry, runtimeMetadataSchema)
        val runtimeSnapshot = RuntimeSnapshot(typeRegistry, runtimeMetadata)
        RuntimeHolder.setRuntime(runtimeSnapshot, fromNet)
    }
}
