/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
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
        every { resourceManager.getString(R.string.asset_details_recent_activity) } returns "recent activity"

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