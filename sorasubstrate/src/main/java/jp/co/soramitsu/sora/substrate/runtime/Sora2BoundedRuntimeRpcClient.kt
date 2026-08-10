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
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.sora.substrate.substrate.canonicalExtrinsicHash
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Narrow transport seam for deterministic protocol tests. Production always constructs the
 * implementation below, whose endpoint is the reviewed runtime-manifest endpoint constant.
 */
internal fun interface Sora2RuntimeRpcTransport {
    suspend fun post(requestBody: String, maximumResponseBytes: Int): String
}

private class ManifestBoundSora2RuntimeRpcTransport(
    private val client: BoundedHttpTextClient,
) : Sora2RuntimeRpcTransport {
    override suspend fun post(
        requestBody: String,
        maximumResponseBytes: Int,
    ): String = client.postJsonUtf8(
        rawUrl = Sora2RuntimeContract.RUNTIME_RPC_ENDPOINT,
        requestBody = requestBody,
        maximumBytes = maximumResponseBytes,
    )
}

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
 * This client deliberately has no selected-node or WebSocket dependency. The HTTPS endpoint is
 * pinned alongside the reviewed metadata contract, JSON POST redirects are rejected by
 * [BoundedHttpTextClient], and a strict streaming envelope admits each result before Gson may
 * materialize only the small, method-bounded runtime-version object.
 */
