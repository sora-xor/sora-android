/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.subs.GlobalSubscriptionManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsUpdateSubscription
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
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
    private lateinit var soraCardInteractor: SoraCardInteractor

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
            soraCardInteractor,
        )
        advanceTimeBy(42000)
        verify(globalSubscriptionManager).start()
        verify(assetsInteractor).updateWhitelistBalances()
        verify(blockExplorerManager, times(3)).updateFiat()
        assertFalse(mainViewModel.badConnectionVisibilityLiveData.getOrAwaitValue())
    }
}
