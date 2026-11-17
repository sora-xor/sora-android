package jp.co.soramitsu.common.nexus

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Current curated Torii MCP contract used exclusively by the public Taira testnet. */
internal object TairaMcpContract {
    const val HEALTH_TOOL = "iroha.health"
    const val ACCOUNT_ASSETS_TOOL = "iroha.accounts.assets"
    const val ASSET_DEFINITION_TOOL = "iroha.assets.definitions.get"
    const val TRANSACTION_STATUS_TOOL = "iroha.transactions.status"
    const val HISTORY_TOOL = "iroha.accounts.history"
    const val SUBMIT_AND_WAIT_TOOL = "iroha.transactions.submit_and_wait"

    private enum class SchemaType(val scalarName: String?) {
        STRING("string"),
        INTEGER("integer"),
        STRING_ARRAY(null),
        OBJECT("object"),
        BOOLEAN("boolean"),
        FILTER(null),
    }

    /** Every property this client may emit, including routed names absent from the old schema. */
    private val requestPropertyTypes = mapOf(
        HEALTH_TOOL to emptyMap(),
        ACCOUNT_ASSETS_TOOL to mapOf(
            "path" to SchemaType.OBJECT, "filter" to SchemaType.FILTER,
            "limit" to SchemaType.INTEGER, "cursor" to SchemaType.STRING,
            "include_total" to SchemaType.BOOLEAN, "accept" to SchemaType.STRING,
        ),
        ASSET_DEFINITION_TOOL to mapOf("path" to SchemaType.OBJECT, "accept" to SchemaType.STRING),
        TRANSACTION_STATUS_TOOL to mapOf("query" to SchemaType.OBJECT, "accept" to SchemaType.STRING),
        HISTORY_TOOL to mapOf(
            "path" to SchemaType.OBJECT, "filter" to SchemaType.FILTER,
            "limit" to SchemaType.INTEGER, "cursor" to SchemaType.STRING,
            "accept" to SchemaType.STRING,
        ),
        SUBMIT_AND_WAIT_TOOL to mapOf(
            "body_base64" to SchemaType.STRING, "hash" to SchemaType.STRING,
            "status_accept" to SchemaType.STRING, "timeout_ms" to SchemaType.INTEGER,
        ),
    )

    private fun protocolMetadata(): JsonObject = JsonObject(mapOf(
        "io.modelcontextprotocol/protocolVersion" to JsonPrimitive(TairaTestnetContract.MCP_PROTOCOL_VERSION),
        "io.modelcontextprotocol/clientCapabilities" to JsonObject(emptyMap()),
    ))

    fun requestHeaders(request: NexusMcpRequest): Map<String, String> = buildMap {
        put("MCP-Protocol-Version", TairaTestnetContract.MCP_PROTOCOL_VERSION)
        put("Mcp-Method", request.method)
        if (request.method == "tools/call") {
            put("Mcp-Name", checkNotNull(request.params?.string("name")))
        }
    }

    fun call(requestId: String, tool: String, arguments: JsonObject): NexusMcpRequest {
        require(tool in requestPropertyTypes) { "TAIRA_MCP_TOOL_NOT_ALLOWED" }
        return NexusMcpRequest(
            jsonrpc = "2.0",
            id = requestId,
            method = "tools/call",
            params = JsonObject(
                mapOf(
                    "_meta" to protocolMetadata(),
                    "name" to JsonPrimitive(tool),
                    "arguments" to arguments,
                )
            ),
        )
    }

    fun submitAndWaitRequest(
        requestId: String,
        signedNorito: ByteArray,
        expectedHash: String,
    ): NexusMcpRequest {
        require(signedNorito.isNotEmpty()) { "NEXUS_EMPTY_SIGNED_TRANSACTION" }
        val canonicalHash = canonicalHash(expectedHash)
        return call(
            requestId,
            SUBMIT_AND_WAIT_TOOL,
            JsonObject(
                mapOf(
                    "body_base64" to JsonPrimitive(Base64.getEncoder().encodeToString(signedNorito)),
                    "hash" to JsonPrimitive(canonicalHash),
                    "status_accept" to JsonPrimitive(
                        NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE
                    ),
                    "timeout_ms" to JsonPrimitive(120_000),
                )
            ),
        )
    }

    private fun JsonElement.matches(expectedType: SchemaType): Boolean {
        val schema = this as? JsonObject ?: return false
        return when (expectedType) {
            SchemaType.STRING,
            SchemaType.INTEGER,
            SchemaType.OBJECT,
            SchemaType.BOOLEAN,
            -> schema.string("type") == expectedType.scalarName
            SchemaType.FILTER -> (schema["type"] as? JsonArray)?.contains(JsonPrimitive("object")) == true
            SchemaType.STRING_ARRAY ->
                schema.string("type") == "array" &&
                    ((schema["items"] as? JsonObject)?.string("type") == "string")
        }
    }

