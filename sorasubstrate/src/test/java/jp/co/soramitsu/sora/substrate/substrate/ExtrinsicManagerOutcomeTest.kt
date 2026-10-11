package jp.co.soramitsu.sora.substrate.substrate

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
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
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2MutationRuntimeContext
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeRpcTransport
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * One outcome for every SORA2 submission (audit finding A5). The single
 * `author_submitExtrinsic` call goes through the production JSON-RPC client; only the HTTPS
 * transport underneath it is scripted.
 */
class ExtrinsicManagerOutcomeTest {

    @Test
    fun `an explicit JSON-RPC rejection is a failure and never submitted`() = runTest {
        val fixture = fixture(
            submitAnswer = rpcError(
                code = 1010,
                message = "Invalid Transaction",
                data = "Inability to pay some fees (e.g. account balance too low)",
            )
        )

        val outcome = sora2TransactionOutcome { fixture.submit() }

        assertEquals(
            TransactionOutcome.RefusedByNode(
                txHash = fixture.transactionHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = "Inability to pay some fees (e.g. account balance too low)",
            ),
            outcome,
        )
        // Shown as a failure: neither "submitted" nor "waiting".
        assertFalse(outcome is TransactionOutcome.Waiting)
        assertFalse(outcome is TransactionOutcome.Confirmed)
        // The refusal is remembered for Activity; the journal row is neither removed nor
        // marked submitted, and nobody waits for a finality that cannot come.
        assertEquals(TransactionNodeRefusal.CANNOT_PAY_FEE, fixture.recordedRefusal.captured.reason)
        assertEquals(1010L, fixture.recordedRefusal.captured.code)
        coVerify(exactly = 0) { fixture.coordinator.markSubmitted(any()) }
        coVerify(exactly = 0) { fixture.coordinator.removeDefinitelyBeforeTransport(any()) }
        coVerify(exactly = 0) { fixture.coordinator.awaitTerminalStatus(any(), any()) }
        assertEquals(1, fixture.submitRequests())
    }

    @Test
    fun `the refusal itself is raised as a definitive error by the manager`() = runTest {
        val fixture = fixture(
            submitAnswer = rpcError(1016, "Immediately Dropped", "The transaction pool is full")
        )

        val error = captureFailure { fixture.submit() }

        assertTrue(error is ExtrinsicRefusedByNodeException)
        assertFalse(error is ExtrinsicSubmissionUnknown)
        assertEquals(
            TransactionNodeRefusal.NETWORK_BUSY,
            (error as ExtrinsicRefusedByNodeException).refusal.reason,
        )
        assertEquals(fixture.transactionHash, error.transactionHash)
    }

    @Test
    fun `answers that do not prove a refusal stay waiting`() = runTest {
        listOf(
            // The node has seen this exact transaction before.
            rpcError(1013, "Transaction Already Imported", null),
            rpcError(1012, "Transaction is temporarily banned", null),
            // The nonce is used, possibly by this very transaction.
            rpcError(1010, "Invalid Transaction", "Transaction is outdated"),
            // Not a transaction-pool answer.
            rpcError(-32603, "Internal error", null),
            rpcError(4003, "State already discarded for 0x00", null),
        ).forEach { answer ->
            val fixture = fixture(submitAnswer = answer)

            val outcome = sora2TransactionOutcome { fixture.submit() }

            assertEquals(
                answer,
                TransactionOutcome.Waiting(fixture.transactionHash, acceptedByNode = false),
                outcome,
            )
            assertFalse(fixture.recordedRefusal.isCaptured)
        }
    }

    @Test
    fun `a lost connection during submission is waiting and not a refusal`() = runTest {
        val fixture = fixture(submitAnswer = null)

        val outcome = sora2TransactionOutcome { fixture.submit() }

        assertEquals(
            TransactionOutcome.Waiting(fixture.transactionHash, acceptedByNode = false),
            outcome,
        )
    }

    @Test
    fun `an accepted transaction whose finality wait ends early is waiting`() = runTest {
        val fixture = fixture(submitAnswer = ACCEPTED)
        coEvery { fixture.coordinator.awaitTerminalStatus(any(), any()) } throws
            Sora2FinalityPendingException()

        val outcome = sora2TransactionOutcome { fixture.submit() }

        assertEquals(
            TransactionOutcome.Waiting(fixture.transactionHash, acceptedByNode = true),
            outcome,
        )
    }

