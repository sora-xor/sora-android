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

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_wallet_api.data.NetworkAccountActivity
import jp.co.soramitsu.feature_wallet_api.data.NetworkActivityRepository
import jp.co.soramitsu.test_data.TestAccounts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitiesViewModelTest {
    @get:Rule val rule = InstantTaskExecutorRule()
    @get:Rule val mainCoroutineRule = MainCoroutineRule()

    private val assetsInteractor = mockk<AssetsInteractor>()
    private val assetsRouter = mockk<AssetsRouter>(relaxed = true)
    private val handler = mockk<TransactionHistoryHandler>(relaxed = true)
    private val networkRepository = mockk<NetworkActivityRepository>(relaxed = true)
    private val accounts = MutableStateFlow(TestAccounts.soraAccount)
    private val soraHistory = MutableStateFlow<HistoryState>(HistoryState.NoData)
    private val networks = MutableStateFlow(listOf(network()))

    @Before
    fun setUp() {
        every { assetsInteractor.flowCurSoraAccount() } returns accounts
        every { handler.flowLocalTransactions() } returns flowOf(true)
        every { handler.historyState } returns soraHistory
        every { networkRepository.observeCurrentWallet() } returns networks
    }

    private fun viewModel() = ActivitiesViewModel(assetsInteractor, assetsRouter, handler, networkRepository)

    @Test
    fun `loads both history sources on entry and refresh`() = runTest {
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(networks.value, model.networkActivity.value.accounts)
        assertEquals(HistoryState.NoData, model.historyState.value)
        coVerify(exactly = 2) { handler.refreshHistoryEvents() }
        model.refresh()
        advanceUntilIdle()
        coVerify(exactly = 3) { handler.refreshHistoryEvents() }
        verify { networkRepository.refresh() }
    }

    @Test
    fun `SORA2 transaction details and pagination retain their routes`() = runTest {
        val model = viewModel()
        advanceUntilIdle()
        model.onTxHistoryItemClick("hash")
        model.onMoreHistoryEventsRequested()
        advanceUntilIdle()
        verify { assetsRouter.showTxDetails("hash") }
        coVerify { handler.onMoreHistoryEventsRequested() }
    }

    @Test
    fun `wallet switch clears network and SORA2 rows before replacement reads finish`() = runTest {
        val oldHistory = HistoryState.History(true, emptyList())
        soraHistory.value = oldHistory
        val gate = CompletableDeferred<Unit>()
        coEvery { handler.refreshHistoryEvents() } coAnswers {
            if (accounts.value == TestAccounts.soraAccount2) gate.await()
        }
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(oldHistory, model.historyState.value)
        accounts.value = TestAccounts.soraAccount2
        advanceUntilIdle()
        assertTrue(model.networkActivity.value.accounts.isEmpty())
        assertEquals(HistoryState.Loading, model.historyState.value)
        // Late emissions for the previous account must never reappear.
        networks.value = listOf(network().copy(address = "late-old-address"))
        advanceUntilIdle()
        assertTrue(model.networkActivity.value.accounts.isEmpty())
        val replacement = network().copy(walletId = TestAccounts.soraAccount2.substrateAddress)
        networks.value = listOf(replacement)
        soraHistory.value = HistoryState.NoData
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(replacement), model.networkActivity.value.accounts)
        assertEquals(HistoryState.NoData, model.historyState.value)
    }

    @Test
    fun `pagination uses current wallet and prevents repeated or stale requests`() = runTest {
        val model = viewModel()
        advanceUntilIdle()
        model.loadMoreNetworkHistory(network().copy(walletId = "other-wallet"))
        model.loadMoreNetworkHistory(network())
        model.loadMoreNetworkHistory(network())
        verify(exactly = 1) { networkRepository.loadMore(TestAccounts.soraAccount.substrateAddress, WalletNetworkId.TAIRA) }
    }

    @Test
    fun `refresh retries a failed network subscription`() = runTest {
        every { networkRepository.observeCurrentWallet() } returns flow { error("offline") } andThen networks
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.networkActivity.value.error)
        model.refresh()
        advanceUntilIdle()
        assertFalse(model.networkActivity.value.error)
        assertFalse(model.networkActivity.value.loading)
        assertEquals(networks.value, model.networkActivity.value.accounts)
    }

    private fun network() = NetworkAccountActivity(
        walletId = TestAccounts.soraAccount.substrateAddress,
        networkId = WalletNetworkId.TAIRA,
        networkName = "Taira",
        isTestnet = true,
        address = "taira-account",
        confirmedTransfers = emptyList(),
        pendingTransactions = emptyList(),
        errorCode = null,
        hasMore = true,
    )
}
