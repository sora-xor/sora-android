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
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
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
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatusDetail
import jp.co.soramitsu.feature_blockexplorer_impl.testdata.TestTransactions
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.Sora2NodeRefusal
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionDetails
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingRecoveryPass
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingSubmissionCoordinator
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionDetailsStore
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_data.TestAccounts
import jp.co.soramitsu.test_data.TestTokens
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    @MockK
    private lateinit var detailsStore: Sora2SubmissionDetailsStore

    @MockK
    private lateinit var pendingSubmissionCoordinator: Sora2PendingSubmissionCoordinator

    private lateinit var health: PiIndexerHealth

    private lateinit var validatedHistoryCache: PiValidatedHistoryCache

    private val mockedUri = Mockito.mock(Uri::class.java)

    private val peersList = listOf("cnPeerOne", "cnPeerTwo")

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
        coEvery { detailsStore.list(any()) } returns emptyList()
        coEvery { detailsStore.retainOnly(any(), any(), any()) } returns Unit
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
        coEvery {
            polkaswapIndexerClient.getRecentTransactionPeers(
                TestAccounts.soraAccount.substrateAddress,
            )
        } returns peersList

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
            detailsStore,
            pendingSubmissionCoordinator,
        )
    }

    @Test
    fun `init successful`() = runTest {
        verify { extrinsicManager.setWatchingExtrinsicListener(any()) }
    }

    @Test
    fun `getContacts() filters recent counterparties on the device`() = runTest {
        val account = TestAccounts.soraAccount.substrateAddress

        assertEquals(
            peersList.toSet(),
            transactionHistoryRepository.getContacts(account, ""),
        )
        assertEquals(
            setOf("cnPeerTwo"),
            transactionHistoryRepository.getContacts(account, " TWO "),
        )
        assertEquals(
            emptySet<String>(),
            transactionHistoryRepository.getContacts(account, "nobody"),
        )
        // One indexer read serves the whole search session.
        coVerify(exactly = 1) { polkaswapIndexerClient.getRecentTransactionPeers(account) }
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
            polkaswapIndexerClient.getRecentTransactionPeers("cnBadAccount")
        } throws IllegalStateException("indexer down")

        val result = transactionHistoryRepository.getContacts("cnBadAccount", "bad query")

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

    // --- audit A5: a waiting transaction is visible where the user looks -------------------

    private fun journal(vararg rows: Sora2PendingSubmissionLocal) {
        coEvery {
            walletIdentityDao.getSora2SubmissionOverlayRows(
                TestAccounts.soraAccount.substrateAddress
            )
        } returns rows.toList()
        coEvery {
            polkaswapIndexerClient.getLastTransactionsQualified(
                address = TestAccounts.soraAccount.substrateAddress,
                count = 10,
            )
        } throws IllegalStateException("history unavailable")
    }

    private fun details(vararg entries: Sora2SubmissionDetails) {
        coEvery {
            detailsStore.list(TestAccounts.soraAccount.substrateAddress)
        } returns entries.toList()
    }

    private fun swapDetails(hash: String) = Sora2SubmissionDetails(
        walletId = TestAccounts.soraAccount.substrateAddress,
        transactionHash = hash,
        createdAt = System.currentTimeMillis(),
        activity = Sora2SubmissionActivity.swap(
            tokenFromId = TestTokens.xorToken.id,
            tokenToId = TestTokens.valToken.id,
            amountFrom = java.math.BigDecimal("10"),
            amountTo = java.math.BigDecimal("2.5"),
            fee = java.math.BigDecimal("0.0007"),
            market = "SMART",
        ),
    )

    private suspend fun lastTransactions(filterTokenId: String? = null) =
        transactionHistoryRepository.getLastTransactions(
            TestAccounts.soraAccount,
            listOf(TestTokens.xorToken, TestTokens.valToken, TestTokens.pswapToken),
            10,
            filterTokenId,
        )

    @Test
    fun `a waiting swap keeps its kind tokens and amounts after a restart`() = runTest {
        val hash = "0x" + "a1".repeat(32)
        journal(
            pendingSora2(hash).copy(
                state = Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY,
                submissionIsAmbiguous = false,
            )
        )
        details(swapDetails(hash))

        // Nothing was saved in memory: this is what the journal and the stored details give.
        val swap = lastTransactions().single() as Transaction.Swap

        assertEquals(hash, swap.base.txHash)
        assertEquals(TestTokens.xorToken.id, swap.tokenFrom.id)
        assertEquals(TestTokens.valToken.id, swap.tokenTo.id)
        assertEquals(java.math.BigDecimal("10"), swap.amountFrom)
        assertEquals(java.math.BigDecimal("2.5"), swap.amountTo)
        assertEquals(java.math.BigDecimal("0.0007"), swap.base.fee)
        assertEquals(TransactionStatus.PENDING, swap.base.status)
        assertEquals(
            TransactionStatusDetail.Kind.WAITING_FOR_CONFIRMATION,
            swap.base.statusDetail?.kind,
        )
    }

    @Test
    fun `a waiting swap is listed in the activity of both of its tokens only`() = runTest {
        val hash = "0x" + "a2".repeat(32)
        journal(pendingSora2(hash))
        details(swapDetails(hash))

        assertEquals(hash, lastTransactions(TestTokens.xorToken.id).single().base.txHash)
        assertEquals(hash, lastTransactions(TestTokens.valToken.id).single().base.txHash)
        assertEquals(emptyList<Transaction>(), lastTransactions(TestTokens.pswapToken.id))
    }

    @Test
    fun `a journal row without details stays generic and out of token activity`() = runTest {
        val hash = "0x" + "a3".repeat(32)
        journal(pendingSora2(hash))

        assertEquals(true, lastTransactions().single() is Transaction.Sora2Submission)
        assertEquals(emptyList<Transaction>(), lastTransactions(TestTokens.xorToken.id))
    }

    @Test
    fun `a waiting entry becomes confirmed when the journal row is resolved`() = runTest {
        val hash = "0x" + "a4".repeat(32)
        val waiting = pendingSora2(hash).copy(
            state = Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY,
            submissionIsAmbiguous = false,
        )
        journal(waiting)
        details(swapDetails(hash))
        assertEquals(TransactionStatus.PENDING, lastTransactions().single().base.status)

        // What the background status worker writes.
        journal(
            waiting.copy(
                state = Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS,
                terminalBlockNumber = 70,
                terminalBlockHash = "0x" + "66".repeat(32),
                terminalFinalizedHeight = 72,
            )
        )

        val resolved = lastTransactions().single() as Transaction.Swap
        assertEquals(TransactionStatus.COMMITTED, resolved.base.status)
        assertEquals("0x" + "66".repeat(32), resolved.base.blockHash)
        assertEquals(null, resolved.base.statusDetail)
    }

    @Test
    fun `a transaction the node refused is a failure at once and never pending`() = runTest {
        val hash = "0x" + "a5".repeat(32)
        // The row itself is still unresolved: its validity window has not been scanned yet.
        journal(pendingSora2(hash))
        details(
            swapDetails(hash).copy(
                nodeRefusal = Sora2NodeRefusal(
                    reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                    code = 1010,
                    message = "Inability to pay some fees",
                )
            )
        )

        val refused = lastTransactions().single()

        assertEquals(TransactionStatus.REJECTED, refused.base.status)
        assertEquals(
            TransactionStatusDetail(
                TransactionStatusDetail.Kind.REFUSED_CANNOT_PAY_FEE,
                "Inability to pay some fees",
            ),
            refused.base.statusDetail,
        )
        assertEquals(true, refused.base.statusDetail?.neverOnChain)
    }

    @Test
    fun `chain proof wins over a recorded refusal`() = runTest {
        val hash = "0x" + "a6".repeat(32)
        journal(
            pendingSora2(hash).copy(
                state = Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS,
                submissionIsAmbiguous = false,
                terminalBlockNumber = 70,
                terminalBlockHash = "0x" + "66".repeat(32),
                terminalFinalizedHeight = 72,
            )
        )
        details(
            swapDetails(hash).copy(
                nodeRefusal = Sora2NodeRefusal(TransactionNodeRefusal.NOT_VALID, 1010, null)
            )
        )

        val transaction = lastTransactions().single()

        assertEquals(TransactionStatus.COMMITTED, transaction.base.status)
        assertEquals(null, transaction.base.statusDetail)
    }

    @Test
    fun `a transaction the indexer lists is never called failed because of a recorded refusal`() =
        runTest {
            // The indexer lists it, so it is in a block, although the node answered the
            // submission with a refusal and the journal row has not been resolved yet.
            val hash = TestTransactions.txHistoryItem.id.lowercase()
            coEvery {
                walletIdentityDao.getSora2SubmissionOverlayRows(
                    TestAccounts.soraAccount.substrateAddress
                )
            } returns listOf(pendingSora2(hash))
            details(
                swapDetails(hash).copy(
                    nodeRefusal = Sora2NodeRefusal(TransactionNodeRefusal.NOT_VALID, 1010, null)
                )
            )

            val listed = transactionHistoryRepository.getLastTransactions(
                TestAccounts.soraAccount,
                listOf(TestTokens.xorToken, TestTokens.valToken),
                1,
                null,
            ).single()

            // Before: REJECTED with the refusal as its reason.
            assertEquals(TransactionStatus.PENDING, listed.base.status)
            assertEquals(
                TransactionStatusDetail(TransactionStatusDetail.Kind.WAITING_FOR_CONFIRMATION),
                listed.base.statusDetail,
            )
        }

    @Test
    fun `a transaction that failed on chain carries the reported runtime error`() = runTest {
        val hash = "0x" + "a7".repeat(32)
        journal(
            pendingSora2(hash).copy(
                state = Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE,
                submissionIsAmbiguous = false,
                terminalBlockNumber = 70,
                terminalBlockHash = "0x" + "66".repeat(32),
                terminalFinalizedHeight = 72,
            )
        )
        details(swapDetails(hash).copy(chainFailure = "LiquidityProxy.SlippageNotTolerated"))

        val failed = lastTransactions().single()

        assertEquals(TransactionStatus.REJECTED, failed.base.status)
        assertEquals(
            TransactionStatusDetail(
                TransactionStatusDetail.Kind.FAILED_ON_CHAIN,
                "LiquidityProxy.SlippageNotTolerated",
            ),
            failed.base.statusDetail,
        )
    }

    @Test
    fun `an expired transaction says it was never included`() = runTest {
        val hash = "0x" + "a8".repeat(32)
        journal(
            pendingSora2(hash).copy(
                state = Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED,
                submissionIsAmbiguous = false,
                terminalFinalizedHeight = 130,
            )
        )
        details(swapDetails(hash))

        val expired = lastTransactions().single()

        assertEquals(TransactionStatus.REJECTED, expired.base.status)
        assertEquals(
            TransactionStatusDetail.Kind.NOT_INCLUDED,
            expired.base.statusDetail?.kind,
        )
    }

    @Test
    fun `a screen can ask for the status of waiting transactions without waiting for the worker`() =
        runTest {
            coEvery {
                pendingSubmissionCoordinator.recoverStatusOnly(any(), any())
            } returns Sora2PendingRecoveryPass(1, 1, 0, 0, 0)

            transactionHistoryRepository.checkWaitingTransactions()

            // The status-only pass of the coordinator: it has no signer and cannot submit.
            coVerify(exactly = 1) { pendingSubmissionCoordinator.recoverStatusOnly(any(), any()) }
        }

    @Test
    fun `a failed status check is never an error for the screen`() = runTest {
        coEvery {
            pendingSubmissionCoordinator.recoverStatusOnly(any(), any())
        } throws java.io.IOException("BOUNDED_HTTP_IO")

        transactionHistoryRepository.checkWaitingTransactions()

        coVerify(exactly = 1) { pendingSubmissionCoordinator.recoverStatusOnly(any(), any()) }
    }

    @Test
    fun `a write to the status journal refreshes the listening screens`() = runTest {
        // Room emits the current value first and then once per write to the table.
        every { walletIdentityDao.observeSora2PendingSubmissionChanges() } returns
            kotlinx.coroutines.flow.flowOf(1, 1)

        assertEquals(true, transactionHistoryRepository.state.first())
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
