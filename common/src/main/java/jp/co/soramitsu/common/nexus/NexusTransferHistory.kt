package jp.co.soramitsu.common.nexus

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

data class NexusTransferHistoryItem(
    val transactionHash: String,
    val timestampMillis: Long,
    val amount: String,
    val sender: String,
    val receiver: String,
)

internal data class NexusTransferHistoryPage(
    val items: List<NexusTransferHistoryItem>,
    val sourceItemCount: Int,
) {
    fun requireBoundedSourceCount(maximum: Int) {
        require(maximum > 0) { "NEXUS_HISTORY_PAGE_SIZE_INVALID" }
        if (sourceItemCount !in 0..maximum) {
            throw NexusToriiException("NEXUS_HISTORY_PAGE_SIZE_EXCEEDED")
        }
    }
}

internal object NexusTransferHistoryParser {
    private val unsignedInteger = Regex("^[0-9]+$")
    private const val millisThreshold = 10_000_000_000L

    fun page(
        json: Json,
        result: JsonElement,
        network: NexusNetwork,
        account: String,
        assetDefinitionId: String,
    ): NexusTransferHistoryPage {
        val root = unwrapToolResult(json, result)
        val body = root["body"] as? JsonObject ?: root
        val items = body["items"] as? JsonArray
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_RESPONSE")

        return NexusTransferHistoryPage(
            sourceItemCount = items.size,
            items = items.flatMap { element ->
                parseInstruction(
                    element = element as? JsonObject
                        ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_ITEM"),
                    network = network,
                    account = account,
                    assetDefinitionId = assetDefinitionId,
                )
            },
        )
    }

    private fun unwrapToolResult(json: Json, result: JsonElement): JsonObject {
        val direct = result as? JsonObject
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_RESPONSE")
        if (direct["isError"].asBoolean() == true) {
            throw NexusToriiException("NEXUS_HISTORY_TOOL_ERROR")
        }
        if ("body" in direct || "items" in direct) return direct
        (direct["structuredContent"] as? JsonObject)?.let { return it }

        val content = direct["content"] as? JsonArray
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_RESPONSE")
        val text = content.asSequence()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it["type"].asString() == "text" }
            ?.get("text")
            .asString()
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_RESPONSE")
        return runCatching { json.parseToJsonElement(text) as? JsonObject }
            .getOrNull()
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_RESPONSE")
    }

    private fun parseInstruction(
        element: JsonObject,
        network: NexusNetwork,
        account: String,
        assetDefinitionId: String,
    ): List<NexusTransferHistoryItem> {
        val status = element.firstString(
            "transaction_status",
            "transactionStatus",
            "status",
        ) ?: throw NexusToriiException("NEXUS_HISTORY_STATUS_MISSING")
        if (!status.equals("committed", ignoreCase = true)) return emptyList()

        val hash = element.firstString("transaction_hash", "transactionHash", "hash")
            ?.let(NexusTransactionHash::normalized)
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_HASH")
        val timestamp = parseTimestamp(
            element.firstString("created_at", "createdAt", "timestamp")
        ) ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_TIMESTAMP")
        val payload = (((element["box"] as? JsonObject)?.get("json") as? JsonObject)
            ?.get("payload") as? JsonObject)
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_PAYLOAD")

        val transfers = when (payload["variant"].asString()) {
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
            else -> emptyList()
        }

        return transfers.map {
            NexusTransferHistoryItem(
                transactionHash = hash,
                timestampMillis = timestamp,
                amount = it.amount.stripTrailingZeros().toPlainString(),
                sender = it.sender,
                receiver = it.receiver,
            )
        }
    }

    private fun JsonObject.parseTransfer(
        network: NexusNetwork,
        account: String,
        assetDefinitionId: String,
    ): ParsedTransfer? {
        val source = firstString(
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
        val destination = firstString("destination", "destination_id", "to", "account_id")
            ?: throw NexusToriiException("NEXUS_HISTORY_DESTINATION_MISSING")
        val explicitSourceAccount = firstString("source_account", "from", "account")
        val amount = parseAmount(
            this["object"] ?: this["amount"] ?: this["quantity"] ?: this["value"]
        ) ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_AMOUNT")

        val canonicalDestination = canonicalHistoryAccount(
            network,
            destination,
            "NEXUS_HISTORY_INVALID_DESTINATION",
        )
        val canonicalExplicitSource = explicitSourceAccount?.let {
            canonicalHistoryAccount(network, it, "NEXUS_HISTORY_INVALID_SOURCE")
        }
        val canonicalEmbeddedSource = embeddedSourceAccount?.let {
            canonicalHistoryAccount(network, it, "NEXUS_HISTORY_INVALID_SOURCE")
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
            ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_DESTINATION")
        return ParsedTransfer(
            amount = amount,
            sender = sender,
            receiver = receiver,
        )
    }

    private fun JsonObject.parseBatchTransfer(
        network: NexusNetwork,
        account: String,
        assetDefinitionId: String,
    ): ParsedTransfer? {
        val definition = firstString(
            "asset_definition",
            "assetDefinition",
            "asset_definition_id",
        ) ?: throw NexusToriiException("NEXUS_HISTORY_INVALID_BATCH_ASSET")
        if (definition != assetDefinitionId) return null
        return parseTransfer(network, account, assetDefinitionId)
    }

    private fun canonicalHistoryAccount(
        network: NexusNetwork,
        value: String,
        errorCode: String,
    ): String = try {
        NexusToriiRoutes.canonicalAccount(network, value)
    } catch (error: IllegalArgumentException) {
        throw NexusToriiException(errorCode, cause = error)
    }

    private fun parseAmount(element: JsonElement?): BigDecimal? {
        val parsed = when (element) {
            is JsonPrimitive -> element.contentOrNull
                ?.takeIf(NexusQuantityContract::isWireQuantity)
                ?.let(::BigDecimal)
            is JsonObject -> {
                val rawScale = element["scale"]?.asExactString()
                    ?.takeIf { it.length <= MAX_SCALE_LENGTH }
                if (rawScale == null) {
                    parseAmount(element["value"] ?: element["amount"] ?: element["mantissa"])
                } else {
                    val scale = rawScale.toIntOrNull()?.takeIf { it in 0..MAX_AMOUNT_SCALE }
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
        val timestamp = value?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_TIMESTAMP_LENGTH }
            ?: return null
        if (timestamp.all(Char::isDigit)) {
            val raw = timestamp.toLongOrNull() ?: return null
            return runCatching {
                if (raw < millisThreshold) Math.multiplyExact(raw, 1_000) else raw
            }.getOrNull()
        }
        return runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrNull()
    }

    private fun JsonObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { this[it].asExactString() }

    private fun JsonElement?.asString(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonElement?.asExactString(): String? =
        (this as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it == it.trim() && it.isNotEmpty() }

    private fun JsonElement?.asBoolean(): Boolean? =
        (this as? JsonPrimitive)?.booleanOrNull

    private data class ParsedTransfer(
        val amount: BigDecimal,
        val sender: String,
        val receiver: String,
    )

    private const val MAX_AMOUNT_SCALE = NexusQuantityContract.MAX_SCALE
    private const val MAX_SCALE_LENGTH = 3
    private const val MAX_TIMESTAMP_LENGTH = 64
}
