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

    private val alwaysProvidedProperties = requestPropertyTypes.mapValues { (tool, properties) ->
        properties.keys - when (tool) {
            HISTORY_TOOL -> setOf("cursor")
            ACCOUNT_ASSETS_TOOL -> setOf("cursor")
            else -> emptySet()
        }
    }

    private fun protocolMetadata(): JsonObject = JsonObject(mapOf(
        "io.modelcontextprotocol/protocolVersion" to JsonPrimitive(TairaTestnetContract.MCP_PROTOCOL_VERSION),
        "io.modelcontextprotocol/clientCapabilities" to JsonObject(emptyMap()),
    ))

    fun serverDiscoveryRequest(requestId: String): NexusMcpRequest = NexusMcpRequest(
        jsonrpc = "2.0",
        id = requestId,
        method = "server/discover",
        params = JsonObject(mapOf("_meta" to protocolMetadata())),
    )

    fun requestHeaders(request: NexusMcpRequest): Map<String, String> = buildMap {
        put("MCP-Protocol-Version", TairaTestnetContract.MCP_PROTOCOL_VERSION)
        put("Mcp-Method", request.method)
        if (request.method == "tools/call") {
            put("Mcp-Name", checkNotNull(request.params?.string("name")))
        }
    }

    fun discoveryRequest(
        requestId: String,
        toolsetVersion: String,
        cursor: String?,
    ): NexusMcpRequest {
        val params = mutableMapOf<String, JsonElement>(
            "_meta" to protocolMetadata(),
            "toolsetVersion" to JsonPrimitive(toolsetVersion),
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
                    "_meta" to protocolMetadata(),
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
                "path" to JsonObject(mapOf("definition_id" to JsonPrimitive(
                    TairaTestnetContract.XOR_ASSET_DEFINITION_ID
                ))),
                "accept" to JsonPrimitive(NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE),
            )
        ),
    )

    fun accountAssetsRequest(
        requestId: String,
        accountId: String,
        definitionId: String,
        limit: Int,
        cursor: String? = null,
    ): NexusMcpRequest {
        require(limit in 1..NexusToriiRoutes.MAX_PAGE_SIZE)
        val arguments = mutableMapOf<String, JsonElement>(
            "path" to JsonObject(mapOf("account_id" to JsonPrimitive(accountId))),
            "filter" to JsonObject(mapOf(
                "op" to JsonPrimitive("and"),
                "args" to JsonArray(listOf(
                    JsonObject(mapOf("op" to JsonPrimitive("eq"), "args" to JsonArray(listOf(JsonPrimitive("asset"), JsonPrimitive(definitionId))))),
                    JsonObject(mapOf("op" to JsonPrimitive("eq"), "args" to JsonArray(listOf(JsonPrimitive("scope"), JsonPrimitive("global"))))),
                )),
            )),
            "limit" to JsonPrimitive(limit),
            "include_total" to JsonPrimitive(true),
            "accept" to JsonPrimitive(NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE),
        )
        cursor?.let { arguments["cursor"] = JsonPrimitive(it) }
        return call(requestId, ACCOUNT_ASSETS_TOOL, JsonObject(arguments))
    }

    fun transactionStatusRequest(requestId: String, hash: String): NexusMcpRequest = call(
        requestId,
        TRANSACTION_STATUS_TOOL,
        JsonObject(
            mapOf(
                "query" to JsonObject(mapOf("hash" to JsonPrimitive(canonicalHash(hash)))) ,
                "accept" to JsonPrimitive(NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE),
            )
        ),
    )

    fun historyRequest(
        requestId: String,
        accountId: String,
        definitionId: String,
        limit: Int,
        cursor: String? = null,
        transactionHash: String? = null,
    ): NexusMcpRequest {
        require(limit in 1..100)
        fun equal(field: String, value: JsonElement) = JsonObject(mapOf(
            "op" to JsonPrimitive("eq"), "args" to JsonArray(listOf(JsonPrimitive(field), value)),
        ))
        val filters = mutableListOf(
            equal("asset_definition_id", JsonPrimitive(definitionId)),
            equal("source", JsonPrimitive("transaction")),
            equal("type", JsonPrimitive("TRANSFER")),
            equal("status", JsonPrimitive("SUCCESS")),
            equal("result_ok", JsonPrimitive(true)),
        )
        transactionHash?.let { filters += equal("tx_hash", JsonPrimitive(canonicalHash(it))) }
        val arguments = mutableMapOf<String, JsonElement>(
            "path" to JsonObject(mapOf("account_id" to JsonPrimitive(accountId))),
            "filter" to JsonObject(mapOf("op" to JsonPrimitive("and"), "args" to JsonArray(filters))),
            "limit" to JsonPrimitive(limit),
            "accept" to JsonPrimitive(NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE),
        )
        cursor?.let { arguments["cursor"] = JsonPrimitive(it) }
        return call(requestId, HISTORY_TOOL, JsonObject(arguments))
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

    fun toolsetVersion(discoveryResult: JsonElement): String {
        val root = discoveryResult as? JsonObject ?: contractMismatch()
        val supported = root["supportedVersions"] as? JsonArray ?: contractMismatch()
        if (JsonPrimitive(TairaTestnetContract.MCP_PROTOCOL_VERSION) !in supported) contractMismatch()
        val serverInfo = (root["_meta"] as? JsonObject)
            ?.get("io.modelcontextprotocol/serverInfo") as? JsonObject ?: contractMismatch()
        if (serverInfo.string("name") != "iroha-torii-mcp") contractMismatch()
        val capabilities = root["capabilities"] as? JsonObject ?: contractMismatch()
        val extensions = capabilities["extensions"] as? JsonObject ?: contractMismatch()
        val tools = extensions["org.hyperledger.iroha/tools"] as? JsonObject ?: contractMismatch()
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
        val metadata = (root["_meta"] as? JsonObject)?.get("iroha") as? JsonObject ?: contractMismatch()
        if (
            metadata.strictBoolean("listChanged") != false ||
            metadata.string("toolsetVersion") != expectedToolsetVersion
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
            requestPropertyTypes.getValue(requiredTool).forEach { (name, expectedType) ->
                val property = properties[name]
                if (property == null || !property.matches(expectedType)) contractMismatch()
            }
            val nested = when (requiredTool) {
                ACCOUNT_ASSETS_TOOL, HISTORY_TOOL -> "path" to "account_id"
                ASSET_DEFINITION_TOOL -> "path" to "definition_id"
                TRANSACTION_STATUS_TOOL -> "query" to "hash"
                else -> null
            }
            nested?.let { (container, key) ->
                val nestedSchema = properties[container] as? JsonObject ?: contractMismatch()
                val nestedProperties = nestedSchema["properties"] as? JsonObject ?: contractMismatch()
                if (nestedProperties[key]?.matches(SchemaType.STRING) != true ||
                    !setOf(key).containsAll(schemaRequiredNames(nestedSchema["required"]))) contractMismatch()
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
            null, JsonNull -> null
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
            SchemaType.OBJECT,
            SchemaType.BOOLEAN,
            -> schema.string("type") == expectedType.scalarName
            SchemaType.FILTER -> (schema["type"] as? JsonArray)?.contains(JsonPrimitive("object")) == true
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
