package jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.WalletErrorMessage
import jp.co.soramitsu.common.presentation.compose.components.walletStatusLabel
import jp.co.soramitsu.common.presentation.walletDateTime
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.TxHistoryListItem
import jp.co.soramitsu.feature_wallet_api.data.NetworkAccountActivity
import jp.co.soramitsu.ui_core.component.card.ContentCardEndless
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun AccountActivityScreen(
    modifier: Modifier,
    historyState: HistoryState,
    networkActivity: NetworkActivityState,
    onRefreshClick: () -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onMoreHistoryItemRequested: () -> Unit,
    onMoreNetworkHistoryRequested: (NetworkAccountActivity) -> Unit,
) {
    val entries = remember(historyState, networkActivity.accounts) {
        accountActivityEntries(historyState, networkActivity.accounts)
    }
    val soraHistory = historyState as? HistoryState.History
    val refreshing = networkActivity.loading || historyState == HistoryState.Loading || soraHistory?.pullToRefresh == true
    val pullRefresh = rememberPullRefreshState(refreshing, onRefreshClick)
    val soraError = historyState == HistoryState.Error || soraHistory?.hasErrorLoadingNew == true
    val hasMore = soraHistory?.endReached == false || networkActivity.accounts.any { it.hasMore }
    val hasError = soraError || networkActivity.error || networkActivity.accounts.any { it.errorCode != null }

    Box(modifier.pullRefresh(pullRefresh)) {
        ContentCardEndless(innerPadding = PaddingValues(top = Dimens.x2), modifier = Modifier.fillMaxSize()) {
            // Switching wallets resets scroll and any expanded transaction details.
            key(networkActivity.walletId) {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Dimens.x3, vertical = Dimens.x2),
                    verticalArrangement = Arrangement.spacedBy(Dimens.x2),
                ) {
                    items(entries, key = { it.key }) { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.x1)) {
                            when (entry) {
                                is AccountActivityEntry.Sora2 -> {
                                    ActivityCaption("SORA2 · ${walletDateTime(entry.timestamp)}")
                                    TxHistoryListItem(Modifier.fillMaxWidth(), onHistoryItemClick, entry.event)
                                }
                                is AccountActivityEntry.Transfer -> NetworkTransferItem(entry)
                                is AccountActivityEntry.Pending -> NetworkPendingItem(entry)
                            }
                            Divider(color = MaterialTheme.customColors.fgOutline)
                        }
                    }
                    if (entries.isEmpty() && !refreshing && !hasError && !hasMore) {
                        item("empty") {
                            Text(stringResource(R.string.activity_empty_content_title),
                                style = MaterialTheme.customTypography.paragraphM,
                                color = MaterialTheme.customColors.fgSecondary,
                                modifier = Modifier.padding(vertical = Dimens.x6))
                        }
                    }
                    if (soraError) {
                        item("sora2-error") {
                            ActivityCaption("SORA2")
                            Text(stringResource(R.string.activity_error_title),
                                style = MaterialTheme.customTypography.paragraphM,
                                color = MaterialTheme.customColors.fgSecondary)
                            RefreshButton(onRefreshClick)
                        }
                    }
                    if (soraHistory != null && !soraHistory.endReached && !soraHistory.hasErrorLoadingNew) {
                        item("sora2-more") {
                            TextButton(onClick = onMoreHistoryItemRequested, enabled = !refreshing) {
                                Text("SORA2 · ${stringResource(R.string.show_more)}")
                            }
                        }
                    }
                    networkActivity.accounts.forEach { account ->
                        if (account.errorCode != null || account.hasMore) {
                            item("${account.networkId}-status") {
                                ActivityCaption(networkLabel(account))
                                account.errorCode?.let { WalletErrorMessage(it, onRetry = onRefreshClick) }
                                if (account.hasMore) TextButton(
                                    onClick = { onMoreNetworkHistoryRequested(account) }, enabled = !networkActivity.loading,
                                ) { Text(stringResource(R.string.nexus_history_load_more)) }
                            }
                        }
                    }
                    if (networkActivity.error) {
                        item("network-error") { WalletErrorMessage("NEXUS_HISTORY_UNAVAILABLE", onRetry = onRefreshClick) }
                    }
                    if (refreshing && entries.isEmpty()) {
                        item("loading") {
                            Box(Modifier.fillMaxWidth().padding(Dimens.x6), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.customColors.accentPrimary)
                            }
                        }
                    }
                    if (entries.isEmpty() && !refreshing && !hasError) {
                        item("refresh") { RefreshButton(onRefreshClick) }
                    }
                }
            }
        }
        PullRefreshIndicator(refreshing, pullRefresh, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun NetworkTransferItem(entry: AccountActivityEntry.Transfer) {
    val transfer = entry.transfer
    val outgoing = transfer.sender == entry.account.address
    var expanded by remember(entry.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = Dimens.x1),
        verticalArrangement = Arrangement.spacedBy(Dimens.x1)) {
        ActivityCaption("${networkLabel(entry.account)} · ${walletDateTime(entry.timestamp)}")
        Text("${stringResource(if (outgoing) R.string.common_sent else R.string.common_received)} · ${transfer.amount} XOR",
            style = MaterialTheme.customTypography.headline3, color = MaterialTheme.customColors.fgPrimary)
        ActivityCaption(stringResource(R.string.wallet_status_confirmed))
        if (expanded) SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.x1)) {
                ActivityCaption(stringResource(R.string.network_history_counterparty,
                    if (outgoing) transfer.receiver else transfer.sender))
                ActivityCaption(stringResource(R.string.network_history_transaction_hash, transfer.transactionHash))
            }
        }
    }
}

@Composable
private fun NetworkPendingItem(entry: AccountActivityEntry.Pending) {
    val pending = entry.pending
    ActivityCaption(networkLabel(entry.account))
    Text("${pending.amount} XOR · ${walletStatusLabel(pending.state, pending.submissionIsAmbiguous)}",
        style = MaterialTheme.customTypography.headline3, color = MaterialTheme.customColors.fgPrimary)
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.x1)) {
            ActivityCaption(stringResource(R.string.network_history_counterparty, pending.recipient))
            pending.transactionHash?.let { ActivityCaption(stringResource(R.string.network_history_transaction_hash, it)) }
        }
    }
}

@Composable
private fun networkLabel(account: NetworkAccountActivity): String =
    "${account.networkName} · ${stringResource(if (account.isTestnet) R.string.network_badge_testnet else R.string.network_badge_mainnet)}"

@Composable
private fun ActivityCaption(text: String) {
    Text(text, style = MaterialTheme.customTypography.textS, color = MaterialTheme.customColors.fgSecondary)
}

@Composable
private fun RefreshButton(onRefresh: () -> Unit) {
    TextButton(onClick = onRefresh) { Text(stringResource(R.string.common_refresh)) }
}
