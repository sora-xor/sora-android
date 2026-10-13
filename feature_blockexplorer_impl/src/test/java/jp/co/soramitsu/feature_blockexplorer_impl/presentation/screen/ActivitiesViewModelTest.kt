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
