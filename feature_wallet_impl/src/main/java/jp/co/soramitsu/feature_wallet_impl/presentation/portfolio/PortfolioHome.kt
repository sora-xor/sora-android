package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.compose.neon.HeroCard
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonSegments
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.NeumorphicSurface
import jp.co.soramitsu.common.presentation.compose.neon.neonGlass
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyTags
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyChip
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FreshnessKind
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioCalculator
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioGroup
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioLens
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioReadiness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioToken
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioTotals
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.Readiness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.WalletFreshness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.isTestnet
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.TiltState
import jp.co.soramitsu.ui_core.component.button.properties.Size as ButtonSize
import jp.co.soramitsu.ui_core.theme.customTypography

/** What the home's controls do; the fragment wires these to the view models. */
internal interface PortfolioHomeActions {
    fun onScopeChip()
    fun onScan()
    fun onFreshness()
    fun onCaption()
    fun onLegend(key: String)
    fun onSend()
    fun onReceive()
    fun onMove()
    fun onSwap()
    fun onLens(lens: PortfolioLens)
    fun onToggleRow(key: String)
    fun onOpenToken(tokenId: String)
    fun onOpenWalletAlone(walletId: String)
    fun onMoveHolding(holdingKey: String)
    fun onSwapHolding(holdingKey: String)
}

internal fun PortfolioModel.colorOfWallet(walletId: String): Color = PortfolioPalette.slot(walletSlots[walletId])

internal fun PortfolioModel.colorOfGroup(key: String?): Color =
    if (key == null || key == OTHER_KEY) PortfolioPalette.other else PortfolioPalette.slot(slots[key])

/** The hero: the sky with the scope chip, and the hero card overlapping its bottom by 22 dp. */
@Composable
internal fun PortfolioHero(
    model: PortfolioModel,
    format: PortfolioFormat,
    tilt: TiltState,
    actions: PortfolioHomeActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        PortfolioSky(
            fan = if (model.empty) emptyList() else model.fan,
            colorOf = { key -> model.colorOfGroup(key) },
            highlighted = model.highlighted,
            tilt = tilt,
            onSliceTap = actions::onLegend,
        ) {
            ScopeChip(
                model = model,
                onClick = actions::onScopeChip,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = SkyGeometry.TOP_ROW),
            )
            // The selected wallet cannot sign on this phone: its name carries the Read-only chip,
            // as on the home without the Portfolio view; the notice under the hero says why.
            if (model.scope == PortfolioScope.SELECTED && LocalSelectedAccountReadOnly.current) {
                ReadOnlyChip(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = SkyGeometry.TOP_ROW + 54.dp)
                        .testTag(AccountReadOnlyTags.CHIP),
                )
            }
            NeuIconButton(
                label = stringResource(R.string.wallet_scan_qr),
                onClick = actions::onScan,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = SkyGeometry.TOP_ROW),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_scan_qr),
                    contentDescription = null,
                    tint = MaterialTheme.neon.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(Modifier.padding(top = SkyGeometry.HEIGHT - 22.dp, start = 16.dp, end = 16.dp)) {
            if (model.empty) {
                EmptyCard(model, actions)
            } else {
                PortfolioHeroCard(model, format, actions)
            }
        }
    }
}

