package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.nexus.WalletNetworkAction
import jp.co.soramitsu.common.nexus.WalletNetworkChoice
import jp.co.soramitsu.common.nexus.WalletNetworkDirectory
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.nexus.WalletNetworkTag
import jp.co.soramitsu.common.nexus.WalletNetworkUnavailable
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.common.presentation.compose.components.WalletSheet
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@get:StringRes
private val WalletNetworkTag.label: Int
    get() = when (this) {
        WalletNetworkTag.MAIN -> R.string.network_tag_main
        WalletNetworkTag.COMING_SOON -> R.string.network_tag_coming_soon
        WalletNetworkTag.TESTNET -> R.string.network_tag_testnet
    }

/** "Taira · Testnet", "SORA Nexus · Coming soon": a network's name and tag, from the directory. */
@Composable
internal fun networkLabel(id: WalletNetworkId): String {
    val directory = WalletNetworkDirectory.current
    val main = directory.mainNetwork(setOf(WalletNetworkId.SORA2, id))
    val name = directory.info(id).name
    return directory.tag(id, main)?.let { "$name · ${stringResource(it.label)}" } ?: name
}

/** "Main", "Testnet" or "Coming soon" next to a network's name. */
@Composable
internal fun NetworkTagChip(tag: WalletNetworkTag, modifier: Modifier = Modifier) {
    val color = when (tag) {
        WalletNetworkTag.MAIN -> MaterialTheme.customColors.accentPrimary
        WalletNetworkTag.TESTNET -> MaterialTheme.customColors.statusWarning
        WalletNetworkTag.COMING_SOON -> MaterialTheme.customColors.fgSecondary
    }
    Text(
        text = stringResource(tag.label),
        style = MaterialTheme.customTypography.textXSBold,
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The one question the wallet asks about networks: which network to receive or send on. The
 * main network is first; each row shows its tag, XOR balance and address.
 */
@Composable
internal fun NetworkPickerSheet(
    action: WalletNetworkAction,
    choices: List<WalletNetworkChoice>,
    onPick: (WalletNetworkChoice) -> Unit,
    onClose: () -> Unit,
) {
    WalletSheet(
        title = stringResource(
            if (action == WalletNetworkAction.RECEIVE) R.string.wallet_receive_on else R.string.wallet_send_on
        ),
        onClose = onClose,
    ) {
        Column(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, choice ->
                NetworkChoiceRow(choice, onClick = { onPick(choice) })
                if (index < choices.lastIndex) Divider(color = MaterialTheme.customColors.fgOutline)
            }
        }
        Text(
            text = stringResource(
                if (action == WalletNetworkAction.RECEIVE) R.string.wallet_receive_on_notice
                else R.string.wallet_send_on_notice
            ),
            style = MaterialTheme.customTypography.paragraphS,
            color = MaterialTheme.customColors.fgSecondary,
        )
    }
}

@Composable
private fun NetworkChoiceRow(choice: WalletNetworkChoice, onClick: () -> Unit) {
    val name = choice.network.name
    val amount = if (!choice.network.isLive) stringResource(R.string.network_address_ready)
    else choice.balance?.let { "$it XOR" }
    val reason = when (choice.unavailable) {
        WalletNetworkUnavailable.NOT_LIVE -> stringResource(R.string.wallet_send_not_live, name)
        WalletNetworkUnavailable.PAUSED -> stringResource(R.string.wallet_send_paused, name)
        null -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = choice.isAvailable, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp)
            .testTag("NetworkChoice${choice.id.wireId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val content = if (choice.isAvailable) MaterialTheme.customColors.fgPrimary
            else MaterialTheme.customColors.fgSecondary
            Text(name, style = MaterialTheme.customTypography.headline3, color = content)
            amount?.let { Text(it, style = MaterialTheme.customTypography.textS, color = content) }
            Text(
                shortAddress(choice.address),
                style = MaterialTheme.customTypography.textXS,
                color = MaterialTheme.customColors.fgSecondary,
            )
            reason?.let {
                Text(it, style = MaterialTheme.customTypography.textXS, color = MaterialTheme.customColors.fgSecondary)
            }
        }
        choice.tag?.let { NetworkTagChip(it, Modifier.padding(start = 12.dp)) }
    }
}

