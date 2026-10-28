package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.*
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal.Companion.STATE_SIGNED_BEFORE_TRANSPORT
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal.Companion.STATE_SUBMISSION_UNKNOWN
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal.Companion.STATE_SUBMITTED_AWAITING_FINALITY
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal.Companion.STATE_FINALIZED_SUCCESS
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal.Companion.STATE_FINALIZED_FAILURE
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal.Companion.STATE_EXPIRED_NOT_INCLUDED
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal.Companion.RECOVERY_SCHEMA_VERSION
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal.Companion.NETWORK_SORA2
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal.Companion.OPERATION_GENERIC
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real Room transactions preserve durable recovery witnesses through the transport lifecycle. */
@RunWith(AndroidJUnit4::class)
class Sora2PendingSubmissionLifecycleTest {
    private lateinit var db: AppDatabase
    private val dao get() = db.walletIdentityDao()
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        db.openHelper.writableDatabase.execSQL("INSERT INTO accounts(substrateAddress, accountName) VALUES('fixture-wallet', 'Fixture')")
        dao.insertWallet(WalletIdentityLocal("fixture-wallet", "Fixture", "MNEMONIC", "VERIFIED", 1))
    }
    @After fun cleanup() { db.close() }

    @Test fun signedUnknownSubmittedAndFinalizedWitnessIsDurableWithMonotonicTime() = runBlocking {
        val signed = witness(); dao.insertSora2PendingSubmission(signed)
        assertEquals(listOf(signed), dao.getSora2SubmissionOverlayRows(signed.walletId))
        val unknown = dao.updateSora2PendingSubmission(signed, STATE_SUBMISSION_UNKNOWN, true, updatedAt = 5)
        assertEquals(10, unknown.updatedAt)
        val submitted = dao.updateSora2PendingSubmission(unknown, STATE_SUBMITTED_AWAITING_FINALITY, false, updatedAt = 20)
        val terminal = dao.updateSora2PendingSubmission(submitted, STATE_FINALIZED_SUCCESS, false, 80, hash("66"), 100, 30)
        assertTrue(terminal.isAuthoritativelyTerminal)
        assertEquals(terminal, dao.getSora2PendingSubmission(signed.localId))
        assertEquals(listOf(terminal), dao.getSora2SubmissionOverlayRows(signed.walletId))
        assertEquals(emptyList<Sora2PendingSubmissionLocal>(), dao.getSora2SubmissionOverlayRows("another-wallet"))
    }

    @Test fun finalityFailureAndExpiryAreAcceptedFromEveryUnresolvedState() = runBlocking {
        var index = 1
        for (start in listOf(STATE_SIGNED_BEFORE_TRANSPORT, STATE_SUBMISSION_UNKNOWN, STATE_SUBMITTED_AWAITING_FINALITY)) {
            for (terminal in listOf(STATE_FINALIZED_FAILURE, STATE_EXPIRED_NOT_INCLUDED)) {
                val row = witness(index++).copy(state = start, submissionIsAmbiguous = start == STATE_SUBMISSION_UNKNOWN)
                dao.insertSora2PendingSubmission(row)
                val result = dao.updateSora2PendingSubmission(row, terminal, false,
                    if (terminal == STATE_FINALIZED_FAILURE) 90 else null,
                    if (terminal == STATE_FINALIZED_FAILURE) hash("77") else null,
                    128, 20)
                assertEquals(terminal, result.state)
                assertEquals(result, dao.getSora2PendingSubmission(row.localId))
            }
        }
    }

    @Test fun staleMissingAndTerminalUpdatesCannotOverwriteRecoveryEvidence() = runBlocking {
        val row = witness(); dao.insertSora2PendingSubmission(row)
        assertFailure("SORA2_PENDING_SUBMISSION_MISSING") { dao.updateSora2PendingSubmission(witness(2), STATE_SUBMISSION_UNKNOWN, true, updatedAt = 20) }
        assertFailure("SORA2_PENDING_SUBMISSION_CHANGED") { dao.updateSora2PendingSubmission(row.copy(updatedAt = 11), STATE_SUBMISSION_UNKNOWN, true, updatedAt = 20) }
        assertFailure("SORA2_PENDING_TRANSITION_INVALID") { dao.updateSora2PendingSubmission(row, STATE_SUBMITTED_AWAITING_FINALITY, false, updatedAt = 20) }
        assertEquals(row, dao.getSora2PendingSubmission(row.localId))
        val terminal = dao.updateSora2PendingSubmission(row, STATE_EXPIRED_NOT_INCLUDED, false, terminalFinalizedHeight = 128, updatedAt = 20)
        assertFailure("SORA2_PENDING_TRANSITION_INVALID") { dao.updateSora2PendingSubmission(terminal, STATE_SUBMISSION_UNKNOWN, true, updatedAt = 30) }
        assertEquals(terminal, dao.getSora2PendingSubmission(row.localId))
    }

    @Test fun invalidFinalityProofRollsBackUpdate() = runBlocking {
        val row = witness(); dao.insertSora2PendingSubmission(row)
        assertFailure("SORA2_PENDING_TERMINAL_PROOF_INVALID") { dao.updateSora2PendingSubmission(row, STATE_FINALIZED_SUCCESS, false, 128, hash("66"), 200, 20) }
        assertFailure("SORA2_PENDING_EXPIRY_PROOF_INVALID") { dao.updateSora2PendingSubmission(row, STATE_EXPIRED_NOT_INCLUDED, false, terminalFinalizedHeight = 127, updatedAt = 20) }
        assertEquals(row, dao.getSora2PendingSubmission(row.localId))
    }

    @Test fun definitelyNotSubmittedDeletesOnlyExactSignedWitness() = runBlocking {
        val row = witness(); dao.insertSora2PendingSubmission(row)
        assertFailure("SORA2_PENDING_SUBMISSION_MISSING") { dao.deleteSora2DefinitelyNotSubmitted(witness(2)) }
        assertFailure("SORA2_PENDING_SUBMISSION_NOT_DEFINITIVE") { dao.deleteSora2DefinitelyNotSubmitted(row.copy(updatedAt = 11)) }
        dao.deleteSora2DefinitelyNotSubmitted(row); assertNull(dao.getSora2PendingSubmission(row.localId))
        dao.insertSora2PendingSubmission(row)
        val unknown = dao.updateSora2PendingSubmission(row, STATE_SUBMISSION_UNKNOWN, true, updatedAt = 20)
        assertFailure("SORA2_PENDING_SUBMISSION_NOT_DEFINITIVE") { dao.deleteSora2DefinitelyNotSubmitted(unknown) }
        assertEquals(unknown, dao.getSora2PendingSubmission(row.localId))
    }

    @Test fun overlayRejectsInvalidWalletAndCorruptWitness() = runBlocking {
        assertFailure("SORA2_PENDING_OVERLAY_WALLET_INVALID") { dao.getSora2SubmissionOverlayRows(" ") }
        assertFailure("SORA2_PENDING_OVERLAY_WALLET_INVALID") { dao.getSora2SubmissionOverlayRows("x".repeat(1000)) }
        dao.insertSora2PendingSubmission(witness())
        db.openHelper.writableDatabase.execSQL("UPDATE sora2PendingSubmissions SET recoverySchemaVersion = 999")
        assertFailure("SORA2_PENDING_SCHEMA_INVALID") { dao.getSora2SubmissionOverlayRows("fixture-wallet") }
    }

    @Test fun everyRecoveryWitnessBoundaryRejectsMalformedIdentityBeforeInsertion() = runBlocking {
        val row = witness()
        val invalid = listOf(
            row.copy(recoverySchemaVersion = 2) to "SCHEMA", row.copy(walletId = "") to "SCOPE",
            row.copy(networkId = "other") to "SCOPE", row.copy(localId = "wrong") to "HASH",
            row.copy(accountId = "00".repeat(32)) to "ACCOUNT", row.copy(publicKey = "88".repeat(32)) to "ACCOUNT",
            row.copy(genesisHash = hash("00")) to "GENESIS", row.copy(specVersion = 0) to "RUNTIME",
            row.copy(metadataSha256 = "00".repeat(32)) to "RUNTIME", row.copy(eraPeriod = 32) to "ERA",
            row.copy(eraBirthBlock = Long.MAX_VALUE) to "ERA", row.copy(operationKind = "OTHER") to "OPERATION",
            row.copy(state = "UNKNOWN") to "STATE", row.copy(submissionIsAmbiguous = true) to "STATE",
            row.copy(terminalFinalizedHeight = 100) to "PRETERMINAL_PROOF", row.copy(createdAt = 0) to "TIMESTAMP",
            row.copy(updatedAt = 9) to "TIMESTAMP",
        )
        for ((bad, code) in invalid) assertFailure("SORA2_PENDING_${code}_INVALID") { dao.insertSora2PendingSubmission(bad) }
        assertNull(dao.getSora2PendingSubmission(row.localId))
    }

    @Test fun derivedAccountsPromoteWalletAndUpdateExistingRowsAtomically() = runBlocking {
        val account = NetworkAccountLocal("fixture-wallet", "sora2", "11".repeat(32), "address-one", "path-one", 1, true)
        dao.installDerivedAccounts("fixture-wallet", "SEED", listOf(account))
        assertEquals("SEED", dao.getWallet("fixture-wallet")!!.secretSource)
        val changed = account.copy(publicKey = "22".repeat(32), address = "address-two", derivationPath = "path-two", derivationVersion = 2, enabled = false)
        dao.upsertNetworkAccounts(listOf(changed))
        assertEquals(listOf(changed), dao.getNetworkAccounts("fixture-wallet"))
        dao.upsertWallet(dao.getWallet("fixture-wallet")!!.copy(displayName = "Updated"))
        assertEquals("Updated", dao.getWallet("fixture-wallet")!!.displayName)
    }

    @Test fun failureJournalCanBeInsertedAndCannotReplaceUnrelatedCompletedReceipt() = runBlocking {
        val row = WalletMigrationJournalLocal("fixture-migration", "RECOVERY_REQUIRED", 1, 0, "fixture-wallet", "hash", "FIXTURE_FAILURE", 10, 20)
        assertTrue(dao.recordMigrationFailure(row))
        assertEquals(row, dao.getMigrationJournal(row.migrationId))
        assertFailure("MIGRATION_FAILURE_JOURNAL_INVALID") { dao.recordMigrationFailure(row.copy(verifiedAccountCount = 1)) }
        assertEquals(row, dao.getMigrationJournal(row.migrationId))
    }

    private suspend fun assertFailure(code: String, block: suspend () -> Unit) {
        val error = try { block(); null } catch (e: IllegalStateException) { e }
        assertNotNull("Expected $code", error); assertEquals(code, error!!.message)
    }
    private fun hash(byte: String) = "0x" + byte.repeat(32)
    private fun witness(index: Int = 1) = Sora2PendingSubmissionLocal(
        localId = "sora2:" + index.toString(16).padStart(64, '0'), recoverySchemaVersion = RECOVERY_SCHEMA_VERSION,
        walletId = "fixture-wallet", networkId = NETWORK_SORA2, transactionHash = "0x" + index.toString(16).padStart(64, '0'),
        accountId = "11".repeat(32), publicKey = "11".repeat(32), genesisHash = hash("22"), specVersion = 130, transactionVersion = 130,
        metadataSha256 = "33".repeat(32), typesSha256 = "44".repeat(32), eraBirthBlock = 64, eraDeathBlockExclusive = 128,
        eraPeriod = 64, eraPhase = 0, eraBirthBlockHash = hash("55"), operationKind = OPERATION_GENERIC,
        state = STATE_SIGNED_BEFORE_TRANSPORT, submissionIsAmbiguous = false, terminalBlockNumber = null, terminalBlockHash = null,
        terminalFinalizedHeight = null, createdAt = 10, updatedAt = 10,
    )
}
