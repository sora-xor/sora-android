package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.presentation.WalletTransactionStatus
import jp.co.soramitsu.common.presentation.walletDateTime
import jp.co.soramitsu.common.presentation.walletTransactionStatus
import jp.co.soramitsu.common.presentation.compose.components.WalletErrorMessage
import jp.co.soramitsu.common.presentation.compose.components.WalletSheet
import jp.co.soramitsu.common.presentation.compose.components.walletStatusLabel
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.common.presentation.compose.neon.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * Which of a network's sheets is open: its XOR page (balance, Send, Receive, history) or its
 * receive address. Remembered per wallet, so another wallet never inherits an open sheet.
 */
@Stable
internal class NetworkSheetsState {
    var selected by mutableStateOf<NexusPortfolioBalance?>(null)
        private set
    var receiving by mutableStateOf(false)
        private set
    private var receiveReturnsToActivity = false

    fun openActivity(balance: NexusPortfolioBalance) {
        receiving = false
        selected = balance
    }

    /** Receive opened from the wallet's Receive: closing it closes the sheet. */
    fun openReceive(balance: NexusPortfolioBalance) {
        receiveReturnsToActivity = false
        receiving = true
        selected = balance
    }

    fun receiveFromActivity() {
        receiveReturnsToActivity = true
        receiving = true
    }

    fun closeReceive() {
        receiving = false
        if (!receiveReturnsToActivity) selected = null
    }

    fun close() {
        receiving = false
        selected = null
    }

    fun replace(balance: NexusPortfolioBalance?) {
        if (balance == null) close() else selected = balance
    }
}

@Composable
internal fun rememberNetworkSheetsState(walletId: String?): NetworkSheetsState =
    remember(walletId) { NetworkSheetsState() }

/** The open network sheet, if any. Rows that stop being current close it. */
@Composable
internal fun NetworkSheets(
    state: NetworkSheetsState,
    balances: List<NexusPortfolioBalance>,
    isNexusBalanceCurrent: (NexusPortfolioBalance) -> Boolean,
    onSend: (NexusPortfolioBalance) -> Unit,
    onRecovery: () -> Unit,
    onRefresh: () -> Unit = {},
    onLoadMore: (NexusPortfolioBalance) -> Unit = {},
) {
    LaunchedEffect(balances) {
        state.selected?.let { previous ->
            state.replace(balances.singleOrNull {
                it.walletId == previous.walletId && it.networkId == previous.networkId &&
                    it.address == previous.address
            }?.takeIf(isNexusBalanceCurrent))
        }
    }
    val balance = state.selected?.takeIf(isNexusBalanceCurrent) ?: return
    if (state.receiving) NexusReceiveSheet(balance, onClose = state::closeReceive,
        isCurrent = { isNexusBalanceCurrent(balance) })
    else NexusActivitySheet(balance, onClose = state::close,
        onReceive = state::receiveFromActivity, onSend = {
            if (isNexusBalanceCurrent(balance)) {
                state.close()
                onSend(balance)
            }
        }, onRecovery = { state.close(); onRecovery() },
        onRefresh = onRefresh, onLoadMore = { onLoadMore(balance) })
}

@Composable
private fun NexusReceiveSheet(balance: NexusPortfolioBalance, onClose: () -> Unit, isCurrent: () -> Boolean) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(balance.address) { mutableStateOf(false) }
    val qr = remember(balance.address) { QrCodeGenerator(Color.BLACK).generateQrBitmap(balance.address).asImageBitmap() }
    val title = stringResource(if (balance.networkLive) R.string.wallet_receive_xor else R.string.nexus_production_address)
    WalletSheet(title = title, onClose = onClose,
        footer = {
            FilledButton(modifier = Modifier.fillMaxWidth(), text = stringResource(
                if (copied) R.string.wallet_copied else if (balance.networkLive) R.string.copy_receive_address
                else R.string.nexus_copy_production_address),
                size = Size.Large, order = Order.PRIMARY, onClick = {
                    if (isCurrent()) { clipboard.setText(AnnotatedString(balance.address)); copied = true }
                })
        }) {
        NetworkLabel(balance)
        Text(if (balance.networkLive) stringResource(R.string.wallet_receive_notice, balance.networkName)
            else stringResource(R.string.nexus_production_address_notice),
            style = MaterialTheme.customTypography.paragraphM)
        Image(qr, contentDescription = title,
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f))
        SelectionContainer { Text(balance.address, style = MaterialTheme.customTypography.paragraphM) }
    }
}

