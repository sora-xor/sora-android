/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.verify
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.RepeatStrategy
import jp.co.soramitsu.common.domain.RepeatStrategyBuilder
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.subs.GlobalSubscriptionManager
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
class MainViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var nodeManager: NodeManager

    @MockK
    private lateinit var assetsInteractor: AssetsInteractor

    @MockK
    private lateinit var walletInteractor: WalletInteractor

    @MockK
    private lateinit var pinCodeInteractor: PinCodeInteractor

    @MockK
    private lateinit var globalSubscriptionManager: GlobalSubscriptionManager

    @MockK
    private lateinit var blockExplorerManager: BlockExplorerManager

    @MockK
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var mainViewModel: MainViewModel

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        every { globalSubscriptionManager.start() } returns flowOf("")
        every { nodeManager.connectionState } returns flowOf(true)
        every { assetsInteractor.flowCurSoraAccount() } returns flowOf(TestAccounts.soraAccount)
        coEvery { assetsInteractor.getCurSoraAccount() } returns TestAccounts.soraAccount
        every { coroutineManager.io } returns this.coroutineContext[CoroutineDispatcher]!!
        coEvery { blockExplorerManager.updateFiat() } returns Unit
        coEvery { assetsInteractor.updateWhitelistBalances(true) } returns Unit
        mockkObject(RepeatStrategyBuilder)
        every { RepeatStrategyBuilder.infinite() } returns object : RepeatStrategy {
            override suspend fun repeat(block: suspend () -> Unit) {
                repeat(4) {
                    block.invoke()
                }
            }
        }
    }

    @Test
    fun `init successful`() = runTest {
        mainViewModel = MainViewModel(
            assetsInteractor,
            nodeManager,
            walletInteractor,
            pinCodeInteractor,
            globalSubscriptionManager,
            blockExplorerManager,
            coroutineManager
        )
        advanceTimeBy(22000)
        verify { globalSubscriptionManager.start() }
        coVerify { assetsInteractor.updateWhitelistBalances(true) }
        coVerify(exactly = 3) { blockExplorerManager.updateFiat() }
        assertFalse(mainViewModel.badConnectionVisibilityLiveData.getOrAwaitValue())
    }
}
