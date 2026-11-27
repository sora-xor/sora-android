package jp.co.soramitsu.common.hashi

import java.util.concurrent.atomic.AtomicLong
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** One JSON-RPC POST to [endpoint]; returns the raw response body. */
fun interface HashiRpcTransport {
    suspend fun post(endpoint: String, body: String): String
}

/** The wallet's bounded HTTPS client: HTTPS only, no redirects, size- and time-limited. */
class BoundedHashiRpcTransport(
    private val client: BoundedHttpTextClient,
    private val maximumResponseBytes: Int = MAXIMUM_RESPONSE_BYTES,
) : HashiRpcTransport {
    override suspend fun post(endpoint: String, body: String): String =
        client.postJson(
            rawUrl = endpoint,
            requestBody = body,
            maximumBytes = maximumResponseBytes,
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS,
            absoluteTimeoutMillis = ABSOLUTE_TIMEOUT_MILLIS,
        ).body

    companion object {
        /** `ethBridge_getRegisteredAssets` is about 40 KB; a block header about 30 KB. */
        const val MAXIMUM_RESPONSE_BYTES = 2 * 1024 * 1024
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val ABSOLUTE_TIMEOUT_MILLIS = 30_000L
    }
}

/** A JSON-RPC error answer. [code] and [message] come from the node; [data] when it sent some. */
open class HashiRpcErrorException(
    val code: Int?,
    override val message: String,
    val data: String?,
) : IllegalStateException(message)

/** No endpoint gave an answer (every one failed in transport or answered something malformed). */
class HashiRpcUnavailableException(cause: Throwable?) : IllegalStateException("HASHI_RPC_UNAVAILABLE", cause)

internal object HashiJsonRpc {

    val json = Json { ignoreUnknownKeys = false }

    private val ids = AtomicLong(1)

    fun request(method: String, params: JsonArray): Pair<Long, String> {
        val id = ids.getAndIncrement()
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        return id to body.toString()
    }

    /** The `result` of a response to request [id]; throws [HashiRpcErrorException] for an error. */
    fun result(body: String, id: Long): JsonElement {
        val response = try {
            json.parseToJsonElement(body)
        } catch (error: Exception) {
            throw IllegalArgumentException("HASHI_RPC_RESPONSE_INVALID", error)
        }
        require(response is JsonObject) { "HASHI_RPC_RESPONSE_INVALID" }
        require((response["jsonrpc"] as? JsonPrimitive)?.content == "2.0") { "HASHI_RPC_RESPONSE_INVALID" }
        require((response["id"] as? JsonPrimitive)?.longOrNull == id) { "HASHI_RPC_RESPONSE_ID_MISMATCH" }
        val error = response["error"]
        if (error != null && error !is JsonNull) {
            val errorObject = error as? JsonObject ?: throw IllegalArgumentException("HASHI_RPC_RESPONSE_INVALID")
            val code = (errorObject["code"] as? JsonPrimitive)?.intOrNull
            val message = (errorObject["message"] as? JsonPrimitive)?.content ?: "HASHI_RPC_ERROR"
            val data = when (val raw = errorObject["data"]) {
                null, is JsonNull -> null
                is JsonPrimitive -> raw.content
                else -> raw.toString()
            }
            throw HashiRpcErrorException(code, message.take(512), data?.take(4096))
        }
        require(response.containsKey("result")) { "HASHI_RPC_RESPONSE_INVALID" }
        return response.getValue("result")
    }
}
