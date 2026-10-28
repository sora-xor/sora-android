@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.polkamarkt

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.dto.PolkamarktClaimableDto
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_blockexplorer_api.data.*
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger

/** Render real market screens with recorded UI callbacks; no signing or external requests. */
@RunWith(AndroidJUnit4::class)
class PolkamarktInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val state = mutableStateOf(PolkamarktScreenState(loading = false))
    private val checkpoint = "0x" + "ab".repeat(32)
    private fun units(value: Long) = BigInteger.valueOf(value).multiply(BigInteger.TEN.pow(18))
    private fun text(id: Int) = compose.activity.getString(id)
    private fun node(id: Int) = compose.onNodeWithText(text(id))
    private fun click(id: Int) = node(id).performScrollTo().performClick()
    private fun show(initial: PolkamarktScreenState) {
        state.value = initial
        compose.setContent {
            SoraAppTheme {
                CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) { events += "uri:$uri" }
                }) {
                    PolkamarktScreen(state.value,
                        onRefresh = { events += "refresh" },
                        onSearch = { events += "search:$it"; state.value = state.value.copy(search = it) },
                        onStatusFilter = { events += "status:$it"; state.value = state.value.copy(statusFilter = it) },
                        onCategoryFilter = { events += "category:$it"; state.value = state.value.copy(categoryFilter = it) },
                        onMineOnly = { events += "mine"; state.value = state.value.copy(mineOnly = !state.value.mineOnly) },
                        onMarket = { events += "market:${it.marketId}" },
                        onBackToMarkets = { events += "back" },
                        onSide = { events += "side:$it"; state.value = state.value.copy(side = it) },
                        onOutcome = { events += "outcome:$it"; state.value = state.value.copy(outcome = it) },
                        onAmount = { events += "amount:$it"; state.value = state.value.copy(amountInput = it) },
                        onSlippage = { events += "slippage:$it"; state.value = state.value.copy(slippageBps = it) },
                        onQuote = { events += "quote" }, onConfirm = { events += "confirm" },
                        onClaim = { p, fees -> events += "claim:${p.marketId}:$fees" },
                        onReviewClaim = { events += "review:$it" },
                        onSelectedClaim = { events += "selected-claim:$it" },
                        onReviewBatchClaims = { events += "review-batch" },
                        onBatchClaim = { events += "claim-batch" },
                        onConfirmClaim = { events += "confirm-claim" },
                        onDismissClaimConfirmation = { events += "dismiss-claim" })
                }
            }
        }
    }

    private fun detail() = PolkamarktScreenState(loading = false, selectedMarket = market(),
        authoritativeMarket = market(), authoritativeClaimable = claim(),
        authoritativeFinalizedBlockHash = checkpoint, mutationsEnabled = true)

    @Test fun searchMatchesDescriptionAndSelectingResultDoesNotPlaceATrade() {
        show(PolkamarktScreenState(loading = false, markets = listOf(market(), market().copy(id = "43", marketId = 43, title = "Other market", description = "Unrelated")), statusFilter = "all"))
        compose.onNode(hasSetTextAction()).performTextReplacement("moon landing")
        compose.onNodeWithText("Other market").assertDoesNotExist()
        compose.onNodeWithText("Test market").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("search:moon landing", "market:42"), events) }
    }

    @Test fun emptyFilterResetClearsSearchStatusCategoryAndOwnership() {
        show(PolkamarktScreenState(loading = false, markets = listOf(market()), search = "unmatched", statusFilter = "closed", categoryFilter = "sports", mineOnly = true, accountAddress = "owner"))
        node(R.string.wallet_market_empty).assertExists()
        click(R.string.wallet_market_reset)
        compose.onNodeWithText("Test market").assertExists()
        compose.runOnIdle { assertEquals(listOf("search:", "status:all", "category:all", "mine"), events) }
    }

    @Test fun refreshAndMarketNavigationKeepTheirCallbacksSeparate() {
        show(detail())
        click(R.string.wallet_market_refresh)
        click(R.string.wallet_market_back_markets)
        compose.runOnIdle { assertEquals(listOf("refresh", "back"), events) }
    }

    @Test fun tradeChoicesExposeExactlyOneSelectedSideAndOutcome() {
        show(detail())
        node(R.string.polkamarkt_action_buy).performScrollTo().assertIsSelected()
        node(R.string.polkamarkt_action_sell).assertIsNotSelected().performClick()
        node(R.string.polkamarkt_action_sell).assertIsSelected()
        node(R.string.polkamarkt_action_buy).assertIsNotSelected()
        node(R.string.polkamarkt_outcome_yes).assertIsSelected()
        node(R.string.polkamarkt_outcome_no).assertIsNotSelected().performClick()
        node(R.string.polkamarkt_outcome_no).assertIsSelected()
        node(R.string.polkamarkt_outcome_yes).assertIsNotSelected()
        compose.runOnIdle { assertEquals(listOf("side:SELL", "outcome:NO"), events) }
    }

    @Test fun reviewKeepsExactInputAndSlippageWithoutConfirmingATrade() {
        show(detail())
        compose.onNodeWithText("Review trade").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("12.000000000000000001")
        compose.onNodeWithText("1%").performScrollTo().performClick()
        compose.onNodeWithText("Review trade").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("amount:12.000000000000000001", "slippage:100", "quote"), events) }
    }

    @Test fun pendingRecoveryAndInFlightMutationDisableReviewAndConfirmation() {
        show(detail().copy(amountInput = "1", quote = quote()))
        for (blocked in listOf(detail().copy(mutationsEnabled = false), detail().copy(pendingRecoveryRequired = true), detail().copy(mutationLoading = true))) {
            compose.runOnIdle { state.value = blocked.copy(amountInput = "1", quote = quote()) }
            compose.onNodeWithText("Quoting temporarily unavailable").performScrollTo().assertIsNotEnabled()
            node(R.string.wallet_market_trading_unavailable).performScrollTo().assertIsNotEnabled()
        }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun reviewedQuoteShowsMinimumAndFeesThenConfirmsOnce() {
        show(detail().copy(amountInput = "1", quote = quote()))
        compose.onNodeWithText(compose.activity.getString(R.string.wallet_market_minimum, "2")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("KUSD 20 · XOR 10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm " + text(R.string.polkamarkt_action_buy)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("confirm"), events) }
    }

    @Test fun unreviewedPositionsRequestAnAuthoritativeBalanceBeforeClaiming() {
        show(PolkamarktScreenState(loading = false, positions = listOf(position()), mutationsEnabled = true))
        click(R.string.wallet_market_review_claim)
        node(R.string.polkamarkt_claim_trader_payout).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("review:42"), events) }
    }

    @Test fun reviewedPositionOffersBothPayoutAndCreatorFees() {
        show(PolkamarktScreenState(loading = false, positions = listOf(position()), mutationsEnabled = true,
            authoritativeClaims = mapOf(42L to claim()), reviewedClaimMarketIds = setOf(42)))
        click(R.string.polkamarkt_claim_trader_payout)
        click(R.string.polkamarkt_claim_creator_fees)
        compose.runOnIdle { assertEquals(listOf("claim:42:false", "claim:42:true"), events) }
    }

    @Test fun pendingClaimConfirmationRequiresExplicitConsentAndCanBeCancelledWhileBusy() {
        val confirmation = PolkamarktClaimConfirmation(PolkamarktClaimConfirmationSource.REVIEWED_POSITIONS,
            PolkamarktClaimKind.TRADER_PAYOUT, "owner", listOf(42), checkpoint, mapOf(42L to claim()))
        show(PolkamarktScreenState(loading = false, mutationsEnabled = true, mutationLoading = true, pendingClaimConfirmation = confirmation))
        node(R.string.common_confirm).assertIsNotEnabled()
        node(R.string.common_cancel).performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss-claim"), events); state.value = state.value.copy(mutationLoading = false) }
        node(R.string.common_confirm).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss-claim", "confirm-claim"), events) }
    }

    @Test fun selectedMarketClaimsAndPricingChartStayExplicit() {
        show(detail())
        click(R.string.polkamarkt_claim_trader_payout)
        click(R.string.polkamarkt_claim_creator_fees)
        click(R.string.wallet_market_show_pricing)
        compose.onNodeWithContentDescription("Pricing model:", substring = true).performScrollTo().assertIsDisplayed()
        click(R.string.wallet_market_hide_pricing)
        compose.onNodeWithContentDescription("Pricing model:", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("selected-claim:false", "selected-claim:true"), events) }
    }

    @Test fun externalLinksUseValidatedUrlsAndNeverExposeUnsafeSchemes() {
        show(detail().copy(selectedMarket = market().copy(metadataUri = "https://example.org/metadata", rulesUri = "javascript:alert(1)", governanceUrl = "file:///etc/passwd")))
        node(R.string.wallet_market_rules).assertDoesNotExist()
        node(R.string.wallet_market_governance).assertDoesNotExist()
        click(R.string.wallet_market_metadata)
        compose.runOnIdle { assertEquals(listOf("uri:https://example.org/metadata"), events) }
    }

    @Test fun batchClaimReviewRequiresExplicitReviewBeforeOfferingCombinedPayout() {
        val positions = listOf(position(), position().copy(id = "p43", marketId = 43))
        show(PolkamarktScreenState(loading = false, mutationsEnabled = true, positions = positions))
        compose.onNodeWithText("Check available payouts").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("review-batch"), events); state.value = state.value.copy(claimReviewLoading = true) }
        compose.onNodeWithText("Checking available payouts…").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(claimReviewLoading = false, authoritativeClaims = mapOf(42L to claim(), 43L to claim().copy(marketId = 43)), reviewedClaimMarketIds = setOf(42, 43)) }
        compose.onNodeWithText(compose.activity.getString(R.string.wallet_market_claim_payouts, 2)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("review-batch", "claim-batch"), events) }
    }

    @Test fun batchAndCreatorConfirmationShowExactClaimsAndRouteExplicitConsent() {
        val claims = mapOf(42L to claim(), 43L to claim().copy(marketId = 43))
        val confirmation = PolkamarktClaimConfirmation(PolkamarktClaimConfirmationSource.REVIEWED_POSITIONS, PolkamarktClaimKind.TRADER_PAYOUT, "owner", listOf(42, 43), checkpoint, claims)
        show(PolkamarktScreenState(loading = false, mutationsEnabled = true, pendingClaimConfirmation = confirmation))
        node(R.string.common_confirm).performClick()
        compose.runOnIdle { assertEquals(listOf("confirm-claim"), events); state.value = state.value.copy(pendingClaimConfirmation = confirmation.copy(kind = PolkamarktClaimKind.CREATOR_FEES, marketIds = listOf(42), claims = mapOf(42L to claim()))) }
        node(R.string.polkamarkt_claim_creator_fees).assertExists()
        node(R.string.common_cancel).performClick()
        compose.runOnIdle { assertEquals(listOf("confirm-claim", "dismiss-claim"), events) }
    }

    @Test fun chartRendersChronologicalProbabilityAndSelectionWithoutSubmittingTrade() {
        val first = PolkamarktMarketSnapshot("s1", 42, 1_800_000_000_000, 80, "trade", 0.4, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, "Active")
        show(detail().copy(snapshots = listOf(first, first.copy(id = "s2", timestamp = 1_800_000_060_000, chartProbability = null, chartPriceNo = 0.3))))
        val chart = compose.onNodeWithContentDescription("Yes probability:", substring = true)
        chart.performScrollTo().assertIsDisplayed().performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun tradeHistoryShowsSingleAndBatchMarketIdentity() {
        val trade = PolkamarktAccountTrade("t1", "owner", 42, listOf(42), "BUY", "YES", "3", "5", null, "5", "0.6", "0.1", "0", "2026-10-01", 90, checkpoint, checkpoint, market())
        show(PolkamarktScreenState(loading = false, trades = listOf(trade, trade.copy(id = "t2", marketIds = listOf(42, 43), side = "CLAIM", outcome = null))))
        compose.onNodeWithText("BUY YES · Market 42").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("CLAIM  · 2 markets · 42, 43").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun cachedCatalogAndPendingMutationStatesRemainVisibleWhileRefreshing() {
        show(PolkamarktScreenState(loading = false, usingCachedData = true, signals = PolkamarktSignals("10", 3, 5, "2", emptyList(), emptyList(), null),
            pendingTransactions = listOf(PolkamarktPendingTransaction("p1", checkpoint, "BUY", "1", "SUBMITTED", false)),
            lastMutation = PolkamarktMutationResult("m1", checkpoint, "FINALIZED")))
        node(R.string.wallet_market_saved_markets).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.wallet_market_activity, 3, 5)).assertExists()
        compose.runOnIdle { state.value = state.value.copy(refreshing = true) }
        node(R.string.wallet_market_refresh).assertDoesNotExist()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    private fun claim() = PolkamarktClaimableDto(42, "owner", "Resolved", "Yes", units(4), units(2), units(3), units(4), units(4), units(1), true)
    private fun position() = PolkamarktAccountPosition("p42", "owner", 42, "Yes", "4", "4", "2", "3", "3", "4", "1", "0", "4", true, "Resolved", "2026-10-01", market())
    private fun quote() = PolkamarktTradeQuote("q", "nonce", "owner", market(), PolkamarktTradeSide.BUY, PolkamarktOutcome.YES,
        units(1), units(3), units(2), units(1), units(1), checkpoint, "DynamicParimutuel", BigInteger.valueOf(100), units(20), units(10))
    private fun market() = PolkamarktMarket(
        id = "42", marketId = 42, title = "Test market", category = "science", tags = "space", description = "Moon landing prediction",
        rulesUri = null, resolutionSource = "Public observations", closeBlock = 100, status = "Active", mechanism = "DynamicParimutuel", creator = "owner",
        collateralAsset = "KUSD", creatorFees = "1", liquidityUsd = "100", volumeUsd = "500", chartProbability = 0.6, chartPriceYes = 0.6, chartPriceNo = 0.4,
        virtualDepth = "100", dpmCollateral = "10", realYesShares = "4", realNoShares = "2", marginalYesPriceBps = 6000, marginalNoPriceBps = 4000,
        impliedYesProbabilityBps = 6000, impliedNoProbabilityBps = 4000, collateral = "10", yesShares = "4", noShares = "2", resolutionOutcome = null,
        resolutionEvidenceUri = null, governanceUrl = null, updatedAtBlock = 90, timestamp = 1_800_000_000_000, displayYesProbabilityBps = 6000, displayNoProbabilityBps = 4000)
}
