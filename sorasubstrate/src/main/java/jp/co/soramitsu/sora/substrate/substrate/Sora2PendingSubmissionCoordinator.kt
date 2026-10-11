package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.Events
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2CanonicalBlock
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.xsubstrate.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.xsubstrate.runtime.metadata.event
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Narrow dependency implemented by the app's WorkManager scheduler. Journal durability never
 * depends on a successful kick: startup schedules the same unique status-only worker again.
 */
fun interface Sora2PendingRecoveryKick {
    fun kickAfterJournal()
}

data class Sora2PendingRecoveryPass(
    val examined: Int,
    val resolved: Int,
    val unresolved: Int,
    val failures: Int,
    val remainingUnattempted: Int,
)

internal data class Sora2PendingRecoveryBatch<T>(
    val rows: List<T>,
    val nextCursor: Int,
)

/** Pure bounded rotation used so an unavailable old fixture cannot starve later witnesses. */
internal object Sora2PendingRecoveryBatchPlanner {
    fun cursorForOffset(offset: Long, rowCount: Int): Int {
        require(offset >= 0L && rowCount >= 0) { "SORA2_RECOVERY_CURSOR_INVALID" }
        if (rowCount == 0) return 0
        return (offset % rowCount.toLong()).toInt()
    }

    fun <T> select(
        ordered: List<T>,
        cursor: Int,
        maximum: Int,
        priority: T? = null,
    ): Sora2PendingRecoveryBatch<T> {
        require(cursor >= 0 && maximum > 0) { "SORA2_RECOVERY_CURSOR_INVALID" }
        if (ordered.isEmpty()) return Sora2PendingRecoveryBatch(emptyList(), 0)
        val start = cursor % ordered.size
        val rotated = List(ordered.size) { offset ->
            ordered[(start + offset) % ordered.size]
        }
        val (rows, consumedRotationSlots) = if (priority == null) {
            val selected = rotated.take(maximum)
            selected to selected.size
        } else {
            check(ordered.count { it == priority } == 1) {
                "SORA2_RECOVERY_PRIORITY_INVALID"
            }
            val selectedFromRotation = rotated.withIndex()
                .filterNot { it.value == priority }
                .take(maximum - 1)
            val selected = listOf(priority) + selectedFromRotation.map { it.value }
            // The priority row does not consume the background rotation. Advance past the exact
            // rotated positions inspected for non-priority work, including a priority position
            // encountered between them. Advancing by selected.size would permanently skip rows
            // for some list-size/batch-size combinations.
            selected to (selectedFromRotation.lastOrNull()?.index?.plus(1) ?: 0)
        }
        return Sora2PendingRecoveryBatch(
            rows = rows,
            nextCursor = (start + consumedRotationSlots) % ordered.size,
        )
    }
}

/** Blocks a second legacy claim while the first exact generic witness is still unresolved. */
internal object Sora2LegacyMigrationAdmissionGate {
    fun requireNoUnresolvedWitness(
        walletId: String,
        unresolved: List<Sora2PendingSubmissionLocal>,
    ) {
        check(
            unresolved.none { row ->
                row.walletId == walletId &&
                    row.operationKind == Sora2PendingSubmissionLocal.OPERATION_GENERIC
            }
        ) { "SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED" }
    }
}

/**
 * Durable ambiguity recovery for ordinary SORA2 mutations.
 *
 * This type intentionally has no signer, signed-byte store, or submission dependency. Recovery
 * can only read canonical finalized blocks and System events through the bounded manifest-bound
 * HTTPS client, then advance a versioned non-secret journal row to an authoritative terminal
 * state. An absent hash remains unresolved until every block in its exact mortal era has been
 * scanned and finality is exclusively beyond the death block.
 */
