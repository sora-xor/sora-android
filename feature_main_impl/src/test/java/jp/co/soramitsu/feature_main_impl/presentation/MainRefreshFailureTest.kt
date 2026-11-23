package jp.co.soramitsu.feature_main_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.RepeatStrategy
import jp.co.soramitsu.common.domain.RepeatStrategyBuilder
import jp.co.soramitsu.common.logger.WalletDiagnostics
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.subs.GlobalSubscriptionManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsUpdateSubscription
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.test_data.TestAccounts
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainRefreshFailureTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule()
    @Test fun `wallet refresh failure leaves PIN and local navigation available`() = runTest {
        val assets = mockk<AssetsInteractor>()
        every { assets.flowCurSoraAccount() } returns flowOf(TestAccounts.soraAccount)
        coEvery { assets.updateWhitelistBalances() } throws IllegalStateException("PI_INDEXER_GRAPHQL_ERROR")
        val node = mockk<NodeManager>()
        every { node.connectionState } returns emptyFlow()
        val global = mockk<GlobalSubscriptionManager>()
        coEvery { global.start() } returns emptyFlow()
        val manager = mockk<CoroutineManager>()
        every { manager.io } returns StandardTestDispatcher(testScheduler)
        val pin = mockk<PinCodeInteractor>(relaxed = true)
        val pools = mockk<PoolsUpdateSubscription>(relaxed = true)
        mockkObject(WalletDiagnostics, RepeatStrategyBuilder)
        every { WalletDiagnostics.recordException(any()) } just Runs
        every { RepeatStrategyBuilder.infinite() } returns object : RepeatStrategy {
            override suspend fun repeat(block: suspend () -> Unit) = Unit
        }
        try {
            val vm = MainViewModel(assets, node, pin, global, mockk(relaxed = true), manager, pools)
            advanceUntilIdle()
            assertTrue(vm.badConnectionVisibilityLiveData.value == true)
            vm.showPinFragment()
            advanceUntilIdle()
            coVerify(exactly = 1) { pin.isPincodeUpdateNeeded() }
            coVerify(exactly = 0) { pools.updateBasicPools() }
            // The read was tried again a few times, and then left to the next balance event.
            coVerify(exactly = 1 + BALANCE_RETRY_WAITS_MILLIS.size) { assets.updateWhitelistBalances() }
        } finally { unmockkObject(WalletDiagnostics, RepeatStrategyBuilder) }
    }
    @Test fun `a first balance read that fails is tried again until the chain answers`() = runTest {
        val assets = mockk<AssetsInteractor>()
        every { assets.flowCurSoraAccount() } returns flowOf(TestAccounts.soraAccount)
        var reads = 0
        coEvery { assets.updateWhitelistBalances() } coAnswers { if (reads++ < 2) throw IllegalStateException("SORA2_STORAGE_ANSWER_INCOMPLETE") }
        coEvery { assets.getTokensList() } returns emptyList()
        coEvery { assets.updateWhitelistBalancesAfterTokenListWalk(any()) } just Runs
        val node = mockk<NodeManager>()
        every { node.connectionState } returns emptyFlow()
        val global = mockk<GlobalSubscriptionManager>()
        coEvery { global.start() } returns emptyFlow()
        val manager = mockk<CoroutineManager>()
        every { manager.io } returns StandardTestDispatcher(testScheduler)
        val pools = mockk<PoolsUpdateSubscription>(relaxed = true)
        mockkObject(WalletDiagnostics, RepeatStrategyBuilder)
        every { WalletDiagnostics.recordException(any()) } just Runs
        every { RepeatStrategyBuilder.infinite() } returns object : RepeatStrategy {
            override suspend fun repeat(block: suspend () -> Unit) = Unit
        }
        try {
            MainViewModel(assets, node, mockk(relaxed = true), global, mockk(relaxed = true), manager, pools)
            runCurrent()
            assertTrue(reads == 1)
            advanceTimeBy(BALANCE_RETRY_WAITS_MILLIS[0] + 1); runCurrent()
            assertTrue(reads == 2)
            advanceUntilIdle()
            // The third read answered: nothing is read after it, and what follows the balances
            // (the token list, the pools) runs as after a first read that succeeds.
            assertTrue(reads == 3)
            coVerifyOrder {
                assets.getTokensList()
                assets.updateWhitelistBalancesAfterTokenListWalk(TestAccounts.soraAccount.substrateAddress)
                pools.updateBasicPools()
            }
        } finally { unmockkObject(WalletDiagnostics, RepeatStrategyBuilder) }
    }
}
