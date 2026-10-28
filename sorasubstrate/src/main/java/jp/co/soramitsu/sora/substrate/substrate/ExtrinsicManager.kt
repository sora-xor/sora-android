/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.WalletSigningGate
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.QualifiedSora2MutationRuntime
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2MutationRuntimeContext
import jp.co.soramitsu.sora.substrate.runtime.requireSameSora2MutationRuntimeIdentity
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Marker for a prepared extrinsic that provably never reached the RPC transport boundary. */
interface DefinitelyNotSubmitted

/**
 * Marker for a signed transaction whose exact hash is known but whose submission cannot be
 * classified after the RPC transport handoff. It is safe to reconcile [transactionHash], never to
 * resubmit the signed mutation or present the failure as a definitive rejection.
 */
interface ExtrinsicSubmissionUnknown {
    val transactionHash: String

    /**
     * True when the node answered the submission with the transaction hash and only the wait for
     * finality is unfinished. False when even the node's receipt of the transaction is unknown.
     */
    val acceptedByNode: Boolean get() = false
}

class ExtrinsicSubmissionUnknownException(
    override val transactionHash: String,
    cause: Throwable,
    override val acceptedByNode: Boolean = false,
) : IllegalStateException("EXTRINSIC_SUBMISSION_UNKNOWN", cause),
    ExtrinsicSubmissionUnknown

class ExtrinsicSubmissionUnknownCancellation(
    override val transactionHash: String,
    cause: CancellationException,
    override val acceptedByNode: Boolean = false,
) : CancellationException("EXTRINSIC_SUBMISSION_UNKNOWN_CANCELLED"),
    ExtrinsicSubmissionUnknown {
    init {
        initCause(cause)
    }
}

class PreparedExtrinsicPreTransportException(
    cause: Throwable,
) : IllegalStateException("EXTRINSIC_PRE_TRANSPORT_VALIDATION_FAILED", cause),
    DefinitelyNotSubmitted

class PreparedExtrinsicPreTransportCancellation(
    cause: CancellationException,
) : CancellationException("EXTRINSIC_PRE_TRANSPORT_VALIDATION_CANCELLED"),
    DefinitelyNotSubmitted {
    init {
        initCause(cause)
    }
}

internal fun requireExactSora2TransferFee(
    reviewedFee: BigInteger,
    exactFee: BigInteger?,
): BigInteger {
    check(reviewedFee > BigInteger.ZERO) { "SORA2_TRANSFER_REVIEWED_FEE_INVALID" }
    val resolved = exactFee
        ?: throw IllegalStateException("SORA2_TRANSFER_EXACT_FEE_UNAVAILABLE")
    check(resolved > BigInteger.ZERO) { "SORA2_TRANSFER_EXACT_FEE_INVALID" }
    check(resolved == reviewedFee) { "SORA2_TRANSFER_FEE_CHANGED" }
    return resolved
}

