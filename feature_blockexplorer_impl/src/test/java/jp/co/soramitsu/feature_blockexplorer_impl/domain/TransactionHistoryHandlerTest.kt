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

import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.util.Calendar
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.androidfoundation.format.unsafeCast
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.test
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.domain.TransactionHistoryHandler
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionMappers
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionsInfo
import jp.co.soramitsu.feature_blockexplorer_impl.presentation.txhistory.TransactionMappersImpl
import jp.co.soramitsu.feature_blockexplorer_impl.testdata.TestTransactions
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class TransactionHistoryHandlerTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

//    @get:Rule
//    val mockkRule = MockKRule(this)

    @Mock
    private lateinit var assetsRepository: AssetsRepository

    @Mock
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var language: LanguagesHolder

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var dateTimeFormatter: DateTimeFormatter

    private val txMapper: TransactionMappers by lazy {
        TransactionMappersImpl(resourceManager, NumbersFormatter(), dateTimeFormatter)
    }

    private var mockedUri = DEFAULT_ICON_URI

    private val tokens = listOf(TestTokens.xorToken)
    private val txHash = TestTransactions.sendSuccessfulTx.base.txHash

    private val transactionsWithHeaders = listOf(
        EventUiModel.EventTimeSeparatorUiModel(title = "01 Feb 1970"),
        EventUiModel.EventTxUiModel.EventTransferOutUiModel(
            txHash,
            mockedUri,
            "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
            "11:58",
            1673918013,
            "10 VAL",
            "~0",
            TransactionStatus.COMMITTED,
        ),
        EventUiModel.EventTxUiModel.EventTransferOutUiModel(
            "txHash2",
            mockedUri,
            "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
            "13:38",
            1679918013,
            "1 VAL",
            "~0",
            TransactionStatus.REJECTED,
        )
    )

    private lateinit var transactionHistoryHandler: TransactionHistoryHandler

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
//        mockkStatic(Uri::parse)
//        every { Uri.parse(any()) } returns mockedUri
//        mockkStatic(Token::iconUri)
//        every { TestTokens.xorToken.iconUri() } returns mockedUri
//        every { TestTokens.valToken.iconUri() } returns mockedUri

