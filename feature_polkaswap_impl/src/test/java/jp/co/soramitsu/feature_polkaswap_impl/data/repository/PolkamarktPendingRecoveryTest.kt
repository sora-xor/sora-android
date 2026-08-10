package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.nexus.WalletNetworkChainIdentity
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktCatalogCache
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkaswapIndexerClient
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.canonicalExtrinsicHash
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PolkamarktPendingRecoveryTest {

    @Test
    fun `restart reconciliation finalizes by exact hash without resubmitting`() = runTest {
        val indexer = mockk<PolkaswapIndexerClient>()
        val calls = mockk<SubstrateCalls>()
        val extrinsicManager = mockk<ExtrinsicManager>()
        val runtimeContract = mockk<Sora2RuntimeContract>()
        val credentials = mockk<CredentialsRepository>()
        val userRepository = mockk<UserRepository>()
        val featureManager = mockk<ProductionFeatureManager>()
        val database = mockk<AppDatabase>()
        val dao = mockk<WalletIdentityDao>()
        val cache = mockk<PolkamarktCatalogCache>()
        val hash = "0x" + "ab".repeat(32)
        val indexedBlockHash = "0x" + "cd".repeat(32)
        val pending = PendingNetworkTransactionLocal(
            localId = "local",
            walletId = WALLET_ID,
            networkId = "sora2",
            chainId = WalletNetworkChainIdentity.SORA2,
            transactionHash = hash,
            assetId = "polkamarkt:7:BUY",
            amount = "100",
            recipient = "Yes",
            state = "UNKNOWN",
            submissionIsAmbiguous = true,
            createdAt = 1,
            updatedAt = 2,
        )
        val witness = Sora2PendingSubmissionLocal(
            localId = "sora2:${hash.removePrefix("0x")}",
            recoverySchemaVersion = Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION,
            walletId = WALLET_ID,
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
            operationKind = Sora2PendingSubmissionLocal.OPERATION_POLKAMARKT,
            state = Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS,
            submissionIsAmbiguous = false,
            terminalBlockNumber = 100,
            terminalBlockHash = indexedBlockHash,
            terminalFinalizedHeight = 101,
            createdAt = 1,
            updatedAt = 2,
        )

        every { database.walletIdentityDao() } returns dao
        coEvery { userRepository.getCurSoraAccount() } returns SoraAccount(WALLET_ID, "Wallet")
        coEvery { dao.getUnresolvedTransactions() } returns listOf(pending)
        coEvery { dao.getSora2PendingSubmission(witness.localId) } returns witness
        coEvery {
            dao.updatePendingTransaction(
                localId = "local",
                transactionHash = hash,
                state = "FINALIZED",
                ambiguous = false,
                updatedAt = any(),
            )
        } returns Unit

        val repository = PolkamarktTraderRepository(
            indexer = indexer,
            calls = calls,
            extrinsicManager = extrinsicManager,
            runtimeContract = runtimeContract,
            credentialsRepository = credentials,
            userRepository = userRepository,
            featureManager = featureManager,
            database = database,
            catalogCache = cache,
        )

        val recovered = repository.recoverPendingTransactions(WALLET_ID)

        assertEquals("FINALIZED", recovered.single().state)
        coVerify(exactly = 0) {
            extrinsicManager.submitPreparedAndWait(any(), any())
        }
        coVerify(exactly = 0) { calls.getFinalizedHead() }
    }

    @Test
    fun `legacy pending row without exact generic witness remains unresolved`() = runTest {
        val indexer = mockk<PolkaswapIndexerClient>()
        val calls = mockk<SubstrateCalls>()
        val extrinsicManager = mockk<ExtrinsicManager>()
        val runtimeContract = mockk<Sora2RuntimeContract>()
        val userRepository = mockk<UserRepository>()
        val database = mockk<AppDatabase>()
        val dao = mockk<WalletIdentityDao>()
        val row = pending(localId = "legacy", hashByte = 7).copy(
            state = "UNKNOWN",
            submissionIsAmbiguous = true,
        )
        every { database.walletIdentityDao() } returns dao
        coEvery { userRepository.getCurSoraAccount() } returns SoraAccount(WALLET_ID, "Wallet")
        coEvery { dao.getUnresolvedTransactions() } returns listOf(row)
        coEvery {
            dao.getSora2PendingSubmission(
                "sora2:${checkNotNull(row.transactionHash).removePrefix("0x")}"
            )
        } returns null
        val repository = PolkamarktTraderRepository(
            indexer = indexer,
            calls = calls,
            extrinsicManager = extrinsicManager,
            runtimeContract = runtimeContract,
            credentialsRepository = mockk<CredentialsRepository>(),
            userRepository = userRepository,
            featureManager = mockk<ProductionFeatureManager>(),
            database = database,
            catalogCache = mockk<PolkamarktCatalogCache>(),
        )

        assertEquals(emptyList<PolkamarktMutationResult>(), repository.recoverPendingTransactions(WALLET_ID))
        coVerify(exactly = 0) {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) { calls.getFinalizedHead() }
        coVerify(exactly = 0) {
            indexer.getAccountTransactionsByHashesQualified(any(), any())
        }
    }

    @Test
    fun `transaction hashes require exactly 32 bytes`() {
        assertEquals("0x${"ab".repeat(32)}", "0X${"AB".repeat(32)}".canonicalExtrinsicHash())
        assertThrows(IllegalArgumentException::class.java) {
            "0x${"ab".repeat(31)}".canonicalExtrinsicHash()
        }
        assertThrows(IllegalArgumentException::class.java) {
            " 0x${"ab".repeat(32)}".canonicalExtrinsicHash()
        }
        assertThrows(IllegalArgumentException::class.java) {
            "0x${"0".repeat(64)}".canonicalExtrinsicHash()
        }
    }

    @Test
    fun `recovery planner visits every retained row in bounded batches`() {
        val rows = (0 until 121).map { index ->
            pending(
                localId = "legacy-$index",
                hashByte = (index % 254) + 1,
            )
        }

        val batches = PolkamarktPendingRecoveryPlanner.batches(rows, 50)

        assertEquals(listOf(50, 50, 21), batches.map { it.size })
        assertEquals(rows.map { it.localId }, batches.flatten().map { it.localId })
        assertThrows(IllegalArgumentException::class.java) {
            PolkamarktPendingRecoveryPlanner.batches(rows, 51)
        }
    }

    @Test
    fun `only versioned pre transport signed rows are definitively unsubmitted`() {
        val versionedId = PolkamarktPendingRecoveryPlanner.newLocalId()
        val versioned = pending(versionedId, 1).copy(
            state = "SIGNED",
            submissionIsAmbiguous = false,
        )

        assertTrue(
            PolkamarktPendingRecoveryPlanner.wasDefinitelyNeverSubmitted(versioned),
        )
        assertFalse(
            PolkamarktPendingRecoveryPlanner.wasDefinitelyNeverSubmitted(
                versioned.copy(localId = "legacy"),
            ),
        )
        assertFalse(
            PolkamarktPendingRecoveryPlanner.wasDefinitelyNeverSubmitted(
                versioned.copy(
                    state = "SUBMITTED",
                    submissionIsAmbiguous = true,
                ),
            ),
        )
    }

    private fun pending(
        localId: String,
        hashByte: Int,
    ) = PendingNetworkTransactionLocal(
        localId = localId,
        walletId = WALLET_ID,
        networkId = "sora2",
        chainId = WalletNetworkChainIdentity.SORA2,
        transactionHash = "0x" + hashByte.toString(16).padStart(2, '0').repeat(32),
        assetId = "polkamarkt:7:BUY",
        amount = "100",
        recipient = "Yes",
        state = "UNKNOWN",
        submissionIsAmbiguous = true,
        createdAt = hashByte.toLong(),
        updatedAt = hashByte.toLong(),
    )

    private companion object {
        const val WALLET_ID = "cnExistingWallet"
    }
}
