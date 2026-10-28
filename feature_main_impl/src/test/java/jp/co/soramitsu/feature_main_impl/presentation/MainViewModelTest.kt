/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.domain.RepeatStrategy
import jp.co.soramitsu.common.domain.RepeatStrategyBuilder
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.subs.GlobalSubscriptionManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsUpdateSubscription
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.test_data.TestAccounts
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
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MainViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var nodeManager: NodeManager

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var pinCodeInteractor: PinCodeInteractor

    @Mock
    private lateinit var globalSubscriptionManager: GlobalSubscriptionManager

    @Mock
    private lateinit var blockExplorerManager: BlockExplorerManager

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var poolUpdateSubscription: PoolsUpdateSubscription

    private lateinit var mainViewModel: MainViewModel

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        whenever(globalSubscriptionManager.start()).thenReturn(flowOf(""))
        whenever(nodeManager.connectionState).thenReturn(flowOf(true))
        whenever(assetsInteractor.flowCurSoraAccount()).thenReturn(flowOf(TestAccounts.soraAccount))
        whenever(coroutineManager.io).thenReturn(this.coroutineContext[CoroutineDispatcher]!!)
        whenever(assetsInteractor.getTokensList()).thenReturn(emptyList())
        whenever(blockExplorerManager.getTokensLiquidity(emptyList())).thenReturn(emptyList())
        whenever(poolUpdateSubscription.updateBasicPools()).thenReturn(Unit)

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
            pinCodeInteractor,
            globalSubscriptionManager,
            blockExplorerManager,
            coroutineManager,
            poolUpdateSubscription,
        )
        advanceTimeBy(42000)
        verify(globalSubscriptionManager).start()
        verify(assetsInteractor).updateWhitelistBalances()
        verify(blockExplorerManager, times(3)).updateFiat()
        assertFalse(mainViewModel.badConnectionVisibilityLiveData.getOrAwaitValue())
    }

    @Test
    fun `pin screen opens without crashing when the wallet key cannot be used`() = runTest {
        // For example the Keystore key did not move with a phone-to-phone transfer: reading the
        // stored PIN fails. The PIN screen still opens (and checks again there).
        whenever(pinCodeInteractor.isPincodeUpdateNeeded())
            .thenThrow(KeyMaterialUnavailableException("alias missing", transient = false))
        mainViewModel = MainViewModel(
            assetsInteractor,
            nodeManager,
            pinCodeInteractor,
            globalSubscriptionManager,
            blockExplorerManager,
            coroutineManager,
            poolUpdateSubscription,
        )

        mainViewModel.showPinFragment()
        advanceTimeBy(42000)

        assertFalse(mainViewModel.isPincodeUpdateNeeded.getOrAwaitValue())
    }
}
