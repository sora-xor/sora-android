package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import io.mockk.*
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.nexus.WalletNetworkChainIdentity
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class PolkamarktRecoveryBoundaryTest {
    private val dao = mockk<WalletIdentityDao>(relaxed = true)
    private val users = mockk<UserRepository>()
    private val database = mockk<AppDatabase> { every { walletIdentityDao() } returns dao }
    private val repo = PolkamarktTraderRepository(mockk(), mockk(), mockk(), mockk(), mockk(), users, mockk(), database, mockk())
    private val hash = "0x" + "ab".repeat(32)
    private fun row() = PendingNetworkTransactionLocal("fixture-pending", "wallet", "sora2", WalletNetworkChainIdentity.SORA2,
        hash, "polkamarkt:42:BUY", "100", "Yes", "UNKNOWN", true, 1, 2)

    @Test fun `observed pending rows preserve operation hash and ambiguity`() = runTest {
        every { dao.observePendingTransactions("wallet") } returns flowOf(listOf(row()))
        val result = repo.observePending("wallet").first().single()
        assertEquals("BUY", result.operation); assertEquals(hash, result.transactionHash)
        assertEquals("100", result.amount); assertEquals("UNKNOWN", result.state); assertTrue(result.submissionIsAmbiguous)
    }

    @Test fun `retained rows without chain proof remain explicit recovery requirements`() = runTest {
        every { dao.observePendingTransactions("wallet") } returns flowOf(listOf(row().copy(chainId = null, transactionHash = null)))
        val result = repo.observePending("wallet").first().single()
        assertEquals("CHAIN_IDENTITY_RECOVERY_REQUIRED", result.state); assertTrue(result.submissionIsAmbiguous)
        assertNull(result.transactionHash)
    }

    @Test fun `observed rows from another network are ignored but corrupt market identity fails`() = runTest {
        every { dao.observePendingTransactions("wallet") } returns flowOf(listOf(row().copy(networkId = "minamoto", chainId = null)))
        assertTrue(repo.observePending("wallet").first().isEmpty())
        every { dao.observePendingTransactions("wallet") } returns flowOf(listOf(row().copy(assetId = "invalid", chainId = null)))
        assertEquals("POLKAMARKT_PENDING_IDENTITY_INVALID", failure { repo.observePending("wallet").first() }.message)
    }

    @Test fun `versioned signed witness is rejected locally without broadcast`() = runTest {
        val pending = row().copy(localId = PolkamarktPendingRecoveryPlanner.newLocalId(), state = "SIGNED", submissionIsAmbiguous = false)
        coEvery { users.getCurSoraAccount() } returns SoraAccount("wallet", "Fixture")
        coEvery { dao.getUnresolvedTransactions() } returns listOf(pending)
        assertEquals(listOf(PolkamarktMutationResult(pending.localId, hash, "REJECTED")), repo.recoverPendingTransactions("wallet"))
        coVerify(exactly = 1) { dao.updatePendingTransaction(pending.localId, hash, "REJECTED", false, any()) }
        coVerify(exactly = 0) { dao.getSora2PendingSubmission(any()) }
    }

    @Test fun `recovery rejects changed account duplicate identities and missing chain proof`() = runTest {
        coEvery { users.getCurSoraAccount() } returns SoraAccount("other", "Other")
        assertEquals("POLKAMARKT_ACCOUNT_CHANGED", failure { repo.recoverPendingTransactions("wallet") }.message)
        coEvery { users.getCurSoraAccount() } returns SoraAccount("wallet", "Fixture")
        for ((rows, code) in listOf(listOf(row(), row()) to "POLKAMARKT_DUPLICATE_PENDING_ID",
            listOf(row(), row().copy(localId = "other")) to "POLKAMARKT_DUPLICATE_PENDING_HASH",
            listOf(row().copy(chainId = null)) to "POLKAMARKT_PENDING_CHAIN_IDENTITY_RECOVERY_REQUIRED")) {
            coEvery { dao.getUnresolvedTransactions() } returns rows
            assertEquals(code, failure { repo.recoverPendingTransactions("wallet") }.message)
        }
        coVerify(exactly = 0) { dao.updatePendingTransaction(any(), any(), any(), any(), any()) }
    }

    @Test fun `empty and unrelated recovery inventories require no witness lookup`() = runTest {
        coEvery { users.getCurSoraAccount() } returns SoraAccount("wallet", "Fixture")
        coEvery { dao.getUnresolvedTransactions() } returns listOf(row().copy(walletId = "other"), row().copy(networkId = "taira"), row().copy(assetId = "non-market"))
        assertTrue(repo.recoverPendingTransactions("wallet").isEmpty())
        coVerify(exactly = 0) { dao.getSora2PendingSubmission(any()) }
    }

    private suspend fun failure(block: suspend () -> Any?): IllegalStateException {
        try { block() } catch (e: IllegalStateException) { return e }
        throw AssertionError("Expected invalid recovery evidence to be rejected")
    }
}
