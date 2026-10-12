/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.substrate.runtime

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.xsubstrate.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.xsubstrate.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.TypePreset
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.xsubstrate.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromByteArrayOrNull
import jp.co.soramitsu.xsubstrate.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.xsubstrate.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.xsubstrate.scale.EncodableStruct
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DEFAULT_TYPES_FILE = "default_types.json"
private const val SORA2_TYPES_FILE = "types_scalecodec_mobile.json"
private const val RUNTIME_METADATA_FILE = "sora2_metadata"
private const val RUNTIME_VERSION_PREF = "last_used_runtime_version"
private const val RUNTIME_VERSION_START = 1
private const val SORA2_SS58_PREFIX: Short = 69
private const val RUNTIME_CACHE_SCHEMA_VERSION = 2
private const val RUNTIME_CACHE_MANIFEST_FILE = "sora2_runtime_cache_manifest_v2.json"
private const val RUNTIME_CACHE_GENERATION_PREFIX = "sora2_runtime_cache_v2"
private const val MAX_RUNTIME_CACHE_MANIFEST_BYTES = 16 * 1024
private const val RUNTIME_CODE_BINDING_SCHEMA_VERSION = 1
private const val RUNTIME_CODE_BINDING_FILE = "sora2_runtime_code_binding_v1.json"
private const val MAX_RUNTIME_CODE_BINDING_BYTES = 4 * 1024
private const val MAX_RUNTIME_METADATA_BYTES = 16 * 1024 * 1024
private const val MAX_RUNTIME_TYPES_BYTES = 8 * 1024 * 1024
private const val MAX_RUNTIME_GENERATION_SCAN = 32
private const val MAX_RUNTIME_GENERATION_PRUNE = 4
private const val MAX_REMOTE_TYPES_URL_LENGTH = 2_048
private const val MAX_IN_MEMORY_RECOVERY_RUNTIMES = 1
private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
private val RUNTIME_METADATA_HEX = Regex("^0x[0-9a-fA-F]+$")
private val BLOCK_HASH_HEX = Regex("^0x[0-9a-fA-F]{64}$")
private val RUNTIME_CACHE_GENERATION_FILE = Regex(
    "^${Regex.escape(RUNTIME_CACHE_GENERATION_PREFIX)}-[1-9][0-9]*-" +
        "[0-9a-f]{64}-[0-9a-f]{64}-[0-9a-f]{64}\\.(?:metadata|types\\.json)$"
)

internal open class Sora2RuntimeIdentityException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * The node answered a runtime RPC with a JSON-RPC error. The message stays the fixed code
 * `SORA2_RUNTIME_RPC_REMOTE_ERROR`; [code], [remoteMessage] and [remoteData] keep the node's
 * bounded error fields for callers that need to tell rejections apart (for example code 1010,
 * "Invalid Transaction", with data "Inability to pay some fees").
 */
internal class Sora2RuntimeRpcRemoteException(
    val code: Long,
    val remoteMessage: String,
    val remoteData: String?,
) : Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_REMOTE_ERROR")

/**
 * One immutable, finalized-chain runtime context used for both call encoding and signed-extension
 * version fields. A mutation must never obtain these values through separate live requests.
 */
data class Sora2MutationRuntimeContext(
    val snapshot: RuntimeSnapshot,
    val runtimeVersion: RuntimeVersion,
    val genesisHash: String,
    val finalizedHash: String,
    val metadataSha256: String,
    val typesSha256: String,
    /** Exact height of [finalizedHash], obtained through the same bounded RPC checkpoint. */
    val finalizedBlockNumber: Long = 0L,
)

/** The runtime identity journaled with a pending submission when it was signed. */
data class Sora2RecoveryRuntimeIdentity(
    val genesisHash: String,
    val specVersion: Int,
    val metadataSha256: String,
    val typesSha256: String,
)

/**
 * Lists the runtimes that unresolved pending submissions still need for status recovery. Their
 * runtime cache generations are never pruned, so a submission signed before a mainnet runtime
 * upgrade can still be decoded afterwards. Returns null when the set cannot be read; the runtime
 * cache is then not pruned at all.
 */
fun interface Sora2RecoveryRuntimeRetention {
    suspend fun retainedRecoveryRuntimes(): Set<Sora2RecoveryRuntimeIdentity>?
}

private data class RuntimeCandidate(
    val snapshot: RuntimeSnapshot,
    val prefix: Short,
    val genesisHash: String,
    val metadataSha256: String,
    val typesSha256: String,
    val runtimeVersion: Int,
    val sora2TypesToCache: String? = null,
    /** See [Sora2SignedExtensionAdmission]; not empty means this runtime cannot be signed for. */
    val unsupportedSignedExtensions: List<String> = emptyList(),
    /** Set when the metadata was just read from the chain together with its runtime code hash. */
    val codeBinding: RuntimeCodeBinding? = null,
)

/**
 * Says that the metadata identified by [metadataSha256] was read from the chain at a finalized
 * block whose runtime code (`:code`) had the hash [runtimeCodeHash], with the given versions.
 *
 * Metadata is produced by the runtime code and by nothing else, so while a finalized block still
 * reports the same code hash and versions, its metadata is this metadata and does not have to be
 * downloaded again (about 1.25 MB on mainnet). A runtime upgrade always replaces the code, which
 * changes the hash and ends the binding.
 */
internal data class RuntimeCodeBinding(
    @SerializedName("schemaVersion")
    val schemaVersion: Int,
    @SerializedName("genesisHash")
    val genesisHash: String?,
    @SerializedName("specVersion")
    val specVersion: Int,
    @SerializedName("transactionVersion")
    val transactionVersion: Int,
    @SerializedName("metadataSha256")
    val metadataSha256: String?,
    @SerializedName("runtimeCodeHash")
    val runtimeCodeHash: String?,
) {
    fun isWellFormed(): Boolean =
        schemaVersion == RUNTIME_CODE_BINDING_SCHEMA_VERSION &&
            specVersion > 0 &&
            transactionVersion >= 0 &&
            genesisHash != null && BLOCK_HASH_HEX.matches(genesisHash) &&
            metadataSha256 != null && SHA256_HEX.matches(metadataSha256) &&
            runtimeCodeHash != null && BLOCK_HASH_HEX.matches(runtimeCodeHash)

    /** True when this binding is about exactly this snapshot and this live checkpoint. */
    fun covers(
        snapshotGenesisHash: String?,
        snapshotSpecVersion: Int?,
        snapshotMetadataSha256: String?,
        liveVersion: RuntimeVersion,
        liveRuntimeCodeHash: String,
    ): Boolean =
        isWellFormed() &&
            snapshotGenesisHash != null &&
            genesisHash.equals(snapshotGenesisHash, ignoreCase = true) &&
            specVersion == snapshotSpecVersion &&
            metadataSha256 == snapshotMetadataSha256 &&
            specVersion == liveVersion.specVersion &&
            transactionVersion == liveVersion.transactionVersion &&
            runtimeCodeHash.equals(liveRuntimeCodeHash, ignoreCase = true)
}

internal data class RuntimeCacheManifest(
    @SerializedName("schemaVersion")
    val schemaVersion: Int,
    @SerializedName("generation")
    val generation: String?,
    @SerializedName("runtimeVersion")
    val runtimeVersion: Int,
    @SerializedName("genesisHash")
    val genesisHash: String?,
    @SerializedName("metadataFile")
    val metadataFile: String?,
    @SerializedName("metadataSha256")
    val metadataSha256: String?,
    @SerializedName("typesFile")
    val typesFile: String?,
    @SerializedName("typesSha256")
    val typesSha256: String?,
)

internal data class PublishedRuntimeCache(
    val metadata: String,
    val sora2Types: String,
    val runtimeVersion: Int,
    val genesisHash: String,
    val metadataSha256: String,
    val typesSha256: String,
)

private data class RuntimeCacheCoordinates(
    val metadataFile: String,
    val typesFile: String,
)

internal enum class RuntimeCacheReadState {
    ABSENT,
    PRESENT,
    UNREADABLE,
}

private data class RuntimeCacheReadResult(
    val value: String?,
    val state: RuntimeCacheReadState,
)

