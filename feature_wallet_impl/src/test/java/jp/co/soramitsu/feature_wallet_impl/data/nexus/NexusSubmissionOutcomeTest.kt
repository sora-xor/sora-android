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
    @Test fun `transport and later status-read failures cannot be classified as unsent`() = runTest {
        val unknown = NexusToriiException("NEXUS_NETWORK_IO", submissionMayHaveReachedTorii = true)
        assertSame(unknown, runCatching { submitAndTrackNexus({ throw unknown }, { it }) }.exceptionOrNull())
        val readFailure = NexusToriiException("NEXUS_STATUS_IO", submissionMayHaveReachedTorii = false)
        val result = NexusSendResult("id", "hash", "SUBMITTED")
        assertSame(readFailure, runCatching { submitAndTrackNexus({ result }, { throw readFailure }) }.exceptionOrNull())
    }
}
