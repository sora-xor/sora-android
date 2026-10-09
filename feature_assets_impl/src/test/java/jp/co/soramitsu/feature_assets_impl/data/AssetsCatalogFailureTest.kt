package jp.co.soramitsu.feature_assets_impl.data

import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetsCatalogFailureTest {
    @Test fun `unavailable catalog is reported to consumers without crashing its host`() = runTest {
        val failure = IllegalStateException("PI_INDEXER_GRAPHQL_ERROR")
        val manager = mockk<CoroutineManager>()
        every { manager.applicationScope } returns this
        val calls = mockk<SubstrateCalls>()
        coEvery { calls.fetchAssetsList() } throws failure
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.recordException(any()) } just Runs
        try {
            val repository = AssetsRepositoryImpl(mockk(), mockk(), mockk(), calls, mockk(), manager, mockk())
            val error = runCatching { repository.tokensList() }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals(failure.message, error?.message)
            verify(exactly = 1) { FirebaseWrapper.recordException(failure) }
        } finally { unmockkObject(FirebaseWrapper) }
    }

    @Test fun `catalog cancellation reaches waiting consumers`() = runTest {
        val cancellation = CancellationException("cancel catalog")
        val manager = mockk<CoroutineManager>()
        every { manager.applicationScope } returns this
        val calls = mockk<SubstrateCalls>()
        coEvery { calls.fetchAssetsList() } throws cancellation
        val repository = AssetsRepositoryImpl(mockk(), mockk(), mockk(), calls, mockk(), manager, mockk())
        val error = runCatching { repository.tokensList() }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(cancellation.message, error?.message)
    }
}
