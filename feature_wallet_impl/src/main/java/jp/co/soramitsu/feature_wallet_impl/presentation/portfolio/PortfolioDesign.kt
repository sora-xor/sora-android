package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.compose.neon.GlassCard
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.NeumorphicSurface
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.ui_core.component.button.properties.Size as ButtonSize
import jp.co.soramitsu.ui_core.component.icons.TokenIcon
import jp.co.soramitsu.ui_core.theme.customTypography

/** Colours of the portfolio surfaces that the Neon tokens do not define (portfolio-view SPEC §3.1). */
internal object PortfolioPalette {
    /** Chart slots, validated for colour-blind separation; colour follows the entity. */
    val slots = listOf(
        Color(0xFFFF009A),
        Color(0xFF4693F1),
        Color(0xFFA97416),
        Color(0xFF8B5CFF),
        Color(0xFF3CA694),
    )
    val other = Color(0xFF6F7280)

    fun slot(index: Int?): Color = index?.let { slots.getOrNull(it) } ?: other

    val polkaswap = Color(0xFFFF0066)
    val polkaswapDeep = Color(0xFFED145B)

    val nightTop = Color(0xFF0B0A14)
    val nightBottom = Color(0xFF1B0C24)
    val dawnTop = Color(0xFFFFF6FA)
    val dawnBottom = Color(0xFFFFE3EE)

    fun network(network: WalletNetworkId): Color = when (network) {
        WalletNetworkId.SORA2 -> Color(0xFFE2242F)
        WalletNetworkId.MINAMOTO -> Color(0xFF6B4BD8)
        WalletNetworkId.TAIRA -> Color(0xFF1E9C87)
    }
}

internal val isDarkTheme: Boolean
    @Composable get() = MaterialTheme.neon.isDark

/** Network names are proper names; Taira is the Nexus testnet. */
internal fun networkName(network: WalletNetworkId): String = when (network) {
    WalletNetworkId.SORA2 -> "SORA2"
    WalletNetworkId.MINAMOTO -> "Minamoto"
    WalletNetworkId.TAIRA -> "Taira"
}

private fun networkMonogram(network: WalletNetworkId): String = when (network) {
    WalletNetworkId.SORA2 -> "S2"
    WalletNetworkId.MINAMOTO -> "MI"
    WalletNetworkId.TAIRA -> "TA"
}

/** Line glyphs drawn on a 24-unit grid, so the screen needs no new drawable resources. */
internal enum class Glyph { SEND, RECEIVE, MOVE, CHEVRON_DOWN, CHEVRON_RIGHT, INFO, CHECK, CLOCK, WARNING, OFFLINE, BACK, ARROW_RIGHT }

@Composable
internal fun PortfolioGlyph(glyph: Glyph, tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(vararg pts: Pair<Float, Float>) {
            val path = Path()
            pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u) }
            drawPath(path, tint, style = stroke)
        }
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
                drawCircle(tint, radius = 2.4f * u, center = p(5.5f, 17.5f), style = stroke)
                drawCircle(tint, radius = 2.4f * u, center = p(18.5f, 6.5f), style = stroke)
                val path = Path().apply {
                    moveTo(7.3f * u, 15.8f * u)
                    cubicTo(10.5f * u, 8.5f * u, 13.5f * u, 15.5f * u, 16.7f * u, 8.2f * u)
                }
                drawPath(path, tint, style = stroke)
            }
            Glyph.CHEVRON_DOWN -> line(6f to 9.5f, 12f to 15.5f, 18f to 9.5f)
            Glyph.CHEVRON_RIGHT -> line(9.5f to 6f, 15.5f to 12f, 9.5f to 18f)
            Glyph.INFO -> {
                drawCircle(tint, radius = 9f * u, center = p(12f, 12f), style = stroke)
                line(12f to 11f, 12f to 16.5f)
                drawCircle(tint, radius = 1.2f * u, center = p(12f, 7.6f))
            }
            Glyph.CHECK -> line(5f to 12.5f, 10f to 17f, 19f to 7.5f)
            Glyph.CLOCK -> {
                drawCircle(tint, radius = 9f * u, center = p(12f, 12f), style = stroke)
                line(12f to 7f, 12f to 12f, 15.5f to 14f)
            }
            Glyph.WARNING -> {
                line(12f to 4f, 21f to 19.5f, 3f to 19.5f, 12f to 4f)
                line(12f to 10f, 12f to 14f)
                drawCircle(tint, radius = 1.2f * u, center = p(12f, 16.8f))
            }
            Glyph.OFFLINE -> {
                drawCircle(tint, radius = 9f * u, center = p(12f, 12f), style = stroke)
                line(5.6f to 5.6f, 18.4f to 18.4f)
            }
            Glyph.BACK -> line(15f to 5f, 8f to 12f, 15f to 19f)
            Glyph.ARROW_RIGHT -> {
                line(5f to 12f, 19f to 12f)
                line(13f to 6f, 19f to 12f, 13f to 18f)
            }
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

