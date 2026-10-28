package jp.co.soramitsu.common.irohaconnect

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import jp.co.soramitsu.common.nexus.NexusNetwork
import jp.co.soramitsu.common.nexus.NexusNetworks

data class IrohaConnectLaunch(
    val sid: ByteArray,
    val sidBase64Url: String,
    val network: NexusNetwork,
    val node: URI,
    val token: String,
    val relayToken: String,
    val networkIdLiteral: String,
    val networkIdBytes: ByteArray,
    val appPublicKey: ByteArray,
    val nonce: ByteArray,
) {
    val webSocketUri: URI
        get() = URI(
            if (node.scheme == "https") "wss" else "ws",
            null,
            node.host,
            node.port,
            "/v1/connect/ws",
            "sid=${IrohaConnectEncoding.percentEncode(sidBase64Url)}&role=wallet",
            null,
        )

    val tokenProtocol: String
        get() = "iroha-connect.token.v1.${IrohaConnectEncoding.base64Url(token.toByteArray())}"
}

enum class IrohaConnectUriError {
    EMPTY,
    INVALID_URI,
    INVALID_SCHEME,
    INVALID_SHAPE,
    DUPLICATE_PARAMETER,
    UNSUPPORTED_PARAMETER,
    MISSING_PARAMETER,
    INVALID_ROLE,
    INVALID_VERSION,
    INVALID_SESSION_ID,
    INVALID_TOKEN,
    INVALID_NETWORK_ID,
    SESSION_ID_MISMATCH,
    UNSUPPORTED_NODE,
    NETWORK_MISMATCH,
}

