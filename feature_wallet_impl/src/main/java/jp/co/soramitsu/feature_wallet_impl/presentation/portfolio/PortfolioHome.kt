package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import jp.co.soramitsu.common.presentation.compose.components.LoadingRows
import jp.co.soramitsu.common.presentation.compose.components.NothingFoundText
import android.os.Build
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.nexus.WalletNetworkTag
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.neonGlass
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyTags
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyChip
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxTier
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
import jp.co.soramitsu.ui_core.component.text.FittedText
import jp.co.soramitsu.ui_core.lit.Lit
import jp.co.soramitsu.ui_core.lit.LitDot
import jp.co.soramitsu.ui_core.lit.NameAndFigures
import jp.co.soramitsu.ui_core.lit.litPad
import jp.co.soramitsu.ui_core.lit.litPress
import jp.co.soramitsu.ui_core.lit.litRise
import jp.co.soramitsu.ui_core.lit.litPlate
import jp.co.soramitsu.ui_core.lit.litWell
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlin.math.min
import kotlin.math.roundToInt
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

internal fun PortfolioModel.colorOfWallet(walletId: String, dark: Boolean): Color = PortfolioPalette.slot(walletSlots[walletId], dark)

internal fun PortfolioModel.colorOfGroup(key: String?, dark: Boolean): Color =
    if (key == null || key == OTHER_KEY) PortfolioPalette.other(dark) else PortfolioPalette.slot(slots[key], dark)

/** How far the hero card overlaps the sky: the ring's front passes just above the card. */
internal val HERO_CARD_OVERLAP = 10.dp

/** What passes behind the hero's sheet: the sky's own picture, and how far the page has scrolled, in dp. */
internal class HeroBackdrop(val sky: GraphicsLayer, val scrollDp: () -> Float)

/**
 * The hero's sheet of glass: the largest, standing highest, the ball's red falling on its top edge and the window's
 * streaks sliding over it as the phone turns. The ball and its ring lag when the page scrolls, so they pass behind
 * this sheet's top edge and show through the glass, blurred. It rises into place the first time it is seen, and a
 * band of fresh light crosses it then and each time a read finishes.
 */
@Composable
private fun HeroPlate(reading: Boolean, backdrop: HeroBackdrop?, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Lit.SheetRadius)
    val fx = LocalFx.current
    val dark = isDarkTheme
    val enter = remember { Animatable(if (fx.litMotion) 0f else 1f) }
    val sweep = remember { Animatable(if (fx.litMotion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (fx.litMotion) {
            launch { sweep.animateTo(1f, SHEEN_SWEEP) }
            enter.animateTo(1f, tween(340, easing = CubicBezierEasing(0.16f, 0.84f, 0.3f, 1f)))
        }
    }
    OnReadFinished(reading) {
        if (fx.litMotion) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, SHEEN_SWEEP)
        }
    }
    // A live blur costs a pass: only on Android 12 and later, on phones that draw the decorative motion, by day
    // (by night the sheet is not glass but matte).
    val blurred = rememberGraphicsLayer()
    val behind: (DrawScope.() -> Unit)? = if (
        backdrop != null && !dark && fx.tier == FxTier.FULL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        {
            // At rest nothing lies behind the sheet: the ball comes under it as the page scrolls.
            if (backdrop.scrollDp() > 4f) {
                val over = BACKDROP_OVERSCAN.toPx()
                val reach = min(size.height, (HERO_CARD_OVERLAP + SkyGeometry.HEIGHT * SkyGeometry.SCROLL_LAG).toPx())
                val left = over - HERO_CARD_INSET.toPx()
                val top = over - (SkyGeometry.HEIGHT - HERO_CARD_OVERLAP).toPx()
                if (blurred.renderEffect == null) {
                    blurred.renderEffect = BlurEffect(BACKDROP_BLUR.toPx(), BACKDROP_BLUR.toPx(), TileMode.Clamp)
                }
                // Recorded with a density of its own: inside, this scope's density would refer to itself.
                blurred.record(
                    Density(density, fontScale),
                    layoutDirection,
                    IntSize((size.width + 2f * over).roundToInt(), (reach + 2f * over).roundToInt()),
                ) {
                    translate(left, top) { drawLayer(backdrop.sky) }
                }
                translate(-over, -over) { drawLayer(blurred) }
            }
        }
    } else {
        null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = (1f - enter.value) * 12.dp.toPx()
                alpha = (enter.value * 2.5f).coerceAtMost(1f)
            }
            .litPlate(
                radius = Lit.SheetRadius,
                elevation = 18.dp,
                sheen = { sweep.value },
                backdrop = behind,
            )
            .clip(shape),
    ) { content() }
}

