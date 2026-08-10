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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import java.net.UnknownHostException
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.PiIndexerHealth
import jp.co.soramitsu.feature_blockexplorer_api.data.PiQualifiedRead
import jp.co.soramitsu.feature_blockexplorer_api.data.PiValidatedHistoryCache
import jp.co.soramitsu.feature_blockexplorer_api.data.PiValidatedHistoryRecord
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkaswapIndexerClient
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_impl.testdata.TestTransactions
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
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
    private lateinit var polkaswapIndexerClient: PolkaswapIndexerClient

    @MockK
    private lateinit var extrinsicManager: ExtrinsicManager

    @MockK
    private lateinit var substrateCalls: SubstrateCalls

    @MockK
    private lateinit var database: AppDatabase

    @MockK
    private lateinit var walletIdentityDao: WalletIdentityDao

    private lateinit var health: PiIndexerHealth

    private lateinit var validatedHistoryCache: PiValidatedHistoryCache

    private val mockedUri = Mockito.mock(Uri::class.java)

    private val peersList = listOf("1", "2")

    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    private lateinit var watchingListener: ExtrinsicManager.WatchingListener

    @Before
    fun setUp() = runTest {
        health = mockk()
        validatedHistoryCache = mockk(relaxed = true)
        mockkStatic(Uri::parse)
        mockkObject(FirebaseWrapper)
        every { Uri.parse(any()) } returns mockedUri
        every { FirebaseWrapper.recordErrorClass(any()) } returns Unit
        every { database.walletIdentityDao() } returns walletIdentityDao
        coEvery {
            walletIdentityDao.getSora2SubmissionOverlayRows(
                TestAccounts.soraAccount.substrateAddress
            )
        } returns emptyList()
        every { health.latestIndexedBlock } returns null
        every { health.latestIndexedBlockHash } returns null
        every { health.workerLatestIndexedBlock } returns "100"
        every { health.workerLatestFinalizedBlock } returns "104"
        coEvery {
            substrateCalls.getBlockHash(100)
        } returns TestTransactions.txHistoryItem.blockHash!!
        every {
            extrinsicManager.setWatchingExtrinsicListener(
                listener = any()
            )
        } answers {
            watchingListener = firstArg()
        }
        coEvery { polkaswapIndexerClient.getTransactionPeers("query") } returns peersList.toSet()

        coEvery {
            polkaswapIndexerClient.getLastTransactionsQualified(
                address = TestAccounts.soraAccount.substrateAddress,
                count = 1,
            )
        } returns PiQualifiedRead(
            value = listOf(TestTransactions.txHistoryItem),
            health = health,
            fromCache = false,
        )

        transactionHistoryRepository = TransactionHistoryRepositoryImpl(
            polkaswapIndexerClient,
            extrinsicManager,
            substrateCalls,
            validatedHistoryCache,
            database,
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

    @Test
    fun `getLastTransactions rejects a PI record for another account`() = runTest {
        coEvery {
            polkaswapIndexerClient.getLastTransactionsQualified(
                address = TestAccounts.soraAccount.substrateAddress,
                count = 1,
            )
        } returns PiQualifiedRead(
            value = listOf(
                TestTransactions.txHistoryItem.copy(
                    address = "cnWrongAccount",
                    dataFrom = null,
                    dataTo = null,
                )
            ),
            health = health,
            fromCache = false,
        )

        val result = transactionHistoryRepository.getLastTransactions(
            TestAccounts.soraAccount,
            listOf(TestTokens.xorToken, TestTokens.valToken),
            1,
            null,
        )

        assertEquals(emptyList<Transaction>(), result)
        verify {
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_HISTORY
            )
        }
    }

    @Test
    fun `getContacts returns empty set when indexer fails`() = runTest {
        coEvery {
            polkaswapIndexerClient.getTransactionPeers("bad query")
        } throws IllegalStateException("indexer down")

        val result = transactionHistoryRepository.getContacts("bad query")

        assertEquals(emptySet<String>(), result)
        verify {
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_PEERS
            )
        }
    }

    @Test
    fun `getLastTransactions keeps local pending transactions when indexer fails`() = runTest {
        coEvery {
            polkaswapIndexerClient.getLastTransactionsQualified(
                address = TestAccounts.soraAccount.substrateAddress,
                count = 10,
            )
        } throws IllegalStateException("history unavailable")
        transactionHistoryRepository.saveTransaction(
            TestAccounts.soraAccount.substrateAddress,
            TestTransactions.sendSuccessfulTx,
        )

        val result = transactionHistoryRepository.getLastTransactions(
            TestAccounts.soraAccount,
            listOf(TestTokens.xorToken, TestTokens.valToken),
            10,
            null,
        )

        assertEquals(1, result.size)
        assertEquals(TestTransactions.sendSuccessfulTx.base.txHash, result.single().base.txHash)
        verify {
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_HISTORY
            )
        }
    }

    @Test
    fun `local pending overlay is isolated to its captured wallet`() = runTest {
        val otherAccount = SoraAccount("other-wallet", "Other")
        coEvery {
            polkaswapIndexerClient.getLastTransactionsQualified(
                address = otherAccount.substrateAddress,
                count = 10,
            )
        } throws IllegalStateException("history unavailable")
        coEvery {
            walletIdentityDao.getSora2SubmissionOverlayRows(
                otherAccount.substrateAddress
            )
        } returns emptyList()
        transactionHistoryRepository.saveTransaction(
            TestAccounts.soraAccount.substrateAddress,
            TestTransactions.sendSuccessfulTx,
        )

        val otherWalletHistory = transactionHistoryRepository.getLastTransactions(
            otherAccount,
            listOf(TestTokens.xorToken, TestTokens.valToken),
            10,
            null,
        )

        assertEquals(emptyList<Transaction>(), otherWalletHistory)
    }

    @Test
    fun `local pending overlay rejects malformed and noncanonical hashes`() {
        val invalidHashes = listOf(
            "",
            "0x" + "ab".repeat(31),
            "0x" + "ab".repeat(33),
            "ab".repeat(32),
            "0X" + "ab".repeat(32),
            "0x" + "AB".repeat(32),
            "0x" + "00".repeat(32),
            "0x" + "ag".repeat(32),
            "0x" + "\uFF11".repeat(64),
            " 0x" + "ab".repeat(32),
            "0x" + "ab".repeat(32) + " ",
        )

        invalidHashes.forEach { hash ->
            val error = runCatching {
                transactionHistoryRepository.saveTransaction(
                    TestAccounts.soraAccount.substrateAddress,
                    pendingTransfer(hash),
                )
            }.exceptionOrNull()

            assertEquals("SORA2_PENDING_OVERLAY_HASH_INVALID", error?.message)
        }
    }

    @Test
    fun `local pending overlay matches status only by canonical hash`() {
        val hash = "0x" + "ab".repeat(32)
        val pending = pendingTransfer(hash)
        transactionHistoryRepository.saveTransaction(
            TestAccounts.soraAccount.substrateAddress,
            pending,
        )

        watchingListener.onChange("0x" + "AB".repeat(32), true, "wrong-block")
        assertEquals(TransactionStatus.PENDING, pending.base.status)
        assertEquals(null, pending.base.blockHash)

        watchingListener.onChange(hash, true, "canonical-block")
        assertEquals(TransactionStatus.COMMITTED, pending.base.status)
        assertEquals("canonical-block", pending.base.blockHash)
    }

    @Test
    fun `getLastTransactions restores generic SORA2 pending journal after restart`() = runTest {
        val hash = "0x" + "ab".repeat(32)
        coEvery {
            polkaswapIndexerClient.getLastTransactionsQualified(
                address = TestAccounts.soraAccount.substrateAddress,
                count = 10,
            )
        } throws IllegalStateException("history unavailable")
        coEvery {
            walletIdentityDao.getSora2SubmissionOverlayRows(
                TestAccounts.soraAccount.substrateAddress
            )
        } returns listOf(
            Sora2PendingSubmissionLocal(
                localId = "sora2:${hash.removePrefix("0x")}",
                recoverySchemaVersion = Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION,
                walletId = TestAccounts.soraAccount.substrateAddress,
                networkId = Sora2PendingSubmissionLocal.NETWORK_SORA2,
                transactionHash = hash,
                accountId = "11".repeat(32),
                publicKey = "11".repeat(32),
                genesisHash = "0x" + "22".repeat(32),
                specVersion = 130,
                transactionVersion = 130,
                metadataSha256 = "33".repeat(32),
                typesSha256 = "44".repeat(32),
                eraBirthBlock = 64,
                eraDeathBlockExclusive = 128,
                eraPeriod = 64,
                eraPhase = 0,
                eraBirthBlockHash = "0x" + "55".repeat(32),
                operationKind = Sora2PendingSubmissionLocal.OPERATION_GENERIC,
                state = Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN,
                submissionIsAmbiguous = true,
                terminalBlockNumber = null,
                terminalBlockHash = null,
                terminalFinalizedHeight = null,
                createdAt = 1,
                updatedAt = 2,
            )
        )

        val result = transactionHistoryRepository.getLastTransactions(
            TestAccounts.soraAccount,
            listOf(TestTokens.xorToken, TestTokens.valToken),
            10,
            null,
        )

        val restored = result.single() as Transaction.Sora2Submission
        assertEquals(hash, restored.base.txHash)
        assertEquals(
            jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus.PENDING,
            restored.base.status,
        )
        assertEquals(true, restored.submissionIsAmbiguous)
    }

    @Test
    fun `pre arm crash witness never claims that submission happened`() = runTest {
        val hash = "0x" + "ad".repeat(32)
        coEvery {
            polkaswapIndexerClient.getLastTransactionsQualified(
                address = TestAccounts.soraAccount.substrateAddress,
                count = 10,
            )
        } throws IllegalStateException("history unavailable")
        coEvery {
            walletIdentityDao.getSora2SubmissionOverlayRows(
                TestAccounts.soraAccount.substrateAddress
            )
        } returns listOf(
            pendingSora2(hash).copy(
                state = Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT,
                submissionIsAmbiguous = false,
            )
        )

        val result = transactionHistoryRepository.getLastTransactions(
            TestAccounts.soraAccount,
            listOf(TestTokens.xorToken, TestTokens.valToken),
            10,
            null,
        )

        val restored = result.single() as Transaction.Sora2Submission
        assertEquals(hash, restored.base.txHash)
        assertEquals(true, restored.submissionIsAmbiguous)
    }

    @Test
    fun `exact PI hash suppresses generic overlay without deleting journal evidence`() = runTest {
        val hash = "0x" + "ac".repeat(32)
        coEvery {
            polkaswapIndexerClient.getLastTransactionsQualified(
                address = TestAccounts.soraAccount.substrateAddress,
                count = 10,
            )
        } returns PiQualifiedRead(
            value = listOf(
                TestTransactions.txHistoryItem.copy(
                    id = hash.uppercase(),
                    module = "UnknownRuntimeMutation",
                    method = "unknown",
                )
            ),
            health = health,
            fromCache = false,
        )
        coEvery {
            walletIdentityDao.getSora2SubmissionOverlayRows(
                TestAccounts.soraAccount.substrateAddress
            )
        } returns listOf(pendingSora2(hash))

        val result = transactionHistoryRepository.getLastTransactions(
            TestAccounts.soraAccount,
            listOf(TestTokens.xorToken, TestTokens.valToken),
            10,
            null,
        )

        assertEquals(emptyList<Transaction>(), result)
        coVerify(exactly = 1) {
            walletIdentityDao.getSora2SubmissionOverlayRows(
                TestAccounts.soraAccount.substrateAddress
            )
        }
    }

    @Test
    fun `exact durable witness overrides PI execution status`() = runTest {
        val hash = TestTransactions.txHistoryItem.id.lowercase()
        coEvery {
            walletIdentityDao.getSora2SubmissionOverlayRows(
                TestAccounts.soraAccount.substrateAddress
            )
        } returns listOf(
            pendingSora2(hash).copy(
                state = Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE,
                submissionIsAmbiguous = false,
                terminalBlockNumber = 64,
                terminalBlockHash = "0x" + "66".repeat(32),
                terminalFinalizedHeight = 65,
            )
        )

        val result = transactionHistoryRepository.getLastTransactions(
            TestAccounts.soraAccount,
            listOf(TestTokens.xorToken, TestTokens.valToken),
            1,
            null,
        )

        assertEquals(1, result.size)
        assertEquals(
            jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus.REJECTED,
            result.single().base.status,
        )
        assertEquals("0x" + "66".repeat(32), result.single().base.blockHash)
    }

    @Test
    fun `old terminal generic witness does not crowd newer PI history`() = runTest {
        val oldHash = "0x" + "ae".repeat(32)
        coEvery {
            walletIdentityDao.getSora2SubmissionOverlayRows(
                TestAccounts.soraAccount.substrateAddress
            )
        } returns listOf(
            pendingSora2(oldHash).copy(
                state = Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS,
                submissionIsAmbiguous = false,
                terminalBlockNumber = 64,
                terminalBlockHash = "0x" + "67".repeat(32),
                terminalFinalizedHeight = 65,
                createdAt = 1,
                updatedAt = 2,
            )
        )

        val result = transactionHistoryRepository.getLastTransactions(
            TestAccounts.soraAccount,
            listOf(TestTokens.xorToken, TestTokens.valToken),
            1,
            null,
        )

        assertEquals(1, result.size)
        assertEquals(
            TestTransactions.txHistoryTransaction.base.txHash,
            result.single().base.txHash,
        )
    }

    @Test
    fun `getLastTransactions uses only previously canonical validated cache when fully offline`() =
        runTest {
            coEvery {
                polkaswapIndexerClient.getLastTransactionsQualified(
                    address = TestAccounts.soraAccount.substrateAddress,
                    count = 1,
                )
            } throws UnknownHostException("offline")
            coEvery {
                validatedHistoryCache.readLast(
                    address = TestAccounts.soraAccount.substrateAddress,
                    count = 1,
                )
            } returns PiValidatedHistoryRecord(
                items = listOf(TestTransactions.txHistoryItem),
                health = health,
            )

            val result = transactionHistoryRepository.getLastTransactions(
                TestAccounts.soraAccount,
                listOf(TestTokens.xorToken, TestTokens.valToken),
                1,
                null,
            )

            assertEquals(
                TestTransactions.txHistoryTransaction.base.txHash,
                result.single().base.txHash,
            )
            coVerify(exactly = 0) { substrateCalls.getBlockHash(any()) }
        }

    @Test
    fun `raw PI cache is replaced by separately canonical validated history without RPC`() =
        runTest {
            coEvery {
                polkaswapIndexerClient.getLastTransactionsQualified(
                    address = TestAccounts.soraAccount.substrateAddress,
                    count = 1,
                )
            } returns PiQualifiedRead(
                value = emptyList(),
                health = health,
                fromCache = true,
            )
            coEvery {
                validatedHistoryCache.readLast(
                    address = TestAccounts.soraAccount.substrateAddress,
                    count = 1,
                )
            } returns PiValidatedHistoryRecord(
                items = listOf(TestTransactions.txHistoryItem),
                health = health,
            )

            val result = transactionHistoryRepository.getLastTransactions(
                TestAccounts.soraAccount,
                listOf(TestTokens.xorToken, TestTokens.valToken),
                1,
                null,
            )

            assertEquals(
                TestTransactions.txHistoryTransaction.base.txHash,
                result.single().base.txHash,
            )
            coVerify(exactly = 0) { substrateCalls.getBlockHash(any()) }
        }

    @Test
    fun `getTransaction falls back to local pending transaction when indexer fails`() = runTest {
        coEvery {
            polkaswapIndexerClient.getTransactionQualified(
                TestTransactions.sendSuccessfulTx.base.txHash
            )
        } throws IllegalStateException("tx unavailable")
        transactionHistoryRepository.saveTransaction(
            TestAccounts.soraAccount.substrateAddress,
            TestTransactions.sendSuccessfulTx,
        )

        val result = transactionHistoryRepository.getTransaction(
            TestTransactions.sendSuccessfulTx.base.txHash,
            listOf(TestTokens.xorToken, TestTokens.valToken),
            TestAccounts.soraAccount,
        )

        assertEquals(TestTransactions.sendSuccessfulTx.base.txHash, result?.base?.txHash)
        verify {
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_DETAIL
            )
        }
    }

    @Test
    fun `getTransactionHistory returns first page pending transactions and error message when indexer fails`() = runTest {
        coEvery {
            polkaswapIndexerClient.getTransactionHistoryQualified(
                address = TestAccounts.soraAccount.substrateAddress,
                page = 1,
                pageCount = 100,
            )
        } throws IllegalStateException("history unavailable")
        transactionHistoryRepository.saveTransaction(
            TestAccounts.soraAccount.substrateAddress,
            TestTransactions.sendSuccessfulTx,
        )

        val result = transactionHistoryRepository.getTransactionHistory(
            page = 1,
            tokens = listOf(TestTokens.xorToken, TestTokens.valToken),
            soraAccount = TestAccounts.soraAccount,
            filterTokenId = null,
        )

        assertEquals(true, result.endReached)
        assertEquals("history unavailable", result.errorMessage)
        assertEquals(TestTransactions.sendSuccessfulTx.base.txHash, result.transactions.single().base.txHash)
        verify {
            FirebaseWrapper.recordErrorClass(
                FirebaseWrapper.PrivacySafeErrorClass.PI_TRANSACTION_HISTORY
            )
        }
    }

    private fun pendingSora2(hash: String) = Sora2PendingSubmissionLocal(
        localId = "sora2:${hash.removePrefix("0x")}",
        recoverySchemaVersion = Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION,
        walletId = TestAccounts.soraAccount.substrateAddress,
        networkId = Sora2PendingSubmissionLocal.NETWORK_SORA2,
        transactionHash = hash,
        accountId = "11".repeat(32),
        publicKey = "11".repeat(32),
        genesisHash = "0x" + "22".repeat(32),
        specVersion = 130,
        transactionVersion = 130,
        metadataSha256 = "33".repeat(32),
        typesSha256 = "44".repeat(32),
        eraBirthBlock = 64,
        eraDeathBlockExclusive = 128,
        eraPeriod = 64,
        eraPhase = 0,
        eraBirthBlockHash = "0x" + "55".repeat(32),
        operationKind = Sora2PendingSubmissionLocal.OPERATION_GENERIC,
        state = Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN,
        submissionIsAmbiguous = true,
        terminalBlockNumber = null,
        terminalBlockHash = null,
        terminalFinalizedHeight = null,
        createdAt = 1,
        updatedAt = 2,
    )

    private fun pendingTransfer(hash: String): Transaction.Transfer {
        val fixture = TestTransactions.sendSuccessfulTx
        return Transaction.Transfer(
            base = TransactionBase(
                txHash = hash,
                blockHash = null,
                fee = fixture.base.fee,
                status = TransactionStatus.PENDING,
                timestamp = fixture.base.timestamp,
            ),
            amount = fixture.amount,
            peer = fixture.peer,
            transferType = fixture.transferType,
            token = fixture.token,
        )
    }
}