@Singleton
class ExtrinsicManager @Inject constructor(
    private val factory: ExtrinsicBuilderFactory,
    private val runtimeManager: RuntimeManager,
    private val database: AppDatabase,
    private val pendingSubmissionCoordinator: Sora2PendingSubmissionCoordinator,
    private val runtimeRpcClient: Sora2BoundedRuntimeRpcClient,
) {
    class PreparedExtrinsic internal constructor(
        val walletId: String,
        val encoded: String,
        val transactionHash: String,
        internal val signingRuntime: Sora2MutationRuntimeContext,
        internal val purpose: PreparedPurpose = PreparedPurpose.GENERIC,
    )

    internal enum class PreparedPurpose {
        GENERIC,
        LEGACY_MIGRATION,
        POLKAMARKT,
    }

    fun interface WatchingListener {
        fun onChange(txHash: String, success: Boolean, block: String?)
    }

    private var watchingListener: WatchingListener? = null

    suspend fun calcFee(
        from: String,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): BigInteger? {
        val runtimeContext = runtimeManager.getMutationRuntimeContext()
        val contextBoundBuilder = factory.createForFee(from, runtimeContext)
        check(contextBoundBuilder.runtimeContext === runtimeContext) {
            "SORA2_FEE_RUNTIME_CONTEXT_REPLACED"
        }
        val builder = contextBoundBuilder.builder
        builder.formExtrinsic()
        val extrinsic = builder.build(useBatchAll)
        return runtimeRpcClient.queryPartialFee(extrinsic)
    }

    /**
     * Calculates the final Polkamarkt fee with the exact qualified context that will also encode
     * and sign the mutation. Quote/preview fees may use [calcFee], but execution must use this
     * context-bound path.
     */
    suspend fun calcPolkamarktFee(
        from: String,
        runtime: QualifiedSora2MutationRuntime,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): BigInteger? {
        val contextBoundBuilder = factory.createForPolkamarkt(from, runtime)
        check(contextBoundBuilder.runtimeContext === runtime.context) {
            "SORA2_FEE_RUNTIME_CONTEXT_REPLACED"
        }
        val builder = contextBoundBuilder.builder
        builder.formExtrinsic()
        val extrinsic = builder.build(useBatchAll)
        return runtimeRpcClient.queryPartialFee(extrinsic)
    }

    suspend fun submitExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): Result<String> {
        return try {
            Result.success(
                WalletMutationCoordinator.withLock {
                    val prepared = prepareExtrinsicUnlocked(
                        from = from,
                        keypair = keypair,
                        useBatchAll = useBatchAll,
                        formExtrinsic = formExtrinsic,
                    )
                    submitGenericPreparedOnceUnlocked(prepared).transactionHash
                }
            )
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun submitAndWaitExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean = false,
        untilStatus: String = SubstrateCalls.FINALIZED,
        activity: Sora2SubmissionActivity? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus = submitAndWaitExtrinsicForPurpose(
        from = from,
        keypair = keypair,
        useBatchAll = useBatchAll,
        untilStatus = untilStatus,
        purpose = PreparedPurpose.GENERIC,
        activity = activity,
        formExtrinsic = formExtrinsic,
    )

    /**
     * The legacy Iroha claim needs a distinct in-memory purpose so an unresolved durable claim
     * witness can block an explicit retry. It remains encoded as the backward-compatible generic
     * journal operation and is never resubmitted by recovery.
     */
    suspend fun submitLegacyMigrationAndWaitExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean = false,
        untilStatus: String = SubstrateCalls.FINALIZED,
        activity: Sora2SubmissionActivity? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus = submitAndWaitExtrinsicForPurpose(
        from = from,
        keypair = keypair,
        useBatchAll = useBatchAll,
        untilStatus = untilStatus,
        purpose = PreparedPurpose.LEGACY_MIGRATION,
        activity = activity,
        formExtrinsic = formExtrinsic,
    )

    private suspend fun submitAndWaitExtrinsicForPurpose(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean,
        untilStatus: String,
        purpose: PreparedPurpose,
        activity: Sora2SubmissionActivity?,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus {
        check(untilStatus == SubstrateCalls.FINALIZED) {
            "SORA2_CANONICAL_FINALITY_REQUIRED"
        }
        val submitted = WalletMutationCoordinator.withLock {
            if (purpose != PreparedPurpose.LEGACY_MIGRATION) {
                // A second legacy claim is refused outright by its own gate while one is unresolved.
                requireNoWaitingSameAction(from, activity)
            }
            val prepared = prepareExtrinsicUnlocked(
                from = from,
                keypair = keypair,
                useBatchAll = useBatchAll,
                purpose = purpose,
                formExtrinsic = formExtrinsic,
            )
            submitGenericPreparedOnceUnlocked(prepared, activity)
        }
        return awaitSubmittedTerminal(submitted)
    }

    /**
     * Signs without submitting so a coordinator can durably persist the deterministic transaction
     * hash before the only transport attempt.
     */
    suspend fun prepareExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): PreparedExtrinsic = WalletMutationCoordinator.withLock {
        prepareExtrinsicUnlocked(
            from = from,
            keypair = keypair,
            useBatchAll = useBatchAll,
            formExtrinsic = formExtrinsic,
        )
    }

    /**
     * Polkamarkt-only preparation path. [runtime] is the opaque context qualified immediately
     * before signing and is passed unchanged into the exact builder that encodes the call.
     */
    suspend fun preparePolkamarktExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        runtime: QualifiedSora2MutationRuntime,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): PreparedExtrinsic = WalletMutationCoordinator.withLock {
        prepareExtrinsicUnlocked(
            from = from,
            keypair = keypair,
            useBatchAll = useBatchAll,
            qualifiedPolkamarktRuntime = runtime,
            purpose = PreparedPurpose.POLKAMARKT,
            formExtrinsic = formExtrinsic,
        )
    }

    private suspend fun prepareExtrinsicUnlocked(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean,
        qualifiedPolkamarktRuntime: QualifiedSora2MutationRuntime? = null,
        purpose: PreparedPurpose = PreparedPurpose.GENERIC,
        preSigningValidation: suspend () -> Unit = {},
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): PreparedExtrinsic {
        check(
            (qualifiedPolkamarktRuntime == null && purpose != PreparedPurpose.POLKAMARKT) ||
                (qualifiedPolkamarktRuntime != null && purpose == PreparedPurpose.POLKAMARKT)
        ) { "SORA2_PREPARED_PURPOSE_INVALID" }
        requireSigningWalletAvailable(from)
        if (purpose == PreparedPurpose.LEGACY_MIGRATION) {
            pendingSubmissionCoordinator.requireLegacyMigrationAdmissible(from)
        }
        val contextBoundBuilder = if (qualifiedPolkamarktRuntime == null) {
            val signingRuntime = runtimeManager.getMutationRuntimeContext()
            factory.createForSigning(from, keypair, signingRuntime).also {
                check(it.runtimeContext === signingRuntime) {
                    "SORA2_SIGNING_RUNTIME_CONTEXT_REPLACED"
                }
            }
        } else {
            factory.createForPolkamarkt(from, keypair, qualifiedPolkamarktRuntime).also {
                check(it.runtimeContext === qualifiedPolkamarktRuntime.context) {
                    "SORA2_SIGNING_RUNTIME_CONTEXT_REPLACED"
                }
            }
        }
        val builder = contextBoundBuilder.builder
        builder.formExtrinsic()
        requireSigningWalletAvailable(from)
        preSigningValidation()
        requireSigningWalletAvailable(from)
        val extrinsic = builder.build(useBatchAll)
        return PreparedExtrinsic(
            walletId = from,
            encoded = extrinsic,
            transactionHash = extrinsic.extrinsicHash().canonicalExtrinsicHash(),
            signingRuntime = contextBoundBuilder.runtimeContext,
            purpose = purpose,
        )
    }

    /**
     * Submits [prepared] exactly once. Callers must never use this method to retry an ambiguous
     * result; reconciliation happens by [PreparedExtrinsic.transactionHash].
     */
    suspend fun submitPreparedAndWait(
        prepared: PreparedExtrinsic,
        untilStatus: String = SubstrateCalls.FINALIZED,
        preTransportValidation: suspend () -> Unit = {},
    ): ExtrinsicSubmitStatus {
        check(untilStatus == SubstrateCalls.FINALIZED) {
            "SORA2_CANONICAL_FINALITY_REQUIRED"
        }
        val submitted = WalletMutationCoordinator.withLock {
            submitGenericPreparedOnceUnlocked(
                prepared = prepared,
                preTransportValidation = preTransportValidation,
            )
        }
        return awaitSubmittedTerminal(submitted)
    }

    /** Every prepared purpose stages and arms the exact witness before one bounded HTTP handoff. */
    private suspend fun submitGenericPreparedOnceUnlocked(
        prepared: PreparedExtrinsic,
        activity: Sora2SubmissionActivity? = null,
        preTransportValidation: suspend () -> Unit = {},
    ): jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal {
        val staged = pendingSubmissionCoordinator.stageBeforeTransport(prepared)
        if (activity != null) {
            // Stored with the journal row and before transport, so a transaction that is still
            // waiting is shown as what it is even if the app is closed during the wait.
            presentationBestEffort {
                pendingSubmissionCoordinator.recordActivity(staged, activity)
            }
        }
        val expectedHash = requireGenericPreparedExtrinsicPreTransport(
            prepared = prepared,
            staged = staged,
            preTransportValidation = preTransportValidation,
        )
        val armed = try {
            pendingSubmissionCoordinator.armForTransport(staged)
        } catch (error: Throwable) {
            pendingSubmissionCoordinator.kickAfterUnknownBestEffort()
            throw submissionUnknown(expectedHash, error)
        }
        val returnedHash = try {
            runtimeRpcClient.submitExtrinsicOnce(prepared.encoded)
        } catch (error: Throwable) {
            pendingSubmissionCoordinator.kickAfterUnknownBestEffort()
            val refusal = Sora2SubmissionRefusalClassifier.refusalOrNull(error)
            if (refusal != null) {
                // The node answered with an explicit refusal, or no reviewed endpoint could be
                // connected to: the transaction was not broadcast and the user is told so. The
                // journal row is left exactly as it is; the status check still scans its
                // validity window and lets it expire.
                presentationBestEffort {
                    pendingSubmissionCoordinator.recordNodeRefusal(armed, refusal)
                }
                throw ExtrinsicRefusedByNodeException(expectedHash, refusal, error)
            }
            throw submissionUnknown(expectedHash, error)
        }
        return try {
            check(returnedHash == expectedHash) { "EXTRINSIC_HASH_MISMATCH" }
            pendingSubmissionCoordinator.markSubmitted(armed)
        } catch (error: Throwable) {
            pendingSubmissionCoordinator.kickAfterUnknownBestEffort()
            throw submissionUnknown(expectedHash, error)
        }
    }

    private suspend fun awaitSubmittedTerminal(
        submitted: jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal,
    ): ExtrinsicSubmitStatus {
        val result = try {
            pendingSubmissionCoordinator.awaitTerminalStatus(submitted)
        } catch (error: Throwable) {
            pendingSubmissionCoordinator.kickAfterUnknownBestEffort()
            throw submissionUnknown(submitted.transactionHash, error, acceptedByNode = true)
        }
        notifyWatchingListenerBestEffort(result)
        return result
    }

    /**
     * Refuses to sign the same action for the same account while an earlier one is still waiting
     * for confirmation, unless the user was told and confirmed again
     * ([Sora2SubmissionActivity.duplicateConfirmed]). If the check itself cannot be made, the
     * action proceeds: a failed local read must not block a transaction.
     */
    private suspend fun requireNoWaitingSameAction(
        from: String,
        activity: Sora2SubmissionActivity?,
    ) {
        if (activity == null || activity.duplicateConfirmed) return
        val waiting = try {
            pendingSubmissionCoordinator.findWaitingSameAction(from, activity)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return
        throw DuplicateWaitingTransactionException(
            waitingTxHash = waiting.transactionHash,
            waitingSinceMillis = waiting.createdAt,
        )
    }

    /** Activity details never decide or delay the result of a transaction. */
    private suspend fun presentationBestEffort(block: suspend () -> Unit) {
        try {
            withContext(NonCancellable) { block() }
        } catch (_: Exception) {
            recordErrorClassBestEffort(FirebaseWrapper.PrivacySafeErrorClass.STATE_FAILURE)
        }
    }

    private suspend fun requireGenericPreparedExtrinsicPreTransport(
        prepared: PreparedExtrinsic,
        staged: jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal,
        preTransportValidation: suspend () -> Unit = {},
    ): String = try {
        requirePreparedExtrinsicPreTransport(prepared, preTransportValidation)
    } catch (original: Throwable) {
        try {
            withContext(NonCancellable) {
                pendingSubmissionCoordinator.removeDefinitelyBeforeTransport(staged)
            }
        } catch (cleanupError: Throwable) {
            pendingSubmissionCoordinator.kickAfterUnknownBestEffort()
            throw submissionUnknown(prepared.transactionHash, cleanupError)
        }
        throw original
    }

    /**
     * The final definitive boundary shared by generic, Polkamarkt, one-shot, and watched
     * submissions. The fresh bounded context is deliberately fetched only after caller policy and
     * every wallet check. Every purpose durably arms the shared ambiguity witness after this
     * comparison and before the single manifest-bound HTTP handoff. A newer canonical finalized
     * head is allowed; every mutation-controlling identity field must still match the context
     * retained with the bytes.
     */
    private suspend fun requirePreparedExtrinsicPreTransport(
        prepared: PreparedExtrinsic,
        preTransportValidation: suspend () -> Unit = {},
    ): String = try {
        requireSigningWalletAvailable(prepared.walletId)
        val canonicalHash = prepared.transactionHash.canonicalExtrinsicHash()
        check(prepared.encoded.extrinsicHash().canonicalExtrinsicHash() == canonicalHash) {
            "EXTRINSIC_PREPARED_HASH_MISMATCH"
        }
        requireSigningWalletAvailable(prepared.walletId)
        preTransportValidation()
        requireSigningWalletAvailable(prepared.walletId)
        val currentRuntime = runtimeManager.getMutationRuntimeContext()
        requireSameSora2MutationRuntimeIdentity(
            signing = prepared.signingRuntime,
            current = currentRuntime,
        )
        canonicalHash
    } catch (error: CancellationException) {
        throw PreparedExtrinsicPreTransportCancellation(error)
    } catch (error: Throwable) {
        throw PreparedExtrinsicPreTransportException(error)
    }

    private fun notifyWatchingListenerBestEffort(result: ExtrinsicSubmitStatus) {
        try {
            watchingListener?.onChange(
                result.txHash,
                result.success,
                result.blockHash,
            )
        } catch (_: Throwable) {
            // A local history observer cannot change an authoritative finalized chain result.
            recordErrorClassBestEffort(
                FirebaseWrapper.PrivacySafeErrorClass.STATE_FAILURE
            )
        }
    }

    private fun submissionUnknown(
        transactionHash: String,
        error: Throwable,
        acceptedByNode: Boolean = false,
    ): Throwable {
        if (error is ExtrinsicSubmissionUnknown) return error
        if (error !is Sora2FinalityPendingException) {
            // A wait that simply has no final answer yet is the normal pending case, not an error.
            recordErrorClassBestEffort(
                FirebaseWrapper.PrivacySafeErrorClass.EXTRINSIC_SUBMISSION
            )
        }
        return if (error is CancellationException) {
            ExtrinsicSubmissionUnknownCancellation(transactionHash, error, acceptedByNode)
        } else {
            ExtrinsicSubmissionUnknownException(transactionHash, error, acceptedByNode)
        }
    }

    private fun recordErrorClassBestEffort(
        errorClass: FirebaseWrapper.PrivacySafeErrorClass,
    ) {
        try {
            FirebaseWrapper.recordErrorClass(errorClass)
        } catch (_: Throwable) {
            // Telemetry is never part of submission classification or a finalized result.
        }
    }

    suspend fun submitAndWatchExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean = false,
        activity: Sora2SubmissionActivity? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus {
        val submitted = WalletMutationCoordinator.withLock {
            requireNoWaitingSameAction(from, activity)
            val prepared = prepareExtrinsicUnlocked(
                from = from,
                keypair = keypair,
                useBatchAll = useBatchAll,
                formExtrinsic = formExtrinsic,
            )
            submitGenericPreparedOnceUnlocked(prepared, activity)
        }
        return awaitSubmittedTerminal(submitted)
    }

    /**
     * Ordinary transfers must not treat a sample/dummy fee as authority. This path signs the exact
     * transfer once, asks the node for the fee of those exact bytes at the final pre-transport
     * boundary, and refuses transport unless it still equals the fee reviewed by the user. The
     * caller's balance validation receives that exact raw XOR fee and runs inside the same boundary.
     */
    suspend fun submitFeeQualifiedAndWatchExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        reviewedFee: BigInteger,
        useBatchAll: Boolean = false,
        activity: Sora2SubmissionActivity? = null,
        preSigningValidation: suspend () -> Unit,
        validateBalances: suspend (exactFee: BigInteger) -> Unit,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus {
        requireExactSora2TransferFee(reviewedFee, reviewedFee)
        val submitted = WalletMutationCoordinator.withLock {
            requireNoWaitingSameAction(from, activity)
            val prepared = prepareExtrinsicUnlocked(
                from = from,
                keypair = keypair,
                useBatchAll = useBatchAll,
                preSigningValidation = preSigningValidation,
                formExtrinsic = formExtrinsic,
            )
            submitGenericPreparedOnceUnlocked(prepared, activity) {
                val exactFee = requireExactSora2TransferFee(
                    reviewedFee = reviewedFee,
                    exactFee = runtimeRpcClient.queryPartialFee(prepared.encoded),
                )
                validateBalances(exactFee)
            }
        }
        return awaitSubmittedTerminal(submitted)
    }

    fun setWatchingExtrinsicListener(listener: WatchingListener) {
        watchingListener = listener
    }

    private suspend fun requireSigningWalletAvailable(walletId: String) {
        WalletRecoveryCapabilityGate.requireUserMutationAllowed()
        // Shared rule: an account whose keys are unavailable is read-only for that account only.
        WalletSigningGate.unavailableReason(database, walletId)?.let { reason -> error(reason) }
    }

}
