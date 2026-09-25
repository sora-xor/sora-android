package jp.co.soramitsu.common.nexus

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

data class NexusTransferHistoryItem(
    val transactionHash: String,
    val timestampMillis: Long,
    val amount: String,
    val sender: String,
    val receiver: String,
)

internal data class NexusTransferHistoryPage(
    val items: List<NexusTransferHistoryItem>,
    val sourceItemIds: Set<String>,
    val sourceItemCount: Int,
    val page: Long,
    val perPage: Long,
    val totalPages: Long,
    val totalItems: Long,
) {
    fun requirePageContract(expectedPage: Int, maximum: Int) {
        val calculatedPages = when {
            perPage <= 0L -> -1L
            totalItems == 0L -> 0L
            else -> 1L + (totalItems - 1L) / perPage
        }
        val precedingItemCount = if (page > 0L && perPage > 0L) {
            runCatching { Math.multiplyExact(page - 1L, perPage) }.getOrNull()
        } else {
            null
        }
        val expectedItemCount = when {
            page <= 0L || perPage <= 0L || totalPages != calculatedPages -> -1L
            page > maxOf(totalPages, 1L) -> -1L
            totalItems == 0L -> 0L
            precedingItemCount == null || precedingItemCount !in 0L..totalItems -> -1L
            else -> minOf(perPage, totalItems - precedingItemCount)
        }
        if (
            expectedPage <= 0 ||
            maximum <= 0 ||
            sourceItemCount !in 0..maximum ||
            sourceItemIds.size != sourceItemCount ||
            page != expectedPage.toLong() ||
            perPage != maximum.toLong() ||
            totalPages < 0 ||
            totalItems < 0 ||
            (totalPages == 0L) != (totalItems == 0L) ||
            totalPages != calculatedPages ||
            page > maxOf(totalPages, 1L) ||
            sourceItemCount.toLong() != expectedItemCount
        ) {
            throw NexusToriiException("NEXUS_HISTORY_PAGE_INVALID")
        }
    }
}

/** Parser for the current `iroha.instructions.list` explorer projection. */
internal object NexusTransferHistoryParser {
    private val unsignedInteger = Regex("^[0-9]+$")
    private const val millisThreshold = 10_000_000_000L

    fun page(
        result: JsonObject,
        network: NexusNetwork,
        account: String,
        assetDefinitionId: String,
    ): NexusTransferHistoryPage {
        val body = result["body"] as? JsonObject
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_RESPONSE")
        val items = body["items"] as? JsonArray
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_RESPONSE")
        val pagination = body["pagination"] as? JsonObject
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_RESPONSE")

        val parsed = items.map { element ->
            parseInstruction(
                element = element as? JsonObject
                    ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_ITEM"),
                network = network,
                account = account,
                assetDefinitionId = assetDefinitionId,
            )
        }
        val sourceIds = parsed.map(ParsedInstruction::sourceId)
        if (sourceIds.size != sourceIds.toSet().size) {
            throw NexusToriiException("NEXUS_HISTORY_DUPLICATE_ITEM")
        }
        return NexusTransferHistoryPage(
            sourceItemCount = items.size,
            sourceItemIds = sourceIds.toSet(),
            page = pagination.canonicalLong("page")
                ?: throw NexusToriiException("NEXUS_HISTORY_PAGINATION_INVALID"),
            perPage = pagination.canonicalLong("per_page")
                ?: throw NexusToriiException("NEXUS_HISTORY_PAGINATION_INVALID"),
            totalPages = pagination.canonicalLong("total_pages")
                ?: throw NexusToriiException("NEXUS_HISTORY_PAGINATION_INVALID"),
            totalItems = pagination.canonicalLong("total_items")
                ?: throw NexusToriiException("NEXUS_HISTORY_PAGINATION_INVALID"),
            items = parsed.flatMap(ParsedInstruction::items),
        )
    }

