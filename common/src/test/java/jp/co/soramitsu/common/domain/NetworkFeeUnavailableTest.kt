package jp.co.soramitsu.common.domain

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import jp.co.soramitsu.common.network.BoundedHttpTextException
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkFeeUnavailableTest {

    @Test
    fun `transport failures anywhere in the cause chain name the network`() {
        listOf(
            BoundedHttpTextException("BOUNDED_HTTP_IO"),
            BoundedHttpTextException("BOUNDED_HTTP_STATUS_503"),
            UnknownHostException("ws.mof.sora.org"),
            IllegalStateException("SORA2_RUNTIME_REFRESH_FAILED", SocketTimeoutException()),
            RuntimeException(IllegalStateException(BoundedHttpTextException("BOUNDED_HTTP_DEADLINE"))),
        ).forEach { error ->
            assertEquals(
                error.toString(),
                NetworkFeeUnavailable.NETWORK_UNREACHABLE,
                NetworkFeeUnavailable.from(error),
            )
        }
    }

    @Test
    fun `other failures stay generic`() {
        listOf(
            IllegalStateException("SORA2_GENESIS_MISMATCH"),
            SoraException.businessError(ResponseCode.FEE_RATE_NOT_AVAILABLE),
            IllegalArgumentException("bad amount"),
        ).forEach { error ->
            assertEquals(NetworkFeeUnavailable.OTHER, NetworkFeeUnavailable.from(error))
        }
    }

    @Test
    fun `a self-referencing cause chain terminates`() {
        val error = object : RuntimeException("loop") {
            override val cause: Throwable get() = this
        }
        assertEquals(NetworkFeeUnavailable.OTHER, NetworkFeeUnavailable.from(error))
    }
}
