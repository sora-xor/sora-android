package jp.co.soramitsu.sora.irohaconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.irohaconnect.*
import jp.co.soramitsu.common.nexus.NexusNetworks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLEncoder

/** Launch admission on the Android runtime, without constructing a relay or account session. */
@RunWith(AndroidJUnit4::class)
class IrohaConnectLinkAdmissionTest {
    private val network = "hash:82531CE8EAE8BFF6BEECA4698BFD13A3BC8BEC5F0EE0D23D428C97FC17AB0F3B#3E94"
    private val appKey = ByteArray(32) { (it + 1).toByte() }
    private val nonce = ByteArray(16) { (it + 2).toByte() }
    private val sid = IrohaConnectCrypto.blake2b256("iroha-connect|sid|".toByteArray() + IrohaNetworkId.parse(network) + appKey + nonce)
    private fun parameters() = linkedMapOf("sid" to IrohaConnectEncoding.base64Url(sid), "network_id" to network,
        "app_pk" to IrohaConnectEncoding.base64Url(appKey), "nonce" to IrohaConnectEncoding.base64Url(nonce),
        "node" to NexusNetworks.taira.toriiBaseUrl, "v" to "1", "role" to "wallet",
        "token" to IrohaConnectEncoding.base64Url(ByteArray(32) { 3 }), "relay" to IrohaConnectEncoding.base64Url(ByteArray(32) { 4 }))
    private fun uri(values: Map<String, String> = parameters()) = "irohaconnect://connect?" + values.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8").replace("+", "%20")}" }
    private fun rejects(raw: String, code: IrohaConnectUriError) {
        val error = runCatching { IrohaConnectUri.parse(raw) }.exceptionOrNull()
        assertTrue("Expected $code for $raw, got $error", error is IrohaConnectUriException)
        assertEquals(code, (error as IrohaConnectUriException).code)
    }
    @Test fun canonicalLaunchBindsNetworkAppKeyNonceAndAdmittedRelayEndpoint() {
        for (raw in listOf(uri(), uri().replace("irohaconnect:", "iroha:"), uri(parameters().apply { put("node", "") }))) {
            val launch = IrohaConnectUri.parse(raw)
            assertArrayEquals(sid, launch.sid); assertArrayEquals(appKey, launch.appPublicKey); assertArrayEquals(nonce, launch.nonce)
            assertEquals(NexusNetworks.taira, launch.network); assertEquals("wss", launch.webSocketUri.scheme)
            assertEquals("/v1/connect/ws", launch.webSocketUri.path)
            assertTrue(launch.webSocketUri.query.endsWith("&role=wallet")); assertTrue(launch.tokenProtocol.startsWith("iroha-connect.token.v1."))
        }
    }
    @Test fun invalidUriShapesAndQueryAmbiguityAreRejectedBeforeSessionConstruction() {
        val cases = listOf("" to IrohaConnectUriError.EMPTY, " " + uri() to IrohaConnectUriError.INVALID_URI,
            "iroha://connect?bad=%GG" to IrohaConnectUriError.INVALID_URI,
            uri().replace("irohaconnect:", "https:") to IrohaConnectUriError.INVALID_SCHEME,
            uri().replace("connect?", "connect/?") to IrohaConnectUriError.INVALID_SHAPE,
            "iroha://connect" to IrohaConnectUriError.MISSING_PARAMETER,
            uri() + "&sid=duplicate" to IrohaConnectUriError.DUPLICATE_PARAMETER,
            uri() + "&unexpected=1" to IrohaConnectUriError.UNSUPPORTED_PARAMETER,
            "iroha://connect?=value" to IrohaConnectUriError.INVALID_URI,
            uri() + "&extra=a+b" to IrohaConnectUriError.INVALID_URI)
        cases.forEach { (raw, code) -> rejects(raw, code) }
    }
    @Test fun absentEmptyAndIncompatibleProtocolFieldsHaveDistinctFailures() {
        rejects(uri(parameters().apply { remove("role") }), IrohaConnectUriError.MISSING_PARAMETER)
        rejects(uri(parameters().apply { put("role", "") }), IrohaConnectUriError.MISSING_PARAMETER)
        rejects(uri(parameters().apply { put("role", "app") }), IrohaConnectUriError.INVALID_ROLE)
        rejects(uri(parameters().apply { put("v", "2") }), IrohaConnectUriError.INVALID_VERSION)
        rejects(uri(parameters().apply { put("sid", "bad") }), IrohaConnectUriError.INVALID_SESSION_ID)
        rejects(uri(parameters().apply { put("sid", IrohaConnectEncoding.base64Url(ByteArray(32) { 1 })) }), IrohaConnectUriError.SESSION_ID_MISMATCH)
    }
    @Test fun tokensRequireBothExactLengthAndCanonicalTrailingBits() {
        for (field in listOf("token", "relay")) {
            rejects(uri(parameters().apply { put(field, "short") }), IrohaConnectUriError.INVALID_TOKEN)
            rejects(uri(parameters().apply { put(field, "A".repeat(42) + "B") }), IrohaConnectUriError.INVALID_TOKEN)
        }
    }
    @Test fun malformedKeyNonceAndNetworkNeverReachTheRelay() {
        rejects(uri(parameters().apply { put("network_id", "invalid") }), IrohaConnectUriError.INVALID_NETWORK_ID)
        for (field in listOf("app_pk", "nonce")) {
            rejects(uri(parameters().apply { put(field, "bad") }), IrohaConnectUriError.INVALID_URI)
            rejects(uri(parameters().apply { put(field, IrohaConnectEncoding.base64Url(ByteArray(if (field == "nonce") 16 else 32))) }), IrohaConnectUriError.INVALID_URI)
        }
    }
    @Test fun onlyTheAdmittedHttpsNodeCanBeUsedByAnOtherwiseValidLaunch() {
        for (node in listOf("https://[", "http://example.test", "https://example.test", NexusNetworks.taira.toriiBaseUrl + "?redirect=1", "https://user@example.test")) {
            rejects(uri(parameters().apply { put("node", node) }), IrohaConnectUriError.UNSUPPORTED_NODE)
        }
    }
}
