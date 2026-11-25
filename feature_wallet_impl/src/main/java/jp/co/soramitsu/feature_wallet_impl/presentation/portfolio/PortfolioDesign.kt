package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import jp.co.soramitsu.ui_core.component.text.FittedText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioToken
import jp.co.soramitsu.common.nexus.WalletNetworkDirectory
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.nexus.WalletNetworkTag
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.common.presentation.compose.neon.GlassCard
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.NeumorphicSurface
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.NetworkTagChip
import jp.co.soramitsu.ui_core.component.button.properties.Size as ButtonSize
import jp.co.soramitsu.common.presentation.compose.TokenBadge
import jp.co.soramitsu.ui_core.lit.LitEngravedLine
import jp.co.soramitsu.ui_core.lit.Lit
import jp.co.soramitsu.ui_core.lit.LitLayout
import jp.co.soramitsu.ui_core.lit.NameAndFigures
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.unit.Constraints
import jp.co.soramitsu.ui_core.lit.LitDot
import jp.co.soramitsu.ui_core.lit.LocalLitPalette
import jp.co.soramitsu.ui_core.lit.litCoin
import jp.co.soramitsu.ui_core.lit.litPlate
import jp.co.soramitsu.ui_core.lit.litWell
import jp.co.soramitsu.ui_core.theme.customTypography

/** Colours of the portfolio surfaces that the theme's tokens do not define (portfolio-view SPEC §3.1). */
internal object PortfolioPalette {
    /**
     * Chart slots, red to ink: told apart by lightness, so they hold for colour-blind eyes; colour follows the
     * entity. By night the ink is ivory and the reds are lifted off the charcoal.
     */
    private val daySlots = listOf(
        Color(0xFFDA2530),
        Color(0xFF3A3836),
        Color(0xFF8D8780),
        Color(0xFF86131B),
        Color(0xFFB9B2A6),
    )
    private val nightSlots = listOf(
        Color(0xFFE2452C),
        Color(0xFFF8F4EE),
        Color(0xFF8D8780),
        Color(0xFFA8382C),
        Color(0xFF8C857A),
    )

    fun slots(dark: Boolean): List<Color> = if (dark) nightSlots else daySlots

    fun other(dark: Boolean): Color = if (dark) Color(0xFF6A645C) else Color(0xFF8A847A)

    fun slot(index: Int?, dark: Boolean): Color = index?.let { slots(dark).getOrNull(it) } ?: other(dark)

    /** SORA's red (the logo and the XOR icon) and the shading of the hero's planet. */
    val soraRed = Color(0xFFE3232C)
    val soraLight = Color(0xFFF2403F)
    val soraDeep = Color(0xFF9E1620)
    val soraEdge = Color(0xFF5E0A10)
    val soraGlow = Color(0xFFF2403F)
    val rimNight = Color(0xFF8AF3FF)

    val nightTop = Color(0xFF08070F)
    val nightBottom = Color(0xFF1A0A14)
    val dawnTop = Color(0x4DFFFFFF)
    val dawnBottom = Color(0x1AFFFFFF)
    val dawnInk = Color(0xFF232220)
    val hazeViolet = Color(0xFF6C3BFF)
    val hazeCyan = Color(0xFF22D3EE)
    val starlight = Color(0xFFFFE9E4)
}

internal val isDarkTheme: Boolean
    @Composable get() = MaterialTheme.neon.isDark

/** The same proper names the home and the Send/Receive network choices use; never translated. */
internal fun networkName(network: WalletNetworkId): String =
    WalletNetworkDirectory.current.info(network).name

private fun networkMonogram(network: WalletNetworkId): String = when (network) {
    WalletNetworkId.SORA2 -> "S2"
    WalletNetworkId.MINAMOTO -> "SN"
    WalletNetworkId.TAIRA -> "TA"
}

/** Line glyphs drawn on a 24-unit grid, so the screen needs no new drawable resources. */
internal enum class Glyph { SEND, RECEIVE, MOVE, CHEVRON_DOWN, CHEVRON_RIGHT, INFO, CHECK, CLOCK, WARNING, OFFLINE, BACK, ARROW_RIGHT }

@Composable
internal fun PortfolioGlyph(glyph: Glyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) { drawGlyph(glyph, SolidColor(tint), null) }
}

/** A glyph for a button's icon slot, tinted by the button. */
@Composable
internal fun rememberGlyphPainter(glyph: Glyph): Painter = remember(glyph) { GlyphPainter(glyph) }

