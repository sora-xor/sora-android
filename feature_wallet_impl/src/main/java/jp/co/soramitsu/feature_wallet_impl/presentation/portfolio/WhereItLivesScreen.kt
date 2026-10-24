package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.neonChrome
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.MoveBlock
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioCalculator
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioLens
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioReadiness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSlots
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.Readiness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.TokenLocations
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.isTestnet
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.TiltState
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.tiltLayer
import jp.co.soramitsu.ui_core.component.button.properties.Size as ButtonSize
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal interface WhereItLivesActions {
    fun onBack()
    fun onSwapSheet(holdingKey: String)
    fun onMoveSheet(holdingKey: String)
    fun onSend(holdingKey: String)
    fun onReceiveOnSora2(walletId: String)
    fun onAssetDetails(holdingKey: String)
    fun onOpenPolkaswap(tokenId: String, holdingKey: String?)
}

/** Depths of the orbit at full deflection, x then y (portfolio-view SPEC §8). */
private object OrbitDepth {
    const val RING = 3f
    const val ORB = 8f
    const val SATELLITES = 15f
    const val POLKASWAP = 24f
    const val Y_RATIO = 0.6f
}

internal val ORBIT_HEIGHT = 252.dp

/** The orbit is on screen while the scroll has not passed its bottom (top bar space plus the orbit). */
/** The top bar: its row starts where the home's top row does. */
private val TOP_BAR_INSET = 16.dp
private val TOP_BAR_HEIGHT = 60.dp

/** The orbit starts under the top bar; its ring may pass behind the bar's lower part. */
private val ORBIT_TOP = TOP_BAR_INSET + TOP_BAR_HEIGHT - 16.dp

internal val ORBIT_BOTTOM = ORBIT_TOP + ORBIT_HEIGHT

