package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FreshnessKind
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.MoveBlock
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioCalculator
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioLens
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioReadiness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSlots
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.SwapBlock
import jp.co.soramitsu.ui_core.component.button.properties.Size as ButtonSize
import jp.co.soramitsu.ui_core.theme.customTypography

internal interface PortfolioSheetActions {
    fun onDismiss()
    fun onSelectAll()
    fun onSelectWallet(walletId: String)
    fun onRefreshAll()
    fun onManageWallets()
    fun onManageAssets()
    fun onPickWallet(action: WalletAction, walletId: String)
    fun onReviewMove(holdingKey: String, destinationWalletId: String, amount: BigDecimal?)
    fun onOpenSwap(holdingKey: String, targetTokenId: String)
    fun onReceiveOnSora2(walletId: String)
    fun onShowSheet(sheet: PortfolioSheet)
}

@Composable
internal fun PortfolioSheetHost(
    model: PortfolioModel,
    sheet: PortfolioSheet?,
    format: PortfolioFormat,
    busy: Boolean,
    actions: PortfolioSheetActions,
) {
    when (sheet) {
        null -> Unit
        PortfolioSheet.Wallets -> WalletsSheet(model, format, actions)
        PortfolioSheet.HowWeCount -> HowWeCountSheet(model, format, actions)
        PortfolioSheet.Sync -> SyncSheet(model, actions)
        is PortfolioSheet.Move -> MoveSheet(model, sheet.holdingKey, format, busy, actions)
        is PortfolioSheet.Swap -> SwapSheet(model, sheet.holdingKey, format, busy, actions)
        is PortfolioSheet.PickWallet -> PickWalletSheet(model, sheet.action, format, actions)
    }
}

@Composable
private fun changeText(format: PortfolioFormat, fraction: Double?): String? =
    fraction?.let { stringResource(R.string.portfolio_change_24h, format.signedPercent(it)) }

private fun isAmountInput(text: String): Boolean =
    text.length <= 40 && text.all { it.isDigit() || it == '.' || it == ',' }

@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NeonButton(modifier = modifier.fillMaxWidth(), size = ButtonSize.Small, variant = NeonVariant.Secondary, text = text, onClick = onClick)
}

/** All wallets first, then each wallet with its total, 24 h change and freshness. */
@Composable
private fun WalletsSheet(model: PortfolioModel, format: PortfolioFormat, actions: PortfolioSheetActions) {
    val n = MaterialTheme.neon
    PortfolioSheetScaffold(stringResource(R.string.portfolio_wallets), actions::onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.allWalletsAvailable) {
                SheetRow(
                    selected = model.scope == PortfolioScope.ALL,
                    onClick = actions::onSelectAll,
                    leading = { AllWalletsAvatar(42.dp) },
                    title = stringResource(R.string.portfolio_all_wallets),
                    subtitle = stringResource(R.string.portfolio_wallets_together, model.wallets.size),
                    trailingTop = format.fiat(model.allTotals.fiat),
                    trailingBottom = changeText(format, model.allTotals.changeFraction),
                )
            }
            model.wallets.forEach { summary ->
                val wallet = summary.wallet
                SheetRow(
                    selected = model.scope == PortfolioScope.SELECTED && wallet.selected,
                    onClick = { actions.onSelectWallet(wallet.id) },
                    leading = { WalletAvatar(wallet.name, model.colorOfWallet(wallet.id), 42.dp) },
                    title = wallet.name,
                    subtitle = listOfNotNull(
                        stringResource(R.string.portfolio_selected).takeIf { wallet.selected },
                        summary.freshness?.let { freshnessLabel(it, model.nowMillis) },
                    ).joinToString(" · "),
                    trailingTop = format.fiat(summary.totals.fiat),
                    trailingBottom = changeText(format, summary.totals.changeFraction),
                )
            }
        }
        PortfolioBanner(stringResource(R.string.portfolio_wallets_note)) {
            Text(
                stringResource(R.string.portfolio_refresh_all),
                style = MaterialTheme.customTypography.textSBold,
                color = n.link,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = actions::onRefreshAll)
                    .padding(vertical = 6.dp),
            )
        }
        SecondaryAction(stringResource(R.string.portfolio_manage_wallets), actions::onManageWallets)
    }
}