private class GlyphPainter(private val glyph: Glyph) : Painter() {
    private var filter: ColorFilter? = null

    override val intrinsicSize: Size = Size(GLYPH_PX, GLYPH_PX)

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        filter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() = drawGlyph(glyph, SolidColor(Color.Black), filter)

    private companion object {
        const val GLYPH_PX = 60f
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, brush: Brush, filter: ColorFilter?) {
    val u = size.minDimension / 24f
    val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(vararg pts: Pair<Float, Float>) {
        val path = Path()
        pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u) }
        drawPath(path, brush, style = stroke, colorFilter = filter)
    }
    fun ring(radius: Float, x: Float, y: Float) =
        drawCircle(brush, radius = radius * u, center = p(x, y), style = stroke, colorFilter = filter)
    fun dot(x: Float, y: Float) = drawCircle(brush, radius = 1.2f * u, center = p(x, y), colorFilter = filter)
    when (glyph) {
        Glyph.SEND -> {
            line(7f to 17f, 17f to 7f)
            line(9f to 7f, 17f to 7f, 17f to 15f)
        }
        Glyph.RECEIVE -> {
            line(17f to 7f, 7f to 17f)
            line(7f to 9f, 7f to 17f, 15f to 17f)
        }
        Glyph.MOVE -> {
            ring(2.4f, 5.5f, 17.5f)
            ring(2.4f, 18.5f, 6.5f)
            val path = Path().apply {
                moveTo(7.3f * u, 15.8f * u)
                cubicTo(10.5f * u, 8.5f * u, 13.5f * u, 15.5f * u, 16.7f * u, 8.2f * u)
            }
            drawPath(path, brush, style = stroke, colorFilter = filter)
        }
        Glyph.CHEVRON_DOWN -> line(6f to 9.5f, 12f to 15.5f, 18f to 9.5f)
        Glyph.CHEVRON_RIGHT -> line(9.5f to 6f, 15.5f to 12f, 9.5f to 18f)
        Glyph.INFO -> {
            ring(9f, 12f, 12f)
            line(12f to 11f, 12f to 16.5f)
            dot(12f, 7.6f)
        }
        Glyph.CHECK -> line(5f to 12.5f, 10f to 17f, 19f to 7.5f)
        Glyph.CLOCK -> {
            ring(9f, 12f, 12f)
            line(12f to 7f, 12f to 12f, 15.5f to 14f)
        }
        Glyph.WARNING -> {
            line(12f to 4f, 21f to 19.5f, 3f to 19.5f, 12f to 4f)
            line(12f to 10f, 12f to 14f)
            dot(12f, 16.8f)
        }
        Glyph.OFFLINE -> {
            ring(9f, 12f, 12f)
            line(5.6f to 5.6f, 18.4f to 18.4f)
        }
        Glyph.BACK -> line(15f to 5f, 8f to 12f, 15f to 19f)
        Glyph.ARROW_RIGHT -> {
            line(5f to 12f, 19f to 12f)
            line(13f to 6f, 19f to 12f, 13f to 18f)
        }
    }
}

/** The Polkaswap flower: the app's own vector and the only swap glyph on these screens. */
@Composable
internal fun PolkaswapFlower(modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painter = painterResource(R.drawable.ic_polkaswap_40),
        contentDescription = null,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier,
    )
}

/** A network's badge, as a printed mark: SORA2 wears the SORA mark; any other network its monogram in ink. */
@Composable
internal fun NetworkBadge(network: WalletNetworkId, size: Dp, modifier: Modifier = Modifier) {
    if (network == WalletNetworkId.SORA2) {
        SoraMark(size, modifier)
        return
    }
    val lit = LocalLitPalette.current
    Box(
        modifier = modifier
            .size(size)
            .litCoin(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = networkMonogram(network),
            color = lit.redText,
            maxLines = 1,
            style = MaterialTheme.customTypography.textXSBold.copy(
                fontSize = (size.value * 0.34f).sp,
                lineHeight = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** The initial identifies the wallet and the small dot matches its chart legend. */
@Composable
internal fun WalletAvatar(name: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .litCoin(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = LocalLitPalette.current.redText,
            style = MaterialTheme.customTypography.headline3.copy(fontSize = (size.value * 0.42f).sp, lineHeight = (size.value * 0.5f).sp),
            maxLines = 1,
        )
        LitDot(color, Modifier.align(Alignment.BottomEnd).padding(2.dp), diameter = size * .18f)
    }
}

/** The SORA mark as the logo is: a red disc with 天 in white. "All wallets" and the SORA2 network wear it. */
@Composable
internal fun SoraMark(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000),
        contentDescription = null, modifier = modifier.size(size).litCoin())
}

/** One small dot per wallet that holds a token. */
@Composable
internal fun WalletDots(colors: List<Color>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        colors.forEach { color -> LitDot(color, diameter = 7.dp) }
    }
}

