package jp.co.soramitsu.common.presentation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class WalletIssue {
    RECOVERY, ADDRESS, AMOUNT, BALANCE, QUOTE, UNAVAILABLE, CONNECTION,
}

/** Stable categories keep transport and validation details out of the main UI. */
fun walletIssue(code: String): WalletIssue = when {
    "RECOVERY" in code || "AMBIGUOUS" in code || "ALREADY_SUBMITTED" in code || "HASH_MISMATCH" in code -> WalletIssue.RECOVERY
    "ADDRESS" in code || "RECIPIENT" in code -> WalletIssue.ADDRESS
    "INSUFFICIENT" in code || "BALANCE" in code -> WalletIssue.BALANCE
    "AMOUNT" in code || "QUANTITY" in code -> WalletIssue.AMOUNT
    "QUOTE" in code || "SLIPPAGE" in code || "STALE" in code -> WalletIssue.QUOTE
    "DISABLED" in code || "UNAVAILABLE" in code || "QUALIFICATION" in code -> WalletIssue.UNAVAILABLE
    else -> WalletIssue.CONNECTION
}

enum class WalletTransactionStatus { CONFIRMED, FAILED, PENDING, CHECKING }

fun walletTransactionStatus(state: String, ambiguous: Boolean = false): WalletTransactionStatus = when {
    ambiguous -> WalletTransactionStatus.CHECKING
    state.uppercase(Locale.ROOT) in setOf("COMMITTED", "FINALIZED", "CONFIRMED", "SUCCESS") ->
        WalletTransactionStatus.CONFIRMED
    state.uppercase(Locale.ROOT) in setOf("REJECTED", "FAILED", "EXPIRED") -> WalletTransactionStatus.FAILED
    state.uppercase(Locale.ROOT) in setOf("PREPARED", "STAGED", "SUBMITTED", "PENDING", "QUEUED", "SUBMITTING") ->
        WalletTransactionStatus.PENDING
    else -> WalletTransactionStatus.CHECKING
}

fun walletDateTime(
    timestampMillis: Long,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale).withZone(zone).format(Instant.ofEpochMilli(timestampMillis))

/** The description must agree with the completion status, including unknown outcomes. */
fun walletTransactionDescription(state: String, ambiguous: Boolean = false): Int = when (walletTransactionStatus(state, ambiguous)) {
    WalletTransactionStatus.CONFIRMED -> jp.co.soramitsu.common.R.string.wallet_transfer_complete
    WalletTransactionStatus.FAILED -> jp.co.soramitsu.common.R.string.wallet_submission_failed
    WalletTransactionStatus.PENDING -> jp.co.soramitsu.common.R.string.wallet_transfer_pending
    WalletTransactionStatus.CHECKING -> jp.co.soramitsu.common.R.string.wallet_submission_uncertain
}