internal fun requireRuntimePayloadWithinLimit(
    content: String,
    maxBytes: Int,
    errorCode: String,
): String {
    if (maxBytes <= 0) {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_PAYLOAD_LIMIT_INVALID")
    }
    var index = 0
    var encodedBytes = 0L
    while (index < content.length) {
        val character = content[index]
        val width = when {
            character.code <= 0x7f -> 1
            character.code <= 0x7ff -> 2
            Character.isHighSurrogate(character) -> {
                if (
                    index + 1 >= content.length ||
                    !Character.isLowSurrogate(content[index + 1])
                ) {
                    throw Sora2RuntimeIdentityException("${errorCode}_UTF8_INVALID")
                }
                index += 1
                4
            }
            Character.isLowSurrogate(character) ->
                throw Sora2RuntimeIdentityException("${errorCode}_UTF8_INVALID")
            else -> 3
        }
        encodedBytes += width
        if (encodedBytes > maxBytes.toLong()) {
            throw Sora2RuntimeIdentityException(errorCode)
        }
        index += 1
    }
    return content
}

internal fun mayAdmitLegacyRuntimeCache(
    metadataState: RuntimeCacheReadState,
    typesState: RuntimeCacheReadState,
): Boolean =
    metadataState == RuntimeCacheReadState.PRESENT &&
        typesState == RuntimeCacheReadState.PRESENT

internal fun selectUnreachableRuntimeGenerationFiles(
    fileNames: List<String>,
    activeMetadataFile: String,
    activeTypesFile: String,
    maxFiles: Int,
    retainedFiles: Set<String> = emptySet(),
): List<String> {
    require(maxFiles in 0..MAX_RUNTIME_GENERATION_PRUNE) {
        "SORA2_RUNTIME_CACHE_PRUNE_LIMIT_INVALID"
    }
    val activeFiles = setOf(activeMetadataFile, activeTypesFile) + retainedFiles
    return fileNames.asSequence()
        .filter(RUNTIME_CACHE_GENERATION_FILE::matches)
        .filterNot(activeFiles::contains)
        .distinct()
        .sorted()
        .take(maxFiles)
        .toList()
}

internal fun createRuntimeCacheManifest(
    metadata: String,
    sora2Types: String,
    runtimeVersion: Int,
    genesisHash: String,
): RuntimeCacheManifest {
    require(runtimeVersion > 0) { "SORA2_RUNTIME_CACHE_VERSION_INVALID" }
    requireRuntimePayloadWithinLimit(
        metadata,
        MAX_RUNTIME_METADATA_BYTES,
        "SORA2_RUNTIME_METADATA_TOO_LARGE",
    )
    requireRuntimePayloadWithinLimit(
        sora2Types,
        MAX_RUNTIME_TYPES_BYTES,
        "SORA2_RUNTIME_TYPES_TOO_LARGE",
    )
    val canonicalGenesisHash = genesisHash.requireCanonicalBlockHash(
        "SORA2_RUNTIME_CACHE_GENESIS_INVALID"
    )
    val metadataSha256 = metadata.runtimeMetadataSha256()
    val typesSha256 = sora2Types.rawSha256()
    val generation = runtimeCacheGeneration(
        runtimeVersion,
        canonicalGenesisHash,
        metadataSha256,
        typesSha256,
    )
    return RuntimeCacheManifest(
        schemaVersion = RUNTIME_CACHE_SCHEMA_VERSION,
        generation = generation,
        runtimeVersion = runtimeVersion,
        genesisHash = canonicalGenesisHash,
        metadataFile = "$generation.metadata",
        metadataSha256 = metadataSha256,
        typesFile = "$generation.types.json",
        typesSha256 = typesSha256,
    )
}

internal fun requirePublishedRuntimeCache(
    manifest: RuntimeCacheManifest,
    metadata: String?,
    sora2Types: String?,
): PublishedRuntimeCache {
    requireRuntimeCacheCoordinates(manifest)
    if (metadata == null) {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_METADATA_MISSING")
    }
    if (sora2Types == null) {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_TYPES_MISSING")
    }
    requireRuntimePayloadWithinLimit(
        metadata,
        MAX_RUNTIME_METADATA_BYTES,
        "SORA2_RUNTIME_METADATA_TOO_LARGE",
    )
    requireRuntimePayloadWithinLimit(
        sora2Types,
        MAX_RUNTIME_TYPES_BYTES,
        "SORA2_RUNTIME_TYPES_TOO_LARGE",
    )
    if (metadata.runtimeMetadataSha256() != manifest.metadataSha256) {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_METADATA_HASH_MISMATCH")
    }
    if (sora2Types.rawSha256() != manifest.typesSha256) {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_TYPES_HASH_MISMATCH")
    }
    return PublishedRuntimeCache(
        metadata = metadata,
        sora2Types = sora2Types,
        runtimeVersion = manifest.runtimeVersion,
        genesisHash = checkNotNull(manifest.genesisHash),
        metadataSha256 = checkNotNull(manifest.metadataSha256),
        typesSha256 = checkNotNull(manifest.typesSha256),
    )
}

private fun requireRuntimeCacheCoordinates(manifest: RuntimeCacheManifest): RuntimeCacheCoordinates {
    val metadataSha256 = manifest.metadataSha256
    val typesSha256 = manifest.typesSha256
    val genesisHash = try {
        manifest.genesisHash?.requireCanonicalBlockHash(
            "SORA2_RUNTIME_CACHE_GENESIS_INVALID"
        )
    } catch (_: Sora2RuntimeIdentityException) {
        null
    }
    if (
        manifest.schemaVersion != RUNTIME_CACHE_SCHEMA_VERSION ||
        manifest.runtimeVersion <= 0 ||
        genesisHash == null ||
        metadataSha256 == null ||
        !SHA256_HEX.matches(metadataSha256) ||
        typesSha256 == null ||
        !SHA256_HEX.matches(typesSha256)
    ) {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_MANIFEST_INVALID")
    }
    val expectedGeneration = runtimeCacheGeneration(
        manifest.runtimeVersion,
        genesisHash,
        metadataSha256,
        typesSha256,
    )
    if (manifest.generation != expectedGeneration) {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_GENERATION_MISMATCH")
    }
    val expectedMetadataFile = "$expectedGeneration.metadata"
    val expectedTypesFile = "$expectedGeneration.types.json"
    if (
        manifest.metadataFile != expectedMetadataFile ||
        manifest.typesFile != expectedTypesFile
    ) {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_FILE_IDENTITY_MISMATCH")
    }
    return RuntimeCacheCoordinates(expectedMetadataFile, expectedTypesFile)
}

private fun runtimeCacheGeneration(
    runtimeVersion: Int,
    genesisHash: String,
    metadataSha256: String,
    typesSha256: String,
): String =
    "$RUNTIME_CACHE_GENERATION_PREFIX-$runtimeVersion-${genesisHash.drop(2)}-$metadataSha256-$typesSha256"

/**
 * Content-addressed cache file names of the generation that holds [identity], or null when the
 * journaled identity is not well formed (such a row can never match a cache generation).
 */
internal fun runtimeCacheGenerationFilesOrNull(
    identity: Sora2RecoveryRuntimeIdentity,
): Pair<String, String>? {
    val genesisHash = try {
        identity.genesisHash.requireCanonicalBlockHash("SORA2_RECOVERY_GENESIS_INVALID")
    } catch (_: Sora2RuntimeIdentityException) {
        return null
    }
    if (
        identity.specVersion <= 0 ||
        !SHA256_HEX.matches(identity.metadataSha256) ||
        !SHA256_HEX.matches(identity.typesSha256)
    ) {
        return null
    }
    val generation = runtimeCacheGeneration(
        identity.specVersion,
        genesisHash,
        identity.metadataSha256,
        identity.typesSha256,
    )
    return "$generation.metadata" to "$generation.types.json"
}

internal fun String.requireCanonicalBlockHash(errorCode: String): String {
    if (!BLOCK_HASH_HEX.matches(this)) {
        throw Sora2RuntimeIdentityException(errorCode)
    }
    return lowercase()
}

internal fun String.runtimeMetadataSha256(): String {
    val canonical = trimEnd('\r', '\n')
    if (!RUNTIME_METADATA_HEX.matches(canonical) || (canonical.length - 2) % 2 != 0) {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_METADATA_INVALID")
    }
    return "${canonical.lowercase()}\n".rawSha256()
}

internal fun String.rawSha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