@Composable
private fun HowWeCountSheet(model: PortfolioModel, format: PortfolioFormat, actions: PortfolioSheetActions) {
    val n = MaterialTheme.neon
    val totals = model.totals
    PortfolioSheetScaffold(stringResource(R.string.portfolio_how_we_count), actions::onDismiss) {
        Text(
            stringResource(R.string.portfolio_how_we_count_body),
            style = MaterialTheme.customTypography.paragraphM,
            color = n.textSecondary,
        )
        PortfolioCard(radius = 18.dp) {
            KeyValueRow(stringResource(R.string.portfolio_counted), format.fiat(totals.fiat))
            RowDivider()
            KeyValueRow(stringResource(R.string.portfolio_hidden_by_you), pluralStringResource(R.plurals.portfolio_assets_not_included, totals.hidden, totals.hidden))
            RowDivider()
            KeyValueRow(stringResource(R.string.portfolio_without_price), pluralStringResource(R.plurals.portfolio_assets_not_included, totals.unpriced, totals.unpriced))
            RowDivider()
            KeyValueRow(stringResource(R.string.portfolio_testnets), stringResource(R.string.portfolio_never_counted))
            RowDivider()
            KeyValueRow(stringResource(R.string.portfolio_pool_stakes), stringResource(R.string.portfolio_not_included))
        }
        if (model.scope == PortfolioScope.ALL) {
            Text(
                stringResource(R.string.portfolio_all_wallets_sum),
                style = MaterialTheme.customTypography.paragraphS,
                color = n.textSecondary,
            )
        }
        SecondaryAction(stringResource(R.string.portfolio_manage_hidden), actions::onManageAssets)
    }
}

/** Sync status: what each wallet's freshness means, and Retry. */
@Composable
private fun SyncSheet(model: PortfolioModel, actions: PortfolioSheetActions) {
    val n = MaterialTheme.neon
    val freshness = model.freshness
    PortfolioSheetScaffold(stringResource(R.string.portfolio_sync_status), actions::onDismiss) {
        val body = when (freshness?.kind) {
            FreshnessKind.OFFLINE -> stringResource(R.string.portfolio_sync_offline)
            FreshnessKind.PARTIAL -> stringResource(
                R.string.portfolio_sync_partial,
                freshness.unreachable.joinToString(", ") { networkName(it) },
            )
            FreshnessKind.SAVED -> stringResource(R.string.portfolio_sync_saved)
            else -> stringResource(R.string.portfolio_sync_live)
        }
        Text(body, style = MaterialTheme.customTypography.paragraphM, color = n.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.wallets.filter { it.wallet.id in model.walletIds }.forEach { summary ->
                val state = summary.freshness
                SheetRow(
                    selected = false,
                    onClick = {},
                    role = Role.Button,
                    leading = { WalletAvatar(summary.wallet.name, model.colorOfWallet(summary.wallet.id), 38.dp) },
                    title = summary.wallet.name,
                    subtitle = state?.unreachable?.takeIf { it.isNotEmpty() }?.let { unreachable ->
                        stringResource(R.string.portfolio_unreachable, unreachable.joinToString(", ") { networkName(it) })
                    },
                    trailingTop = state?.let { freshnessLabel(it, model.nowMillis) },
                )
            }
        }
        NeonButton(
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            variant = NeonVariant.Primary,
            text = stringResource(R.string.portfolio_refresh_all),
            onClick = actions::onRefreshAll,
        )
    }
}

/** "This is in {wallet}. We switch to it first." */
@Composable
private fun SwitchNote(model: PortfolioModel, walletId: String) {
    val wallet = model.wallet(walletId) ?: return
    if (wallet.selected) return
    PortfolioBanner(stringResource(R.string.portfolio_switch_note, wallet.name), warning = true)
}

@Composable
private fun HoldingCard(model: PortfolioModel, holding: PortfolioHolding, line: String, onClick: (() -> Unit)?) {
    val n = MaterialTheme.neon
    val wallet = model.wallet(holding.walletId)
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(n.glassFill)
            .border(1.dp, n.hairline, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TokenOrb(holding.token.iconUri, 38.dp)
            NetworkBadge(holding.network, 17.dp, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${holding.token.symbol} · ${networkName(holding.network)}",
                style = MaterialTheme.customTypography.headline3,
                color = n.textPrimary,
                maxLines = 1,
            )
            Text(
                listOfNotNull(wallet?.name, line).joinToString(" · "),
                style = MaterialTheme.customTypography.textS,
                color = n.textSecondary,
            )
        }
        if (onClick != null) PortfolioGlyph(Glyph.CHEVRON_DOWN, n.textTertiary, Modifier.size(18.dp))
    }
}

