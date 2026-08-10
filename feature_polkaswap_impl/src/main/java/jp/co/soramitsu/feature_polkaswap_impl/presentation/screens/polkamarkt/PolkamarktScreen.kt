package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigInteger
import kotlin.math.sqrt
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.common.data.network.dto.PolkamarktClaimableDto
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktAccountPosition
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktMarket
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktMarketSnapshot
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktExternalLinkPolicy
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktOutcome
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktTradeQuote
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktTradeSide
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktWebContract
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
internal fun PolkamarktScreen(
    state: PolkamarktScreenState,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onStatusFilter: (String) -> Unit,
    onCategoryFilter: (String) -> Unit,
    onMineOnly: () -> Unit,
    onMarket: (PolkamarktMarket) -> Unit,
    onBackToMarkets: () -> Unit,
    onSide: (PolkamarktTradeSide) -> Unit,
    onOutcome: (PolkamarktOutcome) -> Unit,
    onAmount: (String) -> Unit,
    onSlippage: (Int) -> Unit,
    onQuote: () -> Unit,
    onConfirm: () -> Unit,
    onClaim: (PolkamarktAccountPosition, Boolean) -> Unit,
    onReviewClaim: (Long) -> Unit,
    onSelectedClaim: (Boolean) -> Unit,
    onReviewBatchClaims: () -> Unit,
    onBatchClaim: () -> Unit,
    onConfirmClaim: () -> Unit,
    onDismissClaimConfirmation: () -> Unit,
) {
    val mutationsAvailable = state.mutationsEnabled &&
        !state.pendingRecoveryRequired &&
        !state.mutationLoading
    state.pendingClaimConfirmation?.let { confirmation ->
        ClaimConfirmationDialog(
            confirmation = confirmation,
            confirmEnabled = mutationsAvailable,
            onConfirm = onConfirmClaim,
            onDismiss = onDismissClaimConfirmation,
        )
    }
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.x2, vertical = Dimens.x2),
    ) {
        PolkamarktHeader(state, onRefresh)
        Spacer(Modifier.size(Dimens.x2))
        if (state.selectedMarket == null) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.search,
                onValueChange = onSearch,
                singleLine = true,
                label = { Text("Search markets") },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PolkamarktWebContract.STATUS_FILTERS.forEach { filter ->
                    TextButton(onClick = { onStatusFilter(filter) }) {
                        Text(
                            text = filter.replaceFirstChar { it.uppercase() },
                            fontWeight = if (state.statusFilter == filter) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                }
                OutlinedButton(onClick = onMineOnly) {
                    Text(if (state.mineOnly) "My markets ✓" else "My markets")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf("all") + PolkamarktWebContract.CATEGORIES).forEach { category ->
                    TextButton(onClick = { onCategoryFilter(category) }) {
                        Text(
                            text = category.replaceFirstChar { it.uppercase() },
                            fontWeight = if (state.categoryFilter == category) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                }
            }

            val filteredMarkets = state.markets.filter { market ->
                val searchMatches = state.search.isBlank() || listOf(
                    market.title,
                    market.description,
                    market.category,
                    market.status,
                    market.creator,
                ).filterNotNull().any {
                    it.contains(state.search.trim(), ignoreCase = true)
                }
                val statusMatches = PolkamarktWebContract.matchesStatusFilter(
                    status = market.status,
                    filter = state.statusFilter,
                )
                val categoryMatches = state.categoryFilter == "all" ||
                    market.category.equals(state.categoryFilter, ignoreCase = true)
                val ownerMatches = PolkamarktWebContract.matchesOwnerFilter(
                    creator = market.creator,
                    selectedAccount = state.accountAddress,
                    mineOnly = state.mineOnly,
                )
                searchMatches && statusMatches && categoryMatches && ownerMatches
            }
            filteredMarkets.forEach { market ->
                MarketRow(
                    market = market,
                    selected = false,
                    onClick = { onMarket(market) },
                )
                Spacer(Modifier.size(Dimens.x1))
            }
        } else {
            val market = checkNotNull(state.selectedMarket)
            TextButton(onClick = onBackToMarkets) {
                Text("‹ Markets")
            }
            Spacer(Modifier.size(Dimens.x2))
            MarketDetail(
                catalogMarket = market,
                authoritativeMarket = state.authoritativeMarket,
                claimable = state.authoritativeClaimable,
                finalizedBlockHash = state.authoritativeFinalizedBlockHash,
                loading = state.marketDetailLoading,
                snapshots = state.snapshots,
                mutationsEnabled = mutationsAvailable,
                onClaim = onSelectedClaim,
            )
            Spacer(Modifier.size(Dimens.x2))
            TradeTicket(
                state = state,
                onSide = onSide,
                onOutcome = onOutcome,
                onAmount = onAmount,
                onSlippage = onSlippage,
                onQuote = onQuote,
                onConfirm = onConfirm,
            )
        }

        if (state.positions.isNotEmpty()) {
            Spacer(Modifier.size(Dimens.x3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Positions",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                val reviewedPayoutMarketIds: List<Long> = state.positions
                    .asSequence()
                    .mapNotNull(PolkamarktAccountPosition::marketId)
                    .filter { marketId ->
                        state.authoritativeClaims[marketId]?.let { claimable ->
                            hasClaimableStatus(claimable) &&
                                claimable.claimablePayout.signum() == 1
                        } == true
                    }
                    .distinct()
                    .sorted()
                    .take(PolkamarktWebContract.MAX_BATCH_CLAIMS)
                    .toList()
                if (reviewedPayoutMarketIds.size > 1) {
                    OutlinedButton(
                        enabled = mutationsAvailable,
                        onClick = onBatchClaim,
                    ) {
                        Text("Claim ${reviewedPayoutMarketIds.size} verified payouts")
                    }
                } else if (state.positions.size > 1) {
                    OutlinedButton(
                        enabled = !state.claimReviewLoading,
                        onClick = onReviewBatchClaims,
                    ) {
                        Text(
                            if (state.claimReviewLoading) {
                                "Reviewing finalized claims…"
                            } else {
                                "Review finalized claims"
                            }
                        )
                    }
                }
            }
            state.positions.forEach { position ->
                val marketId = position.marketId
                PositionRow(
                    position = position,
                    claimable = marketId?.let(state.authoritativeClaims::get),
                    reviewed = marketId?.let(
                        state.reviewedClaimMarketIds::contains
                    ) == true,
                    reviewLoading = state.claimReviewLoading,
                    mutationsEnabled = mutationsAvailable,
                    onReview = onReviewClaim,
                    onClaim = onClaim,
                )
            }
        }

        if (state.trades.isNotEmpty()) {
            Spacer(Modifier.size(Dimens.x3))
            Text(
                "Trade history",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.customColors.fgPrimary,
            )
            state.trades.take(25).forEach { trade ->
                val marketLabel = if (trade.marketIds.size > 1) {
                    "${trade.marketIds.size} markets · ${trade.marketIds.joinToString(", ")}"
                } else {
                    "Market ${trade.marketId ?: "—"}"
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    backgroundColor = MaterialTheme.customColors.bgSurface,
                ) {
                    Column(Modifier.padding(Dimens.x2)) {
                        Text(
                            "${trade.side.orEmpty()} ${trade.outcome.orEmpty()} · $marketLabel",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Shares ${trade.sharesAmount.orEmpty()} · Price ${trade.executionPrice.orEmpty()}",
                            style = MaterialTheme.typography.caption,
                        )
                        Text(trade.timestamp.orEmpty(), style = MaterialTheme.typography.caption)
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimens.x4))
    }
}

@Composable
private fun ClaimConfirmationDialog(
    confirmation: PolkamarktClaimConfirmation,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        backgroundColor = MaterialTheme.customColors.bgPage,
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (confirmation.marketIds.size > 1) {
                    stringResource(
                        CommonR.string.polkamarkt_claim_confirmation_batch_title,
                        confirmation.marketIds.size,
                    )
                } else {
                    stringResource(CommonR.string.polkamarkt_claim_confirmation_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (confirmation.kind == PolkamarktClaimKind.CREATOR_FEES) {
                            CommonR.string.polkamarkt_claim_creator_fees
                        } else {
                            CommonR.string.polkamarkt_claim_trader_payout
                        }
                    ),
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(CommonR.string.polkamarkt_claim_confirmation_body))
                confirmation.marketIds.forEach { marketId ->
                    val claim = checkNotNull(confirmation.claims[marketId])
                    val amount = when (confirmation.kind) {
                        PolkamarktClaimKind.TRADER_PAYOUT -> claim.claimablePayout
                        PolkamarktClaimKind.CREATOR_FEES -> claim.creatorFees
                    }
                    Text(
                        stringResource(
                            CommonR.string.polkamarkt_claim_confirmation_market,
                            marketId,
                            PolkamarktViewModel.formatUnits(amount),
                        )
                    )
                }
                Text(
                    stringResource(
                        CommonR.string.polkamarkt_claim_confirmation_checkpoint,
                        confirmation.finalizedBlockHash.take(18),
                    ),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                Text(
                    stringResource(CommonR.string.polkamarkt_claim_confirmation_fee_notice),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = confirmEnabled, onClick = onConfirm) {
                Text(stringResource(CommonR.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.common_cancel))
            }
        },
    )
}

@Composable
private fun PolkamarktHeader(
    state: PolkamarktScreenState,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "Prediction markets",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.customColors.fgPrimary,
            )
            Text(
                state.signals?.let {
                    "${it.activeMarkets} active · ${it.activeAccounts} traders"
                } ?: "SORA2 · KUSD markets",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.customColors.fgSecondary,
            )
        }
        if (state.loading || state.refreshing) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
    }
    state.errorCode?.let {
        Text(
            text = it,
            color = MaterialTheme.customColors.statusError,
            style = MaterialTheme.typography.caption,
        )
    }
    if (state.pendingRecoveryRequired) {
        Text(
            text = "Pending transaction recovery required · trading and claims disabled",
            color = MaterialTheme.customColors.statusError,
            style = MaterialTheme.typography.caption,
        )
    }
    if (state.usingCachedData) {
        Text(
            text = "Offline market catalog · runtime checks still required for trading",
            color = MaterialTheme.customColors.fgSecondary,
            style = MaterialTheme.typography.caption,
        )
    }
    state.pendingTransactions.forEach { pending ->
        Text(
            text = "${pending.operation} · ${pending.state} · " +
                (pending.transactionHash?.take(12) ?: pending.localId.take(12)),
            color = MaterialTheme.customColors.fgSecondary,
            style = MaterialTheme.typography.caption,
        )
    }
    state.lastMutation?.let {
        Text(
            text = "${it.state} · ${it.transactionHash?.take(12) ?: it.localId.take(12)}",
            color = MaterialTheme.customColors.fgSecondary,
            style = MaterialTheme.typography.caption,
        )
    }
}

