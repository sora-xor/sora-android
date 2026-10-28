package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.fullpoollist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.domain.model.*
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

/** Real portfolio projection, search timing, lifecycle and navigation with inert boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class FullPoolListFlowTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val liveData = InstantTaskExecutorRule()
    private val interactor = mockk<PoolsInteractor>()
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val store = ViewModelStore()
    private fun token(id: String, name: String = id.uppercase()) = Token(id, name, id.uppercase(), 18, true, null, 1.0, 0.0, "$")
    private fun pool(target: String, name: String = target.uppercase()) = CommonUserPoolData(
        BasicPoolData(token("xor"), token(target, name), BigDecimal("100"), BigDecimal("200"), BigDecimal("100"), "pool-$target", 12.0),
        UserPoolData(BigDecimal("2"), BigDecimal("4"), 2.0, BigDecimal.TEN, false, 0))
    private val pools = MutableStateFlow(listOf(pool("val", "Validator"), pool("eth", "Ethereum")))
    @Before fun setup() {
        every { interactor.subscribePoolsCacheOfCurAccount() } returns pools
        mockkObject(FirebaseWrapper); every { FirebaseWrapper.recordException(any()) } just Runs
    }
    @After fun cleanup() { store.clear(); unmockkObject(FirebaseWrapper) }
    private fun vm() = FullPoolListViewModel(interactor, NumbersFormatter(), router).also { store.put("vm", it) }
    private fun ids(vm: FullPoolListViewModel) = vm.state.value.list.pools.map { it.tokenIds }
    @Test fun loadingEndsAfterTheFirstSnapshotAndShowsExactPooledAmounts() = runTest {
        val vm = vm(); assertTrue(vm.state.value.loading); runCurrent(); assertFalse(vm.state.value.loading)
        assertEquals(listOf("xor" to "val", "xor" to "eth"), ids(vm))
        assertEquals("2 XOR - 4 VAL", vm.state.value.list.pools.first().poolAmounts)
        assertEquals("$12", vm.state.value.fiatSum)
    }
    @Test fun emptyWalletFinishesLoadingWithoutPhantomPositions() = runTest {
        pools.value = emptyList(); val vm = vm(); runCurrent()
        assertFalse(vm.state.value.loading); assertTrue(ids(vm).isEmpty()); assertEquals("$0", vm.state.value.fiatSum)
    }
    @Test fun firstSnapshotFailureStopsLoadingAndReportsTheFailure() = runTest {
        every { interactor.subscribePoolsCacheOfCurAccount() } returns flow { throw IllegalStateException("pool cache unavailable") }
        val vm = vm(); runCurrent(); assertFalse(vm.state.value.loading); assertTrue(ids(vm).isEmpty())
        assertNotNull(vm.errorFromResourceLiveData.value)
    }
    @Test fun cancelledCollectionDoesNotPresentAnError() = runTest {
        every { interactor.subscribePoolsCacheOfCurAccount() } returns flow { throw CancellationException("screen closed") }
        val vm = vm(); runCurrent(); assertNull(vm.errorLiveData.value)
    }
    @Test fun clearingScreenStopsLaterPoolUpdates() = runTest {
        val vm = vm(); runCurrent(); val previous = vm.state.value; store.clear(); pools.value = emptyList(); runCurrent()
        assertSame(previous, vm.state.value)
    }
    @Test fun searchWaitsForTheDebounceAndUpdatesTheDisplayedQueryImmediately() = runTest {
        val vm = vm(); runCurrent(); vm.onToolbarSearch("val"); assertEquals("val", vm.toolbarState.value!!.basic.searchValue)
        advanceTimeBy(399); runCurrent(); assertEquals(2, ids(vm).size)
        advanceTimeBy(1); runCurrent(); assertEquals(listOf("xor" to "val"), ids(vm)); assertEquals("$6", vm.state.value.fiatSum)
    }
    @Test fun newerQueryReplacesAnUnsettledPreviousSearch() = runTest {
        val vm = vm(); runCurrent(); vm.onToolbarSearch("val"); advanceTimeBy(300); vm.onToolbarSearch("ETH")
        advanceTimeBy(100); runCurrent(); assertEquals(2, ids(vm).size)
        advanceTimeBy(300); runCurrent(); assertEquals(listOf("xor" to "eth"), ids(vm))
    }
    @Test fun tokenNameMatchesCaseInsensitively() = runTest {
        val vm = vm(); runCurrent(); vm.onToolbarSearch("VALIDATOR"); advanceTimeBy(400); runCurrent()
        assertEquals(listOf("xor" to "val"), ids(vm))
    }
    @Test fun baseTokenSearchKeepsEveryMatchingPair() = runTest {
        val vm = vm(); runCurrent(); vm.onToolbarSearch("XOR"); advanceTimeBy(400); runCurrent(); assertEquals(2, ids(vm).size)
    }
    @Test fun noMatchDoesNotKeepThePreviousFiatTotal() = runTest {
        val vm = vm(); runCurrent(); vm.onToolbarSearch("not-a-pool"); advanceTimeBy(400); runCurrent()
        assertTrue(ids(vm).isEmpty()); assertEquals("$0", vm.state.value.fiatSum)
    }
    @Test fun blankQueryRestoresAllPositions() = runTest {
        val vm = vm(); runCurrent(); vm.onToolbarSearch("val"); advanceTimeBy(400); runCurrent()
        vm.onToolbarSearch("  "); advanceTimeBy(400); runCurrent(); assertEquals(2, ids(vm).size)
    }
    @Test fun refreshedPoolsRespectTheCurrentFilterAndRemoveStaleBalances() = runTest {
        val vm = vm(); runCurrent(); vm.onToolbarSearch("val"); advanceTimeBy(400); runCurrent()
        pools.value = listOf(pool("eth")); runCurrent(); assertTrue(ids(vm).isEmpty()); assertEquals("$0", vm.state.value.fiatSum)
    }
    @Test fun refreshedBalancesRecalculateDisplayedAmountsAndFiat() = runTest {
        val vm = vm(); runCurrent(); pools.value = listOf(pool("val").let { it.copy(user = it.user.copy(basePooled = BigDecimal.TEN)) }); runCurrent()
        assertEquals("10 XOR - 4 VAL", vm.state.value.list.pools.single().poolAmounts); assertEquals("$14", vm.state.value.fiatSum)
    }
    @Test fun editingAndOpeningAPoolUseDedicatedRoutesAndExactIdentifiers() = runTest {
        val vm = vm(); runCurrent(); vm.onAction(); vm.onPoolClick("xor" to "eth")
        verify(exactly = 1) { router.showFullPoolsSettings() }; verify(exactly = 1) { router.showPoolDetails("xor" to "eth") }
    }
}