@Composable
private fun HoldingChooser(model: PortfolioModel, candidates: List<PortfolioHolding>, format: PortfolioFormat, onPick: (PortfolioHolding) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        candidates.forEach { holding ->
            SheetRow(
                selected = false,
                onClick = { onPick(holding) },
                role = Role.Button,
                leading = {
                    Box {
                        TokenOrb(holding.token.iconUri, 38.dp)
                        NetworkBadge(holding.network, 17.dp, Modifier.align(Alignment.BottomEnd))
                    }
                },
                title = "${holding.token.symbol} · ${networkName(holding.network)}",
                subtitle = model.wallet(holding.walletId)?.name,
                trailingTop = format.amount(holding.transferable),
                trailingBottom = PortfolioCalculator.countedFiat(holding)?.let(format::fiat),
            )
        }
    }
}

/** Move between the user's own wallets on one network; hands off to Send (or the Nexus send). */
@Composable
private fun MoveSheet(model: PortfolioModel, holdingKey: String?, format: PortfolioFormat, busy: Boolean, actions: PortfolioSheetActions) {
    val n = MaterialTheme.neon
    val holding = holdingKey?.let(model::holding)
    val candidates = PortfolioCalculator.sortHoldings(PortfolioCalculator.visibleHoldings(model.snapshot, model.walletIds))
        .filter { PortfolioReadiness.moveBlock(model.snapshot, it) == null }
    PortfolioSheetScaffold(
        if (holding != null) stringResource(R.string.portfolio_move_token, holding.token.symbol) else stringResource(R.string.portfolio_move),
        actions::onDismiss,
    ) {
        if (holding == null) {
            if (candidates.isEmpty()) {
                val single = model.snapshot.wallets.size < 2
                Text(
                    stringResource(if (single) R.string.portfolio_move_needs_wallets else R.string.portfolio_nothing_to_move),
                    style = MaterialTheme.customTypography.paragraphM,
                    color = n.textSecondary,
                )
                if (single) SecondaryAction(stringResource(R.string.portfolio_manage_wallets), actions::onManageWallets)
            } else {
                Overline(stringResource(R.string.portfolio_choose_holding))
                HoldingChooser(model, candidates, format) { actions.onShowSheet(PortfolioSheet.Move(it.key)) }
            }
            return@PortfolioSheetScaffold
        }
        val block = PortfolioReadiness.moveBlock(model.snapshot, holding)
        val targets = PortfolioReadiness.moveTargets(model.snapshot, holding)
        var destination by rememberSaveable(holding.key) { mutableStateOf(targets.firstOrNull()?.id) }
        var amountText by rememberSaveable(holding.key) { mutableStateOf("") }
        val amount = amountText.replace(',', '.').toBigDecimalOrNull()
        // Blank means enter the amount in Send; nonempty input must be a positive balance.
        val invalidAmount = amountText.isNotEmpty() &&
            (amount == null || amount.signum() <= 0 || amount > holding.transferable)
        val symbol = holding.token.symbol
        val network = networkName(holding.network)
        SwitchNote(model, holding.walletId)
        Overline(stringResource(R.string.portfolio_from))
        HoldingCard(
            model,
            holding,
            stringResource(R.string.portfolio_transferable_amount, format.amount(holding.transferable, symbol)),
            onClick = if (candidates.size > 1) ({ actions.onShowSheet(PortfolioSheet.Move(null)) }) else null,
        )
        if (block != null) {
            val reason = when (block) {
                MoveBlock.NO_OTHER_WALLET -> stringResource(R.string.portfolio_move_no_other_wallet, network)
                MoveBlock.SEND_UNAVAILABLE -> stringResource(R.string.portfolio_send_unavailable, network)
                MoveBlock.NOTHING_TRANSFERABLE -> stringResource(R.string.portfolio_nothing_transferable)
            }
            PortfolioBanner(reason, warning = true)
            if (holding.network != WalletNetworkId.SORA2) {
                val receive = stringResource(R.string.portfolio_receive_on_sora2)
                SecondaryAction(receive, { actions.onReceiveOnSora2(holding.walletId) })
            }
            return@PortfolioSheetScaffold
        }
        Overline(stringResource(R.string.portfolio_to))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEach { wallet ->
                val address = model.snapshot.addresses[wallet.id]?.get(holding.network).orEmpty()
                SheetRow(
                    selected = destination == wallet.id,
                    onClick = { destination = wallet.id },
                    leading = { WalletAvatar(wallet.name, model.colorOfWallet(wallet.id), 38.dp) },
                    title = wallet.name,
                    subtitle = stringResource(R.string.portfolio_arrives_on, symbol, network) + " · " + address.truncateUserAddress(),
                )
            }
        }
        Overline(stringResource(R.string.portfolio_amount))
        AmountField(
            value = amountText,
            symbol = symbol,
            iconUri = holding.token.iconUri,
            error = invalidAmount,
            onValueChange = { text -> if (isAmountInput(text)) amountText = text },
            onMax = { amountText = PortfolioReadiness.maxAmount(holding).stripTrailingZeros().toPlainString() },
        )
        PortfolioCard(radius = 18.dp) {
            KeyValueRow(stringResource(R.string.portfolio_network), network)
            RowDivider()
            KeyValueRow(stringResource(R.string.portfolio_route), stringResource(R.string.portfolio_route_same_network))
            RowDivider()
            KeyValueRow(stringResource(R.string.network_fee), stringResource(R.string.portfolio_fee_in_send))
        }
        PortfolioBanner(stringResource(R.string.portfolio_move_note))
        val wallet = model.wallet(holding.walletId)
        NeonButton(
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            variant = NeonVariant.Primary,
            enabled = !busy && destination != null && !invalidAmount,
            loading = busy,
            text = if (wallet != null && !wallet.selected) {
                stringResource(R.string.portfolio_switch_and_review, wallet.name)
            } else {
                stringResource(R.string.portfolio_review_in_send)
            },
            onClick = { destination?.let { actions.onReviewMove(holding.key, it, amount) } },
        )
    }
}

