/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.data

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkStatic
import io.mockk.verify
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_impl.testdata.TestTransactions
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWallet
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class TransactionHistoryRepositoryTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)


    @MockK
    private lateinit var subQueryClient: SubQueryClientForSoraWallet

    @MockK
    private lateinit var extrinsicManager: ExtrinsicManager

    private val mockedUri = Mockito.mock(Uri::class.java)

    private val peersList = listOf("1", "2")

    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @Before
    fun setUp() = runTest {
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
        every { extrinsicManager.setWatchingExtrinsicListener(any()) } returns Unit
        every { subQueryClient.getTransactionPeers("query") } returns peersList
        coEvery { subQueryClient.getTransactionHistoryCached(TestAccounts.soraAccount.substrateAddress, 1, null) } returns listOf(TestTransactions.txHistoryItem)

        transactionHistoryRepository = TransactionHistoryRepositoryImpl(
            subQueryClient,
            extrinsicManager
        )
    }

    @Test
    fun `init successful`() = runTest {
        verify { extrinsicManager.setWatchingExtrinsicListener(any()) }
    }

    @Test
    fun `getContacts() called`() = runTest {
        val result = transactionHistoryRepository.getContacts("query")

        assertEquals(result, peersList.toSet())
    }

    @Test
    fun `getLastTransaction() called`() = runTest {
        val result = transactionHistoryRepository.getLastTransactions(TestAccounts.soraAccount, listOf(TestTokens.xorToken, TestTokens.valToken), 1, null)

        TestTransactions.txHistoryTransaction.let { expected ->
            (result.first() as Transaction.Liquidity).let { res ->
                assertEquals(expected.base.txHash, res.base.txHash)
                assertEquals(expected.base.blockHash, res.base.blockHash)
                assertEquals(expected.base.fee, res.base.fee)
                assertEquals(expected.base.status, res.base.status)
                assertEquals(expected.base.timestamp, res.base.timestamp)
                assertEquals(expected.amount1, res.amount1)
                assertEquals(expected.amount2, res.amount2)
                assertEquals(expected.type, res.type)
                assertEquals(expected.token1, res.token1)
                assertEquals(expected.token2, res.token2)
            }
        }
    }
}