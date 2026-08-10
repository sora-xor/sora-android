package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.nexus.WalletNetworkChainIdentity
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktCatalogCache
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkaswapIndexerClient
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureState
import jp.co.soramitsu.sora.substrate.runtime.QualifiedSora2MutationRuntime
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.runtime.VerifiedSora2Runtime
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PolkamarktPreTransportGateTest {

    @Test
    fun `unchanged account flags runtime and deletion state open transport`() = runTest {
        val fixture = fixture()

        fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)

        coVerify(exactly = 1) { fixture.userRepository.getCurSoraAccount() }
        coVerify(exactly = 1) { fixture.featureManager.getState() }
        coVerify(exactly = 1) {
            fixture.runtimeContract.requirePolkamarktMutationRuntimeUnchanged(
                fixture.signingRuntime,
            )
        }
        coVerify(exactly = 1) { fixture.walletDao.hasActiveDeletionOperation() }
        verify(exactly = 1) {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        }
    }

    @Test
    fun `account switch immediately before transport fails closed`() = runTest {
        val fixture = fixture()
        coEvery { fixture.userRepository.getCurSoraAccount() } returns
            SoraAccount("cnDifferentWallet", "Different")

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("POLKAMARKT_ACCOUNT_CHANGED", error.message)
        coVerify(exactly = 0) { fixture.featureManager.getState() }
        coVerify(exactly = 0) {
            fixture.runtimeContract.requirePolkamarktMutationRuntimeUnchanged(
                fixture.signingRuntime,
            )
        }
        coVerify(exactly = 0) { fixture.walletDao.hasActiveDeletionOperation() }
    }

    @Test
    fun `visibility disable immediately before transport fails closed`() = runTest {
        val fixture = fixture()
        coEvery { fixture.featureManager.getState() } returns featureState(
            visible = false,
            mutationsAvailable = true,
        )

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("POLKAMARKT_DISABLED", error.message)
        coVerify(exactly = 0) {
            fixture.runtimeContract.requirePolkamarktMutationRuntimeUnchanged(
                fixture.signingRuntime,
            )
        }
        coVerify(exactly = 0) { fixture.walletDao.hasActiveDeletionOperation() }
    }

    @Test
    fun `mutation disable immediately before transport fails closed`() = runTest {
        val fixture = fixture()
        coEvery { fixture.featureManager.getState() } returns featureState(
            visible = true,
            mutationsAvailable = false,
        )

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("POLKAMARKT_MUTATIONS_DISABLED", error.message)
        coVerify(exactly = 0) {
            fixture.runtimeContract.requirePolkamarktMutationRuntimeUnchanged(
                fixture.signingRuntime,
            )
        }
        coVerify(exactly = 0) { fixture.walletDao.hasActiveDeletionOperation() }
    }

    @Test
    fun `runtime drift immediately before transport fails closed`() = runTest {
        val fixture = fixture()
        coEvery {
            fixture.runtimeContract.requirePolkamarktMutationRuntimeUnchanged(
                fixture.signingRuntime,
            )
        } throws IllegalStateException("SORA2_SPEC_VERSION_MISMATCH")

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("SORA2_SPEC_VERSION_MISMATCH", error.message)
        coVerify(exactly = 0) { fixture.walletDao.hasActiveDeletionOperation() }
    }

    @Test
    fun `wallet deletion immediately before transport fails closed`() = runTest {
        val fixture = fixture()
        coEvery { fixture.walletDao.hasActiveDeletionOperation() } returns true

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("WALLET_DELETION_ACTIVE", error.message)
        verify(exactly = 0) {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        }
    }

    @Test
    fun `exact current signed row is allowed only for its own transport`() = runTest {
        val fixture = fixture()
        every {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        } returns flowOf(listOf(signedPending("current")))

        fixture.repository.requireMutationPreTransport(
            expectedAccountId = WALLET_ID,
            signingRuntime = fixture.signingRuntime,
            allowedCurrentLocalId = "current",
        )

        verify(exactly = 1) {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        }
    }

    @Test
    fun `unreconciled signed row blocks a new mutation`() = runTest {
        val fixture = fixture()
        every {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        } returns flowOf(listOf(signedPending("older")))

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("POLKAMARKT_PENDING_RECOVERY_REQUIRED", error.message)
        assertEquals(
            "PENDING_POLKAMARKT_TRANSACTION_UNRESOLVED",
            error.cause?.message,
        )
    }

    @Test
    fun `unbound chain journal is recovery evidence before transport`() = runTest {
        val fixture = fixture()
        every {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        } returns flowOf(listOf(signedPending("historical").copy(chainId = null)))

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("POLKAMARKT_PENDING_RECOVERY_REQUIRED", error.message)
        assertEquals(
            "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED",
            error.cause?.message,
        )
    }

    @Test
    fun `historical chain evidence in another wallet blocks transport globally`() = runTest {
        val fixture = fixture()
        coEvery {
            fixture.walletDao.countPendingTransactionsRequiringChainRecovery()
        } returns 1

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("POLKAMARKT_PENDING_RECOVERY_REQUIRED", error.message)
        assertEquals(
            "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED",
            error.cause?.message,
        )
        verify(exactly = 0) {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        }
    }

    @Test
    fun `pending recovery immediately before transport fails closed`() = runTest {
        val fixture = fixture()
        every {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        } returns flowOf(
            listOf(
                PendingNetworkTransactionLocal(
                    localId = "pending",
                    walletId = WALLET_ID,
                    networkId = "sora2",
                    chainId = WalletNetworkChainIdentity.SORA2,
                    transactionHash = "0x" + "ab".repeat(32),
                    assetId = "polkamarkt:7:BUY",
                    amount = "100",
                    recipient = "Yes",
                    state = "UNKNOWN",
                    submissionIsAmbiguous = true,
                    createdAt = 1,
                    updatedAt = 2,
                )
            )
        )

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("POLKAMARKT_PENDING_RECOVERY_REQUIRED", error.message)
        assertEquals(
            "PENDING_TRANSACTION_SUBMISSION_AMBIGUOUS",
            error.cause?.message,
        )
    }

    @Test
    fun `durable submitted row remains ambiguous and blocks a new mutation`() = runTest {
        val fixture = fixture()
        every {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        } returns flowOf(
            listOf(
                signedPending("submitted").copy(
                    state = "SUBMITTED",
                    submissionIsAmbiguous = true,
                )
            )
        )

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("POLKAMARKT_PENDING_RECOVERY_REQUIRED", error.message)
        assertEquals(
            "PENDING_TRANSACTION_SUBMISSION_AMBIGUOUS",
            error.cause?.message,
        )
    }

    @Test
    fun `committed pending reconciliation row blocks without being treated as terminal`() = runTest {
        val fixture = fixture()
        every {
            fixture.walletDao.observePendingTransactions(WALLET_ID)
        } returns flowOf(
            listOf(
                signedPending("committed").copy(
                    state = "COMMITTED_PENDING_RECONCILIATION",
                    submissionIsAmbiguous = false,
                )
            )
        )

        val error = captureFailure {
            fixture.repository.requireMutationPreTransport(WALLET_ID, fixture.signingRuntime)
        }

        assertEquals("POLKAMARKT_PENDING_RECOVERY_REQUIRED", error.message)
        assertEquals(
            "PENDING_POLKAMARKT_TRANSACTION_UNRESOLVED",
            error.cause?.message,
        )
    }

    @Test
    fun `transport arm is durably marked submitted and ambiguous before rpc`() = runTest {
        val fixture = fixture()
        val hash = "0x" + "ab".repeat(32)

        fixture.repository.markPendingSubmitted("current", hash)

        coVerify(exactly = 1) {
            fixture.walletDao.updatePendingTransaction(
                localId = "current",
                transactionHash = hash,
                state = "SUBMITTED",
                ambiguous = true,
                updatedAt = any(),
            )
        }
    }

    private fun signedPending(localId: String) = PendingNetworkTransactionLocal(
        localId = localId,
        walletId = WALLET_ID,
        networkId = "sora2",
        chainId = WalletNetworkChainIdentity.SORA2,
        transactionHash = "0x" + "ab".repeat(32),
        assetId = "polkamarkt:7:BUY",
        amount = "100",
        recipient = "Yes",
        state = "SIGNED",
        submissionIsAmbiguous = false,
        createdAt = 1,
        updatedAt = 2,
    )

    private fun fixture(): Fixture {
        val userRepository = mockk<UserRepository>(relaxed = true)
        val featureManager = mockk<ProductionFeatureManager>(relaxed = true)
        val runtimeContract = mockk<Sora2RuntimeContract>(relaxed = true)
        val signingRuntime = mockk<QualifiedSora2MutationRuntime>()
        val database = mockk<AppDatabase>(relaxed = true)
        val walletDao = mockk<WalletIdentityDao>(relaxed = true)

        every { database.walletIdentityDao() } returns walletDao
        coEvery { userRepository.getCurSoraAccount() } returns
            SoraAccount(WALLET_ID, "Wallet")
        coEvery { featureManager.getState() } returns featureState(
            visible = true,
            mutationsAvailable = true,
        )
        coEvery {
            runtimeContract.requirePolkamarktMutationRuntimeUnchanged(signingRuntime)
        } returns
            VerifiedSora2Runtime("reviewed", 130, 130)
        coEvery { walletDao.hasActiveDeletionOperation() } returns false
        every { walletDao.observePendingTransactions(WALLET_ID) } returns flowOf(emptyList())

        return Fixture(
            repository = PolkamarktTraderRepository(
                indexer = mockk<PolkaswapIndexerClient>(relaxed = true),
                calls = mockk<SubstrateCalls>(relaxed = true),
                extrinsicManager = mockk<ExtrinsicManager>(relaxed = true),
                runtimeContract = runtimeContract,
                credentialsRepository = mockk<CredentialsRepository>(relaxed = true),
                userRepository = userRepository,
                featureManager = featureManager,
                database = database,
                catalogCache = mockk<PolkamarktCatalogCache>(relaxed = true),
            ),
            userRepository = userRepository,
            featureManager = featureManager,
            runtimeContract = runtimeContract,
            signingRuntime = signingRuntime,
            walletDao = walletDao,
        )
    }

    private fun featureState(
        visible: Boolean,
        mutationsAvailable: Boolean,
    ) = ProductionFeatureState(
        nexusAvailable = false,
        nexusSendsAvailable = false,
        polkamarktVisible = visible,
        polkamarktMutationsAvailable = mutationsAvailable,
        tairaVisible = false,
        tairaPreferenceIsExplicit = false,
    )

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Exception) {
            return error
        }
        throw AssertionError("Expected the pre-transport gate to fail")
    }

    private data class Fixture(
        val repository: PolkamarktTraderRepository,
        val userRepository: UserRepository,
        val featureManager: ProductionFeatureManager,
        val runtimeContract: Sora2RuntimeContract,
        val signingRuntime: QualifiedSora2MutationRuntime,
        val walletDao: WalletIdentityDao,
    )

    private companion object {
        const val WALLET_ID = "cnExistingWallet"
    }
}
