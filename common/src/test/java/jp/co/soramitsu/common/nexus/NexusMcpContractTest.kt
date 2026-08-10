package jp.co.soramitsu.common.nexus

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
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
    fun `response without json rpc version fails closed`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                NexusMcpResponse.serializer(),
                """{"id":"history-0","result":{}}""",
            )
        }
    }
}
