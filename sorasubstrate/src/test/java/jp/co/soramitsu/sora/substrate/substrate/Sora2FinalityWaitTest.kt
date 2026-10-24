package jp.co.soramitsu.sora.substrate.substrate

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
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
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.Type
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.xsubstrate.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.xsubstrate.runtime.metadata.StorageEntryModifier
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Error
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Event
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Module
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.StorageEntryType
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The wait for finality after a broadcast (audit finding A5). One failed or slow status request
 * used to end the wait with an exception, which the screens showed as "Transaction rejected".
 * The wait now retries failed reads with a pause, never reports a read failure as a result, and
 * always ends within its time limit. Virtual time makes the pauses and the limit exact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Sora2FinalityWaitTest {

    private val codec = Sora2AddressCodec()
    private val publicKey = ByteArray(32) { 9 }
    private val walletId = checkNotNull(codec.toSoraAddressOrNull(publicKey))
    private val publicKeyHex = publicKey.toHexString(withPrefix = false).lowercase()

    private val submitted = Sora2PendingSubmissionLocal(
        localId = "sora2:${"cd".repeat(32)}",
        recoverySchemaVersion = Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION,
        walletId = walletId,
        networkId = Sora2PendingSubmissionLocal.NETWORK_SORA2,
        transactionHash = TX_HASH,
        accountId = publicKeyHex,
        publicKey = publicKeyHex,
        genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
        specVersion = 133,
        transactionVersion = 131,
        metadataSha256 = "1".repeat(64),
        typesSha256 = "2".repeat(64),
        eraBirthBlock = BIRTH,
        eraDeathBlockExclusive = BIRTH + 64,
        eraPeriod = 64,
        eraPhase = (BIRTH % 64).toInt(),
        eraBirthBlockHash = BIRTH_HASH,
        operationKind = Sora2PendingSubmissionLocal.OPERATION_GENERIC,
        state = Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY,
        submissionIsAmbiguous = false,
        terminalBlockNumber = null,
        terminalBlockHash = null,
        terminalFinalizedHeight = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private var current = submitted
    private val writtenStates = mutableListOf<String>()

    private val dao = mockk<WalletIdentityDao>().also { dao ->
        coEvery { dao.getUnresolvedSora2PendingSubmissions() } answers {
            listOf(current).filterNot { it.isAuthoritativelyTerminal }
        }
        coEvery { dao.hasActiveDeletionOperation() } returns false
        coEvery { dao.getMigrationJournal(any()) } returns null
        coEvery { dao.getWallet(walletId) } returns WalletIdentityLocal(
            walletId = walletId,
            displayName = "Primary",
            secretSource = "MNEMONIC",
            migrationState = "VERIFIED",
            derivationVersion = 1,
        )
        coEvery { dao.getNetworkAccount(walletId, "sora2") } returns NetworkAccountLocal(
            walletId = walletId,
            networkId = "sora2",
            publicKey = publicKeyHex,
            address = walletId,
            derivationPath = "",
            derivationVersion = 0,
            enabled = true,
        )
        coEvery { dao.getSora2PendingSubmission(submitted.localId) } answers { current }
        coEvery {
            dao.updateSora2PendingSubmission(any(), any(), any(), any(), any(), any(), any())
        } answers {
            current = current.copy(
                state = secondArg(),
                submissionIsAmbiguous = thirdArg(),
                terminalBlockNumber = arg(3),
                terminalBlockHash = arg(4),
                terminalFinalizedHeight = arg(5),
                updatedAt = arg(6),
            )
            writtenStates += current.state
            current
        }
    }
    private val accountDao = mockk<AccountDao>().also {
        coEvery { it.getAccount(walletId) } returns SoraAccountLocal(walletId, "Primary")
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

    private fun identityAt(finalizedHeight: Long) = Sora2FinalizedRuntimeIdentity(
        genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
        finalizedHash = blockHash(finalizedHeight),
        runtimeVersion = RuntimeVersion(133, 131),
        finalizedBlockNumber = finalizedHeight,
    )

    private fun blocks(includedAt: Long?) {
        coEvery { rpc.getCanonicalBlock(any(), any(), any()) } answers {
            val height = firstArg<Long>()
            Sora2CanonicalBlock(
                blockHash = if (height == BIRTH) BIRTH_HASH else blockHash(height),
                blockNumber = height,
                matchingExtrinsicIndices = if (height == includedAt) {
                    mapOf(TX_HASH to EXTRINSIC_INDEX)
                } else {
                    emptyMap()
                },
            )
        }
    }

    /** The signing runtime, reduced to what the status check reads: System.Events. */
    private fun runtimeWithEvents(records: List<Struct.Instance>) {
        val system = Module(
            name = "System",
            storage = Storage(
                prefix = "System",
                entries = mapOf(
                    "Events" to StorageEntry(
                        name = "Events",
                        modifier = StorageEntryModifier.values().first(),
                        type = StorageEntryType.Plain(FixedEvents(records)),
                        default = ByteArray(0),
                        documentation = emptyList(),
                        moduleName = "System",
                    ),
                ),
            ),
            calls = null,
            events = mapOf(
                "ExtrinsicSuccess" to Event("ExtrinsicSuccess", 0 to 0, emptyList(), emptyList()),
                "ExtrinsicFailed" to Event("ExtrinsicFailed", 0 to 1, emptyList(), emptyList()),
            ),
            constants = emptyMap(),
            errors = emptyMap(),
            index = BigInteger.ZERO,
        )
        val liquidityProxy = Module(
            name = "LiquidityProxy",
            storage = null,
            calls = null,
            events = null,
            constants = emptyMap(),
            errors = linkedMapOf(
                "UnavailableExchangePath" to Error("UnavailableExchangePath", emptyList()),
                "MaxFeeExceeded" to Error("MaxFeeExceeded", emptyList()),
                "SlippageNotTolerated" to Error("SlippageNotTolerated", emptyList()),
            ),
            index = BigInteger.valueOf(26),
        )
        val snapshot = RuntimeSnapshot(
            typeRegistry = mockk(),
            metadata = RuntimeMetadata(
                runtimeVersion = BigInteger.valueOf(14),
                modules = mapOf(system.name to system, liquidityProxy.name to liquidityProxy),
                extrinsic = ExtrinsicMetadata(BigInteger.valueOf(4), emptyList()),
            ),
        )
        coEvery {
            runtimeManager.getReviewedRecoveryRuntimeSnapshotOrNull(
                any(), any(), any(), any(), any(), any(),
            )
        } returns snapshot
        coEvery { rpc.getStorageAt(any(), any()) } returns "0x00"
    }

    private fun successRecord() = eventRecord(DictEnum.Entry("ExtrinsicSuccess", null))

    private fun failedRecord(dispatchError: DictEnum.Entry<*>) = eventRecord(
        DictEnum.Entry(
            "ExtrinsicFailed",
            Struct.Instance(mapOf("dispatchError" to dispatchError, "dispatchInfo" to null)),
        )
    )

    /**
     * The form mainnet runtime 133 decodes to with the app's type registry: a generic event whose
     * first argument is the DispatchError (checked offline against live metadata).
     */
    private fun genericFailedRecord(dispatchError: DictEnum.Entry<*>) = Struct.Instance(
        mapOf(
            "phase" to DictEnum.Entry(
                "ApplyExtrinsic",
                BigInteger.valueOf(EXTRINSIC_INDEX.toLong()),
            ),
            "event" to GenericEvent.Instance(
                module = Module(
                    name = "System",
                    storage = null,
                    calls = null,
                    events = null,
                    constants = emptyMap(),
                    errors = emptyMap(),
                    index = BigInteger.ZERO,
                ),
                event = Event("ExtrinsicFailed", 0 to 1, emptyList(), emptyList()),
                arguments = listOf(dispatchError, null),
            ),
            "topics" to emptyList<Any>(),
        )
    )

    private fun eventRecord(systemEvent: DictEnum.Entry<*>) = Struct.Instance(
        mapOf(
            "phase" to DictEnum.Entry(
                "ApplyExtrinsic",
                BigInteger.valueOf(EXTRINSIC_INDEX.toLong()),
            ),
            "event" to DictEnum.Entry("System", systemEvent),
            "topics" to emptyList<Any>(),
        )
    )

    @Test
    fun `failed status reads are retried with growing pauses and the result is still returned`() =
        runTest {
            var reads = 0
            coEvery { rpc.getFinalizedIdentity() } answers {
                reads += 1
                if (reads <= 3) throw IOException("BOUNDED_HTTP_IO")
                identityAt(BIRTH + 3)
            }
            blocks(includedAt = BIRTH + 2)
            runtimeWithEvents(listOf(successRecord()))

            val status = coordinator().awaitTerminalStatus(submitted)

            assertTrue(status.success)
            assertEquals(TX_HASH, status.txHash)
            assertEquals(blockHash(BIRTH + 2), status.blockHash)
            assertEquals(4, reads)
            // Pauses of 1 s, 2 s and 4 s after the three failed reads; no error surfaced.
            assertEquals(7_000L, currentTime)
            assertEquals(
                listOf(Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS),
                writtenStates,
            )
        }

    @Test
    fun `status reads that keep failing end as still pending and never as a result`() = runTest {
        var reads = 0
        coEvery { rpc.getFinalizedIdentity() } answers {
            reads += 1
            throw IOException("BOUNDED_HTTP_IO")
        }

        val error = captureFailure { coordinator().awaitTerminalStatus(submitted) }

        assertTrue(error is Sora2FinalityPendingException)
        // Bounded: six reads, with pauses of 1, 2, 4, 8 and 8 seconds between them.
        assertEquals(6, reads)
        assertEquals(23_000L, currentTime)
        // The journal row is exactly as it was; the background worker carries on from it.
        assertEquals(emptyList<String>(), writtenStates)
        assertEquals(submitted, current)
    }

    @Test
    fun `one slow request cannot hold the wait beyond its time limit`() = runTest {
        coEvery { rpc.getFinalizedIdentity() } coAnswers {
            delay(600_000L)
            identityAt(BIRTH + 3)
        }

        val error = captureFailure { coordinator().awaitTerminalStatus(submitted) }

        assertTrue(error is Sora2FinalityPendingException)
        assertEquals(120_000L, currentTime)
        assertEquals(emptyList<String>(), writtenStates)
    }

    @Test
    fun `a shorter limit is enforced while a request is in flight`() = runTest {
        coEvery { rpc.getFinalizedIdentity() } coAnswers {
            delay(45_000L)
            throw IOException("BOUNDED_HTTP_DEADLINE")
        }

        val error = captureFailure {
            coordinator().awaitTerminalStatus(submitted, maximumWaitMillis = 10_000L)
        }

        assertTrue(error is Sora2FinalityPendingException)
        assertEquals(10_000L, currentTime)
    }

    @Test
    fun `a transaction that is not final yet is polled until the limit and stays pending`() =
        runTest {
            var reads = 0
            coEvery { rpc.getFinalizedIdentity() } answers {
                reads += 1
                identityAt(BIRTH + 5)
            }
            blocks(includedAt = null)

            val error = captureFailure { coordinator().awaitTerminalStatus(submitted) }

            assertTrue(error is Sora2FinalityPendingException)
            assertEquals(120_000L, currentTime)
            // One read every three seconds; successful reads never count towards the retry bound.
            assertEquals(40, reads)
            assertEquals(emptyList<String>(), writtenStates)
        }

    @Test
    fun `a read failure in the middle of polling does not end the wait`() = runTest {
        var reads = 0
        coEvery { rpc.getFinalizedIdentity() } answers {
            reads += 1
            when (reads) {
                1 -> identityAt(BIRTH + 1)
                2 -> throw IOException("handover from Wi-Fi to mobile data")
                else -> identityAt(BIRTH + 4)
            }
        }
        blocks(includedAt = BIRTH + 3)
        runtimeWithEvents(listOf(successRecord()))

        val status = coordinator().awaitTerminalStatus(submitted)

        assertTrue(status.success)
        assertEquals(3, reads)
        // 3 s poll interval after the first read, 1 s pause after the failed one.
        assertEquals(4_000L, currentTime)
    }

    @Test
    fun `a transaction that failed on chain is returned with the runtime error`() = runTest {
        coEvery { rpc.getFinalizedIdentity() } returns identityAt(BIRTH + 3)
        blocks(includedAt = BIRTH + 1)
        runtimeWithEvents(
            listOf(
                genericFailedRecord(
                    DictEnum.Entry(
                        "Module",
                        Struct.Instance(
                            mapOf(
                                "index" to BigInteger.valueOf(26),
                                "error" to byteArrayOf(2, 0, 0, 0),
                            )
                        ),
                    )
                )
            )
        )

        val status = coordinator().awaitTerminalStatus(submitted)

        assertFalse(status.success)
        assertEquals(blockHash(BIRTH + 1), status.blockHash)
        assertEquals("LiquidityProxy.SlippageNotTolerated", status.failureDetail)
        assertEquals(
            "LiquidityProxy.SlippageNotTolerated",
            details.get(walletId, TX_HASH)?.chainFailure,
        )
        assertEquals(
            listOf(Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE),
            writtenStates,
        )
    }

    @Test
    fun `a failure that is not a pallet error is named as well`() = runTest {
        coEvery { rpc.getFinalizedIdentity() } returns identityAt(BIRTH + 3)
        blocks(includedAt = BIRTH + 1)
        runtimeWithEvents(
            listOf(
                failedRecord(DictEnum.Entry("Token", DictEnum.Entry("FundsUnavailable", null)))
            )
        )

        val status = coordinator().awaitTerminalStatus(submitted)

        assertFalse(status.success)
        assertEquals("Token.FundsUnavailable", status.failureDetail)
    }

    @Test
    fun `an expired transaction is returned as not included`() = runTest {
        coEvery { rpc.getFinalizedIdentity() } returns identityAt(BIRTH + 100)
        blocks(includedAt = null)

        val status = coordinator().awaitTerminalStatus(submitted)

        assertFalse(status.success)
        assertNull(status.blockHash)
        assertNull(status.failureDetail)
    }

    @Test
    fun `the same action that is still waiting is found for the duplicate warning`() = runTest {
        val swap = Sora2SubmissionActivity(
            kind = Sora2SubmissionActivity.Kind.SWAP,
            tokenIds = listOf("0x02", "0x04"),
            amounts = listOf("1", "2"),
        )
        details.recordActivity(walletId, TX_HASH, swap)
        val coordinator = coordinator()

        // Same kind and tokens, another amount: still the same action.
        assertEquals(
            TX_HASH,
            coordinator.findWaitingSameAction(walletId, swap.copy(amounts = listOf("3", "6")))
                ?.transactionHash,
        )
        assertNull(
            coordinator.findWaitingSameAction(
                walletId,
                swap.copy(tokenIds = listOf("0x02", "0x05")),
            )
        )
        assertNull(
            coordinator.findWaitingSameAction(
                walletId,
                swap.copy(kind = Sora2SubmissionActivity.Kind.LIQUIDITY_ADD),
            )
        )
        assertNull(coordinator.findWaitingSameAction("cnAnotherWallet", swap))

        // A transaction the node refused is not waiting.
        details.recordNodeRefusal(
            walletId,
            TX_HASH,
            Sora2NodeRefusal(TransactionNodeRefusal.CANNOT_PAY_FEE, 1010, null),
        )
        assertNull(coordinator.findWaitingSameAction(walletId, swap))
    }

    @Test
    fun `a transaction with a final status is no longer waiting`() = runTest {
        val swap = Sora2SubmissionActivity(
            kind = Sora2SubmissionActivity.Kind.SWAP,
            tokenIds = listOf("0x02", "0x04"),
        )
        details.recordActivity(walletId, TX_HASH, swap)
        coEvery { rpc.getFinalizedIdentity() } returns identityAt(BIRTH + 100)
        blocks(includedAt = null)
        val coordinator = coordinator()

        coordinator.recoverStatusOnly()

        assertNull(coordinator.findWaitingSameAction(walletId, swap))
        coVerify(exactly = 1) { rpc.getFinalizedIdentity() }
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable = try {
        block()
        fail("Expected the wait to end without a final status")
        error("unreachable")
    } catch (error: Throwable) {
        error
    }

    private class FixedEvents(
        private val records: List<Struct.Instance>,
    ) : Type<List<Struct.Instance>>("FixedEvents") {
        override val isFullyResolved = true

        override fun decode(
            scaleCodecReader: ScaleCodecReader,
            runtime: RuntimeSnapshot,
        ): List<Struct.Instance> = records

        override fun encode(
            scaleCodecWriter: ScaleCodecWriter,
            runtime: RuntimeSnapshot,
            value: List<Struct.Instance>,
        ) = error("not used")

        override fun isValidInstance(instance: Any?) = instance is List<*>
    }

    private companion object {
        const val BIRTH = 1_000L
        const val EXTRINSIC_INDEX = 2
        const val TX_HASH = "0xcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd"
        val BIRTH_HASH = blockHash(BIRTH)

        fun blockHash(height: Long): String =
            "0x" + height.toString(16).padStart(64, '0')
    }
}