@Composable
private fun ScopeChip(model: PortfolioModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val all = model.scope == PortfolioScope.ALL
    val wallet = model.selectedWallet
    val title = if (all) stringResource(R.string.portfolio_all_wallets) else wallet?.name.orEmpty()
    val label = stringResource(R.string.portfolio_scope_label, title)
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.minimumInteractiveComponentSize()) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .widthIn(max = 224.dp)
                .neonGlass(shape, n, fx)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (all || wallet == null) {
                AllWalletsAvatar(34.dp)
            } else {
                WalletAvatar(wallet.name, model.colorOfWallet(wallet.id), 34.dp)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.customTypography.headline3,
                color = n.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            PortfolioGlyph(Glyph.CHEVRON_DOWN, n.textPrimary, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyCard(model: PortfolioModel, actions: PortfolioHomeActions) {
    val n = MaterialTheme.neon
    HeroCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // An empty list is only "empty" if it could be read: the state says how fresh it is.
            model.freshness?.let {
                FreshnessChip(it, model.nowMillis, actions::onFreshness, Modifier.align(Alignment.End))
            }
            Text(
                text = stringResource(R.string.portfolio_wallet_ready),
                style = MaterialTheme.customTypography.headline1,
                color = n.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.portfolio_wallet_ready_body),
                style = MaterialTheme.customTypography.paragraphM,
                color = n.textSecondary,
                textAlign = TextAlign.Center,
            )
            NeonButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                size = ButtonSize.Large,
                variant = NeonVariant.Primary,
                text = stringResource(R.string.common_receive),
                // The empty-wallet Receive is one of the two CTAs allowed to pulse (design spec 4.7.2).
                pulse = true,
                onClick = actions::onReceive,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortfolioHeroCard(model: PortfolioModel, format: PortfolioFormat, actions: PortfolioHomeActions) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    HeroCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline(stringResource(R.string.portfolio_total_balance), Modifier.weight(1f))
                model.freshness?.let {
                    FreshnessChip(it, model.nowMillis, actions::onFreshness, Modifier.padding(end = 6.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            TotalBlock(model.totals, format, stale = model.freshness?.kind == FreshnessKind.OFFLINE)
            if (model.totals.excluded > 0) {
                Spacer(Modifier.height(4.dp))
                ExclusionCaption(model.totals, actions::onCaption)
            }
            if (model.fan.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val chips: @Composable () -> Unit = {
                    model.fan.forEach { slice ->
                        val key = slice.key ?: OTHER_KEY
                        LegendChip(
                            label = legendLabel(model, slice.key),
                            percent = format.rays(slice.rays, slice.countedFiat),
                            fiat = format.fiat(slice.countedFiat),
                            color = model.colorOfGroup(slice.key),
                            selected = model.highlighted == key,
                            onClick = { actions.onLegend(key) },
                        )
                    }
                }
                if (largeText) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
                } else {
                    Row(
                        Modifier
                            .padding(horizontal = 0.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { chips() }
                }
            }
            Spacer(Modifier.height(10.dp))
            QuickActions(actions, largeText)
        }
    }
}

@Composable
internal fun legendLabel(model: PortfolioModel, key: String?): String = when {
    key == null -> stringResource(R.string.portfolio_other)
    model.lens == PortfolioLens.TOKENS -> tokenSymbol(model, key) ?: key
    model.lens == PortfolioLens.NETWORKS -> WalletNetworkId.fromWireId(key)?.let(::networkName) ?: key
    else -> model.wallet(key)?.name ?: key
}

private fun tokenSymbol(model: PortfolioModel, key: String): String? =
    model.groups.firstOrNull { it.key == key }?.holdings?.firstOrNull()?.token?.symbol

@Composable
private fun TotalBlock(totals: PortfolioTotals, format: PortfolioFormat, stale: Boolean) {
    val n = MaterialTheme.neon
    val (symbol, whole, cents) = format.fiatParts(totals.fiat)
    val primary = if (stale) n.textSecondary else n.textPrimary
    val compact = whole.length > 10
    val big = if (compact) 32.sp else 42.sp
    val small = if (compact) 22.sp else 28.sp
    val change = totals.changeFraction
    val title = stringResource(R.string.portfolio_total_balance)
    val description = buildString {
        append(title).append(", ").append(format.fiat(totals.fiat))
        if (change != null) {
            append(", ").append(format.signedFiat(totals.change)).append(", ").append(format.signedPercent(change)).append(" 24h")
        }
    }
    Column(
        Modifier.clearAndSetSemantics {
            contentDescription = description
            heading()
        }
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = small, color = n.textSecondary)) { append(symbol) }
                withStyle(SpanStyle(fontSize = big, color = primary)) { append(whole) }
                withStyle(SpanStyle(fontSize = small, color = n.textSecondary)) { append(cents) }
            },
            style = NeonType.displayHero.copy(lineHeight = 50.sp),
            maxLines = 1,
        )
        if (change != null) {
            ChangeLine(totals.change, change, format)
        }
    }
}