@Composable
private fun MarketRow(
    market: PolkamarktMarket,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        backgroundColor = if (selected) {
            MaterialTheme.customColors.bgSurfaceVariant
        } else {
            MaterialTheme.customColors.bgSurface
        },
    ) {
        Column(Modifier.padding(Dimens.x2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    market.category ?: "Market",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                Text(
                    market.status.orEmpty().uppercase(),
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                market.title ?: "Market ${market.marketId ?: ""}",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${polkamarktOutcomeLabel(PolkamarktOutcome.YES).uppercase()} " +
                        formatBps(market.displayYesProbabilityBps),
                )
                Text(
                    "${polkamarktOutcomeLabel(PolkamarktOutcome.NO).uppercase()} " +
                        formatBps(market.displayNoProbabilityBps),
                )
            }
            Text(
                "Liquidity ${market.liquidityUsd.orEmpty()} · Volume ${market.volumeUsd.orEmpty()}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.customColors.fgSecondary,
            )
        }
    }
}

@Composable
private fun MarketDetail(
    catalogMarket: PolkamarktMarket,
    authoritativeMarket: PolkamarktMarket?,
    claimable: PolkamarktClaimableDto?,
    finalizedBlockHash: String?,
    loading: Boolean,
    snapshots: List<PolkamarktMarketSnapshot>,
    mutationsEnabled: Boolean,
    onClaim: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.customColors.bgSurface,
    ) {
        Column(Modifier.padding(Dimens.x3)) {
            Text(
                catalogMarket.title.orEmpty(),
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
            )
            catalogMarket.description?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.size(Dimens.x1))
                Text(it, style = MaterialTheme.typography.body2)
            }
            Spacer(Modifier.size(Dimens.x2))
            when {
                loading -> Text(
                    "Loading finalized runtime state…",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                authoritativeMarket == null -> Text(
                    "Finalized runtime state unavailable · catalog state is not authoritative",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.customColors.statusError,
                )
                else -> {
                    Text(
                        authoritativeMarket.status.orEmpty().uppercase(),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${polkamarktOutcomeLabel(PolkamarktOutcome.YES).uppercase()} " +
                            "${formatBps(authoritativeMarket.displayYesProbabilityBps)} · " +
                            "${polkamarktOutcomeLabel(PolkamarktOutcome.NO).uppercase()} " +
                            formatBps(authoritativeMarket.displayNoProbabilityBps),
                    )
                    Text(
                        "Finalized ${finalizedBlockHash?.take(12).orEmpty()}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.customColors.fgSecondary,
                    )
                    val yesShares = claimable?.yesShares ?: BigInteger.ZERO
                    val noShares = claimable?.noShares ?: BigInteger.ZERO
                    Text(
                        "Your shares · " +
                            "${polkamarktOutcomeLabel(PolkamarktOutcome.YES).uppercase()} " +
                            "${PolkamarktViewModel.formatUnits(yesShares)} · " +
                            "${polkamarktOutcomeLabel(PolkamarktOutcome.NO).uppercase()} " +
                            PolkamarktViewModel.formatUnits(noShares),
                        style = MaterialTheme.typography.body2,
                    )
                    claimable?.claimablePayout?.takeIf { it.signum() == 1 }?.let { payout ->
                        Text(
                            "Claimable ${PolkamarktViewModel.formatUnits(payout)} KUSD",
                            style = MaterialTheme.typography.caption,
                        )
                    }
                    if (claimable != null && hasClaimableStatus(claimable)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (claimable.claimablePayout.signum() == 1) {
                                OutlinedButton(
                                    enabled = mutationsEnabled,
                                    onClick = { onClaim(false) },
                                ) {
                                    Text(
                                        stringResource(
                                            CommonR.string.polkamarkt_claim_trader_payout,
                                        )
                                    )
                                }
                            }
                            if (
                                claimable.isCreator &&
                                claimable.creatorFees.signum() == 1
                            ) {
                                OutlinedButton(
                                    enabled = mutationsEnabled,
                                    onClick = { onClaim(true) },
                                ) {
                                    Text(
                                        stringResource(
                                            CommonR.string.polkamarkt_claim_creator_fees,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(Dimens.x2))
            ProbabilityChart(snapshots)
            if (authoritativeMarket != null && isDpmMarket(authoritativeMarket)) {
                Spacer(Modifier.size(Dimens.x2))
                DpmPricingCurve(authoritativeMarket)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                safeWebUri(catalogMarket.metadataUri)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) { Text("Metadata") }
                }
                safeWebUri(catalogMarket.rulesUri)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) { Text("Rules") }
                }
                safeWebUri(catalogMarket.resolutionEvidenceUri)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) { Text("Evidence") }
                }
                safeWebUri(catalogMarket.cancellationEvidenceUri)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) {
                        Text("Cancellation")
                    }
                }
                safeWebUri(catalogMarket.governanceUrl)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) { Text("Governance") }
                }
            }
            catalogMarket.resolutionSource?.takeIf(String::isNotBlank)?.let {
                Text(
                    "Resolution source · $it",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            }
        }
    }
}

