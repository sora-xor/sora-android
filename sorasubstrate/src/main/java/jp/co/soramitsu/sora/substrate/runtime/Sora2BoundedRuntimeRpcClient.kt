/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021 Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.sora.substrate.runtime

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.common.network.BoundedHttpTextException
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.sora.substrate.substrate.canonicalExtrinsicHash
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Narrow transport seam for deterministic protocol tests. Production always constructs the
 * implementation below, whose endpoints are the reviewed runtime endpoint constants.
 *
 * A transport reports a request that provably never reached its node by throwing
 * [BoundedHttpTextException] with `requestNotSent = true`. Any other failure leaves open that the
 * node received the request.
 */
internal fun interface Sora2RuntimeRpcTransport {
    suspend fun post(requestBody: String, maximumResponseBytes: Int): String
}

private class ManifestBoundSora2RuntimeRpcTransport(
    private val client: BoundedHttpTextClient,
    private val endpoint: String,
) : Sora2RuntimeRpcTransport {
    override suspend fun post(
        requestBody: String,
        maximumResponseBytes: Int,
    ): String = client.postJson(
        rawUrl = endpoint,
        requestBody = requestBody,
        maximumBytes = maximumResponseBytes,
        // Short, so that a host that cannot be reached is given up in time for the other
        // reviewed endpoint to answer within the same user action.
        connectTimeoutMillis = RPC_CONNECT_TIMEOUT_MILLIS,
        // Only the runtime metadata is large. It is downloaded once per runtime version and may
        // take longer on a slow mobile connection than a small request is allowed to.
        absoluteTimeoutMillis = if (
            maximumResponseBytes >= Sora2BoundedRuntimeRpcClient.MAXIMUM_METADATA_RESPONSE_BYTES
        ) {
            METADATA_DOWNLOAD_DEADLINE_MILLIS
        } else {
            BoundedHttpTextClient.DEFAULT_ABSOLUTE_TIMEOUT_MILLIS
        },
    ).body
}

private const val RPC_CONNECT_TIMEOUT_MILLIS = 10_000
private const val METADATA_DOWNLOAD_DEADLINE_MILLIS = 150_000L

/**
 * A node that prunes old state answers `4003 ... State already discarded for <hash>` for blocks
 * older than its state window. The same read is then answered by the reviewed archive endpoint.
 */
internal fun Sora2RuntimeRpcRemoteException.isDiscardedStateError(): Boolean =
    code == STATE_DISCARDED_ERROR_CODE &&
        remoteMessage.contains(STATE_DISCARDED_MESSAGE, ignoreCase = true)

private const val STATE_DISCARDED_ERROR_CODE = 4003L
private const val STATE_DISCARDED_MESSAGE = "State already discarded"

/**
 * No reviewed endpoint received the signed transaction: every attempt failed before the request
 * body was handed to a connection. Nothing was broadcast.
 */
internal class Sora2SubmissionNotSentException(
    cause: Throwable,
) : Sora2RuntimeIdentityException("SORA2_SUBMISSION_NOT_SENT", cause)

internal data class Sora2FinalizedRuntimeIdentity(
    val genesisHash: String,
    val finalizedHash: String,
    val runtimeVersion: RuntimeVersion,
    val finalizedBlockNumber: Long,
)

internal data class Sora2CanonicalBlock(
    val blockHash: String,
    val blockNumber: Long,
    val matchingExtrinsicIndices: Map<String, Int>,
)

private enum class Sora2RuntimeRpcResultKind {
    STRING,
    RUNTIME_VERSION,
    UNSIGNED_INTEGER,
    STRUCTURED,
}

/**
 * Bounded JSON-RPC client for the SORA2 values that authorize runtime decoding and signing.
 *
 * This client deliberately has no selected-node or WebSocket dependency. It talks only to the
 * reviewed HTTPS endpoints ([Sora2RuntimeContract.RUNTIME_RPC_ENDPOINT] and
 * [Sora2RuntimeContract.SECOND_RUNTIME_RPC_ENDPOINT]); JSON POST redirects are rejected by
 * [BoundedHttpTextClient], and a strict streaming envelope admits each result before Gson may
 * materialize only the small, method-bounded runtime-version object.
 *
 * Two endpoints, one set of rules:
 *
 * - **Reads** (chain identity, metadata, runtime code hash, nonce, fee, blocks, storage) are
 *   answered by the first endpoint that gives an acceptable answer. Every answer passes the same
 *   validation whichever endpoint gave it. One logical read is never assembled from two
 *   endpoints: [getFinalizedIdentity] and [getCanonicalBlock] run all their requests on one.
 * - **An endpoint is used only on the expected chain.** With more than one endpoint, each must
 *   itself have reported the expected genesis hash in this process before it serves any other
 *   request, and it loses that admission as soon as it reports another one.
 * - **A block counts as finalized only on the endpoint that says so.** Two healthy nodes finalize
 *   the same chain, but not at the same instant. [getCanonicalBlock] therefore reads a block by
 *   number only from an endpoint whose own finalized head has reached that number, and
 *   [getFinalizedIdentity] prefers an endpoint that is not behind a finalized height this
 *   process has already been given.
 * - **A signed transaction is delivered at most once.** [submitExtrinsicOnce] moves on to the
 *   next endpoint only when the previous attempt provably never reached its node. Any other
 *   outcome is final for the submission; the caller resolves it by transaction hash.
 */
