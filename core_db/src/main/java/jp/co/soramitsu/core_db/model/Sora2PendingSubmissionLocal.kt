package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Versioned, non-secret recovery witness for one exact ordinary SORA2 extrinsic.
 *
 * Signed bytes and secret material are deliberately absent. The witness contains only the
 * wallet/account, chain/runtime, deterministic hash, and mortal-era identity required for a
 * status-only restart scan. It is separate from the Nexus/Polkamarkt journal because overloading
 * those asset/recipient columns would make wallet-deletion authority ambiguous.
 */
@Entity(
    tableName = "sora2PendingSubmissions",
    foreignKeys = [
        ForeignKey(
            entity = WalletIdentityLocal::class,
            parentColumns = ["walletId"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index("walletId"),
        Index(value = ["networkId", "transactionHash"], unique = true),
        Index("state"),
    ],
)
data class Sora2PendingSubmissionLocal(
    @PrimaryKey val localId: String,
    val recoverySchemaVersion: Int,
    val walletId: String,
    val networkId: String,
    val transactionHash: String,
    val accountId: String,
    val publicKey: String,
    val genesisHash: String,
    val specVersion: Int,
    val transactionVersion: Int,
    val metadataSha256: String,
    val typesSha256: String,
    val eraBirthBlock: Long,
    val eraDeathBlockExclusive: Long,
    val eraPeriod: Int,
    val eraPhase: Int,
    val eraBirthBlockHash: String,
    val operationKind: String,
    val state: String,
    val submissionIsAmbiguous: Boolean,
    val terminalBlockNumber: Long?,
    val terminalBlockHash: String?,
    val terminalFinalizedHeight: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isAuthoritativelyTerminal: Boolean
        get() = state in TERMINAL_STATES

    companion object {
        const val RECOVERY_SCHEMA_VERSION = 1
        const val NETWORK_SORA2 = "sora2"
        const val OPERATION_GENERIC = "GENERIC_SORA2_MUTATION"
        const val OPERATION_POLKAMARKT = "POLKAMARKT_SORA2_MUTATION"
        val OPERATION_KINDS = setOf(OPERATION_GENERIC, OPERATION_POLKAMARKT)

        const val STATE_SIGNED_BEFORE_TRANSPORT = "SIGNED_BEFORE_TRANSPORT"
        const val STATE_SUBMISSION_UNKNOWN = "SUBMISSION_UNKNOWN"
        const val STATE_SUBMITTED_AWAITING_FINALITY = "SUBMITTED_AWAITING_FINALITY"
        const val STATE_FINALIZED_SUCCESS = "FINALIZED_SUCCESS"
        const val STATE_FINALIZED_FAILURE = "FINALIZED_FAILURE"
        const val STATE_EXPIRED_NOT_INCLUDED = "EXPIRED_NOT_INCLUDED"

        val TERMINAL_STATES = setOf(
            STATE_FINALIZED_SUCCESS,
            STATE_FINALIZED_FAILURE,
            STATE_EXPIRED_NOT_INCLUDED,
        )
        val UNRESOLVED_STATES = setOf(
            STATE_SIGNED_BEFORE_TRANSPORT,
            STATE_SUBMISSION_UNKNOWN,
            STATE_SUBMITTED_AWAITING_FINALITY,
        )
    }
}
