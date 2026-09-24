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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import jp.co.soramitsu.common.presentation.walletDateTime
import jp.co.soramitsu.common.presentation.compose.components.WalletErrorMessage
import jp.co.soramitsu.common.presentation.compose.components.walletStatusLabel
import jp.co.soramitsu.ui_core.theme.customTypography
import java.text.NumberFormat
import kotlin.math.abs
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
                label = { Text(stringResource(CommonR.string.wallet_market_search_markets)) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PolkamarktWebContract.STATUS_FILTERS.forEach { filter ->
                    TextButton(onClick = { onStatusFilter(filter) }, modifier = Modifier.semantics { selected = state.statusFilter == filter; role = Role.Tab }) {
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
                OutlinedButton(onClick = onMineOnly, modifier = Modifier.semantics { selected = state.mineOnly }) {
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
                    TextButton(onClick = { onCategoryFilter(category) }, modifier = Modifier.semantics { selected = state.categoryFilter == category; role = Role.Tab }) {
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
            if (filteredMarkets.isEmpty() && !state.loading) {
                Text(stringResource(CommonR.string.wallet_market_empty), modifier = Modifier.padding(vertical = 24.dp))
                TextButton(onClick = {
                    onSearch(""); onStatusFilter("all"); onCategoryFilter("all")
                    if (state.mineOnly) onMineOnly()
                }) { Text(stringResource(CommonR.string.wallet_market_reset)) }
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
                Text(stringResource(CommonR.string.wallet_market_back_markets))
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
                    style = MaterialTheme.customTypography.headline2,
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
                        Text(stringResource(CommonR.string.wallet_market_claim_payouts, reviewedPayoutMarketIds.size))
                    }
                } else if (state.positions.size > 1) {
                    OutlinedButton(
                        enabled = !state.claimReviewLoading,
                        onClick = onReviewBatchClaims,
                    ) {
                        Text(
                            if (state.claimReviewLoading) {
                                "Checking available payouts…"
                            } else {
                                "Check available payouts"
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
                style = MaterialTheme.customTypography.headline2,
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
                            style = MaterialTheme.customTypography.paragraphS,
                        )
                        Text(trade.timestamp.orEmpty(), style = MaterialTheme.customTypography.paragraphS)
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
                    style = MaterialTheme.customTypography.paragraphS,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                Text(
                    stringResource(CommonR.string.polkamarkt_claim_confirmation_fee_notice),
                    style = MaterialTheme.customTypography.paragraphS,
                    color = MaterialTheme.customColors.fgPrimary,
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
                stringResource(CommonR.string.wallet_market_market_title),
                style = MaterialTheme.customTypography.displayS,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.customColors.fgPrimary,
            )
            Text(
                state.signals?.let {
                    stringResource(CommonR.string.wallet_market_activity, it.activeMarkets, it.activeAccounts)
                } ?: "SORA2 · KUSD markets",
                style = MaterialTheme.customTypography.paragraphS,
                color = MaterialTheme.customColors.fgPrimary,
            )
        }
        if (state.loading || state.refreshing) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            TextButton(onClick = onRefresh) { Text(stringResource(CommonR.string.wallet_market_refresh)) }
        }
    }
    state.errorCode?.let { WalletErrorMessage(it, onRetry = onRefresh) }
    if (state.pendingRecoveryRequired) {
        WalletErrorMessage("POLKAMARKT_RECOVERY_REQUIRED", onRetry = onRefresh)
    }
    if (state.usingCachedData) {
        Text(
            text = stringResource(CommonR.string.wallet_market_saved_markets),
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.paragraphS,
        )
    }
    state.pendingTransactions.forEach { pending ->
        Text(
            text = walletStatusLabel(pending.state),
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.paragraphS,
        )
    }
    state.lastMutation?.let {
        Text(
            text = walletStatusLabel(it.state),
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.paragraphS,
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
                    style = MaterialTheme.customTypography.paragraphS,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                Text(
                    market.status.orEmpty().uppercase(),
                    style = MaterialTheme.customTypography.paragraphS,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                market.title ?: "Market ${market.marketId ?: ""}",
                style = MaterialTheme.customTypography.headline3,
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
                style = MaterialTheme.customTypography.paragraphS,
                color = MaterialTheme.customColors.fgPrimary,
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
                style = MaterialTheme.customTypography.headline2,
                fontWeight = FontWeight.Bold,
            )
            catalogMarket.description?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.size(Dimens.x1))
                Text(it, style = MaterialTheme.customTypography.paragraphM)
            }
            Spacer(Modifier.size(Dimens.x2))
            when {
                loading -> Text(
                    "Checking the latest market information…",
                    style = MaterialTheme.customTypography.paragraphS,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                authoritativeMarket == null -> Text(
                    "The latest market information is unavailable. Trading is paused until it can be checked.",
                    style = MaterialTheme.customTypography.paragraphS,
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
                        "Market information confirmed by the network",
                        style = MaterialTheme.customTypography.paragraphS,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                    val yesShares = claimable?.yesShares ?: BigInteger.ZERO
                    val noShares = claimable?.noShares ?: BigInteger.ZERO
                    Text(
                        "Your shares · " +
                            "${polkamarktOutcomeLabel(PolkamarktOutcome.YES).uppercase()} " +
                            "${PolkamarktViewModel.formatUnits(yesShares)} · " +
                            "${polkamarktOutcomeLabel(PolkamarktOutcome.NO).uppercase()} " +
                            PolkamarktViewModel.formatUnits(noShares),
                        style = MaterialTheme.customTypography.paragraphM,
                    )
                    claimable?.claimablePayout?.takeIf { it.signum() == 1 }?.let { payout ->
                        Text(
                            "Claimable ${PolkamarktViewModel.formatUnits(payout)} KUSD",
                            style = MaterialTheme.customTypography.paragraphS,
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
                var showPricing by remember(catalogMarket.marketId) { mutableStateOf(false) }
                TextButton(onClick = { showPricing = !showPricing }) { Text(if (showPricing) stringResource(CommonR.string.wallet_market_hide_pricing) else stringResource(CommonR.string.wallet_market_show_pricing)) }
                if (showPricing) DpmPricingCurve(authoritativeMarket)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                safeWebUri(catalogMarket.metadataUri)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) { Text(stringResource(CommonR.string.wallet_market_metadata)) }
                }
                safeWebUri(catalogMarket.rulesUri)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) { Text(stringResource(CommonR.string.wallet_market_rules)) }
                }
                safeWebUri(catalogMarket.resolutionEvidenceUri)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) { Text(stringResource(CommonR.string.wallet_market_evidence)) }
                }
                safeWebUri(catalogMarket.cancellationEvidenceUri)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) {
                        Text(stringResource(CommonR.string.wallet_market_cancellation))
                    }
                }
                safeWebUri(catalogMarket.governanceUrl)?.let { uri ->
                    TextButton(onClick = { uriHandler.openUri(uri) }) { Text(stringResource(CommonR.string.wallet_market_governance)) }
                }
            }
            catalogMarket.resolutionSource?.takeIf(String::isNotBlank)?.let {
                Text(
                    "Resolution source · $it",
                    style = MaterialTheme.customTypography.paragraphS,
                    color = MaterialTheme.customColors.fgPrimary,
                )
            }
        }
    }
}

