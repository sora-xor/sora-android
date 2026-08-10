package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import jp.co.soramitsu.sora.substrate.substrate.DefinitelyNotSubmitted
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicSubmissionUnknownException
import jp.co.soramitsu.sora.substrate.substrate.PreparedExtrinsicPreTransportException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolkamarktPendingFailurePolicyTest {

    @Test
    fun `cancellation before pre transport validation completes is terminal and non ambiguous`() {
        val disposition = PolkamarktPendingFailurePolicy.classify(
            preTransportValidationCompleted = false,
            error = CancellationException("cancelled after durable SIGNED insert"),
        )

        assertEquals("REJECTED", disposition.state)
        assertFalse(disposition.submissionIsAmbiguous)
    }

    @Test
    fun `ordinary failure before pre transport validation completes is terminal and non ambiguous`() {
        val disposition = PolkamarktPendingFailurePolicy.classify(
            preTransportValidationCompleted = false,
            error = IllegalStateException("failed before RPC transport"),
        )

        assertEquals("REJECTED", disposition.state)
        assertFalse(disposition.submissionIsAmbiguous)
    }

    @Test
    fun `raw cancellation after pre transport validation remains ambiguous`() {
        val disposition = PolkamarktPendingFailurePolicy.classify(
            preTransportValidationCompleted = true,
            error = CancellationException("transport watch cancelled"),
        )

        assertEquals("UNKNOWN", disposition.state)
        assertTrue(disposition.submissionIsAmbiguous)
    }

    @Test
    fun `ordinary failure after pre transport validation remains ambiguous`() {
        val disposition = PolkamarktPendingFailurePolicy.classify(
            preTransportValidationCompleted = true,
            error = IllegalStateException("RPC transport failed"),
        )

        assertEquals("UNKNOWN", disposition.state)
        assertTrue(disposition.submissionIsAmbiguous)
    }

    @Test
    fun `core submission unknown marker overrides stale pre transport bookkeeping`() {
        val disposition = PolkamarktPendingFailurePolicy.classify(
            preTransportValidationCompleted = false,
            error = ExtrinsicSubmissionUnknownException(
                transactionHash = "0x${"11".repeat(32)}",
                cause = IllegalStateException("RPC handoff failed"),
            ),
        )

        assertEquals("UNKNOWN", disposition.state)
        assertTrue(disposition.submissionIsAmbiguous)
    }

    @Test
    fun `transport marker remains terminal after pre transport validation`() {
        val disposition = PolkamarktPendingFailurePolicy.classify(
            preTransportValidationCompleted = true,
            error = PreTransportFailure(),
        )

        assertEquals("REJECTED", disposition.state)
        assertFalse(disposition.submissionIsAmbiguous)
    }

    @Test
    fun `actual pre transport wrapper remains definitive while submission unknown is ambiguous`() {
        val definitive = PolkamarktPendingFailurePolicy.classify(
            preTransportValidationCompleted = true,
            error = PreparedExtrinsicPreTransportException(
                IllegalStateException("POLKAMARKT_ACCOUNT_CHANGED")
            ),
        )
        val ambiguous = PolkamarktPendingFailurePolicy.classify(
            preTransportValidationCompleted = true,
            error = ExtrinsicSubmissionUnknownException(
                transactionHash = "0x${"22".repeat(32)}",
                cause = IllegalStateException("RPC request may have been written"),
            ),
        )

        assertEquals("REJECTED", definitive.state)
        assertFalse(definitive.submissionIsAmbiguous)
        assertEquals("UNKNOWN", ambiguous.state)
        assertTrue(ambiguous.submissionIsAmbiguous)
    }

    private class PreTransportFailure :
        IllegalStateException("pre-transport validation failed"),
        DefinitelyNotSubmitted
}
