package jp.co.soramitsu.feature_main_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.RepeatStrategy
import jp.co.soramitsu.common.domain.RepeatStrategyBuilder
import jp.co.soramitsu.common.logger.FirebaseWrapper
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
        mockkObject(FirebaseWrapper, RepeatStrategyBuilder)
        every { FirebaseWrapper.recordException(any()) } just Runs
        every { RepeatStrategyBuilder.infinite() } returns object : RepeatStrategy {
            override suspend fun repeat(block: suspend () -> Unit) = Unit
        }
        try {
            val vm = MainViewModel(assets, node, pin, global, mockk(relaxed = true), manager, pools, mockk(relaxed = true))
            advanceUntilIdle()
            assertTrue(vm.badConnectionVisibilityLiveData.value == true)
            vm.showPinFragment()
            advanceUntilIdle()
            coVerify(exactly = 1) { pin.isPincodeUpdateNeeded() }
            coVerify(exactly = 0) { pools.updateBasicPools() }
        } finally { unmockkObject(FirebaseWrapper, RepeatStrategyBuilder) }
    }
}
