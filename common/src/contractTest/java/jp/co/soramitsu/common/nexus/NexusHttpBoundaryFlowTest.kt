package jp.co.soramitsu.common.nexus

import java.io.*
import java.net.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

/** Exercises real HTTP admission/receipt handling through an in-memory connection, never a server. */
class NexusHttpBoundaryFlowTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val network = NexusNetworks.taira
    private val hash = "ab".repeat(32)
    private val body = byteArrayOf(1, 2, 3, 4) // Opaque fixture bytes; not a signed transaction.
    private val requestId = "taira-submit-${hash.take(12)}"
    private val connections = mutableListOf<FakeConnection>()
    private var configure: (FakeConnection) -> Unit = {}
    private val client = NexusToriiClient(json, null) { url ->
        FakeConnection(url).also { configure(it); connections += it }
    }
    private class FakeConnection(url: URL) : HttpURLConnection(url) {
        var status = 200
        var response = "Healthy".toByteArray()
        var failureBody: ByteArray? = null
        var outputError: IOException? = null
        var inputError: IOException? = null
        var statusError: IOException? = null
        var disconnected = false
        var inputClosed = false
        var outputClosed = false
        var fixedLength = -1
        val headers = mutableMapOf("content-type" to "text/plain")
        val sent = object : ByteArrayOutputStream() { override fun close() { outputClosed = true; super.close() } }
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getHeaderField(name: String) = headers[name.lowercase()]
        override fun getResponseCode(): Int { statusError?.let { throw it }; return status }
        override fun setFixedLengthStreamingMode(contentLength: Int) { fixedLength = contentLength; super.setFixedLengthStreamingMode(contentLength) }
        override fun getOutputStream(): OutputStream { outputError?.let { throw it }; return sent }
        override fun getInputStream(): InputStream { inputError?.let { throw it }; return object : ByteArrayInputStream(response) {
            override fun close() { inputClosed = true; super.close() }
        } }
        override fun getErrorStream(): InputStream? = failureBody?.let { object : ByteArrayInputStream(it) {
            override fun close() { inputClosed = true; super.close() }
        } }
    }
    private fun envelope(result: JsonElement = success(), id: String = requestId, version: String = "2.0") = buildJsonObject {
        put("jsonrpc", version); put("id", id); put("result", result)
    }.toString()
    private fun submissionResponse(payload: String = envelope(), status: Int = 200) {
        configure = { it.status = status; it.headers["content-type"] = "application/json"; it.response = payload.toByteArray(); it.failureBody = payload.toByteArray() }
    }
    private fun submit() = runBlocking { client.submit(network, body, hash) }
    private fun failedSubmit(code: String): NexusToriiException {
        val error = assertThrows(NexusToriiException::class.java) { submit() }
        assertEquals(code, error.safeCode); assertTrue(error.submissionMayHaveReachedTorii)
        assertTrue(connections.single().disconnected); assertEquals(1, connections.size)
        return error
    }
    private fun failedRead(code: String): NexusToriiException {
        val error = assertThrows(NexusToriiException::class.java) { runBlocking { client.health(network) } }
        assertEquals(code, error.safeCode); assertFalse(error.submissionMayHaveReachedTorii)
        assertTrue(connections.single().disconnected); return error
    }
    @Test fun healthRequestUsesBoundedGetAndClosesResponse() = runBlocking {
        assertEquals("Healthy", client.health(network)); val sent = connections.single()
        assertEquals("GET", sent.requestMethod); assertEquals("text/plain", sent.getRequestProperty("Accept"))
        assertEquals("SORA-Wallet", sent.getRequestProperty("User-Agent")); assertEquals(15_000, sent.connectTimeout)
        assertEquals(30_000, sent.readTimeout); assertFalse(sent.instanceFollowRedirects); assertFalse(sent.doOutput)
        assertTrue(sent.disconnected); assertTrue(sent.inputClosed); assertEquals(0, sent.sent.size())
    }
    @Test fun healthRejectsUnexpectedBodyEvenWithSuccessStatus() { configure = { it.response = "healthy".toByteArray() }; failedRead("NEXUS_HEALTH_INVALID") }
    @Test fun successfulReadRequiresExactMediaType() { configure = { it.headers["content-type"] = "text/html" }; failedRead("NEXUS_RESPONSE_CONTENT_TYPE_INVALID") }
    @Test fun missingMediaTypeCannotBecomeSuccess() { configure = { it.headers.clear() }; failedRead("NEXUS_RESPONSE_CONTENT_TYPE_INVALID") }
    @Test fun redirectIsNotFollowed() { configure = { it.status = 302; it.headers["location"] = "https://untrusted.invalid" }; failedRead("NEXUS_HTTP_302") }
    @Test fun ordinaryHttpErrorRetainsStatus() { configure = { it.status = 401 }; assertEquals(401, failedRead("NEXUS_HTTP_401").httpStatus) }
    @Test fun unavailableIngressHasDeploymentCategory() {
        configure = { it.status = 503 }; assertEquals(NexusToriiFailureCategory.DEPLOYMENT_HEALTH, failedRead("NEXUS_PUBLIC_INGRESS_UNAVAILABLE").category)
    }
    @Test fun readSocketFailureHasTransportCategory() {
        configure = { it.statusError = SocketTimeoutException("fixture timeout") }
        assertEquals(NexusToriiFailureCategory.TRANSPORT, failedRead("NEXUS_NETWORK_IO").category)
    }
    @Test fun readStreamFailureStillDisconnects() { configure = { it.inputError = IOException("fixture read") }; failedRead("NEXUS_NETWORK_IO") }
    @Test fun oversizedReadIsRejectedAndStreamClosed() {
        configure = { it.response = ByteArray(2 * 1024 * 1024 + 1) }
        failedRead("NEXUS_RESPONSE_TOO_LARGE"); assertTrue(connections.single().inputClosed)
    }
    @Test fun oversizedErrorBodyIsRejectedAndStreamClosed() {
        configure = { it.status = 400; it.failureBody = ByteArray(16 * 1024 + 1) }
        failedRead("NEXUS_RESPONSE_TOO_LARGE"); assertTrue(connections.single().inputClosed)
    }
    @Test fun incompleteFanoutOverridesOtherwiseHealthyBody() {
        configure = { it.headers["x-iroha-fanout-routes-attempted"] = "1" }
        failedRead("NEXUS_FANOUT_HEADERS_INVALID")
    }
    @Test fun submissionSerializesExactOpaqueBytesAndProtocolHeaders() {
        submissionResponse(); assertEquals(hash, submit().payload.transactionHash)
        val sent = connections.single(); assertEquals("POST", sent.requestMethod)
        assertEquals(TairaTestnetContract.MCP_ENDPOINT, sent.url.toString())
        assertEquals("application/json", sent.getRequestProperty("Content-Type"))
        assertEquals("tools/call", sent.getRequestProperty("Mcp-Method"))
        assertEquals(TairaMcpContract.SUBMIT_AND_WAIT_TOOL, sent.getRequestProperty("Mcp-Name"))
        assertEquals(TairaTestnetContract.MCP_PROTOCOL_VERSION, sent.getRequestProperty("MCP-Protocol-Version"))
        assertEquals(135_000, sent.readTimeout); assertEquals(sent.sent.size(), sent.fixedLength)
        assertTrue(sent.outputClosed); assertTrue(sent.inputClosed); assertTrue(sent.disconnected)
        val request = json.parseToJsonElement(sent.sent.toString("UTF-8")).jsonObject
        val arguments = request.getValue("params").jsonObject.getValue("arguments").jsonObject
        assertEquals("AQIDBA==", arguments.getValue("body_base64").jsonPrimitive.content)
        assertEquals(hash, arguments.getValue("hash").jsonPrimitive.content)
        assertEquals(requestId, request.getValue("id").jsonPrimitive.content)
    }
    @Test fun emptySubmissionNeverOpensConnection() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.submit(network, byteArrayOf(), hash) } }
        assertTrue(connections.isEmpty())
    }
    @Test fun oversizedSubmissionNeverOpensConnection() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.submit(network, ByteArray(2 * 1024 * 1024 + 1), hash) } }
        assertTrue(connections.isEmpty())
    }
    @Test fun invalidHashNeverOpensConnection() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.submit(network, body, "invalid") } }
        assertTrue(connections.isEmpty())
    }
    @Test fun unavailableNetworkNeverOpensConnection() {
        assertThrows(IllegalStateException::class.java) { runBlocking { client.submit(NexusNetworks.minamoto, body, hash) } }
        assertTrue(connections.isEmpty())
    }
    @Test fun outputFailureConservativelyMarksSubmissionAmbiguous() {
        configure = { it.outputError = IOException("fixture write") }; failedSubmit("NEXUS_SUBMISSION_IO")
    }
    @Test fun unreadableReceiptMarksSubmissionAmbiguous() {
        submissionResponse(); val original = configure; configure = { original(it); it.inputError = IOException("fixture read") }
        failedSubmit("NEXUS_SUBMISSION_IO")
    }
    @Test fun missingMcpEndpointHasActionableTypedError() {
        submissionResponse(status = 404); assertEquals(NexusToriiFailureCategory.DEPLOYMENT_HEALTH, failedSubmit("TAIRA_MCP_NOT_ENABLED").category)
    }
    @Test fun malformedReceiptRemainsAmbiguousWithoutRetry() { submissionResponse("{"); failedSubmit("NEXUS_INVALID_RESPONSE") }
    @Test fun missingMcpResultRemainsAmbiguous() { submissionResponse("""{"jsonrpc":"2.0","id":"$requestId"}"""); failedSubmit("TAIRA_SUBMIT_AND_WAIT_RESULT_MISSING") }
    @Test fun wrongMcpIdentityRemainsTypedAndAmbiguous() { submissionResponse(envelope(id = "different")); failedSubmit("NEXUS_MCP_IDENTITY_MISMATCH") }
    @Test fun wrongMcpVersionRemainsTypedAndAmbiguous() { submissionResponse(envelope(version = "1.0")); failedSubmit("NEXUS_MCP_IDENTITY_MISMATCH") }
    @Test fun wrongSubmissionHashRemainsAmbiguous() { submissionResponse(envelope(success().let {
        JsonObject(it + ("structuredContent" to JsonObject(it.getValue("structuredContent").jsonObject + ("hash" to JsonPrimitive("cd".repeat(32))))))
    })); failedSubmit("NEXUS_TRANSACTION_HASH_MISMATCH") }
    @Test fun missingReceiptContentTypeRemainsAmbiguous() {
        submissionResponse(); val original = configure; configure = { original(it); it.headers.clear() }; failedSubmit("NEXUS_RESPONSE_CONTENT_TYPE_INVALID")
    }
    private fun mcpError(code: Int, message: String, data: JsonElement? = null) {
        submissionResponse(buildJsonObject {
            put("jsonrpc", "2.0"); put("id", requestId); putJsonObject("error") {
                put("code", code); put("message", message); data?.let { put("data", it) }
            }
        }.toString())
    }
    @Test fun missingToolPreservesServerDiagnostic() {
        mcpError(-32601, "Tool unavailable", JsonPrimitive("public diagnostic"))
        val error = failedSubmit("NEXUS_MCP_TOOL_UNAVAILABLE")
        assertEquals("Tool unavailable", error.serverMessage); assertEquals(JsonPrimitive("public diagnostic"), error.serverData)
    }
    @Test fun invalidArgumentsPreserveTypedFailure() { mcpError(-32602, "Invalid arguments"); failedSubmit("NEXUS_MCP_ARGUMENTS_REJECTED") }
    @Test fun otherMcpErrorRetainsCodeWithoutRetry() { mcpError(-32001, "Server error"); failedSubmit("NEXUS_MCP_ERROR_-32001") }
    @Test fun oversizedMcpMessageIsRejected() { mcpError(-32001, "x".repeat(4097)); failedSubmit("NEXUS_MCP_ERROR_INVALID") }
    @Test fun paddedMcpMessageIsRejected() { mcpError(-32001, " padded "); failedSubmit("NEXUS_MCP_ERROR_INVALID") }
    @Test fun oversizedMcpDataIsDiscarded() { mcpError(-32001, "Server error", JsonPrimitive("x".repeat(16385))); assertNull(failedSubmit("NEXUS_MCP_ERROR_-32001").serverData) }
    @Test fun definitionReadsRequireFanoutEvidence() {
        configure = { it.headers["content-type"] = "application/json"; it.response = "{}".toByteArray() }
        val error = assertThrows(NexusToriiException::class.java) { runBlocking { client.resolveXorDefinition(network) } }
        assertEquals("NEXUS_FANOUT_EVIDENCE_MISSING", error.safeCode); assertTrue(connections.single().disconnected)
    }
    @Test fun publicTairaRouteAllowsQualifiedDefinitionRead() = runBlocking {
        configure = { it.headers["content-type"] = "application/json"; it.headers["x-iroha-routed-by"] = "local"
            it.headers["x-iroha-route-lane-id"] = "0"; it.headers["x-iroha-route-dataspace-id"] = "0"
            it.response = """{"id":"${TairaTestnetContract.XOR_ASSET_DEFINITION_ID}","name":"XOR"}""".toByteArray() }
        assertEquals(TairaTestnetContract.XOR_ASSET_DEFINITION_ID, client.resolveXorDefinition(network).id)
    }
    private fun success(): JsonObject {
        fun routed(body: JsonElement, receipt: Boolean = false) = buildJsonObject {
            put("status", 200); put("content_type", "application/json"); put("body", body)
            putJsonObject("headers") { put("content-type", "application/json")
                if (receipt) { put("x-iroha-entrypoint-hash", hash); put("x-iroha-signed-transaction-hash", hash) } }
        }
        return buildJsonObject { put("isError", false); putJsonObject("structuredContent") {
            put("status", 200); put("hash", hash); put("terminal_kind", "Applied"); put("attempts", 1); put("elapsed_ms", 1)
            put("submit", routed(buildJsonObject { putJsonObject("payload") { put("entrypoint_hash", hash)
                put("signed_transaction_hash", hash); put("submitted_at_ms", 1); put("submitted_at_height", 42) } }, true))
            put("final", routed(buildJsonObject { put("hash", hash); putJsonObject("status") { put("kind", "Applied"); put("block_height", 43) }
                put("scope", "global"); put("resolved_from", "state") }))
        } }
    }
}