/**
 * XOR on networks other than the main one, drawn as more rows of the home's asset list: the
 * title carries the network's name, so one list shows everything the wallet holds. Tapping a row
 * opens that network's XOR page.
 */
@Composable
internal fun NetworkAssetRows(
    balances: List<NexusPortfolioBalance>,
    xorIcon: String?,
    xorName: String?,
    onOpen: (NexusPortfolioBalance) -> Unit,
) {
    val directory = WalletNetworkDirectory.current
    val networks = balances.map { it.networkId }
    val main = directory.mainNetwork(networks.toSet() + WalletNetworkId.SORA2)
    directory.ordered(networks, main).forEach { id ->
        val balance = balances.firstOrNull { it.networkId == id } ?: return@forEach
        Spacer(Modifier.size(Dimens.x2))
        NetworkAssetRow(
            balance = balance,
            title = listOfNotNull(xorName ?: "XOR", directory.rowLabel(id, main)).joinToString(" · "),
            tag = directory.tag(id, main)?.takeIf { it != WalletNetworkTag.MAIN },
            icon = xorIcon,
            onClick = { onOpen(balance) },
        )
    }
}

@Composable
private fun NetworkAssetRow(
    balance: NexusPortfolioBalance,
    title: String,
    tag: WalletNetworkTag?,
    icon: String?,
    onClick: () -> Unit,
) {
    val pending = balance.pendingTransactions.size + balance.recoveryPendingTransactions.size
    val amount = when {
        !balance.networkLive -> stringResource(R.string.network_address_ready)
        balance.xorQuantity != null -> "${balance.xorQuantity} XOR"
        balance.errorCode != null -> stringResource(R.string.balance_unavailable)
        else -> "…"
    } + if (pending > 0) " · $pending ${stringResource(R.string.wallet_status_checking)}" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.x3)
            .testTag("NetworkRow${balance.networkId.wireId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenIcon(uri = icon, size = 40.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.customTypography.textM, color = MaterialTheme.customColors.fgPrimary)
            Text(amount, style = MaterialTheme.customTypography.textXSBold, color = MaterialTheme.customColors.fgSecondary)
        }
        tag?.let { NetworkTagChip(it) }
    }
}

/**
 * The portfolio layout's place for other networks: there the portfolio list shows every holding,
 * and this card opens a network's XOR page (activity, Receive, Send) with the same rows.
 */
@Composable
internal fun OtherNetworksCard(
    balances: List<NexusPortfolioBalance>,
    xorIcon: String?,
    xorName: String?,
    isNexusBalanceCurrent: (NexusPortfolioBalance) -> Boolean,
    onSend: (NexusPortfolioBalance) -> Unit,
    onRecovery: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: (NexusPortfolioBalance) -> Unit,
    walletId: String?,
) {
    val sheets = rememberNetworkSheetsState(walletId)
    NetworkSheets(
        state = sheets,
        balances = balances,
        isNexusBalanceCurrent = isNexusBalanceCurrent,
        onSend = onSend,
        onRecovery = onRecovery,
        onRefresh = onRefresh,
        onLoadMore = onLoadMore,
    )
    ContentCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = Dimens.x3, bottom = Dimens.x3)) {
            Text(
                text = stringResource(R.string.wallet_other_networks),
                style = MaterialTheme.customTypography.headline2,
                color = MaterialTheme.customColors.fgPrimary,
                modifier = Modifier.padding(horizontal = Dimens.x3),
            )
            NetworkAssetRows(
                balances = balances.filter(isNexusBalanceCurrent),
                xorIcon = xorIcon,
                xorName = xorName,
                onOpen = sheets::openActivity,
            )
        }
    }
}

private fun shortAddress(value: String): String =
    if (value.length <= 24) value else "${value.take(10)}…${value.takeLast(8)}"
