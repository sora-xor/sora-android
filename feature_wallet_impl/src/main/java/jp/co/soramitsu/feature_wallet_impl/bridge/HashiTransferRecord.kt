package jp.co.soramitsu.feature_wallet_impl.bridge

import java.math.BigInteger
import kotlinx.serialization.Serializable

/** One bridge transfer as the wallet remembers it (spec §5), per SORA account. */
@Serializable
data class HashiTransferRecord(
    val id: String,
    /** The SORA account (SS58) the transfer belongs to. */
    val wallet: String,
    val direction: HashiDirection,
    val assetId: String,
    val symbol: String,
    val soraPrecision: Int,
    val ethDecimals: Int,
    /** `0x` token address (zero for Ether). */
    val ethToken: String,
    /** Amount in SORA units. */
    val soraAmount: String,
    /** Amount in Ethereum token units (expected for outgoing, deposited for incoming). */
    val ethAmount: String,
    /** Outgoing: the Ethereum recipient; incoming: the SORA account id (`0x` 32 bytes). */
    val recipient: String,
    /** The wallet's own Ethereum address that signs Ethereum steps. */
    val ethAccount: String,
    val state: HashiTransferState,
    val soraExtrinsicHash: String? = null,
    val soraBlockHash: String? = null,
    /** Outgoing: the SORA request hash from `RequestRegistered`. */
    val requestHash: String? = null,
    /** Incoming: the incoming transfer request hash (`loadToIncomingRequestHash`). */
    val incomingRequestHash: String? = null,
    /** The last request status read from SORA. */
    val requestStatus: String? = null,
    val ethSteps: List<HashiEthStep> = emptyList(),
    /** A chain error or refusal, as the chain named it. */
    val error: String? = null,
    /** Why the transfer or its last Ethereum step failed; [error] carries the chain's own words. */
    val failure: HashiFailure? = null,
    /**
     * The last refusal of a step that ran after the confirmation screen closed (a deposit's
     * approvals and deposit): a [HashiRefusal.Reason] name, and its detail in [error]. Nothing was
     * signed for it. Cleared by the next step.
     */
    val refusal: String? = null,
    /** Outgoing claim: SORA approved another amount than expected (both shown, nothing sent). */
    val approvedAmountMismatch: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val soraUnits: BigInteger get() = BigInteger(soraAmount)
    val ethUnits: BigInteger get() = BigInteger(ethAmount)

    fun step(intent: HashiEthIntent): HashiEthStep? = ethSteps.lastOrNull { it.intent == intent }

    fun withStep(step: HashiEthStep): HashiTransferRecord {
        val index = ethSteps.indexOfLast { it.intent == step.intent && it.nonce == step.nonce }
        val steps = if (index >= 0) ethSteps.toMutableList().also { it[index] = step } else ethSteps + step
        return copy(ethSteps = steps)
    }
}

@Serializable
enum class HashiDirection { SORA_TO_ETHEREUM, ETHEREUM_TO_SORA }

@Serializable
enum class HashiTransferState {
    // SORA → Ethereum
    /** Signed; the SORA extrinsic hash is saved before the single submission. */
    SORA_SIGNED,
    SORA_SUBMITTED,
    WAITING_APPROVALS,
    READY_TO_CLAIM,
    CLAIM_SUBMITTED,

    // Ethereum → SORA
    APPROVE_ZERO_SUBMITTED,
    APPROVE_SUBMITTED,
    /** An allowance step is confirmed; the deposit waits for the person's confirmation. */
    READY_TO_DEPOSIT,
    DEPOSIT_SUBMITTED,
    WAITING_SORA,

    // Both
    /** `Frozen` on SORA: not final, it can return to ApprovalsReady. */
    PAUSED_BY_BRIDGE,

    /** `Broken` on SORA: waits for the bridge operators; nothing is re-sent. */
    NEEDS_OPERATORS,

    /** An Ethereum step reverted (status 0) or was replaced; the person may try again. */
    ETHEREUM_STEP_FAILED,
    DONE,
    FAILED;

    val isFinal: Boolean get() = this == DONE || this == FAILED

    /** The SORA transfer is signed and its result is not known yet. */
    val isSoraUnresolved: Boolean get() = this == SORA_SIGNED || this == SORA_SUBMITTED
}

/** Why a transfer is [HashiTransferState.FAILED] or its Ethereum step failed. */
@Serializable
enum class HashiFailure {
    /** Never handed to the network: nothing left the wallet and no fee was paid. */
    NOT_SENT,

    /** Applied to SORA's current state before sending and refused there: nothing was sent. */
    REFUSED_BEFORE_SEND,

    /** Included in a finalized SORA block and failed there: only the fee was paid. */
    FAILED_ON_SORA,

    /** The bridge marked the request `Failed`. */
    FAILED_BY_BRIDGE,

    /** An Ethereum transaction of the wallet has status 0, or lacks the contract's event. */
    ETHEREUM_REVERTED,

    /** Another transaction used the nonce. */
    ETHEREUM_REPLACED,

    /**
     * Mined with status 1 but without the contract's event that proves it. It is never signed
     * again (that could send the funds twice); it needs a look by a person.
     */
    ETHEREUM_UNPROVEN,

    /** An Ethereum node refused the signed transaction: nothing is pending. */
    ETHEREUM_REFUSED,

    /** The allowance did not reach the amount after the approvals. */
    ALLOWANCE_NOT_SET,
}

@Serializable
enum class HashiEthIntent { APPROVE_ZERO, APPROVE, DEPOSIT, CLAIM }

/**
 * One Ethereum transaction intent at one nonce. Every signed attempt (the first and each speed-up
 * at the same nonce) is saved with its raw bytes before it is broadcast, so an unknown result is
 * resolved by sending the identical bytes again, never by signing anew.
 */
@Serializable
data class HashiEthStep(
    val intent: HashiEthIntent,
    val nonce: String,
    val to: String,
    val value: String,
    val data: String,
    val gasLimit: String,
    val attempts: List<HashiEthAttempt>,
    val state: HashiEthStepState,
    val confirmedHash: String? = null,
    val failure: String? = null,
) {
    val nonceValue: BigInteger get() = BigInteger(nonce)
    val latest: HashiEthAttempt get() = attempts.last()
    val hashes: List<String> get() = attempts.map { it.hash }
}

@Serializable
data class HashiEthAttempt(
    val raw: String,
    val hash: String,
    val maxFeePerGas: String,
    val maxPriorityFeePerGas: String,
    val signedAt: Long,
)

@Serializable
enum class HashiEthStepState {
    /** Saved; the broadcast result is not known yet. */
    SIGNED,

    /** A node took it. */
    BROADCAST,

    /** A receipt with status 1 and the contract's event. */
    CONFIRMED,

    /** A receipt with status 0. */
    REVERTED,

    /** The nonce was used by a transaction that is none of these attempts. */
    REPLACED;

    val isOpen: Boolean get() = this == SIGNED || this == BROADCAST
}
