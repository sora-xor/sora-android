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
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
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
        coEvery { assetsInteractor.updateWhitelistBalances() } returns Unit
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
            coroutineManager
        )
        advanceTimeBy(22000)
        verify { globalSubscriptionManager.start() }
        coVerify { assetsInteractor.updateWhitelistBalances() }
        coVerify(exactly = 3) { blockExplorerManager.updateFiat() }
        assertFalse(mainViewModel.badConnectionVisibilityLiveData.getOrAwaitValue())
    }
}
