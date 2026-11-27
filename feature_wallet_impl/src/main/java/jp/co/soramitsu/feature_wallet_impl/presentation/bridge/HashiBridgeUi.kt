package jp.co.soramitsu.feature_wallet_impl.presentation.bridge

import java.math.BigDecimal
import java.math.BigInteger
import java.text.DateFormat
import java.util.Date
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.hashi.HashiAmounts
import jp.co.soramitsu.common.hashi.HashiAvailability
import jp.co.soramitsu.common.hashi.HashiBlock
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiDirection
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthIntent
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthStepState
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiFailure
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiRefusal
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiTransferRecord
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiTransferState

/** Whether new transfers can start, as the bridge screen shows it. */
enum class HashiAvailabilityUi {
    CHECKING,
    OPEN,

    /** Any block but a failed read: the paused banner. */
    PAUSED,

    /** The status could not be read: blocked, with a refresh. */
    UNREADABLE;

    companion object {
        fun of(availability: HashiAvailability): HashiAvailabilityUi = when {
            availability.newTransfersAllowed -> OPEN
            HashiBlock.READ_FAILED in availability.blocks -> UNREADABLE
            else -> PAUSED
        }
    }
}

data class HashiAssetItem(
    val token: Token,
    val balance: String,
)

data class HashiConfirmUi(
    /** What is being confirmed: the direction, or the step's name (receive, continue, speed up). */
    val title: String? = null,
    val amount: String,
    val receive: String,
    val from: String,
    val to: String,
    /** Each fee with its own name: the SORA network fee in XOR, Ethereum gas in ETH. */
    val fees: List<HashiFeeUi>,
    val warning: String? = null,
)

data class HashiFeeUi(val label: String, val value: String)

data class HashiHistoryItem(
    val id: String,
    val title: String,
    val amount: String,
    val status: String,
    val statusKind: HashiStatusKind,
    val date: String,
)

enum class HashiStatusKind { PROGRESS, ACTION, DONE, FAILED }

data class HashiBridgeUiState(
    val availability: HashiAvailabilityUi = HashiAvailabilityUi.CHECKING,
    val direction: HashiDirection = HashiDirection.SORA_TO_ETHEREUM,
    val assets: List<HashiAssetItem> = emptyList(),
    val pickerOpen: Boolean = false,
    val input: AssetAmountInputState? = null,
    val recipient: String = "",
    val ethereumAccount: String? = null,
    val noEthereumAccount: Boolean = false,
    val ethBalance: String = "",
    val notice: String? = null,
    val quoting: Boolean = false,
    val confirm: HashiConfirmUi? = null,
    val sending: Boolean = false,
    val history: List<HashiHistoryItem> = emptyList(),
) {
    val canContinue: Boolean
        get() = availability == HashiAvailabilityUi.OPEN && !noEthereumAccount && input != null &&
            (input.amount?.signum() ?: 0) > 0 && !quoting && !sending &&
            (direction == HashiDirection.ETHEREUM_TO_SORA || recipient.isNotBlank())
}

enum class HashiStepStatus { DONE, ACTIVE, WAITING, FAILED }

data class HashiStepUi(val label: String, val status: HashiStepStatus, val detail: String? = null)

enum class HashiTransferAction { CLAIM, CONTINUE_DEPOSIT, TRY_AGAIN }

data class HashiTransferUiState(
    val loaded: Boolean = false,
    val title: String = "",
    val amount: String = "",
    val recipient: String = "",
    val status: String = "",
    val statusKind: HashiStatusKind = HashiStatusKind.PROGRESS,
    val steps: List<HashiStepUi> = emptyList(),
    val error: String? = null,
    val soraHash: String? = null,
    val ethHash: String? = null,
    val action: HashiTransferAction? = null,
    val canSpeedUp: Boolean = false,
    /** Off only while this page itself is reading a quote or handing a confirmation over. */
    val speedUpEnabled: Boolean = false,
    val busy: Boolean = false,
    /** This page is reading a quote or handing a confirmation over: its sheet's button is off. */
    val quoting: Boolean = false,
    val confirm: HashiConfirmUi? = null,
)

