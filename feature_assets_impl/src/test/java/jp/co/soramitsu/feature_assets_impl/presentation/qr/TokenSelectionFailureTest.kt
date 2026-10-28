package jp.co.soramitsu.feature_assets_impl.presentation.qr

import androidx.lifecycle.ViewModelStore
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.IOException
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.SearchTokenFilter
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.SelectSearchTokenViewModel
import jp.co.soramitsu.test_data.TestAssets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TokenSelectionFailureTest {
    @get:Rule val main = MainCoroutineRule()

    private val interactor = mockk<AssetsInteractor>()
    private val store = ViewModelStore()
    private val asset = TestAssets.xorAsset(BigDecimal.TEN)

    @Before
    fun setup() {
        mockkObject(FirebaseWrapper)
        justRun { FirebaseWrapper.recordException(any()) }
    }

    @After
    fun cleanup() {
        store.clear()
        unmockkObject(FirebaseWrapper)
    }

    private fun viewModel() = SelectSearchTokenViewModel(interactor, NumbersFormatter()).also {
        store.put("token-selection", it)
    }

    @Test
    fun initialFailureLeavesNoSelectableAssetsAndRecordsError() = runTest {
        val failure = IOException("asset subscription failed")
        every { interactor.subscribeAssetsActiveOfCurAccount() } returns flow { throw failure }

        val viewModel = viewModel()
        runCurrent()

        assertTrue(viewModel.state.value.list.isEmpty())
        verify(exactly = 1) { FirebaseWrapper.recordException(failure) }
    }

    @Test
    fun failedAccountUpdatesClearRowsAndCannotBeRestoredByChangingFilter() = runTest {
        val fail = CompletableDeferred<Unit>()
        val failure = IOException("account asset update failed")
        every { interactor.subscribeAssetsActiveOfCurAccount() } returns flow {
            emit(listOf(asset))
            fail.await()
            throw failure
        }
        val viewModel = viewModel()
        runCurrent()
        assertEquals(listOf(asset.token.id), viewModel.state.value.list.map { it.tokenId })

        fail.complete(Unit)
        runCurrent()
        assertTrue(viewModel.state.value.list.isEmpty())
        viewModel.onFilterChange(SearchTokenFilter("XOR", emptyList()))
        assertTrue(viewModel.state.value.list.isEmpty())
        verify(exactly = 1) { FirebaseWrapper.recordException(failure) }
    }

    @Test
    fun closingScreenDoesNotReportSubscriptionCancellationAsFailure() = runTest {
        every { interactor.subscribeAssetsActiveOfCurAccount() } returns flow {
            emit(listOf(asset))
            awaitCancellation()
        }
        viewModel()
        runCurrent()

        store.clear()
        runCurrent()

        verify(exactly = 0) { FirebaseWrapper.recordException(any()) }
    }
}
