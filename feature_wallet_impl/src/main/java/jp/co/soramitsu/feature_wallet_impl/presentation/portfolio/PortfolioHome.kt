package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.nexus.WalletNetworkTag
import jp.co.soramitsu.common.presentation.compose.neon.HeroCard
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.neonGlass
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyTags
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyChip
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FreshnessKind
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioGroup
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioReadiness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.Readiness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.WalletFreshness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.isTestnet
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.NetworkTagChip
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.TiltState
import jp.co.soramitsu.ui_core.component.button.properties.Size as ButtonSize
import jp.co.soramitsu.ui_core.lit.LitCabochon
import jp.co.soramitsu.ui_core.lit.LitLed
import jp.co.soramitsu.ui_core.lit.litPlate
import jp.co.soramitsu.ui_core.lit.litWell
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    fun onToggleRow(key: String)
    fun onOpenToken(tokenId: String, holdingKey: String? = null)
    fun onMoveHolding(holdingKey: String)
    fun onSwapHolding(holdingKey: String)
}

internal fun PortfolioModel.colorOfWallet(walletId: String): Color = PortfolioPalette.slot(walletSlots[walletId])

internal fun PortfolioModel.colorOfGroup(key: String?): Color =
    if (key == null || key == OTHER_KEY) PortfolioPalette.other else PortfolioPalette.slot(slots[key])

/** How far the hero card overlaps the sky: the ring's front passes just above the card. */
internal val HERO_CARD_OVERLAP = 10.dp

/**
 * The hero's plate: the largest, standing highest, the planet's red falling on its top edge and the window's
 * reflection sliding over its glaze as the phone turns. It rises into place the first time it is seen, and fresh
 * light crosses it then and each time a read finishes.
 */
@Composable
private fun HeroPlate(reading: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val fx = LocalFx.current
    val enter = remember { Animatable(if (fx.litMotion) 0f else 1f) }
    val sweep = remember { Animatable(if (fx.litMotion) SHEEN_SWEEP_FROM else 0f) }
    LaunchedEffect(Unit) {
        if (fx.litMotion) {
            launch { sweep.animateTo(0f, SHEEN_SWEEP) }
            enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
        }
    }
    OnReadFinished(reading) {
        if (fx.litMotion) {
            sweep.snapTo(SHEEN_SWEEP_FROM)
            sweep.animateTo(0f, SHEEN_SWEEP)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = (1f - enter.value) * 22.dp.toPx()
                alpha = (enter.value * 2.5f).coerceAtMost(1f)
            }
            .litPlate(
                radius = 26.dp,
                elevation = 18.dp,
                halo = 0.45f,
                bounce = PortfolioPalette.soraGlow,
                sheen = true,
                sheenSweep = { sweep.value },
            )
            .clip(shape),
    ) { content() }
}

private const val SHEEN_SWEEP_FROM = -1.2f
private val SHEEN_SWEEP = tween<Float>(950, easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f))

private val PortfolioModel.isReading: Boolean
    get() = noTotal == NoTotal.READING || freshness?.kind == FreshnessKind.READING

/**
 * The hero: the sky with the scope chip, and the hero card overlapping its bottom a little.
 */
