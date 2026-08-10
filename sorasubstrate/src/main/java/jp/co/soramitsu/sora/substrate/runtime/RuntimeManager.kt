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
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkaswapIndexerClient
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
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
private const val MAX_RUNTIME_METADATA_BYTES = 16 * 1024 * 1024
private const val MAX_RUNTIME_TYPES_BYTES = 8 * 1024 * 1024
private const val MAX_RUNTIME_GENERATION_SCAN = 32
private const val MAX_RUNTIME_GENERATION_PRUNE = 4
private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
private val RUNTIME_METADATA_HEX = Regex("^0x[0-9a-fA-F]+$")
private val BLOCK_HASH_HEX = Regex("^0x[0-9a-fA-F]{64}$")
private val RUNTIME_CACHE_GENERATION_FILE = Regex(
    "^${Regex.escape(RUNTIME_CACHE_GENERATION_PREFIX)}-[1-9][0-9]*-" +
        "[0-9a-f]{64}-[0-9a-f]{64}-[0-9a-f]{64}\\.(?:metadata|types\\.json)$"
)

internal class Sora2RuntimeIdentityException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

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

private data class RuntimeCandidate(
    val snapshot: RuntimeSnapshot,
    val prefix: Short,
    val genesisHash: String,
    val metadataSha256: String,
    val typesSha256: String,
    val runtimeVersion: Int,
    val sora2TypesToCache: String? = null,
)

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
): List<String> {
    require(maxFiles in 0..MAX_RUNTIME_GENERATION_PRUNE) {
        "SORA2_RUNTIME_CACHE_PRUNE_LIMIT_INVALID"
    }
    val activeFiles = setOf(activeMetadataFile, activeTypesFile)
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
    private val connectionManager: ConnectionManager,
    private val boundedHttpTextClient: BoundedHttpTextClient,
    private val coroutineManager: CoroutineManager,
    private val polkaswapIndexerClient: PolkaswapIndexerClient,
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
    private var prefix: Short = SORA2_SS58_PREFIX

    init {
        coroutineManager.applicationScope.launch {
            getRuntimeSnapshot()
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
        return runtimeSnapshot ?: mutex.withLock {
            runtimeSnapshot ?: createRuntimeSnapshot()
        }
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
    suspend fun getMutationRuntimeContext(): Sora2MutationRuntimeContext = mutex.withLock {
        withContext(coroutineManager.io) {
            createMutationRuntimeContextLocked()
        }
    }

    /**
     * Loads the exact reviewed packaged runtime fixture for status-only recovery. This method
     * never substitutes the active cache or live metadata: an older witness is admitted only if
     * every recorded identity field still matches a checked-in fixture byte-for-byte. A missing
     * historical fixture returns null so its journal row remains unresolved and non-prunable.
     */
    internal suspend fun getReviewedRecoveryRuntimeSnapshotOrNull(
        genesisHash: String,
        specVersion: Int,
        transactionVersion: Int,
        metadataSha256: String,
        typesSha256: String,
    ): RuntimeSnapshot? = mutex.withLock {
        withContext(coroutineManager.io) {
            val fixture = initFromBundledAssets()
            fixture.snapshot.takeIf {
                fixture.genesisHash == genesisHash &&
                    fixture.runtimeVersion == specVersion &&
                    transactionVersion == Sora2RuntimeContract.TRANSACTION_VERSION &&
                    fixture.metadataSha256 == metadataSha256 &&
                    fixture.typesSha256 == typesSha256
            }
        }
    }

    suspend fun resetRuntimeVersion() {
        soraPreferences.putInt(RUNTIME_VERSION_PREF, 0)
    }

    private suspend fun createRuntimeSnapshot(): RuntimeSnapshot =
        withContext(coroutineManager.io) {
            val candidate = checkRuntimeVersion(initFromCache())
            publishRuntimeCandidate(candidate)
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
        runtimeSnapshot = candidate.snapshot
    }

    private suspend fun createMutationRuntimeContextLocked(): Sora2MutationRuntimeContext {
        // ExtrinsicManager holds WalletMutationCoordinator throughout preparation/signing, while
        // NodeManager takes that same boundary before switching. This fresh check therefore binds
        // the fixed HTTPS authority to the actual connected WebSocket for the entire preparation
        // span. SubstrateCalls takes an additional transport lease for the final handoff.
        connectionManager.requireReviewedSora2MutationTransport()
        val enforceReviewedProductionRuntime = !BuildUtils.isFlavors(
            Flavor.DEVELOP,
            Flavor.SORALUTION,
        )
        val liveIdentity = sora2RuntimeRpcClient.getFinalizedIdentity()
        val liveGenesisHash = liveIdentity.genesisHash.let { observed ->
            requireCanonicalProductionGenesis(
                observedGenesisHash = observed,
                enforceCanonicalMainnet = enforceReviewedProductionRuntime,
            )
        }
        val finalizedHash = liveIdentity.finalizedHash.requireCanonicalBlockHash(
            "SORA2_FINALIZED_HASH_INVALID"
        )
        val liveRuntimeVersion = liveIdentity.runtimeVersion
        val liveMetadata = sora2RuntimeRpcClient.getMetadataAtFinalized(finalizedHash)
            .let { metadata ->
                requireRuntimePayloadWithinLimit(
                    metadata,
                    MAX_RUNTIME_METADATA_BYTES,
                    "SORA2_RUNTIME_METADATA_TOO_LARGE",
                )
            }
        val liveMetadataSha256 = liveMetadata.runtimeMetadataSha256()

        val activeSnapshot = runtimeSnapshot
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
            )
        } else {
            buildRuntimeCandidate(
                metadata = liveMetadata,
                metadataSource = MetadataSource.SoraNet,
                runtimeVersion = liveRuntimeVersion.specVersion,
                genesisHash = liveGenesisHash,
                requireReviewedIdentityBeforeParsing =
                    enforceReviewedProductionRuntime,
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
        if (enforceReviewedProductionRuntime) {
            check(liveRuntimeVersion.specVersion == Sora2RuntimeContract.SPEC_VERSION) {
                "SORA2_SPEC_VERSION_MISMATCH"
            }
            check(
                liveRuntimeVersion.transactionVersion ==
                    Sora2RuntimeContract.TRANSACTION_VERSION
            ) { "SORA2_TRANSACTION_VERSION_MISMATCH" }
            requireReviewedSora2SnapshotIdentity(
                specVersion = candidate.runtimeVersion,
                metadataSha256 = candidate.metadataSha256,
                typesSha256 = candidate.typesSha256,
            )
        }

        if (!activeMatchesCheckpoint) {
            val refreshedTypes = checkNotNull(candidate.sora2TypesToCache) {
                "SORA2_RUNTIME_TYPES_NOT_FETCHED"
            }
            publishRuntimeCache(
                metadata = liveMetadata,
                sora2Types = refreshedTypes,
                runtimeVersion = liveRuntimeVersion.specVersion,
                genesisHash = liveGenesisHash,
            )
            currentCoroutineContext().ensureActive()
            try {
                soraPreferences.putInt(RUNTIME_VERSION_PREF, liveRuntimeVersion.specVersion)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                FirebaseWrapper.recordException(error)
            }
            publishRuntimeCandidate(candidate)
        }

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
            FirebaseWrapper.recordException(
                Sora2RuntimeIdentityException("SORA2_RUNTIME_LEGACY_CACHE_IDENTITY_MISMATCH")
            )
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
        pruneUnreachableRuntimeCacheFiles(coordinates)
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
                val refreshedCandidate = buildRuntimeCandidate(
                    metadata,
                    MetadataSource.SoraNet,
                    runtimeVersion.specVersion,
                    liveGenesisHash,
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
                // files or one complete old/new generation, never a mixed active pair.
                publishRuntimeCache(
                    metadata = metadata,
                    sora2Types = refreshedTypes,
                    runtimeVersion = runtimeVersion.specVersion,
                    genesisHash = liveGenesisHash,
                )
                currentCoroutineContext().ensureActive()
                try {
                    soraPreferences.putInt(
                        RUNTIME_VERSION_PREF,
                        runtimeVersion.specVersion,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    // The manifest already contains the authoritative runtime version. A stale
                    // preference can only trigger a redundant refresh on the next process.
                    FirebaseWrapper.recordException(error)
                }
                return refreshedCandidate
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

    private fun pruneUnreachableRuntimeCacheFiles(active: RuntimeCacheCoordinates) {
        try {
            val candidates = fileManager.listInternalCacheFileNames(
                "$RUNTIME_CACHE_GENERATION_PREFIX-",
                MAX_RUNTIME_GENERATION_SCAN,
            )
            selectUnreachableRuntimeGenerationFiles(
                fileNames = candidates,
                activeMetadataFile = active.metadataFile,
                activeTypesFile = active.typesFile,
                maxFiles = MAX_RUNTIME_GENERATION_PRUNE,
            ).forEach { fileName ->
                fileManager.deleteInternalCacheFile(fileName)
            }
        } catch (error: Exception) {
            // Publication already succeeded and the active generation is explicitly excluded.
            // Cache hygiene failure is reportable but cannot invalidate the verified pointer.
            FirebaseWrapper.recordException(error)
        }
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
            is MetadataSource.SoraNet -> {
                val substrateTypesUrl = checkNotNull(
                    polkaswapIndexerClient.requireMobileConfig().substrateTypesUrl
                ) { "PI_INDEXER_TYPES_CAPABILITY_UNAVAILABLE" }
                boundedHttpTextClient.getUtf8(
                    rawUrl = substrateTypesUrl,
                    maximumBytes = MAX_RUNTIME_TYPES_BYTES,
                ).let { remoteTypes ->
                    requireRuntimePayloadWithinLimit(
                        remoteTypes,
                        MAX_RUNTIME_TYPES_BYTES,
                        "SORA2_RUNTIME_TYPES_TOO_LARGE",
                    )
                }.also { sora2TypesToCache = it }
            }
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
        data object SoraNet : MetadataSource()
    }
}