    fun validateSubmitAndWaitResult(
        json: Json,
        result: JsonElement,
        expectedHash: String,
    ): NexusSubmissionReceipt {
        val canonicalExpected = canonicalHash(expectedHash)
        val direct = result as? JsonObject ?: submissionInvalid()
        if (direct.strictBoolean("isError") != false) submissionFailed()
        val structured = direct["structuredContent"] as? JsonObject ?: submissionInvalid()
        val status = structured.strictInt("status") ?: submissionInvalid()
        if (status !in 200..299) submissionFailed(status)
        requireMatchingHash(structured.string("hash"), canonicalExpected)
        if (
            structured.string("terminal_kind") != "Applied"
        ) {
            submissionFailed()
        }
        if ((structured.strictLong("attempts") ?: 0) <= 0) submissionInvalid()
        if ((structured.strictLong("elapsed_ms") ?: -1) < 0) submissionInvalid()

        val submit = structured["submit"] as? JsonObject ?: submissionInvalid()
        val final = structured["final"] as? JsonObject ?: submissionInvalid()
        val submitRoute = NexusMcpResultContract.validateEmbeddedRoute(
            wrapStructured(submit),
            requireFanout = false,
            allowPublicTairaRoute = true,
        )
        val finalRoute = NexusMcpResultContract.validateEmbeddedRoute(
            wrapStructured(final),
            requireFanout = false,
            allowPublicTairaRoute = true,
        )
        val submitHeaders = submitRoute["headers"] as? JsonObject ?: submissionInvalid()
        val submitContentType = submitHeaders.string("content-type") ?: submissionInvalid()
        if (
            !NexusToriiRoutes.isExpectedContentType(
                submitContentType,
                NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE,
            )
        ) {
            submissionInvalid()
        }
        requireMatchingHash(
            submitHeaders.string("x-iroha-entrypoint-hash"),
            canonicalExpected,
        )
        requireMatchingHash(submitHeaders.string("x-iroha-signed-transaction-hash"), canonicalExpected)
        val receiptBody = submitRoute["body"] as? JsonObject ?: submissionInvalid()
        val payload = receiptBody["payload"] as? JsonObject ?: submissionInvalid()
        val entrypointHash = payload.string("entrypoint_hash") ?: submissionInvalid()
        requireMatchingHash(entrypointHash, canonicalExpected)
        requireMatchingHash(payload.string("signed_transaction_hash"), canonicalExpected)
        payload.string("tx_hash")?.let { requireMatchingHash(it, canonicalExpected) }
        // Internal journal DTO retains its historical tx_hash property; current Torii returns
        // entrypoint_hash and signed_transaction_hash, both checked before this projection.
        val projectedBody = JsonObject(receiptBody + ("payload" to JsonObject(payload + ("tx_hash" to JsonPrimitive(entrypointHash)))))
        val receipt = runCatching {
            json.decodeFromJsonElement(
                NexusSubmissionReceipt.serializer(),
                projectedBody,
            )
        }.getOrElse { submissionInvalid(it) }
        listOf(
            receipt.payload.transactionHash,
            receipt.payload.entrypointHash,
            receipt.payload.signedTransactionHash,
        ).forEach { returned -> returned?.let { requireMatchingHash(it, canonicalExpected) } }

        val finalStatus = runCatching {
            json.decodeFromJsonElement(
                NexusTransactionStatus.serializer(),
                finalRoute.getValue("body"),
            )
        }.getOrElse { submissionInvalid(it) }
        requireMatchingHash(finalStatus.hash, canonicalExpected)
        if (
            finalStatus.status.kind != "Applied" ||
            !finalStatus.hasAuthoritativeGlobalResolution
        ) {
            submissionFailed()
        }
        return receipt
    }

    private fun wrapStructured(structured: JsonObject): JsonObject = JsonObject(
        mapOf(
            "isError" to JsonPrimitive(false),
            "structuredContent" to structured,
        )
    )

    private fun requireMatchingHash(returned: String?, expected: String) {
        if (returned == null || NexusTransactionHash.normalized(returned) != expected) {
            throw NexusToriiException("NEXUS_TRANSACTION_HASH_MISMATCH")
        }
    }

    private fun canonicalHash(value: String): String =
        requireNotNull(NexusTransactionHash.normalized(value)) {
            "NEXUS_INVALID_TRANSACTION_HASH"
        }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.isNotEmpty() && it == it.trim() }

    private fun JsonObject.strictBoolean(name: String): Boolean? =
        (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.strictInt(name: String): Int? =
        (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.strictLong(name: String): Long? =
        (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun submissionFailed(status: Int? = null): Nothing =
        throw NexusToriiException(
            safeCode = "TAIRA_SUBMIT_AND_WAIT_FAILED",
            httpStatus = status,
            submissionMayHaveReachedTorii = true,
            category = NexusToriiFailureCategory.PROTOCOL,
        )

    private fun submissionInvalid(cause: Throwable? = null): Nothing =
        throw NexusToriiException(
            safeCode = "TAIRA_SUBMIT_AND_WAIT_INVALID",
            submissionMayHaveReachedTorii = true,
            category = NexusToriiFailureCategory.PROTOCOL,
            cause = cause,
        )
}
