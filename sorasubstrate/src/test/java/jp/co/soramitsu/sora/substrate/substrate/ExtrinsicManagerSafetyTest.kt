package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.QualifiedSora2MutationRuntime
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2MutationRuntimeContext
import jp.co.soramitsu.sora.substrate.runtime.VerifiedSora2Runtime
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExtrinsicManagerSafetyTest {

    /**
     * The legacy-claim gate through the production manager and the production coordinator. Only
     * the database, the description store and the network are doubles. [afterGate] is thrown by
     * the first step after the gate (reading the signing runtime), so a claim that passed the
     * gate ends with it and one the gate stopped never gets there.
     */
    private class ClaimGateFixture(
        unresolved: List<Sora2PendingSubmissionLocal>,
        val details: InMemorySora2SubmissionDetailsStore = InMemorySora2SubmissionDetailsStore(),
    ) {
        val runtime = mockk<RuntimeManager>()
        val walletDao = mockk<WalletIdentityDao>()
        val runtimeRpcClient = mockk<Sora2BoundedRuntimeRpcClient>()
        val afterGate = java.io.IOException("BOUNDED_HTTP_IO")
        private val database = mockk<AppDatabase>()
        private val accountDao = mockk<AccountDao>()

        init {
            every { database.accountDao() } returns accountDao
            every { database.walletIdentityDao() } returns walletDao
            coEvery { walletDao.hasActiveDeletionOperation() } returns false
            coEvery { walletDao.getMigrationJournal(any()) } returns null
            coEvery { accountDao.getAccount(WALLET_ID) } returns
                SoraAccountLocal(WALLET_ID, "Primary")
            coEvery { walletDao.getWallet(WALLET_ID) } returns
                WalletIdentityLocal(
                    walletId = WALLET_ID,
                    displayName = "Primary",
                    secretSource = "MNEMONIC",
                    migrationState = "VERIFIED",
                    derivationVersion = 1,
                )
            coEvery { walletDao.getNetworkAccount(WALLET_ID, "sora2") } returns
                NetworkAccountLocal(
                    walletId = WALLET_ID,
                    networkId = "sora2",
                    publicKey = "01",
                    address = WALLET_ID,
                    derivationPath = "",
                    derivationVersion = 0,
                    enabled = true,
                )
            coEvery { walletDao.getUnresolvedSora2PendingSubmissions() } returns unresolved
            coEvery { runtime.getMutationRuntimeContext() } throws afterGate
        }

        suspend fun claim() {
            ExtrinsicManager(
                factory = mockk(),
                runtimeManager = runtime,
                database = database,
                pendingSubmissionCoordinator = Sora2PendingSubmissionCoordinator(
                    database = database,
                    runtimeManager = runtime,
                    runtimeRpcClient = runtimeRpcClient,
                    addressCodec = jp.co.soramitsu.common.account.Sora2AddressCodec(),
                    recoveryKick = {},
                    detailsStore = details,
                ),
                runtimeRpcClient = runtimeRpcClient,
            ).submitLegacyMigrationAndWaitExtrinsic(
                from = WALLET_ID,
                keypair = mockk(),
            ) {}
        }
    }

    private fun unresolvedRow(
        hash: String,
        state: String,
        walletId: String = WALLET_ID,
    ): Sora2PendingSubmissionLocal = mockk<Sora2PendingSubmissionLocal>().also { row ->
        every { row.walletId } returns walletId
        every { row.transactionHash } returns hash
        every { row.state } returns state
        every { row.operationKind } returns Sora2PendingSubmissionLocal.OPERATION_GENERIC
    }

    private val busy = Sora2NodeRefusal(
        reason = jp.co.soramitsu.common.domain.TransactionNodeRefusal.NETWORK_BUSY,
        code = 1016L,
        message = "Immediately Dropped",
    )

    @Test
    fun `legacy migration refuses an unresolved witness before signing`() = runTest {
        val fixture = ClaimGateFixture(
            unresolved = listOf(
                unresolvedRow(HASH_1, Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN),
            ),
        )

        val error = captureFailure { fixture.claim() }

        assertEquals("SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED", error.message)
        coVerify(exactly = 0) { fixture.runtime.getMutationRuntimeContext() }
        coVerify(exactly = 0) { fixture.walletDao.insertSora2PendingSubmission(any()) }
        coVerify(exactly = 0) { fixture.runtimeRpcClient.submitExtrinsicOnce(any()) }
    }

    @Test
    fun `a claim the node refused does not hold back the next claim`() = runTest {
        // The node answered 1014 or 1016: "too busy ... please try again in a moment". The
        // refused row stays unresolved for its whole validity window (about six and a half
        // minutes), and the gate used to answer every further Submit in that time with "an
        // earlier transaction is still being checked", on a screen that cannot be left.
        val fixture = ClaimGateFixture(
            unresolved = listOf(
                unresolvedRow(HASH_1, Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN),
            ),
        )
        fixture.details.recordNodeRefusal(WALLET_ID, HASH_1, busy)

        val error = captureFailure { fixture.claim() }

        // The gate let the claim through: it went on to read the signing runtime.
        assertEquals(fixture.afterGate.toString(), error.deepestCause().toString())
        coVerify(exactly = 1) { fixture.runtime.getMutationRuntimeContext() }
    }

    @Test
    fun `a refused claim next to one that is still waiting does not open the gate`() = runTest {
        val fixture = ClaimGateFixture(
            unresolved = listOf(
                unresolvedRow(HASH_1, Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN),
                // Sent without an answer: it may be on its way.
                unresolvedRow(HASH_2, Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN),
            ),
        )
        fixture.details.recordNodeRefusal(WALLET_ID, HASH_1, busy)

        val error = captureFailure { fixture.claim() }

        assertEquals("SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED", error.message)
        coVerify(exactly = 0) { fixture.runtime.getMutationRuntimeContext() }
    }

    @Test
    fun `a transaction the node accepted holds the claim back whatever was recorded`() = runTest {
        // A refusal can only be recorded for a row that never left "submission unknown". Should
        // one ever stand next to an accepted row, the row decides: it may still be included.
        val fixture = ClaimGateFixture(
            unresolved = listOf(
                unresolvedRow(
                    HASH_1,
                    Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY,
                ),
            ),
        )
        fixture.details.recordNodeRefusal(WALLET_ID, HASH_1, busy)

        val error = captureFailure { fixture.claim() }

        assertEquals("SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED", error.message)
        coVerify(exactly = 0) { fixture.runtime.getMutationRuntimeContext() }
    }

    @Test
    fun `a refusal recorded for another account does not open this account's gate`() = runTest {
        val fixture = ClaimGateFixture(
            unresolved = listOf(
                unresolvedRow(HASH_1, Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN),
            ),
        )
        fixture.details.recordNodeRefusal("cnAnotherWallet", HASH_1, busy)

        val error = captureFailure { fixture.claim() }

        assertEquals("SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED", error.message)
    }

    @Test
    fun `bounded submission followed by unresolved finality is submission unknown`() = runTest {
        val calls = mockk<SubstrateCalls>()
        val factory = mockk<ExtrinsicBuilderFactory>()
        val runtime = mockk<RuntimeManager>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val pendingCoordinator = mockk<Sora2PendingSubmissionCoordinator>()
        val runtimeRpcClient = mockk<Sora2BoundedRuntimeRpcClient>()
        val staged = mockk<Sora2PendingSubmissionLocal>()
        val armed = mockk<Sora2PendingSubmissionLocal>()
        val submitted = mockk<Sora2PendingSubmissionLocal>()
        val builder = mockk<ExtrinsicBuilder>()
        val keypair = mockk<Sr25519Keypair>()
        val encoded = "0x00"
        val transactionHash = encoded.extrinsicHash()
        val signingRuntime = mutationContext(finalizedByte = "11")
        val currentRuntime = mutationContext(finalizedByte = "22")

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery { walletDao.hasActiveDeletionOperation() } returns false
        coEvery { walletDao.getMigrationJournal(any()) } returns null
        coEvery { accountDao.getAccount(WALLET_ID) } returns
            SoraAccountLocal(WALLET_ID, "Primary")
        coEvery { walletDao.getWallet(WALLET_ID) } returns
            WalletIdentityLocal(
                walletId = WALLET_ID,
                displayName = "Primary",
                secretSource = "MNEMONIC",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        coEvery { walletDao.getNetworkAccount(WALLET_ID, "sora2") } returns
            NetworkAccountLocal(
                walletId = WALLET_ID,
                networkId = "sora2",
                publicKey = "01",
                address = WALLET_ID,
                derivationPath = "",
                derivationVersion = 0,
                enabled = true,
            )
        coEvery {
            runtime.getMutationRuntimeContext()
        } returnsMany listOf(signingRuntime, currentRuntime)
        coEvery {
            factory.createForSigning(WALLET_ID, keypair, signingRuntime)
        } returns ExtrinsicBuilderFactory.ContextBoundExtrinsicBuilder(
            builder = builder,
            runtimeContext = signingRuntime,
        )
        every { builder.build(false) } returns encoded
        coEvery { pendingCoordinator.stageBeforeTransport(any()) } returns staged
        coEvery { pendingCoordinator.armForTransport(staged) } returns armed
        coEvery { pendingCoordinator.markSubmitted(armed) } returns submitted
        coEvery { pendingCoordinator.removeDefinitelyBeforeTransport(staged) } returns Unit
        every { pendingCoordinator.kickAfterUnknownBestEffort() } returns Unit
        every { submitted.transactionHash } returns transactionHash
        coEvery { runtimeRpcClient.submitExtrinsicOnce(encoded) } returns transactionHash
        coEvery { pendingCoordinator.awaitTerminalStatus(submitted) } throws
            IllegalStateException("SORA2_FINALITY_PENDING")

        val error = try {
            ExtrinsicManager(
                factory = factory,
                runtimeManager = runtime,
                database = database,
                pendingSubmissionCoordinator = pendingCoordinator,
                runtimeRpcClient = runtimeRpcClient,
            ).submitAndWatchExtrinsic(
                from = WALLET_ID,
                keypair = keypair,
            ) {}
            fail("Expected finality timeout to remain unresolved")
            null
        } catch (error: Throwable) {
            error
        }

        assertTrue(error is ExtrinsicSubmissionUnknownException)
        assertEquals(
            transactionHash.canonicalExtrinsicHash(),
            (error as ExtrinsicSubmissionUnknown).transactionHash,
        )
    }

    @Test
    fun `bounded HTTP failure after handoff is submission unknown with exact local hash`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false, false, false))
        val rpcError = IllegalStateException("socket closed after request write")
        coEvery { fixture.runtimeRpcClient.submitExtrinsicOnce(any()) } throws rpcError

        val error = captureFailure {
            fixture.manager.submitPreparedAndWait(fixture.prepared)
        }

        assertTrue(error is ExtrinsicSubmissionUnknownException)
        assertEquals(
            fixture.prepared.transactionHash,
            (error as ExtrinsicSubmissionUnknown).transactionHash,
        )
        assertTrue(error.cause === rpcError)
    }

    @Test
    fun `cancellation after bounded handoff is submission unknown cancellation`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false, false, false))
        val cancellation = CancellationException("screen disappeared after request write")
        coEvery { fixture.runtimeRpcClient.submitExtrinsicOnce(any()) } throws cancellation

        val error = captureFailure {
            fixture.manager.submitPreparedAndWait(fixture.prepared)
        }

        assertTrue(error is ExtrinsicSubmissionUnknownCancellation)
        assertEquals(
            fixture.prepared.transactionHash,
            (error as ExtrinsicSubmissionUnknown).transactionHash,
        )
        assertTrue(error.cause === cancellation)
    }

    @Test
    fun `bounded returned hash mismatch after handoff is submission unknown`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false, false, false))
        val mismatchedHash = "0x${"11".repeat(32)}"
        coEvery { fixture.runtimeRpcClient.submitExtrinsicOnce(any()) } returns mismatchedHash

        val error = captureFailure {
            fixture.manager.submitPreparedAndWait(fixture.prepared)
        }

        assertTrue(error is ExtrinsicSubmissionUnknownException)
        assertEquals(
            fixture.prepared.transactionHash,
            (error as ExtrinsicSubmissionUnknown).transactionHash,
        )
        assertEquals("EXTRINSIC_HASH_MISMATCH", error.cause?.message)
    }

    @Test
    fun `one shot RPC failure is submission unknown with exact local hash`() = runTest {
        val fixture = preparedFixture(activeDeletion = List(5) { false })
        val builder = mockk<ExtrinsicBuilder>()
        val keypair = mockk<Sr25519Keypair>()
        val rpcError = IllegalStateException("connection lost after request write")
        coEvery {
            fixture.factory.createForSigning(
                WALLET_ID,
                keypair,
                fixture.currentRuntime,
            )
        } returns ExtrinsicBuilderFactory.ContextBoundExtrinsicBuilder(
            builder = builder,
            runtimeContext = fixture.currentRuntime,
        )
        every { builder.build(false) } returns fixture.prepared.encoded
        coEvery {
            fixture.runtimeRpcClient.submitExtrinsicOnce(fixture.prepared.encoded)
        } throws rpcError

        val error = fixture.manager.submitExtrinsic(
            from = WALLET_ID,
            keypair = keypair,
        ) {}.exceptionOrNull()

        assertTrue(error is ExtrinsicSubmissionUnknownException)
        assertEquals(
            fixture.prepared.transactionHash,
            (error as ExtrinsicSubmissionUnknown).transactionHash,
        )
        assertTrue(error.cause === rpcError)
    }

    @Test
    fun `one shot returned hash mismatch is submission unknown`() = runTest {
        val fixture = preparedFixture(activeDeletion = List(5) { false })
        val builder = mockk<ExtrinsicBuilder>()
        val keypair = mockk<Sr25519Keypair>()
        coEvery {
            fixture.factory.createForSigning(
                WALLET_ID,
                keypair,
                fixture.currentRuntime,
            )
        } returns ExtrinsicBuilderFactory.ContextBoundExtrinsicBuilder(
            builder = builder,
            runtimeContext = fixture.currentRuntime,
        )
        every { builder.build(false) } returns fixture.prepared.encoded
        coEvery {
            fixture.runtimeRpcClient.submitExtrinsicOnce(fixture.prepared.encoded)
        } returns "0x${"22".repeat(32)}"

        val error = fixture.manager.submitExtrinsic(
            from = WALLET_ID,
            keypair = keypair,
        ) {}.exceptionOrNull()

        assertTrue(error is ExtrinsicSubmissionUnknownException)
        assertEquals(
            fixture.prepared.transactionHash,
            (error as ExtrinsicSubmissionUnknown).transactionHash,
        )
        assertEquals("EXTRINSIC_HASH_MISMATCH", error.cause?.message)
    }

    @Test
    fun `one shot signing failure is definitive and never opens RPC`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false))
        val keypair = mockk<Sr25519Keypair>()
        val signingError = IllegalStateException("signing failed")
        coEvery {
            fixture.factory.createForSigning(
                WALLET_ID,
                keypair,
                fixture.currentRuntime,
            )
        } throws signingError

        val error = fixture.manager.submitExtrinsic(
            from = WALLET_ID,
            keypair = keypair,
        ) {}.exceptionOrNull()

        assertEquals(signingError.message, error?.deepestCause()?.message)
        assertFalse(error is ExtrinsicSubmissionUnknown)
        coVerify(exactly = 0) { fixture.runtimeRpcClient.submitExtrinsicOnce(any()) }
    }

    @Test
    fun `generic preparation retains the exact context returned with its signing builder`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false, false))
        val keypair = mockk<Sr25519Keypair>()
        val builder = mockk<ExtrinsicBuilder>()
        coEvery {
            fixture.factory.createForSigning(
                WALLET_ID,
                keypair,
                fixture.currentRuntime,
            )
        } returns ExtrinsicBuilderFactory.ContextBoundExtrinsicBuilder(
            builder = builder,
            runtimeContext = fixture.currentRuntime,
        )
        every { builder.build(false) } returns fixture.prepared.encoded

        val prepared = fixture.manager.prepareExtrinsic(
            from = WALLET_ID,
            keypair = keypair,
        ) {}

        assertTrue(prepared.signingRuntime === fixture.currentRuntime)
        coVerify(exactly = 1) {
            fixture.factory.createForSigning(
                WALLET_ID,
                keypair,
                fixture.currentRuntime,
            )
        }
        coVerify(exactly = 1) {
            fixture.runtimeManager.getMutationRuntimeContext()
        }
    }

    @Test
    fun `Polkamarkt preparation passes the qualified context into the exact builder`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false, false))
        val keypair = mockk<Sr25519Keypair>()
        val builder = mockk<ExtrinsicBuilder>()
        val rawContext = fixture.signingRuntime
        val qualified = QualifiedSora2MutationRuntime(
            context = rawContext,
            verified = VerifiedSora2Runtime("reviewed", 130, 130),
        )
        coEvery {
            fixture.factory.createForPolkamarkt(WALLET_ID, keypair, qualified)
        } returns ExtrinsicBuilderFactory.ContextBoundExtrinsicBuilder(
            builder = builder,
            runtimeContext = rawContext,
        )
        every { builder.build(false) } returns fixture.prepared.encoded

        val prepared = fixture.manager.preparePolkamarktExtrinsic(
            from = WALLET_ID,
            keypair = keypair,
            runtime = qualified,
        ) {}

        assertEquals(fixture.prepared.walletId, prepared.walletId)
        assertEquals(fixture.prepared.encoded, prepared.encoded)
        assertEquals(fixture.prepared.transactionHash, prepared.transactionHash)
        assertTrue(prepared.signingRuntime === rawContext)
        coVerify(exactly = 1) {
            fixture.factory.createForPolkamarkt(WALLET_ID, keypair, qualified)
        }
        coVerify(exactly = 0) {
            fixture.factory.createForSigning(any(), any(), any())
        }
    }

    @Test
    fun `ordinary fee keeps behavior while passing one exact runtime context`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false))
        val builder = mockk<ExtrinsicBuilder>()
        val expectedFee = BigInteger.valueOf(7L)
        coEvery {
            fixture.factory.createForFee(WALLET_ID, fixture.currentRuntime)
        } returns ExtrinsicBuilderFactory.ContextBoundExtrinsicBuilder(
            builder = builder,
            runtimeContext = fixture.currentRuntime,
        )
        every { builder.build(false) } returns fixture.prepared.encoded
        coEvery {
            fixture.runtimeRpcClient.queryPartialFee(fixture.prepared.encoded)
        } returns expectedFee

        val fee = fixture.manager.calcFee(from = WALLET_ID) {}

        assertEquals(expectedFee, fee)
        assertEquals(
            expectedFee,
            requireExactSora2TransferFee(expectedFee, expectedFee),
        )
        val drift = runCatching {
            requireExactSora2TransferFee(
                expectedFee,
                expectedFee + BigInteger.ONE,
            )
        }.exceptionOrNull()
        assertEquals("SORA2_TRANSFER_FEE_CHANGED", drift?.message)
        coVerify(exactly = 1) {
            fixture.runtimeManager.getMutationRuntimeContext()
        }
        coVerify(exactly = 1) {
            fixture.factory.createForFee(WALLET_ID, fixture.currentRuntime)
        }

        val nominalFree = BigInteger.TEN
        val partiallyFrozen = calculateSora2TransferableTokenBalance(
            free = nominalFree,
            frozen = BigInteger.ONE,
        )
        assertEquals(BigInteger.valueOf(9L), partiallyFrozen)
        assertFalse(
            "A transfer equal to free must not pass when any token balance is frozen",
            partiallyFrozen >= nominalFree,
        )
        assertEquals(
            BigInteger.ZERO,
            calculateSora2TransferableTokenBalance(
                free = BigInteger.ONE,
                frozen = BigInteger.TEN,
            ),
        )
    }

    @Test
    fun `final Polkamarkt fee uses the same qualified builder context`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false))
        val builder = mockk<ExtrinsicBuilder>()
        val qualified = QualifiedSora2MutationRuntime(
            context = mockk<Sora2MutationRuntimeContext>(),
            verified = VerifiedSora2Runtime("reviewed", 130, 130),
        )
        val expectedFee = BigInteger.valueOf(9L)
        coEvery {
            fixture.factory.createForPolkamarkt(WALLET_ID, qualified)
        } returns ExtrinsicBuilderFactory.ContextBoundExtrinsicBuilder(
            builder = builder,
            runtimeContext = qualified.context,
        )
        every { builder.build(false) } returns fixture.prepared.encoded
        coEvery {
            fixture.runtimeRpcClient.queryPartialFee(fixture.prepared.encoded)
        } returns expectedFee

        val fee = fixture.manager.calcPolkamarktFee(
            from = WALLET_ID,
            runtime = qualified,
        ) {}

        assertEquals(expectedFee, fee)
        coVerify(exactly = 1) {
            fixture.factory.createForPolkamarkt(WALLET_ID, qualified)
        }
        coVerify(exactly = 0) {
            fixture.factory.createForFee(any(), any())
        }
    }

    @Test
    fun `every prepared runtime identity drift is definitive and never opens RPC`() = runTest {
        val signingRuntime = mutationContext(finalizedByte = "11")
        val driftCases = listOf(
            "SORA2_MUTATION_GENESIS_DRIFT" to signingRuntime.copy(
                genesisHash = "0x${"ab".repeat(32)}",
                finalizedHash = "0x${"21".repeat(32)}",
            ),
            "SORA2_MUTATION_SPEC_VERSION_DRIFT" to signingRuntime.copy(
                runtimeVersion = runtimeVersion(specVersion = 131, transactionVersion = 130),
                finalizedHash = "0x${"22".repeat(32)}",
            ),
            "SORA2_MUTATION_TRANSACTION_VERSION_DRIFT" to signingRuntime.copy(
                runtimeVersion = runtimeVersion(specVersion = 130, transactionVersion = 131),
                finalizedHash = "0x${"23".repeat(32)}",
            ),
            "SORA2_MUTATION_METADATA_DRIFT" to signingRuntime.copy(
                metadataSha256 = "c".repeat(64),
                finalizedHash = "0x${"24".repeat(32)}",
            ),
            "SORA2_MUTATION_TYPES_DRIFT" to signingRuntime.copy(
                typesSha256 = "d".repeat(64),
                finalizedHash = "0x${"25".repeat(32)}",
            ),
        )

        driftCases.forEach { (expectedMessage, currentRuntime) ->
            val fixture = preparedFixture(
                activeDeletion = listOf(false, false, false),
                signingRuntime = signingRuntime,
                currentRuntime = currentRuntime,
            )

            val error = captureFailure {
                fixture.manager.submitPreparedAndWait(fixture.prepared)
            }

            assertTrue(error is DefinitelyNotSubmitted)
            assertFalse(error is ExtrinsicSubmissionUnknown)
            assertEquals(expectedMessage, error.deepestCause().message)
            coVerify(exactly = 0) {
                fixture.runtimeRpcClient.submitExtrinsicOnce(any())
            }
        }
    }

    @Test
    fun `fresh bounded runtime is checked only after caller pre transport validation`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false, false, false))
        val order = mutableListOf<String>()
        coEvery {
            fixture.runtimeManager.getMutationRuntimeContext()
        } answers {
            order += "fresh-runtime"
            fixture.currentRuntime
        }
        coEvery {
            fixture.runtimeRpcClient.submitExtrinsicOnce(any())
        } throws IllegalStateException("transport entered")

        val error = captureFailure {
            fixture.manager.submitPreparedAndWait(
                prepared = fixture.prepared,
                preTransportValidation = { order += "caller-validation" },
            )
        }

        assertEquals(listOf("caller-validation", "fresh-runtime"), order)
        assertTrue(error is ExtrinsicSubmissionUnknownException)
    }

    @Test
    fun `finalized head advance passes identity validation and reaches transport`() = runTest {
        val fixture = preparedFixture(
            activeDeletion = listOf(false, false, false),
            signingRuntime = mutationContext(finalizedByte = "31", finalizedBlockNumber = 64),
            currentRuntime = mutationContext(finalizedByte = "32", finalizedBlockNumber = 65),
        )
        val handoffError = IllegalStateException("transport entered")
        coEvery { fixture.runtimeRpcClient.submitExtrinsicOnce(any()) } throws handoffError

        val error = captureFailure {
            fixture.manager.submitPreparedAndWait(fixture.prepared)
        }

        assertTrue(error is ExtrinsicSubmissionUnknownException)
        assertTrue(error.cause === handoffError)
        coVerify(exactly = 1) {
            fixture.runtimeRpcClient.submitExtrinsicOnce(any())
        }
    }

    @Test
    fun `expired mortal era is definitive and never opens RPC`() = runTest {
        val fixture = preparedFixture(
            activeDeletion = listOf(false, false, false),
            signingRuntime = mutationContext(finalizedByte = "31", finalizedBlockNumber = 64),
            currentRuntime = mutationContext(finalizedByte = "32", finalizedBlockNumber = 128),
        )

        val error = captureFailure {
            fixture.manager.submitPreparedAndWait(fixture.prepared)
        }

        assertTrue(error is DefinitelyNotSubmitted)
        assertFalse(error is ExtrinsicSubmissionUnknown)
        assertEquals("SORA2_MUTATION_MORTAL_ERA_EXPIRED", error.deepestCause().message)
        coVerify(exactly = 0) {
            fixture.runtimeRpcClient.submitExtrinsicOnce(any())
        }
    }

    @Test
    fun `one shot runtime drift is definitive and does not enter SubstrateCalls`() = runTest {
        val signingRuntime = mutationContext(finalizedByte = "41")
        val driftedRuntime = signingRuntime.copy(
            metadataSha256 = "e".repeat(64),
            finalizedHash = "0x${"42".repeat(32)}",
        )
        val fixture = preparedFixture(
            activeDeletion = List(5) { false },
            signingRuntime = signingRuntime,
            currentRuntime = signingRuntime,
        )
        val builder = mockk<ExtrinsicBuilder>()
        val keypair = mockk<Sr25519Keypair>()
        coEvery {
            fixture.runtimeManager.getMutationRuntimeContext()
        } returnsMany listOf(signingRuntime, driftedRuntime)
        coEvery {
            fixture.factory.createForSigning(WALLET_ID, keypair, signingRuntime)
        } returns ExtrinsicBuilderFactory.ContextBoundExtrinsicBuilder(
            builder = builder,
            runtimeContext = signingRuntime,
        )
        every { builder.build(false) } returns fixture.prepared.encoded

        val error = fixture.manager.submitExtrinsic(
            from = WALLET_ID,
            keypair = keypair,
        ) {}.exceptionOrNull()

        assertTrue(error is DefinitelyNotSubmitted)
        assertFalse(error is ExtrinsicSubmissionUnknown)
        assertEquals("SORA2_MUTATION_METADATA_DRIFT", error?.deepestCause()?.message)
        coVerify(exactly = 0) { fixture.runtimeRpcClient.submitExtrinsicOnce(any()) }
    }

    @Test
    fun `pre transport validation failure is definitive and never opens RPC`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false))
        var callbackExecuted = false

        val error = try {
            fixture.manager.submitPreparedAndWait(
                prepared = fixture.prepared,
                preTransportValidation = {
                    callbackExecuted = true
                    error("POLKAMARKT_MUTATIONS_DISABLED")
                },
            )
            fail("Expected pre-transport validation to fail")
            null
        } catch (error: Throwable) {
            error
        }

        assertTrue(callbackExecuted)
        assertTrue(error is DefinitelyNotSubmitted)
        assertEquals("POLKAMARKT_MUTATIONS_DISABLED", error?.deepestCause()?.message)
        coVerify(exactly = 0) {
            fixture.runtimeManager.getMutationRuntimeContext()
        }
        coVerify(exactly = 0) {
            fixture.runtimeRpcClient.submitExtrinsicOnce(any())
        }
    }

    @Test
    fun `wallet invalidated after callback is definitive and never opens RPC`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false, false, true))
        var callbackExecuted = false

        val error = try {
            fixture.manager.submitPreparedAndWait(
                prepared = fixture.prepared,
                preTransportValidation = {
                    callbackExecuted = true
                },
            )
            fail("Expected final wallet validation to fail")
            null
        } catch (error: Throwable) {
            error
        }

        assertTrue(callbackExecuted)
        assertTrue(error is DefinitelyNotSubmitted)
        assertEquals("WALLET_DELETION_ACTIVE", error?.deepestCause()?.message)
        coVerify(exactly = 0) {
            fixture.runtimeManager.getMutationRuntimeContext()
        }
        coVerify(exactly = 0) {
            fixture.runtimeRpcClient.submitExtrinsicOnce(any())
        }
    }

    @Test
    fun `pre transport cancellation is definitive and never opens RPC`() = runTest {
        val fixture = preparedFixture(activeDeletion = listOf(false, false))
        val cancellation = CancellationException("screen disappeared")

        val error = try {
            fixture.manager.submitPreparedAndWait(
                prepared = fixture.prepared,
                preTransportValidation = {
                    throw cancellation
                },
            )
            fail("Expected pre-transport cancellation")
            null
        } catch (error: Throwable) {
            error
        }

        assertTrue(error is PreparedExtrinsicPreTransportCancellation)
        assertTrue(error is DefinitelyNotSubmitted)
        assertTrue(error?.cause === cancellation)
        coVerify(exactly = 0) {
            fixture.runtimeRpcClient.submitExtrinsicOnce(any())
        }
    }

    private fun preparedFixture(
        activeDeletion: List<Boolean>,
        signingRuntime: Sora2MutationRuntimeContext = mutationContext(finalizedByte = "11"),
        currentRuntime: Sora2MutationRuntimeContext = mutationContext(finalizedByte = "22"),
    ): PreparedFixture {
        val calls = mockk<SubstrateCalls>()
        val factory = mockk<ExtrinsicBuilderFactory>()
        val runtimeManager = mockk<RuntimeManager>()
        val database = mockk<AppDatabase>()
        val accountDao = mockk<AccountDao>()
        val walletDao = mockk<WalletIdentityDao>()
        val pendingCoordinator = mockk<Sora2PendingSubmissionCoordinator>()
        val runtimeRpcClient = mockk<Sora2BoundedRuntimeRpcClient>()
        val staged = mockk<Sora2PendingSubmissionLocal>()
        val armed = mockk<Sora2PendingSubmissionLocal>()
        val submitted = mockk<Sora2PendingSubmissionLocal>()
        val encoded = "0x00"
        val transactionHash = encoded.extrinsicHash()

        coEvery { pendingCoordinator.stageBeforeTransport(any()) } returns staged
        coEvery { pendingCoordinator.armForTransport(staged) } returns armed
        coEvery { pendingCoordinator.markSubmitted(armed) } returns submitted
        coEvery { pendingCoordinator.removeDefinitelyBeforeTransport(staged) } returns Unit
        every { pendingCoordinator.kickAfterUnknownBestEffort() } returns Unit
        every { submitted.transactionHash } returns transactionHash
        coEvery { runtimeRpcClient.submitExtrinsicOnce(encoded) } returns transactionHash
        coEvery { pendingCoordinator.awaitTerminalStatus(submitted) } returns
            ExtrinsicSubmitStatus(
                success = true,
                txHash = transactionHash,
                blockHash = "0x${"33".repeat(32)}",
            )

        every { database.accountDao() } returns accountDao
        every { database.walletIdentityDao() } returns walletDao
        coEvery {
            walletDao.hasActiveDeletionOperation()
        } returnsMany activeDeletion
        coEvery { walletDao.getMigrationJournal(any()) } returns null
        coEvery { accountDao.getAccount(WALLET_ID) } returns
            SoraAccountLocal(WALLET_ID, "Primary")
        coEvery { walletDao.getWallet(WALLET_ID) } returns
            WalletIdentityLocal(
                walletId = WALLET_ID,
                displayName = "Primary",
                secretSource = "MNEMONIC",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        coEvery { walletDao.getNetworkAccount(WALLET_ID, "sora2") } returns
            NetworkAccountLocal(
                walletId = WALLET_ID,
                networkId = "sora2",
                publicKey = "01",
                address = WALLET_ID,
                derivationPath = "",
                derivationVersion = 0,
                enabled = true,
            )
        coEvery {
            runtimeManager.getMutationRuntimeContext()
        } returns currentRuntime

        return PreparedFixture(
            manager = ExtrinsicManager(
                factory = factory,
                runtimeManager = runtimeManager,
                database = database,
                pendingSubmissionCoordinator = pendingCoordinator,
                runtimeRpcClient = runtimeRpcClient,
            ),
            calls = calls,
            factory = factory,
            runtimeManager = runtimeManager,
            runtimeRpcClient = runtimeRpcClient,
            prepared = ExtrinsicManager.PreparedExtrinsic(
                walletId = WALLET_ID,
                encoded = encoded,
                transactionHash = encoded.extrinsicHash(),
                signingRuntime = signingRuntime,
            ),
            signingRuntime = signingRuntime,
            currentRuntime = currentRuntime,
        )
    }

    private data class PreparedFixture(
        val manager: ExtrinsicManager,
        val calls: SubstrateCalls,
        val factory: ExtrinsicBuilderFactory,
        val runtimeManager: RuntimeManager,
        val runtimeRpcClient: Sora2BoundedRuntimeRpcClient,
        val prepared: ExtrinsicManager.PreparedExtrinsic,
        val signingRuntime: Sora2MutationRuntimeContext,
        val currentRuntime: Sora2MutationRuntimeContext,
    )

    private fun mutationContext(
        finalizedByte: String,
        finalizedBlockNumber: Long = 64,
    ): Sora2MutationRuntimeContext = Sora2MutationRuntimeContext(
        snapshot = mockk<RuntimeSnapshot>(),
        runtimeVersion = runtimeVersion(specVersion = 130, transactionVersion = 130),
        genesisHash = "0x${"aa".repeat(32)}",
        finalizedHash = "0x${finalizedByte.repeat(32)}",
        metadataSha256 = "a".repeat(64),
        typesSha256 = "b".repeat(64),
        finalizedBlockNumber = finalizedBlockNumber,
    )

    private fun runtimeVersion(
        specVersion: Int,
        transactionVersion: Int,
    ): RuntimeVersion = mockk<RuntimeVersion>().also { version ->
        every { version.specVersion } returns specVersion
        every { version.transactionVersion } returns transactionVersion
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable = try {
        block()
        fail("Expected failure")
        error("unreachable")
    } catch (error: Throwable) {
        error
    }

    private fun Throwable.deepestCause(): Throwable {
        var current = this
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private companion object {
        const val WALLET_ID = "cnRetainedWallet"
        val HASH_1 = "0x${"a1".repeat(32)}"
        val HASH_2 = "0x${"b2".repeat(32)}"
    }
}