    private fun parseInstruction(
        element: JsonObject,
        network: NexusNetwork,
        account: String,
        assetDefinitionId: String,
    ): ParsedInstruction {
        val status = element.exactString("transaction_status")
            ?: throw NexusToriiException("NEXUS_HISTORY_STATUS_MISSING")
        if (!status.equals("committed", ignoreCase = true)) {
            throw NexusToriiException("NEXUS_HISTORY_IDENTITY_MISMATCH")
        }
        if (element.exactString("kind") != "Transfer") {
            throw NexusToriiException("NEXUS_HISTORY_IDENTITY_MISMATCH")
        }

        val rawHash = element.exactString("transaction_hash")
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_HASH")
        val hash = NexusTransactionHash.normalized(rawHash)
            ?.takeIf { it == rawHash }
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_HASH")
        val index = element.canonicalLong("index")
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_INDEX")
        if (element.canonicalLong("block")?.let { it > 0L } != true) {
            throw NexusToriiException("NEXUS_HISTORY_INVALID_BLOCK")
        }
        val timestamp = parseTimestamp(element.exactString("created_at"))
            ?.takeIf { it > 0L }
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_TIMESTAMP")
        element.exactString("authority")?.let {
            canonicalAccount(network, it, "NEXUS_HISTORY_INVALID_AUTHORITY")
        } ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_AUTHORITY")
        val projection = (element["box"] as? JsonObject)?.get("json") as? JsonObject
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_PAYLOAD")
        if (projection.exactString("kind") != "Transfer") {
            throw NexusToriiException("NEXUS_HISTORY_IDENTITY_MISMATCH")
        }
        val payload = (projection["payload"] as? JsonObject)
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_PAYLOAD")

        val transfers = when (payload.exactString("variant")) {
            "Asset" -> listOfNotNull(
                (payload["value"] as? JsonObject)?.parseTransfer(
                    network,
                    account,
                    assetDefinitionId,
                )
            )
            "AssetBatch" -> {
                val value = payload["value"] as? JsonObject
                    ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_PAYLOAD")
                val entries = listOf("entries", "transfers", "items")
                    .firstNotNullOfOrNull { value[it] as? JsonArray }
                    ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_PAYLOAD")
                entries.mapNotNull {
                    val entry = it as? JsonObject
                        ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_PAYLOAD")
                    entry.parseBatchTransfer(network, account, assetDefinitionId)
                }
            }
            else -> throw NexusToriiException("NEXUS_HISTORY_IDENTITY_MISMATCH")
        }
        if (transfers.isEmpty()) {
            throw NexusToriiException("NEXUS_HISTORY_IDENTITY_MISMATCH")
        }

        return ParsedInstruction(
            sourceId = "$hash:$index",
            items = transfers.map { transfer ->
                NexusTransferHistoryItem(
                    transactionHash = hash,
                    timestampMillis = timestamp,
                    amount = transfer.amount.stripTrailingZeros().toPlainString(),
                    sender = transfer.sender,
                    receiver = transfer.receiver,
                )
            },
        )
    }

    private fun JsonObject.parseTransfer(
        network: NexusNetwork,
        account: String,
        assetDefinitionId: String,
    ): ParsedTransfer? {
        val source = firstExactString(
            "source",
            "source_id",
            "asset",
            "asset_id",
            "asset_definition",
            "assetDefinition",
            "asset_definition_id",
        )
        val embeddedSourceAccount = when {
            source == null || source == assetDefinitionId -> null
            source.startsWith("$assetDefinitionId#") ->
                source.removePrefix("$assetDefinitionId#")
                    .takeIf { it.isNotEmpty() && !it.contains('#') }
                    ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_SOURCE_ASSET")
            else -> return null
        }
        val destination = firstExactString("destination", "destination_id", "to", "account_id")
            ?: throw NexusToriiException("NEXUS_HISTORY_DESTINATION_MISSING")
        val explicitSourceAccount = firstExactString("source_account", "from", "account")
        val amount = parseAmount(
            this["object"] ?: this["amount"] ?: this["quantity"] ?: this["value"]
        ) ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_AMOUNT")
        if (
            network.id == WalletNetworkId.TAIRA &&
            amount.scale().coerceAtLeast(0) > TairaTestnetContract.XOR_SCALE
        ) {
            throw NexusToriiException("TAIRA_XOR_SCALE_INVALID")
        }

        val canonicalDestination = canonicalAccount(network, destination, "NEXUS_HISTORY_INVALID_DESTINATION")
        val canonicalExplicitSource = explicitSourceAccount?.let {
            canonicalAccount(network, it, "NEXUS_HISTORY_INVALID_SOURCE")
        }
        val canonicalEmbeddedSource = embeddedSourceAccount?.let {
            canonicalAccount(network, it, "NEXUS_HISTORY_INVALID_SOURCE")
        }
        if (
            canonicalExplicitSource != null &&
            canonicalEmbeddedSource != null &&
            canonicalExplicitSource != canonicalEmbeddedSource
        ) {
            throw NexusToriiException("NEXUS_HISTORY_SOURCE_MISMATCH")
        }
        val canonicalSource = canonicalExplicitSource ?: canonicalEmbeddedSource
        val incoming = canonicalDestination == account
        val outgoing = canonicalSource == account
        if (!incoming && !outgoing) return null

        val sender = if (outgoing) account else canonicalSource
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_SOURCE")
        val receiver = if (incoming) account else canonicalDestination
        return ParsedTransfer(amount, sender, receiver)
    }

