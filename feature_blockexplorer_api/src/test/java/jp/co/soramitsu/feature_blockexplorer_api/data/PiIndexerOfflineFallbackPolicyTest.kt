package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import jp.co.soramitsu.common.network.BoundedHttpTextException
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.RestClientException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import org.junit.Test

class PiIndexerOfflineFallbackPolicyTest {

    @Test
    fun `offline and transport failures permit fallback`() {
        assertThat(
            PiIndexerOfflineFallbackPolicy.allows(
                UnknownHostException("offline")
            )
        ).isTrue()
        assertThat(
            PiIndexerOfflineFallbackPolicy.allows(
                SocketTimeoutException("timeout")
            )
        ).isTrue()
        listOf(
            "BOUNDED_HTTP_IO",
            "BOUNDED_HTTP_DEADLINE",
            "BOUNDED_HTTP_STATUS_408",
            "BOUNDED_HTTP_STATUS_429",
            "BOUNDED_HTTP_STATUS_500",
            "BOUNDED_HTTP_STATUS_599",
        ).forEach { safeCode ->
            assertThat(
                PiIndexerOfflineFallbackPolicy.allows(
                    BoundedHttpTextException(safeCode)
                )
            ).isTrue()
        }
    }

    @Test
    fun `schema decoding cancellation and programming failures stay visible`() {
        assertThat(
            PiIndexerOfflineFallbackPolicy.allows(
                SerializationException("schema changed")
            )
        ).isFalse()
        assertThat(
            PiIndexerOfflineFallbackPolicy.allows(
                CancellationException("cancelled")
            )
        ).isFalse()
        assertThat(
            PiIndexerOfflineFallbackPolicy.allows(
                IllegalStateException("PI_INDEXER_CHAIN_MISMATCH")
            )
        ).isFalse()
        assertThat(
            PiIndexerOfflineFallbackPolicy.allows(
                IllegalArgumentException("programming error")
            )
        ).isFalse()
        assertThat(
            PiIndexerOfflineFallbackPolicy.allows(
                IOException("unclassified IO")
            )
        ).isFalse()
        assertThat(
            PiIndexerOfflineFallbackPolicy.allows(
                ProtocolException("invalid protocol")
            )
        ).isFalse()
        listOf(
            "BOUNDED_HTTP_RESPONSE_TOO_LARGE",
            "BOUNDED_HTTP_UTF8_INVALID",
            "BOUNDED_HTTP_JSON_INVALID",
            "BOUNDED_HTTP_JSON_COMPLEXITY",
            "BOUNDED_HTTP_CONTENT_TYPE_INVALID",
            "BOUNDED_HTTP_REDIRECT_REJECTED",
            "BOUNDED_HTTP_RUNTIME",
            "BOUNDED_HTTP_STATUS_400",
            "BOUNDED_HTTP_STATUS_499",
            "BOUNDED_HTTP_STATUS_NOT_A_NUMBER",
        ).forEach { safeCode ->
            assertThat(
                PiIndexerOfflineFallbackPolicy.allows(
                    BoundedHttpTextException(safeCode)
                )
            ).isFalse()
        }
    }

    @Test
    fun `serialization cause prevents an outer transport wrapper from falling back`() {
        val wrapped = IOException(
            "transport wrapper",
            SerializationException("invalid response"),
        )

        assertThat(PiIndexerOfflineFallbackPolicy.allows(wrapped)).isFalse()
    }

    @Test
    fun `authoritative bounded rejection wins over a nested transient error`() {
        val rejection = BoundedHttpTextException("BOUNDED_HTTP_UTF8_INVALID").apply {
            initCause(SocketTimeoutException("nested transient failure"))
        }

        assertThat(PiIndexerOfflineFallbackPolicy.allows(rejection)).isFalse()
    }

    @Test
    fun `protocol and programming causes cannot borrow nested transport eligibility`() {
        val protocol = ProtocolException("invalid response framing").apply {
            initCause(SocketTimeoutException("nested transient failure"))
        }
        val programming = IOException(
            "outer IO wrapper",
            IllegalStateException(
                "invalid decoded state",
                SocketTimeoutException("nested transient failure"),
            ),
        )
        val unchecked = IOException(
            "outer IO wrapper",
            RuntimeException(
                "unexpected implementation defect",
                SocketTimeoutException("nested transient failure"),
            ),
        )
        val restSerialization = RestClientException.WhileSerialization(
            "response decoding failed",
            SocketTimeoutException("nested transient failure"),
        )

        assertThat(PiIndexerOfflineFallbackPolicy.allows(protocol)).isFalse()
        assertThat(PiIndexerOfflineFallbackPolicy.allows(programming)).isFalse()
        assertThat(PiIndexerOfflineFallbackPolicy.allows(unchecked)).isFalse()
        assertThat(PiIndexerOfflineFallbackPolicy.allows(restSerialization)).isFalse()
    }
}
