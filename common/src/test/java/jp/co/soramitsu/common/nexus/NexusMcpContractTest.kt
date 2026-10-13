package jp.co.soramitsu.common.nexus

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusMcpContractTest {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
    }

    @Test
    fun `json rpc version is present even when defaults are not encoded`() {
        val encoded = json.encodeToString(
            NexusMcpRequest.serializer(),
            NexusMcpRequest(
                jsonrpc = "2.0",
                id = "history-0",
                method = "tools/call",
            ),
        )

        assertTrue(encoded.contains("\"jsonrpc\":\"2.0\""))
    }

    @Test
    fun `modern server discovery and tool calls include protocol metadata`() {
        val requests = listOf(
            TairaMcpContract.serverDiscoveryRequest("init"),
            TairaMcpContract.discoveryRequest("list", "current", null),
            TairaMcpContract.healthRequest("health"),
        )
        requests.forEach { request ->
            val headers = TairaMcpContract.requestHeaders(request)
            org.junit.Assert.assertEquals(request.method, headers["Mcp-Method"])
            org.junit.Assert.assertEquals(TairaTestnetContract.MCP_PROTOCOL_VERSION, headers["MCP-Protocol-Version"])
            org.junit.Assert.assertEquals(if (request.method == "tools/call") TairaMcpContract.HEALTH_TOOL else null, headers["Mcp-Name"])
            val metadata = request.params?.get("_meta") as JsonObject
            org.junit.Assert.assertEquals(
                JsonPrimitive(TairaTestnetContract.MCP_PROTOCOL_VERSION),
                metadata["io.modelcontextprotocol/protocolVersion"],
            )
            org.junit.Assert.assertEquals(
                JsonObject(emptyMap()), metadata["io.modelcontextprotocol/clientCapabilities"],
            )
        }
    }

    @Test
    fun `response without json rpc version fails closed`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                NexusMcpResponse.serializer(),
                """{"id":"history-0","result":{}}""",
            )
        }
    }
}
