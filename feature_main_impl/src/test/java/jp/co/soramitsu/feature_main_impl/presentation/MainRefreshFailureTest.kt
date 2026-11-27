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
        coEvery { assets.updateWhitelistBalances(any()) } throws IllegalStateException("PI_INDEXER_GRAPHQL_ERROR")
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
            coVerify(exactly = 1 + BALANCE_RETRY_WAITS_MILLIS.size) { assets.updateWhitelistBalances(TestAccounts.soraAccount.substrateAddress) }
        } finally { unmockkObject(WalletDiagnostics, RepeatStrategyBuilder) }
    }
    @Test fun `a first balance read that fails is tried again until the chain answers`() = runTest {
        val assets = mockk<AssetsInteractor>()
        every { assets.flowCurSoraAccount() } returns flowOf(TestAccounts.soraAccount)
        var reads = 0
        coEvery { assets.updateWhitelistBalances(any()) } coAnswers { if (reads++ < 2) throw IllegalStateException("SORA2_STORAGE_ANSWER_INCOMPLETE"); true }
        coEvery { assets.getTokensList() } returns emptyList()
        coEvery { assets.updateWhitelistBalancesAfterTokenListWalk(any()) } returns true
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
            val vm = MainViewModel(assets, node, mockk(relaxed = true), global, mockk(relaxed = true), manager, pools)
            runCurrent()
            assertTrue(reads == 1)
            advanceTimeBy(BALANCE_RETRY_WAITS_MILLIS[0] + 1); runCurrent()
            assertTrue(reads == 2)
            assertTrue(vm.badConnectionVisibilityLiveData.value == true)
            advanceUntilIdle()
            assertTrue("the read answered: no banner", vm.badConnectionVisibilityLiveData.value == false)
            // The third read answered: nothing is read after it, and what follows the balances
            // (the read after the token list walk, the pools, the token list for the liquidity)
            // runs as after a first read that succeeds.
            assertTrue(reads == 3)
            coVerifyOrder {
                assets.updateWhitelistBalancesAfterTokenListWalk(TestAccounts.soraAccount.substrateAddress)
                pools.updateBasicPools()
                assets.getTokensList()
            }
        } finally { unmockkObject(WalletDiagnostics, RepeatStrategyBuilder) }
    }
    @Test fun `a read for a wallet that is no longer selected ends what was to follow it`() = runTest {
        for (leftAt in listOf("first read", "read after the walk")) {
            val assets = mockk<AssetsInteractor>()
            every { assets.flowCurSoraAccount() } returns flowOf(TestAccounts.soraAccount)
            // The selection changed while the read waited (a retry's wait, the walk): the next
            // account's emission is still on its way.
            coEvery { assets.updateWhitelistBalances(any()) } returns (leftAt != "first read")
            coEvery { assets.updateWhitelistBalancesAfterTokenListWalk(any()) } returns false
            val node = mockk<NodeManager>()
            every { node.connectionState } returns emptyFlow()
            val global = mockk<GlobalSubscriptionManager>()
            coEvery { global.start() } returns emptyFlow()
            val manager = mockk<CoroutineManager>()
            every { manager.io } returns StandardTestDispatcher(testScheduler)
            val pools = mockk<PoolsUpdateSubscription>(relaxed = true)
            val explorer = mockk<jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager>(relaxed = true)
            mockkObject(RepeatStrategyBuilder)
            every { RepeatStrategyBuilder.infinite() } returns object : RepeatStrategy {
                override suspend fun repeat(block: suspend () -> Unit) = Unit
            }
            try {
                val vm = MainViewModel(assets, node, mockk(relaxed = true), global, explorer, manager, pools)
                advanceUntilIdle()
                // Not a failure: no banner, no retry, and nothing of the old wallet's follow-up.
                assertTrue(leftAt, vm.badConnectionVisibilityLiveData.value != true)
                coVerify(exactly = 1) { assets.updateWhitelistBalances(TestAccounts.soraAccount.substrateAddress) }
                coVerify(exactly = if (leftAt == "first read") 0 else 1) {
                    assets.updateWhitelistBalancesAfterTokenListWalk(TestAccounts.soraAccount.substrateAddress)
                }
                coVerify(exactly = 0) { pools.updateBasicPools() }
                coVerify(exactly = 0) { assets.getTokensList() }
                coVerify(exactly = 0) { explorer.getTokensLiquidity(any()) }
            } finally { unmockkObject(RepeatStrategyBuilder) }
        }
    }
}