private val SHEEN_SWEEP = tween<Float>(1050, easing = CubicBezierEasing(0.3f, 0.1f, 0.3f, 1f))
private val BACKDROP_BLUR = 8.dp
private val BACKDROP_OVERSCAN = 24.dp

/** The hero card's margin from the screen's sides. */
private val HERO_CARD_INSET = 16.dp

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
    covered: Boolean = false,
    scrollDp: () -> Float = { 0f },
    pullDp: () -> Float = { 0f },
) {
    val dark = isDarkTheme
    val sky = rememberGraphicsLayer()
    val backdrop = remember(sky, scrollDp) { HeroBackdrop(sky, scrollDp) }
    Box(modifier.fillMaxWidth()) {
        PortfolioSky(
            fan = if (model.empty) emptyList() else model.fan,
            colorOf = { key -> model.colorOfGroup(key, dark) },
            highlighted = model.highlighted,
            tilt = tilt,
            onSliceTap = actions::onLegend,
            heroVisible = heroVisible,
            covered = covered,
            reading = model.isReading,
            scrollDp = scrollDp,
            pullDp = pullDp,
            capture = sky,
        ) {
            ScopeChip(
                model = model,
                onClick = actions::onScopeChip,
                // Up to the scan button and no further: a long wallet name, or "All wallets" in Japanese under
                // larger text, takes the room the row has before it is cut short.
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, end = SCOPE_CHIP_END, top = SkyGeometry.TOP_ROW),
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
        Box(Modifier.padding(top = SkyGeometry.HEIGHT - HERO_CARD_OVERLAP, start = HERO_CARD_INSET, end = HERO_CARD_INSET)) {
            if (model.empty) {
                EmptyCard(model, actions, backdrop)
            } else {
                PortfolioHeroCard(model, format, actions, backdrop)
            }
        }
    }
}

/** The scan button at the row's other end (48 dp and its 12 dp margin) and a gap before it. */
private val SCOPE_CHIP_END = 72.dp

/** The widest the scope chip gets where the row would let it grow further (a tablet). */
private val SCOPE_CHIP_WIDEST = 320.dp

@Composable
private fun ScopeChip(model: PortfolioModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val all = model.scope == PortfolioScope.ALL
    val wallet = model.selectedWallet
    val title = if (all) stringResource(R.string.portfolio_all_wallets) else wallet?.name.orEmpty()
    val label = stringResource(R.string.portfolio_scope_label, title)
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.minimumInteractiveComponentSize()) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .widthIn(max = SCOPE_CHIP_WIDEST)
                .neonGlass(shape)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (all || wallet == null) {
                SoraMark(34.dp)
            } else {
                WalletAvatar(wallet.name, model.colorOfWallet(wallet.id, isDarkTheme), 34.dp)
            }
            Spacer(Modifier.width(10.dp))
            // "All wallets" in Japanese under larger text, or a long wallet name, is set a little smaller before
            // anything of it is cut.
            FittedText(
                text = AnnotatedString(title),
                style = MaterialTheme.customTypography.headline3.copy(color = n.textPrimary),
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
private fun EmptyCard(model: PortfolioModel, actions: PortfolioHomeActions, backdrop: HeroBackdrop) {
    val n = MaterialTheme.neon
    HeroPlate(model.isReading, backdrop) {
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
private fun PortfolioHeroCard(model: PortfolioModel, format: PortfolioFormat, actions: PortfolioHomeActions, backdrop: HeroBackdrop) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val dark = isDarkTheme
    HeroPlate(model.isReading, backdrop) {
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
                            color = model.colorOfGroup(slice.key, dark),
                            selected = model.highlighted == key,
                            onClick = { actions.onLegend(key) },
                        )
                    }
                }
                if (largeText) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
                } else {
                    // Scrolling clips: the row runs to the sheet's edges, with room for the chips' shadows.
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
            val measurer = rememberTextMeasurer(cacheSize = TOTAL_GLYPHS)
            // The total at its full size where the card has room for it, and smaller, all of it together, where it
            // does not (a large balance on a 320 dp screen or under larger text): never cut, never on two lines.
            BoxWithConstraints {
                val room = constraints.maxWidth
                val scale = remember(symbol, whole, cents, room, measurer) {
                    fitTotal(measurer, symbol + whole, cents, room)
                }
                if (scale != null) {
                    RollingTotal(
                        main = symbol + whole,
                        cents = cents,
                        big = TOTAL_SIZE * scale,
                        small = CENTS_SIZE * scale,
                        mainColor = if (stale) n.textSecondary else n.textPrimary,
                        centsColor = n.textSecondary,
                        rollsUp = (totals.change) >= 0,
                    )
                } else {
                    // Not even its smallest size fits on one line: the whole total, on two, rather than any digit cut.
                    val ink = if (stale) n.textSecondary else n.textPrimary
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = TOTAL_SIZE * TOTAL_LEAST_SCALE, color = ink)) { append(symbol + whole) }
                            withStyle(SpanStyle(fontSize = CENTS_SIZE * TOTAL_LEAST_SCALE, color = n.textSecondary)) { append(cents) }
                        },
                        style = TOTAL_STYLE.copy(lineHeight = TextUnit.Unspecified),
                    )
                }
            }
        } else {
            NoTotalDash()
        }
        if (change != null) {
            ChangeLine(totals.change, change, format)
        }
    }
}