    @Test
    fun `finalized results map to confirmed and failed on chain`() = runTest {
        val block = "0x${"33".repeat(32)}"
        val confirmed = fixture(submitAnswer = ACCEPTED)
        coEvery { confirmed.coordinator.awaitTerminalStatus(any(), any()) } returns
            ExtrinsicSubmitStatus(true, confirmed.transactionHash, block)
        assertEquals(
            TransactionOutcome.Confirmed(confirmed.transactionHash, block),
            sora2TransactionOutcome { confirmed.submit() },
        )

        val failed = fixture(submitAnswer = ACCEPTED)
        coEvery { failed.coordinator.awaitTerminalStatus(any(), any()) } returns
            ExtrinsicSubmitStatus(
                success = false,
                txHash = failed.transactionHash,
                blockHash = block,
                failureDetail = "LiquidityProxy.SlippageNotTolerated",
            )
        assertEquals(
            TransactionOutcome.FailedOnChain(
                txHash = failed.transactionHash,
                blockHash = block,
                reason = TransactionChainFailure.EXECUTION_FAILED,
                detail = "LiquidityProxy.SlippageNotTolerated",
            ),
            sora2TransactionOutcome { failed.submit() },
        )

        val expired = fixture(submitAnswer = ACCEPTED)
        coEvery { expired.coordinator.awaitTerminalStatus(any(), any()) } returns
            ExtrinsicSubmitStatus(false, expired.transactionHash, null)
        assertEquals(
            TransactionOutcome.FailedOnChain(
                txHash = expired.transactionHash,
                blockHash = null,
                reason = TransactionChainFailure.NOT_INCLUDED,
            ),
            sora2TransactionOutcome { expired.submit() },
        )
    }

    @Test
    fun `an error before transport is not an outcome`() = runTest {
        val fixture = fixture(submitAnswer = ACCEPTED)
        coEvery { fixture.runtimeManager.getMutationRuntimeContext() } throws
            IOException("BOUNDED_HTTP_IO")

        val error = captureFailure { sora2TransactionOutcome { fixture.submit() } }

        assertNull(error.toTransactionOutcomeOrNull())
        assertEquals(0, fixture.submitRequests())
    }

    @Test
    fun `the activity is stored with the journal row before transport`() = runTest {
        val fixture = fixture(submitAnswer = ACCEPTED)
        val order = mutableListOf<String>()
        coEvery { fixture.coordinator.recordActivity(any(), any()) } answers {
            order += "activity:${fixture.submitRequests()}"
        }
        coEvery { fixture.coordinator.awaitTerminalStatus(any(), any()) } returns
            ExtrinsicSubmitStatus(true, fixture.transactionHash, "0x${"33".repeat(32)}")

        fixture.submit(SWAP)

        // Recorded while no submission request had been made yet.
        assertEquals(listOf("activity:0"), order)
        assertEquals(1, fixture.submitRequests())
    }

    @Test
    fun `the same action still waiting needs a deliberate second confirmation`() = runTest {
        val fixture = fixture(submitAnswer = ACCEPTED)
        coEvery { fixture.coordinator.findWaitingSameAction(WALLET_ID, any()) } returns
            Sora2SubmissionDetails(
                walletId = WALLET_ID,
                transactionHash = "0x${"77".repeat(32)}",
                createdAt = 42L,
                activity = SWAP,
            )
        coEvery { fixture.coordinator.awaitTerminalStatus(any(), any()) } returns
            ExtrinsicSubmitStatus(true, fixture.transactionHash, "0x${"33".repeat(32)}")

        val error = captureFailure { fixture.submit(SWAP) }

        assertTrue(error is DuplicateWaitingTransactionException)
        assertEquals("0x${"77".repeat(32)}", (error as DuplicateWaitingTransactionException).waitingTxHash)
        assertEquals(42L, error.waitingSinceMillis)
        // Nothing was signed, journaled or sent.
        coVerify(exactly = 0) { fixture.runtimeManager.getMutationRuntimeContext() }
        coVerify(exactly = 0) { fixture.coordinator.stageBeforeTransport(any()) }
        assertEquals(0, fixture.submitRequests())

        // After the user confirmed again the same request goes through.
        val status = fixture.submit(SWAP.copy(duplicateConfirmed = true))
        assertTrue(status.success)
        assertEquals(1, fixture.submitRequests())
    }

    @Test
    fun `a failed duplicate check never blocks the transaction`() = runTest {
        val fixture = fixture(submitAnswer = ACCEPTED)
        coEvery { fixture.coordinator.findWaitingSameAction(any(), any()) } throws
            IllegalStateException("SORA2_PENDING_SUBMISSION_LIMIT_EXCEEDED")
        coEvery { fixture.coordinator.awaitTerminalStatus(any(), any()) } returns
            ExtrinsicSubmitStatus(true, fixture.transactionHash, "0x${"33".repeat(32)}")

        assertTrue(fixture.submit(SWAP).success)
    }

    private class Fixture(
        val manager: ExtrinsicManager,
        val coordinator: Sora2PendingSubmissionCoordinator,
        val runtimeManager: RuntimeManager,
        val keypair: Sr25519Keypair,
        val transactionHash: String,
        val recordedRefusal: io.mockk.CapturingSlot<Sora2NodeRefusal>,
        val submitRequests: () -> Int,
    ) {
        suspend fun submit(activity: Sora2SubmissionActivity? = null): ExtrinsicSubmitStatus =
            manager.submitAndWatchExtrinsic(
                from = WALLET_ID,
                keypair = keypair,
                activity = activity,
            ) {}
    }