/** ▲ +$148.20 · +1.2% · 24h: arrow and sign always, the app's up and down colours. */
@Composable
private fun ChangeLine(change: Double, fraction: Double, format: PortfolioFormat) {
    val n = MaterialTheme.neon
    val up = change >= 0
    val color = if (up) n.success else n.error
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = color)) {
                append(if (up) "▲ " else "▼ ")
                append(format.signedFiat(change))
            }
            withStyle(SpanStyle(color = n.textSecondary)) {
                append("  ·  ")
                append(format.signedPercent(fraction))
                append("  ·  24h")
            }
        },
        style = NeonType.amountM,
        maxLines = 1,
    )
}

@Composable
private fun ExclusionCaption(totals: PortfolioTotals, onClick: () -> Unit) {
    val n = MaterialTheme.neon
    val parts = buildList {
        if (totals.hidden > 0) add(stringResource(R.string.portfolio_excluded_hidden, totals.hidden))
        if (totals.unpriced > 0) add(stringResource(R.string.portfolio_excluded_unpriced, totals.unpriced))
        if (totals.testnet > 0) add(stringResource(R.string.portfolio_excluded_testnet, totals.testnet))
    }
    val text = stringResource(R.string.portfolio_excluded_caption, parts.joinToString(", "))
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(R.string.portfolio_how_we_count), role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortfolioGlyph(Glyph.INFO, n.link, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.customTypography.textS, color = n.textSecondary)
    }
}

