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

package jp.co.soramitsu.feature_assets_impl.presentation.txlist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.screens.txlist.TxListViewModel
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens.xorToken
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