@Singleton
class Sora2BoundedRuntimeRpcClient internal constructor(
    private val transport: Sora2RuntimeRpcTransport,
    private val gson: Gson,
    private val sora2AddressCodec: Sora2AddressCodec = Sora2AddressCodec(),
) {
    @Inject
    constructor(
        boundedHttpTextClient: BoundedHttpTextClient,
        gson: Gson,
        sora2AddressCodec: Sora2AddressCodec,
    ) : this(
        transport = ManifestBoundSora2RuntimeRpcTransport(boundedHttpTextClient),
        gson = gson,
        sora2AddressCodec = sora2AddressCodec,
    )

    private val requestIds = AtomicLong(0)

    /**
     * Obtains one canonical finalized checkpoint. Runtime version is explicitly queried at that
     * hash, so a caller can fetch metadata at the same immutable checkpoint without mixing heads.
     */
    internal suspend fun getFinalizedIdentity(): Sora2FinalizedRuntimeIdentity {
        val genesisHash = execute(
            method = METHOD_BLOCK_HASH,
            params = listOf(JsonPrimitive(0)),
            maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.STRING,
        ).requireCanonicalHashResult("SORA2_RUNTIME_RPC_GENESIS_INVALID")
        val finalizedHash = execute(
            method = METHOD_FINALIZED_HEAD,
            params = emptyList(),
            maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.STRING,
        ).requireCanonicalHashResult("SORA2_RUNTIME_RPC_FINALIZED_INVALID")
        val runtimeVersionElement = execute(
            method = METHOD_RUNTIME_VERSION,
            params = listOf(JsonPrimitive(finalizedHash)),
            maximumResponseBytes = MAXIMUM_RUNTIME_VERSION_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.RUNTIME_VERSION,
        )
        val runtimeVersion = runtimeVersionElement.requireRuntimeVersion(gson)
        val finalizedBlockNumber = execute(
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

    /** Fetches metadata only at a previously admitted, canonical finalized hash. */
    suspend fun getMetadataAtFinalized(finalizedHash: String): String {
        val canonicalFinalizedHash = finalizedHash.requireCanonicalBlockHash(
            "SORA2_RUNTIME_RPC_FINALIZED_INVALID"
        )
        val result = execute(
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
        return metadata
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
        val result = execute(
            method = METHOD_ACCOUNT_NEXT_INDEX,
            params = listOf(JsonPrimitive(accountAddress)),
            maximumResponseBytes = MAXIMUM_ACCOUNT_NONCE_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.UNSIGNED_INTEGER,
        )
        if (!result.isJsonPrimitive || !result.asJsonPrimitive.isNumber) {
            throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_NONCE_INVALID")
        }
        return try {
            BigInteger(result.asString)
        } catch (_: NumberFormatException) {
            throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_NONCE_INVALID")
        }
    }

    /**
     * Performs exactly one manifest-bound HTTPS `author_submitExtrinsic` attempt. The transport
     * has no retry or alternate endpoint, and the returned node hash must equal the deterministic
     * hash of the exact submitted bytes. Any error after the caller durably arms UNKNOWN remains
     * transport-ambiguous to that caller.
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
        val returnedHash = execute(
            method = METHOD_SUBMIT_EXTRINSIC,
            params = listOf(JsonPrimitive(encodedExtrinsic)),
            maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.STRING,
        ).requireCanonicalHashResult("SORA2_SUBMISSION_HASH_INVALID")
        if (returnedHash != expectedHash) {
            throw Sora2RuntimeIdentityException("SORA2_SUBMISSION_HASH_MISMATCH")
        }
        currentCoroutineContext().ensureActive()
        return returnedHash
    }

    /**
     * Reads one canonical block through the same manifest-bound, redirect-rejecting transport used
     * for mutation identity. Only hashes requested by the bounded recovery pass are retained.
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
        val blockHash = execute(
            method = METHOD_BLOCK_HASH,
            params = listOf(JsonPrimitive(blockNumber)),
            maximumResponseBytes = MAXIMUM_HASH_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.STRING,
        ).requireCanonicalHashResult("SORA2_STATUS_BLOCK_HASH_INVALID")
        val block = execute(
            method = METHOD_BLOCK,
            params = listOf(JsonPrimitive(blockHash)),
            maximumResponseBytes = MAXIMUM_BLOCK_RESPONSE_BYTES,
            resultKind = Sora2RuntimeRpcResultKind.STRUCTURED,
        ).requireCanonicalBlock(
            expectedBlockNumber = blockNumber,
            recoveryHashes = canonicalRecoveryHashes,
        )
        currentCoroutineContext().ensureActive()
        return Sora2CanonicalBlock(
            blockHash = blockHash,
            blockNumber = blockNumber,
            matchingExtrinsicIndices = block,
        )
    }

    /** Returns bounded raw SCALE storage only at an admitted canonical block hash. */
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
        val raw = execute(
            method = METHOD_STORAGE,
            params = listOf(JsonPrimitive(storageKey.lowercase()), JsonPrimitive(canonicalBlockHash)),
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
        return storage.lowercase()
    }

    private suspend fun execute(
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
        val rawResponse = try {
            transport.post(
                requestBody = gson.toJson(request),
                maximumResponseBytes = maximumResponseBytes,
            )
        } catch (error: CancellationException) {
            throw error
        }
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
                            readStrictJsonRpcError(reader)
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
            throw Sora2RuntimeIdentityException("SORA2_RUNTIME_RPC_REMOTE_ERROR")
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

    private fun readStrictJsonRpcError(reader: JsonReader) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) invalidResponse()
        var code: String? = null
        var message: String? = null
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
                FIELD_ERROR_DATA -> reader.skipValue()
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
        requireCanonicalJsonIntegerRaw(
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
        // Reviewed runtime-130 metadata is ~1.24 MiB on wire. Fail closed before parsing if a
        // response grows beyond the reviewed envelope plus a deliberate upgrade margin.
        internal const val MAXIMUM_METADATA_RESPONSE_BYTES = 2 * 1024 * 1024

        private const val METHOD_BLOCK_HASH = "chain_getBlockHash"
        private const val METHOD_FINALIZED_HEAD = "chain_getFinalizedHead"
        private const val METHOD_RUNTIME_VERSION = "state_getRuntimeVersion"
        private const val METHOD_METADATA = "state_getMetadata"
        private const val METHOD_ACCOUNT_NEXT_INDEX = "system_accountNextIndex"
        private const val METHOD_SUBMIT_EXTRINSIC = "author_submitExtrinsic"
        private const val METHOD_HEADER = "chain_getHeader"
        private const val METHOD_BLOCK = "chain_getBlock"
        private const val METHOD_STORAGE = "state_getStorage"
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
                BigInteger(number.asString.removePrefix("0x"), 16).longValueExact()
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
