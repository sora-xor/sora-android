package jp.co.soramitsu.common.nexus

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TairaSigningClientTest {
    private fun draft() = TairaSigningClient.Draft("authority", "recipient", ByteArray(32) { 1 }, ByteArray(32) { 2 }, "1", 100, 7)
    private fun quote(authority: String = "authority") = buildJsonObject {
        putJsonObject("intent") { put("payer", "authority"); putJsonObject("value") { put("charge_limits", JsonArray(emptyList())); put("gas_limit", JsonNull) } }
        put("components", JsonArray(emptyList())); put("capacities", JsonArray(emptyList()))
        putJsonObject("observation") { put("ledger_time_ms", 100); put("next_block_height", 3); put("route_dataspace_id", 0) }
        putJsonObject("decision") { put("status", "accepted"); putJsonObject("value") { put("program_revision", JsonNull); putJsonObject("debit_source") { put("kind", "account"); put("value", authority) } } }
    }
    @Test fun `authenticated empty charge limits means exactly zero fee`() {
        val result = TairaSigningClient.decodeQuote(quote().toString().toByteArray(), draft())
        assertEquals("0", result.total)
        assertEquals(TairaTransferCodec.hex(TairaTransferCodec.signingMessage(result.payload)), result.identity)
        assertThrows(IllegalStateException::class.java) { TairaSigningClient.decodeQuote(quote("different").toString().toByteArray(), draft()) }
    }
    @Test fun `positive XOR quote matches the live validated cross client codec`() {
        val fee = buildJsonObject {
            putJsonObject("kind") { put("kind", "nexus"); put("value", JsonNull) }
            put("asset_definition_id", TairaTestnetContract.XOR_ASSET_DEFINITION_ID)
            put("max_amount", "0.01")
        }
        val fees = JsonArray(listOf(fee))
        val base = quote()
        val priced = JsonObject(base + mapOf(
            "components" to fees,
            "intent" to buildJsonObject {
                put("payer", "authority")
                putJsonObject("value") { put("charge_limits", fees); put("gas_limit", JsonNull) }
            },
        ))
        val result = TairaSigningClient.decodeQuote(priced.toString().toByteArray(), draft().copy(amount = "0.001"))
        val reference = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/nexus/taira-fee-parity.json")).readText()).jsonObject
        assertEquals("0.01", result.total)
        assertEquals(reference.getValue("payload_hex").jsonPrimitive.content, TairaTransferCodec.hex(result.payload))
        assertEquals(reference.getValue("signing_message_hex").jsonPrimitive.content, result.identity)
        assertEquals(reference.getValue("transaction_hash").jsonPrimitive.content, TairaTransferCodec.transactionHash(result.payload))
        val mismatched = JsonObject(priced + ("components" to JsonArray(emptyList())))
        assertThrows(IllegalStateException::class.java) {
            TairaSigningClient.decodeQuote(mismatched.toString().toByteArray(), draft())
        }
    }

    @Test fun `quote rejects unknown fee policy fields and stale draft`() {
        val changed = JsonObject(quote() + ("unreviewed" to JsonPrimitive(true)))
        assertThrows(IllegalStateException::class.java) { TairaSigningClient.decodeQuote(changed.toString().toByteArray(), draft()) }
        assertThrows(IllegalStateException::class.java) { draft().requireFresh(80_100) }
    }
    @Test fun `HTTPS checkpoint binds fresh challenge network height and exact explorer block`() = runBlocking {
        var calls = 0
        val client = TairaSigningClient(transport = { request ->
            calls++
            if (calls == 1) {
                assertEquals("/v1/bridge/finality/attestation/latest", request.path)
                buildJsonObject { putJsonObject("body") {
                    put("challenge", request.headers.getValue("X-Iroha-Finality-Challenge").uppercase())
                    put("network_id", TairaTestnetContract.NETWORK_LITERAL); put("genesis_block_hash", TairaTestnetContract.NETWORK_LITERAL)
                    put("observed_at_unix_ms", 100)
                    putJsonObject("status") { put("applied_height", 3); put("committed_height", 4) }
                } }.toString().toByteArray()
            } else {
                assertEquals("/v1/explorer/blocks/3", request.path)
                buildJsonObject { put("height", 3); put("hash", TairaTestnetContract.NETWORK_LITERAL) }.toString().toByteArray()
            }
        }, now = { 100 })
        assertEquals(3, client.checkpoint().height)
        assertEquals(2, calls)
    }
    @Test fun `checkpoint rejects challenge replay before block lookup`() {
        var calls = 0
        val client = TairaSigningClient(transport = {
            calls++
            """{"body":{"challenge":"old","network_id":"${TairaTestnetContract.NETWORK_LITERAL}","genesis_block_hash":"${TairaTestnetContract.NETWORK_LITERAL}"}}""".toByteArray()
        })
        assertThrows(IllegalStateException::class.java) { runBlocking { client.checkpoint() } }
        assertEquals(1, calls)
    }
}