@Singleton
class Sora2PendingSubmissionCoordinator @Inject constructor(
    private val database: AppDatabase,
    private val runtimeManager: RuntimeManager,
    private val runtimeRpcClient: Sora2BoundedRuntimeRpcClient,
    private val addressCodec: Sora2AddressCodec,
    private val recoveryKick: Sora2PendingRecoveryKick,
    private val detailsStore: Sora2SubmissionDetailsStore,
) {
    private data class RecoveryCacheKey(
        val localId: String,
        val createdAt: Long,
        val eraBirthBlockHash: String,
    )

    private data class RecoveryFixtureKey(
        val genesisHash: String,
        val specVersion: Int,
        val transactionVersion: Int,
        val metadataSha256: String,
        val typesSha256: String,
    )

    private val recoveryMutex = Mutex()
    private val scannedThroughBlockExclusive = mutableMapOf<RecoveryCacheKey, Long>()
    private var recoveryBatchCursor = 0

    suspend fun stageBeforeTransport(
        prepared: ExtrinsicManager.PreparedExtrinsic,
    ): Sora2PendingSubmissionLocal = WalletMutationCoordinator.withLock {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        val dao = database.walletIdentityDao()
        val hash = prepared.transactionHash.canonicalExtrinsicHash()
        check(prepared.encoded.extrinsicHash().canonicalExtrinsicHash() == hash) {
            "SORA2_PENDING_PREPARED_HASH_MISMATCH"
        }
        val runtime = prepared.signingRuntime
        val birth = runtime.finalizedBlockNumber
        val period = SORA2_MORTAL_ERA_PERIOD
        val death = try {
            Math.addExact(birth, period.toLong())
        } catch (error: ArithmeticException) {
            throw IllegalStateException("SORA2_PENDING_ERA_OVERFLOW", error)
        }
        check(birth >= 0L) { "SORA2_PENDING_ERA_INVALID" }
        val publicKey = requireWalletWitness(
            dao = dao,
            walletId = prepared.walletId,
            expectedPublicKey = null,
        )
        val now = positiveNow()
        val row = Sora2PendingSubmissionLocal(
            localId = localId(hash),
            recoverySchemaVersion = Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION,
            walletId = prepared.walletId,
            networkId = Sora2PendingSubmissionLocal.NETWORK_SORA2,
            transactionHash = hash,
            accountId = publicKey,
            publicKey = publicKey,
            genesisHash = runtime.genesisHash.canonicalExtrinsicHash(),
            specVersion = runtime.runtimeVersion.specVersion,
            transactionVersion = runtime.runtimeVersion.transactionVersion,
            metadataSha256 = runtime.metadataSha256,
            typesSha256 = runtime.typesSha256,
            eraBirthBlock = birth,
            eraDeathBlockExclusive = death,
            eraPeriod = period,
            eraPhase = (birth % period).toInt(),
            eraBirthBlockHash = runtime.finalizedHash.canonicalExtrinsicHash(),
            operationKind = when (prepared.purpose) {
                ExtrinsicManager.PreparedPurpose.GENERIC,
                ExtrinsicManager.PreparedPurpose.LEGACY_MIGRATION ->
                    Sora2PendingSubmissionLocal.OPERATION_GENERIC
                ExtrinsicManager.PreparedPurpose.POLKAMARKT ->
                    Sora2PendingSubmissionLocal.OPERATION_POLKAMARKT
            },
            state = Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT,
            submissionIsAmbiguous = false,
            terminalBlockNumber = null,
            terminalBlockHash = null,
            terminalFinalizedHeight = null,
            createdAt = now,
            updatedAt = now,
        )
        WalletIdentityDao.validateSora2PendingSubmission(row)
        val existing = dao.getSora2PendingSubmission(row.localId)
        if (existing != null) {
            WalletIdentityDao.validateSora2PendingSubmission(existing)
            throw Sora2PendingSubmissionAlreadyJournaled(hash)
        }
        if (prepared.purpose == ExtrinsicManager.PreparedPurpose.LEGACY_MIGRATION) {
            Sora2LegacyMigrationAdmissionGate.requireNoUnresolvedWitness(
                walletId = prepared.walletId,
                unresolved = dao.getUnresolvedSora2PendingSubmissions(),
            )
        }
        dao.insertSora2PendingSubmission(row)
        kickBestEffort()
        row
    }

    /** Persist ambiguity before the exact line that enters the one permitted transport attempt. */
    suspend fun armForTransport(
        staged: Sora2PendingSubmissionLocal,
    ): Sora2PendingSubmissionLocal = WalletMutationCoordinator.withLock {
        val updated = database.walletIdentityDao().updateSora2PendingSubmission(
            expected = staged,
            state = Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN,
            ambiguous = true,
            updatedAt = positiveNow(),
        )
        kickBestEffort()
        updated
    }

    /** A matching RPC result proves admission, but not execution or finality. */
    suspend fun markSubmitted(
        armed: Sora2PendingSubmissionLocal,
    ): Sora2PendingSubmissionLocal = WalletMutationCoordinator.withLock {
        val current = database.walletIdentityDao().getSora2PendingSubmission(armed.localId)
            ?: throw IllegalStateException("SORA2_PENDING_SUBMISSION_MISSING")
        WalletIdentityDao.validateSora2PendingSubmission(current)
        val updated = when (current.state) {
            Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN ->
                database.walletIdentityDao().updateSora2PendingSubmission(
                    expected = current,
                    state = Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY,
                    ambiguous = false,
                    updatedAt = positiveNow(),
                )
            Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY -> current
            in Sora2PendingSubmissionLocal.TERMINAL_STATES -> current
            else -> throw IllegalStateException("SORA2_PENDING_SUBMISSION_STATE_CHANGED")
        }
        kickBestEffort()
        updated
    }

    /**
     * Removes a row only while the same coroutine still knows transport was never armed. A row
     * found after process restart is never deleted through this path.
     */
    suspend fun removeDefinitelyBeforeTransport(staged: Sora2PendingSubmissionLocal) {
        WalletMutationCoordinator.withLock {
            database.walletIdentityDao().deleteSora2DefinitelyNotSubmitted(staged)
        }
    }

    /** Keeps the durable UNKNOWN row intact after every transport-side error or cancellation. */
    fun kickAfterUnknownBestEffort() {
        kickBestEffort()
    }

    /**
     * [startOffset] lets a durable scheduler resume bounded rotation after process death. Foreground
     * callers omit it and retain the coordinator's in-memory cursor.
     */
    suspend fun recoverStatusOnly(
        maximumTransactions: Int = MAXIMUM_TRANSACTIONS_PER_PASS,
        startOffset: Long? = null,
    ): Sora2PendingRecoveryPass = recoveryMutex.withLock {
        recoverStatusOnlyLocked(
            maximumTransactions = maximumTransactions,
            priorityLocalId = null,
            startOffset = startOffset,
        )
    }

    /**
     * Waits only for status; it never retries submission and returns only terminal chain proof.
     *
     * A status read that fails or is slow is not a result and never an error for the user: it is
     * repeated with a growing pause, at most [MAXIMUM_CONSECUTIVE_STATUS_READ_FAILURES] times in
     * a row. The whole wait, including a request that is still in flight, ends after
     * [maximumWaitMillis]. When the wait ends without a final answer the transaction is simply
     * still pending ([Sora2FinalityPendingException]); the durable journal row is untouched and
     * the background status worker keeps resolving it.
     */
    suspend fun awaitTerminalStatus(
        staged: Sora2PendingSubmissionLocal,
        maximumWaitMillis: Long = MAXIMUM_FOREGROUND_FINALITY_WAIT_MILLIS,
    ): ExtrinsicSubmitStatus {
        check(maximumWaitMillis in 1L..MAXIMUM_FOREGROUND_FINALITY_WAIT_MILLIS) {
            "SORA2_FINALITY_WAIT_INVALID"
        }
        val terminal = withTimeoutOrNull(maximumWaitMillis) {
            var consecutiveReadFailures = 0
            var result: ExtrinsicSubmitStatus? = null
            while (result == null) {
                currentCoroutineContext().ensureActive()
                val readSucceeded = try {
                    recoveryMutex.withLock {
                        recoverStatusOnlyLocked(
                            maximumTransactions = 1,
                            priorityLocalId = staged.localId,
                            startOffset = null,
                        )
                    }
                    result = terminalStatusOrNull(staged)
                    true
                } catch (error: CancellationException) {
                    // Includes the overall time limit, which interrupts a slow request.
                    throw error
                } catch (_: Exception) {
                    false
                }
                if (result != null) break
                if (readSucceeded) {
                    consecutiveReadFailures = 0
                    delay(FINALITY_POLL_INTERVAL_MILLIS)
                } else {
                    consecutiveReadFailures += 1
                    if (consecutiveReadFailures >= MAXIMUM_CONSECUTIVE_STATUS_READ_FAILURES) break
                    delay(statusReadBackoffMillis(consecutiveReadFailures))
                }
            }
            result
        }
        // The time limit may have fired just after a pass stored the final state.
        return terminal
            ?: terminalStatusAfterWaitOrNull(staged)
            ?: throw Sora2FinalityPendingException()
    }

    private suspend fun terminalStatusAfterWaitOrNull(
        staged: Sora2PendingSubmissionLocal,
    ): ExtrinsicSubmitStatus? = try {
        terminalStatusOrNull(staged)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    /** Reads the journal row only; the row is the single authority for a final state. */
    private suspend fun terminalStatusOrNull(
        staged: Sora2PendingSubmissionLocal,
    ): ExtrinsicSubmitStatus? {
        val current = database.walletIdentityDao()
            .getSora2PendingSubmission(staged.localId)
            ?: throw IllegalStateException("SORA2_PENDING_SUBMISSION_MISSING")
        WalletIdentityDao.validateSora2PendingSubmission(current)
        requireSameRecoveryWitness(staged, current)
        return when (current.state) {
            Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS ->
                ExtrinsicSubmitStatus(
                    success = true,
                    txHash = current.transactionHash,
                    blockHash = checkNotNull(current.terminalBlockHash),
                )
            Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE ->
                ExtrinsicSubmitStatus(
                    success = false,
                    txHash = current.transactionHash,
                    blockHash = checkNotNull(current.terminalBlockHash),
                    failureDetail = detailsStore
                        .get(current.walletId, current.transactionHash)
                        ?.chainFailure,
                )
            Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED ->
                ExtrinsicSubmitStatus(
                    success = false,
                    txHash = current.transactionHash,
                    blockHash = null,
                )
            else -> null
        }
    }

    /**
     * The earliest transaction of [walletId] that does the same thing as [activity] and has no
     * final status yet. A transaction the node explicitly refused is not waiting. Used to warn
     * before the same action is signed a second time.
     */
    suspend fun findWaitingSameAction(
        walletId: String,
        activity: Sora2SubmissionActivity,
    ): Sora2SubmissionDetails? {
        val unresolved = database.walletIdentityDao()
            .getUnresolvedSora2PendingSubmissions()
            .filter { it.walletId == walletId }
        if (unresolved.isEmpty()) return null
        val unresolvedHashes = unresolved.mapTo(hashSetOf()) { it.transactionHash }
        return detailsStore.list(walletId)
            .filter { details ->
                details.transactionHash in unresolvedHashes &&
                    details.nodeRefusal == null &&
                    details.activity?.isSameActionAs(activity) == true
            }
            .minByOrNull(Sora2SubmissionDetails::createdAt)
    }

    /** Remembers what a staged transaction does. Best effort; never part of the result. */
    suspend fun recordActivity(
        staged: Sora2PendingSubmissionLocal,
        activity: Sora2SubmissionActivity,
    ) {
        detailsStore.recordActivity(staged.walletId, staged.transactionHash, activity)
    }

    /** Remembers the node's explicit refusal next to the unchanged journal row. Best effort. */
    suspend fun recordNodeRefusal(
        staged: Sora2PendingSubmissionLocal,
        refusal: Sora2NodeRefusal,
    ) {
        detailsStore.recordNodeRefusal(staged.walletId, staged.transactionHash, refusal)
    }

    private fun statusReadBackoffMillis(consecutiveFailures: Int): Long =
        minOf(
            STATUS_READ_BACKOFF_BASE_MILLIS shl (consecutiveFailures - 1).coerceIn(0, 16),
            STATUS_READ_BACKOFF_MAXIMUM_MILLIS,
        )

    private suspend fun recoverStatusOnlyLocked(
        maximumTransactions: Int,
        priorityLocalId: String?,
        startOffset: Long?,
    ): Sora2PendingRecoveryPass {
        check(maximumTransactions in 1..MAXIMUM_TRANSACTIONS_PER_PASS) {
            "SORA2_RECOVERY_BATCH_INVALID"
        }
        if (WalletRecoveryCapabilityGate.mode() != WalletRecoveryCapabilityGate.Mode.NORMAL) {
            return Sora2PendingRecoveryPass(0, 0, 0, 1, 0)
        }
        val dao = database.walletIdentityDao()
        val all = dao.getUnresolvedSora2PendingSubmissions()
        val ordered = all.sortedWith(
            compareBy<Sora2PendingSubmissionLocal>(
                Sora2PendingSubmissionLocal::eraDeathBlockExclusive,
                Sora2PendingSubmissionLocal::createdAt,
                Sora2PendingSubmissionLocal::localId,
            )
        )
        val priority = priorityLocalId?.let { priority ->
            val exact = ordered.singleOrNull { it.localId == priority }
                ?: return Sora2PendingRecoveryPass(
                    examined = 0,
                    resolved = 0,
                    unresolved = 0,
                    failures = 1,
                    remainingUnattempted = all.size,
                )
            exact
        }
        val selectionCursor = startOffset?.let { offset ->
            Sora2PendingRecoveryBatchPlanner.cursorForOffset(offset, ordered.size)
        } ?: recoveryBatchCursor
        val batch = Sora2PendingRecoveryBatchPlanner.select(
            ordered = ordered,
            cursor = selectionCursor,
            maximum = maximumTransactions,
            priority = priority,
        )
        val selected = batch.rows
        if (startOffset == null) {
            recoveryBatchCursor = batch.nextCursor
        }
        if (selected.isEmpty()) {
            return Sora2PendingRecoveryPass(0, 0, 0, 0, 0)
        }

        selected.forEach { row ->
            requireWalletWitness(dao, row.walletId, row.publicKey)
        }
        val liveIdentity = runtimeRpcClient.getFinalizedIdentity()
        val liveGenesisHash = liveIdentity.genesisHash.canonicalExtrinsicHash()
        val finalizedHeight = liveIdentity.finalizedBlockNumber
        check(finalizedHeight >= 0L) { "SORA2_RECOVERY_FINALIZED_HEIGHT_INVALID" }
        val fixtures = mutableMapOf<RecoveryFixtureKey, RuntimeSnapshot?>()
        val fixtureKeyByLocalId = mutableMapOf<String, RecoveryFixtureKey>()
        var unavailableFixtures = 0
        selected.forEach { row ->
            check(row.genesisHash == liveGenesisHash) {
                "SORA2_RECOVERY_GENESIS_MISMATCH"
            }
            fixtureKeyByLocalId[row.localId] = row.recoveryFixtureKey()
        }

        /**
         * The runtime a row was signed with, looked up only for a row found in a block. Finding
         * (or not finding) the exact transaction hash in the canonical blocks of its mortal era
         * needs no runtime, so a row whose signing runtime is unavailable still reaches
         * EXPIRED_NOT_INCLUDED once its era has passed.
         */
        suspend fun signingSnapshotOrNull(row: Sora2PendingSubmissionLocal): RuntimeSnapshot? {
            val key = fixtureKeyByLocalId.getValue(row.localId)
            if (fixtures.containsKey(key)) return fixtures[key]
            return runtimeManager.getReviewedRecoveryRuntimeSnapshotOrNull(
                genesisHash = key.genesisHash,
                specVersion = key.specVersion,
                transactionVersion = key.transactionVersion,
                metadataSha256 = key.metadataSha256,
                typesSha256 = key.typesSha256,
                eraBirthBlockHash = row.eraBirthBlockHash,
            ).also { fixtures[key] = it }
        }
        val admitted = selected

        val heights = linkedSetOf<Long>()
        admitted.forEach { row ->
            check(finalizedHeight >= row.eraBirthBlock) {
                "SORA2_RECOVERY_FINALITY_BEFORE_BIRTH"
            }
            val lastHeight = minOf(
                finalizedHeight,
                row.eraDeathBlockExclusive - 1L,
            )
            val cacheKey = row.recoveryCacheKey()
            val cachedThrough = scannedThroughBlockExclusive[cacheKey]
                ?: row.eraBirthBlock
            check(cachedThrough <= Math.addExact(lastHeight, 1L)) {
                "SORA2_RECOVERY_FINALIZED_HEIGHT_REGRESSION"
            }
            var height = maxOf(
                row.eraBirthBlock,
                cachedThrough,
            )
            while (height <= lastHeight) {
                check(heights.size < MAXIMUM_BLOCKS_PER_PASS || height in heights) {
                    "SORA2_RECOVERY_BLOCK_LIMIT_EXCEEDED"
                }
                heights += height
                height = Math.addExact(height, 1L)
            }
        }
        val hashes = admitted.mapTo(linkedSetOf()) { it.transactionHash }
        val blocks = linkedMapOf<Long, Sora2CanonicalBlock>()
        heights.sorted().forEach { height ->
            currentCoroutineContext().ensureActive()
            blocks[height] = runtimeRpcClient.getCanonicalBlock(
                blockNumber = height,
                finalizedHeight = finalizedHeight,
                recoveryHashes = hashes,
            )
        }
        admitted.forEach { row ->
            val cacheKey = row.recoveryCacheKey()
            if ((scannedThroughBlockExclusive[cacheKey] ?: row.eraBirthBlock) == row.eraBirthBlock) {
                check(blocks.getValue(row.eraBirthBlock).blockHash == row.eraBirthBlockHash) {
                    "SORA2_RECOVERY_ERA_BIRTH_HASH_MISMATCH"
                }
            }
        }

        data class Inclusion(
            val block: Sora2CanonicalBlock,
            val extrinsicIndex: Int,
        )

        val inclusions = linkedMapOf<String, Inclusion>()
        blocks.values.forEach { block ->
            block.matchingExtrinsicIndices.forEach { (hash, index) ->
                check(inclusions.put(hash, Inclusion(block, index)) == null) {
                    "SORA2_RECOVERY_DUPLICATE_INCLUSION"
                }
            }
        }
        val eventOutcomes = mutableMapOf<String, Map<Long, Sora2ExtrinsicEventOutcome>>()
        val rawStorage = mutableMapOf<Pair<String, String>, String>()
        val decodedEvents =
            mutableMapOf<Pair<String, RecoveryFixtureKey>, Map<Long, Sora2ExtrinsicEventOutcome>>()
        admitted.forEach { row ->
            val inclusion = inclusions[row.transactionHash] ?: return@forEach
            currentCoroutineContext().ensureActive()
            val snapshot = signingSnapshotOrNull(row) ?: return@forEach
            val fixtureKey = fixtureKeyByLocalId.getValue(row.localId)
            val decodeKey = inclusion.block.blockHash to fixtureKey
            val outcomes = decodedEvents[decodeKey] ?: run {
                val eventsStorage = snapshot.metadata.module(Pallete.SYSTEM.palletName)
                    .storage("Events")
                val storageKey = eventsStorage.storageKey()
                val rawKey = inclusion.block.blockHash to storageKey
                val raw = rawStorage[rawKey] ?: runtimeRpcClient.getStorageAt(
                    storageKey = storageKey,
                    blockHash = inclusion.block.blockHash,
                ).also { rawStorage[rawKey] = it }
                requireSystemEventOutcomes(
                    snapshot = snapshot,
                    storageHex = raw,
                ).also { decodedEvents[decodeKey] = it }
            }
            eventOutcomes[row.localId] = outcomes
        }

        data class Resolution(
            val row: Sora2PendingSubmissionLocal,
            val state: String,
            val blockNumber: Long?,
            val blockHash: String?,
            val failureDetail: String? = null,
        )

        val resolutions = mutableListOf<Resolution>()
        val undecodedInclusions = mutableSetOf<String>()
        var unresolved = 0
        admitted.forEach { row ->
            val inclusion = inclusions[row.transactionHash]
            val outcomes = eventOutcomes[row.localId]
            if (inclusion != null && outcomes == null) {
                // Included, but the runtime that can decode its events is unavailable in this
                // pass. The row stays unresolved and is retried; it is never guessed. Its scan
                // restarts at the era birth next time so the inclusion is found again.
                unavailableFixtures += 1
                undecodedInclusions += row.localId
            } else if (inclusion != null) {
                val outcome = checkNotNull(outcomes)
                    .getValue(inclusion.extrinsicIndex.toLong())
                resolutions += Resolution(
                    row = row,
                    state = if (outcome.success) {
                        Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS
                    } else {
                        Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE
                    },
                    blockNumber = inclusion.block.blockNumber,
                    blockHash = inclusion.block.blockHash,
                    failureDetail = outcome.failureDetail,
                )
            } else if (finalizedHeight >= row.eraDeathBlockExclusive) {
                // Every height in [birth, death) was fetched above before this branch is reachable.
                resolutions += Resolution(
                    row = row,
                    state = Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED,
                    blockNumber = null,
                    blockHash = null,
                )
            } else {
                unresolved += 1
            }
        }

        var resolved = 0
        resolutions.forEach { resolution ->
            currentCoroutineContext().ensureActive()
            resolution.failureDetail?.let { detail ->
                // Stored before the state so the reason is there when the final state is seen.
                detailsStore.recordChainFailure(
                    walletId = resolution.row.walletId,
                    transactionHash = resolution.row.transactionHash,
                    detail = detail,
                )
            }
            WalletMutationCoordinator.withLock {
                val current = dao.getSora2PendingSubmission(resolution.row.localId)
                    ?: throw IllegalStateException("SORA2_PENDING_SUBMISSION_MISSING")
                WalletIdentityDao.validateSora2PendingSubmission(current)
                if (current.isAuthoritativelyTerminal) return@withLock
                requireSameRecoveryWitness(resolution.row, current)
                dao.updateSora2PendingSubmission(
                    expected = current,
                    state = resolution.state,
                    ambiguous = false,
                    terminalBlockNumber = resolution.blockNumber,
                    terminalBlockHash = resolution.blockHash,
                    terminalFinalizedHeight = finalizedHeight,
                    updatedAt = positiveNow(),
                )
                resolved += 1
            }
            scannedThroughBlockExclusive.remove(resolution.row.recoveryCacheKey())
        }
        undecodedInclusions.forEach { localId ->
            admitted.firstOrNull { it.localId == localId }?.let { row ->
                scannedThroughBlockExclusive.remove(row.recoveryCacheKey())
            }
        }
        admitted.filterNot { row ->
            row.localId in undecodedInclusions ||
                resolutions.any { it.row.localId == row.localId }
        }
            .forEach { row ->
                val lastScanned = minOf(
                    finalizedHeight,
                    row.eraDeathBlockExclusive - 1L,
                )
                if (lastScanned >= row.eraBirthBlock) {
                    scannedThroughBlockExclusive[row.recoveryCacheKey()] =
                        Math.addExact(lastScanned, 1L)
                }
            }
        return Sora2PendingRecoveryPass(
            examined = selected.size,
            resolved = resolved,
            unresolved = unresolved,
            failures = unavailableFixtures,
            remainingUnattempted = all.size - selected.size,
        )
    }

    private fun Sora2PendingSubmissionLocal.recoveryCacheKey(): RecoveryCacheKey =
        RecoveryCacheKey(localId, createdAt, eraBirthBlockHash)

    private fun Sora2PendingSubmissionLocal.recoveryFixtureKey(): RecoveryFixtureKey =
        RecoveryFixtureKey(
            genesisHash = genesisHash,
            specVersion = specVersion,
            transactionVersion = transactionVersion,
            metadataSha256 = metadataSha256,
            typesSha256 = typesSha256,
        )

    private suspend fun requireWalletWitness(
        dao: WalletIdentityDao,
        walletId: String,
        expectedPublicKey: String?,
    ): String {
        check(!dao.hasActiveDeletionOperation()) { "WALLET_DELETION_ACTIVE" }
        val migration = dao.getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        val wallet = dao.getWallet(walletId)
        val account = dao.getNetworkAccount(
            walletId = walletId,
            networkId = Sora2PendingSubmissionLocal.NETWORK_SORA2,
        )
        val decodedPublicKey = addressCodec.soraPublicKeyOrNull(walletId)
            ?.toHexString(withPrefix = false)
            ?.lowercase()
            ?: throw IllegalStateException("SORA2_PENDING_WALLET_ADDRESS_INVALID")
        check(
            database.accountDao().getAccount(walletId) != null &&
                wallet?.migrationState == "VERIFIED" &&
                (migration == null || migration.state == "VERIFIED") &&
                account?.enabled == true &&
                account.address == walletId &&
                account.publicKey == decodedPublicKey &&
                (expectedPublicKey == null || expectedPublicKey == decodedPublicKey)
        ) { "SORA2_PENDING_WALLET_WITNESS_MISMATCH" }
        return decodedPublicKey
    }

    private fun requireSystemEventOutcomes(
        snapshot: RuntimeSnapshot,
        storageHex: String,
    ): Map<Long, Sora2ExtrinsicEventOutcome> {
        val metadata = snapshot.metadata
        val eventStorage = metadata.module(Pallete.SYSTEM.palletName).storage("Events")
        val eventType = checkNotNull(eventStorage.type.value) {
            "SORA2_RECOVERY_EVENTS_TYPE_MISSING"
        }
        val decoded = eventType.fromHex(snapshot, storageHex)
        val records = decoded as? List<*>
            ?: throw IllegalStateException("SORA2_RECOVERY_EVENTS_INVALID")
        check(records.all { it is Struct.Instance }) {
            "SORA2_RECOVERY_EVENTS_INVALID"
        }
        val successIndex = metadata.module(Pallete.SYSTEM.palletName)
            .event(Events.EXTRINSIC_SUCCESS.eventName).index
        val failedIndex = metadata.module(Pallete.SYSTEM.palletName)
            .event(Events.EXTRINSIC_FAILED.eventName).index
        val successes = mutableMapOf<Long, Int>()
        val failures = mutableMapOf<Long, Int>()
        val failureDetails = mutableMapOf<Long, String?>()
        records.filterIsInstance<Struct.Instance>().forEach { record ->
            val phase = record.get<DictEnum.Entry<*>>("phase")
            if (phase?.name != "ApplyExtrinsic") return@forEach
            val phaseNumber = phase.value as? BigInteger
                ?: throw IllegalStateException("SORA2_RECOVERY_EVENT_PHASE_INVALID")
            check(phaseNumber.signum() >= 0 && phaseNumber <= BigInteger.valueOf(Long.MAX_VALUE)) {
                "SORA2_RECOVERY_EVENT_PHASE_INVALID"
            }
            val number = phaseNumber.toLong()
            val generic = record.get<GenericEvent.Instance>("event")
            val numericIdentity = generic?.let {
                it.module.index.toInt() to it.event.index.second
            }
            val named = record.get<DictEnum.Entry<*>>("event")
            val namedIdentity = named?.let {
                it.name to it.value?.safeCast<DictEnum.Entry<*>>()?.name
            }
            when {
                numericIdentity == successIndex ||
                    namedIdentity ==
                    (Pallete.SYSTEM.palletName to Events.EXTRINSIC_SUCCESS.eventName) ->
                    successes[number] = (successes[number] ?: 0) + 1
                numericIdentity == failedIndex ||
                    namedIdentity ==
                    (Pallete.SYSTEM.palletName to Events.EXTRINSIC_FAILED.eventName) -> {
                    failures[number] = (failures[number] ?: 0) + 1
                    failureDetails[number] = Sora2DispatchErrorDescriber.describeOrNull(
                        metadata = metadata,
                        genericEvent = generic,
                        namedEvent = named,
                    )
                }
            }
        }
        val indices = successes.keys + failures.keys
        return indices.associateWith { index ->
            val successCount = successes[index] ?: 0
            val failureCount = failures[index] ?: 0
            check(
                (successCount == 1 && failureCount == 0) ||
                    (successCount == 0 && failureCount == 1)
            ) { "SORA2_RECOVERY_SYSTEM_EVENT_AMBIGUOUS" }
            Sora2ExtrinsicEventOutcome(
                success = successCount == 1,
                failureDetail = failureDetails[index].takeIf { successCount == 0 },
            )
        }
    }

    private fun requireSameRecoveryWitness(
        original: Sora2PendingSubmissionLocal,
        current: Sora2PendingSubmissionLocal,
    ) {
        check(
            current.copy(
                state = original.state,
                submissionIsAmbiguous = original.submissionIsAmbiguous,
                terminalBlockNumber = original.terminalBlockNumber,
                terminalBlockHash = original.terminalBlockHash,
                terminalFinalizedHeight = original.terminalFinalizedHeight,
                updatedAt = original.updatedAt,
            ) == original
        ) { "SORA2_PENDING_RECOVERY_WITNESS_CHANGED" }
    }

    private fun kickBestEffort() {
        try {
            recoveryKick.kickAfterJournal()
        } catch (_: RuntimeException) {
            // The row is already durable. Startup schedules the same unique status-only worker.
        }
    }

    private fun positiveNow(): Long = maxOf(System.currentTimeMillis(), 1L)

    private fun localId(transactionHash: String): String =
        "sora2:${transactionHash.removePrefix("0x")}"

    private companion object {
        const val SORA2_MORTAL_ERA_PERIOD = 64
        const val MAXIMUM_TRANSACTIONS_PER_PASS = 8
        const val MAXIMUM_BLOCKS_PER_PASS =
            SORA2_MORTAL_ERA_PERIOD * MAXIMUM_TRANSACTIONS_PER_PASS
        const val FINALITY_POLL_INTERVAL_MILLIS = 3_000L
        const val MAXIMUM_FOREGROUND_FINALITY_WAIT_MILLIS = 120_000L

        /**
         * Pauses of 1, 2, 4, 8 and 8 seconds separate six failed reads. A short interruption (one
         * lost request, a Wi-Fi to mobile handover) is absorbed; when the network stays away the
         * screen stops waiting after roughly half a minute and reports "waiting for
         * confirmation" instead of holding a spinner for the whole time limit.
         */
        const val MAXIMUM_CONSECUTIVE_STATUS_READ_FAILURES = 6
        const val STATUS_READ_BACKOFF_BASE_MILLIS = 1_000L
        const val STATUS_READ_BACKOFF_MAXIMUM_MILLIS = 8_000L
    }
}

/** The foreground wait ended and the transaction has no final status yet. Not a failure. */
class Sora2FinalityPendingException : IllegalStateException("SORA2_FINALITY_PENDING")

internal data class Sora2ExtrinsicEventOutcome(
    val success: Boolean,
    /** Runtime error of a failed extrinsic, when its `ExtrinsicFailed` event could be read. */
    val failureDetail: String?,
)

/**
 * Turns the `DispatchError` of a `System.ExtrinsicFailed` event into a short name such as
 * `LiquidityProxy.SlippageNotTolerated` or `Token.FundsUnavailable`. It only describes a failure
 * that the event counts above have already proven; any shape it does not recognise yields null
 * and never changes the status.
 */
internal object Sora2DispatchErrorDescriber {

    fun describeOrNull(
        metadata: RuntimeMetadata,
        genericEvent: GenericEvent.Instance?,
        namedEvent: DictEnum.Entry<*>?,
    ): String? = try {
        val dispatchError = genericEvent?.arguments?.firstOrNull()
            ?: (namedEvent?.value as? DictEnum.Entry<*>)?.value.firstField()
        describe(metadata, dispatchError as? DictEnum.Entry<*>)
    } catch (_: Exception) {
        null
    }

    private fun Any?.firstField(): Any? = when (this) {
        is Struct.Instance -> mapping["dispatchError"] ?: mapping.values.firstOrNull()
        is List<*> -> firstOrNull()
        else -> this
    }

    private fun describe(metadata: RuntimeMetadata, error: DictEnum.Entry<*>?): String? {
        error ?: return null
        if (error.name != "Module") {
            val inner = (error.value as? DictEnum.Entry<*>)?.name
            return listOfNotNull(error.name, inner).joinToString(".").sanitizedStatusText()
        }
        val moduleError = error.value as? Struct.Instance ?: return null
        val moduleIndex = moduleError.mapping["index"].toSmallIntOrNull() ?: return null
        val module = metadata.modules.values.firstOrNull { it.index.toInt() == moduleIndex }
            ?: return null
        // Pallet error enums are numbered in declaration order, which is the order of this map.
        val errorIndex = moduleError.mapping["error"].firstByteOrNull()
        val errorName = errorIndex?.let { module.errors.values.elementAtOrNull(it)?.name }
        return listOfNotNull(module.name, errorName ?: errorIndex?.let { "Error $it" })
            .joinToString(".")
            .sanitizedStatusText()
    }

    private fun Any?.toSmallIntOrNull(): Int? = when (this) {
        is BigInteger -> takeIf { it.signum() >= 0 && it.bitLength() <= 16 }?.toInt()
        is Number -> toInt().takeIf { it in 0..0xFFFF }
        is UByte -> toInt()
        else -> null
    }

    private fun Any?.firstByteOrNull(): Int? = when (this) {
        is ByteArray -> firstOrNull()?.let { it.toInt() and 0xFF }
        is List<*> -> firstOrNull().toSmallIntOrNull()
        else -> toSmallIntOrNull()
    }
}

/** Prevents a deterministic hash already protected by the journal from being submitted again. */
class Sora2PendingSubmissionAlreadyJournaled(
    override val transactionHash: String,
) : IllegalStateException("SORA2_PENDING_SUBMISSION_ALREADY_JOURNALED"),
    ExtrinsicSubmissionUnknown