internal fun legacyRuntimeCacheMatchesBundled(
    legacyMetadata: String?,
    legacyTypes: String?,
    bundledMetadata: String,
    bundledTypes: String,
): Boolean {
    // Validate the bundled identity outside the recoverable legacy parse. A corrupt packaged
    // fixture must fail closed, while malformed cache bytes from a pre-manifest app version are
    // merely ignored as non-authoritative.
    val bundledMetadataSha256 = bundledMetadata.runtimeMetadataSha256()
    val bundledTypesSha256 = bundledTypes.rawSha256()
    if (legacyMetadata == null || legacyTypes == null) return false
    return try {
        legacyMetadata.runtimeMetadataSha256() == bundledMetadataSha256 &&
            legacyTypes.rawSha256() == bundledTypesSha256
    } catch (_: Sora2RuntimeIdentityException) {
        false
    }
}

internal fun requireSora2Ss58Prefix(observed: BigInteger?): Short {
    if (observed != BigInteger.valueOf(SORA2_SS58_PREFIX.toLong())) {
        throw Sora2RuntimeIdentityException("SORA2_SS58_PREFIX_MISMATCH")
    }
    return SORA2_SS58_PREFIX
}

internal fun requireMutationRuntimeIdentity(
    candidateSpecVersion: Int,
    candidateGenesisHash: String,
    candidateMetadataSha256: String,
    liveSpecVersion: Int,
    liveGenesisHash: String,
    liveMetadataSha256: String,
) {
    if (candidateSpecVersion != liveSpecVersion) {
        throw Sora2RuntimeIdentityException("SORA2_MUTATION_RUNTIME_VERSION_MISMATCH")
    }
    if (!candidateGenesisHash.equals(liveGenesisHash, ignoreCase = true)) {
        throw Sora2RuntimeIdentityException("SORA2_MUTATION_GENESIS_MISMATCH")
    }
    if (candidateMetadataSha256 != liveMetadataSha256) {
        throw Sora2RuntimeIdentityException("SORA2_MUTATION_METADATA_MISMATCH")
    }
}

internal fun requireCanonicalProductionGenesis(
    observedGenesisHash: String,
    enforceCanonicalMainnet: Boolean,
): String {
    val canonical = observedGenesisHash.requireCanonicalBlockHash("SORA2_GENESIS_HASH_INVALID")
    if (
        enforceCanonicalMainnet &&
        !canonical.equals(Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH, ignoreCase = true)
    ) {
        throw Sora2RuntimeIdentityException("SORA2_GENESIS_HASH_MISMATCH")
    }
    return canonical
}

