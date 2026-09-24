package jp.co.soramitsu.common.nexus

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
    const val INSTRUCTIONS_TOOL = "iroha.instructions.list"
    const val SUBMIT_AND_WAIT_TOOL = "iroha.transactions.submit_and_wait"

    private enum class SchemaType(val scalarName: String?) {
        STRING("string"),
        INTEGER("integer"),
        STRING_ARRAY(null),
    }

    /** Every property this client may emit, including routed names absent from the old schema. */
    private val requestPropertyTypes = mapOf(
        HEALTH_TOOL to emptyMap(),
        ACCOUNT_ASSETS_TOOL to mapOf(
            "account_id" to SchemaType.STRING,
            "asset" to SchemaType.STRING,
            "limit" to SchemaType.INTEGER,
            "offset" to SchemaType.INTEGER,
            "scope" to SchemaType.STRING,
            "accept" to SchemaType.STRING,
        ),
        ASSET_DEFINITION_TOOL to mapOf(
            "definition_id" to SchemaType.STRING,
            "accept" to SchemaType.STRING,
        ),
        TRANSACTION_STATUS_TOOL to mapOf(
            "hash" to SchemaType.STRING,
            "scope" to SchemaType.STRING,
            "accept" to SchemaType.STRING,
        ),
        INSTRUCTIONS_TOOL to mapOf(
            "account" to SchemaType.STRING,
            "asset_definition_id" to SchemaType.STRING,
            "kind" to SchemaType.STRING,
            "page" to SchemaType.INTEGER,
            "per_page" to SchemaType.INTEGER,
            "transaction_hash" to SchemaType.STRING,
            "transaction_status" to SchemaType.STRING,
            "accept" to SchemaType.STRING,
        ),
        SUBMIT_AND_WAIT_TOOL to mapOf(
            "body_base64" to SchemaType.STRING,
            "hash" to SchemaType.STRING,
            "status_accept" to SchemaType.STRING,
            "terminal_statuses" to SchemaType.STRING_ARRAY,
            "timeout_ms" to SchemaType.INTEGER,
        ),
    )

    /** Properties present on every request for a tool; schemas may require only these. */
    private val alwaysProvidedProperties = requestPropertyTypes.mapValues { (tool, properties) ->
        if (tool == INSTRUCTIONS_TOOL) properties.keys - "transaction_hash" else properties.keys
    }

    /** Undeclared routed names known to be consumed by the currently deployed handlers. */
    private val legacyUndeclaredProperties = mapOf(
        ACCOUNT_ASSETS_TOOL to setOf("asset", "scope"),
        TRANSACTION_STATUS_TOOL to setOf("scope"),
    )

    fun initializeRequest(requestId: String): NexusMcpRequest = NexusMcpRequest(
        jsonrpc = "2.0",
        id = requestId,
        method = "initialize",
        params = JsonObject(
            mapOf(
                "protocolVersion" to JsonPrimitive(TairaTestnetContract.MCP_PROTOCOL_VERSION),
                "capabilities" to JsonObject(emptyMap()),
                "clientInfo" to JsonObject(
                    mapOf(
                        "name" to JsonPrimitive("sora-wallet-android"),
                        "version" to JsonPrimitive("1"),
                    )
                ),
            )
        ),
    )

    fun discoveryRequest(
        requestId: String,
        toolsetVersion: String,
        cursor: String?,
    ): NexusMcpRequest {
        val params = mutableMapOf<String, JsonElement>(
            "toolset_version" to JsonPrimitive(toolsetVersion),
        )
        cursor?.let { params["cursor"] = JsonPrimitive(it) }
        return NexusMcpRequest(
            jsonrpc = "2.0",
            id = requestId,
            method = "tools/list",
            params = JsonObject(params),
        )
    }

    fun call(requestId: String, tool: String, arguments: JsonObject): NexusMcpRequest {
        require(tool in requestPropertyTypes) { "TAIRA_MCP_TOOL_NOT_ALLOWED" }
        return NexusMcpRequest(
            jsonrpc = "2.0",
            id = requestId,
            method = "tools/call",
            params = JsonObject(
                mapOf(
                    "name" to JsonPrimitive(tool),
                    "arguments" to arguments,
                )
            ),
        )
    }

    fun healthRequest(requestId: String): NexusMcpRequest =
        call(requestId, HEALTH_TOOL, JsonObject(emptyMap()))

    fun assetDefinitionRequest(requestId: String): NexusMcpRequest = call(
        requestId,
        ASSET_DEFINITION_TOOL,
        JsonObject(
            mapOf(
                "definition_id" to JsonPrimitive(
                    TairaTestnetContract.XOR_ASSET_DEFINITION_ID
                ),
                "accept" to JsonPrimitive(NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE),
            )
        ),
    )

    fun accountAssetsRequest(
        requestId: String,
        accountId: String,
        definitionId: String,
        limit: Int,
        offset: Long,
    ): NexusMcpRequest = call(
        requestId,
        ACCOUNT_ASSETS_TOOL,
        JsonObject(
            mapOf(
                "account_id" to JsonPrimitive(accountId),
                // Unpatched Torii advertises `asset_id` while its GET handler consumes `asset`.
                // Discovery admits only that known drift; patched Torii advertises this key.
                "asset" to JsonPrimitive(definitionId),
                "limit" to JsonPrimitive(limit),
                "offset" to JsonPrimitive(offset),
                "scope" to JsonPrimitive("global"),
                "accept" to JsonPrimitive(NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE),
            )
        ),
    )

    fun transactionStatusRequest(requestId: String, hash: String): NexusMcpRequest = call(
        requestId,
        TRANSACTION_STATUS_TOOL,
        JsonObject(
            mapOf(
                "hash" to JsonPrimitive(canonicalHash(hash)),
                "scope" to JsonPrimitive("global"),
                "accept" to JsonPrimitive(NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE),
            )
        ),
    )

    fun instructionsRequest(
        requestId: String,
        accountId: String,
        definitionId: String,
        page: Int,
        perPage: Int,
        transactionHash: String? = null,
    ): NexusMcpRequest {
        require(page > 0 && perPage > 0)
        val arguments = mutableMapOf<String, JsonElement>(
            "account" to JsonPrimitive(accountId),
            // A full `asset_id` is a source-owned balance bucket and would exclude incoming
            // transfers. The first-release Torii contract exposes the definition selector.
            "asset_definition_id" to JsonPrimitive(definitionId),
            "kind" to JsonPrimitive("Transfer"),
            "page" to JsonPrimitive(page),
            "per_page" to JsonPrimitive(perPage),
            "transaction_status" to JsonPrimitive("committed"),
            "accept" to JsonPrimitive(NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE),
        )
        transactionHash?.let { arguments["transaction_hash"] = JsonPrimitive(canonicalHash(it)) }
        return call(requestId, INSTRUCTIONS_TOOL, JsonObject(arguments))
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
                    "terminal_statuses" to JsonArray(listOf(JsonPrimitive("Applied"))),
                    "timeout_ms" to JsonPrimitive(120_000),
                )
            ),
        )
    }

    fun toolsetVersion(initializeResult: JsonElement): String {
        val root = initializeResult as? JsonObject ?: contractMismatch()
        if (root.string("protocolVersion") != TairaTestnetContract.MCP_PROTOCOL_VERSION) {
            contractMismatch()
        }
        val capabilities = root["capabilities"] as? JsonObject ?: contractMismatch()
        val tools = capabilities["tools"] as? JsonObject ?: contractMismatch()
        return tools.string("toolsetVersion")
            ?.takeIf { NexusTransactionHash.normalized(it) == it }
            ?: contractMismatch()
    }

    fun validateToolPage(
        result: JsonElement,
        tools: Set<String>,
        expectedToolsetVersion: String,
    ): ToolDiscoveryPage {
        require(tools.isNotEmpty() && requestPropertyTypes.keys.containsAll(tools))
        val root = result as? JsonObject ?: contractMismatch()
        val advertised = root["tools"] as? JsonArray ?: contractMismatch()
        if (
            root.strictBoolean("listChanged") != false ||
            root.string("toolsetVersion") != expectedToolsetVersion
        ) {
            contractMismatch()
        }
        val descriptors = advertised.map { it as? JsonObject ?: contractMismatch() }
        val names = descriptors.map { it.string("name") ?: contractMismatch() }
        if (names.size != names.toSet().size) contractMismatch()
        val matched = mutableSetOf<String>()
        descriptors.forEach { descriptor ->
            val requiredTool = descriptor.string("name") ?: contractMismatch()
            if (requiredTool !in tools) return@forEach
            matched += requiredTool
            val schema = descriptor["inputSchema"] as? JsonObject ?: contractMismatch()
            if (schema.string("type") != "object") contractMismatch()
            val properties = schema["properties"] as? JsonObject ?: contractMismatch()
            val admitsAdditionalProperties = schema.strictBoolean("additionalProperties") == true
            requestPropertyTypes.getValue(requiredTool).forEach { (name, expectedType) ->
                val property = properties[name]
                if (property == null) {
                    if (
                        !admitsAdditionalProperties ||
                        name !in legacyUndeclaredProperties[requiredTool].orEmpty()
                    ) {
                        contractMismatch()
                    }
                } else if (!property.matches(expectedType)) {
                    contractMismatch()
                }
            }
            val required = schemaRequiredNames(schema["required"])
            if (!alwaysProvidedProperties.getValue(requiredTool).containsAll(required)) {
                contractMismatch()
            }
            if (requiredTool == SUBMIT_AND_WAIT_TOOL) {
                if ("body_base64" !in required) contractMismatch()
            }
        }
        val nextCursor = when (val raw = root["nextCursor"]) {
            JsonNull -> null
            is JsonPrimitive -> raw.takeIf { it.isString }?.content
                ?.takeIf { it.matches(Regex("^[1-9][0-9]{0,5}$")) }
                ?: contractMismatch()
            else -> contractMismatch()
        }
        return ToolDiscoveryPage(
            matched = matched,
            advertisedNames = names.toSet(),
            nextCursor = nextCursor,
        )
    }

    data class ToolDiscoveryPage(
        val matched: Set<String>,
        val advertisedNames: Set<String>,
        val nextCursor: String?,
    )

    private fun JsonElement.matches(expectedType: SchemaType): Boolean {
        val schema = this as? JsonObject ?: return false
        return when (expectedType) {
            SchemaType.STRING,
            SchemaType.INTEGER,
            -> schema.string("type") == expectedType.scalarName
            SchemaType.STRING_ARRAY ->
                schema.string("type") == "array" &&
                    ((schema["items"] as? JsonObject)?.string("type") == "string")
        }
    }

    private fun schemaRequiredNames(value: JsonElement?): Set<String> {
        if (value == null) return emptySet()
        val array = value as? JsonArray ?: contractMismatch()
        val names = array.map { item ->
            (item as? JsonPrimitive)
                ?.takeIf(JsonPrimitive::isString)
                ?.content
                ?.takeIf(String::isNotEmpty)
                ?: contractMismatch()
        }
        if (names.size != names.toSet().size) contractMismatch()
        return names.toSet()
    }

    fun validateHealthResult(result: JsonElement): String {
        val direct = result as? JsonObject ?: contractMismatch()
        if (direct.strictBoolean("isError") != false) contractMismatch()
        val structured = direct["structuredContent"] as? JsonObject ?: contractMismatch()
        val status = structured.strictInt("status") ?: contractMismatch()
        if (status !in 200..299) {
            throw NexusToriiException(
                safeCode = "NEXUS_MCP_HTTP_$status",
                httpStatus = status,
                category = NexusToriiFailureCategory.HTTP,
            )
        }
        val contentType = structured.string("content_type") ?: contractMismatch()
        if (
            !NexusToriiRoutes.isExpectedContentType(
                contentType,
                NexusToriiRoutes.HEALTH_RESPONSE_MEDIA_TYPE,
            )
        ) {
            contractMismatch()
        }
        val body = structured.string("body") ?: contractMismatch()
        if (!NexusToriiRoutes.isHealthyResponse(body)) {
            throw NexusToriiException("NEXUS_HEALTH_INVALID")
        }
        return body
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
            structured.string("terminal_kind") != "Applied" ||
            structured["terminal_statuses"] != JsonArray(listOf(JsonPrimitive("Applied")))
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
        )
        val finalRoute = NexusMcpResultContract.validateEmbeddedRoute(
            wrapStructured(final),
            requireFanout = false,
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
            submitHeaders.string("x-iroha-transaction-hash"),
            canonicalExpected,
        )
        val receipt = runCatching {
            json.decodeFromJsonElement(
                NexusSubmissionReceipt.serializer(),
                submitRoute.getValue("body"),
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

    private fun contractMismatch(): Nothing =
        throw NexusToriiException(
            safeCode = "TAIRA_MCP_CONTRACT_MISMATCH",
            category = NexusToriiFailureCategory.PROTOCOL,
        )

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