/** LIVE · 31s (green dot), SAVED · 2h, PARTIAL (amber), OFFLINE. */
@Composable
internal fun FreshnessChip(freshness: WalletFreshness, nowMillis: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val label = freshnessLabel(freshness, nowMillis)
    val color = when (freshness.kind) {
        FreshnessKind.LIVE -> n.textPrimary
        FreshnessKind.SAVED, FreshnessKind.PARTIAL -> n.warning
        FreshnessKind.OFFLINE -> n.textSecondary
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(n.glassFill)
            .border(1.dp, n.hairline, shape)
            .clickable(onClickLabel = stringResource(R.string.portfolio_sync_status), role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (freshness.kind) {
            FreshnessKind.LIVE -> Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(n.success)
            )
            FreshnessKind.SAVED -> PortfolioGlyph(Glyph.CLOCK, color, Modifier.size(14.dp))
            FreshnessKind.PARTIAL -> PortfolioGlyph(Glyph.WARNING, color, Modifier.size(14.dp))
            FreshnessKind.OFFLINE -> PortfolioGlyph(Glyph.OFFLINE, color, Modifier.size(14.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = NeonType.eyebrow, maxLines = 1)
    }
}

/** The freshness of one wallet in a row: a small dot or glyph and the label, no chip. */
@Composable
internal fun FreshnessLine(freshness: WalletFreshness, nowMillis: Long, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val color = when (freshness.kind) {
        FreshnessKind.LIVE -> n.textSecondary
        FreshnessKind.SAVED, FreshnessKind.PARTIAL -> n.warning
        FreshnessKind.OFFLINE -> n.textTertiary
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (freshness.kind) {
            FreshnessKind.LIVE -> Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(n.success)
            )
            FreshnessKind.SAVED -> PortfolioGlyph(Glyph.CLOCK, color, Modifier.size(12.dp))
            FreshnessKind.PARTIAL -> PortfolioGlyph(Glyph.WARNING, color, Modifier.size(12.dp))
            FreshnessKind.OFFLINE -> PortfolioGlyph(Glyph.OFFLINE, color, Modifier.size(12.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(freshnessLabel(freshness, nowMillis), color = color, style = NeonType.eyebrow, maxLines = 1)
    }
}

/** A group's share as the fan shows it (its rays), so the row and its legend chip agree. */
internal fun PortfolioModel.shareText(group: PortfolioGroup, format: PortfolioFormat): String =
    fan.firstOrNull { it.key == group.key }?.let { format.rays(it.rays, it.countedFiat) }
        ?: format.share(group.share)

@Composable
internal fun freshnessLabel(freshness: WalletFreshness, nowMillis: Long): String {
    val state = stringResource(
        when (freshness.kind) {
            FreshnessKind.LIVE -> R.string.portfolio_state_live
            FreshnessKind.SAVED -> R.string.portfolio_state_saved
            FreshnessKind.PARTIAL -> R.string.portfolio_state_partial
            FreshnessKind.OFFLINE -> R.string.portfolio_state_offline
        }
    )
    val age = freshness.updatedAtMillis?.let { ageLabel(nowMillis - it) }
    return if (age != null && (freshness.kind == FreshnessKind.LIVE || freshness.kind == FreshnessKind.SAVED)) "$state · $age" else state
}

@Composable
internal fun ageLabel(elapsedMillis: Long): String {
    val seconds = (elapsedMillis / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> stringResource(R.string.portfolio_age_seconds, seconds.toInt())
        seconds < 3600 -> stringResource(R.string.portfolio_age_minutes, (seconds / 60).toInt())
        seconds < 86_400 -> stringResource(R.string.portfolio_age_hours, (seconds / 3600).toInt())
        else -> stringResource(R.string.portfolio_age_days, (seconds / 86_400).toInt())
    }
}

@Composable
private fun LegendChip(
    label: String,
    percent: String,
    fiat: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val n = MaterialTheme.neon
    val description =
        stringResource(R.string.portfolio_legend_description, label, percent.removeSuffix("%"), fiat)
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(shape)
                .background(if (selected) color.copy(alpha = 0.18f) else n.glassFill)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) color else n.hairline, shape)
                .clickable(role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    this.selected = selected
                }
                .padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.customTypography.textSBold, color = n.textPrimary, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text(percent, style = NeonType.amountS.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = n.textSecondary, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(actions: PortfolioHomeActions, largeText: Boolean) {
    val n = MaterialTheme.neon
    val items: @Composable () -> Unit = {
        QuickAction(stringResource(R.string.common_send), onClick = actions::onSend) {
            PortfolioGlyph(Glyph.SEND, n.textPrimary, Modifier.size(24.dp))
        }
        QuickAction(stringResource(R.string.common_receive), onClick = actions::onReceive) {
            PortfolioGlyph(Glyph.RECEIVE, n.textPrimary, Modifier.size(24.dp))
        }
        QuickAction(stringResource(R.string.portfolio_move), onClick = actions::onMove) {
            PortfolioGlyph(Glyph.MOVE, n.textPrimary, Modifier.size(24.dp))
        }
        QuickAction(stringResource(R.string.portfolio_swap), onClick = actions::onSwap) {
            PolkaswapFlower(Modifier.size(26.dp), tint = n.polkaswapBrand)
        }
    }
    if (largeText) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { items() }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { items() }
    }
}

@Composable
private fun QuickAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeumorphicSurface(shape = CircleShape, interactionSource = interaction, modifier = Modifier.size(56.dp)) {
            icon()
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.customTypography.textSBold, color = MaterialTheme.neon.textPrimary, maxLines = 1)
    }
}

/** Tokens · Networks · Wallets, with the app's segmented control. */
@Composable
internal fun LensControl(lenses: List<PortfolioLens>, selected: PortfolioLens, onSelect: (PortfolioLens) -> Unit, modifier: Modifier = Modifier) {
    val labels = lenses.map { lens ->
        stringResource(
            when (lens) {
                PortfolioLens.TOKENS -> R.string.portfolio_lens_tokens
                PortfolioLens.NETWORKS -> R.string.portfolio_lens_networks
                PortfolioLens.WALLETS -> R.string.portfolio_lens_wallets
            }
        )
    }
    NeonSegments(
        labels = labels,
        selected = lenses.indexOf(selected).coerceAtLeast(0),
        onSelect = { index -> lenses.getOrNull(index)?.let(onSelect) },
        modifier = modifier,
    )
}

/** The list for the active lens, in one glass card. Rows add up to the header. */
@Composable
internal fun PortfolioList(model: PortfolioModel, format: PortfolioFormat, actions: PortfolioHomeActions, modifier: Modifier = Modifier) {
    val groups = model.groups.filter { it.holdings.isNotEmpty() || model.lens == PortfolioLens.WALLETS }
    PortfolioCard(modifier) {
        if (groups.isEmpty()) {
            Text(
                text = stringResource(R.string.portfolio_nothing_listed),
                style = MaterialTheme.customTypography.paragraphS,
                color = MaterialTheme.neon.textSecondary,
                modifier = Modifier.padding(20.dp),
            )
        }
        groups.forEachIndexed { index, group ->
            if (index > 0) RowDivider()
            when (model.lens) {
                PortfolioLens.TOKENS -> TokenRow(model, group, format, actions)
                PortfolioLens.NETWORKS -> NetworkRow(model, group, format, actions)
                PortfolioLens.WALLETS -> WalletRow(model, group, format, actions)
            }
        }
    }
}

@Composable
private fun ExpandChevron(expanded: Boolean) {
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    PortfolioGlyph(Glyph.CHEVRON_DOWN, MaterialTheme.neon.textTertiary, Modifier.size(20.dp).rotate(angle))
}

@Composable
private fun RowAmounts(top: String, topMuted: Boolean, bottom: String) {
    val n = MaterialTheme.neon
    Column(horizontalAlignment = Alignment.End) {
        Text(
            top,
            style = if (topMuted) NeonType.amountS else NeonType.amountM,
            color = if (topMuted) n.textTertiary else n.textPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(bottom, style = NeonType.amountS, color = n.textSecondary, maxLines = 1)
    }
}

@Composable
private fun ChangeBadge(change: Double?, format: PortfolioFormat) {
    if (change == null || kotlin.math.abs(change) < 0.0005) return
    val n = MaterialTheme.neon
    val up = change > 0
    Text(
        text = (if (up) "▲ " else "▼ ") + format.signedPercent(change),
        style = MaterialTheme.customTypography.textXSBold,
        color = if (up) n.success else n.error,
        maxLines = 1,
    )
}

@Composable
private fun fiatOrReason(format: PortfolioFormat, group: PortfolioGroup, token: PortfolioToken?): Pair<String, Boolean> = when {
    group.testnetOnly -> stringResource(R.string.portfolio_no_value) to true
    token != null && token.price == null -> stringResource(R.string.portfolio_no_price) to true
    else -> format.fiat(group.countedFiat) to false
}

@Composable
private fun TokenRow(model: PortfolioModel, group: PortfolioGroup, format: PortfolioFormat, actions: PortfolioHomeActions) {
    val n = MaterialTheme.neon
    val mainnet = group.holdings.firstOrNull { !it.network.isTestnet }
    val token = (mainnet ?: group.holdings.first()).token
    val expandable = group.holdings.size >= 2
    val expanded = expandable && group.key in model.expanded
    val subtitle = when {
        group.networks.size >= 2 -> pluralStringResource(R.plurals.portfolio_networks, group.networks.size, group.networks.size)
        model.scope == PortfolioScope.ALL && group.walletIds.size >= 2 ->
            pluralStringResource(R.plurals.portfolio_wallets, group.walletIds.size, group.walletIds.size)
        else -> networkName(group.networks.first())
    }
    val (fiat, muted) = fiatOrReason(format, group, token)
    val whereLabel = stringResource(R.string.portfolio_where_it_lives)
    val moveLabel = stringResource(R.string.portfolio_move)
    val swapLabel = stringResource(R.string.portfolio_swap)
    val first = group.holdings.first()
    Column(Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(
                    onClickLabel = if (expandable) null else whereLabel,
                    onClick = { if (expandable) actions.onToggleRow(group.key) else actions.onOpenToken(group.key) },
                )
                .semantics {
                    if (expandable) stateDescription = if (expanded) "expanded" else "collapsed"
                    customActions = listOf(
                        CustomAccessibilityAction(whereLabel) {
                            actions.onOpenToken(group.key)
                            true
                        },
                        CustomAccessibilityAction(moveLabel) {
                            actions.onMoveHolding(first.key)
                            true
                        },
                        CustomAccessibilityAction(swapLabel) {
                            actions.onSwapHolding(first.key)
                            true
                        },
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TokenOrb(token.iconUri, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(token.symbol, style = MaterialTheme.customTypography.headline3, color = n.textPrimary, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    if (group.testnetOnly) PortfolioTag(stringResource(R.string.portfolio_test_tag)) else ChangeBadge(token.change24h, format)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(subtitle, style = MaterialTheme.customTypography.textS, color = n.textSecondary, maxLines = 1)
                    if (model.scope == PortfolioScope.ALL && model.allWalletsAvailable) {
                        Spacer(Modifier.width(8.dp))
                        WalletDots(group.walletIds.map { model.colorOfWallet(it) })
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            RowAmounts(fiat, muted, format.amount(group.amount, token.symbol))
            if (expandable) {
                Spacer(Modifier.width(6.dp))
                ExpandChevron(expanded)
            }
        }
        if (expanded) {
            HoldingsWell {
                group.holdings.forEachIndexed { index, holding ->
                    if (index > 0) RowDivider(inset = 12.dp)
                    HoldingRow(
                        model = model,
                        holding = holding,
                        format = format,
                        showToken = false,
                        onClick = { actions.onOpenToken(holding.token.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingsWell(content: @Composable () -> Unit) {
    val n = MaterialTheme.neon
    Column(
        Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(n.neuBg.copy(alpha = if (n.isDark) 0.85f else 0.9f))
            .border(1.dp, n.hairline, RoundedCornerShape(16.dp))
    ) { content() }
}

/** Holding row: network badge, network, wallet, amount, fiat and a readiness chip when actionable. */
@Composable
internal fun HoldingRow(
    model: PortfolioModel,
    holding: PortfolioHolding,
    format: PortfolioFormat,
    showToken: Boolean,
    onClick: () -> Unit,
) {
    val n = MaterialTheme.neon
    val wallet = model.wallet(holding.walletId)
    val readiness = PortfolioReadiness.readiness(holding)
    val counted = PortfolioCalculator.countedFiat(holding)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showToken) TokenOrb(holding.token.iconUri, 30.dp) else NetworkBadge(holding.network, 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showToken) holding.token.symbol else networkName(holding.network),
                    style = MaterialTheme.customTypography.textMBold,
                    color = n.textPrimary,
                    maxLines = 1,
                )
                if (readiness != Readiness.NONE) {
                    Spacer(Modifier.width(8.dp))
                    ReadinessChip(ready = readiness == Readiness.READY)
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (model.allWalletsAvailable && wallet != null) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(model.colorOfWallet(wallet.id))
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = listOfNotNull(
                        wallet?.name?.takeIf { model.allWalletsAvailable },
                        if (showToken) networkName(holding.network) else null,
                    ).joinToString(" · ").ifEmpty { networkName(holding.network) },
                    style = MaterialTheme.customTypography.textS,
                    color = n.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(format.amount(holding.total, holding.token.symbol), style = NeonType.amountS.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = n.textPrimary, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                counted?.let(format::fiat) ?: stringResource(if (holding.network.isTestnet) R.string.portfolio_no_value else R.string.portfolio_no_price),
                style = NeonType.amountS,
                color = if (counted != null) n.textSecondary else n.textTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NetworkRow(model: PortfolioModel, group: PortfolioGroup, format: PortfolioFormat, actions: PortfolioHomeActions) {
    val n = MaterialTheme.neon
    val network = WalletNetworkId.fromWireId(group.key) ?: return
    val expanded = group.key in model.expanded
    val tokens = group.holdings.groupBy { it.token.id }.values.map { holdings ->
        holdings to holdings.sumOf { PortfolioCalculator.countedFiat(it) ?: 0.0 }
    }.sortedByDescending { it.second }
    val tokenCount = tokens.size
    Column(Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClick = { actions.onToggleRow(group.key) })
                .semantics { stateDescription = if (expanded) "expanded" else "collapsed" }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetworkBadge(network, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(networkName(network), style = MaterialTheme.customTypography.headline3, color = n.textPrimary, maxLines = 1)
                    if (network.isTestnet) {
                        Spacer(Modifier.width(8.dp))
                        PortfolioTag(stringResource(R.string.portfolio_test_tag))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        if (network.isTestnet) null else model.shareText(group, format),
                        pluralStringResource(R.plurals.portfolio_tokens, tokenCount, tokenCount),
                    ).joinToString(" · "),
                    style = MaterialTheme.customTypography.textS,
                    color = n.textSecondary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (network.isTestnet) stringResource(R.string.portfolio_no_value) else format.fiat(group.countedFiat),
                style = if (network.isTestnet) NeonType.amountS else NeonType.amountM,
                color = if (network.isTestnet) n.textTertiary else n.textPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            ExpandChevron(expanded)
        }
        if (expanded) {
            HoldingsWell {
                tokens.take(MAX_EXPANDED).forEachIndexed { index, (holdings, fiat) ->
                    if (index > 0) RowDivider(inset = 12.dp)
                    val token = holdings.first().token
                    val amount = holdings.fold(BigDecimal.ZERO) { acc, h -> acc + h.total }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClick = { actions.onOpenToken(token.id) })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TokenOrb(token.iconUri, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(token.symbol, style = MaterialTheme.customTypography.textMBold, color = n.textPrimary)
                            if (model.allWalletsAvailable && model.scope == PortfolioScope.ALL) {
                                Spacer(Modifier.height(4.dp))
                                WalletDots(holdings.map { it.walletId }.distinct().map { model.colorOfWallet(it) })
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(format.amount(amount, token.symbol), style = NeonType.amountS.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = n.textPrimary, maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                when {
                                    network.isTestnet -> stringResource(R.string.portfolio_no_value)
                                    token.price == null -> stringResource(R.string.portfolio_no_price)
                                    else -> format.fiat(fiat)
                                },
                                style = NeonType.amountS,
                                color = n.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletRow(model: PortfolioModel, group: PortfolioGroup, format: PortfolioFormat, actions: PortfolioHomeActions) {
    val n = MaterialTheme.neon
    val wallet = model.wallet(group.key) ?: return
    val summary = model.walletSummary(wallet.id)
    val expanded = group.key in model.expanded
    val networks = summary?.networks?.size ?: group.networks.size
    Column(Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClick = { actions.onToggleRow(group.key) })
                .semantics { stateDescription = if (expanded) "expanded" else "collapsed" }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WalletAvatar(wallet.name, model.colorOfWallet(wallet.id), 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        wallet.name,
                        style = MaterialTheme.customTypography.headline3,
                        color = n.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (wallet.selected) {
                        Spacer(Modifier.width(8.dp))
                        PortfolioTag(stringResource(R.string.portfolio_selected))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOf(
                        model.shareText(group, format),
                        pluralStringResource(R.plurals.portfolio_networks, networks, networks),
                    ).joinToString(" · "),
                    style = MaterialTheme.customTypography.textS,
                    color = n.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(format.fiat(group.countedFiat), style = NeonType.amountM, color = n.textPrimary, maxLines = 1)
                summary?.freshness?.let {
                    Spacer(Modifier.height(4.dp))
                    FreshnessLine(it, model.nowMillis)
                }
            }
            Spacer(Modifier.width(6.dp))
            ExpandChevron(expanded)
        }
        if (expanded) {
            HoldingsWell {
                group.holdings.take(MAX_EXPANDED).forEachIndexed { index, holding ->
                    if (index > 0) RowDivider(inset = 12.dp)
                    HoldingRow(
                        model = model,
                        holding = holding,
                        format = format,
                        showToken = true,
                        onClick = { actions.onOpenToken(holding.token.id) },
                    )
                }
                if (group.holdings.isNotEmpty()) RowDivider(inset = 12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = { actions.onOpenWalletAlone(wallet.id) })
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.portfolio_open_wallet_alone, wallet.name),
                        style = MaterialTheme.customTypography.textSBold,
                        color = n.link,
                        modifier = Modifier.weight(1f),
                    )
                    PortfolioGlyph(Glyph.ARROW_RIGHT, n.link, Modifier.size(18.dp))
                }
            }
        }
    }
}

private const val MAX_EXPANDED = 5
