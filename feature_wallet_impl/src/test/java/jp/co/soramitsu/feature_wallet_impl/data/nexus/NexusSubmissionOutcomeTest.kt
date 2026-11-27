package jp.co.soramitsu.feature_wallet_impl.data.nexus

import jp.co.soramitsu.common.nexus.NexusToriiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NexusSubmissionOutcomeTest {
    @Test fun `only an explicitly unsent submit failure permits a fresh review`() = runTest {
        val failed = NexusToriiException("NEXUS_NETWORK_IO", submissionMayHaveReachedTorii = false)
        val error = runCatching { submitAndTrackNexus({ throw failed }, { fail("No submitted hash"); it }) }.exceptionOrNull()
        assertTrue(error is NexusNotSubmittedException)
        assertSame(failed, error?.cause)
    }
    @Test fun `a transport failure that may have reached Torii stays unknown`() = runTest {
        val unknown = NexusToriiException("NEXUS_NETWORK_IO", submissionMayHaveReachedTorii = true)
        assertSame(unknown, runCatching { submitAndTrackNexus({ throw unknown }, { it }) }.exceptionOrNull())
    }
    @Test fun `a status read that fails after Torii took the transfer leaves it pending, not unsent or unknown`() = runTest {
        val readFailure = NexusToriiException("NEXUS_STATUS_IO", submissionMayHaveReachedTorii = false)
        val submitted = NexusSendResult("id", "hash", "SUBMITTED")
        assertSame(submitted, submitAndTrackNexus({ submitted }, { throw readFailure }))
        assertSame(submitted, submitAndTrackNexus({ submitted }, { throw IllegalStateException("TAIRA_HTTP_409") }))
    }
    @Test fun `tracking reports how the transfer ended`() = runTest {
        val submitted = NexusSendResult("id", "hash", "SUBMITTED")
        val finalized = NexusSendResult("id", "hash", "FINALIZED")
        assertSame(finalized, submitAndTrackNexus({ submitted }, { finalized }))
    }
    @Test fun `closing the outcome cancels tracking rather than reporting a result`() = runTest {
        val closed = kotlinx.coroutines.CancellationException("closed")
        val submitted = NexusSendResult("id", "hash", "SUBMITTED")
        assertSame(closed, runCatching { submitAndTrackNexus({ submitted }, { throw closed }) }.exceptionOrNull())
    }
}