//        Mockito.mockStatic(Token::class.java).use { mocked ->
//            mocked.`when`<Any> { Token.iconUri() }.thenReturn(mockedUri)
//        }

        // whenever(language.getCurrentLocale()).thenReturn(Locale.ENGLISH)
        whenever(dateTimeFormatter.formatTimeWithoutSeconds(any())).thenReturn("01 Feb 1970")
        whenever(
            dateTimeFormatter.dateToDayWithoutCurrentYear(
                any(),
                any(),
                any()
            )
        ).thenReturn("01 Feb 1970")
        whenever(resourceManager.getString(any())).thenReturn("")
        whenever(transactionHistoryRepository.state).thenReturn(flowOf(true))
        whenever(coroutineManager.applicationScope).thenReturn(this)
        whenever(coroutineManager.io).thenReturn(this.coroutineContext[CoroutineDispatcher]!!)
        // whenever(transactionHistoryRepository.onSoraAccountChange()).thenReturn(Unit)
        assetsRepository.stub {
            onBlocking { tokensList() } doReturn tokens
        }
        whenever(userRepository.flowCurSoraAccount()).thenReturn(
            flow {
                emit(TestAccounts.soraAccount)
                emit(TestAccounts.soraAccount2)
            }
        )
        transactionHistoryRepository.stub {
            onBlocking {
                getTransaction(
                    txHash,
                    tokens,
                    TestAccounts.soraAccount
                )
            } doReturn TestTransactions.sendSuccessfulTx
        }
        userRepository.stub {
            onBlocking { getCurSoraAccount() } doReturn TestAccounts.soraAccount
        }
        transactionHistoryRepository.stub {
            onBlocking {
                getTransactionHistory(
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                )
            } doReturn TransactionsInfo(
                listOf(TestTransactions.sendSuccessfulTx),
                true
            )
        }
        transactionHistoryRepository.stub {
            onBlocking {
                getLastTransactions(
                    TestAccounts.soraAccount,
                    listOf(TestTokens.xorToken),
                    1,
                    null
                )
            } doReturn listOf(TestTransactions.sendFailedTx)
        }
        transactionHistoryHandler = TransactionHistoryHandlerImpl(
            assetsRepository,
            txMapper,
            transactionHistoryRepository,
            resourceManager,
            userRepository,
            dateTimeFormatter,
            coroutineManager,
        )
    }

    @Test
    fun `cache plus new tx`() = runTest {
        val curTime = transactionHistoryHandler.getCachedEvents(1).getOrNull(0)
            ?.safeCast<EventUiModel.EventTxUiModel>()?.timestamp!!
        assertEquals(1679918013, curTime)
        transactionHistoryHandler.historyState.test(this) {
            transactionHistoryHandler.refreshHistoryEvents()
            var flowValue = this.awaitValue(0)
            assertTrue(flowValue is HistoryState.Loading)
            flowValue = this.awaitValue(1)
            assertTrue(flowValue is HistoryState.History)
            assertEquals(2, flowValue.unsafeCast<HistoryState.History>().events.size)
            this.finishAssertion()
        }
    }

    @Test
    fun `has new tx`() = runTest {
        advanceUntilIdle()
        val new = transactionHistoryHandler.hasNewTransaction()
        assertTrue(new)
    }

    @Test
    fun `init successful`() = runTest {
        advanceUntilIdle()
        verify(transactionHistoryRepository).onSoraAccountChange()
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
            transactionsWithHeaders.subList(0, 2)
        )

        transactionHistoryHandler.historyState.test(
            this
        ) {
            transactionHistoryHandler.refreshHistoryEvents()
            advanceUntilIdle()
            var flowValue = this.awaitValue(0)
            assertEquals(flowValue, HistoryState.Loading)
            transactionHistoryHandler.onMoreHistoryEventsRequested()
            advanceUntilIdle()
            flowValue = this.awaitValue(1).unsafeCast<HistoryState.History>()
            assertEquals(flowValue.endReached, state.endReached)
            assertEquals(flowValue.pullToRefresh, state.pullToRefresh)
            assertEquals(flowValue.hasErrorLoadingNew, state.hasErrorLoadingNew)
            assertEquals(flowValue.events.size, state.events.size)
            assertEquals(
                flowValue.events[0].unsafeCast<EventUiModel.EventTimeSeparatorUiModel>().title,
                state.events[0].unsafeCast<EventUiModel.EventTimeSeparatorUiModel>().title,
            )
            assertEquals(
                flowValue.events[1].unsafeCast<EventUiModel.EventTxUiModel.EventTransferOutUiModel>().txHash,
                state.events[1].unsafeCast<EventUiModel.EventTxUiModel.EventTransferOutUiModel>().txHash,
            )
            this.finishAssertion()
        }
    }

    @Test
    fun `loading history separates matching day numbers across months and years`() = runTest {
        advanceUntilIdle()
        val timestamps = listOf(2026 to 10, 2026 to 9, 2025 to 9).map { (year, month) ->
            Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 24, 12, 0, 0)
            }.timeInMillis
        }
        val transfers = timestamps.mapIndexed { index, timestamp ->
            Transaction.Transfer(
                TransactionBase(
                    txHash = "history-date-$index",
                    blockHash = "blockHash",
                    fee = BigDecimal.ONE,
                    status = TransactionStatus.COMMITTED,
                    timestamp = timestamp,
                ),
                BigDecimal.TEN,
                "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
                TransactionTransferType.OUTGOING,
                TestTokens.valToken,
            )
        }
        transactionHistoryRepository.stub {
            onBlocking { getTransactionHistory(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) }
                .thenReturn(
                    TransactionsInfo(transfers.take(1), false),
                    TransactionsInfo(transfers.drop(1), true),
                )
        }
        transactionHistoryHandler.refreshHistoryEvents()
        transactionHistoryHandler.onMoreHistoryEventsRequested()
        advanceUntilIdle()

        val events = (transactionHistoryHandler.historyState.value as HistoryState.History).events
        assertEquals(6, events.size)
        for (index in transfers.indices) {
            assertTrue(events[index * 2] is EventUiModel.EventTimeSeparatorUiModel)
            assertEquals(
                transfers[index].base.txHash,
                (events[index * 2 + 1] as EventUiModel.EventTxUiModel).txHash,
            )
        }
    }

    @Test
    fun `getCachedEvents() called`() = runTest {
        val result = transactionHistoryHandler.getCachedEvents(1)
        val a =
            transactionsWithHeaders[2].unsafeCast<EventUiModel.EventTxUiModel.EventTransferOutUiModel>()
        val b = result[0].unsafeCast<EventUiModel.EventTxUiModel.EventTransferOutUiModel>()

        assertEquals(a.txHash, b.txHash)
        assertEquals(a.timestamp, b.timestamp)
        assertEquals(a.status, b.status)
    }

    @Test
    fun `getTransaction() called`() = runTest {
        assertEquals(
            TestTransactions.sendSuccessfulTx,
            transactionHistoryHandler.getTransaction(txHash)
        )
    }

    @Test
    fun `account refresh waits for active history read and replaces its results`() = runTest {
        val releaseFirstRead = CompletableDeferred<Unit>()
        val queriedAccounts = mutableListOf<SoraAccount>()
        val repository = mockk<TransactionHistoryRepository>(relaxed = true)
        coEvery { repository.getTransactionHistory(any(), any(), any(), any()) } coAnswers {
            val account = thirdArg<SoraAccount>()
            queriedAccounts += account
            if (queriedAccounts.size == 1) releaseFirstRead.await()
            TransactionsInfo(
                listOf(if (account == TestAccounts.soraAccount) TestTransactions.sendSuccessfulTx
                    else TestTransactions.sendFailedTx),
                endReached = true,
            )
        }
        whenever(userRepository.getCurSoraAccount())
            .thenReturn(TestAccounts.soraAccount, TestAccounts.soraAccount2)
        val handler = TransactionHistoryHandlerImpl(
            assetsRepository, txMapper, repository, resourceManager, userRepository,
            dateTimeFormatter, coroutineManager,
        )

        val activeRefresh = launch { handler.refreshHistoryEvents() }
        runCurrent()
        val selectedAccountRefresh = launch { handler.refreshHistoryEvents() }
        runCurrent()
        try {
            assertEquals(listOf(TestAccounts.soraAccount), queriedAccounts)
            assertFalse(selectedAccountRefresh.isCompleted)
        } finally {
            releaseFirstRead.complete(Unit)
        }
        activeRefresh.join()
        selectedAccountRefresh.join()

        assertEquals(listOf(TestAccounts.soraAccount, TestAccounts.soraAccount2), queriedAccounts)
        val finalHistory = handler.historyState.value as HistoryState.History
        val events = finalHistory.events.filterIsInstance<EventUiModel.EventTxUiModel>()
        assertEquals(listOf(TestTransactions.sendFailedTx.base.txHash), events.map { it.txHash })
        assertFalse(finalHistory.pullToRefresh)
    }

    private fun transfer(n: Int) = Transaction.Transfer(
        TransactionBase(
            "0x" + n.toString(16).padStart(64, '0'),
            "blockHash",
            BigDecimal.ONE,
            TransactionStatus.COMMITTED,
            2_679_918_013L - n,
        ),
        BigDecimal.TEN,
        "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
        TransactionTransferType.OUTGOING,
        TestTokens.valToken,
    )

    private fun stubHistoryPage(page: Long, info: TransactionsInfo) {
        transactionHistoryRepository.stub {
            onBlocking {
                getTransactionHistory(eq(page), anyOrNull(), anyOrNull(), anyOrNull())
            } doReturn info
        }
    }

    private fun listedTxHashes(): List<String> =
        transactionHistoryHandler.historyState.value
            .unsafeCast<HistoryState.History>()
            .events
            .filterIsInstance<EventUiModel.EventTxUiModel>()
            .map { it.txHash }

    @Test
    fun `load more skips rows that shifted into the next page after a new row was indexed`() = runTest {
        advanceUntilIdle()
        // Page 1 was read before the indexer added a new row for this account.
        stubHistoryPage(1L, TransactionsInfo(listOf(transfer(1), transfer(2), transfer(3)), false))
        transactionHistoryHandler.refreshHistoryEvents()
        advanceUntilIdle()
        assertEquals(
            listOf(transfer(1), transfer(2), transfer(3)).map { it.base.txHash },
            listedTxHashes(),
        )

        // The indexer has since indexed a new row, so the re-read page 2 starts with the last
        // row of the old page 1.
        stubHistoryPage(2L, TransactionsInfo(listOf(transfer(3), transfer(4)), true))
        transactionHistoryHandler.onMoreHistoryEventsRequested()
        advanceUntilIdle()

        val hashes = listedTxHashes()
        assertEquals(listOf(transfer(1), transfer(2), transfer(3), transfer(4)).map { it.base.txHash }, hashes)
        assertEquals(hashes.size, hashes.toSet().size)
        assertTrue(transactionHistoryHandler.historyState.value.unsafeCast<HistoryState.History>().endReached)
    }

    @Test
    fun `load more treats differently cased hashes as the same transaction`() = runTest {
        advanceUntilIdle()
        val upper = Transaction.Transfer(
            TransactionBase(
                transfer(3).base.txHash.uppercase().replace("0X", "0x"),
                "blockHash",
                BigDecimal.ONE,
                TransactionStatus.COMMITTED,
                transfer(3).base.timestamp,
            ),
            BigDecimal.TEN,
            "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
            TransactionTransferType.OUTGOING,
            TestTokens.valToken,
        )
        stubHistoryPage(1L, TransactionsInfo(listOf(transfer(1), transfer(3)), false))
        stubHistoryPage(2L, TransactionsInfo(listOf(upper, transfer(4)), true))
        transactionHistoryHandler.refreshHistoryEvents()
        advanceUntilIdle()
        transactionHistoryHandler.onMoreHistoryEventsRequested()
        advanceUntilIdle()

        assertEquals(
            listOf(transfer(1), transfer(3), transfer(4)).map { it.base.txHash },
            listedTxHashes(),
        )
    }

    @Test
    fun `a page with no new rows reads the next page instead of stalling`() = runTest {
        advanceUntilIdle()
        stubHistoryPage(1L, TransactionsInfo(listOf(transfer(1), transfer(2)), false))
        stubHistoryPage(2L, TransactionsInfo(listOf(transfer(2)), false))
        stubHistoryPage(3L, TransactionsInfo(listOf(transfer(5)), true))
        transactionHistoryHandler.refreshHistoryEvents()
        advanceUntilIdle()
        transactionHistoryHandler.onMoreHistoryEventsRequested()
        advanceUntilIdle()

        assertEquals(
            listOf(transfer(1), transfer(2), transfer(5)).map { it.base.txHash },
            listedTxHashes(),
        )
    }

    @Test
    fun `pages with no new rows are skipped only a bounded number of times`() = runTest {
        advanceUntilIdle()
        stubHistoryPage(1L, TransactionsInfo(listOf(transfer(1)), false))
        stubHistoryPage(2L, TransactionsInfo(listOf(transfer(1)), false))
        stubHistoryPage(3L, TransactionsInfo(listOf(transfer(1)), false))
        stubHistoryPage(4L, TransactionsInfo(listOf(transfer(1)), false))
        transactionHistoryHandler.refreshHistoryEvents()
        advanceUntilIdle()
        transactionHistoryHandler.onMoreHistoryEventsRequested()
        advanceUntilIdle()

        verify(transactionHistoryRepository).getTransactionHistory(
            eq(4L),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )
        verify(transactionHistoryRepository, never()).getTransactionHistory(
            eq(5L),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )
        val state = transactionHistoryHandler.historyState.value.unsafeCast<HistoryState.History>()
        assertEquals(listOf(transfer(1).base.txHash), listedTxHashes())
        assertTrue(!state.endReached)
    }
}