/** Texts of the bridge screens. */
class HashiTexts(private val resources: ResourceManager, private val numbers: NumbersFormatter) {

    fun amount(units: BigInteger, decimals: Int, symbol: String): String =
        "${numbers.formatBigDecimal(HashiAmounts.fromUnits(units, decimals), decimals.coerceAtMost(8))} $symbol"

    fun amount(value: BigDecimal, symbol: String): String = "${numbers.formatBigDecimal(value, 8)} $symbol"

    fun eth(wei: BigInteger): String = amount(wei, 18, "ETH")

    fun string(id: Int): String = resources.getString(id)

    fun sorafee(units: BigInteger): HashiFeeUi = HashiFeeUi(resources.getString(R.string.hashi_bridge_fee_sora), amount(units, 18, "XOR"))

    fun gas(wei: BigInteger): HashiFeeUi = HashiFeeUi(resources.getString(R.string.hashi_bridge_fee_ethereum), eth(wei))

    fun claimEstimate(wei: BigInteger): HashiFeeUi = HashiFeeUi(resources.getString(R.string.hashi_bridge_claim_fee), eth(wei))

    fun refusal(error: HashiRefusal, decimals: Int): String = when (error.reason) {
        HashiRefusal.Reason.PAUSED -> resources.getString(R.string.hashi_bridge_paused)
        HashiRefusal.Reason.NO_ETHEREUM_ACCOUNT -> resources.getString(R.string.hashi_bridge_no_ethereum_account)
        HashiRefusal.Reason.NOT_BRIDGEABLE -> resources.getString(R.string.hashi_bridge_error_not_bridgeable)
        HashiRefusal.Reason.AMOUNT_INVALID -> resources.getString(R.string.hashi_bridge_error_amount)
        HashiRefusal.Reason.TOO_MANY_DECIMALS -> resources.getString(R.string.hashi_bridge_error_decimals).format(decimals)
        HashiRefusal.Reason.INSUFFICIENT_BALANCE -> resources.getString(R.string.common_insufficient_balance)
        HashiRefusal.Reason.INSUFFICIENT_FEE_BALANCE -> resources.getString(R.string.hashi_bridge_error_fee_balance)
        HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS -> resources.getString(R.string.hashi_bridge_error_eth_gas)
        HashiRefusal.Reason.RECIPIENT_INVALID -> resources.getString(R.string.hashi_bridge_error_recipient)
        HashiRefusal.Reason.RECIPIENT_HAS_CODE -> resources.getString(R.string.hashi_bridge_error_recipient_contract)
        HashiRefusal.Reason.NOT_ENOUGH_LOCKED_LIQUIDITY,
        HashiRefusal.Reason.BRIDGE_LACKS_TOKENS -> resources.getString(R.string.hashi_bridge_error_liquidity)
        HashiRefusal.Reason.TOKEN_NOT_ACCEPTED -> resources.getString(R.string.hashi_bridge_error_token)
        HashiRefusal.Reason.DRY_RUN_FAILED -> soraRefusal(error.detail)
        HashiRefusal.Reason.CALL_REVERTED -> resources.getString(R.string.hashi_bridge_error_reverted).format(error.detail.orEmpty())
        HashiRefusal.Reason.AMOUNT_MISMATCH -> {
            val (expected, approved) = error.detail.orEmpty().split('/').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            resources.getString(R.string.hashi_bridge_error_amount_mismatch).format(approved, expected)
        }
        HashiRefusal.Reason.NOT_READY -> resources.getString(R.string.hashi_bridge_state_waiting)
        HashiRefusal.Reason.ALREADY_RECEIVED -> resources.getString(R.string.hashi_bridge_state_already_received)
        HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING -> resources.getString(R.string.hashi_bridge_error_pending)
        HashiRefusal.Reason.COST_CHANGED -> resources.getString(R.string.hashi_bridge_error_cost_changed)
        HashiRefusal.Reason.READ_FAILED -> resources.getString(R.string.hashi_bridge_error_read)
    }

