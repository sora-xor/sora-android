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

package jp.co.soramitsu.feature_blockexplorer_impl.domain

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionMappers
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionsInfo
import jp.co.soramitsu.feature_blockexplorer_impl.testdata.TestTransactions
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class TransactionHistoryHandlerTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var assetsRepository: AssetsRepository

    @MockK
    private lateinit var transactionMappers: TransactionMappers

    @MockK
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var dateTimeFormatter: DateTimeFormatter

    @MockK
    private lateinit var coroutineManager: CoroutineManager

    @MockK
    private lateinit var coroutineScope: CoroutineScope

    @MockK
    private lateinit var coroutineContext: CoroutineContext

    private val mockedUri = Mockito.mock(Uri::class.java)

    private val tokens = listOf(TestTokens.xorToken)
    private val txHash = "txHash"
    private val date = "10 Jan. 2021 12:12"

    private val transactionsWithHeaders = listOf(
        EventUiModel.EventTimeSeparatorUiModel(title = "01 Jan 1970 00:00"),
        EventUiModel.EventTxUiModel.EventTransferInUiModel(
            "",
            mockedUri,
            "peerId",
            "01 Jan 1970 00:00",
            1000000,
            "10.12 VAL",
            "$ 34.3",
            TransactionStatus.COMMITTED
        )
    )

    private lateinit var transactionHistoryHandler: TransactionHistoryHandler

    @Before
    fun setUp() = runTest {
        every { resourceManager.getString(any()) } returns ""
        every { dateTimeFormatter.dateToDayWithoutCurrentYear(any(), any(), any()) } returns "01 Jan 1970 00:00"
        every { transactionHistoryRepository.state } returns flowOf(true)
        every { coroutineManager.applicationScope } returns coroutineScope
        every { coroutineScope.coroutineContext } returns coroutineContext
        every { transactionHistoryRepository.onSoraAccountChange() } returns Unit
        coEvery { assetsRepository.tokensList() } returns tokens
        every { userRepository.flowCurSoraAccount() } returns flow {
            emit(TestAccounts.soraAccount)
            emit(TestAccounts.soraAccount2)
        }
        coEvery {
            transactionHistoryRepository.getTransaction(txHash, tokens, TestAccounts.soraAccount)
        } returns TestTransactions.sendSuccessfulTx
        coEvery { userRepository.getCurSoraAccount() } returns TestAccounts.soraAccount
        coEvery {
            transactionHistoryRepository.getTransactionHistory(any(), any(), any(), any())
        } returns TransactionsInfo(
            listOf(TestTransactions.sendSuccessfulTx),
            true
        )
        every {
            transactionMappers.mapTransaction(TestTransactions.sendSuccessfulTx, TestAccounts.soraAccount.substrateAddress)
        } returns transactionsWithHeaders[1] as EventUiModel.EventTxUiModel
        coEvery {
            transactionHistoryRepository.getLastTransactions(TestAccounts.soraAccount, listOf(TestTokens.xorToken), 1, null)
        } returns listOf(TestTransactions.sendSuccessfulTx)

        transactionHistoryHandler = TransactionHistoryHandlerImpl(
            assetsRepository,
            transactionMappers,
            transactionHistoryRepository,
            resourceManager,
            userRepository,
            dateTimeFormatter,
            coroutineManager
        )
    }

    @Test
    fun `init successful`() = runTest {
        verify { transactionHistoryRepository.onSoraAccountChange() }
    }

    @Test
    fun `flowLocalTransactions() called`() = runTest {
        transactionHistoryHandler.flowLocalTransactions().collectLatest {
            assertTrue(it)
        }
    }

    @Test
    fun `onMoreHistoryEventsRequested() called`() = runTest {
        advanceUntilIdle()
        val state = HistoryState.History(
            true,
            transactionsWithHeaders
        )

        transactionHistoryHandler.historyState.test(
            this
        ) {
            transactionHistoryHandler.refreshHistoryEvents()
            advanceUntilIdle()
            assertEquals(this.awaitValue(0), HistoryState.Loading)
            transactionHistoryHandler.onMoreHistoryEventsRequested()
            advanceUntilIdle()
            assertEquals(this.awaitValue(1), state)
            this.finishAssertion()
        }
    }

    @Test
    fun `getCatchedEvents() called`() = runTest {
        val result = transactionHistoryHandler.getCachedEvents(1)

        assertEquals(transactionsWithHeaders.subList(1, transactionsWithHeaders.size), result)
    }

    @Test
    fun `getTransaction() called`() = runTest {
        assertEquals(TestTransactions.sendSuccessfulTx, transactionHistoryHandler.getTransaction(txHash))
    }
}