@Composable
private fun AmountField(
    value: String,
    symbol: String,
    iconUri: String?,
    error: Boolean,
    onValueChange: (String) -> Unit,
    onMax: () -> Unit,
) {
    val n = MaterialTheme.neon
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(n.neuBg)
            .border(1.dp, if (error) n.error else n.borderInput, shape)
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("0", style = MaterialTheme.customTypography.headline1, color = n.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.customTypography.headline1.copy(color = n.textPrimary),
                cursorBrush = SolidColor(n.focusRing),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = symbol },
            )
        }
        TokenOrb(iconUri, 24.dp)
        Spacer(Modifier.width(6.dp))
        Text(symbol, style = MaterialTheme.customTypography.headline3, color = n.textPrimary)
        Spacer(Modifier.width(8.dp))
        NeonButton(size = ButtonSize.ExtraSmall, variant = NeonVariant.Secondary, text = stringResource(R.string.portfolio_max), onClick = onMax)
    }
    if (error) {
        Text(stringResource(R.string.portfolio_amount_too_high), style = MaterialTheme.customTypography.textS, color = n.error)
    }
}

private val TARGET_SYMBOLS = mapOf(
    AssetHolder.XOR_ASSET_ID to "XOR",
    PortfolioSlots.VAL_ID to "VAL",
    PortfolioSlots.PSWAP_ID to "PSWAP",
    PortfolioSlots.XSTUSD_ID to "XSTUSD",
)