class IrohaConnectUriException(
    val code: IrohaConnectUriError,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

object IrohaConnectUri {
    private val schemes = setOf("iroha", "irohaconnect")
    private val fields = setOf(
        "sid",
        "network_id",
        "app_pk",
        "nonce",
        "node",
        "v",
        "role",
        "token",
        "relay",
    )
    private val tokenPattern = Regex("^[A-Za-z0-9_-]{43}$")
    private const val TAIRA_NETWORK_ID =
        "hash:82531CE8EAE8BFF6BEECA4698BFD13A3BC8BEC5F0EE0D23D428C97FC17AB0F3B#3E94"

    fun parse(value: String): IrohaConnectLaunch {
        val raw = value.trim()
        if (raw.isEmpty()) fail(IrohaConnectUriError.EMPTY, "IrohaConnect link is empty")
        if (raw != value) {
            fail(IrohaConnectUriError.INVALID_URI, "IrohaConnect link contains surrounding whitespace")
        }
        val uri = try {
            URI(raw)
        } catch (error: Exception) {
            throw IrohaConnectUriException(
                IrohaConnectUriError.INVALID_URI,
                "IrohaConnect link is invalid",
                error,
            )
        }
        if (uri.scheme?.lowercase() !in schemes) {
            fail(IrohaConnectUriError.INVALID_SCHEME, "Unsupported IrohaConnect link scheme")
        }
        if (
            uri.host != "connect" ||
            !uri.path.isNullOrEmpty() ||
            uri.fragment != null ||
            uri.userInfo != null ||
            uri.port != -1
        ) {
            fail(IrohaConnectUriError.INVALID_SHAPE, "IrohaConnect link has an invalid target")
        }

        val parameters = parseQuery(uri.rawQuery)
        parameters.keys.firstOrNull { it !in fields }?.let {
            fail(IrohaConnectUriError.UNSUPPORTED_PARAMETER, "Unsupported IrohaConnect parameter: $it")
        }
        fields.filterNot(parameters::containsKey).firstOrNull()?.let {
            fail(IrohaConnectUriError.MISSING_PARAMETER, "IrohaConnect link is missing $it")
        }
        val sidText = required(parameters, "sid")
        val sid = try {
            IrohaConnectEncoding.base64UrlDecode(sidText, 32)
        } catch (error: Exception) {
            throw IrohaConnectUriException(
                IrohaConnectUriError.INVALID_SESSION_ID,
                "IrohaConnect session id must be canonical 32-byte base64url",
                error,
            )
        }
        if (required(parameters, "role") != "wallet") {
            fail(IrohaConnectUriError.INVALID_ROLE, "Open the wallet-role IrohaConnect link")
        }
        if (required(parameters, "v") != "1") {
            fail(IrohaConnectUriError.INVALID_VERSION, "IrohaConnect link version must be 1")
        }
        val token = required(parameters, "token")
        val relayToken = required(parameters, "relay")
        if (!tokenPattern.matches(token) || !tokenPattern.matches(relayToken)) {
            fail(IrohaConnectUriError.INVALID_TOKEN, "IrohaConnect tokens must be canonical 32-byte base64url")
        }
        try {
            IrohaConnectEncoding.base64UrlDecode(token, 32)
            IrohaConnectEncoding.base64UrlDecode(relayToken, 32)
        } catch (error: Exception) {
            throw IrohaConnectUriException(
                IrohaConnectUriError.INVALID_TOKEN,
                "IrohaConnect tokens must be canonical 32-byte base64url",
                error,
            )
        }
        val networkIdLiteral = required(parameters, "network_id")
        val networkIdBytes = try {
            IrohaNetworkId.parse(networkIdLiteral)
        } catch (error: Exception) {
            throw IrohaConnectUriException(
                IrohaConnectUriError.INVALID_NETWORK_ID,
                "IrohaConnect NetworkId is invalid",
                error,
            )
        }
        val appPublicKey = decodeFixed(parameters, "app_pk", 32)
        val nonce = decodeFixed(parameters, "nonce", 16)
        if (appPublicKey.all { it == 0.toByte() } || nonce.all { it == 0.toByte() }) {
            fail(IrohaConnectUriError.INVALID_URI, "IrohaConnect app key and nonce must not be all zero")
        }
        val network = resolveNetwork(networkIdLiteral, parameters.getValue("node"))
        val node = URI(network.toriiBaseUrl)
        val expectedSid = IrohaConnectCrypto.blake2b256(
            "iroha-connect|sid|".toByteArray() + networkIdBytes + appPublicKey + nonce,
        )
        if (!IrohaConnectCrypto.constantTimeEquals(sid, expectedSid)) {
            fail(
                IrohaConnectUriError.SESSION_ID_MISMATCH,
                "IrohaConnect session does not bind its network, app key, and nonce",
            )
        }
        return IrohaConnectLaunch(
            sid = sid,
            sidBase64Url = sidText,
            network = network,
            node = node,
            token = token,
            relayToken = relayToken,
            networkIdLiteral = networkIdLiteral,
            networkIdBytes = networkIdBytes,
            appPublicKey = appPublicKey,
            nonce = nonce,
        )
    }

    private fun resolveNetwork(networkIdLiteral: String, rawNode: String): NexusNetwork {
        val network = when (networkIdLiteral) {
            TAIRA_NETWORK_ID -> NexusNetworks.taira
            else -> fail(
                IrohaConnectUriError.NETWORK_MISMATCH,
                "This wallet does not have a verified account for that Iroha network",
            )
        }
        if (rawNode.isEmpty()) return network
        val candidate = try {
            URI(rawNode)
        } catch (error: Exception) {
            throw IrohaConnectUriException(
                IrohaConnectUriError.UNSUPPORTED_NODE,
                "IrohaConnect node is invalid",
                error,
            )
        }
        if (
            candidate.scheme != "https" ||
            candidate.userInfo != null ||
            candidate.query != null ||
            candidate.fragment != null ||
            candidate.port != -1 ||
            (!candidate.path.isNullOrEmpty() && candidate.path != "/")
        ) {
            fail(IrohaConnectUriError.UNSUPPORTED_NODE, "IrohaConnect requires an admitted HTTPS SORA node")
        }
        val admitted = URI(network.toriiBaseUrl)
        if (
            !candidate.host.equals(admitted.host, ignoreCase = true) ||
            candidate.scheme != admitted.scheme
        ) {
            fail(IrohaConnectUriError.UNSUPPORTED_NODE, "IrohaConnect node does not match its NetworkId")
        }
        return network
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) {
            fail(IrohaConnectUriError.MISSING_PARAMETER, "IrohaConnect link has no session parameters")
        }
        val result = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0 || '+' in pair) {
                fail(IrohaConnectUriError.INVALID_URI, "IrohaConnect query encoding is invalid")
            }
            val key = decodeQueryPart(pair.substring(0, separator))
            val value = decodeQueryPart(pair.substring(separator + 1))
            if (result.put(key, value) != null) {
                fail(IrohaConnectUriError.DUPLICATE_PARAMETER, "Duplicate IrohaConnect parameter: $key")
            }
        }
        return result
    }

    private fun decodeQueryPart(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (error: Exception) {
        throw IrohaConnectUriException(
            IrohaConnectUriError.INVALID_URI,
            "IrohaConnect query encoding is invalid",
            error,
        )
    }

    private fun required(parameters: Map<String, String>, name: String): String =
        parameters[name]?.takeIf(String::isNotBlank)
            ?: fail(IrohaConnectUriError.MISSING_PARAMETER, "IrohaConnect link is missing $name")

    private fun decodeFixed(parameters: Map<String, String>, name: String, size: Int): ByteArray = try {
        IrohaConnectEncoding.base64UrlDecode(required(parameters, name), size)
    } catch (error: Exception) {
        throw IrohaConnectUriException(
            IrohaConnectUriError.INVALID_URI,
            "IrohaConnect $name must be canonical $size-byte base64url",
            error,
        )
    }

    private fun fail(code: IrohaConnectUriError, message: String): Nothing =
        throw IrohaConnectUriException(code, message)
}

object IrohaNetworkId {
    private val pattern = Regex("^hash:([0-9A-F]{64})#([0-9A-F]{4})$")

    fun parse(literal: String): ByteArray {
        val match = pattern.matchEntire(literal) ?: throw IllegalArgumentException("INVALID_NETWORK_ID")
        val body = match.groupValues[1]
        require(crc16CcittFalse("hash:$body".toByteArray()).toString(16).uppercase().padStart(4, '0') == match.groupValues[2]) {
            "INVALID_NETWORK_ID_CHECKSUM"
        }
        val bytes = IrohaConnectEncoding.hexDecode(body)
        require(bytes.size == 32 && bytes.last().toInt() and 1 == 1) { "INVALID_NETWORK_ID_MARKER" }
        return bytes
    }

    private fun crc16CcittFalse(bytes: ByteArray): Int {
        var crc = 0xffff
        bytes.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xff) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    ((crc shl 1) xor 0x1021) and 0xffff
                } else {
                    (crc shl 1) and 0xffff
                }
            }
        }
        return crc
    }
}