/**
 * How much of its full size the total takes to fit [room] pixels, or null where not even its smallest size fits (an
 * enormous balance in a window far narrower than a phone). The total is drawn a character at a time, each in a box of
 * whole pixels, so it is measured that way; and as larger text scales large type less than small type, each
 * candidate size is measured rather than worked out from the full one.
 */
private fun fitTotal(measurer: TextMeasurer, main: String, cents: String, room: Int): Float? {
    if (room == Constraints.Infinity) return 1f
    fun width(scale: Float): Int {
        fun glyphs(text: String, size: TextUnit) =
            text.sumOf { measurer.measure(it.toString(), TOTAL_STYLE.copy(fontSize = size), maxLines = 1, softWrap = false).size.width }
        return glyphs(main, TOTAL_SIZE * scale) + glyphs(cents, CENTS_SIZE * scale)
    }
    var scale = 1f
    repeat(TOTAL_FIT_TRIES) {
        val wide = width(scale)
        if (wide <= room) return scale
        if (scale <= TOTAL_LEAST_SCALE) return null
        // At least half a scaled pixel smaller each time, so it settles; never below the least the total is set at.
        scale = minOf(scale * room / wide, scale - 0.5f / TOTAL_SIZE.value).coerceAtLeast(TOTAL_LEAST_SCALE)
    }
    return scale.takeIf { width(it) <= room }
}

/**
 * The total. When it changes, only the digits that changed roll, like a counter: digits have one width, so the
 * rest stay still.
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
    val base = TOTAL_STYLE
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
    // One character per box, in the order they are read: a figure reads left to right in every language, and a row
    // in a right-to-left layout would otherwise show "$12.34" as "43.21$".
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        RollingRow(text, mainLength, big, small, mainColor, centsColor, rollsUp, base)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun RollingRow(
    text: String,
    mainLength: Int,
    big: TextUnit,
    small: TextUnit,
    mainColor: Color,
    centsColor: Color,
    rollsUp: Boolean,
    base: TextStyle,
) {
    Row {
        text.forEachIndexed { index, char ->
            val inMain = index < mainLength
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val direction = if (rollsUp) 1 else -1
                        (slideInVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it * direction * 5 / 8 } + fadeIn()) togetherWith
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

/**
 * ▲ +$148.20 · +1.2% · 24h: arrow and sign always, the app's up and down colours, in a shallow well whose floor
 * takes the direction's colour.
 */
@Composable
private fun ChangeLine(change: Double, fraction: Double, format: PortfolioFormat) {
    val n = MaterialTheme.neon
    val up = change >= 0
    val color = if (up) n.textPrimary else n.redText
    // The whole line or none of it: on a narrow screen or under larger text it is set smaller rather than losing
    // its percentage and its "24h".
    FittedText(
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
        modifier = Modifier
            .padding(top = 6.dp)
            .litWell(radius = 12.dp, depth = 1.8.dp)
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
            val excluded = totals.hidden + totals.unpriced + totals.testnet + totals.unread
            listOfNotNull(
                model.pricesAsOf?.let { stringResource(R.string.portfolio_prices_as_of, pricesTime(it)) },
                if (excluded > 0) stringResource(R.string.portfolio_exclusions) else null,
            ).joinToString(" · ").ifEmpty { null }
        }
        else -> noTotalText(model.noTotal)
    } ?: return
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(R.string.portfolio_how_we_count), role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortfolioGlyph(Glyph.INFO, n.textTertiary, Modifier.size(16.dp))
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

/**
 * LIVE · 31s (a dot of ink in the gain colour), SAVED · 2h, PARTIAL, READING, OFFLINE · 3m. LIVE is said only while
 * the node answers; what was read before shows as saved, with its age.
 */
