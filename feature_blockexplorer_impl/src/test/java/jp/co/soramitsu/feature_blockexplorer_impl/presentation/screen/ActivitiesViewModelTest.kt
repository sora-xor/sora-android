/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ActivitiesViewModelTest {

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
    private lateinit var walletInteractor: WalletInteractor

    @MockK
    private lateinit var transactionHistoryHandler: TransactionHistoryHandler

    @MockK
    private lateinit var assetsRouter: AssetsRouter

    @MockK
    private lateinit var router: MainRouter

    private lateinit var viewModel: ActivitiesViewModel

    @Before
    fun setUp() = runTest {
        every { assetsRouter.showTxDetails(any()) } returns Unit
        every { assetsInteractor.flowCurSoraAccount() } returns flowOf(TestAccounts.soraAccount)
        coEvery { walletInteractor.getFeeToken() } returns TestTokens.xorToken
        coEvery { transactionHistoryHandler.refreshHistoryEvents() } returns Unit
        coEvery { transactionHistoryHandler.onMoreHistoryEventsRequested() } returns Unit
        coEvery { transactionHistoryHandler.flowLocalTransactions() } returns flowOf(true)
        coEvery { transactionHistoryHandler.historyState } returns MutableStateFlow(HistoryState.Loading)

        viewModel = ActivitiesViewModel(
                assetsInteractor,
                assetsRouter,
                walletInteractor,
                transactionHistoryHandler,
                router
        )
    }

    @Test
    fun `init successful`() = runTest {
        coVerify { transactionHistoryHandler.refreshHistoryEvents() }
    }

    @Test
    fun `onTxHistoryItemClick() called`() = runTest {
        val txHash = "txHash"

        viewModel.onTxHistoryItemClick(txHash)

        assetsRouter.showTxDetails(txHash)
    }

    @Test
    fun `onMoreHistoryEventsRequested() called`() = runTest {
        viewModel.onMoreHistoryEventsRequested()
        advanceUntilIdle()

        coVerify { transactionHistoryHandler.onMoreHistoryEventsRequested() }
    }
}