@Composable
private fun DpmPricingCurve(market: PolkamarktMarket) {
    Text(
        "Dynamic pari-mutuel pricing curve",
        style = MaterialTheme.typography.subtitle2,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "${polkamarktOutcomeLabel(PolkamarktOutcome.YES).uppercase()} / " +
            "${polkamarktOutcomeLabel(PolkamarktOutcome.NO).uppercase()} " +
            "marginal quote by demand share",
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.customColors.fgSecondary,
    )
    val yesColor = MaterialTheme.customColors.accentPrimary
    val noColor = MaterialTheme.customColors.fgSecondary
    val marker = market.impliedYesProbabilityBps
        ?.div(10_000f)
        ?.coerceIn(0.01f, 0.99f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val yesPath = Path()
        val noPath = Path()
        repeat(PolkamarktWebContract.DPM_CURVE_POINT_COUNT) { index ->
            val yesShare = (index + 1) / 100f
            val noShare = 1f - yesShare
            val yesQuote = yesShare / sqrt(yesShare * yesShare + noShare * noShare)
            val noQuote = noShare / sqrt(noShare * noShare + yesShare * yesShare)
            val x = size.width * index /
                (PolkamarktWebContract.DPM_CURVE_POINT_COUNT - 1)
            val yesY = size.height * (1f - yesQuote)
            val noY = size.height * (1f - noQuote)
            if (index == 0) {
                yesPath.moveTo(x, yesY)
                noPath.moveTo(x, noY)
            } else {
                yesPath.lineTo(x, yesY)
                noPath.lineTo(x, noY)
            }
        }
        drawPath(
            path = yesPath,
            color = yesColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = noPath,
            color = noColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        marker?.let { current ->
            val markerX = size.width * ((current - 0.01f) / 0.98f)
            drawLine(
                color = yesColor.copy(alpha = 0.45f),
                start = Offset(markerX, 0f),
                end = Offset(markerX, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

@Composable
private fun ProbabilityChart(snapshots: List<PolkamarktMarketSnapshot>) {
    val points = snapshots.asReversed().mapNotNull { snapshot ->
        val yesProbability = snapshot.chartPriceYes
            ?: snapshot.chartProbability
            ?: snapshot.chartPriceNo?.let { 1.0 - it }
        yesProbability?.toFloat()
    }
    if (points.size < 2) {
        Text(
            "Price history unavailable",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.customColors.fgSecondary,
        )
        return
    }
    val lineColor = MaterialTheme.customColors.accentPrimary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / (points.lastIndex.coerceAtLeast(1))
            val normalized = value.coerceIn(0f, 1f)
            val y = size.height * (1f - normalized)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawLine(
            color = lineColor.copy(alpha = 0.2f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
        )
    }
}

@Composable
private fun TradeTicket(
    state: PolkamarktScreenState,
    onSide: (PolkamarktTradeSide) -> Unit,
    onOutcome: (PolkamarktOutcome) -> Unit,
    onAmount: (String) -> Unit,
    onSlippage: (Int) -> Unit,
    onQuote: () -> Unit,
    onConfirm: () -> Unit,
) {
    val mutationsAvailable = state.mutationsEnabled &&
        !state.pendingRecoveryRequired &&
        !state.mutationLoading
    val buyLabel = stringResource(CommonR.string.polkamarkt_action_buy)
    val sellLabel = stringResource(CommonR.string.polkamarkt_action_sell)
    val yesLabel = stringResource(CommonR.string.polkamarkt_outcome_yes)
    val noLabel = stringResource(CommonR.string.polkamarkt_outcome_no)
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.customColors.bgSurface,
    ) {
        Column(Modifier.padding(Dimens.x3)) {
            Text("Trade", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            ChoiceRow(
                values = PolkamarktTradeSide.entries,
                selected = state.side,
                label = {
                    if (it == PolkamarktTradeSide.BUY) buyLabel else sellLabel
                },
                onSelect = onSide,
            )
            ChoiceRow(
                values = PolkamarktOutcome.entries,
                selected = state.outcome,
                label = {
                    if (it == PolkamarktOutcome.YES) yesLabel else noLabel
                },
                onSelect = onOutcome,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.amountInput,
                onValueChange = onAmount,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                label = {
                    Text(if (state.side == PolkamarktTradeSide.BUY) "KUSD" else "Shares")
                },
            )
            if (state.side == PolkamarktTradeSide.SELL) {
                val availableShares = state.authoritativeMarket?.let {
                    when (state.outcome) {
                        PolkamarktOutcome.YES ->
                            state.authoritativeClaimable?.yesShares ?: BigInteger.ZERO
                        PolkamarktOutcome.NO ->
                            state.authoritativeClaimable?.noShares ?: BigInteger.ZERO
                    }
                }
                Text(
                    availableShares?.let {
                        "Finalized available shares ${PolkamarktViewModel.formatUnits(it)}"
                    } ?: "Finalized share balance unavailable",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            }
            Text(
                stringResource(CommonR.string.polkamarkt_ticket_slippage),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.customColors.fgSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PolkamarktWebContract.SLIPPAGE_PRESETS_BPS.forEach { bps ->
                    TextButton(onClick = { onSlippage(bps) }) {
                        Text(
                            text = formatBps(bps),
                            fontWeight = if (state.slippageBps == bps) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = mutationsAvailable &&
                    !state.quoteLoading &&
                    state.amountInput.isNotBlank(),
                onClick = onQuote,
            ) {
                Text(
                    when {
                        state.quoteLoading -> "Quoting…"
                        mutationsAvailable -> "Get fresh quote"
                        else -> "Quoting temporarily unavailable"
                    }
                )
            }
            state.quote?.let { quote ->
                QuoteDetails(quote)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = mutationsAvailable && !state.quoteLoading,
                    onClick = onConfirm,
                ) {
                    Text(
                        if (mutationsAvailable) {
                            "Confirm " + if (quote.side == PolkamarktTradeSide.BUY) {
                                buyLabel
                            } else {
                                sellLabel
                            }
                        } else {
                            "Trading temporarily unavailable"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteDetails(quote: PolkamarktTradeQuote) {
    Spacer(Modifier.size(Dimens.x2))
    val outputLabel = if (quote.side == PolkamarktTradeSide.BUY) {
        stringResource(CommonR.string.polkamarkt_ticket_shares_out)
    } else {
        stringResource(CommonR.string.polkamarkt_ticket_collateral_out)
    }
    Text(
        "$outputLabel ${PolkamarktViewModel.formatUnits(quote.outputAmount)}",
        fontWeight = FontWeight.Bold,
    )
    Text("Minimum ${PolkamarktViewModel.formatUnits(quote.minimumOutput)}")
    Text(
        "${stringResource(CommonR.string.polkamarkt_ticket_taker_fee)} " +
            PolkamarktViewModel.formatUnits(quote.marketFee),
    )
    Text(
        "${stringResource(CommonR.string.network_fee)} " +
            "${PolkamarktViewModel.formatUnits(quote.xorNetworkFee)} XOR",
    )
    val inputBalance = when (quote.side) {
        PolkamarktTradeSide.BUY ->
            "KUSD ${PolkamarktViewModel.formatUnits(quote.kusdBalance)}"
        PolkamarktTradeSide.SELL ->
            quote.shareBalance?.let {
                "Shares ${PolkamarktViewModel.formatUnits(it)}"
            } ?: "Shares unavailable"
    }
    Text(
        "$inputBalance · " +
            "XOR ${PolkamarktViewModel.formatUnits(quote.xorBalance)}",
        style = MaterialTheme.typography.caption,
    )
}

@Composable
private fun PositionRow(
    position: PolkamarktAccountPosition,
    claimable: PolkamarktClaimableDto?,
    reviewed: Boolean,
    reviewLoading: Boolean,
    mutationsEnabled: Boolean,
    onReview: (Long) -> Unit,
    onClaim: (PolkamarktAccountPosition, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        backgroundColor = MaterialTheme.customColors.bgSurface,
    ) {
        Column(Modifier.padding(Dimens.x2)) {
            Text(
                position.market?.title ?: "Market ${position.marketId ?: "—"}",
                fontWeight = FontWeight.Bold,
            )
            Text(
                claimable?.let {
                    "${polkamarktOutcomeLabel(PolkamarktOutcome.YES).uppercase()} " +
                        "${PolkamarktViewModel.formatUnits(it.yesShares)} · " +
                        "${polkamarktOutcomeLabel(PolkamarktOutcome.NO).uppercase()} " +
                        PolkamarktViewModel.formatUnits(it.noShares)
                } ?: "Finalized share balance not reviewed",
                style = MaterialTheme.typography.body2,
            )
            Text(
                "PnL ${position.realizedPnlUsd.orEmpty()} / ${position.unrealizedPnlUsd.orEmpty()}",
                style = MaterialTheme.typography.caption,
            )
            val marketId = position.marketId
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!reviewed && marketId != null) {
                    OutlinedButton(
                        enabled = !reviewLoading,
                        onClick = { onReview(marketId) },
                    ) { Text("Review claim") }
                } else if (claimable == null) {
                    Text(
                        "No claim at reviewed finalized block",
                        style = MaterialTheme.typography.caption,
                    )
                }
                if (
                    claimable != null &&
                    hasClaimableStatus(claimable) &&
                    claimable.claimablePayout.signum() == 1
                ) {
                    OutlinedButton(
                        enabled = mutationsEnabled,
                        onClick = { onClaim(position, false) },
                    ) {
                        Text(
                            stringResource(
                                CommonR.string.polkamarkt_claim_trader_payout,
                            )
                        )
                    }
                }
                if (
                    claimable != null &&
                    hasClaimableStatus(claimable) &&
                    claimable.isCreator &&
                    claimable.creatorFees.signum() == 1
                ) {
                    OutlinedButton(
                        enabled = mutationsEnabled,
                        onClick = { onClaim(position, true) },
                    ) {
                        Text(
                            stringResource(
                                CommonR.string.polkamarkt_claim_creator_fees,
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun hasClaimableStatus(claimable: PolkamarktClaimableDto): Boolean =
    PolkamarktWebContract.CLAIMABLE_STATUSES.any {
        it.equals(claimable.status, ignoreCase = true)
    }

@Composable
private fun polkamarktOutcomeLabel(outcome: PolkamarktOutcome): String =
    stringResource(
        if (outcome == PolkamarktOutcome.YES) {
            CommonR.string.polkamarkt_outcome_yes
        } else {
            CommonR.string.polkamarkt_outcome_no
        },
    )

@Composable
private fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            if (value == selected) {
                Button(onClick = { onSelect(value) }) { Text(label(value)) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }) { Text(label(value)) }
            }
        }
    }
}

private fun formatBps(value: Int?): String {
    if (value == null) return "—"
    val whole = value / 100
    val fraction = value % 100
    return if (fraction == 0) "$whole%" else "$whole.${fraction.toString().padStart(2, '0')}%"
}

private fun safeWebUri(value: String?): String? =
    PolkamarktExternalLinkPolicy.validated(value)

private fun isDpmMarket(market: PolkamarktMarket): Boolean {
    val mechanism = market.mechanism
        .orEmpty()
        .filter(Char::isLetterOrDigit)
        .lowercase()
    if (mechanism == "dynamicparimutuel") return true
    if (mechanism.isNotEmpty()) return false
    return market.marginalYesPriceBps != null ||
        market.marginalNoPriceBps != null ||
        market.impliedYesProbabilityBps != null ||
        market.impliedNoProbabilityBps != null
}
