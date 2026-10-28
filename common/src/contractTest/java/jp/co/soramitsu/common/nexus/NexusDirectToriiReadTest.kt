package jp.co.soramitsu.common.nexus

import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NexusDirectToriiReadTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val network = NexusNetworks.taira
    private val account = IrohaAddressCodec.encode(ByteArray(32) { 1 }, network.chainDiscriminant)
    private val peer = IrohaAddressCodec.encode(ByteArray(32) { 2 }, network.chainDiscriminant)
    private val definition = TairaTestnetContract.XOR_ASSET_DEFINITION_ID
    private val hash = "ab".repeat(32)

    @Test
    fun authoritativeHashLookupUsesTheSameExactAccountAssetAndHashProof() = runBlocking {
        val requests = mutableListOf<String>()
        val client = NexusToriiClient(json, NexusToriiReadTransport { url, requireFanout ->
            requests += url; assertTrue(requireFanout)
            val result = if (query(URI(url)).getValue("filter").contains("tx_hash = \"$hash\"")) history(transfer()) else history()
            result.toString().toByteArray()
        })
        assertTrue(client.hasAuthoritativeCommittedTransaction(network, account, definition, hash))
        assertFalse(client.hasAuthoritativeCommittedTransaction(network, account, definition, "cd".repeat(32)))
        assertEquals(2, requests.size)
        assertTrue(requests.all { URI(it).path == "/v1/accounts/$account/history" })
    }

    @Test
    fun invalidImmutableAssetIdentityNeverStartsAnAuthoritativeRead() = runBlocking {
        var requests = 0
        val client = NexusToriiClient(json, NexusToriiReadTransport { _, _ -> requests++; error("must not fetch") })
        val failure = runCatching { client.hasAuthoritativeCommittedTransaction(network, account, "XOR#mutable", hash) }.exceptionOrNull()
        assertEquals("NEXUS_INVALID_ASSET_SELECTOR", failure?.message)
        assertEquals(0, requests)
    }

    @Test
    fun walletReadsUseFixedDirectRoutesWithoutDiscoveryRoundTrips() = runBlocking {
        val requests = mutableListOf<Pair<URI, Boolean>>()
        val client = NexusToriiClient(json, NexusToriiReadTransport { url, requireFanout ->
            val uri = URI(url)
            requests += uri to requireFanout
            val body = when (uri.path) {
                "/health" -> "Healthy"
                "/v1/assets/definitions/$definition" -> """{"id":"$definition","name":"XOR"}"""
                "/v1/accounts/$account/assets" -> assets().toString()
                "/v1/accounts/$account/history" -> history(transfer()).toString()
                "/v1/pipeline/transactions/status" -> """{"hash":"$hash","status":{"kind":"Applied","block_height":5},"scope":"global","resolved_from":"state"}"""
                else -> error("Unexpected read: $uri")
            }
            body.toByteArray()
        })

        assertEquals("Healthy", client.health(network))
        assertEquals("2.5", client.getXorBalance(network, account).quantity)
        val transfer = client.committedXorTransferPage(network, account, definition, null).items.single()
        assertEquals(peer, transfer.sender)
        assertEquals(account, transfer.receiver)
        assertEquals("1.25", transfer.amount)
        assertTrue(client.transactionStatus(network, hash).hasAuthoritativeGlobalResolution)
        assertEquals(5, requests.size)
        assertEquals(listOf(false, true, true, true, false), requests.map { it.second })
        assertTrue(requests.all { it.first.host == "taira.sora.org" && !it.first.path.contains("mcp") })
    }

    @Test
    fun directHistoryKeepsAnEmptyPageContinuationAndFiltersExactHashReads() = runBlocking {
        val requests = mutableListOf<URI>()
        val client = NexusToriiClient(json, NexusToriiReadTransport { url, _ ->
            val uri = URI(url)
            requests += uri
            if (requests.size == 1) history(cursor = "next_page-1").toString().toByteArray()
            else history(transfer()).toString().toByteArray()
        })

        val result = client.committedXorTransfersForHash(network, account, definition, hash)
        assertEquals(hash, result.single().transactionHash)
        assertEquals(2, requests.size)
        assertFalse(query(requests.first()).containsKey("cursor"))
        assertEquals("next_page-1", query(requests.last())["cursor"])
        assertTrue(requests.all { query(it).getValue("filter").contains("tx_hash = \"$hash\"") })
    }

    @Test
    fun directHistoryRejectsMismatchedAccountAndAssetEvidence() {
        listOf("account_id" to JsonPrimitive(peer), "asset_definition_id" to JsonPrimitive("other"),
            "tx_hash" to JsonPrimitive("cd".repeat(32))).forEach { (field, value) ->
            val client = NexusToriiClient(json, NexusToriiReadTransport { _, _ ->
                history(JsonObject(transfer() + (field to value))).toString().toByteArray()
            })
            assertThrows(Exception::class.java) {
                runBlocking { client.committedXorTransfersForHash(network, account, definition, hash) }
            }
        }
    }

    @Test
    fun incompleteDirectBalancePageCannotBecomeAZeroBalance() {
        val client = NexusToriiClient(json, NexusToriiReadTransport { _, _ ->
            """{"items":[],"total":1,"next_cursor":null}""".toByteArray()
        })
        assertThrows(IllegalStateException::class.java) {
            runBlocking { client.getAssetBalanceByDefinition(network, account, definition) }
        }
    }

    @Test
    fun balancePaginationRejectsARepeatedCursor() {
        val requested = mutableListOf<URI>()
        val client = NexusToriiClient(json, NexusToriiReadTransport { url, _ ->
            requested += URI(url)
            assets(total = 3, cursor = "same_cursor").toString().toByteArray()
        })
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { client.getAssetBalanceByDefinition(network, account, definition) }
        }
        assertEquals("NEXUS_ASSET_REPEATED_PAGE", failure.message)
        assertEquals("same_cursor", query(requested.last())["cursor"])
    }

    @Test
    fun directReadErrorsPropagateWithoutRetryOrMcpFallback() {
        var calls = 0
        val client = NexusToriiClient(json, NexusToriiReadTransport { _, _ ->
            calls++
            throw NexusToriiException("NEXUS_HTTP_503")
        })
        val error = assertThrows(NexusToriiException::class.java) {
            runBlocking { client.committedXorTransferPage(network, account, definition, null) }
        }
        assertEquals("NEXUS_HTTP_503", error.safeCode)
        assertEquals(1, calls)
    }

    private fun transfer() = buildJsonObject {
        put("id", "movement-1"); put("source", "transaction"); put("type", "TRANSFER")
        put("status", "SUCCESS"); put("result_ok", true); put("asset_definition_id", definition)
        put("account_id", account); put("counterparty_account_id", peer); put("direction", "incoming")
        put("amount", "1.25"); put("tx_hash", hash); put("block_height", 5); put("timestamp_ms", 1000)
    }

    private fun history(vararg items: JsonObject, cursor: String? = null) = buildJsonObject {
        put("items", JsonArray(items.toList())); put("next_cursor", cursor?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun assets(total: Long = 1, cursor: String? = null) = buildJsonObject {
        putJsonArray("items") {
            addJsonObject {
                put("account_id", account); put("asset", definition); put("asset_name", "XOR")
                put("asset_alias", "xor#universal"); put("quantity", "2.5"); put("scope", "global")
            }
        }
        put("total", total); put("next_cursor", cursor?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun query(uri: URI): Map<String, String> = uri.rawQuery.split('&').associate { parameter ->
        val parts = parameter.split('=', limit = 2)
        parts[0] to URLDecoder.decode(parts[1], Charsets.UTF_8.name())
    }
}
