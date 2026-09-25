package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.WalletIssue
import jp.co.soramitsu.common.presentation.WalletTransactionStatus
import jp.co.soramitsu.common.presentation.walletIssue
import jp.co.soramitsu.common.presentation.walletTransactionStatus
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun walletStatusLabel(state: String, ambiguous: Boolean = false): String = stringResource(
    when (walletTransactionStatus(state, ambiguous)) {
        WalletTransactionStatus.CONFIRMED -> R.string.wallet_status_confirmed
        WalletTransactionStatus.FAILED -> R.string.wallet_status_failed
        WalletTransactionStatus.PENDING -> R.string.wallet_status_pending
        WalletTransactionStatus.CHECKING -> R.string.wallet_status_checking
    }
)

@Composable
fun WalletErrorMessage(code: String, onRetry: (() -> Unit)? = null, onRecovery: (() -> Unit)? = null) {
    val issue = walletIssue(code)
    var details by remember(code) { mutableStateOf(false) }
    Column {
        Text(stringResource(when (issue) {
            WalletIssue.RECOVERY -> R.string.wallet_issue_recovery
            WalletIssue.ADDRESS -> R.string.wallet_issue_address
            WalletIssue.AMOUNT -> R.string.wallet_issue_amount
            WalletIssue.BALANCE -> R.string.wallet_issue_balance
            WalletIssue.QUOTE -> R.string.wallet_issue_quote
            WalletIssue.UNAVAILABLE -> R.string.wallet_issue_unavailable
            WalletIssue.CONNECTION -> R.string.wallet_issue_connection
        }), color = MaterialTheme.customColors.statusError, style = MaterialTheme.customTypography.paragraphS)
        if (issue == WalletIssue.RECOVERY && onRecovery != null) {
            TextButton(onClick = onRecovery) { Text(stringResource(R.string.wallet_backup_options)) }
        } else if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.wallet_check_again)) }
        }
        TextButton(onClick = { details = !details }) {
            Text(stringResource(if (details) R.string.wallet_hide_details else R.string.wallet_technical_details))
        }
        if (details) Text(code, color = MaterialTheme.customColors.fgSecondary,
            style = MaterialTheme.customTypography.textS)
    }
}
