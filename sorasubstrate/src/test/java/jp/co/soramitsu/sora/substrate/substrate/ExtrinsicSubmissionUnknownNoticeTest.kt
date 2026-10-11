package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.common.network.BoundedHttpTextException
import jp.co.soramitsu.common.presentation.viewmodel.FailureNotice
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Swap, liquidity and referral hand [ExtrinsicSubmissionUnknownException] to
 * `BaseViewModel.onError` as it is. Its cause is the network error that hid the node's answer
 * (`BoundedHttpTextException` is an `IOException`), and the transaction may already be on chain:
 * the person must be told to check Activity, never to check the connection and try again.
 */
class ExtrinsicSubmissionUnknownNoticeTest {

    @Test
    fun `the real exception is classified by its own code before its network cause`() {
        val error = ExtrinsicSubmissionUnknownException(
            transactionHash = "0x" + "ab".repeat(32),
            cause = BoundedHttpTextException("BOUNDED_HTTP_DEADLINE"),
        )

        assertEquals(FailureNotice.EXTRINSIC_SUBMISSION_UNKNOWN, error.message)
        assertEquals(FailureNotice.SUBMISSION_UNKNOWN, FailureNotice.of(error))
        assertEquals(
            FailureNotice.SUBMISSION_UNKNOWN,
            FailureNotice.of(RuntimeException("wrapped", error)),
        )
    }
}
