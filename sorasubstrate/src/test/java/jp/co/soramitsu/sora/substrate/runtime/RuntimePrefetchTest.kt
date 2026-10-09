package jp.co.soramitsu.sora.substrate.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RuntimePrefetchTest {
    @Test fun `unavailable PI prefetch is reported without aborting local startup`() = runTest {
        val unavailable = IllegalStateException("PI_INDEXER_GRAPHQL_ERROR")
        var recorded: Exception? = null
        prefetchRuntimeForDisplay({ recorded = it }) { throw unavailable }
        assertSame(unavailable, recorded)
        // Containment belongs to optional warming only; direct loading still fails closed.
        val strictLoader: suspend () -> Unit = { throw unavailable }
        try { strictLoader(); fail("Strict loader must continue to reject unavailable runtime") }
        catch (error: IllegalStateException) { assertSame(unavailable, error) }
    }
    @Test fun `cancelled prefetch propagates cancellation`() = runTest {
        val cancelled = CancellationException("disposed")
        try {
            prefetchRuntimeForDisplay({ fail("Cancellation is not an availability error") }) { throw cancelled }
            fail("Cancellation must propagate")
        } catch (error: CancellationException) { assertSame(cancelled, error) }
    }
}
