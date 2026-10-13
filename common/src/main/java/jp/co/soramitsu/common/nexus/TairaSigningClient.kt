package jp.co.soramitsu.common.nexus

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import jp.co.soramitsu.common.network.requireStrictNexusJsonDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Public testnet confirmation trusts canonical HTTPS Torii, not a production consensus verifier. */
class TairaSigningClient(
    private val transport: suspend (Request) -> ByteArray = { send(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Request(val path: String, val body: ByteArray? = null, val headers: Map<String, String> = emptyMap(), val maximum: Int = 65_536)
    data class Draft(val authority: String, val recipient: String, val authorityKey: ByteArray,
        val recipientKey: ByteArray, val amount: String, val createdAt: Long, val nonce: Long,
    ) {
        fun payload(fees: List<TairaTransferCodec.FeeLimit>) = TairaTransferCodec.payload(
            TairaTransferCodec.unhex(TairaTestnetContract.NETWORK_HEX), authorityKey, recipientKey,
            TairaTestnetContract.XOR_ASSET_DEFINITION_ID, amount, createdAt, nonce = nonce, fees = fees)
        fun requireFresh(now: Long) {
            check(createdAt <= now + 5_000 && now < createdAt + 80_000) { "TAIRA_QUOTE_EXPIRED" }
        }
        fun quoteBody(): ByteArray = buildJsonObject {
            putJsonObject("payload") {
                putJsonObject("domain") { put("kind", "network"); put("value", TairaTestnetContract.NETWORK_LITERAL) }
                put("authority", authority); put("creation_time_ms", createdAt)
                putJsonObject("instructions") { put("Instructions", JsonArray(listOf(JsonPrimitive(
                    TairaTransferCodec.instructionJSON(authorityKey, recipientKey, TairaTestnetContract.XOR_ASSET_DEFINITION_ID, amount))))) }
                put("time_to_live_ms", 100_000); put("nonce", nonce)
                putJsonObject("fee_payment") { put("payer", "authority"); putJsonObject("value") { put("charge_limits", JsonArray(emptyList())); put("gas_limit", JsonNull) } }
                put("metadata", JsonObject(emptyMap())); put("attachments", JsonNull)
            }
        }.toString().toByteArray()
    }
    data class Quote(val fees: List<TairaTransferCodec.FeeLimit>, val payload: ByteArray, val identity: String) {
        val total: String get() = fees.fold(java.math.BigDecimal.ZERO) { total, fee -> total + fee.maximum.toBigDecimal() }.stripTrailingZeros().toPlainString()
    }
    data class Checkpoint(val height: Long, val hash: String)

    suspend fun quote(draft: Draft, sign: (ByteArray) -> ByteArray): Quote {
        draft.requireFresh(now())
        val body = draft.quoteBody(); val timestamp = now(); val nonce = randomNonce()
        val message = authMessage(body, timestamp, nonce); val signature = sign(message)
        check(TairaTransferCodec.verify(draft.authorityKey, message, signature)) { "TAIRA_AUTH_SIGNATURE_INVALID" }
        val authority = IrohaAddressCodec.parse(draft.authority, TairaTestnetContract.I105_DISCRIMINANT)
        val data = transport(Request("/v1/fees/quote", body, mapOf(
            "X-Iroha-Account" to authority.canonicalHex,
            "X-Iroha-Signature" to Base64.getEncoder().encodeToString(signature),
            "X-Iroha-Timestamp-Ms" to timestamp.toString(), "X-Iroha-Nonce" to nonce)))
        draft.requireFresh(now())
        return decodeQuote(data, draft)
    }

    suspend fun checkpoint(): Checkpoint {
        val challenge = randomNonce() + randomNonce()
        val root = parse(transport(Request("/v1/bridge/finality/attestation/latest", headers = mapOf("X-Iroha-Finality-Challenge" to challenge), maximum = 8 * 1024 * 1024)))
        val body = root.getValue("body").jsonObject
        check(body.string("challenge") == challenge.uppercase() && body.string("network_id") == TairaTestnetContract.NETWORK_LITERAL &&
            body.string("genesis_block_hash") == TairaTestnetContract.NETWORK_LITERAL) { "TAIRA_NETWORK_MISMATCH" }
        val observed = body.integer("observed_at_unix_ms")
        check(kotlin.math.abs(now().toDouble() - observed.toDouble()) < 60_000) { "TAIRA_CHECKPOINT_STALE" }
        val status = body.getValue("status").jsonObject
        val height = status.integer("applied_height")
        check(height > 0 && status.integer("committed_height") >= height) { "TAIRA_CHECKPOINT_INVALID" }
        val block = parse(transport(Request("/v1/explorer/blocks/$height")))
        check(block.integer("height") == height) { "TAIRA_CHECKPOINT_INVALID" }
        return Checkpoint(height, hashLiteral(block.string("hash")))
    }

    companion object {
        fun authMessage(body: ByteArray, timestamp: Long, nonce: String): ByteArray =
            "iroha.app.request.network.v1\u0000".toByteArray() + TairaTransferCodec.unhex(TairaTestnetContract.NETWORK_HEX) +
                "POST\n/v1/fees/quote\n\n${TairaTransferCodec.hex(MessageDigest.getInstance("SHA-256").digest(body))}\n$timestamp\n$nonce".toByteArray()

        fun decodeQuote(data: ByteArray, draft: Draft): Quote {
            check(data.size <= 65_536)
            val root = parse(data); root.keysExactly("intent", "observation", "components", "capacities", "decision")
            val intent = root.getValue("intent").jsonObject; intent.keysExactly("payer", "value"); check(intent.string("payer") == "authority")
            val value = intent.getValue("value").jsonObject; value.keysExactly("charge_limits", "gas_limit"); check(value["gas_limit"] == JsonNull)
            val limits = value.getValue("charge_limits").jsonArray; check(limits.size <= 2 && root["components"] == limits)
            check(root.getValue("capacities").jsonArray.isEmpty())
            val observation = root.getValue("observation").jsonObject; observation.keysExactly("ledger_time_ms", "next_block_height", "route_dataspace_id")
            observation.integer("ledger_time_ms"); check(observation.integer("next_block_height") > 0 && observation.integer("route_dataspace_id") == 0L)
            val decision = root.getValue("decision").jsonObject; decision.keysExactly("status", "value"); check(decision.string("status") == "accepted")
            val accepted = decision.getValue("value").jsonObject; accepted.keysExactly("debit_source", "program_revision"); check(accepted["program_revision"] == JsonNull)
            val debit = accepted.getValue("debit_source").jsonObject; debit.keysExactly("kind", "value"); check(debit.string("kind") == "account" && debit.string("value") == draft.authority)
            val fees = limits.map { item ->
                val fee = item.jsonObject; fee.keysExactly("kind", "asset_definition_id", "max_amount")
                val kind = fee.getValue("kind").jsonObject; kind.keysExactly("kind", "value"); check(kind["value"] == JsonNull)
                val tag = when(kind.string("kind")) { "nexus" -> 0; "pipeline_gas" -> 1; else -> error("TAIRA_FEE_KIND_INVALID") }
                check(fee.string("asset_definition_id") == TairaTestnetContract.XOR_ASSET_DEFINITION_ID)
                val max = fee.string("max_amount"); check(TairaTransferCodec.canonicalQuantity(max, true) == max)
                TairaTransferCodec.FeeLimit(tag, TairaTestnetContract.XOR_ASSET_DEFINITION_ID, max)
            }
            val payload = draft.payload(fees)
            return Quote(fees, payload, TairaTransferCodec.hex(TairaTransferCodec.signingMessage(payload)))
        }

        private fun randomNonce() = UUID.randomUUID().toString().replace("-", "")
        private fun parse(data: ByteArray): JsonObject {
            val text = data.toString(Charsets.UTF_8); requireStrictNexusJsonDocument(text)
            return Json.parseToJsonElement(text).jsonObject
        }
        private fun JsonObject.keysExactly(vararg fields: String) { check(keys == fields.toSet()) { "TAIRA_FEE_SCHEMA_INVALID" } }
        private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.let { check(it.isString); it.content }
        private fun JsonObject.integer(name: String): Long = getValue(name).jsonPrimitive.let {
            check(!it.isString && Regex("^(0|[1-9][0-9]*)$").matches(it.content)); it.content.toLong()
        }
        private fun hashLiteral(raw: String): String {
            check(raw.startsWith("hash:")) { "TAIRA_BLOCK_HASH_INVALID" }
            return checkNotNull(NexusTransactionHash.normalized(raw)) { "TAIRA_BLOCK_HASH_INVALID" }
        }
        private suspend fun send(request: Request): ByteArray = withContext(Dispatchers.IO) {
            check(request.path in setOf("/v1/fees/quote", "/v1/bridge/finality/attestation/latest") || Regex("^/v1/explorer/blocks/[1-9][0-9]*$").matches(request.path))
            val connection = URL(TairaTestnetContract.TORII_ROOT + request.path).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false; connection.connectTimeout = 25_000; connection.readTimeout = 25_000
                connection.useCaches = false; connection.setRequestProperty("Accept", "application/json")
                request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                request.body?.let { body ->
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(body.size); connection.outputStream.use { it.write(body) }
                }
                check(connection.responseCode == 200) { "TAIRA_HTTP_${connection.responseCode}" }
                check(connection.contentType?.substringBefore(';')?.trim() == "application/json") { "TAIRA_CONTENT_TYPE_INVALID" }
                check(connection.contentLengthLong <= request.maximum)
                val output = java.io.ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        ensureActive(); val read = input.read(buffer); if (read < 0) break
                        check(output.size() + read <= request.maximum) { "TAIRA_RESPONSE_TOO_LARGE" }; output.write(buffer, 0, read)
                    }
                }
                output.toByteArray()
            } finally { connection.disconnect() }
        }
    }
}
