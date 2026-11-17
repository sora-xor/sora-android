package jp.co.soramitsu.common.nexus

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TairaCurrentContractTest {
    @Test fun `live Applied receipt accepts canonical hashes and rejects changed checksum`() {
        val result = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/nexus/taira-live-applied-receipt.json")).readText())
        val hash = "4a48384ad05a8e1669baf4cd0ed83773f006f178e486ad421bcf2abcadd658a1"
        val receipt = TairaMcpContract.validateSubmitAndWaitResult(Json { ignoreUnknownKeys = true }, result, hash)
        assertEquals(hash, NexusTransactionHash.normalized(receipt.payload.transactionHash))
        val changed = Json.parseToJsonElement(result.toString().replace("#EC45", "#EC44"))
        assertThrows(NexusToriiException::class.java) { TairaMcpContract.validateSubmitAndWaitResult(Json { ignoreUnknownKeys = true }, changed, hash) }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private fun fixture(name: String): JsonObject = json.parseToJsonElement(
        checkNotNull(javaClass.getResource("/nexus/taira-2026-10-13/$name.json")).readText()
    ).jsonObject
    private fun result(name: String) = fixture(name).getValue("result")

    @Test
    fun `public Taira balance accepts current local route evidence and uppercase XOR name`() {
        val routed = NexusMcpResultContract.validateEmbeddedRoute(result("balance"), requireFanout = true, allowPublicTairaRoute = true)
        val page = json.decodeFromJsonElement<TairaAccountAssetPage>(routed.getValue("body"))
        val balance = page.items.single()
        assertEquals("23.5", NexusBalanceValidator.exactXorBalance(page.items, NexusNetworks.taira, balance.accountId!!,
            TairaTestnetContract.XOR_ASSET_DEFINITION_ID)?.quantity)
        assertEquals(null, page.nextCursor)
        assertThrows(NexusToriiException::class.java) {
            NexusMcpResultContract.validateEmbeddedRoute(result("balance"), requireFanout = true)
        }
        val wrongHeaders = routed.getValue("headers").jsonObject + ("x-iroha-route-dataspace-id" to JsonPrimitive("1"))
        val wrong = JsonObject(result("balance").jsonObject + ("structuredContent" to JsonObject(routed + ("headers" to JsonObject(wrongHeaders)))))
        assertThrows(NexusToriiException::class.java) {
            NexusMcpResultContract.validateEmbeddedRoute(wrong, requireFanout = true, allowPublicTairaRoute = true)
        }
    }

    @Test
    fun `public movement cursor can continue after an empty page`() {
        val routed = NexusMcpResultContract.validateEmbeddedRoute(result("movements"), allowPublicTairaRoute = true)
        val cursorBody = JsonObject(routed.getValue("body").jsonObject + ("next_cursor" to JsonPrimitive("next_page")))
        val page = NexusAccountHistoryParser.page(JsonObject(routed + ("body" to cursorBody)), NexusNetworks.taira,
            IrohaAddressCodec.encode(ByteArray(32) { 1 }, 369), TairaTestnetContract.XOR_ASSET_DEFINITION_ID, 100)
        assertTrue(page.items.isEmpty())
        assertEquals("next_page", page.nextCursor)
    }

    @Test
    fun `movement evidence binds account asset success hash and transfer direction`() {
        val owner = IrohaAddressCodec.encode(ByteArray(32) { 1 }, 369)
        val peer = IrohaAddressCodec.encode(ByteArray(32) { 2 }, 369)
        val hash = "ab".repeat(32)
        val row = buildJsonObject {
            put("id", "movement-1"); put("source", "transaction"); put("type", "TRANSFER")
            put("status", "SUCCESS"); put("result_ok", true); put("asset_definition_id", TairaTestnetContract.XOR_ASSET_DEFINITION_ID)
            put("account_id", owner); put("counterparty_account_id", peer); put("direction", "incoming")
            put("amount", "1.000000001"); put("tx_hash", hash); put("block_height", 1); put("timestamp_ms", 2)
        }
        fun page(item: JsonObject) = buildJsonObject {
            putJsonObject("body") { put("items", JsonArray(listOf(item))); put("next_cursor", JsonNull) }
        }
        val parsed = NexusAccountHistoryParser.page(page(row), NexusNetworks.taira, owner, TairaTestnetContract.XOR_ASSET_DEFINITION_ID, 100, hash).items.single()
        assertEquals(peer, parsed.sender); assertEquals(owner, parsed.receiver); assertEquals("1.000000001", parsed.amount)
        listOf("account_id" to JsonPrimitive(peer), "result_ok" to JsonPrimitive(false),
            "asset_definition_id" to JsonPrimitive("another"), "tx_hash" to JsonPrimitive("cd".repeat(32)),
            "amount" to JsonPrimitive("0.0000000001")).forEach { (key, value) ->
            assertThrows(Exception::class.java) {
                NexusAccountHistoryParser.page(page(JsonObject(row + (key to value))), NexusNetworks.taira, owner,
                    TairaTestnetContract.XOR_ASSET_DEFINITION_ID, 100, hash)
            }
        }
    }
    @Test
    fun `exact hash reconciliation traverses more than twenty empty cursor pages`() = kotlinx.coroutines.runBlocking {
        val item = NexusTransferHistoryItem("ab".repeat(32), 1, "1", "sender", "receiver")
        var calls = 0
        val result = NexusHistoryCursorCollector.collect { cursor ->
            assertEquals(if (calls == 0) null else "page$calls", cursor)
            calls += 1
            if (calls <= 25) NexusTransferHistorySlice(emptyList(), "page$calls")
            else NexusTransferHistorySlice(listOf(item), null, setOf("movement"))
        }
        assertEquals(26, calls)
        assertEquals(listOf(item), result)
    }

    @Test
    fun `exact hash cursor scans reject repeated pages duplicate evidence and transport failure`() {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                NexusHistoryCursorCollector.collect { NexusTransferHistorySlice(emptyList(), "same") }
            }
        }
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                NexusHistoryCursorCollector.collect { cursor ->
                    NexusTransferHistorySlice(emptyList(), if (cursor == null) "next" else null, setOf("duplicate"))
                }
            }
        }
        assertThrows(NexusToriiException::class.java) {
            kotlinx.coroutines.runBlocking {
                NexusHistoryCursorCollector.collect { cursor ->
                    if (cursor != null) throw NexusToriiException("NEXUS_NETWORK_UNAVAILABLE")
                    NexusTransferHistorySlice(emptyList(), "next")
                }
            }
        }
    }

}
