/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.WalletMigrationIds
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
}

class ExtrinsicSubmissionUnknownException(
    override val transactionHash: String,
    cause: Throwable,
) : IllegalStateException("EXTRINSIC_SUBMISSION_UNKNOWN", cause),
    ExtrinsicSubmissionUnknown

class ExtrinsicSubmissionUnknownCancellation(
    override val transactionHash: String,
    cause: CancellationException,
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
    private val calls: SubstrateCalls,
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
        return calls.getExtrinsicFee(extrinsic)
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
        return calls.getExtrinsicFee(extrinsic)
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
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus = submitAndWaitExtrinsicForPurpose(
        from = from,
        keypair = keypair,
        useBatchAll = useBatchAll,
        untilStatus = untilStatus,
        purpose = PreparedPurpose.GENERIC,
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
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus = submitAndWaitExtrinsicForPurpose(
        from = from,
        keypair = keypair,
        useBatchAll = useBatchAll,
        untilStatus = untilStatus,
        purpose = PreparedPurpose.LEGACY_MIGRATION,
        formExtrinsic = formExtrinsic,
    )

    private suspend fun submitAndWaitExtrinsicForPurpose(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean,
        untilStatus: String,
        purpose: PreparedPurpose,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus {
        check(untilStatus == SubstrateCalls.FINALIZED) {
            "SORA2_CANONICAL_FINALITY_REQUIRED"
        }
        val submitted = WalletMutationCoordinator.withLock {
            val prepared = prepareExtrinsicUnlocked(
                from = from,
                keypair = keypair,
                useBatchAll = useBatchAll,
                purpose = purpose,
                formExtrinsic = formExtrinsic,
            )
            submitGenericPreparedOnceUnlocked(prepared)
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
            Sora2LegacyMigrationAdmissionGate.requireNoUnresolvedWitness(
                walletId = from,
                unresolved = database.walletIdentityDao()
                    .getUnresolvedSora2PendingSubmissions(),
            )
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
        preTransportValidation: suspend () -> Unit = {},
    ): jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal {
        val staged = pendingSubmissionCoordinator.stageBeforeTransport(prepared)
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
            throw submissionUnknown(submitted.transactionHash, error)
        }
        notifyWatchingListenerBestEffort(result)
        return result
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
    ): Throwable {
        if (error is ExtrinsicSubmissionUnknown) return error
        recordErrorClassBestEffort(
            FirebaseWrapper.PrivacySafeErrorClass.EXTRINSIC_SUBMISSION
        )
        return if (error is CancellationException) {
            ExtrinsicSubmissionUnknownCancellation(transactionHash, error)
        } else {
            ExtrinsicSubmissionUnknownException(transactionHash, error)
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
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus {
        val submitted = WalletMutationCoordinator.withLock {
            val prepared = prepareExtrinsicUnlocked(
                from = from,
                keypair = keypair,
                useBatchAll = useBatchAll,
                formExtrinsic = formExtrinsic,
            )
            submitGenericPreparedOnceUnlocked(prepared)
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
        preSigningValidation: suspend () -> Unit,
        validateBalances: suspend (exactFee: BigInteger) -> Unit,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus {
        requireExactSora2TransferFee(reviewedFee, reviewedFee)
        val submitted = WalletMutationCoordinator.withLock {
            val prepared = prepareExtrinsicUnlocked(
                from = from,
                keypair = keypair,
                useBatchAll = useBatchAll,
                preSigningValidation = preSigningValidation,
                formExtrinsic = formExtrinsic,
            )
            submitGenericPreparedOnceUnlocked(prepared) {
                val exactFee = requireExactSora2TransferFee(
                    reviewedFee = reviewedFee,
                    exactFee = calls.getExtrinsicFee(prepared.encoded),
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
        val walletDao = database.walletIdentityDao()
        check(!walletDao.hasActiveDeletionOperation()) {
            "WALLET_DELETION_ACTIVE"
        }
        val migration = walletDao.getMigrationJournal(
            WalletMigrationIds.NETWORK_ACCOUNTS_V1
        )
        val sora2 = walletDao.getNetworkAccount(walletId, "sora2")
        check(
            database.accountDao().getAccount(walletId) != null &&
                walletDao.getWallet(walletId)?.migrationState == "VERIFIED" &&
                (migration == null || migration.state == "VERIFIED") &&
                sora2?.let { account ->
                    account.enabled && account.address == walletId
                } == true
        ) { "SIGNING_WALLET_UNAVAILABLE" }
    }

}
