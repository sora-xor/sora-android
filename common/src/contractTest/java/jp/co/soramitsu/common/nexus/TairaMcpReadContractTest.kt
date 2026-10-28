package jp.co.soramitsu.common.nexus

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Local wire contracts: no credentials, signing, or network access. */
class TairaMcpReadContractTest {
    private val definition = TairaTestnetContract.XOR_ASSET_DEFINITION_ID
    private val hash = "ab".repeat(32)
    private fun arguments(request: NexusMcpRequest) = request.params!!.getValue("arguments").jsonObject
    private fun health(status: JsonElement = JsonPrimitive(200), contentType: JsonElement = JsonPrimitive("text/plain"), body: JsonElement = JsonPrimitive("Healthy")) = buildJsonObject {
        put("isError", false)
        putJsonObject("structuredContent") { put("status", status); put("content_type", contentType); put("body", body) }
    }
    @Test fun definitionRequestUsesImmutableNativeIdentity() {
        val request = TairaMcpContract.assetDefinitionRequest("definition-1")
        assertEquals("definition-1", request.id)
        assertEquals(TairaMcpContract.ASSET_DEFINITION_TOOL, request.params!!["name"]!!.jsonPrimitive.content)
        assertEquals(definition, arguments(request)["path"]!!.jsonObject["definition_id"]!!.jsonPrimitive.content)
        assertEquals("application/json", arguments(request)["accept"]!!.jsonPrimitive.content)
    }
    @Test fun statusRequestCanonicalizesHashAndRejectsMalformedHash() {
        val request = TairaMcpContract.transactionStatusRequest("status-1", "0x${hash.uppercase()}")
        assertEquals(hash, arguments(request)["query"]!!.jsonObject["hash"]!!.jsonPrimitive.content)
        for (bad in listOf("", "ab", "zz".repeat(32), " $hash")) {
            assertThrows(IllegalArgumentException::class.java) { TairaMcpContract.transactionStatusRequest("bad", bad) }
        }
    }
    @Test fun accountRequestPinsGlobalAssetAndOnlyAddsExplicitCursor() {
        val first = arguments(TairaMcpContract.accountAssetsRequest("first", "fixture-account", definition, 1))
        val next = arguments(TairaMcpContract.accountAssetsRequest("next", "fixture-account", definition, 100, "opaque-cursor"))
        assertFalse(first.containsKey("cursor")); assertEquals(JsonPrimitive("opaque-cursor"), next["cursor"])
        assertEquals(JsonPrimitive(true), next["include_total"])
        assertEquals(JsonPrimitive("fixture-account"), next["path"]!!.jsonObject["account_id"])
        assertEquals(JsonPrimitive("and"), next["filter"]!!.jsonObject["op"])
        assertEquals(listOf("asset", "scope"), next["filter"]!!.jsonObject["args"]!!.jsonArray.map { it.jsonObject["args"]!!.jsonArray[0].jsonPrimitive.content })
        for (bad in listOf(0, -1, NexusToriiRoutes.MAX_PAGE_SIZE + 1)) assertThrows(IllegalArgumentException::class.java) {
            TairaMcpContract.accountAssetsRequest("invalid", "fixture-account", definition, bad)
        }
    }
    @Test fun unfilteredHistoryStillRequiresCommittedSuccessfulTransfers() {
        val request = arguments(TairaMcpContract.historyRequest("history", "fixture-account", definition, 100))
        assertFalse(request.containsKey("cursor"))
        val terms = request["filter"]!!.jsonObject["args"]!!.jsonArray.map { it.jsonObject["args"]!!.jsonArray }
        assertEquals(setOf("asset_definition_id", "source", "type", "status", "result_ok"), terms.map { it[0].jsonPrimitive.content }.toSet())
        assertEquals(JsonPrimitive(true), terms.single { it[0] == JsonPrimitive("result_ok") }[1])
        for (bad in listOf(0, 101)) assertThrows(IllegalArgumentException::class.java) { TairaMcpContract.historyRequest("invalid", "fixture", definition, bad) }
    }
    @Test fun qualifiedMcpHealthAcceptsOnlyHealthyText() {
        assertEquals("Healthy", TairaMcpContract.validateHealthResult(health()))
        val error = assertThrows(NexusToriiException::class.java) { TairaMcpContract.validateHealthResult(health(body = JsonPrimitive("Unhealthy"))) }
        assertEquals("NEXUS_HEALTH_INVALID", error.safeCode)
    }
    @Test fun mcpHealthPreservesUnsuccessfulHttpStatus() {
        for (status in listOf(199, 301, 401, 503)) {
            val error = assertThrows(NexusToriiException::class.java) { TairaMcpContract.validateHealthResult(health(status = JsonPrimitive(status))) }
            assertEquals("NEXUS_MCP_HTTP_$status", error.safeCode); assertEquals(status, error.httpStatus)
            assertEquals(NexusToriiFailureCategory.HTTP, error.category)
        }
    }
    @Test fun malformedHealthEnvelopesNeverBecomeHealthy() {
        val valid = health()
        val bad = listOf(JsonNull, JsonPrimitive("Healthy"), JsonObject(emptyMap()), JsonObject(valid + ("isError" to JsonPrimitive(true))),
            JsonObject(valid + ("isError" to JsonPrimitive("false"))), JsonObject(valid + ("structuredContent" to JsonArray(emptyList()))),
            health(status = JsonPrimitive("200")), health(status = JsonNull), health(contentType = JsonPrimitive("application/json")),
            health(contentType = JsonNull), health(body = JsonPrimitive(1)), health(body = JsonNull))
        bad.forEach { response ->
            val error = assertThrows(NexusToriiException::class.java) { TairaMcpContract.validateHealthResult(response) }
            assertEquals(response.toString(), "TAIRA_MCP_CONTRACT_MISMATCH", error.safeCode)
        }
    }
    @Test fun nonTairaAssetIdentityRequiresAnActiveExactAliasBinding() {
        val valid = NexusAssetDefinition(definition, "xor", "xor#universal", NexusAssetAliasBinding("xor#universal", "permanent", boundAtMillis = 1))
        assertTrue(NexusAssetDefinitionIdentity.isQualifiedXorDefinition(valid, NexusNetworks.minamoto))
        assertTrue(NexusAssetDefinitionIdentity.isQualifiedXorDefinition(valid.copy(aliasBinding = valid.aliasBinding!!.copy(status = "leased_active")), NexusNetworks.minamoto))
        for (bad in listOf(valid.copy(name = "XOR"), valid.copy(alias = null), valid.copy(aliasBinding = null), valid.copy(aliasBinding = valid.aliasBinding!!.copy(status = "expired")))) {
            assertFalse(NexusAssetDefinitionIdentity.isQualifiedXorDefinition(bad, NexusNetworks.minamoto))
        }
    }
}
