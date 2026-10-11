package jp.co.soramitsu.common.presentation.viewmodel

import java.io.IOException
import java.net.SocketTimeoutException
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