/** "Where it lives" for one token (portfolio-view SPEC §5). The holdings list is the accessible truth. */
@Composable
internal fun WhereItLivesScreen(
    model: PortfolioModel,
    tokenId: String,
    format: PortfolioFormat,
    tilt: TiltState,
    scroll: ScrollState,
    actions: WhereItLivesActions,
) {
    val n = MaterialTheme.neon
    val locations = remember(model, tokenId) {
        PortfolioCalculator.tokenLocations(model.snapshot, model.walletIds, tokenId)
    }
    Box(Modifier.fillMaxSize()) {
        if (locations == null) {
            Text(
                stringResource(R.string.portfolio_token_gone),
                style = MaterialTheme.customTypography.paragraphM,
                color = n.textSecondary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
            TopBar(title = "", chrome = { 0f }, onBack = actions::onBack)
            return@Box
        }
        var selectedKey by rememberSaveable(tokenId) { mutableStateOf<String?>(null) }
        val selected = locations.holdings.firstOrNull { it.key == selectedKey }
            ?: locations.holdings.first()
        val tokenColor = tokenColor(model, tokenId)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(bottom = 28.dp)
        ) {
            Spacer(Modifier.height(ORBIT_TOP))
            Orbit(
                locations = locations,
                selectedNetwork = selected.network,
                color = tokenColor,
                tilt = tilt,
                format = format,
                onSatellite = { network ->
                    selectedKey = locations.holdings.firstOrNull { it.network == network }?.key
                },
            )
            TokenHeader(model, locations, format)
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.portfolio_where_it_lives),
                    style = MaterialTheme.customTypography.headline1,
                    color = n.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Overline(
                    pluralStringResource(R.plurals.portfolio_holdings, locations.holdings.size, locations.holdings.size) + " · " +
                        pluralStringResource(R.plurals.portfolio_networks, locations.networks.size, locations.networks.size)
                )
            }
            Spacer(Modifier.height(12.dp))
            PortfolioCard(Modifier.padding(horizontal = 16.dp)) {
                locations.holdings.forEachIndexed { index, holding ->
                    if (index > 0) RowDivider()
                    HoldingItem(
                        model = model,
                        holding = holding,
                        selected = holding.key == selected.key,
                        format = format,
                        color = tokenColor,
                        actions = actions,
                        onSelect = { selectedKey = holding.key },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PolkaswapCard(model, locations, actions, Modifier.padding(horizontal = 16.dp))
        }
        val threshold = with(LocalDensity.current) { 280.dp.toPx() }
        TopBar(
            title = stringResource(R.string.portfolio_where_it_lives_title, locations.token.symbol),
            chrome = { (scroll.value / threshold).coerceIn(0f, 1f) },
            onBack = actions::onBack,
        )
    }
}

private fun tokenColor(model: PortfolioModel, tokenId: String): Color {
    val slot = if (model.lens == PortfolioLens.TOKENS) model.slots[tokenId] else PortfolioSlots.seed(PortfolioLens.TOKENS)[tokenId]
    return if (slot != null) PortfolioPalette.slot(slot) else PortfolioPalette.polkaswap
}

@Composable
private fun TopBar(title: String, chrome: () -> Float, onBack: () -> Unit) {
    val n = MaterialTheme.neon
    val titleAlpha by animateFloatAsState(if (chrome() >= 1f) 1f else 0f, label = "titleAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .neonChrome(n, chrome)
            .padding(top = TOP_BAR_INSET)
            .height(TOP_BAR_HEIGHT)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeuIconButton(stringResource(R.string.common_back), onBack) {
            PortfolioGlyph(Glyph.BACK, n.textPrimary, Modifier.size(20.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.customTypography.headline3,
            color = n.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .alpha(titleAlpha),
        )
        Spacer(Modifier.width(48.dp))
    }
}

/** Satellite angles per count (degrees, 0 = right, positive = down), from the reference design. */
private val SATELLITE_ANGLES = mapOf(
    1 to listOf(200.0),
    2 to listOf(165.0, 20.0),
    3 to listOf(168.0, 92.0, 14.0),
)

@Composable
private fun Orbit(
    locations: TokenLocations,
    selectedNetwork: WalletNetworkId,
    color: Color,
    tilt: TiltState,
    format: PortfolioFormat,
    onSatellite: (WalletNetworkId) -> Unit,
) {
    val n = MaterialTheme.neon
    val dark = n.isDark
    // One satellite per network, largest first.
    val networks = locations.holdings.map { it.network }.distinct()
    val amounts = networks.associateWith { network ->
        locations.holdings
            .filter { it.network == network }
            .fold(BigDecimal.ZERO) { acc, h -> acc + h.total }
    }
    val angles = SATELLITE_ANGLES[networks.size.coerceIn(1, 3)].orEmpty()
    val lineColor = n.textPrimary.copy(alpha = if (dark) 0.22f else 0.18f)
    val ringColor = n.textPrimary
    val muted = n.textTertiary
    val pink = n.polkaswapBrand
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(ORBIT_HEIGHT)
    ) {
        val width = maxWidth
        val cx = width / 2
        val cy = ORBIT_HEIGHT * 0.52f
        val rx = min(150.dp, width * 0.36f)
        val ry = 54.dp
        val gateX = min(width - 58.dp, cx + rx + 34.dp)
        val gateY = 64.dp
        val positions = networks.mapIndexed { index, network ->
            val angle = (angles.getOrNull(index) ?: 0.0) * PI / 180
            network to Pair(cx + rx * cos(angle).toFloat(), cy + ry * sin(angle).toFloat())
        }
        // Aura in the token's colour (static).
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { hideFromAccessibility() }
        ) {
            val center = Offset(cx.toPx(), cy.toPx())
            val radius = size.height * 0.62f
            drawCircle(
                Brush.radialGradient(
                    0f to color.copy(alpha = if (dark) 0.42f else 0.22f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        // Rings in perspective.
        Canvas(
            Modifier
                .fillMaxSize()
                .tiltLayer(tilt, OrbitDepth.RING, OrbitDepth.RING * OrbitDepth.Y_RATIO)
                .semantics { hideFromAccessibility() }
        ) {
            val center = Offset(cx.toPx(), cy.toPx())
            fun ring(w: Dp, h: Dp, alpha: Float) = drawOval(
                color = ringColor.copy(alpha = alpha),
                topLeft = Offset(center.x - w.toPx() / 2, center.y - h.toPx() / 2),
                size = Size(w.toPx(), h.toPx()),
                style = Stroke(1.dp.toPx()),
            )
            ring(rx * 2, 112.dp, if (dark) 0.16f else 0.14f)
            ring(rx * 1.43f, 78.dp, if (dark) 0.09f else 0.08f)
        }
        // The token's orb.
        Box(
            Modifier
                .offset(cx - 38.dp, cy - 38.dp)
                .size(76.dp)
                .tiltLayer(tilt, OrbitDepth.ORB, OrbitDepth.ORB * OrbitDepth.Y_RATIO)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .semantics { hideFromAccessibility() }
        ) {
            TokenOrb(locations.token.iconUri, 76.dp)
        }
        // Links and satellites.
        Box(
            Modifier
                .fillMaxSize()
                .tiltLayer(tilt, OrbitDepth.SATELLITES, OrbitDepth.SATELLITES * OrbitDepth.Y_RATIO)
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { hideFromAccessibility() }
            ) {
                val center = Offset(cx.toPx(), cy.toPx())
                val gate = Offset(gateX.toPx(), gateY.toPx())
                positions.forEach { (network, pos) ->
                    val p = Offset(pos.first.toPx(), pos.second.toPx())
                    drawLine(lineColor, p, center, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    val control = Offset((p.x + gate.x) / 2, minOf(p.y, gate.y) - 38.dp.toPx())
                    val path = Path().apply {
                        moveTo(p.x, p.y)
                        quadraticTo(control.x, control.y, gate.x, gate.y)
                    }
                    val alpha = if (network == selectedNetwork) 1f else 0.55f
                    val ready = network == WalletNetworkId.SORA2
                    drawPath(
                        path = path,
                        color = if (ready) pink.copy(alpha = alpha) else muted.copy(alpha = 0.8f * alpha),
                        style = Stroke(
                            width = if (ready) 2.dp.toPx() else 1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = if (ready) null else PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())),
                        ),
                    )
                }
            }
            val largest = amounts.values.maxOf { it.toDouble() }.takeIf { it > 0.0 } ?: 1.0
            positions.forEach { (network, pos) ->
                val on = network == selectedNetwork
                // Satellites are sized by amount: 30 dp for the smallest, 44 dp for the largest.
                val badge = 30.dp + 14.dp * sqrt(amounts.getValue(network).toDouble() / largest).toFloat()
                val label = networkName(network) + ", " + format.amount(amounts.getValue(network), locations.token.symbol)
                Column(
                    modifier = Modifier
                        .offset(pos.first - 34.dp, pos.second - 26.dp)
                        .width(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = label, role = Role.Button, onClick = { onSatellite(network) })
                        .semantics {
                            contentDescription = label
                            selected = on
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .scale(if (on) 1.12f else 1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .border(if (on) 2.dp else 0.dp, if (on) pink else Color.Transparent, CircleShape)
                                .padding(if (on) 3.dp else 0.dp)
                        ) {
                            NetworkBadge(network, badge)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        format.compact(amounts.getValue(network)),
                        style = MaterialTheme.customTypography.textXSBold,
                        color = if (on) n.textPrimary else n.textSecondary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(n.glassSolid.copy(alpha = 0.82f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        // The Polkaswap node.
        Column(
            Modifier
                .offset(gateX - 56.dp, gateY - 23.dp)
                .width(112.dp)
                .tiltLayer(tilt, OrbitDepth.POLKASWAP, OrbitDepth.POLKASWAP * OrbitDepth.Y_RATIO)
                .semantics { hideFromAccessibility() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PolkaswapDisc(46.dp)
            Spacer(Modifier.height(4.dp))
            Overline("Polkaswap")
        }
    }
}

@Composable
internal fun PolkaswapDisc(size: Dp, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(n.ctaSwap)
            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        PolkaswapFlower(Modifier.size(size * 0.56f), tint = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenHeader(model: PortfolioModel, locations: TokenLocations, format: PortfolioFormat) {
    val n = MaterialTheme.neon
    val token = locations.token
    val testnetOnly = locations.holdings.all { it.network.isTestnet }
    val amount = if (testnetOnly) locations.holdings.fold(BigDecimal.ZERO) { acc, h -> acc + h.total } else locations.countedAmount
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(token.symbol, style = MaterialTheme.customTypography.displayM, color = n.textPrimary)
            Spacer(Modifier.width(10.dp))
            Text(
                token.name,
                style = MaterialTheme.customTypography.textM,
                color = n.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            format.amount(amount, token.symbol),
            style = NeonType.displayHero.copy(fontSize = 34.sp, lineHeight = 42.sp),
            color = n.textPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    testnetOnly -> stringResource(R.string.portfolio_no_value)
                    token.price == null -> stringResource(R.string.portfolio_no_price)
                    else -> format.fiat(locations.countedFiat)
                },
                style = NeonType.amountM.copy(fontWeight = FontWeight.Normal),
                color = n.textSecondary,
            )
            token.change24h?.takeIf { kotlin.math.abs(it) >= 0.0005 && !testnetOnly }?.let { change ->
                val up = change > 0
                Text(
                    (if (up) "▲ " else "▼ ") + format.signedPercent(change),
                    style = NeonType.amountM,
                    color = if (up) n.success else n.error,
                )
            }
            if (!testnetOnly && token.price != null) {
                Text(
                    if (model.scope == PortfolioScope.ALL) {
                        stringResource(R.string.portfolio_share_of_all, format.share(locations.share))
                    } else {
                        stringResource(R.string.portfolio_share_of_wallet, format.share(locations.share), model.selectedWallet?.name.orEmpty())
                    },
                    style = MaterialTheme.customTypography.textM,
                    color = n.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun HoldingItem(
    model: PortfolioModel,
    holding: PortfolioHolding,
    selected: Boolean,
    format: PortfolioFormat,
    color: Color,
    actions: WhereItLivesActions,
    onSelect: () -> Unit,
) {
    val n = MaterialTheme.neon
    val edge = n.polkaswapBrand
    Column(
        Modifier
            .animateContentSize()
            .background(if (selected) edge.copy(alpha = if (n.isDark) 0.08f else 0.04f) else Color.Transparent)
            .drawBehind {
                if (selected) {
                    drawRoundRect(
                        color = edge,
                        topLeft = Offset(0f, 12.dp.toPx()),
                        size = Size(3.dp.toPx(), size.height - 24.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
    ) {
        Box(Modifier.semantics { this.selected = selected }) {
            HoldingRow(model, holding, format, showToken = false, onClick = onSelect)
        }
        if (selected) {
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Breakdown(holding, format, color)
                RouteCard(model, holding)
                HoldingActions(model, holding, actions)
            }
        }
    }
}

/** Transferable, Locked and Total counted, with a bar that uses a pattern, never colour alone. */
@Composable
private fun Breakdown(holding: PortfolioHolding, format: PortfolioFormat, color: Color) {
    val n = MaterialTheme.neon
    val symbol = holding.token.symbol
    val total = holding.total
    val locked = holding.locked
    val transferable = holding.transferable
    val stripe = Color.White.copy(alpha = if (n.isDark) 0.55f else 0.8f)
    val lockedColor = PortfolioPalette.slots[3]
    val exclusion = PortfolioCalculator.exclusion(holding)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(n.neuBg)
            .border(1.dp, n.hairline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val share = if (total.signum() > 0) transferable.toDouble() / total.toDouble() else 1.0
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .semantics { hideFromAccessibility() }
        ) {
            val gap = if (locked.signum() > 0) 2.dp.toPx() else 0f
            val a = (size.width - gap) * share.toFloat().coerceIn(0f, 1f)
            drawRect(color, size = Size(a, size.height))
            if (locked.signum() > 0) {
                val left = a + gap
                drawRect(lockedColor, topLeft = Offset(left, 0f), size = Size(size.width - left, size.height))
                clipRect(left = left) {
                    var x = left - size.height
                    while (x < size.width) {
                        drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5.dp.toPx())
                        x += 4.dp.toPx()
                    }
                }
            }
        }
        BreakdownRow(stringResource(R.string.portfolio_transferable), format.amount(transferable, symbol)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
        if (locked.signum() > 0) {
            BreakdownRow(stringResource(R.string.portfolio_locked), format.amount(locked, symbol)) {
                Canvas(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp))) {
                    drawRect(lockedColor)
                    drawLine(stripe, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
                }
            }
        }
        BreakdownRow(
            if (exclusion == null) stringResource(R.string.portfolio_total_counted) else stringResource(R.string.portfolio_total_not_counted),
            format.amount(total, symbol),
            bold = true,
        ) {}
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, bold: Boolean = false, key: @Composable () -> Unit) {
    val n = MaterialTheme.neon
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        key()
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.customTypography.textS, color = n.textSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (bold) NeonType.amountS.copy(fontWeight = FontWeight.SemiBold) else NeonType.amountS,
            color = n.textPrimary,
        )
    }
}

/** What stands between this holding and a swap. Never invents a route. */
@Composable
private fun RouteCard(model: PortfolioModel, holding: PortfolioHolding) {
    val n = MaterialTheme.neon
    val readiness = PortfolioReadiness.readiness(holding)
    val ready = readiness == Readiness.READY
    val pink = n.polkaswapBrand
    val wallet = model.wallet(holding.walletId)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (ready) pink.copy(alpha = if (n.isDark) 0.10f else 0.05f) else n.glassFill)
            .border(1.dp, if (ready) pink.copy(alpha = 0.40f) else n.hairline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HopChip(networkName(holding.network)) { NetworkBadge(holding.network, 22.dp) }
            Spacer(Modifier.width(6.dp))
            if (ready) {
                PortfolioGlyph(Glyph.ARROW_RIGHT, n.textTertiary, Modifier.size(16.dp))
            } else {
                Text(
                    stringResource(R.string.portfolio_no_route),
                    style = MaterialTheme.customTypography.textXSBold,
                    color = n.textTertiary,
                )
            }
            Spacer(Modifier.width(6.dp))
            HopChip("Polkaswap", dex = true, muted = !ready) {
                PolkaswapFlower(Modifier.size(16.dp), tint = n.polkaswapBrand)
            }
        }
        val text = when (readiness) {
            Readiness.READY -> stringResource(R.string.portfolio_route_ready, wallet?.name.orEmpty())
            Readiness.NONE -> stringResource(R.string.portfolio_route_runs_on_sora2) + " " +
                stringResource(R.string.portfolio_route_none, networkName(holding.network))
            Readiness.TEST -> stringResource(R.string.portfolio_route_test) + " " +
                stringResource(R.string.portfolio_route_none, networkName(holding.network))
        }
        Text(text, style = MaterialTheme.customTypography.paragraphS, color = n.textSecondary)
    }
}

@Composable
private fun HopChip(label: String, dex: Boolean = false, muted: Boolean = false, icon: @Composable () -> Unit) {
    val n = MaterialTheme.neon
    val pink = n.polkaswapBrand
    Row(
        Modifier
            .alpha(if (muted) 0.72f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (dex) pink.copy(alpha = if (muted) 0.06f else 0.16f) else n.glassFillStrong)
            .border(1.dp, if (dex) pink.copy(alpha = if (muted) 0.25f else 0.5f) else n.hairline, RoundedCornerShape(14.dp))
            .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.customTypography.textSBold, color = n.textPrimary, maxLines = 1)
    }
}

/** One primary and two secondary actions, each with a reason when unavailable. */
@Composable
private fun HoldingActions(model: PortfolioModel, holding: PortfolioHolding, actions: WhereItLivesActions) {
    val n = MaterialTheme.neon
    val readiness = PortfolioReadiness.readiness(holding)
    val moveBlock = PortfolioReadiness.moveBlock(model.snapshot, holding)
    val network = networkName(holding.network)
    val reasons = mutableListOf<String>()
    val moveLabel = stringResource(R.string.portfolio_move)
    val sendLabel = stringResource(R.string.common_send)
    when (moveBlock) {
        MoveBlock.NO_OTHER_WALLET -> reasons += stringResource(R.string.portfolio_move_no_other_wallet, network)
        MoveBlock.SEND_UNAVAILABLE -> reasons += stringResource(R.string.portfolio_send_unavailable, network)
        MoveBlock.NOTHING_TRANSFERABLE -> reasons += stringResource(R.string.portfolio_nothing_transferable)
        null -> Unit
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (readiness) {
            Readiness.READY -> {
                NeonButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Large,
                    variant = NeonVariant.Swap,
                    text = stringResource(R.string.portfolio_swap_on_polkaswap),
                    leftIcon = painterResource(R.drawable.ic_polkaswap),
                    onClick = { actions.onSwapSheet(holding.key) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonButton(
                        modifier = Modifier.weight(1f),
                        size = ButtonSize.Small,
                        variant = NeonVariant.Secondary,
                        text = sendLabel,
                        enabled = holding.sendAvailable,
                        onClick = { actions.onSend(holding.key) },
                    )
                    NeonButton(
                        modifier = Modifier.weight(1f),
                        size = ButtonSize.Small,
                        variant = NeonVariant.Secondary,
                        text = moveLabel,
                        enabled = moveBlock == null,
                        onClick = { actions.onMoveSheet(holding.key) },
                    )
                }
            }
            Readiness.NONE, Readiness.TEST -> {
                if (!holding.sendAvailable) reasons.add(0, stringResource(R.string.portfolio_send_unavailable, network))
                NeonButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Large,
                    variant = NeonVariant.Primary,
                    text = stringResource(R.string.portfolio_send_on, network),
                    enabled = holding.sendAvailable,
                    onClick = { actions.onSend(holding.key) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonButton(
                        modifier = Modifier.weight(1f),
                        size = ButtonSize.Small,
                        variant = NeonVariant.Secondary,
                        text = stringResource(R.string.portfolio_receive_on_sora2),
                        onClick = { actions.onReceiveOnSora2(holding.walletId) },
                    )
                    NeonButton(
                        modifier = Modifier.weight(1f),
                        size = ButtonSize.Small,
                        variant = NeonVariant.Secondary,
                        text = moveLabel,
                        enabled = moveBlock == null,
                        onClick = { actions.onMoveSheet(holding.key) },
                    )
                }
                reasons += stringResource(
                    if (readiness == Readiness.TEST) R.string.portfolio_route_test else R.string.portfolio_route_runs_on_sora2
                )
            }
        }
        reasons.distinct().forEach { reason ->
            Text(reason, style = MaterialTheme.customTypography.textS, color = n.textSecondary)
        }
        if (holding.network == WalletNetworkId.SORA2 && !model.sample) {
            NeonButton(
                size = ButtonSize.ExtraSmall,
                variant = NeonVariant.Tertiary,
                text = stringResource(R.string.portfolio_asset_details),
                onClick = { actions.onAssetDetails(holding.key) },
            )
        }
    }
}

/** The checks that gate a swap, and one call to action. */
@Composable
private fun PolkaswapCard(model: PortfolioModel, locations: TokenLocations, actions: WhereItLivesActions, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val pink = n.polkaswapBrand
    val sora2 = locations.holdings.firstOrNull { it.network == WalletNetworkId.SORA2 }
    val walletName = (sora2?.walletId?.let(model::wallet) ?: model.selectedWallet)?.name.orEmpty()
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(pink.copy(alpha = if (n.isDark) 0.18f else 0.08f), n.glassFill, n.glassFill)))
            .border(1.dp, pink.copy(alpha = 0.40f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PolkaswapDisc(44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Polkaswap", style = MaterialTheme.customTypography.headline2, color = n.textPrimary)
                Text(stringResource(R.string.portfolio_polkaswap_body), style = MaterialTheme.customTypography.paragraphS, color = n.textSecondary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckLine(stringResource(R.string.portfolio_check_account, walletName), ok = true)
            CheckLine(stringResource(R.string.portfolio_check_available), ok = true)
            CheckLine(
                stringResource(if (model.swapDisclaimerPending) R.string.portfolio_check_disclaimer_pending else R.string.portfolio_check_disclaimer_done),
                ok = !model.swapDisclaimerPending,
            )
        }
        NeonButton(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            size = ButtonSize.Large,
            variant = NeonVariant.Swap,
            text = stringResource(R.string.portfolio_open_polkaswap),
            leftIcon = painterResource(R.drawable.ic_polkaswap),
            onClick = { actions.onOpenPolkaswap(locations.token.id, sora2?.key) },
        )
    }
}