    /**
     * What SORA answered when the signed transfer was applied to its current state. The errors the
     * bridge is known to give are said in plain words; any other is shown by the name the runtime
     * gives it.
     */
    private fun soraRefusal(detail: String?): String {
        val name = detail.orEmpty()
        return when {
            name.endsWith("TransferLimitReached") -> resources.getString(R.string.hashi_bridge_error_limit)
            name.endsWith("NotEnoughLockedLiquidity") -> resources.getString(R.string.hashi_bridge_error_liquidity)
            name.endsWith("RecoveryHalted") || name == "EthBridge.Unavailable" -> resources.getString(R.string.hashi_bridge_paused)
            name.endsWith("NonZeroDust") || name.endsWith("InvalidAmount") -> resources.getString(R.string.hashi_bridge_error_amount)
            name == "Invalid.Payment" -> resources.getString(R.string.hashi_bridge_error_fee_balance)
            else -> resources.getString(R.string.hashi_bridge_error_rejected).format(name)
        }
    }

    /** Why a transfer failed, in the person's words; the chain's own name follows where it helps. */
    fun failure(record: HashiTransferRecord): String? = when (record.failure) {
        null -> null
        HashiFailure.NOT_SENT -> resources.getString(R.string.hashi_bridge_error_not_sent)
        HashiFailure.REFUSED_BEFORE_SEND -> soraRefusal(record.error)
        HashiFailure.FAILED_ON_SORA -> listOfNotNull(resources.getString(R.string.hashi_bridge_error_failed_on_sora), record.error)
            .joinToString("\n")
        // The bridge's own error follows (spec: show it; the SORA balance shows what came back).
        HashiFailure.FAILED_BY_BRIDGE -> listOfNotNull(resources.getString(R.string.hashi_bridge_error_failed_by_bridge), record.error)
            .joinToString("\n")
        HashiFailure.ETHEREUM_REVERTED -> resources.getString(R.string.hashi_bridge_error_eth_failed)
        HashiFailure.ETHEREUM_REPLACED -> resources.getString(R.string.hashi_bridge_error_eth_replaced)
        HashiFailure.ETHEREUM_UNPROVEN -> resources.getString(R.string.hashi_bridge_error_eth_unproven)
        HashiFailure.DEPOSIT_UNCONFIRMED -> resources.getString(R.string.hashi_bridge_error_deposit_unconfirmed)
        HashiFailure.ETHEREUM_REFUSED -> resources.getString(R.string.hashi_bridge_error_reverted).format(record.error.orEmpty())
        HashiFailure.ALLOWANCE_NOT_SET -> resources.getString(R.string.hashi_bridge_error_allowance)
    }

    /** The refusal of a step that ran after the confirmation closed (nothing was signed for it). */
    private fun savedRefusal(record: HashiTransferRecord): String? {
        val name = record.refusal ?: return null
        val reason = HashiRefusal.Reason.values().firstOrNull { it.name == name } ?: HashiRefusal.Reason.READ_FAILED
        return refusal(HashiRefusal(reason, record.error), record.ethDecimals)
    }

    fun title(record: HashiTransferRecord): String = resources.getString(
        if (record.direction == HashiDirection.SORA_TO_ETHEREUM) R.string.hashi_bridge_to_ethereum else R.string.hashi_bridge_to_sora
    )