@Composable
internal fun FreshnessChip(freshness: WalletFreshness, nowMillis: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val label = freshnessLabel(freshness, nowMillis)
    val color = when (freshness.kind) {
        FreshnessKind.LIVE -> n.textPrimary
        FreshnessKind.PARTIAL -> n.error
        FreshnessKind.SAVED, FreshnessKind.READING, FreshnessKind.OFFLINE -> n.textSecondary
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 32.dp)
            .litWell(radius = 12.dp, depth = 1.8.dp)
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.portfolio_sync_status), role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (freshness.kind) {
            FreshnessKind.LIVE -> LitDot(n.textPrimary, diameter = 7.dp)
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
    // What is shown without the node was read some time ago: saved and offline numbers say how long.
    val aged = freshness.kind == FreshnessKind.LIVE || freshness.kind == FreshnessKind.SAVED || freshness.kind == FreshnessKind.OFFLINE
    val age = freshness.updatedAtMillis?.let { ageLabel(nowMillis - it) }
    return if (age != null && aged) "$state · $age" else state
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
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .litPress(pressed, motion = !LocalFx.current.reduceMotion)
                // Raised at rest, let in when selected.
                .then(if (selected) Modifier.litWell(radius = 12.dp, depth = 2.2.dp) else Modifier.litPlate(radius = 12.dp, elevation = 6.dp))
                .clip(shape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    this.selected = selected
                }
                .padding(start = 13.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LitDot(color, diameter = 8.dp)
            Spacer(Modifier.width(9.dp))
            Text(label, style = MaterialTheme.customTypography.textSBold, color = n.textPrimary, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text(percent, style = NeonType.amountS.copy(fontWeight = FontWeight.SemiBold), color = n.textSecondary, maxLines = 1)
        }
    }
}

