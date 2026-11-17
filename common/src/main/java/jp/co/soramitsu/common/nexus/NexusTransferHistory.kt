package jp.co.soramitsu.common.nexus

import java.math.BigDecimal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull

data class NexusTransferHistoryItem(
    val transactionHash: String,
    val timestampMillis: Long,
    val amount: String,
    val sender: String,
    val receiver: String,
)

data class NexusTransferHistorySlice(
    val items: List<NexusTransferHistoryItem>,
    val nextCursor: String?,
    val sourceItemIds: Set<String> = emptySet(),
)

/** Exact-hash queries may need several empty bounded scans before reaching their evidence. */
internal object NexusHistoryCursorCollector {
    suspend fun collect(fetch: suspend (String?) -> NexusTransferHistorySlice): List<NexusTransferHistoryItem> {
        val items = mutableListOf<NexusTransferHistoryItem>()
        val cursors = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        var cursor: String? = null
        while (true) {
            val page = fetch(cursor)
            check(page.sourceItemIds.none(ids::contains)) { "NEXUS_HISTORY_DUPLICATE_ITEM" }
            ids += page.sourceItemIds
            items += page.items
            cursor = page.nextCursor
            if (cursor == null) return items
            check(cursors.add(checkNotNull(cursor))) { "NEXUS_HISTORY_REPEATED_PAGE" }
        }
    }
}

/** Current public account movement projection; no private keys or Norito decoder are needed. */
internal object NexusAccountHistoryParser {
    fun page(result: JsonObject, network: NexusNetwork, account: String, assetDefinitionId: String,
             maximum: Int, expectedHash: String? = null,
             expectedBlockHeight: Long? = null): NexusTransferHistorySlice {
        return pageBody(result["body"] as? JsonObject ?: invalid(), network, account,
            assetDefinitionId, maximum, expectedHash, expectedBlockHeight)
    }

    /**
     * [expectedBlockHeight] is the block a narrowed lookup asked for: a row from any other block
     * means the server did not apply the filter, and the whole page is rejected.
     */
    fun pageBody(body: JsonObject, network: NexusNetwork, account: String, assetDefinitionId: String,
                 maximum: Int, expectedHash: String? = null,
                 expectedBlockHeight: Long? = null): NexusTransferHistorySlice {
        val source = body["items"] as? JsonArray ?: invalid()
        if (source.size > maximum) invalid()
        val nextCursor = cursor(body["next_cursor"])
        val ids = mutableSetOf<String>()
        val items = source.map { raw ->
            val row = raw as? JsonObject ?: invalid()
            fun text(key: String) = (row[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
            fun positive(key: String) = (row[key] as? JsonPrimitive)?.takeIf { !it.isString }
                ?.longOrNull?.takeIf { it > 0 } ?: invalid()
            val id = text("id")
            if (id.isBlank() || id.length > 2048 || !ids.add(id)) invalid()
            if (text("source") != "transaction" || text("type") != "TRANSFER" ||
                text("status") != "SUCCESS" ||
                (row["result_ok"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull != true ||
                text("asset_definition_id") != assetDefinitionId) invalid()
            val owner = IrohaAddressCodec.parse(text("account_id"), network.chainDiscriminant).address
            if (owner != account) invalid()
            val hash = text("tx_hash")
            if (NexusTransactionHash.normalized(hash) != hash || (expectedHash != null && expectedHash != hash)) invalid()
            val amount = text("amount")
            if (!NexusQuantityContract.isWireQuantity(amount) || BigDecimal(amount).signum() <= 0 ||
                (network.id == WalletNetworkId.TAIRA && !NexusQuantityContract.isTairaXorQuantity(amount))) invalid()
            val blockHeight = positive("block_height")
            if (expectedBlockHeight != null && blockHeight != expectedBlockHeight) invalid()
            val direction = text("direction")
            val counterparty = if (direction == "self") account else
                IrohaAddressCodec.parse(text("counterparty_account_id"), network.chainDiscriminant).address
            val (sender, receiver) = when (direction) {
                "incoming" -> counterparty to account
                "outgoing", "self" -> account to counterparty
                else -> invalid()
            }
            NexusTransferHistoryItem(hash, positive("timestamp_ms"), BigDecimal(amount).stripTrailingZeros().toPlainString(), sender, receiver)
        }
        return NexusTransferHistorySlice(items, nextCursor, ids)
    }

    fun cursor(raw: JsonElement?): String? = when (raw) {
        kotlinx.serialization.json.JsonNull -> null
        is JsonPrimitive -> raw.takeIf { it.isString }?.content
            ?.takeIf { it.length in 1..4096 && it.matches(Regex("^[A-Za-z0-9_-]+$")) } ?: invalid()
        else -> invalid()
    }

    private fun invalid(): Nothing = throw NexusToriiException("NEXUS_HISTORY_INVALID_RESPONSE")
}