@Composable
private fun NexusActivitySheet(balance: NexusPortfolioBalance, onClose: () -> Unit,
    onReceive: () -> Unit, onSend: () -> Unit, onRecovery: () -> Unit,
    onRefresh: () -> Unit, onLoadMore: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    WalletSheet(title = stringResource(if (balance.networkLive) R.string.wallet_network_activity
        else R.string.nexus_production_address), onClose = onClose) {
        NetworkLabel(balance)
        Text(if (!balance.networkLive) stringResource(R.string.nexus_production_not_live)
            else balance.xorQuantity?.let { "$it XOR" } ?: stringResource(R.string.balance_unavailable),
            style = MaterialTheme.customTypography.displayS)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledButton(modifier = Modifier.weight(1f), text = stringResource(R.string.common_send),
                size = Size.Large, order = Order.PRIMARY, enabled = balance.sendAvailable, onClick = onSend)
            FilledButton(modifier = Modifier.weight(1f), text = stringResource(
                if (balance.networkLive) R.string.common_receive else R.string.nexus_production_address),
                size = Size.Large, order = Order.SECONDARY, onClick = onReceive)
        }
        if (!balance.networkLive) {
            Text(stringResource(R.string.nexus_production_address_notice), style = MaterialTheme.customTypography.paragraphM)
        } else {
            if (!balance.sendAvailable && balance.errorCode == null) {
                Text(stringResource(R.string.nexus_send_not_available), style = MaterialTheme.customTypography.paragraphM)
            }
            balance.errorCode?.let { WalletErrorMessage(it, onRetry = onRefresh, onRecovery = onRecovery) }
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.common_refresh)) }
        }
        balance.pendingTransactions.forEach { pending ->
            Text(walletStatusLabel(pending.state, pending.submissionIsAmbiguous),
                style = MaterialTheme.customTypography.headline3)
            Text("${pending.amount} XOR", style = MaterialTheme.customTypography.paragraphM)
            Text(stringResource(R.string.wallet_pending_notice), style = MaterialTheme.customTypography.paragraphS)
        }
        if (balance.recoveryPendingTransactions.isNotEmpty()) WalletErrorMessage("NEXUS_RECOVERY_REQUIRED", onRecovery = onRecovery)
        if (balance.networkLive && balance.confirmedTransfers.isEmpty() && balance.pendingTransactions.isEmpty() && balance.historyErrorCode == null && !balance.historyHasMore) {
            Text(stringResource(R.string.wallet_no_activity), style = MaterialTheme.customTypography.paragraphM)
        }
        balance.confirmedTransfers.forEach { NexusTransferRow(it, balance.address) }
        balance.historyErrorCode?.let { WalletErrorMessage(it, onRetry = onRefresh) }
        if (balance.historyHasMore) TextButton(onClick = onLoadMore) {
            Text(stringResource(R.string.nexus_history_load_more))
        }
        var details by remember(balance.address) { mutableStateOf(false) }
        TextButton(onClick = { details = !details }) { Text(stringResource(R.string.wallet_network_details)) }
        if (details) {
            SelectionContainer { Text(balance.address, style = MaterialTheme.customTypography.paragraphS) }
            if (balance.networkLive) TextButton(onClick = { uriHandler.openUri(balance.explorerBaseUrl) }) { Text(stringResource(R.string.wallet_open_block_explorer)) }
        }
    }
}

@Composable
private fun NexusTransferRow(transfer: NexusTransferHistoryItem, address: String) {
    var expanded by remember(transfer.transactionHash) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = { expanded = !expanded }).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(if (transfer.sender == address) R.string.common_sent else R.string.common_received) + " · ${transfer.amount} XOR",
            style = MaterialTheme.customTypography.headline3)
        Text(walletDateTime(transfer.timestampMillis), style = MaterialTheme.customTypography.textS,
            color = MaterialTheme.customColors.fgPrimary)
        if (expanded) SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.network_history_counterparty,
                    if (transfer.sender == address) transfer.receiver else transfer.sender))
                Text(stringResource(R.string.network_history_transaction_hash, transfer.transactionHash))
            }
        }
    }
    Divider(color = MaterialTheme.customColors.fgOutline)
}

@Composable
private fun NetworkLabel(network: NexusPortfolioBalance) {
    Text(networkLabel(network.networkId), style = MaterialTheme.customTypography.headline3,
        color = MaterialTheme.customColors.fgPrimary)
}

