package jp.co.soramitsu.common.nexus

import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class NexusRoutedResponseBoundaryTest {
    private fun headers(failed: Int = 0, denied: Int = 0, missing: Int = 0, unavailable: Int = 0) = mapOf(
        "x-iroha-routed-by" to "proxy", "x-iroha-fanout-routes-attempted" to "1",
        "x-iroha-fanout-routes-succeeded" to (1 - failed).toString(), "x-iroha-fanout-routes-failed" to failed.toString(),
        "x-iroha-fanout-routes-unavailable" to unavailable.toString(), "x-iroha-fanout-routes-denied" to denied.toString(),
        "x-iroha-fanout-routes-not-found" to missing.toString())
    private fun failure(block: () -> Unit): NexusToriiException = try { block(); error("Expected rejection") } catch (error: NexusToriiException) { error }
    private fun envelope(change: (MutableMap<String, JsonElement>) -> Unit = {}): JsonObject {
        val structured = mutableMapOf<String, JsonElement>("status" to JsonPrimitive(200), "content_type" to JsonPrimitive("application/json"),
            "headers" to JsonObject(headers().mapValues { JsonPrimitive(it.value) }), "body" to JsonObject(mapOf("fixture" to JsonPrimitive(true))))
        change(structured)
        return JsonObject(mapOf("isError" to JsonPrimitive(false), "structuredContent" to JsonObject(structured)))
    }
    @Test fun completeFanoutRetainsTheStructuredBodyAndHeaderProvenance() {
        val value = envelope()
        assertEquals(value["structuredContent"], NexusMcpResultContract.validateEmbeddedRoute(value, requireFanout = true))
        NexusToriiResponseContract.validateFanout(headers()::get, requireFanout = true)
        NexusToriiResponseContract.validateFanout({ null })
    }
    @Test fun laneAndDataspaceMustBeCanonicalAndBelongToOneCompleteLocalRoute() {
        val local = headers() + mapOf("x-iroha-routed-by" to "local", "x-iroha-route-lane-id" to "4294967295", "x-iroha-route-dataspace-id" to "18446744073709551615")
        NexusToriiResponseContract.validateFanout(local::get, requireFanout = true)
        val invalid = listOf(local + ("x-iroha-route-lane-id" to "4294967296"), local + ("x-iroha-route-dataspace-id" to "18446744073709551616"), local + ("x-iroha-route-lane-id" to "01"), local + ("x-iroha-routed-by" to "proxy"), local - "x-iroha-route-lane-id", local + ("x-iroha-fanout-first-failure" to "not_found"))
        for (value in invalid) assertEquals("NEXUS_FANOUT_HEADERS_INVALID", failure { NexusToriiResponseContract.validateFanout(value::get) }.safeCode)
    }
    @Test fun incompleteFanoutReportsTheSpecificUnavailableDeniedOrMissingRoute() {
        for ((value, reason) in listOf(headers(1, denied = 1) to NexusFanoutFailureReason.PERMISSION_DENIED,
            headers(1, missing = 1) to NexusFanoutFailureReason.NOT_FOUND, headers(1, unavailable = 1) to NexusFanoutFailureReason.ROUTE_UNAVAILABLE,
            headers(1) to NexusFanoutFailureReason.INCOMPLETE)) {
            val error = failure { NexusToriiResponseContract.validateFanout(value::get, requireFanout = true) }
            assertEquals(NexusToriiFailureCategory.DEPLOYMENT_HEALTH, error.category)
            assertEquals(reason, error.fanoutDiagnostic!!.reason)
            assertEquals(1, error.fanoutDiagnostic!!.failedRoutes)
        }
    }
    @Test fun inconsistentOrPartialCountersCannotQualifyAResponse() {
        val invalid = listOf(headers() - "x-iroha-fanout-routes-denied", headers() - "x-iroha-routed-by",
            headers() + ("x-iroha-fanout-routes-attempted" to "0"), headers() + ("x-iroha-fanout-routes-attempted" to "1025"),
            headers() + ("x-iroha-fanout-routes-attempted" to "+1"), headers() + ("x-iroha-fanout-routes-failed" to "1"),
            headers() + ("x-iroha-fanout-routes-denied" to "1"), headers() + ("x-iroha-fanout-first-failure" to "not_found"))
        for (value in invalid) assertEquals("NEXUS_FANOUT_HEADERS_INVALID", failure { NexusToriiResponseContract.validateFanout(value::get) }.safeCode)
    }
    @Test fun malformedMcpEnvelopesAndAmbiguousHeadersAreRejectedBeforeParsingBody() {
        val invalid = listOf<JsonElement>(JsonPrimitive("body"), JsonObject(emptyMap()), JsonObject(envelope() + ("body" to JsonObject(emptyMap()))),
            envelope { it.remove("headers") }, envelope { it["headers"] = JsonObject(mapOf("X-A" to JsonPrimitive("a"), "x-a" to JsonPrimitive("b"))) },
            envelope { it["headers"] = JsonObject(mapOf("" to JsonPrimitive("a"))) }, envelope { it["headers"] = JsonObject(mapOf("x-a" to JsonPrimitive(1))) },
            envelope { it["status"] = JsonPrimitive("200") }, envelope { it["body"] = JsonArray(emptyList()) },
            envelope { it["content_type"] = JsonPrimitive("text/html") }, envelope { it.remove("content_type") },
            envelope { it["items"] = JsonArray(emptyList()) }, JsonObject(envelope() + ("isError" to JsonPrimitive(true))))
        for (value in invalid) assertEquals("NEXUS_MCP_RESULT_INVALID", failure { NexusMcpResultContract.validateEmbeddedRoute(value) }.safeCode)
    }
    @Test fun mcpHttpFailureKeepsHttpStatusAndMapsDeploymentHealthOnlyWithEvidence() {
        val missing = failure { NexusMcpResultContract.validateEmbeddedRoute(envelope { it["status"] = JsonPrimitive(404) }) }
        assertEquals("NEXUS_MCP_HTTP_404", missing.safeCode); assertEquals(404, missing.httpStatus); assertEquals(NexusToriiFailureCategory.HTTP, missing.category)
        val unavailable = failure { NexusMcpResultContract.validateEmbeddedRoute(envelope { it["status"] = JsonPrimitive(503) }) }
        assertEquals("NEXUS_PUBLIC_INGRESS_UNAVAILABLE", unavailable.safeCode)
        val forbidden = failure { NexusMcpResultContract.validateEmbeddedRoute(envelope {
            it["status"] = JsonPrimitive(403); it["body"] = JsonObject(mapOf("code" to JsonPrimitive("permission_denied"), "message" to JsonPrimitive("permission_denied")))
        }) }
        assertEquals(NexusFanoutFailureReason.PERMISSION_DENIED, forbidden.fanoutDiagnostic!!.reason)
    }
    @Test fun firstFailureEvidenceWorksWithoutCountersButNeverContradictsCounters() {
        val first = mapOf("x-iroha-fanout-first-failure" to "permission_denied")
        assertEquals(NexusFanoutFailureReason.PERMISSION_DENIED, failure { NexusToriiResponseContract.validateFanout(first::get) }.fanoutDiagnostic!!.reason)
        val invalid = headers(1, denied = 1) + ("x-iroha-fanout-first-failure" to "route_unavailable")
        assertEquals("NEXUS_FANOUT_HEADERS_INVALID", failure { NexusToriiResponseContract.validateFanout(invalid::get) }.safeCode)
    }
}