@Singleton
class RuntimeManager @Inject constructor(
    private val fileManager: FileManager,
    private val gson: Gson,
    private val soraPreferences: SoraPreferences,
    private val sora2RuntimeRpcClient: Sora2BoundedRuntimeRpcClient,
    private val boundedHttpTextClient: BoundedHttpTextClient,
    private val coroutineManager: CoroutineManager,
    private val soraConfigManager: SoraConfigManager,
    private val recoveryRuntimeRetention: Sora2RecoveryRuntimeRetention,
) {

    private val mutex = Mutex()
    @Volatile
    private var runtimeSnapshot: RuntimeSnapshot? = null
    @Volatile
    private var runtimeMetadataSha256: String? = null
    @Volatile
    private var runtimeTypesSha256: String? = null
    @Volatile
    private var runtimeSpecVersion: Int? = null
    @Volatile
    private var runtimeGenesisHash: String? = null
    @Volatile
    private var runtimeUnsupportedSignedExtensions: List<String> = emptyList()
    private var prefix: Short = SORA2_SS58_PREFIX

    /** What is known about the active snapshot's runtime code. Guarded by [mutex]. */
    private var runtimeCodeBinding: RuntimeCodeBinding? = null

    /** False until the saved binding was looked at for the active snapshot. Guarded by [mutex]. */
    private var runtimeCodeBindingFileRead = false

    private val pruneRequested = AtomicBoolean(false)

    /** Recovery runtimes rebuilt from the network, guarded by [mutex]. Newest last. */
    private val recoveryRuntimes = LinkedHashMap<Sora2RecoveryRuntimeIdentity, RuntimeSnapshot>()

    init {
        coroutineManager.applicationScope.launch {
            prefetchRuntimeForDisplay(onFailure = FirebaseWrapper::recordException) { getRuntimeSnapshot() }
        }
    }

    fun isAddressOk(address: String): Boolean =
        runCatching { address.toAccountId() }.getOrNull() != null &&
            SS58Encoder.extractAddressByte(address) == prefix

    fun soraPublicKeyOrNull(address: String): ByteArray? =
        runCatching {
            address.toAccountId().takeIf {
                it.size == 32 && SS58Encoder.extractAddressByte(address) == prefix
            }
        }.getOrNull()

    fun toSoraAddress(byteArray: ByteArray): String = byteArray.toAddress(prefix)
    fun toSoraAddressOrNull(byteArray: ByteArray?): String? =
        runCatching { byteArray?.toAddress(prefix) }.getOrNull()

    suspend fun getRuntimeSnapshot(): RuntimeSnapshot {
        runtimeSnapshot?.let { return it }
        val snapshot = mutex.withLock {
            runtimeSnapshot ?: createRuntimeSnapshot()
        }
        pruneRuntimeCacheIfRequested()
        return snapshot
    }

    /**
     * Hash of the canonical lowercase metadata hex used to construct [getRuntimeSnapshot].
     * Mutation coordinators compare this with both the reviewed fixture and the current node.
     */
    suspend fun getRuntimeMetadataSha256(): String {
        getRuntimeSnapshot()
        return checkNotNull(runtimeMetadataSha256) { "RUNTIME_METADATA_IDENTITY_MISSING" }
    }

    /** Hash of the exact mobile type-definition bytes used to construct the active snapshot. */
    suspend fun getRuntimeTypesSha256(): String {
        getRuntimeSnapshot()
        return checkNotNull(runtimeTypesSha256) { "RUNTIME_TYPES_IDENTITY_MISSING" }
    }

    suspend fun getRuntimeSpecVersion(): Int {
        getRuntimeSnapshot()
        return checkNotNull(runtimeSpecVersion) { "RUNTIME_VERSION_IDENTITY_MISSING" }
    }

    /**
     * Resolves metadata and runtime version at one finalized block. Every failure after a live
     * checkpoint is known is fatal to mutation preparation; the read-only cached snapshot remains
     * available, but it is never returned as mutation authority.
     */
    suspend fun getMutationRuntimeContext(): Sora2MutationRuntimeContext {
        val context = mutex.withLock {
            withContext(coroutineManager.io) {
                createMutationRuntimeContextLocked()
            }
        }
        pruneRuntimeCacheIfRequested()
        return context
    }

    /**
     * Finds the runtime snapshot that signed a pending submission, for status-only recovery.
     * A snapshot is admitted only when genesis, spec version, metadata hash and types hash all
     * equal the values journaled with the submission. Sources, in order:
     *
     * 1. the active snapshot (normally the live runtime the submission was signed with);
     * 2. a snapshot rebuilt earlier in this process (source 5);
     * 3. the content-addressed runtime cache generation named by those values. Generations that
     *    unresolved submissions need are kept when the cache is pruned
     *    ([Sora2RecoveryRuntimeRetention]), so a mainnet runtime upgrade does not remove them;
     * 4. the packaged fixture;
     * 5. with [eraBirthBlockHash] (the finalized block the submission's runtime was read at): the
     *    runtime version and metadata at that block, from the reviewed endpoint or, once that node
     *    has discarded the block's state, the status archive endpoint. The metadata is admitted
     *    only when its SHA-256 equals [metadataSha256] and the version equals [specVersion] and
     *    [transactionVersion]; the type aliases are the cached, packaged or remote-config file
     *    whose SHA-256 equals [typesSha256]. The rebuilt generation is cached for later passes.
     *
     * The operating system may clear the cache directory at any time, which is why source 5
     * exists. When no source matches, the result is null and the journal row stays unresolved; it
     * is never pruned or treated as failed.
     */
    internal suspend fun getReviewedRecoveryRuntimeSnapshotOrNull(
        genesisHash: String,
        specVersion: Int,
        transactionVersion: Int,
        metadataSha256: String,
        typesSha256: String,
        eraBirthBlockHash: String? = null,
    ): RuntimeSnapshot? {
        val identity = Sora2RecoveryRuntimeIdentity(
            genesisHash = genesisHash.lowercase(),
            specVersion = specVersion,
            metadataSha256 = metadataSha256,
            typesSha256 = typesSha256,
        )
        val local = mutex.withLock {
            withContext(coroutineManager.io) {
                findLocalRecoveryRuntimeLocked(identity, transactionVersion)
            }
        }
        if (local != null || eraBirthBlockHash == null) return local
        // The download and parse run outside the runtime lock, so a recovery pass never delays a
        // fee quote or a signature.
        val rebuilt = withContext(coroutineManager.io) {
            rebuildRecoveryRuntimeOrNull(identity, transactionVersion, eraBirthBlockHash)
        } ?: return null
        return mutex.withLock {
            withContext(coroutineManager.io) {
                rememberRecoveryRuntimeLocked(identity, rebuilt)
            }
        }
    }

    private fun Sora2RecoveryRuntimeIdentity.matches(candidate: RuntimeCandidate): Boolean =
        candidate.genesisHash.equals(genesisHash, ignoreCase = true) &&
            candidate.runtimeVersion == specVersion &&
            candidate.metadataSha256 == metadataSha256 &&
            candidate.typesSha256 == typesSha256

    private suspend fun findLocalRecoveryRuntimeLocked(
        identity: Sora2RecoveryRuntimeIdentity,
        transactionVersion: Int,
    ): RuntimeSnapshot? {
        val active = runtimeSnapshot
        if (
            active != null &&
            runtimeGenesisHash.equals(identity.genesisHash, ignoreCase = true) &&
            runtimeSpecVersion == identity.specVersion &&
            runtimeMetadataSha256 == identity.metadataSha256 &&
            runtimeTypesSha256 == identity.typesSha256
        ) {
            return active
        }
        recoveryRuntimes[identity]?.let { return it }
        loadRetainedRecoveryCandidateOrNull(identity)?.let { return it.snapshot }
        // Parse the packaged pair only when it can match.
        if (
            identity.specVersion != Sora2RuntimeContract.SPEC_VERSION ||
            transactionVersion != Sora2RuntimeContract.TRANSACTION_VERSION ||
            identity.metadataSha256 != Sora2RuntimeContract.METADATA_FILE_SHA256 ||
            identity.typesSha256 != Sora2RuntimeContract.TYPES_FILE_SHA256
        ) {
            return null
        }
        val fixture = initFromBundledAssets()
        return fixture.snapshot.takeIf { identity.matches(fixture) }
    }

    /** Reads the exact content-addressed generation, whether or not the manifest points to it. */
    private suspend fun loadRetainedRecoveryCandidateOrNull(
        identity: Sora2RecoveryRuntimeIdentity,
    ): RuntimeCandidate? {
        val (metadataFile, typesFile) = runtimeCacheGenerationFilesOrNull(identity) ?: return null
        return try {
            val metadata = fileManager.readInternalCacheFileAtomically(
                metadataFile,
                MAX_RUNTIME_METADATA_BYTES,
            ) ?: return null
            val types = fileManager.readInternalCacheFileAtomically(
                typesFile,
                MAX_RUNTIME_TYPES_BYTES,
            ) ?: return null
            if (
                metadata.runtimeMetadataSha256() != identity.metadataSha256 ||
                types.rawSha256() != identity.typesSha256
            ) {
                return null
            }
            buildRuntimeCandidate(
                metadata,
                MetadataSource.Cache(types),
                identity.specVersion,
                identity.genesisHash,
            ).takeIf { identity.matches(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            FirebaseWrapper.recordException(error)
            null
        }
    }

    private class RebuiltRecoveryRuntime(
        val candidate: RuntimeCandidate,
        val metadata: String,
        val sora2Types: String,
    )

    private suspend fun rebuildRecoveryRuntimeOrNull(
        identity: Sora2RecoveryRuntimeIdentity,
        transactionVersion: Int,
        eraBirthBlockHash: String,
    ): RebuiltRecoveryRuntime? {
        val (_, typesFile) = runtimeCacheGenerationFilesOrNull(identity) ?: return null
        return try {
            val version = sora2RuntimeRpcClient.getHistoricRuntimeVersionForStatus(
                eraBirthBlockHash
            )
            if (
                version.specVersion != identity.specVersion ||
                version.transactionVersion != transactionVersion
            ) {
                return null
            }
            val metadata = requireRuntimePayloadWithinLimit(
                sora2RuntimeRpcClient.getHistoricMetadataForStatus(eraBirthBlockHash),
                MAX_RUNTIME_METADATA_BYTES,
                "SORA2_RUNTIME_METADATA_TOO_LARGE",
            )
            if (metadata.runtimeMetadataSha256() != identity.metadataSha256) return null
            val types = recoveryTypeCandidates(typesFile)
                .firstOrNull { it.rawSha256() == identity.typesSha256 }
                ?: loadRemoteSora2TypesOrNull()?.takeIf { it.rawSha256() == identity.typesSha256 }
                ?: return null
            val candidate = buildRuntimeCandidate(
                metadata,
                MetadataSource.Cache(types),
                identity.specVersion,
                identity.genesisHash,
            )
            if (!identity.matches(candidate)) return null
            RebuiltRecoveryRuntime(candidate, metadata, types)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            FirebaseWrapper.recordException(error)
            null
        }
    }

    /** Local type-alias files to try before the remote-config file is fetched. */
    private fun recoveryTypeCandidates(cachedTypesFile: String): Sequence<String> = sequence {
        val cached = try {
            fileManager.readInternalCacheFileAtomically(cachedTypesFile, MAX_RUNTIME_TYPES_BYTES)
        } catch (error: Exception) {
            FirebaseWrapper.recordException(error)
            null
        }
        cached?.let { yield(it) }
        yield(fileManager.readAssetFile(SORA2_TYPES_FILE, MAX_RUNTIME_TYPES_BYTES))
    }

    private suspend fun rememberRecoveryRuntimeLocked(
        identity: Sora2RecoveryRuntimeIdentity,
        rebuilt: RebuiltRecoveryRuntime,
    ): RuntimeSnapshot {
        val (metadataFile, typesFile) = checkNotNull(runtimeCacheGenerationFilesOrNull(identity))
        try {
            // Content-addressed and outside the manifest: later passes in a new process find it
            // through source 3, and pruning keeps it while the submission is unresolved.
            fileManager.writeInternalCacheFileAtomically(
                metadataFile,
                rebuilt.metadata,
                MAX_RUNTIME_METADATA_BYTES,
            )
            fileManager.writeInternalCacheFileAtomically(
                typesFile,
                rebuilt.sora2Types,
                MAX_RUNTIME_TYPES_BYTES,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            FirebaseWrapper.recordException(error)
        }
        while (recoveryRuntimes.size >= MAX_IN_MEMORY_RECOVERY_RUNTIMES) {
            recoveryRuntimes.remove(recoveryRuntimes.keys.first())
        }
        recoveryRuntimes[identity] = rebuilt.candidate.snapshot
        return rebuilt.candidate.snapshot
    }

    suspend fun resetRuntimeVersion() {
        soraPreferences.putInt(RUNTIME_VERSION_PREF, 0)
    }

    private suspend fun createRuntimeSnapshot(): RuntimeSnapshot =
        withContext(coroutineManager.io) {
            val candidate = checkRuntimeVersion(initFromCache())
            publishRuntimeCandidate(candidate)
            // A refresh for reading already downloaded the live metadata. Remember what it
            // belongs to, so the first fee quote does not download it a second time.
            candidate.codeBinding?.let(::rememberRuntimeCodeBindingLocked)
            candidate.snapshot
        }

    private fun publishRuntimeCandidate(candidate: RuntimeCandidate) {
        // The volatile snapshot is the publication barrier. No caller can observe a candidate
        // until its prefix and complete chain/runtime/type identity have been published.
        prefix = candidate.prefix
        runtimeMetadataSha256 = candidate.metadataSha256
        runtimeTypesSha256 = candidate.typesSha256
        runtimeSpecVersion = candidate.runtimeVersion
        runtimeGenesisHash = candidate.genesisHash
        runtimeUnsupportedSignedExtensions = candidate.unsupportedSignedExtensions
        // A binding belongs to one snapshot. The saved one is looked at again for this one.
        runtimeCodeBinding = null
        runtimeCodeBindingFileRead = false
        runtimeSnapshot = candidate.snapshot
    }

    /**
     * The binding of the active snapshot, from memory or, once per snapshot, from the file the
     * last download left. A missing, unreadable or foreign file only means one more download.
     */
    private fun activeRuntimeCodeBindingLocked(): RuntimeCodeBinding? {
        runtimeCodeBinding?.let { return it }
        if (runtimeCodeBindingFileRead) return null
        runtimeCodeBindingFileRead = true
        val saved = try {
            fileManager.readInternalCacheFileAtomically(
                RUNTIME_CODE_BINDING_FILE,
                MAX_RUNTIME_CODE_BINDING_BYTES,
            )?.let { gson.fromJson(it, RuntimeCodeBinding::class.java) }
        } catch (error: Exception) {
            FirebaseWrapper.recordException(error)
            null
        }
        return saved
            ?.takeIf { binding ->
                binding.isWellFormed() &&
                    binding.genesisHash.equals(runtimeGenesisHash, ignoreCase = true) &&
                    binding.specVersion == runtimeSpecVersion &&
                    binding.metadataSha256 == runtimeMetadataSha256
            }
            ?.also { runtimeCodeBinding = it }
    }

    /** Remembers [binding] for the active snapshot and, best effort, for the next process. */
    private fun rememberRuntimeCodeBindingLocked(binding: RuntimeCodeBinding) {
        if (runtimeCodeBinding == binding) return
        runtimeCodeBinding = binding
        runtimeCodeBindingFileRead = true
        try {
            fileManager.writeInternalCacheFileAtomically(
                RUNTIME_CODE_BINDING_FILE,
                gson.toJson(binding),
                MAX_RUNTIME_CODE_BINDING_BYTES,
            )
        } catch (error: Exception) {
            // Only an optimization for the next process; a full disk must not stop a transfer.
            FirebaseWrapper.recordException(error)
        }
    }

    /**
     * The runtime code hash at an admitted finalized block, or null when no reviewed endpoint
     * gives one. Null is never an error for the caller: the metadata is then downloaded and
     * compared by its own hash, as before this shortcut existed.
     */
    private suspend fun runtimeCodeHashOrNull(finalizedHash: String): String? = try {
        sora2RuntimeRpcClient.getRuntimeCodeHashAtFinalized(finalizedHash)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        FirebaseWrapper.recordException(error)
        null
    }

    private suspend fun createMutationRuntimeContextLocked(): Sora2MutationRuntimeContext {
        // Every value that authorizes a mutation (this runtime context, the nonce, the fee
        // quote) and the single delivery of the signed bytes go through the reviewed HTTPS
        // endpoints (Sora2BoundedRuntimeRpcClient; two hosts, one set of checks). The node the
        // app's WebSocket shows balances from is not involved, so an automatic or manual switch
        // to another node never blocks sending.
        // Production signs against whatever runtime the canonical mainnet is running now. The
        // chain is pinned by genesis hash and the SS58 prefix; the runtime version, transaction
        // version and metadata come from the node at one finalized block and must match the
        // snapshot that encodes the call (requireMutationRuntimeIdentity below). No runtime
        // version is hard-coded here: SORA mainnet upgrades its runtime often, and a pinned
        // version stops every transfer, swap and claim until an app update ships.
        val enforceCanonicalMainnet = !BuildUtils.isFlavors(
            Flavor.DEVELOP,
            Flavor.SORALUTION,
        )
        val liveIdentity = sora2RuntimeRpcClient.getFinalizedIdentity()
        val liveGenesisHash = liveIdentity.genesisHash.let { observed ->
            requireCanonicalProductionGenesis(
                observedGenesisHash = observed,
                enforceCanonicalMainnet = enforceCanonicalMainnet,
            )
        }
        val finalizedHash = liveIdentity.finalizedHash.requireCanonicalBlockHash(
            "SORA2_FINALIZED_HASH_INVALID"
        )
        val liveRuntimeVersion = liveIdentity.runtimeVersion

        // Metadata is downloaded once per runtime, not once per fee quote or signature. The
        // runtime code hash at this finalized block is a 32-byte answer; while it and both
        // versions equal what the active snapshot's metadata was read for, that metadata is the
        // live metadata. Any difference, or no answer, falls through to the download below.
        val liveRuntimeCodeHash = runtimeCodeHashOrNull(finalizedHash)
        val activeSnapshot = runtimeSnapshot
        val activeMetadataSha256 = runtimeMetadataSha256
        val activeTypesSha256 = runtimeTypesSha256
        if (
            activeSnapshot != null &&
            activeMetadataSha256 != null &&
            activeTypesSha256 != null &&
            liveRuntimeCodeHash != null &&
            activeRuntimeCodeBindingLocked()?.covers(
                snapshotGenesisHash = runtimeGenesisHash,
                snapshotSpecVersion = runtimeSpecVersion,
                snapshotMetadataSha256 = activeMetadataSha256,
                liveVersion = liveRuntimeVersion,
                liveRuntimeCodeHash = liveRuntimeCodeHash,
            ) == true &&
            runtimeGenesisHash == liveGenesisHash
        ) {
            requireSignedExtensionsSupported(runtimeUnsupportedSignedExtensions)
            return Sora2MutationRuntimeContext(
                snapshot = activeSnapshot,
                runtimeVersion = liveRuntimeVersion,
                genesisHash = liveGenesisHash,
                finalizedHash = finalizedHash,
                metadataSha256 = activeMetadataSha256,
                typesSha256 = activeTypesSha256,
                finalizedBlockNumber = liveIdentity.finalizedBlockNumber,
            )
        }

        val liveMetadata = sora2RuntimeRpcClient.getMetadataAtFinalized(finalizedHash)
            .let { metadata ->
                requireRuntimePayloadWithinLimit(
                    metadata,
                    MAX_RUNTIME_METADATA_BYTES,
                    "SORA2_RUNTIME_METADATA_TOO_LARGE",
                )
            }
        val liveMetadataSha256 = liveMetadata.runtimeMetadataSha256()

        val activeMatchesCheckpoint =
            activeSnapshot != null &&
                runtimeSpecVersion == liveRuntimeVersion.specVersion &&
                runtimeGenesisHash == liveGenesisHash &&
                runtimeMetadataSha256 == liveMetadataSha256 &&
                runtimeTypesSha256 != null
        val candidate = if (activeMatchesCheckpoint) {
            RuntimeCandidate(
                snapshot = checkNotNull(activeSnapshot),
                prefix = prefix,
                genesisHash = liveGenesisHash,
                metadataSha256 = liveMetadataSha256,
                typesSha256 = checkNotNull(runtimeTypesSha256),
                runtimeVersion = liveRuntimeVersion.specVersion,
                unsupportedSignedExtensions = runtimeUnsupportedSignedExtensions,
            )
        } else {
            buildLiveRuntimeCandidate(
                metadata = liveMetadata,
                runtimeVersion = liveRuntimeVersion.specVersion,
                genesisHash = liveGenesisHash,
            )
        }

        requireMutationRuntimeIdentity(
            candidateSpecVersion = candidate.runtimeVersion,
            candidateGenesisHash = candidate.genesisHash,
            candidateMetadataSha256 = candidate.metadataSha256,
            liveSpecVersion = liveRuntimeVersion.specVersion,
            liveGenesisHash = liveGenesisHash,
            liveMetadataSha256 = liveMetadataSha256,
        )

        if (!activeMatchesCheckpoint) {
            val refreshedTypes = checkNotNull(candidate.sora2TypesToCache) {
                "SORA2_RUNTIME_TYPES_NOT_FETCHED"
            }
            // Signing needs only the validated candidate. Saving it is an optimization for the
            // next process; a full disk must not stop a transfer.
            if (
                publishRuntimeCacheBestEffort(
                    metadata = liveMetadata,
                    sora2Types = refreshedTypes,
                    runtimeVersion = liveRuntimeVersion.specVersion,
                    genesisHash = liveGenesisHash,
                )
            ) {
                try {
                    soraPreferences.putInt(RUNTIME_VERSION_PREF, liveRuntimeVersion.specVersion)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    FirebaseWrapper.recordException(error)
                }
            }
            publishRuntimeCandidate(candidate)
        }
        // The metadata just read and the code hash belong to the same finalized block.
        if (liveRuntimeCodeHash != null) {
            rememberRuntimeCodeBindingLocked(
                RuntimeCodeBinding(
                    schemaVersion = RUNTIME_CODE_BINDING_SCHEMA_VERSION,
                    genesisHash = liveGenesisHash,
                    specVersion = liveRuntimeVersion.specVersion,
                    transactionVersion = liveRuntimeVersion.transactionVersion,
                    metadataSha256 = liveMetadataSha256,
                    runtimeCodeHash = liveRuntimeCodeHash.lowercase(),
                )
            )
        }

        // Reads keep working with this runtime; only signing is refused.
        requireSignedExtensionsSupported(candidate.unsupportedSignedExtensions)

        return Sora2MutationRuntimeContext(
            snapshot = candidate.snapshot,
            runtimeVersion = liveRuntimeVersion,
            genesisHash = liveGenesisHash,
            finalizedHash = finalizedHash,
            metadataSha256 = candidate.metadataSha256,
            typesSha256 = candidate.typesSha256,
            finalizedBlockNumber = liveIdentity.finalizedBlockNumber,
        )
    }

    private fun requireSignedExtensionsSupported(unsupported: List<String>) {
        if (unsupported.isNotEmpty()) {
            throw Sora2AppUpdateRequiredException(unsupported)
        }
    }

    private suspend fun initFromCache(): RuntimeCandidate {
        val manifestRaw = try {
            fileManager.readInternalCacheFileAtomically(
                RUNTIME_CACHE_MANIFEST_FILE,
                MAX_RUNTIME_CACHE_MANIFEST_BYTES,
            )
        } catch (error: Exception) {
            FirebaseWrapper.recordException(error)
            return initFromBundledAssets()
        }
        if (manifestRaw != null) {
            return try {
                val published = loadPublishedRuntimeCache(manifestRaw)
                val admittedGenesisHash = requireCanonicalProductionGenesis(
                    observedGenesisHash = published.genesisHash,
                    enforceCanonicalMainnet = !BuildUtils.isFlavors(
                        Flavor.DEVELOP,
                        Flavor.SORALUTION,
                    ),
                )
                buildRuntimeCandidate(
                    published.metadata,
                    MetadataSource.Cache(published.sora2Types),
                    published.runtimeVersion,
                    admittedGenesisHash,
                    requireReviewedIdentityBeforeParsing =
                        !BuildUtils.isFlavors(
                            Flavor.DEVELOP,
                            Flavor.SORALUTION,
                        ) &&
                            published.runtimeVersion ==
                            Sora2RuntimeContract.SPEC_VERSION,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A corrupt or partially evicted generation is never combined with legacy cache
                // files. The bundled metadata/types pair is the only safe recovery source.
                FirebaseWrapper.recordException(error)
                initFromBundledAssets()
            }
        }
        return initFromLegacyOrBundledAssets()
    }

    private suspend fun initFromLegacyOrBundledAssets(): RuntimeCandidate {
        // Packaged fixtures are authoritative and remain fail-closed: no cache read failure may
        // hide a missing, oversize, malformed, or unreviewed bundled pair.
        val assetMetadata = fileManager.readAssetFile(
            RUNTIME_METADATA_FILE,
            MAX_RUNTIME_METADATA_BYTES,
        )
        val assetTypes = fileManager.readAssetFile(
            SORA2_TYPES_FILE,
            MAX_RUNTIME_TYPES_BYTES,
        )
        val legacyMetadata = readLegacyRuntimeCache(
            RUNTIME_METADATA_FILE,
            MAX_RUNTIME_METADATA_BYTES,
        )
        val legacyTypes = readLegacyRuntimeCache(
            SORA2_TYPES_FILE,
            MAX_RUNTIME_TYPES_BYTES,
        )
        val legacyPairMayBeAdmitted = mayAdmitLegacyRuntimeCache(
            legacyMetadata.state,
            legacyTypes.state,
        )
        val legacyPairMatchesAssets = legacyPairMayBeAdmitted &&
            legacyRuntimeCacheMatchesBundled(
            legacyMetadata = legacyMetadata.value,
            legacyTypes = legacyTypes.value,
            bundledMetadata = assetMetadata,
            bundledTypes = assetTypes,
        )
        if (
            (legacyMetadata.state != RuntimeCacheReadState.ABSENT ||
                legacyTypes.state != RuntimeCacheReadState.ABSENT) &&
            !legacyPairMatchesAssets
        ) {
            // An older runtime cache is expected on upgrade; the bundled/live contract replaces
            // it. A fixed breadcrumb records that path without counting it as an app failure.
            FirebaseWrapper.log("SORA2_RUNTIME_LEGACY_CACHE_IDENTITY_MISMATCH")
        }
        return buildBundledCandidate(
            metadata = if (legacyPairMatchesAssets) {
                checkNotNull(legacyMetadata.value)
            } else {
                assetMetadata
            },
            sora2Types = if (legacyPairMatchesAssets) {
                checkNotNull(legacyTypes.value)
            } else {
                assetTypes
            },
        )
    }

    private fun readLegacyRuntimeCache(
        fileName: String,
        maxBytes: Int,
    ): RuntimeCacheReadResult = try {
        val value = fileManager.readInternalCacheFile(fileName, maxBytes)
        RuntimeCacheReadResult(
            value = value,
            state = if (value == null) {
                RuntimeCacheReadState.ABSENT
            } else {
                RuntimeCacheReadState.PRESENT
            },
        )
    } catch (error: Exception) {
        // I/O, security, directory, malformed UTF-8, and oversize failures are evidence of an
        // unreadable legacy entry. Record the class only through the existing privacy-safe crash
        // channel and admit the validated packaged pair; never combine a surviving half-cache.
        FirebaseWrapper.recordException(error)
        RuntimeCacheReadResult(null, RuntimeCacheReadState.UNREADABLE)
    }

    private suspend fun initFromBundledAssets(): RuntimeCandidate =
        buildBundledCandidate(
            metadata = fileManager.readAssetFile(
                RUNTIME_METADATA_FILE,
                MAX_RUNTIME_METADATA_BYTES,
            ),
            sora2Types = fileManager.readAssetFile(
                SORA2_TYPES_FILE,
                MAX_RUNTIME_TYPES_BYTES,
            ),
        )

    private suspend fun buildBundledCandidate(
        metadata: String,
        sora2Types: String,
    ): RuntimeCandidate {
        val storedRuntimeVersion = soraPreferences.getInt(
            RUNTIME_VERSION_PREF,
            RUNTIME_VERSION_START,
        )
        val reviewedRuntimeVersion = if (
            metadata.runtimeMetadataSha256() == Sora2RuntimeContract.METADATA_FILE_SHA256 &&
            sora2Types.rawSha256() == Sora2RuntimeContract.TYPES_FILE_SHA256
        ) {
            Sora2RuntimeContract.SPEC_VERSION
        } else {
            storedRuntimeVersion
        }
        return buildRuntimeCandidate(
            metadata,
            MetadataSource.Cache(sora2Types),
            reviewedRuntimeVersion,
            Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
            requireReviewedIdentityBeforeParsing =
                !BuildUtils.isFlavors(
                    Flavor.DEVELOP,
                    Flavor.SORALUTION,
                ),
        )
    }

    private fun loadPublishedRuntimeCache(manifestRaw: String): PublishedRuntimeCache {
        requireRuntimePayloadWithinLimit(
            manifestRaw,
            MAX_RUNTIME_CACHE_MANIFEST_BYTES,
            "SORA2_RUNTIME_CACHE_MANIFEST_TOO_LARGE",
        )
        val manifest = try {
            gson.fromJson(manifestRaw, RuntimeCacheManifest::class.java)
        } catch (error: Exception) {
            throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_MANIFEST_INVALID")
        } ?: throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_MANIFEST_INVALID")
        val coordinates = requireRuntimeCacheCoordinates(manifest)
        val published = requirePublishedRuntimeCache(
            manifest = manifest,
            metadata = fileManager.readInternalCacheFileAtomically(
                coordinates.metadataFile,
                MAX_RUNTIME_METADATA_BYTES,
            ),
            sora2Types = fileManager.readInternalCacheFileAtomically(
                coordinates.typesFile,
                MAX_RUNTIME_TYPES_BYTES,
            ),
        )
        // Pruning needs the pending-submission journal, which is never read under the runtime
        // lock; the caller prunes after releasing it.
        pruneRequested.set(true)
        return published
    }

    private fun rawTypesToTree(raw: String) = gson.fromJson(raw, TypeDefinitionsTree::class.java)

    private suspend fun checkRuntimeVersion(candidate: RuntimeCandidate): RuntimeCandidate {
        val liveIdentity = try {
            sora2RuntimeRpcClient.getFinalizedIdentity()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Read-only startup may use a validated installed cache only when no live chain
            // identity was obtained. Mutation preparation uses getMutationRuntimeContext() and
            // never takes this availability fallback.
            FirebaseWrapper.recordException(error)
            return candidate
        }
        // A complete live identity is now available. An unexpected chain is identity drift, not
        // an availability failure, and must never be converted into cached-startup permission.
        val liveGenesisHash = requireCanonicalProductionGenesis(
            observedGenesisHash = liveIdentity.genesisHash,
            enforceCanonicalMainnet = !BuildUtils.isFlavors(
                Flavor.DEVELOP,
                Flavor.SORALUTION,
            ),
        )
        val runtimeVersion = liveIdentity.runtimeVersion
        val storedRuntimeVersionPreference = soraPreferences.getInt(
            RUNTIME_VERSION_PREF,
            RUNTIME_VERSION_START,
        )
        // The generation manifest is authoritative for parsing its metadata/types. The legacy
        // preference remains only a refresh hint (and supports the debug reset action).
        val refreshRequested = storedRuntimeVersionPreference != candidate.runtimeVersion
        val liveIdentityDrift =
            runtimeVersion.specVersion != candidate.runtimeVersion ||
                liveGenesisHash != candidate.genesisHash
        val reviewedFixtureNeedsRefresh =
            runtimeVersion.specVersion == Sora2RuntimeContract.SPEC_VERSION &&
                (
                    candidate.metadataSha256 != Sora2RuntimeContract.METADATA_FILE_SHA256 ||
                        candidate.typesSha256 != Sora2RuntimeContract.TYPES_FILE_SHA256
                )
        val refreshRequired = liveIdentityDrift || reviewedFixtureNeedsRefresh
        if (refreshRequired || refreshRequested) {
            FirebaseWrapper.log("Refresh runtime version ${runtimeVersion.specVersion}")
            try {
                val metadata = sora2RuntimeRpcClient.getMetadataAtFinalized(
                    liveIdentity.finalizedHash
                ).let { refreshedMetadata ->
                    requireRuntimePayloadWithinLimit(
                        refreshedMetadata,
                        MAX_RUNTIME_METADATA_BYTES,
                        "SORA2_RUNTIME_METADATA_TOO_LARGE",
                    )
                }
                val refreshedCandidate = buildLiveRuntimeCandidate(
                    metadata = metadata,
                    runtimeVersion = runtimeVersion.specVersion,
                    genesisHash = liveGenesisHash,
                    requireReviewedIdentityBeforeParsing =
                        !BuildUtils.isFlavors(
                            Flavor.DEVELOP,
                            Flavor.SORALUTION,
                        ) &&
                            runtimeVersion.specVersion ==
                            Sora2RuntimeContract.SPEC_VERSION,
                )
                currentCoroutineContext().ensureActive()
                val refreshedTypes = checkNotNull(refreshedCandidate.sora2TypesToCache) {
                    "SORA2_RUNTIME_TYPES_NOT_FETCHED"
                }
                // Each content-addressed generation file is atomic. The manifest pointer is
                // published last, so cancellation or process death can leave only unreachable
                // files or one complete old/new generation, never a mixed active pair. A failed
                // save (for example a full disk) does not fail the refresh: the validated
                // candidate is used in memory and saving is retried on the next refresh.
                if (
                    publishRuntimeCacheBestEffort(
                        metadata = metadata,
                        sora2Types = refreshedTypes,
                        runtimeVersion = runtimeVersion.specVersion,
                        genesisHash = liveGenesisHash,
                    )
                ) {
                    try {
                        soraPreferences.putInt(
                            RUNTIME_VERSION_PREF,
                            runtimeVersion.specVersion,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        // The manifest already contains the authoritative runtime version. A
                        // stale preference can only trigger a redundant refresh next time.
                        FirebaseWrapper.recordException(error)
                    }
                }
                // The code hash of the same finalized block the metadata was read at.
                val runtimeCodeHash = runtimeCodeHashOrNull(liveIdentity.finalizedHash)
                return refreshedCandidate.copy(
                    codeBinding = runtimeCodeHash?.let { codeHash ->
                        RuntimeCodeBinding(
                            schemaVersion = RUNTIME_CODE_BINDING_SCHEMA_VERSION,
                            genesisHash = liveGenesisHash,
                            specVersion = runtimeVersion.specVersion,
                            transactionVersion = runtimeVersion.transactionVersion,
                            metadataSha256 = refreshedCandidate.metadataSha256,
                            runtimeCodeHash = codeHash.lowercase(),
                        )
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Sora2RuntimeIdentityException) {
                // A node serving metadata for another SS58 network is not an availability
                // failure. Never turn an identity mismatch into permission to reuse cached
                // metadata while still connected to that node.
                throw error
            } catch (t: Throwable) {
                FirebaseWrapper.recordException(t)
                if (refreshRequired) {
                    throw Sora2RuntimeIdentityException("SORA2_RUNTIME_REFRESH_FAILED", t)
                }
            }
        }
        return candidate
    }

    /** Returns false (and records why) when the generation could not be saved and verified. */
    private suspend fun publishRuntimeCacheBestEffort(
        metadata: String,
        sora2Types: String,
        runtimeVersion: Int,
        genesisHash: String,
    ): Boolean = try {
        publishRuntimeCache(metadata, sora2Types, runtimeVersion, genesisHash)
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        FirebaseWrapper.recordException(error)
        false
    }

    private suspend fun publishRuntimeCache(
        metadata: String,
        sora2Types: String,
        runtimeVersion: Int,
        genesisHash: String,
    ) {
        val manifest = createRuntimeCacheManifest(
            metadata,
            sora2Types,
            runtimeVersion,
            genesisHash,
        )
        val coordinates = requireRuntimeCacheCoordinates(manifest)
        fileManager.writeInternalCacheFileAtomically(
            coordinates.metadataFile,
            metadata,
            MAX_RUNTIME_METADATA_BYTES,
        )
        currentCoroutineContext().ensureActive()
        fileManager.writeInternalCacheFileAtomically(
            coordinates.typesFile,
            sora2Types,
            MAX_RUNTIME_TYPES_BYTES,
        )
        currentCoroutineContext().ensureActive()
        requirePublishedRuntimeCache(
            manifest = manifest,
            metadata = fileManager.readInternalCacheFileAtomically(
                coordinates.metadataFile,
                MAX_RUNTIME_METADATA_BYTES,
            ),
            sora2Types = fileManager.readInternalCacheFileAtomically(
                coordinates.typesFile,
                MAX_RUNTIME_TYPES_BYTES,
            ),
        )
        val manifestRaw = requireRuntimePayloadWithinLimit(
            gson.toJson(manifest),
            MAX_RUNTIME_CACHE_MANIFEST_BYTES,
            "SORA2_RUNTIME_CACHE_MANIFEST_TOO_LARGE",
        )
        fileManager.writeInternalCacheFileAtomically(
            RUNTIME_CACHE_MANIFEST_FILE,
            manifestRaw,
            MAX_RUNTIME_CACHE_MANIFEST_BYTES,
        )
        currentCoroutineContext().ensureActive()
        // Verify the exact manifest pointer after publication before old generations become
        // eligible for bounded best-effort pruning.
        val publishedManifest = fileManager.readInternalCacheFileAtomically(
            RUNTIME_CACHE_MANIFEST_FILE,
            MAX_RUNTIME_CACHE_MANIFEST_BYTES,
        ) ?: throw Sora2RuntimeIdentityException("SORA2_RUNTIME_CACHE_MANIFEST_MISSING")
        loadPublishedRuntimeCache(publishedManifest)
    }

    /**
     * Bounded best-effort cache hygiene after a generation was loaded or published. The active
     * generation and every generation an unresolved pending submission was signed with are kept.
     * When the journal cannot be read (for example while the upgrade gate is closed), nothing is
     * deleted.
     */
    private suspend fun pruneRuntimeCacheIfRequested() {
        if (!pruneRequested.compareAndSet(true, false)) return
        try {
            val retainedFiles = recoveryRuntimeRetention.retainedRecoveryRuntimes()
                ?.mapNotNull(::runtimeCacheGenerationFilesOrNull)
                ?.flatMapTo(mutableSetOf()) { (metadataFile, typesFile) ->
                    listOf(metadataFile, typesFile)
                }
                ?: return
            mutex.withLock {
                withContext(coroutineManager.io) {
                    val manifestRaw = fileManager.readInternalCacheFileAtomically(
                        RUNTIME_CACHE_MANIFEST_FILE,
                        MAX_RUNTIME_CACHE_MANIFEST_BYTES,
                    ) ?: return@withContext
                    requireRuntimePayloadWithinLimit(
                        manifestRaw,
                        MAX_RUNTIME_CACHE_MANIFEST_BYTES,
                        "SORA2_RUNTIME_CACHE_MANIFEST_TOO_LARGE",
                    )
                    val manifest = gson.fromJson(manifestRaw, RuntimeCacheManifest::class.java)
                        ?: return@withContext
                    val active = requireRuntimeCacheCoordinates(manifest)
                    val candidates = fileManager.listInternalCacheFileNames(
                        "$RUNTIME_CACHE_GENERATION_PREFIX-",
                        MAX_RUNTIME_GENERATION_SCAN,
                    )
                    selectUnreachableRuntimeGenerationFiles(
                        fileNames = candidates,
                        activeMetadataFile = active.metadataFile,
                        activeTypesFile = active.typesFile,
                        maxFiles = MAX_RUNTIME_GENERATION_PRUNE,
                        retainedFiles = retainedFiles,
                    ).forEach { fileName ->
                        fileManager.deleteInternalCacheFile(fileName)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Publication already succeeded and the active generation is explicitly excluded.
            // Cache hygiene failure is reportable but cannot invalidate the verified pointer.
            FirebaseWrapper.recordException(error)
        }
    }

    /**
     * Builds a candidate from live metadata. Type aliases come from the `substrate_types_android`
     * file named in the SORA remote config (the source 3.8.6.3 used). When that file cannot be
     * fetched or does not produce a valid SORA2 snapshot, the packaged file is used instead, so a
     * config outage never blocks the runtime refresh. The PI indexer's `substrateTypesUrl` is not
     * used: it names the iOS file, whose aliases are numeric metadata lookup ids.
     */
    private suspend fun buildLiveRuntimeCandidate(
        metadata: String,
        runtimeVersion: Int,
        genesisHash: String,
        requireReviewedIdentityBeforeParsing: Boolean = false,
    ): RuntimeCandidate {
        val remoteTypes = loadRemoteSora2TypesOrNull()
        if (remoteTypes != null) {
            try {
                return buildRuntimeCandidate(
                    metadata = metadata,
                    metadataSource = MetadataSource.SoraNet(remoteTypes),
                    runtimeVersion = runtimeVersion,
                    genesisHash = genesisHash,
                    requireReviewedIdentityBeforeParsing = requireReviewedIdentityBeforeParsing,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Retry once with the packaged aliases. A wrong network or malformed metadata
                // fails the same way there, and that second failure is the one reported.
                FirebaseWrapper.recordException(error)
            }
        }
        return buildRuntimeCandidate(
            metadata = metadata,
            metadataSource = MetadataSource.SoraNet(
                fileManager.readAssetFile(SORA2_TYPES_FILE, MAX_RUNTIME_TYPES_BYTES)
            ),
            runtimeVersion = runtimeVersion,
            genesisHash = genesisHash,
            requireReviewedIdentityBeforeParsing = requireReviewedIdentityBeforeParsing,
        )
    }

    private suspend fun loadRemoteSora2TypesOrNull(): String? = try {
        val url = soraConfigManager.getSubstrateTypesUrl()
        if (url.startsWith("https://") && url.length <= MAX_REMOTE_TYPES_URL_LENGTH) {
            boundedHttpTextClient.getUtf8(
                rawUrl = url,
                maximumBytes = MAX_RUNTIME_TYPES_BYTES,
            ).let { remoteTypes ->
                requireRuntimePayloadWithinLimit(
                    remoteTypes,
                    MAX_RUNTIME_TYPES_BYTES,
                    "SORA2_RUNTIME_TYPES_TOO_LARGE",
                )
            }.takeIf { remoteTypes ->
                rawTypesToTree(remoteTypes)?.versioning?.isNotEmpty() == true
            }
        } else {
            null
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        FirebaseWrapper.recordException(error)
        null
    }

    private suspend fun buildRuntimeCandidate(
        metadata: String,
        metadataSource: MetadataSource,
        runtimeVersion: Int,
        genesisHash: String,
        requireReviewedIdentityBeforeParsing: Boolean = false,
    ): RuntimeCandidate {
        val admittedMetadata = requireRuntimePayloadWithinLimit(
            metadata,
            MAX_RUNTIME_METADATA_BYTES,
            "SORA2_RUNTIME_METADATA_TOO_LARGE",
        )
        val metadataSha256 = admittedMetadata.runtimeMetadataSha256()
        if (requireReviewedIdentityBeforeParsing) {
            check(runtimeVersion == Sora2RuntimeContract.SPEC_VERSION) {
                "SORA2_RUNTIME_SNAPSHOT_VERSION_MISMATCH"
            }
            check(metadataSha256 == Sora2RuntimeContract.METADATA_FILE_SHA256) {
                "SORA2_RUNTIME_SNAPSHOT_IDENTITY_MISMATCH"
            }
        }
        var sora2TypesToCache: String? = null
        val sora2TypesRaw = when (metadataSource) {
            is MetadataSource.Cache -> metadataSource.sora2Types
            is MetadataSource.SoraNet -> metadataSource.sora2Types
                .also { sora2TypesToCache = it }
        }.let { types ->
            requireRuntimePayloadWithinLimit(
                types,
                MAX_RUNTIME_TYPES_BYTES,
                "SORA2_RUNTIME_TYPES_TOO_LARGE",
            )
        }
        val typesSha256 = sora2TypesRaw.rawSha256()
        if (requireReviewedIdentityBeforeParsing) {
            requireReviewedSora2SnapshotIdentity(
                specVersion = runtimeVersion,
                metadataSha256 = metadataSha256,
                typesSha256 = typesSha256,
            )
        }
        // Only bounded, hash-admitted production bytes may reach either parser.
        val runtimeMetadataReader = RuntimeMetadataReader.read(admittedMetadata)
        val typeRegistry = if (runtimeMetadataReader.metadataVersion < 14) {
            val defaultTypesRaw = fileManager.readAssetFile(
                DEFAULT_TYPES_FILE,
                MAX_RUNTIME_TYPES_BYTES,
            )
            buildTypeRegistry12(defaultTypesRaw, sora2TypesRaw, runtimeVersion)
        } else {
            buildTypeRegistry14(sora2TypesRaw, runtimeMetadataReader, runtimeVersion)
        }
        val runtimeMetadata =
            VersionedRuntimeBuilder.buildMetadata(runtimeMetadataReader, typeRegistry)
        val snapshot = RuntimeSnapshot(typeRegistry, runtimeMetadata)
        val unsupportedSignedExtensions = if (runtimeMetadataReader.metadataVersion < 14) {
            emptyList()
        } else {
            @Suppress("UNCHECKED_CAST")
            Sora2SignedExtensionAdmission.unsupportedExtensions(
                runtimeMetadataReader.metadata as EncodableStruct<RuntimeMetadataSchemaV14>
            )
        }
        val valueConstant =
            snapshot.metadata.module(Pallete.SYSTEM.palletName).constants[Constants.SS58Prefix.constantName]
        val observedPrefix = valueConstant?.type?.fromByteArrayOrNull(
            snapshot,
            valueConstant.value
        ) as? BigInteger
        val validatedPrefix = requireSora2Ss58Prefix(
            observedPrefix
        )
        FirebaseWrapper.log("Set Runtime Net ${metadataSource is MetadataSource.SoraNet}")
        return RuntimeCandidate(
            snapshot = snapshot,
            prefix = validatedPrefix,
            genesisHash = genesisHash.requireCanonicalBlockHash("SORA2_GENESIS_HASH_INVALID"),
            metadataSha256 = metadataSha256,
            typesSha256 = typesSha256,
            runtimeVersion = runtimeVersion,
            sora2TypesToCache = sora2TypesToCache,
            unsupportedSignedExtensions = unsupportedSignedExtensions,
        )
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
        data class Cache(val sora2Types: String) : MetadataSource()
        /** Live metadata from the node, parsed with [sora2Types] and cached with them. */
        data class SoraNet(val sora2Types: String) : MetadataSource()
    }
}
