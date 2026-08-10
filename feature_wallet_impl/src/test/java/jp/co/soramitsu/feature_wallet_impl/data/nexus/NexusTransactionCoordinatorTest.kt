package jp.co.soramitsu.feature_wallet_impl.data.nexus

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusAssetAliasBinding
import jp.co.soramitsu.common.nexus.NexusAssetDefinition
import jp.co.soramitsu.common.nexus.NexusAssetBalance
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.NexusSubmissionPayload
import jp.co.soramitsu.common.nexus.NexusSubmissionReceipt
import jp.co.soramitsu.common.nexus.NexusToriiClient
import jp.co.soramitsu.common.nexus.NexusToriiException
import jp.co.soramitsu.common.nexus.NexusTransactionStatus
import jp.co.soramitsu.common.nexus.NexusTransactionStatusValue
import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.nexus.TairaDeployment
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.core_db.model.WalletMigrationJournalLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusTransactionCoordinatorTest {

    private val database = mockk<AppDatabase>()
    private val dao = mockk<WalletIdentityDao>()
    private val userRepository = mockk<UserRepository>()
    private val torii = mockk<NexusToriiClient>()
    private val signer = mockk<NexusTransactionSigner>()
    private val finalityReader = mockk<NexusFinalityReader>()
    private val featureManager = mockk<ProductionFeatureManager>()
    private val pendingRecoveryScheduler = mockk<NexusPendingRecoveryScheduler>(relaxed = true)
    private val submissionActivity = NexusSubmissionActivityRegistry()
    private val pendingReconciler = NexusPendingReconciler(
        database = database,
        userRepository = userRepository,
        torii = torii,
        finalityReader = finalityReader,
        submissionActivity = submissionActivity,
    )
    private val coordinator = NexusTransactionCoordinator(
        database = database,
        userRepository = userRepository,
        torii = torii,
        signer = signer,
        finalityReader = finalityReader,
        featureManager = featureManager,
        pendingRecoveryScheduler = pendingRecoveryScheduler,
        pendingReconciler = pendingReconciler,
        submissionActivity = submissionActivity,
    )

    @Test
    fun `restart recovery rotates bounded passes so failed exact hashes cannot starve later rows`() =
        runTest {
            val first = unresolvedTransaction("first", WalletNetworkId.MINAMOTO)
            val second = unresolvedTransaction("second", WalletNetworkId.TAIRA)
            every { database.walletIdentityDao() } returns dao
            coEvery { dao.getUnresolvedTransactions() } returns listOf(first, second)
            coEvery { dao.getPendingTransaction("second") } returns null

            val pass = coordinator.recoverAfterRestart(
                maxTransactions = 1,
                startOffset = 1,
            )

            assertEquals(1, pass.attempted)
            assertEquals(0, pass.terminal)
            assertEquals(0, pass.unresolved)
            assertEquals(1, pass.failures)
            assertEquals(1, pass.remainingUnattempted)
            assertTrue(pass.requiresRetry)
            coVerify(exactly = 0) { dao.getPendingTransaction("first") }
            coVerify(exactly = 1) { dao.getPendingTransaction("second") }
        }

    @Test
    fun `empty restart recovery pass terminates durable work`() = runTest {
        every { database.walletIdentityDao() } returns dao
        coEvery { dao.getUnresolvedTransactions() } returns emptyList()

        val pass = coordinator.recoverAfterRestart()

        assertEquals(0, pass.attempted)
        assertFalse(pass.requiresRetry)
    }

    @Test
    fun `unbound and retired Taira journals remain immutable without Torii calls`() = runTest {
        val retained = unresolvedTransaction("retained", WalletNetworkId.TAIRA)
        val chainIds = listOf(
            null,
            "809574f5-fee7-5e69-bfcf-52451e42d50f",
        )
        every { database.walletIdentityDao() } returns dao

        chainIds.forEachIndexed { index, chainId ->
            val localId = "retained-$index"
            coEvery { dao.getPendingTransaction(localId) } returns retained.copy(
                localId = localId,
                chainId = chainId,
            )

            val error = runCatching { pendingReconciler.reconcile(localId) }.exceptionOrNull()

            assertEquals(
                "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED",
                error?.message,
            )
        }
        coVerify(exactly = 0) { torii.transactionStatus(any(), any()) }
        coVerify(exactly = 0) {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `historical chain evidence blocks prepare before Torii quote or signing`() = runTest {
        every { database.walletIdentityDao() } returns dao
        coEvery { featureManager.getState() } returns enabledFeatureState()
        every { signer.isQualifiedFor(NexusNetworks.minamoto) } returns true
        every { finalityReader.isQualifiedFor(NexusNetworks.minamoto) } returns true
        coEvery { dao.countPendingTransactionsRequiringChainRecovery() } returns 1

        val error = runCatching {
            coordinator.prepare(
                NexusSendRequest(
                    walletId = WALLET_ID,
                    networkId = WalletNetworkId.MINAMOTO,
                    recipient = verifiedNetworkAccount(WalletNetworkId.MINAMOTO).address,
                    amount = "1",
                )
            )
        }.exceptionOrNull()

        assertEquals(
            "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED",
            error?.message,
        )
        coVerify(exactly = 0) { torii.resolveXorDefinition(any()) }
        coVerify(exactly = 0) { torii.getXorBalance(any(), any()) }
        coVerify(exactly = 0) { signer.quote(any()) }
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `status tracking reaches finality without resubmitting`() = runTest {
        val hash = "ab".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        every { database.walletIdentityDao() } returns dao
        coEvery { dao.getPendingTransaction("local") } returns PendingNetworkTransactionLocal(
            localId = "local",
            walletId = WALLET_ID,
            networkId = WalletNetworkId.MINAMOTO.wireId,
            chainId = NexusNetworks.minamoto.chainId,
            transactionHash = hash,
            assetId = XOR_DEFINITION_ID,
            amount = "1",
            recipient = account.address,
            state = "SUBMITTED",
            submissionIsAmbiguous = false,
            createdAt = 1,
            updatedAt = 1,
        )
        stubVerifiedWallet(account)
        every { signer.isQualifiedFor(NexusNetworks.minamoto) } returns false
        coEvery {
            torii.transactionStatus(NexusNetworks.minamoto, hash)
        } returnsMany listOf(
            transactionStatus(hash, "pending"),
            transactionStatus(hash, "committed"),
        )
        coEvery {
            torii.getAssetBalanceByDefinition(
                NexusNetworks.minamoto,
                account.address,
                XOR_DEFINITION_ID,
            )
        } returns NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "9")
        coEvery {
            torii.committedXorTransfers(
                NexusNetworks.minamoto,
                account.address,
                XOR_DEFINITION_ID,
            )
        } returns listOf(
            NexusTransferHistoryItem(
                transactionHash = hash,
                timestampMillis = 1,
                amount = "1",
                sender = account.address,
                receiver = account.address,
            )
        )
        coEvery {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        } returns Unit

        val result = coordinator.reconcileUntilTerminal(
            localId = "local",
            maxAttempts = 2,
            pollDelayMillis = 0,
        )

        assertEquals("FINALIZED", result.state)
        coVerify(exactly = 2) {
            torii.transactionStatus(NexusNetworks.minamoto, hash)
        }
        verify(exactly = 0) { signer.isQualifiedFor(any()) }
        coVerify(exactly = 0) { signer.quote(any()) }
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { torii.resolveXorDefinition(any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `committed status remains pending until exact history reconciliation`() = runTest {
        val hash = "cd".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        every { database.walletIdentityDao() } returns dao
        coEvery { dao.getPendingTransaction("local") } returns PendingNetworkTransactionLocal(
            localId = "local",
            walletId = WALLET_ID,
            networkId = WalletNetworkId.MINAMOTO.wireId,
            chainId = NexusNetworks.minamoto.chainId,
            transactionHash = hash,
            assetId = XOR_DEFINITION_ID,
            amount = "1",
            recipient = account.address,
            state = "SUBMITTED",
            submissionIsAmbiguous = false,
            createdAt = 1,
            updatedAt = 1,
        )
        stubVerifiedWallet(account)
        coEvery {
            torii.transactionStatus(NexusNetworks.minamoto, hash)
        } returns transactionStatus(hash, "committed")
        coEvery {
            torii.getAssetBalanceByDefinition(
                NexusNetworks.minamoto,
                account.address,
                XOR_DEFINITION_ID,
            )
        } returns NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "9")
        coEvery {
            torii.committedXorTransfers(
                NexusNetworks.minamoto,
                account.address,
                XOR_DEFINITION_ID,
            )
        } returns emptyList()
        coEvery {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        } returns Unit

        val result = coordinator.reconcileUntilTerminal(
            localId = "local",
            maxAttempts = 1,
            pollDelayMillis = 0,
        )

        assertEquals("COMMITTED_PENDING_RECONCILIATION", result.state)
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `duplicate exact transfer history cannot finalize a committed send`() = runTest {
        val hash = "cf".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        every { database.walletIdentityDao() } returns dao
        coEvery { dao.getPendingTransaction("local") } returns PendingNetworkTransactionLocal(
            localId = "local",
            walletId = WALLET_ID,
            networkId = WalletNetworkId.MINAMOTO.wireId,
            chainId = NexusNetworks.minamoto.chainId,
            transactionHash = hash,
            assetId = XOR_DEFINITION_ID,
            amount = "1",
            recipient = account.address,
            state = "SUBMITTED",
            submissionIsAmbiguous = false,
            createdAt = 1,
            updatedAt = 1,
        )
        stubVerifiedWallet(account)
        coEvery {
            torii.transactionStatus(NexusNetworks.minamoto, hash)
        } returns transactionStatus(hash, "committed")
        coEvery {
            torii.getAssetBalanceByDefinition(
                NexusNetworks.minamoto,
                account.address,
                XOR_DEFINITION_ID,
            )
        } returns NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "9")
        val duplicate = NexusTransferHistoryItem(
            transactionHash = hash,
            timestampMillis = 1,
            amount = "1",
            sender = account.address,
            receiver = account.address,
        )
        coEvery {
            torii.committedXorTransfers(
                NexusNetworks.minamoto,
                account.address,
                XOR_DEFINITION_ID,
            )
        } returns listOf(duplicate, duplicate.copy(timestampMillis = 2))
        coEvery {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        } returns Unit

        val result = coordinator.reconcile("local")

        assertEquals("COMMITTED_PENDING_RECONCILIATION", result.state)
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `finality checkpoint from another chain cannot finalize a committed send`() = runTest {
        val hash = "d0".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        every { database.walletIdentityDao() } returns dao
        coEvery { dao.getPendingTransaction("local") } returns PendingNetworkTransactionLocal(
            localId = "local",
            walletId = WALLET_ID,
            networkId = WalletNetworkId.MINAMOTO.wireId,
            chainId = NexusNetworks.minamoto.chainId,
            transactionHash = hash,
            assetId = XOR_DEFINITION_ID,
            amount = "1",
            recipient = account.address,
            state = "SUBMITTED",
            submissionIsAmbiguous = false,
            createdAt = 1,
            updatedAt = 1,
        )
        stubVerifiedWallet(account)
        coEvery {
            torii.transactionStatus(NexusNetworks.minamoto, hash)
        } returns transactionStatus(hash, "committed")
        coEvery {
            finalityReader.finalizedCheckpoint(NexusNetworks.minamoto)
        } returns NexusFinalityCheckpoint(
            networkId = WalletNetworkId.TAIRA,
            chainId = NexusNetworks.taira.chainId,
            finalizedBlockHeight = 10,
            finalizedBlockHash = "ab".repeat(32),
        )

        val error = runCatching { coordinator.reconcile("local") }.exceptionOrNull()

        assertEquals("NEXUS_FINALITY_NETWORK_MISMATCH", error?.message)
        coVerify(exactly = 0) { torii.getAssetBalanceByDefinition(any(), any(), any()) }
        coVerify(exactly = 0) { torii.committedXorTransfers(any(), any(), any()) }
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `approved status remains nonterminal until committed or applied`() = runTest {
        val hash = "ce".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        every { database.walletIdentityDao() } returns dao
        coEvery { dao.getPendingTransaction("local") } returns PendingNetworkTransactionLocal(
            localId = "local",
            walletId = WALLET_ID,
            networkId = WalletNetworkId.MINAMOTO.wireId,
            chainId = NexusNetworks.minamoto.chainId,
            transactionHash = hash,
            assetId = XOR_DEFINITION_ID,
            amount = "1",
            recipient = account.address,
            state = "SUBMITTED",
            submissionIsAmbiguous = false,
            createdAt = 1,
            updatedAt = 1,
        )
        coEvery {
            torii.transactionStatus(NexusNetworks.minamoto, hash)
        } returns transactionStatus(hash, "approved")
        coEvery {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        } returns Unit

        val result = coordinator.reconcile("local")

        assertEquals("SUBMITTED", result.state)
        coVerify(exactly = 0) { torii.resolveXorDefinition(any()) }
        coVerify(exactly = 0) { finalityReader.finalizedCheckpoint(any()) }
        coVerify(exactly = 0) { torii.getXorBalance(any(), any()) }
        coVerify(exactly = 0) { torii.getAssetBalanceByDefinition(any(), any(), any()) }
        coVerify(exactly = 0) { torii.committedXorTransfers(any(), any(), any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `stale status cannot regress finality and recovery does not require wallet selection`() =
        runTest {
            val hash = "de".repeat(32)
            val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
            val network = NexusNetworks.minamoto
            var persisted = PendingNetworkTransactionLocal(
                localId = "local",
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO.wireId,
                chainId = NexusNetworks.minamoto.chainId,
                transactionHash = hash,
                assetId = XOR_DEFINITION_ID,
                amount = "1",
                recipient = account.address,
                state = "SUBMITTED",
                submissionIsAmbiguous = false,
                createdAt = 1,
                updatedAt = 1,
            )
            every { database.walletIdentityDao() } returns dao
            stubVerifiedWallet(account)
            coEvery { dao.getPendingTransaction("local") } coAnswers { persisted }
            coEvery {
                dao.updatePendingTransaction(any(), any(), any(), any(), any())
            } coAnswers {
                persisted = persisted.copy(
                    transactionHash = secondArg(),
                    state = arg(2),
                    submissionIsAmbiguous = arg(3),
                    updatedAt = arg(4),
                )
            }
            coEvery {
                torii.getAssetBalanceByDefinition(
                    network,
                    account.address,
                    XOR_DEFINITION_ID,
                )
            } returns NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "9")
            coEvery {
                torii.committedXorTransfers(
                    network,
                    account.address,
                    XOR_DEFINITION_ID,
                )
            } returns listOf(
                NexusTransferHistoryItem(
                    transactionHash = hash,
                    timestampMillis = 1,
                    amount = "1",
                    sender = account.address,
                    receiver = account.address,
                )
            )

            val staleStatusStarted = CompletableDeferred<Unit>()
            val releaseStaleStatus = CompletableDeferred<Unit>()
            var statusCalls = 0
            coEvery { torii.transactionStatus(network, hash) } coAnswers {
                if (statusCalls++ == 0) {
                    staleStatusStarted.complete(Unit)
                    releaseStaleStatus.await()
                    transactionStatus(hash, "pending")
                } else {
                    transactionStatus(hash, "committed")
                }
            }

            val staleReconciliation = async { coordinator.reconcile("local") }
            staleStatusStarted.await()
            val finalized = coordinator.reconcile("local")
            releaseStaleStatus.complete(Unit)
            val staleResult = staleReconciliation.await()

            assertEquals("FINALIZED", finalized.state)
            assertEquals("FINALIZED", staleResult.state)
            assertEquals("FINALIZED", persisted.state)
            coVerify(exactly = 0) { userRepository.getCurSoraAccount() }
            coVerify(exactly = 0) { torii.submit(any(), any()) }
        }

    @Test
    fun `signed restart record becomes ambiguous before status lookup failure`() = runTest {
        val hash = "ed".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        var persisted = PendingNetworkTransactionLocal(
            localId = "local",
            walletId = WALLET_ID,
            networkId = WalletNetworkId.MINAMOTO.wireId,
            chainId = NexusNetworks.minamoto.chainId,
            transactionHash = hash,
            assetId = XOR_DEFINITION_ID,
            amount = "1",
            recipient = account.address,
            state = "SIGNED",
            submissionIsAmbiguous = false,
            createdAt = 1,
            updatedAt = 1,
        )
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { dao.getPendingTransaction("local") } coAnswers { persisted }
        coEvery {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        } coAnswers {
            persisted = persisted.copy(
                transactionHash = secondArg(),
                state = arg(2),
                submissionIsAmbiguous = arg(3),
                updatedAt = arg(4),
            )
        }
        coEvery { torii.transactionStatus(network, hash) } throws NexusToriiException(
            safeCode = "NEXUS_NETWORK_IO",
        )

        val error = runCatching { coordinator.reconcile("local") }.exceptionOrNull()

        assertTrue(error is NexusToriiException)
        assertEquals("UNKNOWN", persisted.state)
        assertTrue(persisted.submissionIsAmbiguous)
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `live signed journal cannot be reconciled before submit handoff completes`() = runTest {
        val hash = "ee".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        val quote = NexusTransferFeeQuote(
            networkId = WalletNetworkId.MINAMOTO.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
            fee = "0.1",
            quoteIdentity = "quote-live-handoff",
            validUntilBlock = 20,
        )
        val transportEntered = CompletableDeferred<Unit>()
        val releaseTransport = CompletableDeferred<Unit>()
        var persisted: PendingNetworkTransactionLocal? = null
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "10")
        coEvery { signer.quote(any()) } returns quote
        coEvery { signer.sign(any(), any()) } returns NexusSignedTransaction(
            noritoBytes = byteArrayOf(1, 2, 3),
            transactionHash = hash,
        )
        coEvery { dao.insertPendingTransaction(any()) } coAnswers {
            persisted = firstArg()
        }
        coEvery { dao.getPendingTransaction(any()) } coAnswers { persisted }
        coEvery { dao.getUnresolvedTransactions() } coAnswers {
            listOf(checkNotNull(persisted))
        }
        coEvery {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        } coAnswers {
            persisted = checkNotNull(persisted).copy(
                transactionHash = secondArg(),
                state = arg(2),
                submissionIsAmbiguous = arg(3),
                updatedAt = arg(4),
            )
        }
        coEvery { torii.submit(network, any()) } coAnswers {
            transportEntered.complete(Unit)
            releaseTransport.await()
            NexusSubmissionReceipt(
                payload = NexusSubmissionPayload(
                    transactionHash = hash,
                    entrypointHash = hash,
                    signedTransactionHash = hash,
                    submittedAtMillis = 1,
                    submittedAtHeight = 1,
                )
            )
        }
        coEvery { torii.transactionStatus(network, hash) } returns
            transactionStatus(hash, "pending")

        val prepared = coordinator.prepare(
            NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = "1",
            )
        )
        val submission = async { coordinator.submit(prepared) }
        transportEntered.await()
        val localId = checkNotNull(persisted).localId
        var stateWhileActive: String? = null
        val recoveryPass = try {
            coordinator.recoverAfterRestart(
                maxTransactions = 1,
                startOffset = 0,
            ).also {
                stateWhileActive = persisted?.state
                coVerify(exactly = 0) { torii.transactionStatus(any(), any()) }
            }
        } finally {
            releaseTransport.complete(Unit)
        }
        val result = submission.await()
        val postHandoff = coordinator.reconcile(localId)

        assertEquals(1, recoveryPass.failures)
        assertTrue(recoveryPass.requiresRetry)
        assertEquals("SIGNED", stateWhileActive)
        assertEquals("SUBMITTED", result.state)
        assertEquals("SUBMITTED", postHandoff.state)
        coVerify(exactly = 1) { torii.transactionStatus(network, hash) }
        coVerify(exactly = 1) { torii.submit(network, any()) }
    }

    @Test
    fun `prepare rejects an address from another network before quote or submission`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        every { signer.isQualifiedFor(NexusNetworks.minamoto) } returns true
        val tairaRecipient = IrohaAddressCodec.encode(
            ByteArray(32) { 2 },
            NexusNetworks.taira.chainDiscriminant,
        )

        val error = runCatching {
            coordinator.prepare(
                NexusSendRequest(
                    walletId = WALLET_ID,
                    networkId = WalletNetworkId.MINAMOTO,
                    recipient = tairaRecipient,
                    amount = "1",
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IrohaAddressCodec.AddressException)
        assertEquals(
            IrohaAddressCodec.ErrorCode.NETWORK_MISMATCH,
            (error as IrohaAddressCodec.AddressException).code,
        )
        coVerify(exactly = 0) { torii.resolveXorDefinition(any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `Taira send is rejected when test networks are hidden`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.TAIRA)
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns
            enabledFeatureState().copy(tairaVisible = false)

        val error = runCatching {
            coordinator.prepare(
                NexusSendRequest(
                    walletId = WALLET_ID,
                    networkId = WalletNetworkId.TAIRA,
                    recipient = account.address,
                    amount = "1",
                )
            )
        }.exceptionOrNull()

        assertEquals("NEXUS_SEND_DISABLED", error?.message)
        coVerify(exactly = 0) { torii.resolveXorDefinition(any()) }
        coVerify(exactly = 0) { signer.quote(any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `Taira cannot sign or journal without an admitted deployment manifest`() = runTest {
        if (TairaDeployment.binding != null) return@runTest

        val account = verifiedNetworkAccount(WalletNetworkId.TAIRA)
        val network = NexusNetworks.taira
        val quote = NexusTransferFeeQuote(
            networkId = WalletNetworkId.TAIRA.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
            fee = "0.1",
            quoteIdentity = "quote-unqualified-taira",
            validUntilBlock = 20,
        )
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "10")
        coEvery { signer.quote(any()) } returns quote

        val prepared = coordinator.prepare(
            NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.TAIRA,
                recipient = account.address,
                amount = "1",
            )
        )
        val error = runCatching { coordinator.submit(prepared) }.exceptionOrNull()

        assertEquals("TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED", error?.message)
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { dao.insertPendingTransaction(any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `prepare rejects stored I105 address and public key mismatch`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO).copy(
            publicKey = "02".repeat(32),
        )
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()

        val error = runCatching {
            coordinator.prepare(
                NexusSendRequest(
                    walletId = WALLET_ID,
                    networkId = WalletNetworkId.MINAMOTO,
                    recipient = account.address,
                    amount = "1",
                )
            )
        }.exceptionOrNull()

        assertEquals("NEXUS_NETWORK_ACCOUNT_KEY_MISMATCH", error?.message)
        coVerify(exactly = 0) { torii.resolveXorDefinition(any()) }
        coVerify(exactly = 0) { signer.quote(any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `prepare preserves exact amount and fee at the shared 255 scale boundary`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        val quantity = quantityWithScale(NexusQuantityContract.MAX_SCALE)
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "1")
        coEvery { signer.quote(any()) } returns NexusTransferFeeQuote(
            networkId = WalletNetworkId.MINAMOTO.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = quantity,
            fee = quantity,
            quoteIdentity = "quote-scale-255",
            validUntilBlock = 20,
        )

        val prepared = coordinator.prepare(
            NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = quantity,
            )
        )

        assertEquals(quantity, prepared.canonicalAmount)
        assertEquals(quantity, prepared.fee)
    }

    @Test
    fun `prepare rejects a zero fee before signing or submission`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "10")
        coEvery { signer.quote(any()) } returns NexusTransferFeeQuote(
            networkId = WalletNetworkId.MINAMOTO.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
            fee = "0",
            quoteIdentity = "quote-zero-fee",
            validUntilBlock = 20,
        )

        val error = runCatching {
            coordinator.prepare(
                NexusSendRequest(
                    walletId = WALLET_ID,
                    networkId = WalletNetworkId.MINAMOTO,
                    recipient = account.address,
                    amount = "1",
                )
            )
        }.exceptionOrNull()

        assertEquals("NEXUS_FEE_NOT_POSITIVE", error?.message)
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `submit rejects a zero fresh fee before signing or submission`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        val quote = NexusTransferFeeQuote(
            networkId = WalletNetworkId.MINAMOTO.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
            fee = "0.1",
            quoteIdentity = "quote-fresh-fee",
            validUntilBlock = 20,
        )
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "10")
        coEvery { signer.quote(any()) } returnsMany listOf(
            quote,
            quote.copy(fee = "0"),
        )

        val prepared = coordinator.prepare(
            NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = "1",
            )
        )
        val error = runCatching { coordinator.submit(prepared) }.exceptionOrNull()

        assertEquals("NEXUS_FEE_NOT_POSITIVE", error?.message)
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { dao.insertPendingTransaction(any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `submit rejects a zero final-lock fee before signing or submission`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        val quote = NexusTransferFeeQuote(
            networkId = WalletNetworkId.MINAMOTO.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
            fee = "0.1",
            quoteIdentity = "quote-final-fee",
            validUntilBlock = 20,
        )
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "10")
        coEvery { signer.quote(any()) } returnsMany listOf(
            quote,
            quote,
            quote.copy(fee = "0"),
        )

        val prepared = coordinator.prepare(
            NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = "1",
            )
        )
        val error = runCatching { coordinator.submit(prepared) }.exceptionOrNull()

        assertEquals("NEXUS_FEE_NOT_POSITIVE", error?.message)
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { dao.insertPendingTransaction(any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `prepare rejects scale 256 before quote or network quantity lookup`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()

        val error = runCatching {
            coordinator.prepare(
                NexusSendRequest(
                    walletId = WALLET_ID,
                    networkId = WalletNetworkId.MINAMOTO,
                    recipient = account.address,
                    amount = quantityWithScale(NexusQuantityContract.MAX_SCALE + 1),
                )
            )
        }.exceptionOrNull()

        assertEquals("NEXUS_INVALID_QUANTITY", error?.message)
        coVerify(exactly = 0) { torii.resolveXorDefinition(any()) }
        coVerify(exactly = 0) { torii.getXorBalance(any(), any()) }
        coVerify(exactly = 0) { signer.quote(any()) }
    }

    @Test
    fun `submit rejects a prepared signing route from another network`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        every { signer.isQualifiedFor(NexusNetworks.minamoto) } returns true
        every { finalityReader.isQualifiedFor(NexusNetworks.minamoto) } returns true
        val signingRequest = NexusTransferSigningRequest(
            network = NexusNetworks.taira,
            account = account,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
        )
        val prepared = NexusPreparedSend(
            request = NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = "1",
            ),
            canonicalRecipient = account.address,
            canonicalAmount = "1",
            assetDefinitionId = XOR_DEFINITION_ID,
            fee = "0.1",
            availableBalance = "10",
            quoteIdentity = "quote-route",
            signingRequest = signingRequest,
            quote = NexusTransferFeeQuote(
                networkId = WalletNetworkId.TAIRA.wireId,
                authority = account.address,
                recipient = account.address,
                assetDefinitionId = XOR_DEFINITION_ID,
                amount = "1",
                fee = "0.1",
                quoteIdentity = "quote-route",
                validUntilBlock = 20,
            ),
        )

        val error = runCatching { coordinator.submit(prepared) }.exceptionOrNull()

        assertEquals("NEXUS_SIGNING_NETWORK_MISMATCH", error?.message)
        coVerify(exactly = 0) { userRepository.getCurSoraAccount() }
        coVerify(exactly = 0) { signer.quote(any()) }
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `submit rejects a prepared signing asset outside the displayed contract`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        every { signer.isQualifiedFor(NexusNetworks.minamoto) } returns true
        every { finalityReader.isQualifiedFor(NexusNetworks.minamoto) } returns true
        val signingRequest = NexusTransferSigningRequest(
            network = NexusNetworks.minamoto,
            account = account,
            recipient = account.address,
            assetDefinitionId = "not-xor",
            amount = "1",
        )
        val prepared = NexusPreparedSend(
            request = NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = "1",
            ),
            canonicalRecipient = account.address,
            canonicalAmount = "1",
            assetDefinitionId = XOR_DEFINITION_ID,
            fee = "0.1",
            availableBalance = "10",
            quoteIdentity = "quote-asset",
            signingRequest = signingRequest,
            quote = NexusTransferFeeQuote(
                networkId = WalletNetworkId.MINAMOTO.wireId,
                authority = account.address,
                recipient = account.address,
                assetDefinitionId = "not-xor",
                amount = "1",
                fee = "0.1",
                quoteIdentity = "quote-asset",
                validUntilBlock = 20,
            ),
        )

        val error = runCatching { coordinator.submit(prepared) }.exceptionOrNull()

        assertEquals("NEXUS_SIGNING_ASSET_MISMATCH", error?.message)
        coVerify(exactly = 0) { userRepository.getCurSoraAccount() }
        coVerify(exactly = 0) { signer.quote(any()) }
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `emergency send disablement immediately before signing fails closed`() = runTest {
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        val quote = NexusTransferFeeQuote(
            networkId = WalletNetworkId.MINAMOTO.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
            fee = "0.1",
            quoteIdentity = "quote-disable",
            validUntilBlock = 20,
        )
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returnsMany listOf(
            enabledFeatureState(),
            enabledFeatureState(),
            enabledFeatureState().copy(nexusSendsAvailable = false),
        )
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "10")
        coEvery { signer.quote(any()) } returns quote

        val prepared = coordinator.prepare(
            NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = "1",
            )
        )
        val error = runCatching { coordinator.submit(prepared) }.exceptionOrNull()

        assertEquals("NEXUS_SEND_DISABLED", error?.message)
        coVerify(exactly = 0) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { dao.insertPendingTransaction(any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `signing and pending journal are locked and confirmation is one shot`() = runTest {
        val hash = "ef".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        val quote = NexusTransferFeeQuote(
            networkId = WalletNetworkId.MINAMOTO.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
            fee = "0.1",
            quoteIdentity = "quote-1",
            validUntilBlock = 20,
        )
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "10")
        coEvery { signer.quote(any()) } returns quote

        val transportEntered = CompletableDeferred<Unit>()
        val competingMutationEntered = CompletableDeferred<Unit>()
        var persisted: PendingNetworkTransactionLocal? = null
        coEvery { signer.sign(any(), any()) } coAnswers {
            yield()
            assertFalse(competingMutationEntered.isCompleted)
            NexusSignedTransaction(
                noritoBytes = byteArrayOf(1, 2, 3),
                transactionHash = hash,
            )
        }
        coEvery { dao.insertPendingTransaction(any()) } coAnswers {
            assertFalse(competingMutationEntered.isCompleted)
            persisted = firstArg()
            Unit
        }
        coEvery { dao.getPendingTransaction(any()) } coAnswers { persisted }
        coEvery {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        } coAnswers {
            persisted = checkNotNull(persisted).copy(
                transactionHash = secondArg(),
                state = arg(2),
                submissionIsAmbiguous = arg(3),
                updatedAt = arg(4),
            )
        }
        coEvery { torii.submit(network, any()) } coAnswers {
            transportEntered.complete(Unit)
            yield()
            assertFalse(competingMutationEntered.isCompleted)
            NexusSubmissionReceipt(
                payload = NexusSubmissionPayload(
                    transactionHash = hash,
                    entrypointHash = hash,
                    signedTransactionHash = hash,
                    submittedAtMillis = 1,
                    submittedAtHeight = 1,
                )
            )
        }

        val prepared = coordinator.prepare(
            NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = "1",
            )
        )
        val submission = async {
            coordinator.submit(prepared)
        }
        val competingMutation = async {
            transportEntered.await()
            WalletMutationCoordinator.withLock {
                competingMutationEntered.complete(Unit)
            }
        }

        val result = submission.await()
        competingMutation.await()
        val replayError = runCatching { coordinator.submit(prepared) }.exceptionOrNull()

        assertEquals("SUBMITTED", result.state)
        assertEquals("NEXUS_CONFIRMATION_ALREADY_SUBMITTED", replayError?.message)
        coVerifyOrder {
            signer.sign(any(), any())
            dao.insertPendingTransaction(any())
            torii.submit(network, any())
        }
        coVerify(exactly = 1) { signer.sign(any(), any()) }
        coVerify(exactly = 1) { dao.insertPendingTransaction(any()) }
        coVerify(exactly = 1) { torii.submit(network, any()) }
    }

    @Test
    fun `emergency disablement before Torii handoff is definitively not submitted`() = runTest {
        val hash = "f1".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        val quote = NexusTransferFeeQuote(
            networkId = WalletNetworkId.MINAMOTO.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
            fee = "0.1",
            quoteIdentity = "quote-pre-transport-disable",
            validUntilBlock = 20,
        )
        val signedBytes = byteArrayOf(1, 2, 3)
        var persisted: PendingNetworkTransactionLocal? = null
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returnsMany listOf(
            enabledFeatureState(),
            enabledFeatureState(),
            enabledFeatureState(),
            enabledFeatureState().copy(nexusSendsAvailable = false),
        )
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "10")
        coEvery { signer.quote(any()) } returns quote
        coEvery { signer.sign(any(), any()) } returns NexusSignedTransaction(
            noritoBytes = signedBytes,
            transactionHash = hash,
        )
        coEvery { dao.insertPendingTransaction(any()) } coAnswers {
            persisted = firstArg()
        }
        coEvery { dao.getPendingTransaction(any()) } coAnswers { persisted }
        coEvery {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        } coAnswers {
            persisted = checkNotNull(persisted).copy(
                transactionHash = secondArg(),
                state = arg(2),
                submissionIsAmbiguous = arg(3),
                updatedAt = arg(4),
            )
        }

        val prepared = coordinator.prepare(
            NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = "1",
            )
        )
        val error = runCatching { coordinator.submit(prepared) }.exceptionOrNull()

        assertEquals("NEXUS_SEND_DISABLED", error?.message)
        assertEquals("REJECTED", persisted?.state)
        assertFalse(persisted?.submissionIsAmbiguous ?: true)
        assertTrue(signedBytes.all { it == 0.toByte() })
        coVerify(exactly = 1) { signer.sign(any(), any()) }
        coVerify(exactly = 0) { torii.submit(any(), any()) }
    }

    @Test
    fun `ambiguous submission keeps exact hash and cannot be retried`() = runTest {
        val hash = "fa".repeat(32)
        val account = verifiedNetworkAccount(WalletNetworkId.MINAMOTO)
        val network = NexusNetworks.minamoto
        val quote = NexusTransferFeeQuote(
            networkId = WalletNetworkId.MINAMOTO.wireId,
            authority = account.address,
            recipient = account.address,
            assetDefinitionId = XOR_DEFINITION_ID,
            amount = "1",
            fee = "0.1",
            quoteIdentity = "quote-ambiguous",
            validUntilBlock = 20,
        )
        val signedBytes = byteArrayOf(1, 2, 3)
        var persisted: PendingNetworkTransactionLocal? = null
        every { database.walletIdentityDao() } returns dao
        stubVerifiedWallet(account)
        coEvery { featureManager.getState() } returns enabledFeatureState()
        coEvery { torii.getXorBalance(network, account.address) } returns
            NexusAssetBalance(asset = XOR_DEFINITION_ID, quantity = "10")
        coEvery { signer.quote(any()) } returns quote
        coEvery { signer.sign(any(), any()) } returns NexusSignedTransaction(
            noritoBytes = signedBytes,
            transactionHash = hash,
        )
        coEvery { dao.insertPendingTransaction(any()) } coAnswers {
            persisted = firstArg()
        }
        coEvery { dao.getPendingTransaction(any()) } coAnswers { persisted }
        coEvery {
            dao.updatePendingTransaction(any(), any(), any(), any(), any())
        } coAnswers {
            persisted = checkNotNull(persisted).copy(
                transactionHash = secondArg(),
                state = arg(2),
                submissionIsAmbiguous = arg(3),
                updatedAt = arg(4),
            )
        }
        coEvery { torii.submit(network, any()) } throws NexusToriiException(
            safeCode = "NEXUS_SUBMISSION_IO",
            submissionMayHaveReachedTorii = true,
        )

        val prepared = coordinator.prepare(
            NexusSendRequest(
                walletId = WALLET_ID,
                networkId = WalletNetworkId.MINAMOTO,
                recipient = account.address,
                amount = "1",
            )
        )
        val submissionError = runCatching { coordinator.submit(prepared) }.exceptionOrNull()
        val replayError = runCatching { coordinator.submit(prepared) }.exceptionOrNull()

        assertTrue(submissionError is NexusToriiException)
        assertEquals("UNKNOWN", persisted?.state)
        assertTrue(persisted?.submissionIsAmbiguous == true)
        assertEquals(hash, persisted?.transactionHash)
        assertTrue(signedBytes.all { it == 0.toByte() })
        assertEquals("NEXUS_CONFIRMATION_ALREADY_SUBMITTED", replayError?.message)
        coVerify(exactly = 1) { signer.sign(any(), any()) }
        coVerify(exactly = 1) { torii.submit(network, any()) }
        verify(exactly = 1) { pendingRecoveryScheduler.kickAfterJournal() }
    }

    private fun unresolvedTransaction(
        localId: String,
        networkId: WalletNetworkId,
    ): PendingNetworkTransactionLocal {
        val network = NexusNetworks.require(networkId)
        return PendingNetworkTransactionLocal(
            localId = localId,
            walletId = WALLET_ID,
            networkId = networkId.wireId,
            chainId = network.chainId,
            transactionHash = "ab".repeat(32),
            assetId = XOR_DEFINITION_ID,
            amount = "1",
            recipient = IrohaAddressCodec.encode(
                ByteArray(32) { 1 },
                network.chainDiscriminant,
            ),
            state = "UNKNOWN",
            submissionIsAmbiguous = true,
            createdAt = 1,
            updatedAt = 1,
        )
    }

    private fun stubVerifiedWallet(account: NetworkAccountLocal) {
        val network = NexusNetworks.require(
            checkNotNull(WalletNetworkId.fromWireId(account.networkId))
        )
        coEvery { dao.hasActiveDeletionOperation() } returns false
        coEvery { dao.countPendingTransactionsRequiringChainRecovery() } returns 0
        coEvery { torii.resolveXorDefinition(network) } returns qualifiedXorDefinition()
        coEvery {
            torii.hasAuthoritativeCommittedTransaction(
                network = network,
                accountId = any(),
                assetDefinitionId = any(),
                transactionHash = any(),
            )
        } returns true
        every { signer.isQualifiedFor(network) } returns true
        every { finalityReader.isQualifiedFor(network) } returns true
        coEvery { finalityReader.finalizedCheckpoint(network) } returns
            NexusFinalityCheckpoint(
                networkId = network.id,
                chainId = network.chainId,
                finalizedBlockHeight = 10L,
                finalizedBlockHash = "ab".repeat(32),
            )
        coEvery {
            dao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        } returns WalletMigrationJournalLocal(
            migrationId = WalletMigrationIds.NETWORK_ACCOUNTS_V1,
            state = "VERIFIED",
            legacyAccountCount = 1,
            verifiedAccountCount = 1,
            selectedWalletId = WALLET_ID,
            integrityHash = "verified",
            failureCode = null,
            startedAt = 1,
            completedAt = 2,
        )
        coEvery { dao.getWallet(WALLET_ID) } returns WalletIdentityLocal(
            walletId = WALLET_ID,
            displayName = "Wallet",
            secretSource = "MNEMONIC",
            migrationState = "VERIFIED",
            derivationVersion = 1,
        )
        coEvery { userRepository.getCurSoraAccount() } returns SoraAccount(WALLET_ID, "Wallet")
        coEvery {
            dao.getNetworkAccount(WALLET_ID, account.networkId)
        } returns account
    }

    private fun verifiedNetworkAccount(networkId: WalletNetworkId): NetworkAccountLocal {
        val network = NexusNetworks.require(networkId)
        return NetworkAccountLocal(
            walletId = WALLET_ID,
            networkId = networkId.wireId,
            publicKey = "01".repeat(32),
            address = IrohaAddressCodec.encode(ByteArray(32) { 1 }, network.chainDiscriminant),
            derivationPath = network.derivationPath,
            derivationVersion = 1,
            enabled = true,
        )
    }

    private fun qualifiedXorDefinition() = NexusAssetDefinition(
        id = XOR_DEFINITION_ID,
        name = "xor",
        alias = "xor#universal",
        aliasBinding = NexusAssetAliasBinding(
            alias = "xor#universal",
            status = "permanent",
            boundAtMillis = 1,
        ),
    )

    private fun transactionStatus(hash: String, kind: String) = NexusTransactionStatus(
        hash = hash,
        status = NexusTransactionStatusValue(
            kind = kind,
            blockHeight = if (kind in setOf("committed", "applied", "approved")) 1L else null,
        ),
        scope = "global",
        resolvedFrom = when (kind) {
            "pending", "validating", "queued", "submitted", "approved" -> "queue"
            else -> "state"
        },
    )

    private fun enabledFeatureState() = ProductionFeatureState(
        nexusAvailable = true,
        nexusSendsAvailable = true,
        polkamarktVisible = true,
        polkamarktMutationsAvailable = true,
        tairaVisible = true,
        tairaPreferenceIsExplicit = true,
    )

    private fun quantityWithScale(scale: Int): String =
        "0." + "0".repeat(scale - 1) + "1"

    private companion object {
        const val WALLET_ID = "cnWallet"
        const val XOR_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
    }
}
