package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.*
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.*
import jp.co.soramitsu.sora.substrate.runtime.*
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*

/** Exact hash and account admission against an in-memory persistence boundary; no signer/transport. */
class Sora2JournalAdmissionFlowTest {
    private val codec = Sora2AddressCodec()
    private val key = ByteArray(32) { 9 }
    private val walletId = checkNotNull(codec.toSoraAddressOrNull(key))
    private val publicKey = key.toHexString(withPrefix = false).lowercase()
    private val wallet = WalletIdentityLocal(walletId, "Public fixture", "MNEMONIC", "VERIFIED", 1)
    private val account = NetworkAccountLocal(walletId, "sora2", publicKey, walletId, "", 0, true)
    private val rows = linkedMapOf<String, Sora2PendingSubmissionLocal>()
    private val writes = mutableListOf<String>()
    private val dao = mockk<WalletIdentityDao>()
    private val accountDao = mockk<AccountDao>()
    private val database = mockk<AppDatabase> {
        every { walletIdentityDao() } returns dao
        every { accountDao() } returns accountDao
    }
    private val runtimeManager = mockk<RuntimeManager>()
    private val rpc = mockk<Sora2BoundedRuntimeRpcClient>()
    private val kick = mockk<Sora2PendingRecoveryKick>(relaxed = true)
    private val details = InMemorySora2SubmissionDetailsStore()
    private val coordinator = Sora2PendingSubmissionCoordinator(database, runtimeManager, rpc, codec, kick, details)
    private val runtime = Sora2MutationRuntimeContext(mockk(), RuntimeVersion(133, 131),
        Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH, "0x${"11".repeat(32)}", "a".repeat(64), "b".repeat(64), 1000L)
    private val encoded = "0x01020304"
    @Before fun setup() {
        WalletRecoveryCapabilityGate.enterNormal()
        coEvery { dao.hasActiveDeletionOperation() } returns false
        coEvery { dao.getMigrationJournal(any()) } returns null
        coEvery { dao.getWallet(walletId) } returns wallet
        coEvery { dao.getNetworkAccount(walletId, "sora2") } returns account
        coEvery { accountDao.getAccount(walletId) } returns SoraAccountLocal(walletId, "Public fixture")
        coEvery { dao.getSora2PendingSubmission(any()) } answers { rows[firstArg()] }
        coEvery { dao.getUnresolvedSora2PendingSubmissions() } answers { rows.values.filterNot { it.isAuthoritativelyTerminal } }
        coEvery { dao.insertSora2PendingSubmission(any()) } answers {
            val row = firstArg<Sora2PendingSubmissionLocal>()
            WalletIdentityDao.validateSora2PendingSubmission(row)
            check(rows.putIfAbsent(row.localId, row) == null)
            writes += row.state
        }
        coEvery { dao.updateSora2PendingSubmission(any(), any(), any(), any(), any(), any(), any()) } answers {
            val expected = firstArg<Sora2PendingSubmissionLocal>()
            check(rows[expected.localId] == expected) { "SORA2_PENDING_SUBMISSION_CHANGED" }
            val updated = expected.copy(state = arg(1), submissionIsAmbiguous = arg(2), terminalBlockNumber = arg(3),
                terminalBlockHash = arg(4), terminalFinalizedHeight = arg(5), updatedAt = maxOf(arg<Long>(6), expected.updatedAt))
            WalletIdentityDao.validateSora2PendingSubmission(updated)
            rows[updated.localId] = updated; writes += updated.state; updated
        }
        coEvery { dao.deleteSora2DefinitelyNotSubmitted(any()) } answers {
            val row = firstArg<Sora2PendingSubmissionLocal>()
            check(rows[row.localId] == row && row.state == Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT)
            rows.remove(row.localId); Unit
        }
    }
    @After fun cleanup() { WalletRecoveryCapabilityGate.enterNormal() }
    private fun prepared(context: Sora2MutationRuntimeContext = runtime,
                         purpose: ExtrinsicManager.PreparedPurpose = ExtrinsicManager.PreparedPurpose.GENERIC,
                         bytes: String = encoded, hash: String = bytes.extrinsicHash(), id: String = walletId) =
        ExtrinsicManager.PreparedExtrinsic(id, bytes, hash, context, purpose)
    private suspend fun rejected(code: String, block: suspend () -> Unit) {
        val failure = try { block(); null } catch (error: Exception) { error }
        assertNotNull("Expected $code", failure); assertEquals(code, failure!!.message)
    }
    @Test fun stagingCapturesExactHashRuntimeAndMortalEraWithoutSignedBytes() = runTest {
        val row = coordinator.stageBeforeTransport(prepared())
        assertEquals(encoded.extrinsicHash(), row.transactionHash); assertEquals("sora2:${row.transactionHash.removePrefix("0x")}", row.localId)
        assertEquals(publicKey, row.accountId); assertEquals(publicKey, row.publicKey); assertEquals(walletId, row.walletId)
        assertEquals(1000L, row.eraBirthBlock); assertEquals(1064L, row.eraDeathBlockExclusive)
        assertEquals(64, row.eraPeriod); assertEquals(40, row.eraPhase)
        assertEquals(runtime.finalizedHash, row.eraBirthBlockHash); assertEquals(runtime.metadataSha256, row.metadataSha256)
        assertEquals(runtime.typesSha256, row.typesSha256); assertEquals(133, row.specVersion); assertEquals(131, row.transactionVersion)
        assertEquals(Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT, row.state)
        assertFalse(row.submissionIsAmbiguous); assertTrue(row.createdAt > 0)
        coVerifyOrder { dao.insertSora2PendingSubmission(any()); kick.kickAfterJournal() }
        assertEquals(row, rows.values.single())
    }
    @Test fun stagingPolkamarktRetainsDistinctOperationKind() = runTest {
        assertEquals(Sora2PendingSubmissionLocal.OPERATION_POLKAMARKT,
            coordinator.stageBeforeTransport(prepared(purpose = ExtrinsicManager.PreparedPurpose.POLKAMARKT)).operationKind)
    }
    @Test fun stagingLegacyClaimUsesGenericWitnessAndAdmissionGate() = runTest {
        assertEquals(Sora2PendingSubmissionLocal.OPERATION_GENERIC,
            coordinator.stageBeforeTransport(prepared(purpose = ExtrinsicManager.PreparedPurpose.LEGACY_MIGRATION)).operationKind)
    }
    @Test fun hashMismatchIsRejectedBeforeAccountReadOrWrite() = runTest {
        rejected("SORA2_PENDING_PREPARED_HASH_MISMATCH") { coordinator.stageBeforeTransport(prepared(hash = "0x${"ff".repeat(32)}")) }
        assertTrue(rows.isEmpty()); coVerify(exactly = 0) { dao.hasActiveDeletionOperation() }
    }
    @Test fun negativeBirthBlockCannotBeJournaled() = runTest {
        rejected("SORA2_PENDING_ERA_INVALID") { coordinator.stageBeforeTransport(prepared(runtime.copy(finalizedBlockNumber = -1))) }
        assertTrue(rows.isEmpty())
    }
    @Test fun mortalEraOverflowCannotBeJournaled() = runTest {
        rejected("SORA2_PENDING_ERA_OVERFLOW") { coordinator.stageBeforeTransport(prepared(runtime.copy(finalizedBlockNumber = Long.MAX_VALUE))) }
        assertTrue(rows.isEmpty())
    }
    @Test fun blockedRecoveryPreventsAnyJournalMutation() = runTest {
        WalletRecoveryCapabilityGate.enterBlocked()
        rejected("WALLET_RECOVERY_READ_ONLY") { coordinator.stageBeforeTransport(prepared()) }
        assertTrue(rows.isEmpty()); coVerify(exactly = 0) { dao.hasActiveDeletionOperation() }
    }
    @Test fun activeWalletDeletionPreventsStaging() = runTest {
        coEvery { dao.hasActiveDeletionOperation() } returns true
        rejected("WALLET_DELETION_ACTIVE") { coordinator.stageBeforeTransport(prepared()) }; assertTrue(rows.isEmpty())
    }
    @Test fun absentWalletIdentityCannotStage() = runTest {
        coEvery { dao.getWallet(walletId) } returns null
        rejected("SORA2_PENDING_WALLET_WITNESS_MISMATCH") { coordinator.stageBeforeTransport(prepared()) }
    }
    @Test fun unverifiedWalletCannotStage() = runTest {
        coEvery { dao.getWallet(walletId) } returns wallet.copy(migrationState = "PREPARED")
        rejected("SORA2_PENDING_WALLET_WITNESS_MISMATCH") { coordinator.stageBeforeTransport(prepared()) }
    }
    @Test fun missingLegacyAccountCannotStage() = runTest {
        coEvery { accountDao.getAccount(walletId) } returns null
        rejected("SORA2_PENDING_WALLET_WITNESS_MISMATCH") { coordinator.stageBeforeTransport(prepared()) }
    }
    @Test fun disabledNetworkAccountCannotStage() = runTest {
        coEvery { dao.getNetworkAccount(walletId, "sora2") } returns account.copy(enabled = false)
        rejected("SORA2_PENDING_WALLET_WITNESS_MISMATCH") { coordinator.stageBeforeTransport(prepared()) }
    }
    @Test fun mismatchedNetworkPublicKeyCannotStage() = runTest {
        coEvery { dao.getNetworkAccount(walletId, "sora2") } returns account.copy(publicKey = "ab".repeat(32))
        rejected("SORA2_PENDING_WALLET_WITNESS_MISMATCH") { coordinator.stageBeforeTransport(prepared()) }
    }
    @Test fun mismatchedNetworkAddressCannotStage() = runTest {
        coEvery { dao.getNetworkAccount(walletId, "sora2") } returns account.copy(address = "another-wallet")
        rejected("SORA2_PENDING_WALLET_WITNESS_MISMATCH") { coordinator.stageBeforeTransport(prepared()) }
    }
    @Test fun duplicateHashIsNeverJournaledOrKickedTwice() = runTest {
        coordinator.stageBeforeTransport(prepared())
        val failure = runCatching { coordinator.stageBeforeTransport(prepared()) }.exceptionOrNull()
        assertTrue(failure is Sora2PendingSubmissionAlreadyJournaled); assertEquals(1, writes.size)
        verify(exactly = 1) { kick.kickAfterJournal() }
    }
    @Test fun failedPersistenceDoesNotScheduleRecovery() = runTest {
        coEvery { dao.insertSora2PendingSubmission(any()) } throws IllegalStateException("fixture disk full")
        rejected("fixture disk full") { coordinator.stageBeforeTransport(prepared()) }
        verify(exactly = 0) { kick.kickAfterJournal() }; assertTrue(rows.isEmpty())
    }
    @Test fun schedulerFailureCannotLoseDurableStagedWitness() = runTest {
        every { kick.kickAfterJournal() } throws IllegalStateException("fixture scheduler failed")
        val row = coordinator.stageBeforeTransport(prepared()); assertEquals(row, rows.values.single())
    }
    @Test fun schedulerCancellationCannotTurnStagingIntoTransportPermission() = runTest {
        every { kick.kickAfterJournal() } throws CancellationException("fixture cancelled")
        val result = runCatching { coordinator.stageBeforeTransport(prepared()) }
        // Recovery scheduling is explicitly best effort after persistence, including shutdown.
        assertTrue(result.isSuccess); assertEquals(1, rows.size)
    }
    @Test fun armingPersistsAmbiguityBeforeSubmissionAndAcceptanceClearsIt() = runTest {
        val staged = coordinator.stageBeforeTransport(prepared()); val armed = coordinator.armForTransport(staged)
        assertEquals(Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN, rows[staged.localId]?.state)
        assertTrue(armed.submissionIsAmbiguous)
        val submitted = coordinator.markSubmitted(armed)
        assertFalse(submitted.submissionIsAmbiguous)
        assertEquals(listOf(Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT,
            Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN, Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY), writes)
        assertEquals(TransactionOutcome.Waiting(submitted.transactionHash, true), coordinator.currentOutcomeOrNull(walletId, submitted.transactionHash))
    }
    @Test fun repeatedAcceptanceDoesNotRewriteSameJournalState() = runTest {
        val armed = coordinator.armForTransport(coordinator.stageBeforeTransport(prepared()))
        val submitted = coordinator.markSubmitted(armed)
        assertEquals(submitted, coordinator.markSubmitted(armed)); assertEquals(3, writes.size)
    }
    @Test fun acceptanceWithoutDurableRowIsRejected() = runTest {
        val armed = coordinator.armForTransport(coordinator.stageBeforeTransport(prepared())); rows.clear()
        rejected("SORA2_PENDING_SUBMISSION_MISSING") { coordinator.markSubmitted(armed) }
    }
    @Test fun unarmedAcceptanceIsRejected() = runTest {
        val staged = coordinator.stageBeforeTransport(prepared())
        rejected("SORA2_PENDING_SUBMISSION_STATE_CHANGED") { coordinator.markSubmitted(staged) }
    }
    @Test fun acceptanceDoesNotOverwriteAlreadyFinalizedChainProof() = runTest {
        val armed = coordinator.armForTransport(coordinator.stageBeforeTransport(prepared()))
        val finalized = armed.copy(state = Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS, submissionIsAmbiguous = false,
            terminalBlockNumber = 1001L, terminalBlockHash = "0x${"12".repeat(32)}", terminalFinalizedHeight = 1002L)
        rows[armed.localId] = finalized
        assertEquals(finalized, coordinator.markSubmitted(armed)); assertEquals(2, writes.size)
    }
    @Test fun beforeTransportCancellationCanRemoveOnlyExactStagedRow() = runTest {
        val row = coordinator.stageBeforeTransport(prepared()); coordinator.removeDefinitelyBeforeTransport(row)
        assertTrue(rows.isEmpty())
    }
    @Test fun armedRowSurvivesStaleBeforeTransportCleanup() = runTest {
        val row = coordinator.stageBeforeTransport(prepared()); coordinator.armForTransport(row)
        assertTrue(runCatching { coordinator.removeDefinitelyBeforeTransport(row) }.isFailure)
        assertEquals(Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN, rows.values.single().state)
    }
    @Test fun unknownOutcomeReschedulesWithoutChangingRow() = runTest {
        val armed = coordinator.armForTransport(coordinator.stageBeforeTransport(prepared()))
        coordinator.kickAfterUnknownBestEffort(); assertEquals(armed, rows.values.single())
        verify(exactly = 3) { kick.kickAfterJournal() }
    }
    @Test fun legacyClaimCannotFollowUnresolvedGenericTransaction() = runTest {
        coordinator.armForTransport(coordinator.stageBeforeTransport(prepared()))
        rejected("SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED") { coordinator.stageBeforeTransport(
            prepared(purpose = ExtrinsicManager.PreparedPurpose.LEGACY_MIGRATION, bytes = "0x02030405")) }
        assertEquals(1, rows.size)
    }
    @Test fun polkamarktWitnessDoesNotBlockLegacyAdmission() = runTest {
        coordinator.armForTransport(coordinator.stageBeforeTransport(prepared(purpose = ExtrinsicManager.PreparedPurpose.POLKAMARKT)))
        coordinator.requireLegacyMigrationAdmissible(walletId)
    }
    @Test fun outcomeLookupDoesNotExposeAnotherWalletJournal() = runTest {
        val row = coordinator.stageBeforeTransport(prepared())
        assertNull(coordinator.currentOutcomeOrNull("another-wallet", row.transactionHash))
    }
    @Test fun malformedHashLookupDoesNotReadStorageOrNetwork() = runTest {
        assertNull(coordinator.currentOutcomeOrNull(walletId, "not-a-hash"))
        coVerify(exactly = 0) { dao.getSora2PendingSubmission(any()) }
        confirmVerified(rpc, runtimeManager)
    }
}