@Composable
private fun DpmPricingCurve(market: PolkamarktMarket) {
    Text(
        "Dynamic pari-mutuel pricing curve",
        style = MaterialTheme.customTypography.headline3,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "${polkamarktOutcomeLabel(PolkamarktOutcome.YES).uppercase()} / " +
            "${polkamarktOutcomeLabel(PolkamarktOutcome.NO).uppercase()} " +
            "marginal quote by demand share",
        style = MaterialTheme.customTypography.paragraphS,
        color = MaterialTheme.customColors.fgPrimary,
    )
    val yesColor = MaterialTheme.customColors.accentPrimary
    val noColor = MaterialTheme.customColors.fgSecondary
    val marker = market.impliedYesProbabilityBps
        ?.div(10_000f)
        ?.coerceIn(0.01f, 0.99f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics {
                contentDescription = "Pricing model: the horizontal axis is the YES share of demand, from 1 to 99 percent. The vertical axis is the quoted price, from zero to one. YES prices rise as YES demand rises; NO prices fall."
                role = Role.Image
            },
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(CommonR.string.wallet_market_demand_low), style = MaterialTheme.customTypography.textS)
        Text(stringResource(CommonR.string.wallet_market_demand_high), style = MaterialTheme.customTypography.textS)
    }
    Text(stringResource(CommonR.string.wallet_market_price_axis), style = MaterialTheme.customTypography.paragraphS)

}