/** Send · Receive · Move · Swap, each a soft raised pad of the sheet's own white with its glyph in red. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(actions: PortfolioHomeActions, largeText: Boolean) {
    val red = MaterialTheme.neon.redText
    val items: @Composable (Modifier) -> Unit = { each ->
        QuickAction(stringResource(R.string.common_send), order = 0, modifier = each, onClick = actions::onSend) {
            PortfolioGlyph(Glyph.SEND, red, Modifier.size(24.dp))
        }
        QuickAction(stringResource(R.string.common_receive), order = 1, modifier = each, onClick = actions::onReceive) {
            PortfolioGlyph(Glyph.RECEIVE, red, Modifier.size(24.dp))
        }
        QuickAction(stringResource(R.string.portfolio_move), order = 2, modifier = each, onClick = actions::onMove) {
            PortfolioGlyph(Glyph.MOVE, red, Modifier.size(24.dp))
        }
        QuickAction(stringResource(R.string.portfolio_swap), order = 3, modifier = each, onClick = actions::onSwap) {
            PolkaswapFlower(Modifier.size(24.dp), tint = red)
        }
    }
    if (largeText) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { items(Modifier) }
    } else {
        // Each action may take a quarter of the row and no more. With room they stand as they always did, spread
        // from edge to edge; on a 320 dp screen the four share the row instead of the last one being squeezed.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            items(Modifier.weight(1f, fill = false))
        }
    }
}

/** Pressing lets the pad into the sheet and moves its glyph down with it. The pad rises into place in turn. */
@Composable
private fun QuickAction(
    label: String,
    order: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pressed) {
        if (pressed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    Column(
        modifier = modifier
            .widthIn(min = 72.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .litRise(motion = fx.litMotion, distance = 7.dp, delayMillis = 180 + 65 * order)
                .size(width = 60.dp, height = 52.dp)
                .litPad(radius = 16.dp, elevation = 10.dp, pressed = pressed),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.graphicsLayer { translationY = if (pressed) 0.75.dp.toPx() else 0f }) { icon() }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            label,
            style = MaterialTheme.customTypography.textSBold,
            color = n.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Every token held across the scope's wallets and networks, in one card titled Assets. */
@Composable
internal fun PortfolioList(model: PortfolioModel, format: PortfolioFormat, actions: PortfolioHomeActions, modifier: Modifier = Modifier) {
    val groups = model.groups.filter { it.holdings.isNotEmpty() }
    PortfolioCard(modifier) {
        Text(
            text = stringResource(R.string.liquid_assets),
            style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.neon.textPrimary,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() },
        )
        if (groups.isEmpty()) {
            if (model.noTotal == NoTotal.READING) {
                LoadingRows(stringResource(R.string.portfolio_reading_balances), count = 2)
            } else {
                NothingFoundText(stringResource(when {
                    model.noTotal == NoTotal.FAILED -> R.string.balance_unavailable
                    model.holdsAnything -> R.string.portfolio_nothing_listed
                    else -> R.string.portfolio_no_balance_yet
                }))
            }
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
    Box(Modifier.litRise(motion = fx.litMotion && order < 8, delayMillis = 40 * order)) { content() }
}

@Composable
private fun ExpandChevron(expanded: Boolean) {
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    PortfolioGlyph(Glyph.CHEVRON_DOWN, MaterialTheme.neon.textTertiary, Modifier.size(20.dp).rotate(angle))
}

/**
 * A row's figures: the amount held on top in bold at [amountSize], a clear step over its value, which is set small
 * and secondary under it.
 */
@Composable
private fun RowAmounts(numbers: RowNumbers, amountSize: TextUnit = TOKEN_ROW_AMOUNT_SIZE, gap: Dp = 2.dp) {
    val n = MaterialTheme.neon
    // On a line of their own the figures may take the row's whole width; past that they are set smaller, and never cut
    // silently.
    Column(horizontalAlignment = Alignment.End) {
        FittedText(AnnotatedString(numbers.main), NeonType.amountM.copy(fontSize = amountSize, color = n.textPrimary))
        numbers.detail?.let {
            Spacer(Modifier.height(gap))
            FittedText(AnnotatedString(it), NeonType.amountS.copy(color = n.textSecondary))
        }
    }
}

/** The amount on a token row of the Wallet home. */
private val TOKEN_ROW_AMOUNT_SIZE = 17.sp

/** The amount on a holding row under a token, and on Where it lives. */
private val HOLDING_ROW_AMOUNT_SIZE = 16.sp

@Composable
private fun ChangeBadge(change: Double?, format: PortfolioFormat) {
    if (change == null || kotlin.math.abs(change) < 0.0005) return
    val n = MaterialTheme.neon
    val up = change > 0
    Text(
        text = (if (up) "▲ " else "▼ ") + format.signedPercent(change),
        style = MaterialTheme.customTypography.textXSBold,
        color = if (up) n.textPrimary else n.redText,
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
    val expansionState = if (expandable) stringResource(
        if (expanded) R.string.portfolio_row_expanded else R.string.portfolio_row_collapsed
    ) else null
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
                    expansionState?.let { stateDescription = it }
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
            // The symbol and its change stay whole: where the amounts beside them would cut them short, the amounts
            // go under the name.
            NameAndFigures(
                modifier = Modifier.weight(1f),
                leading = { TokenOrb(token, 40.dp) },
                name = {
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
                },
                below = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            subtitle,
                            style = MaterialTheme.customTypography.textS,
                            color = n.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (model.scope == PortfolioScope.ALL && model.allWalletsAvailable) {
                            Spacer(Modifier.width(8.dp))
                            val dark = isDarkTheme
                            WalletDots(group.walletIds.map { model.colorOfWallet(it, dark) })
                        }
                    }
                },
                figures = { RowAmounts(format.rowNumbers(group.holdings, group.amount, token.symbol)) },
            )
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
            .litWell(radius = 14.dp, depth = 3.dp)
            .clip(RoundedCornerShape(14.dp))
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
        // The network and the wallet name are what tell two holdings apart: where the figures beside them would cut
        // either short, the figures go under them.
        NameAndFigures(
            modifier = Modifier.weight(1f),
            leading = { NetworkBadge(holding.network, 30.dp) },
            leadingGap = 10.dp,
            name = {
                Column {
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
                            LitDot(model.colorOfWallet(wallet.id, isDarkTheme), diameter = 7.dp)
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
            },
            figures = { RowAmounts(numbers, amountSize = HOLDING_ROW_AMOUNT_SIZE, gap = 3.dp) },
        )
    }
}

/** The size of the hero's total, where the card has room for it. */
private val TOTAL_SIZE = 42.sp

/** The size of its cents. */
private val CENTS_SIZE = 28.sp

/** The total's type: digits of one width, so a rolling digit moves nothing beside it. */
private val TOTAL_STYLE = NeonType.displayHero.copy(lineHeight = 50.sp, fontFeatureSettings = "tnum")

/** Sizes tried before the total settles (it nearly always fits at the first or second). */
private const val TOTAL_FIT_TRIES = 8

/** The smallest the total is set, as a share of its full size: about the size of the text under it. */
private const val TOTAL_LEAST_SCALE = 0.4f

/** The characters a total is made of (digits, a currency sign, separators), each measured once per size. */
private const val TOTAL_GLYPHS = 24
