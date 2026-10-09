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
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun NexusPortfolioCard(
    sora2: Sora2PortfolioBalance?,
    balances: List<NexusPortfolioBalance>,
    onSora2Receive: (String) -> Unit,
    onSora2Open: (String) -> Unit,
    isSora2WalletCurrent: (String) -> Boolean,
    onSend: (NexusPortfolioBalance) -> Unit,
    isNexusBalanceCurrent: (NexusPortfolioBalance) -> Boolean,
    onRecovery: () -> Unit,
) {
    val scopedBalances = balances.filter { it.walletId == sora2?.address }
    var selected by remember(sora2?.address) { mutableStateOf<NexusPortfolioBalance?>(null) }
    var receiving by remember(sora2?.address) { mutableStateOf(false) }
    LaunchedEffect(scopedBalances) {
        selected = selected?.let { previous ->
            scopedBalances.singleOrNull {
                it.walletId == previous.walletId && it.networkId == previous.networkId &&
                    it.address == previous.address
            }?.takeIf(isNexusBalanceCurrent)
        }
    }
    selected?.takeIf(isNexusBalanceCurrent)?.let { balance ->
        if (receiving) NexusReceiveSheet(balance, onClose = { receiving = false },
            isCurrent = { isNexusBalanceCurrent(balance) })
        else NexusActivitySheet(balance, onClose = { selected = null },
            onReceive = { receiving = true }, onSend = {
                if (isNexusBalanceCurrent(balance)) {
                    selected = null
                    onSend(balance)
                }
            }, onRecovery = { selected = null; onRecovery() })
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.networks_title), style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.customColors.fgPrimary)
        Text(stringResource(R.string.wallet_network_description),
            style = MaterialTheme.customTypography.paragraphS, color = MaterialTheme.customColors.fgPrimary)
        sora2?.let { balance ->
            NetworkSummary("SORA2", false, balance.xorQuantity,
                onOpen = { if (isSora2WalletCurrent(balance.address)) onSora2Open(balance.address) })
            TextButton(onClick = { if (isSora2WalletCurrent(balance.address)) onSora2Receive(balance.address) }) {
                Text(stringResource(R.string.common_receive))
            }
        }
        scopedBalances.forEach { balance ->
            NetworkSummary(balance.networkName, balance.isTestnet, balance.xorQuantity, onOpen = {
                if (isNexusBalanceCurrent(balance)) { receiving = false; selected = balance }
            })
            val pendingCount = balance.pendingTransactions.size + balance.recoveryPendingTransactions.size
            if (pendingCount > 0) Text("$pendingCount · ${stringResource(R.string.wallet_status_checking)}",
                style = MaterialTheme.customTypography.textS, color = MaterialTheme.customColors.fgPrimary)
        }
    }
}

@Composable
private fun NetworkSummary(name: String, testnet: Boolean, quantity: String?, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.wallet_network_activity),
        onClick = onOpen).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.customTypography.headline3, color = MaterialTheme.customColors.fgPrimary)
            Text(stringResource(if (testnet) R.string.network_badge_testnet else R.string.network_badge_mainnet),
                style = MaterialTheme.customTypography.textS, color = MaterialTheme.customColors.fgPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(quantity?.let { "$it XOR" } ?: stringResource(R.string.balance_unavailable),
                style = MaterialTheme.customTypography.headline3, color = MaterialTheme.customColors.fgPrimary)
            Text(stringResource(R.string.wallet_network_activity) + " ›",
                style = MaterialTheme.customTypography.textS, color = MaterialTheme.customColors.accentPrimary)
        }
    }
    Divider(color = MaterialTheme.customColors.fgOutline)
}

@Composable
private fun NexusReceiveSheet(balance: NexusPortfolioBalance, onClose: () -> Unit, isCurrent: () -> Boolean) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(balance.address) { mutableStateOf(false) }
    val qr = remember(balance.address) { QrCodeGenerator(Color.BLACK).generateQrBitmap(balance.address).asImageBitmap() }
    WalletSheet(title = stringResource(R.string.wallet_receive_xor), onClose = onClose,
        footer = {
            FilledButton(modifier = Modifier.fillMaxWidth(), text = stringResource(
                if (copied) R.string.wallet_copied else R.string.copy_receive_address),
                size = Size.Large, order = Order.PRIMARY, onClick = {
                    if (isCurrent()) { clipboard.setText(AnnotatedString(balance.address)); copied = true }
                })
        }) {
        NetworkLabel(balance)
        Text(stringResource(R.string.wallet_receive_notice, balance.networkName),
            style = MaterialTheme.customTypography.paragraphM)
        Image(qr, contentDescription = stringResource(R.string.wallet_receive_xor),
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f))
        SelectionContainer { Text(balance.address, style = MaterialTheme.customTypography.paragraphM) }
    }
}

@Composable
private fun NexusActivitySheet(balance: NexusPortfolioBalance, onClose: () -> Unit,
    onReceive: () -> Unit, onSend: () -> Unit, onRecovery: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    WalletSheet(title = stringResource(R.string.wallet_network_activity), onClose = onClose) {
        NetworkLabel(balance)
        Text(balance.xorQuantity?.let { "$it XOR" } ?: stringResource(R.string.balance_unavailable),
            style = MaterialTheme.customTypography.displayS)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledButton(modifier = Modifier.weight(1f), text = stringResource(R.string.common_send),
                size = Size.Large, order = Order.PRIMARY, enabled = balance.sendAvailable, onClick = onSend)
            FilledButton(modifier = Modifier.weight(1f), text = stringResource(R.string.common_receive),
                size = Size.Large, order = Order.SECONDARY, onClick = onReceive)
        }
        if (!balance.sendAvailable) WalletErrorMessage(balance.errorCode ?: "NEXUS_UNAVAILABLE", onRecovery = onRecovery)
        else balance.errorCode?.let { WalletErrorMessage(it, onRecovery = onRecovery) }
        balance.pendingTransactions.forEach { pending ->
            Text(walletStatusLabel(pending.state, pending.submissionIsAmbiguous),
                style = MaterialTheme.customTypography.headline3)
            Text("${pending.amount} XOR", style = MaterialTheme.customTypography.paragraphM)
            Text(stringResource(R.string.wallet_pending_notice), style = MaterialTheme.customTypography.paragraphS)
        }
        if (balance.recoveryPendingTransactions.isNotEmpty()) WalletErrorMessage("NEXUS_RECOVERY_REQUIRED", onRecovery = onRecovery)
        if (balance.confirmedTransfers.isEmpty() && balance.pendingTransactions.isEmpty() && balance.historyErrorCode == null) {
            Text(stringResource(R.string.wallet_no_activity), style = MaterialTheme.customTypography.paragraphM)
        }
        balance.confirmedTransfers.forEach { NexusTransferRow(it, balance.address) }
        balance.historyErrorCode?.let { WalletErrorMessage(it) }
        var details by remember(balance.address) { mutableStateOf(false) }
        TextButton(onClick = { details = !details }) { Text(stringResource(R.string.wallet_network_details)) }
        if (details) {
            SelectionContainer { Text(balance.address, style = MaterialTheme.customTypography.paragraphS) }
            TextButton(onClick = { uriHandler.openUri(balance.explorerBaseUrl) }) { Text(stringResource(R.string.wallet_open_block_explorer)) }
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
    Text("${network.networkName} · " + stringResource(
        if (network.isTestnet) R.string.network_badge_testnet else R.string.network_badge_mainnet),
        style = MaterialTheme.customTypography.headline3, color = MaterialTheme.customColors.fgPrimary)
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