@Composable
internal fun ProbabilityChart(snapshots: List<PolkamarktMarketSnapshot>) {
    val history = remember(snapshots) { probabilityHistory(snapshots.map {
        it.timestamp to (it.chartPriceYes ?: it.chartProbability ?: it.chartPriceNo?.let { no -> 1.0 - no })
    }) }
    var duration by remember { mutableStateOf<Long?>(null) }
    val points = remember(history, duration) { probabilityWindow(history, duration) }
    var selectedPoint by remember(points) { mutableStateOf(points.lastOrNull()) }
    val percent = remember { NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 } }
    Text(stringResource(CommonR.string.wallet_probability_history), style = MaterialTheme.customTypography.headline3)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()) {
        listOf(86_400_000L to CommonR.string.wallet_chart_range_day,
            604_800_000L to CommonR.string.wallet_chart_range_week,
            null to CommonR.string.wallet_chart_range_all).forEach { (range, label) ->
            TextButton(onClick = { duration = range }, modifier = Modifier.semantics {
                selected = duration == range; role = Role.Tab
            }) { Text(stringResource(label), fontWeight = if (duration == range) FontWeight.Bold else FontWeight.Normal) }
        }
    }
    if (points.size < 2) {
        Text(stringResource(CommonR.string.wallet_chart_insufficient), style = MaterialTheme.customTypography.paragraphS)
        return
    }
    val first = points.first()
    val last = points.last()
    val summary = stringResource(CommonR.string.wallet_chart_summary,
        percent.format(first.probability), walletDateTime(first.timestampMillis),
        percent.format(last.probability), walletDateTime(last.timestampMillis),
        percent.format(points.minOf { it.probability }), percent.format(points.maxOf { it.probability }))
    val lineColor = MaterialTheme.customColors.accentPrimary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(160.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(1.0, 0.5, 0.0).map(percent::format).forEach { Text(it, style = MaterialTheme.customTypography.textS) }
        }
        Canvas(Modifier.weight(1f).height(160.dp).semantics {
            contentDescription = summary; role = Role.Image
        }.pointerInput(points) {
            detectTapGestures { offset ->
                selectedPoint = points.minByOrNull { abs(probabilityX(it, points) - offset.x / size.width) }
            }
        }) {
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                drawLine(lineColor.copy(alpha = 0.2f), Offset(0f, size.height * fraction),
                    Offset(size.width, size.height * fraction))
            }
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = size.width * probabilityX(point, points)
                val y = size.height * (1f - point.probability.toFloat())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            selectedPoint?.let { point ->
                drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(
                    size.width * probabilityX(point, points), size.height * (1f - point.probability.toFloat())))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(walletDateTime(first.timestampMillis), modifier = Modifier.weight(1f), style = MaterialTheme.customTypography.textS)
        Text(walletDateTime(last.timestampMillis), modifier = Modifier.weight(1f), style = MaterialTheme.customTypography.textS)
    }
    selectedPoint?.let { point ->
        Text("${percent.format(point.probability)} · ${walletDateTime(point.timestampMillis)}",
            style = MaterialTheme.customTypography.paragraphM)
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
            Text(stringResource(CommonR.string.wallet_market_trade), style = MaterialTheme.customTypography.headline2, fontWeight = FontWeight.Bold)
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
                        "Available shares: ${PolkamarktViewModel.formatUnits(it)}"
                    } ?: "Share balance unavailable",
                    style = MaterialTheme.customTypography.paragraphS,
                    color = MaterialTheme.customColors.fgPrimary,
                )
            }
            Text(
                stringResource(CommonR.string.polkamarkt_ticket_slippage),
                style = MaterialTheme.customTypography.paragraphS,
                color = MaterialTheme.customColors.fgPrimary,
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PolkamarktWebContract.SLIPPAGE_PRESETS_BPS.forEach { bps ->
                    TextButton(onClick = { onSlippage(bps) }, modifier = Modifier.semantics { selected = state.slippageBps == bps; role = Role.RadioButton }) {
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
                        mutationsAvailable -> "Review trade"
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
                            stringResource(CommonR.string.wallet_market_trading_unavailable)
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
    Text(stringResource(CommonR.string.wallet_market_minimum, PolkamarktViewModel.formatUnits(quote.minimumOutput)))
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
        style = MaterialTheme.customTypography.paragraphS,
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
                } ?: "Check your latest share balance",
                style = MaterialTheme.customTypography.paragraphM,
            )
            Text(
                "PnL ${position.realizedPnlUsd.orEmpty()} / ${position.unrealizedPnlUsd.orEmpty()}",
                style = MaterialTheme.customTypography.paragraphS,
            )
            val marketId = position.marketId
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!reviewed && marketId != null) {
                    OutlinedButton(
                        enabled = !reviewLoading,
                        onClick = { onReview(marketId) },
                    ) { Text(stringResource(CommonR.string.wallet_market_review_claim)) }
                } else if (claimable == null) {
                    Text(
                        "No payout is currently available",
                        style = MaterialTheme.customTypography.paragraphS,
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
    Row(modifier = Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            if (value == selected) {
                Button(onClick = { onSelect(value) }, modifier = Modifier.weight(1f).semantics { this.selected = true; role = Role.RadioButton }) { Text(label(value)) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }, modifier = Modifier.weight(1f).semantics { this.selected = true; role = Role.RadioButton }) { Text(label(value)) }
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