/** Swap on Polkaswap: only picks the pair, then opens the app's swap screen. */
@Composable
private fun SwapSheet(model: PortfolioModel, holdingKey: String?, format: PortfolioFormat, busy: Boolean, actions: PortfolioSheetActions) {
    val n = MaterialTheme.neon
    val holding = holdingKey?.let(model::holding)
    val candidates = PortfolioCalculator.sortHoldings(PortfolioCalculator.visibleHoldings(model.snapshot, model.walletIds))
        .filter { PortfolioReadiness.swapBlock(it) == null }
    PortfolioSheetScaffold(stringResource(R.string.portfolio_swap_on_polkaswap), actions::onDismiss) {
        if (holding == null) {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.portfolio_nothing_to_swap), style = MaterialTheme.customTypography.paragraphM, color = n.textSecondary)
                model.selectedWallet?.let { wallet ->
                    val receive = stringResource(R.string.portfolio_receive_on_sora2)
                    SecondaryAction(receive, { actions.onReceiveOnSora2(wallet.id) })
                }
            } else {
                Overline(stringResource(R.string.portfolio_choose_holding))
                HoldingChooser(model, candidates, format) { actions.onShowSheet(PortfolioSheet.Swap(it.key)) }
            }
            return@PortfolioSheetScaffold
        }
        val block = PortfolioReadiness.swapBlock(holding)
        val network = networkName(holding.network)
        if (block != null) {
            val reason = when (block) {
                SwapBlock.NOT_ON_SORA2 -> stringResource(R.string.portfolio_route_runs_on_sora2) + " " + stringResource(R.string.portfolio_route_none, network)
                SwapBlock.TESTNET -> stringResource(R.string.portfolio_route_test) + " " + stringResource(R.string.portfolio_route_none, network)
                SwapBlock.NOTHING_TRANSFERABLE -> stringResource(R.string.portfolio_nothing_transferable)
            }
            Text(reason, style = MaterialTheme.customTypography.paragraphM, color = n.textSecondary)
            val receive = stringResource(R.string.portfolio_receive_on_sora2)
            SecondaryAction(receive, { actions.onReceiveOnSora2(holding.walletId) })
            return@PortfolioSheetScaffold
        }
        val targets = PortfolioReadiness.swapTargets(holding.token.id)
        var target by rememberSaveable(holding.key) { mutableStateOf(targets.first()) }
        val wallet = model.wallet(holding.walletId)
        SwitchNote(model, holding.walletId)
        Overline(stringResource(R.string.portfolio_you_pay))
        HoldingCard(
            model,
            holding,
            stringResource(R.string.portfolio_transferable_amount, format.amount(holding.transferable, holding.token.symbol)),
            onClick = if (candidates.size > 1) ({ actions.onShowSheet(PortfolioSheet.Swap(null)) }) else null,
        )
        Overline(stringResource(R.string.portfolio_you_get))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            targets.forEach { id ->
                val on = id == target
                val color = PortfolioPalette.slot(PortfolioSlots.seed(PortfolioLens.TOKENS)[id])
                val shape = RoundedCornerShape(20.dp)
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(if (on) color.copy(alpha = 0.18f) else n.glassFill)
                        .border(if (on) 1.5.dp else 1.dp, if (on) color else n.hairline, shape)
                        .clickable(role = Role.RadioButton, onClick = { target = id })
                        .semantics { selected = on }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(TARGET_SYMBOLS[id] ?: id.take(6), style = MaterialTheme.customTypography.textSBold, color = n.textPrimary)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckLine(stringResource(R.string.portfolio_check_account, wallet?.name.orEmpty()), ok = true)
            CheckLine(stringResource(R.string.portfolio_check_available), ok = true)
            CheckLine(
                stringResource(if (model.swapDisclaimerPending) R.string.portfolio_check_disclaimer_pending else R.string.portfolio_check_disclaimer_done),
                ok = !model.swapDisclaimerPending,
            )
        }
        Text(
            stringResource(R.string.portfolio_swap_note),
            style = MaterialTheme.customTypography.paragraphS,
            color = n.textSecondary,
        )
        NeonButton(
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            variant = NeonVariant.Swap,
            enabled = !busy,
            loading = busy,
            text = if (wallet != null && !wallet.selected) {
                stringResource(R.string.portfolio_switch_and_review, wallet.name)
            } else {
                stringResource(R.string.portfolio_open_polkaswap)
            },
            leftIcon = painterResource(R.drawable.ic_polkaswap),
            onClick = { actions.onOpenSwap(holding.key, target) },
        )
    }
}

/** In All wallets, Send and Receive first ask which wallet. */
@Composable
private fun PickWalletSheet(model: PortfolioModel, action: WalletAction, format: PortfolioFormat, actions: PortfolioSheetActions) {
    PortfolioSheetScaffold(
        stringResource(if (action == WalletAction.SEND) R.string.portfolio_send_from else R.string.portfolio_receive_to),
        actions::onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.wallets.forEach { summary ->
                val wallet = summary.wallet
                SheetRow(
                    selected = false,
                    onClick = { actions.onPickWallet(action, wallet.id) },
                    role = Role.Button,
                    leading = { WalletAvatar(wallet.name, model.colorOfWallet(wallet.id), 42.dp) },
                    title = wallet.name,
                    subtitle = if (wallet.selected) stringResource(R.string.portfolio_selected) else stringResource(R.string.portfolio_switch_first),
                    trailingTop = format.fiat(summary.totals.fiat),
                )
            }
        }
    }
}
