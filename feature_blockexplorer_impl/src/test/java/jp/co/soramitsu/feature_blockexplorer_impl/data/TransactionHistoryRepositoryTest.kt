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
import jp.co.soramitsu.xnetworking.sorawallet.txhistory.client.SubQueryClientForSoraWallet
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