    /** One status for every state a transfer can be in, for its page and its history row. */
    fun status(record: HashiTransferRecord): Pair<String, HashiStatusKind> = when (record.state) {
        HashiTransferState.SORA_SIGNED,
        HashiTransferState.SORA_SUBMITTED -> resources.getString(R.string.hashi_bridge_state_sending_sora) to HashiStatusKind.PROGRESS
        HashiTransferState.WAITING_APPROVALS,
        HashiTransferState.WAITING_SORA -> resources.getString(R.string.hashi_bridge_state_waiting) to HashiStatusKind.PROGRESS
        HashiTransferState.READY_TO_CLAIM -> resources.getString(R.string.hashi_bridge_state_ready_to_claim) to HashiStatusKind.ACTION
        HashiTransferState.CLAIM_SUBMITTED -> resources.getString(R.string.hashi_bridge_state_receiving) to HashiStatusKind.PROGRESS
        HashiTransferState.APPROVE_ZERO_SUBMITTED,
        HashiTransferState.APPROVE_SUBMITTED -> resources.getString(R.string.hashi_bridge_state_approving) to HashiStatusKind.PROGRESS
        HashiTransferState.READY_TO_DEPOSIT -> resources.getString(R.string.hashi_bridge_continue_deposit) to HashiStatusKind.ACTION
        HashiTransferState.DEPOSIT_SUBMITTED -> resources.getString(R.string.hashi_bridge_state_depositing) to HashiStatusKind.PROGRESS
        HashiTransferState.PAUSED_BY_BRIDGE -> resources.getString(R.string.hashi_bridge_state_paused) to HashiStatusKind.PROGRESS
        HashiTransferState.NEEDS_OPERATORS -> resources.getString(R.string.hashi_bridge_state_needs_operators) to HashiStatusKind.PROGRESS
        HashiTransferState.ETHEREUM_STEP_FAILED -> resources.getString(R.string.hashi_bridge_state_eth_failed) to HashiStatusKind.ACTION
        HashiTransferState.DONE -> resources.getString(
            if (record.error == "ALREADY_RECEIVED") R.string.hashi_bridge_state_already_received else R.string.common_done
        ) to HashiStatusKind.DONE
        HashiTransferState.FAILED -> resources.getString(R.string.common_failed) to HashiStatusKind.FAILED
    }