/** A network's badge: its monogram on its colour. */
@Composable
internal fun NetworkBadge(network: WalletNetworkId, size: Dp, modifier: Modifier = Modifier) {
    val color = PortfolioPalette.network(network)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.22f), lerp(color, Color.Black, 0.28f))))
            .border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = networkMonogram(network),
            color = Color.White,
            maxLines = 1,
            style = MaterialTheme.customTypography.textXSBold.copy(
                fontSize = (size.value * 0.34f).sp,
                lineHeight = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** A wallet's avatar: its initial on its slot colour (the colour of its dots and rays). */
@Composable
internal fun WalletAvatar(name: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.16f), lerp(color, Color.Black, 0.34f))))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            style = MaterialTheme.customTypography.headline3.copy(fontSize = (size.value * 0.42f).sp, lineHeight = (size.value * 0.5f).sp),
            maxLines = 1,
        )
    }
}

/** "All wallets": the Polkaswap flower on a pink badge with faint rings. */
@Composable
internal fun AllWalletsAvatar(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF2D84), Color(0xFF8C0040)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            listOf(0.56f to 0.20f, 0.74f to 0.12f, 0.90f to 0.07f).forEach { (k, a) ->
                drawCircle(Color.White.copy(alpha = a), radius = r * k, style = Stroke(width = 1.dp.toPx()))
            }
        }
        PolkaswapFlower(Modifier.size(size * 0.56f), tint = Color.White)
    }
}

/** One small dot per wallet that holds a token. */
@Composable
internal fun WalletDots(colors: List<Color>, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.neon.glassSolid
    Row(modifier, horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
        colors.forEach { color ->
            Box(
                Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(ring)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * A token's icon from the app's own icon source (the whitelist icons the asset list and asset
 * details show); a token without one shows the app's default token icon, as everywhere else.
 */
@Composable
internal fun TokenOrb(iconUri: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        TokenIcon(uri = iconUri ?: DEFAULT_ICON_URI, size = size)
    }
}

/** Small uppercase tag, e.g. TEST. */
@Composable
internal fun PortfolioTag(text: String, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Text(
        text = text,
        color = n.textSecondary,
        style = NeonType.eyebrow.copy(letterSpacing = 0.8.sp),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(n.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Readiness chip: Swap-ready (with the flower) or TEST. "No route" has no chip. */
@Composable
internal fun ReadinessChip(ready: Boolean, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    if (ready) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(11.dp))
                .background(n.polkaswapBrand.copy(alpha = 0.13f))
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
        PortfolioTag(stringResource(R.string.portfolio_test_tag), modifier)
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

/** The app's glass card with a column inside. */
@Composable
internal fun PortfolioCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = radius) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun RowDivider(modifier: Modifier = Modifier, inset: Dp = 16.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(MaterialTheme.neon.hairline)
    )
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
    val edge = if (warning) n.warning else n.info
    val fill = if (warning) n.warningContainer else n.infoContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(1.dp, edge.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
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

/** A round neumorphic icon button (48 dp target). */
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
 * Bottom sheet in the SORA Neon style (as the app's other sheets): scrim, opaque glass body, top
 * radius 28, grabber, title and Close, scrolling body; at most 88 % of the screen.
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
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val maxSheetHeight = maxHeight * 0.88f
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            AnimatedVisibility(visible = shown, enter = slideInVertically { it / 3 } + fadeIn()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
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

/** A selectable row inside a sheet (radio semantics by default). */
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
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(if (selected) n.polkaswapBrand.copy(alpha = if (n.isDark) 0.14f else 0.07f) else n.glassFill)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) n.polkaswapBrand.copy(alpha = 0.75f) else n.hairline, shape)
            .clickable(role = role, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.customTypography.headline3, color = n.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.customTypography.textS, color = n.textSecondary, maxLines = 2)
            }
        }
        if (trailingTop != null || trailingBottom != null) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                trailingTop?.let {
                    Text(it, style = NeonType.amountM, color = n.textPrimary, maxLines = 1, textAlign = TextAlign.End)
                }
                trailingBottom?.let {
                    Text(it, style = NeonType.amountS, color = n.textSecondary, maxLines = 1, textAlign = TextAlign.End)
                }
            }
        }
    }
}

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
        Text(key, style = MaterialTheme.customTypography.textS, color = n.textSecondary)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.customTypography.textSBold, color = n.textPrimary, textAlign = TextAlign.End)
    }
}

/** A check line for the Polkaswap card: a tick when the condition holds, an info mark otherwise. */
@Composable
internal fun CheckLine(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PortfolioGlyph(if (ok) Glyph.CHECK else Glyph.INFO, if (ok) n.success else n.warning, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.customTypography.paragraphS, color = n.textSecondary)
    }
}
