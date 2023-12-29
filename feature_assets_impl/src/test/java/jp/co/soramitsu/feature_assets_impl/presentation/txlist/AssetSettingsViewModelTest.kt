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

package jp.co.soramitsu.feature_assets_impl.presentation.txlist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.screens.txlist.TxListViewModel
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens.xorToken
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class TxListViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var assetsInteractor: AssetsInteractor

    @MockK
    private lateinit var router: AssetsRouter

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var transactionHistoryHandler: TransactionHistoryHandler

    private val assetId = xorToken.id
    private val txHash = "txHash"

    private lateinit var viewModel: TxListViewModel

    @Before
    fun setUp() = runTest {
        every { assetsInteractor.flowCurSoraAccount() } returns flowOf(TestAccounts.soraAccount)
        every { transactionHistoryHandler.flowLocalTransactions() } returns flowOf(true)
        coEvery { transactionHistoryHandler.onMoreHistoryEventsRequested() } returns Unit
        every { transactionHistoryHandler.historyState } returns MutableStateFlow(HistoryState.Loading)
        coEvery { transactionHistoryHandler.refreshHistoryEvents(any()) } returns Unit
        every { router.showTxDetails(txHash) } returns Unit
        every {
            resourceManager.getString(R.string.asset_details_recent_activity)
        } returns "recent activity"

        viewModel = jp.co.soramitsu.feature_assets_impl.presentation.screens.txlist.TxListViewModel(
            assetsInteractor,
            router,
            transactionHistoryHandler,
            assetId
        )
    }

    @Test
    fun `init check`() = runTest {
        val s = viewModel.toolbarState.getOrAwaitValue()
        assertEquals(R.string.asset_details_recent_activity, s.basic.title)
    }

    @Test
    fun `onMoreHistoryEventsRequested() called`() = runTest {
        viewModel.onMoreHistoryEventsRequested()
        advanceUntilIdle()
        coVerify { transactionHistoryHandler.onMoreHistoryEventsRequested() }
    }

    @Test
    fun `onHistoryItemClick() called`() {
        viewModel.onTxHistoryItemClick(txHash)

        verify { router.showTxDetails(txHash) }
    }

    @Test
    fun `refresh() called`() {
        viewModel.refresh()

        coVerify { transactionHistoryHandler.refreshHistoryEvents(assetId) }
    }
}
