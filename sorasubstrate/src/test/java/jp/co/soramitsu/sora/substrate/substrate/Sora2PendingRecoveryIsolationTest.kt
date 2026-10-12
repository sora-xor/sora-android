package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2CanonicalBlock
import jp.co.soramitsu.sora.substrate.runtime.Sora2FinalizedRuntimeIdentity
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * One transaction that cannot be checked must not keep the others waiting.
 *
 * A status pass used to be all or nothing: when the wallet of one selected row no longer passed
 * its witness, or the events of one included row could not be decoded, the pass threw and no row
 * of the batch was resolved. The transaction page, the duplicate question and the legacy-claim
 * gate of another account then stayed on "pending" for good. These tests run the production
 * coordinator over two journal rows of two accounts.
 */
class Sora2PendingRecoveryIsolationTest {

    private val codec = Sora2AddressCodec()

    private class Wallet(codec: Sora2AddressCodec, fill: Byte) {
        val publicKey = ByteArray(32) { fill }
        val id = checkNotNull(codec.toSoraAddressOrNull(publicKey))
        val publicKeyHex = publicKey.toHexString(withPrefix = false).lowercase()
    }

    private val broken = Wallet(codec, 7)
    private val healthy = Wallet(codec, 8)

    private fun row(
        wallet: Wallet,
        hash: String,
        birth: Long,
        specVersion: Int = 133,
        state: String = Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY,
    ) = Sora2PendingSubmissionLocal(
        localId = "sora2:${hash.removePrefix("0x")}",
        recoverySchemaVersion = Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION,
        walletId = wallet.id,
        networkId = Sora2PendingSubmissionLocal.NETWORK_SORA2,
        transactionHash = hash,
        accountId = wallet.publicKeyHex,
        publicKey = wallet.publicKeyHex,
        genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
        specVersion = specVersion,
        transactionVersion = 131,
        metadataSha256 = "1".repeat(64),
        typesSha256 = "2".repeat(64),
        eraBirthBlock = birth,
        eraDeathBlockExclusive = birth + 64,
        eraPeriod = 64,
        eraPhase = (birth % 64).toInt(),
        eraBirthBlockHash = blockHash(birth),
        operationKind = Sora2PendingSubmissionLocal.OPERATION_GENERIC,
        state = state,
        submissionIsAmbiguous = state == Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN,
        terminalBlockNumber = null,
        terminalBlockHash = null,
        terminalFinalizedHeight = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private val rows = linkedMapOf<String, Sora2PendingSubmissionLocal>()
    private val writtenStates = mutableListOf<Pair<String, String>>()

    private val dao = mockk<WalletIdentityDao>().also { dao ->
        coEvery { dao.getUnresolvedSora2PendingSubmissions() } answers {
            rows.values.filterNot { it.isAuthoritativelyTerminal }
        }
        coEvery { dao.hasActiveDeletionOperation() } returns false
        coEvery { dao.getMigrationJournal(any()) } returns null
        listOf(broken, healthy).forEach { wallet ->
            coEvery { dao.getWallet(wallet.id) } answers {
                WalletIdentityLocal(
                    walletId = wallet.id,
                    displayName = "Wallet",
                    secretSource = "MNEMONIC",
                    // The first wallet stopped passing the witness after its row was written.
                    migrationState = if (wallet === broken) "PENDING" else "VERIFIED",
                    derivationVersion = 1,
                )
            }
            coEvery { dao.getNetworkAccount(wallet.id, "sora2") } returns NetworkAccountLocal(
                walletId = wallet.id,
                networkId = "sora2",
                publicKey = wallet.publicKeyHex,
                address = wallet.id,
                derivationPath = "",
                derivationVersion = 0,
                enabled = true,
            )
        }
        coEvery { dao.getSora2PendingSubmission(any()) } answers { rows[firstArg()] }
        coEvery {
            dao.updateSora2PendingSubmission(any(), any(), any(), any(), any(), any(), any())
        } answers {
            val expected = firstArg<Sora2PendingSubmissionLocal>()
            val updated = expected.copy(
                state = secondArg(),
                submissionIsAmbiguous = thirdArg(),
                terminalBlockNumber = arg(3),
                terminalBlockHash = arg(4),
                terminalFinalizedHeight = arg(5),
                updatedAt = arg(6),
            )
            rows[expected.localId] = updated
            writtenStates += expected.transactionHash to updated.state
            updated
        }
    }
    private val accountDao = mockk<AccountDao>().also { accounts ->
        listOf(broken, healthy).forEach { wallet ->
            coEvery { accounts.getAccount(wallet.id) } returns SoraAccountLocal(wallet.id, "W")
        }
    }
    private val database = mockk<AppDatabase>().also {
        every { it.walletIdentityDao() } returns dao
        every { it.accountDao() } returns accountDao
    }
    private val runtimeManager = mockk<RuntimeManager>()
    private val rpc = mockk<Sora2BoundedRuntimeRpcClient>()
    private val details = InMemorySora2SubmissionDetailsStore()

    private fun coordinator() = Sora2PendingSubmissionCoordinator(
        database = database,
        runtimeManager = runtimeManager,
        runtimeRpcClient = rpc,
        addressCodec = codec,
        recoveryKick = {},
        detailsStore = details,
    )

    private fun chainAt(finalizedHeight: Long, included: Map<Long, Pair<String, Int>> = emptyMap()) {
        coEvery { rpc.getFinalizedIdentity() } returns Sora2FinalizedRuntimeIdentity(
            genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
            finalizedHash = blockHash(finalizedHeight),
            runtimeVersion = RuntimeVersion(134, 131),
            finalizedBlockNumber = finalizedHeight,
        )
        coEvery { rpc.getCanonicalBlock(any(), any(), any()) } answers {
            val height = firstArg<Long>()
            Sora2CanonicalBlock(
                blockHash = blockHash(height),
                blockNumber = height,
                matchingExtrinsicIndices = included[height]?.let { mapOf(it) } ?: emptyMap(),
            )
        }
    }

    private fun journal(vararg submissions: Sora2PendingSubmissionLocal) {
        submissions.forEach { rows[it.localId] = it }
    }

    @Test
    fun `a row whose wallet fails its witness does not hold back another account's row`() =
        runTest {
            journal(row(broken, HASH_A, BIRTH), row(healthy, HASH_B, BIRTH + 3))
            chainAt(finalizedHeight = BIRTH + 200)

            val pass = coordinator().recoverStatusOnly()

            // Before: SORA2_PENDING_WALLET_WITNESS_MISMATCH for the whole pass, every time, so
            // the second account's transaction stayed "pending, don't send it again" for good.
            assertEquals(2, pass.examined)
            assertEquals(1, pass.resolved)
            assertEquals(1, pass.failures)
            assertEquals(
                listOf(HASH_B to Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED),
                writtenStates,
            )
            // The row that could not be checked is exactly as it was.
            assertEquals(row(broken, HASH_A, BIRTH), rows.getValue("sora2:${"a1".repeat(32)}"))
        }

    @Test
    fun `an included row whose events cannot be decoded does not hold back the others`() =
        runTest {
            // The first row was signed with another runtime, and that runtime cannot be decoded.
            journal(
                row(healthy, HASH_A, BIRTH, specVersion = 132),
                row(healthy, HASH_B, BIRTH + 3),
            )
            chainAt(finalizedHeight = BIRTH + 200, included = mapOf(BIRTH + 5 to (HASH_A to 2)))
            coEvery {
                runtimeManager.getReviewedRecoveryRuntimeSnapshotOrNull(
                    any(), 132, any(), any(), any(), any(),
                )
            } throws IllegalStateException("SORA2_RECOVERY_EVENTS_INVALID")

            val pass = coordinator().recoverStatusOnly()

            assertEquals(1, pass.resolved)
            assertEquals(1, pass.failures)
            assertEquals(
                listOf(HASH_B to Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED),
                writtenStates,
            )
            // An included transaction is never guessed: it is neither expired nor final.
            assertEquals(
                Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY,
                rows.getValue("sora2:${"a1".repeat(32)}").state,
            )
        }

    @Test
    fun `a screen's check looks at its own transaction alone`() = runTest {
        journal(row(broken, HASH_A, BIRTH), row(healthy, HASH_B, BIRTH + 100))
        chainAt(finalizedHeight = BIRTH + 300)

        val pass = coordinator().recoverStatusOnlyFor(healthy.id, HASH_B)

        assertEquals(1, pass?.examined)
        assertEquals(1, pass?.resolved)
        assertEquals(0, pass?.failures)
        assertEquals(
            listOf(HASH_B to Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED),
            writtenStates,
        )
        // Only the 64 blocks of its own validity window were read, none of the other row's.
        coVerify(exactly = 64) { rpc.getCanonicalBlock(any(), any(), any()) }
        coVerify(exactly = 0) { rpc.getCanonicalBlock(BIRTH, any(), any()) }
    }

    @Test
    fun `a check asked for one transaction reports that transaction's own failure`() = runTest {
        journal(row(broken, HASH_A, BIRTH), row(healthy, HASH_B, BIRTH + 3))
        chainAt(finalizedHeight = BIRTH + 200)

        try {
            coordinator().recoverStatusOnlyFor(broken.id, HASH_A)
            fail("the failed witness of the requested transaction is the result of the check")
        } catch (error: IllegalStateException) {
            assertEquals("SORA2_PENDING_WALLET_WITNESS_MISMATCH", error.message)
        }
        assertEquals(emptyList<Pair<String, String>>(), writtenStates)
    }

    @Test
    fun `nothing is asked when the journal has no unresolved row for the transaction`() = runTest {
        journal(
            row(
                healthy,
                HASH_B,
                BIRTH,
                state = Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED,
            ).copy(terminalFinalizedHeight = BIRTH + 70)
        )
        val coordinator = coordinator()

        // Final already, unknown, not a hash, and a row of another account.
        assertNull(coordinator.recoverStatusOnlyFor(healthy.id, HASH_B))
        assertNull(coordinator.recoverStatusOnlyFor(healthy.id, HASH_A))
        assertNull(coordinator.recoverStatusOnlyFor(healthy.id, "not-a-hash"))
        assertNull(coordinator.recoverStatusOnlyFor(broken.id, HASH_B))

        coVerify(exactly = 0) { rpc.getFinalizedIdentity() }
    }

    @Test
    fun `the journal row is reported as the outcome every screen shows`() = runTest {
        val coordinator = coordinator()
        fun put(state: String, block: String? = null) = journal(
            row(healthy, HASH_B, BIRTH, state = state).copy(
                terminalBlockNumber = block?.let { BIRTH + 5 },
                terminalBlockHash = block,
                terminalFinalizedHeight = if (state in FINAL_STATES) BIRTH + 70 else null,
            )
        )

        put(Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY)
        assertEquals(
            TransactionOutcome.Waiting(HASH_B, acceptedByNode = true),
            coordinator.currentOutcomeOrNull(healthy.id, HASH_B),
        )

        // The phone never got the node's answer: even its receipt is unknown.
        put(Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN)
        assertEquals(
            TransactionOutcome.Waiting(HASH_B, acceptedByNode = false),
            coordinator.currentOutcomeOrNull(healthy.id, HASH_B),
        )

        details.recordNodeRefusal(
            healthy.id,
            HASH_B,
            Sora2NodeRefusal(TransactionNodeRefusal.CANNOT_PAY_FEE, 1010, "Inability to pay"),
        )
        assertEquals(
            TransactionOutcome.RefusedByNode(
                HASH_B,
                TransactionNodeRefusal.CANNOT_PAY_FEE,
                "Inability to pay",
            ),
            coordinator.currentOutcomeOrNull(healthy.id, HASH_B),
        )

        // Chain proof wins over a recorded refusal.
        put(Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS, block = blockHash(BIRTH + 5))
        assertEquals(
            TransactionOutcome.Confirmed(HASH_B, blockHash(BIRTH + 5)),
            coordinator.currentOutcomeOrNull(healthy.id, HASH_B),
        )

        details.recordChainFailure(healthy.id, HASH_B, "IrohaMigration.AccountNotFound")
        put(Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE, block = blockHash(BIRTH + 5))
        assertEquals(
            TransactionOutcome.FailedOnChain(
                txHash = HASH_B,
                blockHash = blockHash(BIRTH + 5),
                reason = TransactionChainFailure.EXECUTION_FAILED,
                detail = "IrohaMigration.AccountNotFound",
            ),
            coordinator.currentOutcomeOrNull(healthy.id, HASH_B),
        )

        put(Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED)
        assertEquals(
            TransactionOutcome.FailedOnChain(
                txHash = HASH_B,
                blockHash = null,
                reason = TransactionChainFailure.NOT_INCLUDED,
            ),
            coordinator.currentOutcomeOrNull(healthy.id, HASH_B),
        )

        // Another account's row, and a transaction this device never sent, are not reported.
        assertNull(coordinator.currentOutcomeOrNull(broken.id, HASH_B))
        assertNull(coordinator.currentOutcomeOrNull(healthy.id, HASH_A))
        // Reading the journal never asks the network.
        coVerify(exactly = 0) { rpc.getFinalizedIdentity() }
    }

    @Test
    fun `the newest transaction of a kind is found from its description`() = runTest {
        val coordinator = coordinator()
        assertNull(
            coordinator.latestSubmissionHashOrNull(
                healthy.id,
                Sora2SubmissionActivity.Kind.LEGACY_CLAIM,
            )
        )
        details.entries[healthy.id to HASH_A] = Sora2SubmissionDetails(
            walletId = healthy.id,
            transactionHash = HASH_A,
            createdAt = 10L,
            activity = Sora2SubmissionActivity.legacyClaim(),
        )
        details.entries[healthy.id to HASH_B] = Sora2SubmissionDetails(
            walletId = healthy.id,
            transactionHash = HASH_B,
            createdAt = 20L,
            activity = Sora2SubmissionActivity.legacyClaim(),
        )
        details.entries[healthy.id to HASH_C] = Sora2SubmissionDetails(
            walletId = healthy.id,
            transactionHash = HASH_C,
            createdAt = 30L,
            activity = Sora2SubmissionActivity.referralSetReferrer("0x02", "peer"),
        )

        assertEquals(
            HASH_B,
            coordinator.latestSubmissionHashOrNull(
                healthy.id,
                Sora2SubmissionActivity.Kind.LEGACY_CLAIM,
            ),
        )
        assertNull(
            coordinator.latestSubmissionHashOrNull(
                broken.id,
                Sora2SubmissionActivity.Kind.LEGACY_CLAIM,
            )
        )
    }

    private companion object {
        const val BIRTH = 27_973_000L
        val HASH_A = "0x${"a1".repeat(32)}"
        val HASH_B = "0x${"b2".repeat(32)}"
        val HASH_C = "0x${"c3".repeat(32)}"
        val FINAL_STATES = Sora2PendingSubmissionLocal.TERMINAL_STATES

        fun blockHash(height: Long): String =
            "0x" + height.toString(16).padStart(64, '0').replaceFirst('0', 'e')
    }
}