@Composable
internal fun PortfolioHero(
    model: PortfolioModel,
    format: PortfolioFormat,
    tilt: TiltState,
    actions: PortfolioHomeActions,
    modifier: Modifier = Modifier,
    heroVisible: Boolean = true,
    scrollDp: () -> Float = { 0f },
    pullDp: () -> Float = { 0f },
) {
    Box(modifier.fillMaxWidth()) {
        PortfolioSky(
            fan = if (model.empty) emptyList() else model.fan,
            colorOf = { key -> model.colorOfGroup(key) },
            highlighted = model.highlighted,
            tilt = tilt,
            onSliceTap = actions::onLegend,
            heroVisible = heroVisible,
            reading = model.isReading,
            scrollDp = scrollDp,
            pullDp = pullDp,
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
        Box(Modifier.padding(top = SkyGeometry.HEIGHT - HERO_CARD_OVERLAP, start = 16.dp, end = 16.dp)) {
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

/** "Total balance" and how fresh the numbers under it are. */
@Composable
private fun HeroCardTop(model: PortfolioModel, actions: PortfolioHomeActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Overline(stringResource(R.string.portfolio_total_balance), Modifier.weight(1f))
        // While the first read runs there is no age to show yet.
        if (model.noTotal != NoTotal.READING) {
            model.freshness?.let {
                FreshnessChip(it, model.nowMillis, actions::onFreshness, Modifier.padding(end = 6.dp))
            }
        }
    }
}

@Composable
private fun EmptyCard(model: PortfolioModel, actions: PortfolioHomeActions) {
    val n = MaterialTheme.neon
    HeroPlate(model.isReading) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
        ) {
            // An empty list is only "empty" if it could be read: the state says how fresh it is.
            HeroCardTop(model, actions)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.portfolio_wallet_ready),
                style = MaterialTheme.customTypography.headline1,
                color = n.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.portfolio_wallet_ready_body),
                style = MaterialTheme.customTypography.paragraphM,
                color = n.textSecondary,
            )
            NeonButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                size = ButtonSize.Large,
                variant = NeonVariant.Primary,
                text = stringResource(R.string.common_receive),
                onClick = actions::onReceive,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortfolioHeroCard(model: PortfolioModel, format: PortfolioFormat, actions: PortfolioHomeActions) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    HeroPlate(model.isReading) {
        Column(
            Modifier
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)
        ) {
            HeroCardTop(model, actions)
            Spacer(Modifier.height(4.dp))
            TotalBlock(model, format)
            TotalCaption(model, actions::onCaption)
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
                    // Scrolling clips: the row runs to the plate's edges, with room for the chips' shadows.
                    Row(
                        Modifier
                            .bleed(horizontal = 18.dp, vertical = 12.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 12.dp),
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
private fun legendLabel(model: PortfolioModel, key: String?): String =
    if (key == null) stringResource(R.string.portfolio_other) else tokenSymbol(model, key) ?: key

private fun tokenSymbol(model: PortfolioModel, key: String): String? =
    model.groups.firstOrNull { it.key == key }?.holdings?.firstOrNull()?.token?.symbol

/** The dash that stands for a total the app does not have. */
@Composable
internal fun NoTotalDash() {
    Text(
        text = PortfolioFormat.NO_VALUE,
        style = NeonType.displayHero.copy(fontSize = TOTAL_SIZE, lineHeight = 50.sp),
        color = MaterialTheme.neon.textSecondary,
        maxLines = 1,
    )
}

@Composable
private fun noTotalText(reason: NoTotal?): String? = when (reason) {
    NoTotal.PRICE -> stringResource(R.string.tx_outcome_quote_unavailable)
    NoTotal.FAILED -> stringResource(R.string.balance_unavailable)
    NoTotal.TESTNET -> stringResource(R.string.portfolio_route_test)
    NoTotal.READING -> stringResource(R.string.portfolio_reading_balances)
    null -> null
}

/** The total, or a dash: a missing price or balance is never a zero. */
@Composable
private fun TotalBlock(model: PortfolioModel, format: PortfolioFormat) {
    val n = MaterialTheme.neon
    val totals = model.totals
    val title = stringResource(R.string.portfolio_total_balance)
    val reason = noTotalText(model.noTotal)
    val change = totals.changeFraction.takeIf { totals.known }
    val description = buildString {
        append(title).append(", ").append(if (totals.known) format.fiat(totals.fiat) else reason ?: PortfolioFormat.NO_VALUE)
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
        if (totals.known) {
            val stale = model.freshness?.kind == FreshnessKind.OFFLINE
            val (symbol, whole, cents) = format.fiatParts(totals.fiat)
            val compact = whole.length > 10
            val big = if (compact) 32.sp else TOTAL_SIZE
            val small = if (compact) 22.sp else 28.sp
            RollingTotal(
                main = symbol + whole,
                cents = cents,
                big = big,
                small = small,
                mainColor = if (stale) n.textSecondary else n.textPrimary,
                centsColor = n.textSecondary,
                rollsUp = (totals.change) >= 0,
            )
        } else {
            NoTotalDash()
        }
        if (change != null) {
            ChangeLine(totals.change, change, format)
        }
    }
}

/**
 * The total, pressed into the plate. When it changes, only the digits that changed roll, like a counter: digits
 * have one width, so the rest stay still.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun RollingTotal(
    main: String,
    cents: String,
    big: TextUnit,
    small: TextUnit,
    mainColor: Color,
    centsColor: Color,
    rollsUp: Boolean,
) {
    val fx = LocalFx.current
    val base = letterpress(NeonType.displayHero.copy(lineHeight = 50.sp, fontFeatureSettings = "tnum"))
    val text = main + cents
    val mainLength = main.length
    if (fx.reduceMotion) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = big, color = mainColor)) { append(main) }
                withStyle(SpanStyle(fontSize = small, color = centsColor)) { append(cents) }
            },
            style = base,
            maxLines = 1,
        )
        return
    }
    Row {
        text.forEachIndexed { index, char ->
            val inMain = index < mainLength
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val direction = if (rollsUp) 1 else -1
                        (slideInVertically(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it * direction * 5 / 8 } + fadeIn()) togetherWith
                            (slideOutVertically(tween(180)) { -it * direction * 5 / 8 } + fadeOut(tween(140)))
                    },
                    label = "rollingDigit",
                    modifier = Modifier.alignByBaseline(),
                ) { shown ->
                    Text(
                        text = shown.toString(),
                        style = base.copy(
                            fontSize = if (inMain) big else small,
                            color = if (inMain) mainColor else centsColor,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Text pressed into a plate: the light catches the far edge of each stroke. */
@Composable
private fun letterpress(style: TextStyle): TextStyle {
    val dark = MaterialTheme.neon.isDark
    val shadow = with(LocalDensity.current) {
        Shadow(
            color = if (dark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.9f),
            offset = Offset(0.35.dp.toPx(), 0.7.dp.toPx()),
            blurRadius = 0f,
        )
    }
    return style.copy(shadow = shadow)
}

/**
 * ▲ +$148.20 · +1.2% · 24h: arrow and sign always, the app's up and down colours, in a shallow well whose floor
 * takes the direction's colour.
 */
@Composable
private fun ChangeLine(change: Double, fraction: Double, format: PortfolioFormat) {
    val n = MaterialTheme.neon
    val up = change >= 0
    val color = if (up) n.success else n.error
    val floor = litTint(color)
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
        modifier = Modifier
            .padding(top = 6.dp)
            .litWell(radius = 18.dp, depth = 1.8.dp, floor = floor)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/**
 * Under the total: why there is none, or what it leaves out. While the first read runs the Assets
 * card says so. A tap explains how the total is counted.
 */
@Composable
private fun TotalCaption(model: PortfolioModel, onClick: () -> Unit) {
    val n = MaterialTheme.neon
    val totals = model.totals
    val text = when (model.noTotal) {
        NoTotal.READING -> null
        null -> {
            val parts = buildList {
                if (totals.hidden > 0) add(stringResource(R.string.portfolio_excluded_hidden, totals.hidden))
                if (totals.unpriced > 0) add(stringResource(R.string.portfolio_excluded_unpriced, totals.unpriced))
                if (totals.testnet > 0) add(stringResource(R.string.portfolio_excluded_testnet, totals.testnet))
                if (totals.unread > 0) add(stringResource(R.string.portfolio_excluded_unread, totals.unread))
            }
            listOfNotNull(
                model.pricesAsOf?.let { stringResource(R.string.portfolio_prices_as_of, pricesTime(it)) },
                if (parts.isEmpty()) null else stringResource(R.string.portfolio_excluded_caption, parts.joinToString(", ")),
            ).joinToString(" · ").ifEmpty { null }
        }
        else -> noTotalText(model.noTotal)
    } ?: return
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(R.string.portfolio_how_we_count), role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortfolioGlyph(Glyph.INFO, n.info, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.customTypography.textS, color = n.textSecondary)
    }
}

/** The time of today's prices; an older price says its date too. */
@Composable
private fun pricesTime(millis: Long): String {
    val flags = DateUtils.FORMAT_SHOW_TIME or
        if (DateUtils.isToday(millis)) 0 else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    return DateUtils.formatDateTime(LocalContext.current, millis, flags)
}

/** LIVE · 31s (green dot), SAVED · 2h, PARTIAL (amber), READING, OFFLINE. */
@Composable
internal fun FreshnessChip(freshness: WalletFreshness, nowMillis: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val label = freshnessLabel(freshness, nowMillis)
    val color = when (freshness.kind) {
        FreshnessKind.LIVE -> n.textPrimary
        FreshnessKind.SAVED, FreshnessKind.PARTIAL -> n.warning
        FreshnessKind.READING, FreshnessKind.OFFLINE -> n.textSecondary
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 32.dp)
            .litWell(radius = 16.dp, depth = 1.8.dp)
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.portfolio_sync_status), role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (freshness.kind) {
            FreshnessKind.LIVE -> LitLed(n.success, diameter = 7.dp)
            FreshnessKind.SAVED, FreshnessKind.READING -> PortfolioGlyph(Glyph.CLOCK, color, Modifier.size(14.dp))
            FreshnessKind.PARTIAL -> PortfolioGlyph(Glyph.WARNING, color, Modifier.size(14.dp))
            FreshnessKind.OFFLINE -> PortfolioGlyph(Glyph.OFFLINE, color, Modifier.size(14.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = NeonType.eyebrow, maxLines = 1)
    }
}

@Composable
internal fun freshnessLabel(freshness: WalletFreshness, nowMillis: Long): String {
    val state = stringResource(
        when (freshness.kind) {
            FreshnessKind.LIVE -> R.string.portfolio_state_live
            FreshnessKind.SAVED -> R.string.portfolio_state_saved
            FreshnessKind.READING -> R.string.portfolio_state_reading
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
    val floor = litTint(color)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        if (pressed) tween(90) else spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow),
        label = "chipPress",
    )
    Box(Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .graphicsLayer {
                    val scale = 1f - 0.05f * press
                    scaleX = scale
                    scaleY = scale
                }
                // Raised at rest, pressed in when selected.
                .then(if (selected) Modifier.litWell(radius = 18.dp, depth = 2.2.dp, floor = floor) else Modifier.litPlate(radius = 18.dp, elevation = 6.dp))
                .clip(shape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    this.selected = selected
                }
                .padding(start = 13.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LitLed(color, diameter = 8.dp)
            Spacer(Modifier.width(9.dp))
            Text(label, style = MaterialTheme.customTypography.textSBold, color = n.textPrimary, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text(percent, style = NeonType.amountS.copy(fontWeight = FontWeight.SemiBold), color = n.textSecondary, maxLines = 1)
        }
    }
}

/** Send · Receive · Move · Swap, each a glass cabochon in one of the hero's colours, seated in a well. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(actions: PortfolioHomeActions, largeText: Boolean) {
    val items: @Composable () -> Unit = {
        QuickAction(stringResource(R.string.common_send), PortfolioPalette.actionSend, order = 0, onClick = actions::onSend) {
            PortfolioGlyph(Glyph.SEND, Color.White, Modifier.size(24.dp))
        }
        QuickAction(stringResource(R.string.common_receive), PortfolioPalette.actionReceive, order = 1, onClick = actions::onReceive) {
            PortfolioGlyph(Glyph.RECEIVE, Color.White, Modifier.size(24.dp))
        }
        QuickAction(stringResource(R.string.portfolio_move), PortfolioPalette.actionMove, order = 2, onClick = actions::onMove) {
            PortfolioGlyph(Glyph.MOVE, Color.White, Modifier.size(24.dp))
        }
        QuickAction(stringResource(R.string.portfolio_swap), PortfolioPalette.actionSwap, order = 3, onClick = actions::onSwap) {
            PolkaswapFlower(Modifier.size(26.dp), tint = Color.White)
        }
    }
    if (largeText) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { items() }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { items() }
    }
}

@Composable
private fun QuickAction(label: String, colors: Pair<Color, Color>, order: Int, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pressed) {
        if (pressed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    Column(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LitCabochon(
            lit = colors.first,
            deep = colors.second,
            pressed = pressed,
            reduceMotion = fx.reduceMotion,
            enterDelayMillis = if (fx.litMotion) 180 + 65 * order else null,
        ) {
            icon()
        }
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.customTypography.textSBold, color = n.textPrimary, maxLines = 1)
    }
}

/** Every token held across the scope's wallets and networks, in one card titled Assets. */
@Composable
internal fun PortfolioList(model: PortfolioModel, format: PortfolioFormat, actions: PortfolioHomeActions, modifier: Modifier = Modifier) {
    val groups = model.groups.filter { it.holdings.isNotEmpty() }
    PortfolioCard(modifier) {
        Text(
            text = stringResource(R.string.liquid_assets),
            style = letterpress(MaterialTheme.customTypography.headline2),
            color = MaterialTheme.neon.textPrimary,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() },
        )
        if (groups.isEmpty()) {
            Text(
                text = when {
                    model.noTotal == NoTotal.READING -> stringResource(R.string.portfolio_reading_balances)
                    model.noTotal == NoTotal.FAILED -> stringResource(R.string.balance_unavailable)
                    model.holdsAnything -> stringResource(R.string.portfolio_nothing_listed)
                    else -> stringResource(R.string.portfolio_no_balance_yet)
                },
                style = MaterialTheme.customTypography.paragraphS,
                color = MaterialTheme.neon.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            )
        }
        groups.forEachIndexed { index, group ->
            RiseIn(order = index) {
                Column {
                    if (index > 0) RowDivider()
                    TokenRow(model, group, format, actions)
                }
            }
        }
    }
}

/** Rises into place and fades in, once, the first time this row is seen; the first eight in turn. */
@Composable
private fun RiseIn(order: Int, content: @Composable () -> Unit) {
    val fx = LocalFx.current
    val animate = fx.litMotion && order < 8
    val rise = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(40L * order)
            rise.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    Box(
        Modifier.graphicsLayer {
            translationY = (1f - rise.value) * 10.dp.toPx()
            alpha = (rise.value * 2f).coerceAtMost(1f)
        },
    ) { content() }
}

@Composable
private fun ExpandChevron(expanded: Boolean) {
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    PortfolioGlyph(Glyph.CHEVRON_DOWN, MaterialTheme.neon.textTertiary, Modifier.size(20.dp).rotate(angle))
}

@Composable
private fun RowAmounts(numbers: RowNumbers) {
    val n = MaterialTheme.neon
    Column(horizontalAlignment = Alignment.End) {
        Text(numbers.main, style = NeonType.amountM, color = n.textPrimary, maxLines = 1)
        numbers.detail?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = NeonType.amountS, color = n.textSecondary, maxLines = 1)
        }
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
private fun TokenRow(model: PortfolioModel, group: PortfolioGroup, format: PortfolioFormat, actions: PortfolioHomeActions) {
    val n = MaterialTheme.neon
    val mainnet = group.holdings.firstOrNull { !it.network.isTestnet }
    val token = (mainnet ?: group.holdings.first()).token
    val expandable = group.holdings.size >= 2
    val expanded = expandable && group.key in model.expanded
    // The token's name, as every asset list names it ("XOR" over "SORA"); where it lives is in
    // the row's expansion.
    val subtitle = when {
        token.name.isNotBlank() && token.name != token.symbol -> token.name
        group.networks.size >= 2 -> pluralStringResource(R.plurals.portfolio_networks, group.networks.size, group.networks.size)
        model.scope == PortfolioScope.ALL && group.walletIds.size >= 2 ->
            pluralStringResource(R.plurals.portfolio_wallets, group.walletIds.size, group.walletIds.size)
        else -> networkName(group.networks.first())
    }
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
                    Text(
                        token.symbol,
                        style = MaterialTheme.customTypography.headline3,
                        color = n.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (group.testnetOnly) NetworkTagChip(WalletNetworkTag.TESTNET) else ChangeBadge(token.change24h, format)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        subtitle,
                        style = MaterialTheme.customTypography.textS,
                        color = n.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (model.scope == PortfolioScope.ALL && model.allWalletsAvailable) {
                        Spacer(Modifier.width(8.dp))
                        WalletDots(group.walletIds.map { model.colorOfWallet(it) })
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            RowAmounts(format.rowNumbers(group.holdings, group.amount, token.symbol))
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
                        onClick = { actions.onOpenToken(holding.token.id, holding.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingsWell(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth()
            .litWell(radius = 18.dp, depth = 3.dp)
            .clip(RoundedCornerShape(18.dp))
    ) { content() }
}

/** Holding row: network badge, network, wallet, its numbers and a readiness chip when actionable. */
@Composable
internal fun HoldingRow(
    model: PortfolioModel,
    holding: PortfolioHolding,
    format: PortfolioFormat,
    onClick: () -> Unit,
) {
    val n = MaterialTheme.neon
    val wallet = model.wallet(holding.walletId)
    val readiness = PortfolioReadiness.readiness(holding)
    val numbers = format.rowNumbers(listOf(holding), holding.total, holding.token.symbol)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkBadge(holding.network, 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    networkName(holding.network),
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
                    LitLed(model.colorOfWallet(wallet.id), diameter = 7.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = wallet?.name ?: networkName(holding.network),
                    style = MaterialTheme.customTypography.textS,
                    color = n.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(numbers.main, style = NeonType.amountS.copy(fontWeight = FontWeight.SemiBold), color = n.textPrimary, maxLines = 1)
            numbers.detail?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = NeonType.amountS, color = n.textSecondary, maxLines = 1)
            }
        }
    }
}

/** The size of the hero's total. */
private val TOTAL_SIZE = 42.sp