    fun historyItem(record: HashiTransferRecord): HashiHistoryItem {
        val (status, kind) = status(record)
        return HashiHistoryItem(
            id = record.id,
            title = title(record),
            amount = amount(record.soraUnits, record.soraPrecision, record.symbol),
            status = status,
            statusKind = kind,
            date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.createdAt)),
        )
    }

    /** The steps of a transfer, in order, with where it stands. */
    fun steps(record: HashiTransferRecord): List<HashiStepUi> {
        val state = record.state
        fun status(done: Boolean, active: Boolean, failed: Boolean = false) = when {
            failed -> HashiStepStatus.FAILED
            done -> HashiStepStatus.DONE
            active -> HashiStepStatus.ACTIVE
            else -> HashiStepStatus.WAITING
        }
        return if (record.direction == HashiDirection.SORA_TO_ETHEREUM) {
            val soraDone = record.requestHash != null
            val approved = state in setOf(
                HashiTransferState.READY_TO_CLAIM, HashiTransferState.CLAIM_SUBMITTED,
                HashiTransferState.ETHEREUM_STEP_FAILED, HashiTransferState.DONE,
            )
            val failed = state == HashiTransferState.FAILED
            listOf(
                HashiStepUi(
                    resources.getString(R.string.hashi_bridge_step_sora),
                    status(soraDone, !soraDone && !failed, failed && !soraDone),
                ),
                HashiStepUi(
                    resources.getString(R.string.hashi_bridge_step_approvals),
                    status(approved, soraDone && !approved && !failed, failed && soraDone),
                    detail = when (state) {
                        HashiTransferState.PAUSED_BY_BRIDGE -> resources.getString(R.string.hashi_bridge_state_paused)
                        HashiTransferState.NEEDS_OPERATORS -> resources.getString(R.string.hashi_bridge_state_needs_operators)
                        else -> null
                    },
                ),
                HashiStepUi(
                    resources.getString(R.string.hashi_bridge_step_claim),
                    status(
                        state == HashiTransferState.DONE,
                        state == HashiTransferState.READY_TO_CLAIM || state == HashiTransferState.CLAIM_SUBMITTED,
                        state == HashiTransferState.ETHEREUM_STEP_FAILED,
                    ),
                ),
            )
        } else {
            val approvals = record.ethSteps.filter { it.intent == HashiEthIntent.APPROVE || it.intent == HashiEthIntent.APPROVE_ZERO }
            val deposit = record.step(HashiEthIntent.DEPOSIT)
            val depositDone = deposit?.state == HashiEthStepState.CONFIRMED
            val stepFailed = state == HashiTransferState.ETHEREUM_STEP_FAILED
            buildList {
                if (approvals.isNotEmpty()) {
                    val approvalDone = approvals.last().state == HashiEthStepState.CONFIRMED && approvals.last().intent == HashiEthIntent.APPROVE
                    add(
                        HashiStepUi(
                            resources.getString(R.string.hashi_bridge_step_allowance),
                            status(approvalDone || deposit != null, !approvalDone, stepFailed && deposit == null),
                        )
                    )
                }
                add(
                    HashiStepUi(
                        resources.getString(R.string.hashi_bridge_step_deposit),
                        status(depositDone, deposit != null && !depositDone, stepFailed && deposit != null),
                    )
                )
                add(
                    HashiStepUi(
                        resources.getString(R.string.hashi_bridge_step_sora_credit),
                        status(state == HashiTransferState.DONE, depositDone && state != HashiTransferState.DONE, state == HashiTransferState.FAILED),
                        detail = when (state) {
                            HashiTransferState.PAUSED_BY_BRIDGE -> resources.getString(R.string.hashi_bridge_state_paused)
                            HashiTransferState.NEEDS_OPERATORS -> resources.getString(R.string.hashi_bridge_state_needs_operators)
                            else -> null
                        },
                    )
                )
            }
        }
    }

    /**
     * The page of one transfer. [working] is true while a confirmed step of it is being signed,
     * sent or waited for: its buttons are off until that ends.
     */
    fun transferState(record: HashiTransferRecord, busy: Boolean, confirm: HashiConfirmUi?, working: Boolean = false): HashiTransferUiState {
        val (status, kind) = status(record)
        val openStep = record.ethSteps.lastOrNull { it.state.isOpen }
        val lastEthHash = record.ethSteps.lastOrNull()?.let { it.confirmedHash ?: it.latest.hash }
        val failed = record.state == HashiTransferState.FAILED || record.state == HashiTransferState.ETHEREUM_STEP_FAILED
        return HashiTransferUiState(
            loaded = true,
            title = title(record),
            amount = amount(record.soraUnits, record.soraPrecision, record.symbol),
            recipient = record.recipient,
            status = status,
            statusKind = kind,
            steps = steps(record),
            error = record.approvedAmountMismatch?.let { mismatch ->
                val (expected, approved) = mismatch.split('/')
                resources.getString(R.string.hashi_bridge_error_amount_mismatch).format(
                    amount(BigInteger(approved), record.ethDecimals, record.symbol),
                    amount(BigInteger(expected), record.ethDecimals, record.symbol),
                )
            } ?: (if (failed) failure(record) ?: record.error else null)
                // Broken: both errors the bridge recorded, kept as it gave them.
                ?: record.error?.takeIf { record.state == HashiTransferState.NEEDS_OPERATORS }
                ?: savedRefusal(record),
            soraHash = record.soraExtrinsicHash,
            ethHash = lastEthHash,
            action = when (record.state) {
                HashiTransferState.READY_TO_CLAIM -> HashiTransferAction.CLAIM
                HashiTransferState.READY_TO_DEPOSIT -> HashiTransferAction.CONTINUE_DEPOSIT
                // A transaction mined without its proof is never offered again.
                HashiTransferState.ETHEREUM_STEP_FAILED -> HashiTransferAction.TRY_AGAIN.takeUnless {
                    record.failure == HashiFailure.ETHEREUM_UNPROVEN || record.failure == HashiFailure.DEPOSIT_UNCONFIRMED
                }
                else -> null
            }.takeUnless { working },
            canSpeedUp = openStep != null,
            // A pending transaction can be made faster while the service waits for it: the service
            // decides under its signing lock, against the saved transfer.
            speedUpEnabled = openStep != null && !busy,
            busy = busy || working,
            quoting = busy,
            confirm = confirm,
        )
    }
}