@Composable
internal fun NexusSendDialog(state: NexusSendUiState, onDismiss: () -> Unit,
    onRecipient: (String) -> Unit, onAmount: (String) -> Unit, onPrepare: () -> Unit,
    onConfirm: () -> Unit, onEdit: () -> Unit, onRecovery: () -> Unit, onScan: () -> Unit = {}) {
    if (!state.visible) return
    val network = state.network ?: return
    val clipboard = LocalClipboardManager.current
    val busy = state.preparing || state.submitting
    val prepared = state.prepared
    val needsStatusCheck = state.submissionNeedsCheck || state.errorCode?.let {
        jp.co.soramitsu.common.presentation.walletIssue(it) == jp.co.soramitsu.common.presentation.WalletIssue.RECOVERY
    } == true
    WalletSheet(title = stringResource(if (prepared == null) R.string.wallet_send_xor else R.string.wallet_review_transfer),
        onClose = onDismiss, dismissEnabled = !busy,
        footer = {
            if (state.result == null && !needsStatusCheck) {
                FilledButton(modifier = Modifier.fillMaxWidth(),
                    text = stringResource(if (prepared == null) R.string.review else R.string.wallet_confirm_send),
                    size = Size.Large, order = Order.PRIMARY,
                    enabled = !busy && state.recipient.isNotBlank() && state.amount.isNotBlank(),
                    onClick = if (prepared == null) onPrepare else onConfirm)
                if (prepared != null) TextButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wallet_edit_transfer))
                }
            } else TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wallet_back_to_wallet)) }
        }) {
        NetworkLabel(network)
        if (state.result == null && !needsStatusCheck) {
            if (prepared == null) {
                Text(network.xorQuantity?.let { stringResource(R.string.wallet_available_xor, it) }
                    ?: stringResource(R.string.balance_unavailable), style = MaterialTheme.customTypography.headline3)
                OutlinedTextField(value = state.recipient, onValueChange = onRecipient,
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.recipient_address)) }, maxLines = 4)
                TextButton(onClick = { clipboard.getText()?.text?.let(onRecipient) }, enabled = !busy) {
                    Text(stringResource(R.string.wallet_paste_address))
                }
                TextButton(onClick = onScan, enabled = !busy) {
                    Text(stringResource(R.string.wallet_scan_recipient))
                }
                if (state.scannerPermissionDenied) Text(stringResource(R.string.wallet_scan_unavailable),
                    style = MaterialTheme.customTypography.paragraphS)
                Text(stringResource(R.string.wallet_send_network_notice, network.networkName),
                    style = MaterialTheme.customTypography.paragraphS)
                OutlinedTextField(value = state.amount, onValueChange = onAmount,
                    enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.wallet_xor_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            } else {
                Text(stringResource(R.string.wallet_review_network), style = MaterialTheme.customTypography.paragraphM)
                Text(stringResource(R.string.recipient_address), style = MaterialTheme.customTypography.headline3)
                SelectionContainer { Text(prepared.canonicalRecipient, style = MaterialTheme.customTypography.paragraphM) }
                Text("${prepared.canonicalAmount} XOR", style = MaterialTheme.customTypography.displayS)
                Text(stringResource(R.string.network_fee) + " · ${prepared.fee} XOR")
                Text(stringResource(R.string.wallet_total_xor,
                    (prepared.canonicalAmount.toBigDecimal() + prepared.fee.toBigDecimal()).stripTrailingZeros().toPlainString()),
                    style = MaterialTheme.customTypography.headline3)
                Text(stringResource(R.string.wallet_available_xor, prepared.availableBalance))
            }
        }
        if (needsStatusCheck) Text(stringResource(R.string.wallet_submission_uncertain), style = MaterialTheme.customTypography.paragraphM)
        state.errorCode?.let { WalletErrorMessage(if (needsStatusCheck) "NEXUS_RECOVERY_REQUIRED" else it, onRetry = if (!busy && !needsStatusCheck) {
            if (prepared == null) onPrepare else onEdit
        } else null, onRecovery = { onDismiss(); onRecovery() }) }
        state.result?.let {
            Text(walletStatusLabel(it.state), style = MaterialTheme.customTypography.headline2)
            Text(stringResource(jp.co.soramitsu.common.presentation.walletTransactionDescription(it.state)))
            SelectionContainer { Text(stringResource(R.string.network_history_transaction_hash, it.transactionHash)) }
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.customColors.accentPrimary)
    }
}
