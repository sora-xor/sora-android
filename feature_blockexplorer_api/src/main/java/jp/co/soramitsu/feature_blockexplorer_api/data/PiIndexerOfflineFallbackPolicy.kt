package jp.co.soramitsu.feature_blockexplorer_api.data

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import jp.co.soramitsu.common.network.BoundedHttpTextException
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.RestClientException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * The single allowlist for PI offline fallback.
 *
 * Callers with an additional display-only cache must use this policy as well,
 * otherwise a schema, identity, checkpoint, decoding, cancellation, or
 * programming error could be silently hidden by older data.
 */
object PiIndexerOfflineFallbackPolicy {
    fun allows(error: Throwable): Boolean {
        var current: Throwable? = error
        val seen = mutableSetOf<Throwable>()
        var hasTransportCause = false
        var hasEligibleHttpStatus = false
        var hasEligibleBoundedTransport = false
        while (current != null && seen.add(current)) {
            val isRestClientError = current is RestClientException
            if (
                current is CancellationException ||
                current is SerializationException ||
                current is RestClientException.WhileSerialization ||
                current is ProtocolException ||
                current is IllegalArgumentException ||
                current is IllegalStateException ||
                (current is RuntimeException && !isRestClientError)
            ) {
                return false
            }
            if (current is BoundedHttpTextException) {
                if (!boundedTransportAllowsFallback(current.safeCode)) return false
                hasEligibleBoundedTransport = true
            }
            if (current is RestClientException.WithCode) {
                val eligible = current.code == 408 ||
                    current.code == 429 ||
                    current.code in 500..599
                if (!eligible) return false
                hasEligibleHttpStatus = true
            }
            hasTransportCause = hasTransportCause ||
                current is SocketTimeoutException ||
                current is UnknownHostException ||
                current is ConnectException ||
                current is NoRouteToHostException ||
                current is SocketException
            current = current.cause
        }
        if (hasEligibleBoundedTransport || hasEligibleHttpStatus) return true
        return hasTransportCause &&
            (error is IOException || error is RestClientException)
    }

    private fun boundedTransportAllowsFallback(safeCode: String): Boolean {
        if (
            safeCode == "BOUNDED_HTTP_IO" ||
            safeCode == "BOUNDED_HTTP_DEADLINE"
        ) {
            return true
        }
        val status = safeCode
            .removePrefix(BOUNDED_HTTP_STATUS_PREFIX)
            .takeIf { safeCode.startsWith(BOUNDED_HTTP_STATUS_PREFIX) }
            ?.toIntOrNull()
            ?: return false
        return status == 408 || status == 429 || status in 500..599
    }

    private const val BOUNDED_HTTP_STATUS_PREFIX = "BOUNDED_HTTP_STATUS_"
}