@Composable
internal fun TokenOrb(token: PortfolioToken, size: Dp, modifier: Modifier = Modifier) {
    TokenBadge(token.id, token.symbol, token.iconUri, size, modifier.litCoin())
}

/** Readiness chip: Swap-ready (with the flower) or the directory's Testnet tag. "No route" has no chip. */
@Composable
internal fun ReadinessChip(ready: Boolean, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    if (ready) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(11.dp))
                .background(n.glassSolid)
                .border(1.dp, n.polkaswapBrand.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PolkaswapFlower(Modifier.size(12.dp), tint = n.polkaswapText)
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.portfolio_swap_ready),
                color = n.polkaswapText,
                style = MaterialTheme.customTypography.textXSBold,
                maxLines = 1,
            )
        }
    } else {
        NetworkTagChip(WalletNetworkTag.TESTNET, modifier)
    }
}

/** Uppercase overline (HUD label). */
@Composable
internal fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.neon.textSecondary) {
    Text(
        text = text.uppercase(),
        color = color,
        style = NeonType.eyebrow,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The app's card with a column inside. */
@Composable
internal fun PortfolioCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ContentCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** Lays this out larger than its slot by these margins, without moving what is round it. */
internal fun Modifier.bleed(horizontal: Dp, vertical: Dp): Modifier = layout { measurable, constraints ->
    val h = horizontal.roundToPx()
    val v = vertical.roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + 2 * h, minWidth = 0))
    val width = (placeable.width - 2 * h).coerceAtLeast(0)
    val height = (placeable.height - 2 * v).coerceAtLeast(0)
    layout(width, height) { placeable.place(-h, -v) }
}

/** A block of rows inside a sheet. */
@Composable
internal fun SheetCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = Lit.SheetRadius) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun RowDivider(modifier: Modifier = Modifier, inset: Dp = 16.dp) {
    LitEngravedLine(modifier.padding(horizontal = inset))
}

/** Info or warning banner with a coloured edge. */
@Composable
internal fun PortfolioBanner(
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val n = MaterialTheme.neon
    val edge = if (warning) n.redText else n.textSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .litWell(radius = 12.dp, depth = 2.2.dp)
            .padding(14.dp),
    ) {
        PortfolioGlyph(if (warning) Glyph.WARNING else Glyph.INFO, edge, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.customTypography.paragraphS, color = n.textPrimary)
            content()
        }
    }
}

/** A round soft pad with an icon (48 dp target). */
@Composable
internal fun NeuIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val target = maxOf(size, 48.dp)
    Box(
        modifier = modifier
            .size(target)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        NeumorphicSurface(shape = CircleShape, interactionSource = interaction, modifier = Modifier.size(size)) {
            icon()
        }
    }
}

/**
 * Bottom sheet as the app's other sheets: scrim, solid body, a sheet's top corners, grabber, title and
 * Close, scrolling body; at most 88 % of the screen.
 */