    /** [submitAnswer] is the node's JSON-RPC answer without id; null closes the connection. */
    private fun fixture(submitAnswer: String?): Fixture {
        val encoded = "0x00"
        val transactionHash = encoded.extrinsicHash().canonicalExtrinsicHash()
        var submitRequests = 0
        val transport = Sora2RuntimeRpcTransport { requestBody, _ ->
            val request = JsonParser.parseString(requestBody).asJsonObject
            check(request.get("method").asString == "author_submitExtrinsic")
            submitRequests += 1
            val answer = submitAnswer ?: throw IOException("BOUNDED_HTTP_IO")
            val id = request.get("id").asLong
            """{"jsonrpc":"2.0","id":$id,${answer.replace(HASH_PLACEHOLDER, transactionHash)}}"""
        }
        val rpcClient = Sora2BoundedRuntimeRpcClient(transport = transport, gson = Gson())

        val factory = mockk<ExtrinsicBuilderFactory>()
        val runtimeManager = mockk<RuntimeManager>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val coordinator = mockk<Sora2PendingSubmissionCoordinator>()
        val staged = mockk<Sora2PendingSubmissionLocal>()
        val armed = mockk<Sora2PendingSubmissionLocal>()
        val submitted = mockk<Sora2PendingSubmissionLocal>()
        val builder = mockk<ExtrinsicBuilder>()
        val keypair = mockk<Sr25519Keypair>()
        val runtime = mutationContext()
        val recordedRefusal = slot<Sora2NodeRefusal>()

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { walletDao.hasActiveDeletionOperation() } returns false
        coEvery { walletDao.getMigrationJournal(any()) } returns null
        coEvery { accountDao.getAccount(WALLET_ID) } returns SoraAccountLocal(WALLET_ID, "Primary")
        coEvery { walletDao.getWallet(WALLET_ID) } returns WalletIdentityLocal(
            walletId = WALLET_ID,
            displayName = "Primary",
            secretSource = "MNEMONIC",
            migrationState = "VERIFIED",
            derivationVersion = 1,
        )
        coEvery { walletDao.getNetworkAccount(WALLET_ID, "sora2") } returns NetworkAccountLocal(
            walletId = WALLET_ID,
            networkId = "sora2",
            publicKey = "01",
            address = WALLET_ID,
            derivationPath = "",
            derivationVersion = 0,
            enabled = true,
        )
        coEvery { runtimeManager.getMutationRuntimeContext() } returns runtime
        coEvery {
            factory.createForSigning(WALLET_ID, keypair, runtime)
        } returns ExtrinsicBuilderFactory.ContextBoundExtrinsicBuilder(
            builder = builder,
            runtimeContext = runtime,
        )
        every { builder.build(false) } returns encoded
        coEvery { coordinator.findWaitingSameAction(any(), any()) } returns null
        coEvery { coordinator.stageBeforeTransport(any()) } returns staged
        coEvery { coordinator.recordActivity(staged, any()) } returns Unit
        coEvery { coordinator.armForTransport(staged) } returns armed
        coEvery { coordinator.recordNodeRefusal(armed, capture(recordedRefusal)) } returns Unit
        coEvery { coordinator.markSubmitted(armed) } returns submitted
        coEvery { coordinator.removeDefinitelyBeforeTransport(staged) } returns Unit
        every { coordinator.kickAfterUnknownBestEffort() } returns Unit
        every { submitted.transactionHash } returns transactionHash

        return Fixture(
            manager = ExtrinsicManager(
                factory = factory,
                runtimeManager = runtimeManager,
                database = database,
                pendingSubmissionCoordinator = coordinator,
                runtimeRpcClient = rpcClient,
            ),
            coordinator = coordinator,
            runtimeManager = runtimeManager,
            keypair = keypair,
            transactionHash = transactionHash,
            recordedRefusal = recordedRefusal,
            submitRequests = { submitRequests },
        )
    }

    private fun mutationContext() = Sora2MutationRuntimeContext(
        snapshot = mockk<RuntimeSnapshot>(),
        runtimeVersion = mockk<RuntimeVersion>().also { version ->
            every { version.specVersion } returns 133
            every { version.transactionVersion } returns 131
        },
        genesisHash = "0x${"aa".repeat(32)}",
        finalizedHash = "0x${"11".repeat(32)}",
        metadataSha256 = "a".repeat(64),
        typesSha256 = "b".repeat(64),
        finalizedBlockNumber = 64,
    )

    private fun rpcError(code: Int, message: String, data: String?): String {
        val dataField = data?.let { ""","data":"$it"""" }.orEmpty()
        return """"error":{"code":$code,"message":"$message"$dataField}"""
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable = try {
        block()
        fail("Expected failure")
        error("unreachable")
    } catch (error: Throwable) {
        error
    }

    private companion object {
        const val WALLET_ID = "cnRetainedWallet"
        const val HASH_PLACEHOLDER = "{hash}"
        const val ACCEPTED = """"result":"{hash}""""
        val SWAP = Sora2SubmissionActivity(
            kind = Sora2SubmissionActivity.Kind.SWAP,
            tokenIds = listOf("0x02", "0x04"),
            amounts = listOf("1", "2"),
        )
    }
}
