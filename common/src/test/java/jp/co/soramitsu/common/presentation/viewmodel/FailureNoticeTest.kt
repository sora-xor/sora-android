package jp.co.soramitsu.common.presentation.viewmodel

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import jp.co.soramitsu.common.network.BoundedHttpTextException
import org.junit.Assert.assertEquals
import org.junit.Test

class FailureNoticeTest {

    @Test
    fun `the first cause that says something decides`() {
        assertEquals(
            FailureNotice.ACCOUNT_READ_ONLY,
            FailureNotice.of(
                RuntimeException(
                    "outer",
                    IllegalStateException(FailureNotice.SIGNING_WALLET_UNAVAILABLE, IOException("io")),
                )
            ),
        )
        assertEquals(
            FailureNotice.NETWORK,
            FailureNotice.of(RuntimeException("outer", SocketTimeoutException("slow"))),
        )
    }

    @Test
    fun `a submission whose outcome is unknown is never called a network problem`() {
        // ExtrinsicSubmissionUnknownException: the answer did not arrive, and its cause is the
        // network error that hid it. The transaction may already be on chain.
        val unknown = IllegalStateException(
            FailureNotice.EXTRINSIC_SUBMISSION_UNKNOWN,
            BoundedHttpTextException("BOUNDED_HTTP_DEADLINE"),
        )

        assertEquals(FailureNotice.SUBMISSION_UNKNOWN, FailureNotice.of(unknown))
        assertEquals(
            FailureNotice.SUBMISSION_UNKNOWN,
            FailureNotice.of(RuntimeException("wrapped by a screen", unknown)),
        )
    }

    @Test
    fun `only a failed network request says to check the connection`() {
        listOf(
            BoundedHttpTextException("BOUNDED_HTTP_IO"),
            UnknownHostException("rpc.example"),
            ConnectException("refused"),
            SocketTimeoutException("slow"),
            SSLHandshakeException("handshake"),
        ).forEach { error ->
            assertEquals(error.toString(), FailureNotice.NETWORK, FailureNotice.of(error))
            assertEquals(
                error.toString(),
                FailureNotice.NETWORK,
                FailureNotice.of(RuntimeException("wrapped", error)),
            )
        }
    }

    @Test
    fun `a storage failure is not a network problem`() {
        // DataStore reports a full disk or an unreadable file as a plain IOException.
        assertEquals(FailureNotice.GENERIC, FailureNotice.of(IOException("No space left on device")))
        assertEquals(
            FailureNotice.GENERIC,
            FailureNotice.of(RuntimeException("save failed", java.io.FileNotFoundException("prefs"))),
        )
    }

    @Test
    fun `nothing known is generic`() {
        assertEquals(FailureNotice.GENERIC, FailureNotice.of(IllegalArgumentException("bad")))
        assertEquals(FailureNotice.GENERIC, FailureNotice.of(RuntimeException()))
    }

    @Test
    fun `a cause chain that loops ends`() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)

        assertEquals(FailureNotice.GENERIC, FailureNotice.of(first))
    }
}
