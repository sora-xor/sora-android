package jp.co.soramitsu.common.network

import java.net.URI
import java.util.Locale

/** Endpoint admission shared by cached, remote and manually selected node connections. */
object SoraNodeEndpointPolicy {
    private val blockedHosts = setOf(
        "ws.mof.sora.org", "mof.sora.org", "mof3.sora.org", "207.254.29.218",
    )

    fun isAllowed(address: String): Boolean = runCatching {
        val uri = URI(address)
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        uri.scheme?.lowercase(Locale.ROOT) in setOf("ws", "wss") &&
            uri.rawUserInfo == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port in 1..65535) &&
            uri.rawAuthority?.endsWith(':') != true &&
            host.isNotEmpty() && host !in blockedHosts
    }.getOrDefault(false)
}