@Composable
internal fun PortfolioSheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val n = MaterialTheme.neon
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val dark = n.isDark
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(n.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                // The sheet stands on the keyboard when one is up, and is measured against what is left above
                // it: an amount being typed and the action under it are never behind the keys.
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val maxSheetHeight = maxHeight * 0.88f
            val shape = RoundedCornerShape(topStart = Lit.SheetRadius, topEnd = Lit.SheetRadius)
            AnimatedVisibility(visible = shown, enter = slideInVertically { it / 3 } + fadeIn()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = LitLayout.ContentMaxWidth)
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight)
                        .clip(shape)
                        .background(n.glassSolid)
                        .border(1.dp, n.hairline, shape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .navigationBarsPadding(),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(n.textTertiary.copy(alpha = 0.6f))
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 8.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.customTypography.headline2,
                            color = n.textPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() },
                        )
                        NeonButton(
                            size = ButtonSize.ExtraSmall,
                            variant = NeonVariant.Tertiary,
                            text = stringResource(R.string.common_close),
                            onClick = onDismiss,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * A selectable row inside a sheet (radio semantics by default), in the app's accent when selected.
 *
 * A figure is never broken across lines and a name is never cut to make room for one: the figures stand beside the
 * name where the whole name fits beside them in its two lines, and on a line of their own under it where it does not
 * (a 320 dp screen, larger text, a long amount, two long wallet names that differ only at the end).
 */
@Composable
internal fun SheetRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailingTop: String? = null,
    trailingBottom: String? = null,
    role: Role = Role.RadioButton,
) {
    val n = MaterialTheme.neon
    val shape = RoundedCornerShape(14.dp)
    val titleStyle = MaterialTheme.customTypography.headline3
    val subtitleStyle = MaterialTheme.customTypography.textS
    val shownSubtitle = subtitle?.takeIf { it.isNotBlank() }
    val measurer = rememberTextMeasurer()
    // Either line can be what tells two rows apart (two holdings on one network differ by their wallet, in the
    // subtitle): the figures stay beside them only where both are whole in their two lines, and no word of them is
    // broken across a line ("Saving" / "s").
    val nameFits: (Int, IntrinsicMeasurable) -> Boolean = remember(title, shownSubtitle, titleStyle, subtitleStyle, measurer) {
        { width, _ ->
            fun whole(text: String, style: TextStyle): Boolean {
                val layout = measurer.measure(
                    text = text,
                    style = style,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = SHEET_ROW_LINES,
                    constraints = Constraints(maxWidth = width),
                )
                return !layout.hasVisualOverflow && !layout.breaksAWord(text)
            }
            whole(title, titleStyle) && (shownSubtitle == null || whole(shownSubtitle, subtitleStyle))
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            // Raised at rest, let in when it is the current choice.
            .then(if (selected) Modifier.litWell(radius = 14.dp, depth = 2.6.dp).border(1.dp, n.redText, shape) else Modifier.litPlate(radius = 14.dp, elevation = 8.dp))
            .clip(shape)
            .clickable(role = role, onClick = onClick)
            .semantics { if (role == Role.RadioButton) this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        NameAndFigures(
            modifier = Modifier.fillMaxWidth(),
            leading = leading,
            belowGap = 2.dp,
            nameFits = nameFits,
            name = {
                Text(title, style = titleStyle, color = n.textPrimary, maxLines = SHEET_ROW_LINES, overflow = TextOverflow.Ellipsis)
            },
            below = {
                shownSubtitle?.let {
                    Text(it, style = subtitleStyle, color = n.textSecondary, maxLines = SHEET_ROW_LINES, overflow = TextOverflow.Ellipsis)
                }
            },
            figures = {
                if (trailingTop != null || trailingBottom != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        trailingTop?.let {
                            FittedText(AnnotatedString(it), NeonType.amountM.copy(color = n.textPrimary, textAlign = TextAlign.End), maxLines = 2)
                        }
                        trailingBottom?.let {
                            FittedText(AnnotatedString(it), NeonType.amountS.copy(color = n.textSecondary, textAlign = TextAlign.End))
                        }
                    }
                }
            },
        )
    }
}

/** A sheet row's name and the line under it may take two lines each; past that they would be cut, so the figures make
 * room for them instead. */
private const val SHEET_ROW_LINES = 2

/**
 * Whether a line of this layout ends inside a word of a script that writes spaces between words ("Saving" / "s").
 * Japanese, Chinese, Korean and Thai lines may end between any two characters.
 */
private fun TextLayoutResult.breaksAWord(text: String): Boolean = (0 until lineCount - 1).any { line ->
    val end = getLineEnd(line)
    end in 1 until text.length && text[end - 1].isWordPart() && text[end].isWordPart()
}

private fun Char.isWordPart(): Boolean = isLetterOrDigit() && Character.UnicodeScript.of(code) !in UNSPACED_SCRIPTS

private val UNSPACED_SCRIPTS = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
    Character.UnicodeScript.THAI,
)

/** A labelled value row (key on the left, value on the right). */
@Composable
internal fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.customTypography.textS, color = n.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.customTypography.textSBold, color = n.textPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** A check line for the Polkaswap card: a tick when the condition holds, an info mark otherwise. */
@Composable
internal fun CheckLine(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PortfolioGlyph(if (ok) Glyph.CHECK else Glyph.INFO, if (ok) n.textPrimary else n.redText, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.customTypography.paragraphS, color = n.textPrimary)
    }
}