@Singleton
class Sora2BoundedRuntimeRpcClient internal constructor(
    transport: Sora2RuntimeRpcTransport,
    private val gson: Gson,
    private val sora2AddressCodec: Sora2AddressCodec = Sora2AddressCodec(),
    /** The second reviewed endpoint; see [Sora2RuntimeContract.SECOND_RUNTIME_RPC_ENDPOINT]. */
    secondTransport: Sora2RuntimeRpcTransport? = null,
    /**
     * The genesis hash every endpoint must report, or null to pin the endpoints to the first
     * genesis hash observed in this process (flavors that are not bound to mainnet).
     */
    private val expectedGenesisHash: String? = null,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    @Inject
    constructor(
        boundedHttpTextClient: BoundedHttpTextClient,
        gson: Gson,
        sora2AddressCodec: Sora2AddressCodec,
    ) : this(
        transport = ManifestBoundSora2RuntimeRpcTransport(
            client = boundedHttpTextClient,
            endpoint = Sora2RuntimeContract.RUNTIME_RPC_ENDPOINT,
        ),
        gson = gson,
        sora2AddressCodec = sora2AddressCodec,
        secondTransport = ManifestBoundSora2RuntimeRpcTransport(
            client = boundedHttpTextClient,
            endpoint = Sora2RuntimeContract.SECOND_RUNTIME_RPC_ENDPOINT,
        ),
        expectedGenesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH.takeUnless {
            BuildUtils.isFlavors(Flavor.DEVELOP, Flavor.SORALUTION)
        },
    )

    private class Endpoint(val transport: Sora2RuntimeRpcTransport) {
        /** The expected genesis hash, once this endpoint itself reported it. */
        @Volatile
        var admittedGenesisHash: String? = null

        /** While in the future, this endpoint is tried after the others. */
        @Volatile
        var avoidUntilElapsedMillis: Long? = null

        @Volatile
        var consecutiveTransportFailures: Int = 0

        /**
         * The highest block this endpoint itself reported as finalized, or -1. A node's
         * finalized head only moves forward, so every block up to it is final on this endpoint.
         */
        @Volatile
        var finalizedHeight: Long = -1L
    }

    private val endpoints: List<Endpoint> = listOfNotNull(transport, secondTransport).map(::Endpoint)
    private val referenceGenesisHash = AtomicReference<String?>(
        expectedGenesisHash?.requireCanonicalBlockHash("SORA2_GENESIS_HASH_INVALID")
    )
    private val requestIds = AtomicLong(0)

    /** The highest finalized height [getFinalizedIdentity] has returned in this process. */
    private val highestFinalizedHeight = AtomicLong(-1L)

    /**
     * Obtains one canonical finalized checkpoint from one endpoint. Runtime version is explicitly
     * queried at that hash, so a caller can fetch metadata at the same immutable checkpoint
     * without mixing heads.
     *
     * When no endpoint is on the expected chain, the identity that was observed is returned
     * unchanged, so the caller's own genesis check fails as identity drift. It is never turned
     * into "the network is unreachable".
     *
     * An endpoint whose finalized head is behind a height this process was already given is
     * passed over while another endpoint is not behind. When every endpoint that answers is
     * behind, the most advanced answer is returned; callers that need finality to move forward
     * check that themselves.
     */
    internal suspend fun getFinalizedIdentity(): Sora2FinalizedRuntimeIdentity {
        var unexpectedChain: Sora2FinalizedRuntimeIdentity? = null
        var behind: Sora2FinalizedRuntimeIdentity? = null
        val identity = try {
            readWithFailover(admitFirst = false) { endpoint ->
                val identity = readFinalizedIdentity(endpoint)
                if (!admitGenesis(endpoint, identity.genesisHash)) {
                    if (unexpectedChain == null) unexpectedChain = identity
                    throw Sora2RuntimeIdentityException(ENDPOINT_GENESIS_MISMATCH)
                }
                endpoint.finalizedHeight =
                    maxOf(endpoint.finalizedHeight, identity.finalizedBlockNumber)
                if (
                    endpoints.size > 1 &&
                    identity.finalizedBlockNumber < highestFinalizedHeight.get()
                ) {
                    // Right chain, but behind what another endpoint already finalized. Ask the
                    // next endpoint first so that callers do not see finality step backwards.
                    if (identity.finalizedBlockNumber > (behind?.finalizedBlockNumber ?: -1L)) {
                        behind = identity
                    }
                    throw Sora2RuntimeIdentityException(ENDPOINT_BEHIND)
                }
                identity
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Nothing better is available: the most advanced answer from the expected chain,
            // else what was observed on an unexpected one (the caller rejects that as drift).
            return behind ?: unexpectedChain ?: throw error
        }
        highestFinalizedHeight.accumulateAndGet(identity.finalizedBlockNumber, ::maxOf)
        return identity
    }

    private suspend fun readFinalizedIdentity(endpoint: Endpoint): Sora2FinalizedRuntimeIdentity {
        val genesisHash = readGenesisHash(endpoint)
        val finalizedHash = executeOn(
            target = endpoint,
            method = METHOD_FINALIZED_HEAD,
            params = emptyList(),
            maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.STRING,
        ).requireCanonicalHashResult("SORA2_RUNTIME_RPC_FINALIZED_INVALID")
        val runtimeVersionElement = executeOn(
            target = endpoint,
            method = METHOD_RUNTIME_VERSION,
            params = listOf(JsonPrimitive(finalizedHash)),
            maximumResponseBytes = MAXIMUM_RUNTIME_VERSION_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.RUNTIME_VERSION,
        )
        val runtimeVersion = runtimeVersionElement.requireRuntimeVersion(gson)
        val finalizedBlockNumber = executeOn(
            target = endpoint,
            method = METHOD_HEADER,
            params = listOf(JsonPrimitive(finalizedHash)),
            maximumResponseBytes = MAXIMUM_HEADER_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.STRUCTURED,
        ).requireHeaderBlockNumber()
        currentCoroutineContext().ensureActive()
        return Sora2FinalizedRuntimeIdentity(
            genesisHash = genesisHash,
            finalizedHash = finalizedHash,
            runtimeVersion = runtimeVersion,
            finalizedBlockNumber = finalizedBlockNumber,
        )
    }

    private suspend fun readGenesisHash(endpoint: Endpoint): String = executeOn(
        target = endpoint,
        method = METHOD_BLOCK_HASH,
        params = listOf(JsonPrimitive(0)),
        maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
        resultKind = Sora2RuntimeRpcResultKind.STRING,
    ).requireCanonicalHashResult("SORA2_RUNTIME_RPC_GENESIS_INVALID")

    /** Fetches metadata only at a previously admitted, canonical finalized hash. */
    suspend fun getMetadataAtFinalized(finalizedHash: String): String =
        getMetadataAt(finalizedHash)

    /**
     * The hash of the runtime code (`:code`) in the state of an admitted finalized block: a
     * 32-byte answer that changes whenever the runtime does, with or without a new spec version.
     * Metadata is a function of that code, so a caller that already holds the metadata for this
     * exact hash does not need to download it again.
     */
    internal suspend fun getRuntimeCodeHashAtFinalized(finalizedHash: String): String {
        val canonicalFinalizedHash = finalizedHash.requireCanonicalBlockHash(
            "SORA2_RUNTIME_RPC_FINALIZED_INVALID"
        )
        return readWithFailover { endpoint ->
            executeOn(
                target = endpoint,
                method = METHOD_STORAGE_HASH,
                params = listOf(
                    JsonPrimitive(RUNTIME_CODE_STORAGE_KEY),
                    JsonPrimitive(canonicalFinalizedHash),
                ),
                maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
                resultKind = Sora2RuntimeRpcResultKind.STRING,
            ).requireCanonicalHashResult("SORA2_RUNTIME_RPC_CODE_HASH_INVALID")
        }
    }

    /**
     * Status recovery only: the metadata a pending submission was signed with, read at the exact
     * block hash journaled with it. An endpoint that has discarded that block's state is followed
     * by the archive endpoint. Callers must admit the result by comparing its SHA-256 with the
     * journaled metadata hash.
     */
    internal suspend fun getHistoricMetadataForStatus(blockHash: String): String =
        getMetadataAt(blockHash)

    /** Status recovery only: runtime version at an exact block hash. */
    internal suspend fun getHistoricRuntimeVersionForStatus(blockHash: String): RuntimeVersion {
        val canonicalBlockHash = blockHash.requireCanonicalBlockHash(
            "SORA2_STATUS_BLOCK_HASH_INVALID"
        )
        return readWithFailover { endpoint ->
            executeOn(
                target = endpoint,
                method = METHOD_RUNTIME_VERSION,
                params = listOf(JsonPrimitive(canonicalBlockHash)),
                maximumResponseBytes = MAXIMUM_RUNTIME_VERSION_RESPONSE_BYTES,
                resultKind = Sora2RuntimeRpcResultKind.RUNTIME_VERSION,
            ).requireRuntimeVersion(gson)
        }
    }

    /**
     * `payment_queryInfo` for exact signed bytes, through the reviewed endpoints that supply the
     * runtime, nonce and submission. The fee does not depend on the node the app's WebSocket is
     * connected to.
     */
    suspend fun queryPartialFee(encodedExtrinsic: String): BigInteger {
        if (
            !HEX_PAYLOAD.matches(encodedExtrinsic) ||
            (encodedExtrinsic.length - 2) % 2 != 0 ||
            encodedExtrinsic.length > MAXIMUM_SUBMISSION_EXTRINSIC_HEX_CHARS
        ) {
            throw Sora2RuntimeIdentityException("SORA2_FEE_EXTRINSIC_INVALID")
        }
        val fee = readWithFailover { endpoint ->
            executeOn(
                target = endpoint,
                method = METHOD_QUERY_INFO,
                params = listOf(JsonPrimitive(encodedExtrinsic)),
                maximumResponseBytes = MAXIMUM_FEE_RESPONSE_BYTES,
                resultKind = Sora2RuntimeRpcResultKind.STRUCTURED,
            ).requirePartialFee()
        }
        currentCoroutineContext().ensureActive()
        return fee
    }

    private suspend fun getMetadataAt(blockHash: String): String {
        val canonicalFinalizedHash = blockHash.requireCanonicalBlockHash(
            "SORA2_RUNTIME_RPC_FINALIZED_INVALID"
        )
        return readWithFailover { endpoint ->
            val result = executeOn(
                target = endpoint,
                method = METHOD_METADATA,
                params = listOf(JsonPrimitive(canonicalFinalizedHash)),
                maximumResponseBytes = MAXIMUM_METADATA_RESPONSE_BYTES,
                resultKind = Sora2RuntimeRpcResultKind.STRING,
            )
            if (!result.isJsonPrimitive || !result.asJsonPrimitive.isString) {
                throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_METADATA_INVALID")
            }
            val metadata = requireRuntimePayloadWithinLimit(
                content = result.asString,
                maxBytes = MAXIMUM_METADATA_RESPONSE_BYTES,
                errorCode = "SORA2_RUNTIME_METADATA_TOO_LARGE",
            )
            // Validate the complete hex shape before the caller hashes or parses it. The hash is
            // intentionally not retained here; RuntimeManager binds it to the exact type bytes.
            metadata.runtimeMetadataSha256()
            currentCoroutineContext().ensureActive()
            metadata
        }
    }

    /**
     * Fetches the exact unsigned System account nonce through the same bounded, strict HTTP
     * envelope used for mutation-authorizing runtime identity. The raw JSON number lexeme is
     * admitted before Gson can normalize it; floating point, exponent notation, quoted numbers,
     * signs, and values outside the runtime's u32 `Index` type fail closed.
     */
    suspend fun getAccountNextIndex(accountAddress: String): BigInteger {
        val publicKey = sora2AddressCodec.soraPublicKeyOrNull(accountAddress)
            ?: throw Sora2RuntimeIdentityException(
                "SORA2_RUNTIME_RPC_ACCOUNT_INVALID"
            )
        if (sora2AddressCodec.toSoraAddressOrNull(publicKey) != accountAddress) {
            throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_ACCOUNT_INVALID")
        }
        return readWithFailover { endpoint ->
            val result = executeOn(
                target = endpoint,
                method = METHOD_ACCOUNT_NEXT_INDEX,
                params = listOf(JsonPrimitive(accountAddress)),
                maximumResponseBytes = MAXIMUM_ACCOUNT_NONCE_RESPONSE_BYTES,
                resultKind = Sora2RuntimeRpcResultKind.UNSIGNED_INTEGER,
            )
            if (!result.isJsonPrimitive || !result.asJsonPrimitive.isNumber) {
                throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_NONCE_INVALID")
            }
            try {
                BigInteger(result.asString)
            } catch (_: NumberFormatException) {
                throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_NONCE_INVALID")
            }
        }
    }

    /**
     * Delivers the signed bytes to at most one node with `author_submitExtrinsic`.
     *
     * The next reviewed endpoint is tried only when the previous attempt provably never reached
     * its node: the connection could not be established, so no byte of the request body left
     * this device ([BoundedHttpTextException.requestNotSent]). After any other outcome (an answer
     * of any kind, a timeout, a connection that broke after the body was handed over) the bytes
     * are never offered to another endpoint, because the first node may already have them.
     *
     * The returned node hash must equal the deterministic hash of the exact submitted bytes.
     * [Sora2SubmissionNotSentException] means no endpoint could be connected to and nothing was
     * sent. Any other error after the caller durably arms UNKNOWN remains transport-ambiguous to
     * that caller, which resolves it by hash through [getCanonicalBlock] on either endpoint.
     */
    internal suspend fun submitExtrinsicOnce(encodedExtrinsic: String): String {
        if (
            !HEX_PAYLOAD.matches(encodedExtrinsic) ||
            (encodedExtrinsic.length - 2) % 2 != 0 ||
            encodedExtrinsic.length > MAXIMUM_SUBMISSION_EXTRINSIC_HEX_CHARS
        ) {
            throw Sora2RuntimeIdentityException("SORA2_SUBMISSION_EXTRINSIC_INVALID")
        }
        val expectedHash = try {
            encodedExtrinsic.extrinsicHash().canonicalExtrinsicHash()
        } catch (error: Exception) {
            throw Sora2RuntimeIdentityException("SORA2_SUBMISSION_EXTRINSIC_INVALID", error)
        }
        var notSent: Exception? = null
        for (endpoint in endpointsInPreferenceOrder()) {
            // Reads only: an endpoint that cannot prove it is on the expected chain gets nothing.
            try {
                requireAdmitted(endpoint)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                noteFailure(endpoint, error)
                notSent = notSent?.also { it.addSuppressed(error) } ?: error
                continue
            }
            val returned = try {
                executeOn(
                    target = endpoint,
                    method = METHOD_SUBMIT_EXTRINSIC,
                    params = listOf(JsonPrimitive(encodedExtrinsic)),
                    maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
                    resultKind = Sora2RuntimeRpcResultKind.STRING,
                )
            } catch (error: BoundedHttpTextException) {
                noteFailure(endpoint, error)
                if (!error.requestNotSent) throw error
                notSent = notSent?.also { it.addSuppressed(error) } ?: error
                continue
            }
            noteReachable(endpoint)
            val returnedHash = returned.requireCanonicalHashResult("SORA2_SUBMISSION_HASH_INVALID")
            if (returnedHash != expectedHash) {
                throw Sora2RuntimeIdentityException("SORA2_SUBMISSION_HASH_MISMATCH")
            }
            currentCoroutineContext().ensureActive()
            return returnedHash
        }
        throw Sora2SubmissionNotSentException(checkNotNull(notSent))
    }

    /**
     * Reads one canonical block through the same bounded, redirect-rejecting transport used for
     * mutation identity; the block hash and the block come from the same endpoint, and only from
     * an endpoint on which [blockNumber] is itself finalized ([requireFinalizedOn]). Only hashes
     * requested by the bounded recovery pass are retained.
     */
    internal suspend fun getCanonicalBlock(
        blockNumber: Long,
        finalizedHeight: Long,
        recoveryHashes: Set<String>,
    ): Sora2CanonicalBlock {
        if (
            blockNumber < 0L ||
            blockNumber > finalizedHeight ||
            recoveryHashes.isEmpty() ||
            recoveryHashes.size > MAXIMUM_RECOVERY_HASHES
        ) {
            throw Sora2RuntimeIdentityException("SORA2_STATUS_BLOCK_REQUEST_INVALID")
        }
        val canonicalRecoveryHashes = recoveryHashes.mapTo(linkedSetOf()) {
            it.canonicalExtrinsicHash()
        }
        if (canonicalRecoveryHashes.size != recoveryHashes.size) {
            throw Sora2RuntimeIdentityException("SORA2_STATUS_HASH_SET_INVALID")
        }
        return readWithFailover { endpoint ->
            requireFinalizedOn(endpoint, blockNumber)
            val blockHash = executeOn(
                target = endpoint,
                method = METHOD_BLOCK_HASH,
                params = listOf(JsonPrimitive(blockNumber)),
                maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
                resultKind = Sora2RuntimeRpcResultKind.STRING,
            ).requireCanonicalHashResult("SORA2_STATUS_BLOCK_HASH_INVALID")
            val block = executeOn(
                target = endpoint,
                method = METHOD_BLOCK,
                params = listOf(JsonPrimitive(blockHash)),
                maximumResponseBytes = MAXIMUM_BLOCK_RESPONSE_BYTES,
                resultKind = Sora2RuntimeRpcResultKind.STRUCTURED,
            ).requireCanonicalBlock(
                expectedBlockNumber = blockNumber,
                recoveryHashes = canonicalRecoveryHashes,
            )
            currentCoroutineContext().ensureActive()
            Sora2CanonicalBlock(
                blockHash = blockHash,
                blockNumber = blockNumber,
                matchingExtrinsicIndices = block,
            )
        }
    }

    /**
     * Bounded raw SCALE storage at an admitted canonical block hash. When one endpoint has
     * discarded that block's state, the same exact read is answered by the archive endpoint.
     */
    internal suspend fun getStorageAt(
        storageKey: String,
        blockHash: String,
    ): String {
        val canonicalBlockHash = blockHash.requireCanonicalBlockHash(
            "SORA2_STATUS_STORAGE_BLOCK_INVALID"
        )
        if (!HEX_PAYLOAD.matches(storageKey) || storageKey.length > MAXIMUM_STORAGE_KEY_CHARS) {
            throw Sora2RuntimeIdentityException("SORA2_STATUS_STORAGE_KEY_INVALID")
        }
        return readWithFailover { endpoint ->
            val raw = executeOn(
                target = endpoint,
                method = METHOD_STORAGE,
                params = listOf(
                    JsonPrimitive(storageKey.lowercase()),
                    JsonPrimitive(canonicalBlockHash),
                ),
                maximumResponseBytes = MAXIMUM_STORAGE_RESPONSE_BYTES,
                resultKind = Sora2RuntimeRpcResultKind.STRING,
            )
            if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isString) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_STORAGE_INVALID")
            }
            val storage = raw.asString
            if (
                !HEX_PAYLOAD.matches(storage) ||
                (storage.length - 2) % 2 != 0 ||
                storage.length > MAXIMUM_STORAGE_HEX_CHARS
            ) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_STORAGE_INVALID")
            }
            currentCoroutineContext().ensureActive()
            storage.lowercase()
        }
    }

    /**
     * Runs one logical read on one endpoint and, when that endpoint gives no acceptable answer,
     * runs the whole read again on the next. Reads change nothing on the chain, and each answer
     * is validated by [read] in the same way whichever endpoint gave it, so asking again is safe
     * for every kind of failure: no connection, a timeout, a malformed answer, a JSON-RPC error.
     *
     * When no endpoint answers, the failure of the endpoint tried first is thrown and the others
     * are attached to it as suppressed.
     */
    private suspend fun <T> readWithFailover(
        admitFirst: Boolean = true,
        read: suspend (Endpoint) -> T,
    ): T {
        var firstFailure: Exception? = null
        for (endpoint in endpointsInPreferenceOrder()) {
            try {
                if (admitFirst) requireAdmitted(endpoint)
                val value = read(endpoint)
                noteReachable(endpoint)
                return value
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                noteFailure(endpoint, error)
                firstFailure = firstFailure?.also { it.addSuppressed(error) } ?: error
            }
        }
        throw checkNotNull(firstFailure)
    }

    /**
     * `chain_getBlockHash(n)` answers from a node's best chain, which above its finalized head
     * can still be replaced. [finalizedHeight][getCanonicalBlock] may have come from the other
     * endpoint, so with more than one endpoint the block at [blockNumber] is read from
     * [endpoint] only once this endpoint's own finalized head has reached it. An endpoint that
     * is behind is not used for that block; the other endpoint, or a later pass, reads it.
     */
    private suspend fun requireFinalizedOn(endpoint: Endpoint, blockNumber: Long) {
        if (endpoints.size == 1 || endpoint.finalizedHeight >= blockNumber) return
        val finalizedHash = executeOn(
            target = endpoint,
            method = METHOD_FINALIZED_HEAD,
            params = emptyList(),
            maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.STRING,
        ).requireCanonicalHashResult("SORA2_RUNTIME_RPC_FINALIZED_INVALID")
        val finalizedNumber = executeOn(
            target = endpoint,
            method = METHOD_HEADER,
            params = listOf(JsonPrimitive(finalizedHash)),
            maximumResponseBytes = MAXIMUM_HEADER_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.STRUCTURED,
        ).requireHeaderBlockNumber()
        endpoint.finalizedHeight = maxOf(endpoint.finalizedHeight, finalizedNumber)
        if (finalizedNumber < blockNumber) {
            throw Sora2RuntimeIdentityException(ENDPOINT_BEHIND)
        }
    }

    /**
     * With more than one endpoint, an endpoint may serve a request only after it has itself
     * reported the expected genesis hash in this process. Otherwise a nonce, a fee or a block
     * could come from a host that serves another chain under a reviewed name. A single endpoint
     * cannot be mixed with another one; its chain is checked by the caller of
     * [getFinalizedIdentity], as before.
     */
    private suspend fun requireAdmitted(endpoint: Endpoint) {
        if (endpoints.size == 1 || endpoint.admittedGenesisHash != null) return
        if (!admitGenesis(endpoint, readGenesisHash(endpoint))) {
            throw Sora2RuntimeIdentityException(ENDPOINT_GENESIS_MISMATCH)
        }
    }

    /** Records what [endpoint] reported and returns whether it is on the expected chain. */
    private fun admitGenesis(endpoint: Endpoint, observedGenesisHash: String): Boolean {
        if (endpoints.size == 1) return true
        val expected = referenceGenesisHash.updateAndGet { it ?: observedGenesisHash }
        val admitted = observedGenesisHash.equals(expected, ignoreCase = true)
        endpoint.admittedGenesisHash = observedGenesisHash.takeIf { admitted }
        return admitted
    }

    /** Endpoints in reviewed order, with those that could not be reached just now moved last. */
    private fun endpointsInPreferenceOrder(): List<Endpoint> {
        if (endpoints.size == 1) return endpoints
        val now = elapsedMillis()
        return endpoints.sortedBy { endpoint ->
            val avoidUntil = endpoint.avoidUntilElapsedMillis
            if (avoidUntil != null && now < avoidUntil) 1 else 0
        }
    }

    private fun noteReachable(endpoint: Endpoint) {
        endpoint.consecutiveTransportFailures = 0
        endpoint.avoidUntilElapsedMillis = null
    }

    /**
     * Only a transport failure moves an endpoint behind the others, for a time that doubles
     * while it keeps failing. It is still tried when the others fail too, so nothing is ever
     * locked out.
     */
    private fun noteFailure(endpoint: Endpoint, error: Exception) {
        if (error !is IOException) return
        val failures = (endpoint.consecutiveTransportFailures + 1).coerceAtMost(16)
        endpoint.consecutiveTransportFailures = failures
        val pause = minOf(
            ENDPOINT_AVOID_BASE_MILLIS shl (failures - 1).coerceAtMost(10),
            ENDPOINT_AVOID_MAXIMUM_MILLIS,
        )
        endpoint.avoidUntilElapsedMillis = elapsedMillis() + pause
    }

    private suspend fun executeOn(
        target: Endpoint,
        method: String,
        params: List<JsonElement>,
        maximumResponseBytes: Int,
        resultKind: Sora2RuntimeRpcResultKind,
    ): JsonElement {
        val requestId = requestIds.incrementAndGet()
        if (requestId <= 0) {
            throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_ID_EXHAUSTED")
        }
        val request = JsonObject().apply {
            addProperty(FIELD_JSON_RPC, JSON_RPC_VERSION)
            addProperty(FIELD_ID, requestId)
            addProperty(FIELD_METHOD, method)
            add(FIELD_PARAMS, gson.toJsonTree(params))
        }
        val rawResponse = target.transport.post(
            requestBody = gson.toJson(request),
            maximumResponseBytes = maximumResponseBytes,
        )
        currentCoroutineContext().ensureActive()
        return requireJsonRpcResult(rawResponse, requestId, resultKind)
    }

    private fun requireJsonRpcResult(
        rawResponse: String,
        expectedId: Long,
        resultKind: Sora2RuntimeRpcResultKind,
    ): JsonElement {
        var version: String? = null
        var responseId: String? = null
        var result: JsonElement? = null
        var hasResult = false
        var hasError = false
        var remoteError: Sora2RuntimeRpcRemoteException? = null
        val fields = mutableSetOf<String>()
        try {
            JsonReader(StringReader(rawResponse)).use { reader ->
                reader.isLenient = false
                if (reader.peek() != JsonToken.BEGIN_OBJECT) invalidResponse()
                reader.beginObject()
                while (reader.hasNext()) {
                    val field = reader.nextName()
                    if (!fields.add(field)) invalidResponse()
                    when (field) {
                        FIELD_JSON_RPC -> {
                            if (reader.peek() != JsonToken.STRING) invalidResponse()
                            version = reader.nextString()
                        }
                        FIELD_ID -> {
                            if (reader.peek() != JsonToken.NUMBER) invalidResponse()
                            responseId = reader.nextString()
                        }
                        FIELD_RESULT -> {
                            hasResult = true
                            result = readStrictResult(reader, resultKind)
                        }
                        FIELD_ERROR -> {
                            hasError = true
                            remoteError = readStrictJsonRpcError(reader)
                        }
                        else -> {
                            reader.skipValue()
                            invalidResponse()
                        }
                    }
                }
                reader.endObject()
                if (reader.peek() != JsonToken.END_DOCUMENT) invalidResponse()
            }
        } catch (error: Sora2RuntimeIdentityException) {
            throw error
        } catch (_: Exception) {
            invalidResponse()
        }
        val expectedFields = if (hasResult && !hasError) {
            RESULT_ENVELOPE_FIELDS
        } else if (hasError && !hasResult) {
            ERROR_ENVELOPE_FIELDS
        } else {
            invalidResponse()
        }
        if (fields != expectedFields) invalidResponse()
        if (version != JSON_RPC_VERSION) {
            throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_VERSION_INVALID")
        }
        if (responseId != expectedId.toString()) {
            throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_ID_MISMATCH")
        }
        if (hasError) {
            throw remoteError ?: Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_REMOTE_ERROR")
        }
        return result ?: throw Sora2RuntimeIdentityException(
            "SORA2_RUNTIME_RPC_RESULT_MISSING"
        )
    }

    private fun readStrictResult(
        reader: JsonReader,
        resultKind: Sora2RuntimeRpcResultKind,
    ): JsonElement {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_RESULT_MISSING")
        }
        return when (resultKind) {
            Sora2RuntimeRpcResultKind.STRING -> {
                if (reader.peek() != JsonToken.STRING) invalidResponse()
                JsonPrimitive(reader.nextString())
            }
            Sora2RuntimeRpcResultKind.RUNTIME_VERSION ->
                readStrictRuntimeVersionObject(reader)
            Sora2RuntimeRpcResultKind.UNSIGNED_INTEGER -> {
                if (reader.peek() != JsonToken.NUMBER) invalidResponse()
                JsonPrimitive(
                    requireCanonicalAccountNonceRaw(reader.nextString())
                )
            }
            Sora2RuntimeRpcResultKind.STRUCTURED -> readStrictJsonValue(
                reader = reader,
                depth = 0,
                tokenCount = intArrayOf(0),
            )
        }
    }

    private fun readStrictRuntimeVersionObject(reader: JsonReader): JsonObject {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) invalidResponse()
        val result = JsonObject()
        val fields = mutableSetOf<String>()
        val tokenCount = intArrayOf(0)
        reader.beginObject()
        while (reader.hasNext()) {
            val field = reader.nextName()
            if (!fields.add(field)) invalidResponse()
            admitStructuredToken(tokenCount)
            if (field == FIELD_SPEC_VERSION || field == FIELD_TRANSACTION_VERSION) {
                if (reader.peek() != JsonToken.NUMBER) invalidResponse()
                result.addProperty(
                    field,
                    requireCanonicalJsonIntegerRaw(
                        raw = reader.nextString(),
                        errorCode = "SORA2_RUNTIME_RPC_VERSION_RESULT_INVALID",
                        allowNegative = false,
                    ),
                )
            } else {
                result.add(
                    field,
                    readStrictJsonValue(
                        reader = reader,
                        depth = 1,
                        tokenCount = tokenCount,
                    ),
                )
            }
        }
        reader.endObject()
        return result
    }

    private fun readStrictJsonValue(
        reader: JsonReader,
        depth: Int,
        tokenCount: IntArray,
    ): JsonElement {
        if (depth > MAXIMUM_STRUCTURED_JSON_DEPTH) invalidResponse()
        admitStructuredToken(tokenCount)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val result = JsonObject()
                val fields = mutableSetOf<String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val field = reader.nextName()
                    if (!fields.add(field)) invalidResponse()
                    result.add(field, readStrictJsonValue(reader, depth + 1, tokenCount))
                }
                reader.endObject()
                result
            }
            JsonToken.BEGIN_ARRAY -> {
                val result = JsonArray()
                reader.beginArray()
                while (reader.hasNext()) {
                    result.add(readStrictJsonValue(reader, depth + 1, tokenCount))
                }
                reader.endArray()
                result
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> {
                val raw = reader.nextString()
                if (raw.length > MAXIMUM_JSON_NUMBER_CHARS || !JSON_NUMBER.matches(raw)) {
                    invalidResponse()
                }
                val number = try {
                    BigDecimal(raw)
                } catch (_: NumberFormatException) {
                    invalidResponse()
                }
                JsonPrimitive(number)
            }
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> {
                reader.nextNull()
                JsonNull.INSTANCE
            }
            else -> invalidResponse()
        }
    }

    private fun admitStructuredToken(tokenCount: IntArray) {
        tokenCount[0] += 1
        if (tokenCount[0] > MAXIMUM_STRUCTURED_JSON_TOKENS) invalidResponse()
    }

    private fun readStrictJsonRpcError(reader: JsonReader): Sora2RuntimeRpcRemoteException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) invalidResponse()
        var code: String? = null
        var message: String? = null
        var data: String? = null
        val fields = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val field = reader.nextName()
            if (!fields.add(field)) invalidResponse()
            when (field) {
                FIELD_ERROR_CODE -> {
                    if (reader.peek() != JsonToken.NUMBER) invalidResponse()
                    code = reader.nextString()
                }
                FIELD_ERROR_MESSAGE -> {
                    if (reader.peek() != JsonToken.STRING) invalidResponse()
                    message = reader.nextString()
                }
                FIELD_ERROR_DATA -> if (reader.peek() == JsonToken.STRING) {
                    data = reader.nextString().take(MAXIMUM_ERROR_MESSAGE_CHARS)
                } else {
                    reader.skipValue()
                }
                else -> {
                    reader.skipValue()
                    invalidResponse()
                }
            }
        }
        reader.endObject()
        if (
            !fields.containsAll(REQUIRED_ERROR_FIELDS) ||
            !ALLOWED_ERROR_FIELDS.containsAll(fields)
        ) {
            invalidResponse()
        }
        val admittedCode = requireCanonicalJsonIntegerRaw(
            raw = code ?: invalidResponse(),
            errorCode = "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
            allowNegative = true,
        )
        val admittedMessage = message ?: invalidResponse()
        if (
            admittedMessage.isBlank() ||
            admittedMessage.length > MAXIMUM_ERROR_MESSAGE_CHARS
        ) {
            invalidResponse()
        }
        return Sora2RuntimeRpcRemoteException(
            code = admittedCode.toLong(),
            remoteMessage = admittedMessage,
            remoteData = data,
        )
    }

    private fun invalidResponse(): Nothing {
        throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_RESPONSE_INVALID")
    }

    companion object {
        internal const val MAXIMUM_HASH_RESPONSE_BYTES = 4 * 1024
        internal const val MAXIMUM_ACCOUNT_NONCE_RESPONSE_BYTES = 4 * 1024
        internal const val MAXIMUM_RUNTIME_VERSION_RESPONSE_BYTES = 64 * 1024
        internal const val MAXIMUM_HEADER_RESPONSE_BYTES = 64 * 1024
        internal const val MAXIMUM_BLOCK_RESPONSE_BYTES = 4 * 1024 * 1024
        internal const val MAXIMUM_STORAGE_RESPONSE_BYTES = 4 * 1024 * 1024
        // The same limit RuntimeManager applies to metadata it parses and caches, and the
        // largest response the bounded HTTP client admits at all. Runtime-133 metadata is
        // 1.19 MiB on the wire and grew by 57 % in one upgrade (May 2026); a lower limit here
        // would stop every fee quote and transfer until an app update the day it is crossed.
        internal const val MAXIMUM_METADATA_RESPONSE_BYTES = 16 * 1024 * 1024

        private const val METHOD_BLOCK_HASH = "chain_getBlockHash"
        private const val METHOD_FINALIZED_HEAD = "chain_getFinalizedHead"
        private const val METHOD_RUNTIME_VERSION = "state_getRuntimeVersion"
        private const val METHOD_METADATA = "state_getMetadata"
        private const val METHOD_ACCOUNT_NEXT_INDEX = "system_accountNextIndex"
        private const val METHOD_SUBMIT_EXTRINSIC = "author_submitExtrinsic"
        private const val METHOD_HEADER = "chain_getHeader"
        private const val METHOD_BLOCK = "chain_getBlock"
        private const val METHOD_STORAGE = "state_getStorage"
        private const val METHOD_STORAGE_HASH = "state_getStorageHash"

        /** The well-known storage key `:code`. */
        private const val RUNTIME_CODE_STORAGE_KEY = "0x3a636f6465"
        private const val ENDPOINT_GENESIS_MISMATCH = "SORA2_RUNTIME_RPC_ENDPOINT_GENESIS_MISMATCH"
        private const val ENDPOINT_BEHIND = "SORA2_RUNTIME_RPC_ENDPOINT_BEHIND"
        private const val ENDPOINT_AVOID_BASE_MILLIS = 30_000L
        private const val ENDPOINT_AVOID_MAXIMUM_MILLIS = 5 * 60_000L
        private const val METHOD_QUERY_INFO = "payment_queryInfo"
        private const val FIELD_PARTIAL_FEE = "partialFee"
        private const val MAXIMUM_FEE_RESPONSE_BYTES = 16 * 1024
        private const val MAXIMUM_FEE_DIGITS = 39
        private val U128_MAX: BigInteger = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
        private const val JSON_RPC_VERSION = "2.0"
        private const val FIELD_JSON_RPC = "jsonrpc"
        private const val FIELD_ID = "id"
        private const val FIELD_METHOD = "method"
        private const val FIELD_PARAMS = "params"
        private const val FIELD_RESULT = "result"
        private const val FIELD_ERROR = "error"
        private const val FIELD_ERROR_CODE = "code"
        private const val FIELD_ERROR_MESSAGE = "message"
        private const val FIELD_ERROR_DATA = "data"
        private const val FIELD_SPEC_VERSION = "specVersion"
        private const val FIELD_TRANSACTION_VERSION = "transactionVersion"
        private const val MAXIMUM_ERROR_MESSAGE_CHARS = 1_024
        private const val MAXIMUM_JSON_INTEGER_CHARS = 16
        private const val MAXIMUM_ACCOUNT_NONCE_CHARS = 10
        private const val MAXIMUM_JSON_NUMBER_CHARS = 64
        private const val MAXIMUM_STRUCTURED_JSON_DEPTH = 32
        private const val MAXIMUM_STRUCTURED_JSON_TOKENS = 4_096
        private const val MAXIMUM_RECOVERY_HASHES = 16
        private const val MAXIMUM_BLOCK_EXTRINSICS = 2_048
        private const val MAXIMUM_EXTRINSIC_HEX_CHARS = 512 * 1024 + 2
        // Leaves ample JSON-envelope headroom below BoundedHttpTextClient's 256 KiB request cap.
        private const val MAXIMUM_SUBMISSION_EXTRINSIC_HEX_CHARS = 240 * 1024
        private const val MAXIMUM_STORAGE_KEY_CHARS = 1_024
        private const val MAXIMUM_STORAGE_HEX_CHARS = 4 * 1024 * 1024
        private val HEX_PAYLOAD = Regex("^0x[0-9a-fA-F]+$")
        private val HEADER_NUMBER = Regex("^0x[0-9a-fA-F]{1,16}$")
        private val RESULT_ENVELOPE_FIELDS = setOf(FIELD_JSON_RPC, FIELD_ID, FIELD_RESULT)
        private val ERROR_ENVELOPE_FIELDS = setOf(FIELD_JSON_RPC, FIELD_ID, FIELD_ERROR)
        private val REQUIRED_ERROR_FIELDS = setOf(FIELD_ERROR_CODE, FIELD_ERROR_MESSAGE)
        private val ALLOWED_ERROR_FIELDS = setOf(
            FIELD_ERROR_CODE,
            FIELD_ERROR_MESSAGE,
            FIELD_ERROR_DATA,
        )

        private fun JsonElement.requirePartialFee(): BigInteger {
            val partialFee = takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get(FIELD_PARTIAL_FEE)
                ?.takeIf { it.isJsonPrimitive }
                ?.asJsonPrimitive
                ?: throw Sora2RuntimeIdentityException("SORA2_FEE_RESULT_INVALID")
            // Substrate sends the u128 fee as a decimal string; older nodes sent a JSON number.
            val raw = if (partialFee.isString) partialFee.asString else partialFee.asBigDecimal
                .let { number ->
                    try {
                        number.toBigIntegerExact().toString()
                    } catch (_: ArithmeticException) {
                        throw Sora2RuntimeIdentityException("SORA2_FEE_RESULT_INVALID")
                    }
                }
            if (raw.length > MAXIMUM_FEE_DIGITS || !UNSIGNED_JSON_INTEGER.matches(raw)) {
                throw Sora2RuntimeIdentityException("SORA2_FEE_RESULT_INVALID")
            }
            val fee = BigInteger(raw)
            if (fee > U128_MAX) {
                throw Sora2RuntimeIdentityException("SORA2_FEE_RESULT_INVALID")
            }
            return fee
        }

        private fun JsonElement.requireHeaderBlockNumber(): Long {
            if (!isJsonObject) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_HEADER_INVALID")
            }
            val number = asJsonObject.get("number")
            if (
                number == null ||
                !number.isJsonPrimitive ||
                !number.asJsonPrimitive.isString ||
                !HEADER_NUMBER.matches(number.asString)
            ) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_HEADER_INVALID")
            }
            return try {
                val parsed = BigInteger(number.asString.removePrefix("0x"), 16)
                if (parsed > BigInteger.valueOf(Long.MAX_VALUE)) {
                    throw ArithmeticException("Header number exceeds Long range")
                }
                parsed.toLong()
            } catch (_: ArithmeticException) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_HEADER_INVALID")
            } catch (_: NumberFormatException) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_HEADER_INVALID")
            }
        }

        private fun JsonElement.requireCanonicalBlock(
            expectedBlockNumber: Long,
            recoveryHashes: Set<String>,
        ): Map<String, Int> {
            if (!isJsonObject) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_BLOCK_INVALID")
            }
            val root = asJsonObject
            if (root.keySet().any { it !in setOf("block", "justifications", "justification") }) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_BLOCK_INVALID")
            }
            val block = root.get("block")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw Sora2RuntimeIdentityException("SORA2_STATUS_BLOCK_INVALID")
            if (block.keySet() != setOf("header", "extrinsics")) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_BLOCK_INVALID")
            }
            if (block.get("header").requireHeaderBlockNumber() != expectedBlockNumber) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_BLOCK_NUMBER_MISMATCH")
            }
            val extrinsics = block.get("extrinsics")
            if (extrinsics == null || !extrinsics.isJsonArray) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_BLOCK_INVALID")
            }
            val encodedExtrinsics = extrinsics.asJsonArray
            if (encodedExtrinsics.size() > MAXIMUM_BLOCK_EXTRINSICS) {
                throw Sora2RuntimeIdentityException("SORA2_STATUS_BLOCK_TOO_LARGE")
            }
            val matches = linkedMapOf<String, Int>()
            encodedExtrinsics.forEachIndexed { index, element ->
                if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
                    throw Sora2RuntimeIdentityException("SORA2_STATUS_EXTRINSIC_INVALID")
                }
                val encoded = element.asString
                if (
                    !HEX_PAYLOAD.matches(encoded) ||
                    (encoded.length - 2) % 2 != 0 ||
                    encoded.length > MAXIMUM_EXTRINSIC_HEX_CHARS
                ) {
                    throw Sora2RuntimeIdentityException("SORA2_STATUS_EXTRINSIC_INVALID")
                }
                val hash = try {
                    encoded.extrinsicHash().canonicalExtrinsicHash()
                } catch (error: Exception) {
                    throw Sora2RuntimeIdentityException(
                        "SORA2_STATUS_EXTRINSIC_INVALID",
                        error,
                    )
                }
                if (hash in recoveryHashes && matches.put(hash, index) != null) {
                    throw Sora2RuntimeIdentityException(
                        "SORA2_STATUS_DUPLICATE_EXTRINSIC"
                    )
                }
            }
            return matches
        }

        private fun JsonElement.requireRuntimeVersion(gson: Gson): RuntimeVersion {
            if (!isJsonObject) {
                throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_VERSION_RESULT_INVALID")
            }
            val versionObject = asJsonObject
            val specVersion = versionObject.get(FIELD_SPEC_VERSION).requireCanonicalJsonInteger(
                errorCode = "SORA2_RUNTIME_RPC_VERSION_RESULT_INVALID",
                allowNegative = false,
            )
            val transactionVersion = versionObject.get(FIELD_TRANSACTION_VERSION)
                .requireCanonicalJsonInteger(
                    errorCode = "SORA2_RUNTIME_RPC_VERSION_RESULT_INVALID",
                    allowNegative = false,
                )
            if (specVersion <= 0) {
                throw Sora2RuntimeIdentityException(
                    "SORA2_RUNTIME_RPC_VERSION_RESULT_INVALID"
                )
            }
            val decoded = try {
                gson.fromJson(this, RuntimeVersion::class.java)
            } catch (_: Exception) {
                throw Sora2RuntimeIdentityException(
                    "SORA2_RUNTIME_RPC_VERSION_RESULT_INVALID"
                )
            } ?: throw Sora2RuntimeIdentityException(
                "SORA2_RUNTIME_RPC_VERSION_RESULT_INVALID"
            )
            if (
                decoded.specVersion != specVersion ||
                decoded.transactionVersion != transactionVersion
            ) {
                throw Sora2RuntimeIdentityException(
                    "SORA2_RUNTIME_RPC_VERSION_RESULT_INVALID"
                )
            }
            return decoded
        }

        private fun JsonElement?.requireCanonicalJsonInteger(
            errorCode: String,
            allowNegative: Boolean,
        ): Int {
            if (this == null || !isJsonPrimitive || !asJsonPrimitive.isNumber) {
                throw Sora2RuntimeIdentityException(errorCode)
            }
            return requireCanonicalJsonIntegerRaw(
                raw = asJsonPrimitive.asString,
                errorCode = errorCode,
                allowNegative = allowNegative,
            )
        }

        private fun requireCanonicalJsonIntegerRaw(
            raw: String,
            errorCode: String,
            allowNegative: Boolean,
        ): Int {
            val syntax = if (allowNegative) SIGNED_JSON_INTEGER else UNSIGNED_JSON_INTEGER
            if (raw.length > MAXIMUM_JSON_INTEGER_CHARS || !syntax.matches(raw)) {
                throw Sora2RuntimeIdentityException(errorCode)
            }
            val value = try {
                BigInteger(raw)
            } catch (_: NumberFormatException) {
                throw Sora2RuntimeIdentityException(errorCode)
            }
            if (value < INT_MIN || value > INT_MAX) {
                throw Sora2RuntimeIdentityException(errorCode)
            }
            return value.toInt()
        }

        private fun requireCanonicalAccountNonceRaw(raw: String): BigInteger {
            if (
                raw.length > MAXIMUM_ACCOUNT_NONCE_CHARS ||
                !UNSIGNED_JSON_INTEGER.matches(raw)
            ) {
                throw Sora2RuntimeIdentityException(
                    "SORA2_RUNTIME_RPC_NONCE_INVALID"
                )
            }
            val value = try {
                BigInteger(raw)
            } catch (_: NumberFormatException) {
                throw Sora2RuntimeIdentityException(
                    "SORA2_RUNTIME_RPC_NONCE_INVALID"
                )
            }
            if (value > ACCOUNT_NONCE_MAX) {
                throw Sora2RuntimeIdentityException(
                    "SORA2_RUNTIME_RPC_NONCE_INVALID"
                )
            }
            return value
        }

        private fun JsonElement.requireCanonicalHashResult(errorCode: String): String {
            if (!isJsonPrimitive || !asJsonPrimitive.isString) {
                throw Sora2RuntimeIdentityException(errorCode)
            }
            return asString.requireCanonicalBlockHash(errorCode)
        }

        private val SIGNED_JSON_INTEGER = Regex("^-?(?:0|[1-9][0-9]*)$")
        private val UNSIGNED_JSON_INTEGER = Regex("^(?:0|[1-9][0-9]*)$")
        private val JSON_NUMBER = Regex(
            "^-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?$",
        )
        private val INT_MIN: BigInteger = BigInteger.valueOf(Int.MIN_VALUE.toLong())
        private val INT_MAX: BigInteger = BigInteger.valueOf(Int.MAX_VALUE.toLong())
        private val ACCOUNT_NONCE_MAX = BigInteger("4294967295")
    }
}