    private fun JsonObject.parseBatchTransfer(
        network: NexusNetwork,
        account: String,
        assetDefinitionId: String,
    ): ParsedTransfer? {
        val definition = firstExactString(
            "asset_definition",
            "assetDefinition",
            "asset_definition_id",
        ) ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_BATCH_ASSET")
        if (definition != assetDefinitionId) return null
        return parseTransfer(network, account, assetDefinitionId)
    }

    private fun parseAmount(element: JsonElement?): BigDecimal? {
        val parsed = when (element) {
            is JsonPrimitive -> element.contentOrNull
                ?.takeIf(NexusQuantityContract::isWireQuantity)
                ?.let(::BigDecimal)
            is JsonObject -> {
                val rawScale = element.exactString("scale")
                if (rawScale == null) {
                    parseAmount(element["value"] ?: element["amount"] ?: element["mantissa"])
                } else {
                    val scale = rawScale.toIntOrNull()?.takeIf { it in 0..NexusQuantityContract.MAX_SCALE }
                    val mantissa = (
                        element["value"] ?: element["amount"] ?: element["mantissa"]
                        ).asExactString()
                        ?.takeIf { it.length <= NexusQuantityContract.MAX_WIRE_CHARACTERS }
                        ?.takeIf(unsignedInteger::matches)
                        ?.let(::BigInteger)
                    if (scale == null || mantissa == null) null else BigDecimal(mantissa, scale)
                }
            }
            else -> null
        }
        return parsed?.takeIf { it.signum() > 0 }
    }

    private fun parseTimestamp(value: String?): Long? {
        val timestamp = value?.takeIf { it.length <= 64 } ?: return null
        if (timestamp.all(Char::isDigit)) {
            val raw = timestamp.toLongOrNull() ?: return null
            return runCatching {
                if (raw < millisThreshold) Math.multiplyExact(raw, 1_000) else raw
            }.getOrNull()
        }
        return runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrNull()
    }

    private fun canonicalAccount(network: NexusNetwork, value: String, code: String): String =
        try {
            NexusToriiRoutes.canonicalAccount(network, value)
        } catch (error: IllegalArgumentException) {
            throw NexusToriiException(code, cause = error)
        }

    private fun JsonObject.firstExactString(vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> this.exactString(name) }

    private fun JsonObject.exactString(name: String): String? = this[name].asExactString()

    private fun JsonElement?.asExactString(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.takeIf { it.isNotEmpty() && it == it.trim() && it.length <= 2_048 }

    private fun JsonObject.canonicalLong(name: String): Long? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.longOrNull?.takeIf { it >= 0 && primitive.content == it.toString() }
    }

    private data class ParsedTransfer(
        val amount: BigDecimal,
        val sender: String,
        val receiver: String,
    )

    private data class ParsedInstruction(
        val sourceId: String,
        val items: List<NexusTransferHistoryItem>,
    )
}
