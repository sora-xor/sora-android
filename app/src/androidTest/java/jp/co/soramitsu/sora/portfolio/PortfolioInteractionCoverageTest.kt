@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.portfolio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.*
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.*
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.TiltState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Real rendering and user actions; fixture wallets never reach a signer or network transport. */
@RunWith(AndroidJUnit4::class)
class PortfolioInteractionCoverageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val actions = Actions()
    private val now = 1_800_000_000_000L
    private val xor = PortfolioToken(AssetHolder.XOR_ASSET_ID, "XOR", "SORA", null, 18, 2.0, 0.05)
    private val valToken = PortfolioToken(PortfolioSlots.VAL_ID, "VAL", "Validator", null, 18, 1.0, -0.03)
    private val unpriced = PortfolioToken("unpriced", "UNKNOWN", "Unpriced token", null, 18, null, null)
    private val precise = BigDecimal("12.34567890123456789")
    private val source = PortfolioHolding("main", WalletNetworkId.SORA2, xor, BigDecimal("20"), precise, false, "main-sora2")
    private val secondary = PortfolioHolding("savings", WalletNetworkId.MINAMOTO, xor, BigDecimal("30"), BigDecimal("25"), false, "savings-minamoto")
    private val testnet = PortfolioHolding("main", WalletNetworkId.TAIRA, xor, BigDecimal("1000"), BigDecimal("1000"), false, "main-taira", false)

    private fun model(
        scope: PortfolioScope = PortfolioScope.ALL,
        holdings: List<PortfolioHolding> = listOf(source, secondary, testnet,
            source.copy(token = valToken, total = BigDecimal("4")),
            source.copy(token = unpriced), source.copy(token = valToken.copy(id = "hidden", symbol = "HIDDEN"), hidden = true)),
        expanded: Boolean = true,
        wallets: List<PortfolioWallet> = listOf(PortfolioWallet("main", "Main wallet", true), PortfolioWallet("savings", "Savings wallet", false)),
        sync: (PortfolioWallet) -> WalletSync = { WalletSync(now, live = it.selected) },
    ): PortfolioModel {
        val snapshot = PortfolioSnapshot(wallets, holdings,
            wallets.associate { it.id to sync(it) }, true,
            wallets.associate { wallet -> wallet.id to WalletNetworkId.entries.associateWith { "${wallet.id}-${it.wireId}" } }, "$")
        val ids = PortfolioCalculator.scopeWalletIds(snapshot, scope)
        val groups = PortfolioCalculator.groups(snapshot, ids, PortfolioLens.TOKENS)
        return PortfolioModel(snapshot, false, now, scope, ids,
            PortfolioCalculator.scopeTotals(snapshot, ids), PortfolioCalculator.worstFreshness(snapshot, ids, now), groups,
            PortfolioCalculator.fan(groups), groups.mapIndexed { index, group -> group.key to index }.toMap(),
            mapOf("main" to 0, "savings" to 1), PortfolioCalculator.walletSummaries(snapshot, now),
            PortfolioCalculator.scopeTotals(snapshot, wallets.map { it.id }), PortfolioCalculator.worstFreshness(snapshot, wallets.map { it.id }, now),
            null, if (expanded) groups.map { it.key }.toSet() else emptySet(), false)
    }

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun node(id: Int) = compose.onNodeWithText(text(id))
    private fun show(content: @Composable () -> Unit) {
        compose.setContent { SoraAppTheme { content() } }
    }
    private fun sheet(model: PortfolioModel, sheet: PortfolioSheet, busy: Boolean = false) = show {
        PortfolioSheetHost(model, sheet, PortfolioFormat(NumbersFormatter(), "$"), busy, actions)
    }
    private fun click(id: Int) { node(id).performScrollTo().performClick() }

    @Test fun emptyWalletOffersReceiveWithoutShowingHoldings() {
        val model = model(holdings = emptyList())
        show { PortfolioHubScreen(rememberScrollState(), model, null, false, null, actions, actions, {}, {}) }
        node(R.string.portfolio_wallet_ready).performScrollTo().assertIsDisplayed()
        node(R.string.portfolio_no_balance_yet).performScrollTo().assertIsDisplayed()
        click(R.string.common_receive)
        compose.runOnIdle { assertEquals(listOf("receive"), actions.events) }
        compose.onNodeWithText("XOR").assertDoesNotExist()
    }

    @Test fun heroQuickActionsHandOffWithoutSubmittingAnything() {
        val model = model()
        show { Column(Modifier.verticalScroll(rememberScrollState())) {
            PortfolioHero(model, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), actions)
        } }
        click(R.string.common_send)
        click(R.string.common_receive)
        click(R.string.portfolio_move)
        click(R.string.portfolio_swap)
        compose.runOnIdle { assertEquals(listOf("send", "receive", "move", "swap"), actions.events) }
    }

    @Test fun ringTapsPickTheirSliceWhileThePlanetTakesNone() {
        val model = model()
        show { Column(Modifier.verticalScroll(rememberScrollState())) {
            PortfolioHero(model, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), actions)
        } }
        compose.onRoot().performTouchInput {
            val planet = Offset(width / 2f, SkyGeometry.CENTER_Y.toPx())
            val pose = RingPose.at(planet, SkyGeometry.RING_RADIUS.toPx(), 0f, 0f)
            click(planet)
            // XOR, the largest slice, wraps round behind the planet; VAL comes round to the front.
            click(pose.point(90f))
            click(pose.point(0f))
        }
        val (xor, valSlice) = model.fan.map { it.key ?: OTHER_KEY }
        compose.runOnIdle { assertEquals(listOf("legend:$valSlice", "legend:$xor"), actions.events) }
    }

    @Test fun beforeTheFirstPictureNothingSpinsAndThereIsNoNumber() {
        show { PortfolioHubScreen(rememberScrollState(), null, null, false, null, actions, actions, {}, {}) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onNodeWithText(PortfolioFormat.NO_VALUE).assertExists()
        compose.onNodeWithText("$0", substring = true).assertDoesNotExist()
    }

    @Test fun legendChipsReportTheirTokenAndOther() {
        val many = (1..7).map { i ->
            source.copy(token = valToken.copy(id = "token$i", symbol = "T$i", price = 1.0), total = BigDecimal(i * 10))
        }
        val model = model(holdings = many)
        show { Column(Modifier.verticalScroll(rememberScrollState())) {
            PortfolioHero(model, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), actions)
        } }
        compose.onNode(hasContentDescription("T7", substring = true)).performClick()
        compose.onNode(hasContentDescription(text(R.string.portfolio_other), substring = true)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("legend:token7", "legend:$OTHER_KEY"), actions.events) }
    }

    @Test fun holdingsWithoutPricesShowAmountsADashAndPriceUnavailableNeverZero() {
        val unpricedXor = xor.copy(price = null, change24h = null)
        val model = model(
            holdings = listOf(
                source.copy(token = unpricedXor, total = BigDecimal("25000")),
                secondary.copy(token = unpricedXor, total = BigDecimal("100"), network = WalletNetworkId.SORA2, address = "savings"),
            ),
            expanded = false,
        )
        val format = PortfolioFormat(NumbersFormatter(), "$")
        show { Column(Modifier.verticalScroll(rememberScrollState())) {
            PortfolioHero(model, format, TiltState(), actions)
            PortfolioList(model, format, actions)
        } }
        node(R.string.tx_outcome_quote_unavailable).assertExists()
        compose.onNodeWithContentDescription(text(R.string.tx_outcome_quote_unavailable), substring = true).assertExists()
        // The amount is the row's number, with nothing under it.
        compose.onNodeWithText(format.amount(BigDecimal("25100"), "XOR")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$0", substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.portfolio_excluded_unpriced, 2), substring = true).assertDoesNotExist()
    }

    @Test fun someMissingPricesShowThePricedTotalAndSayWhatIsLeftOut() {
        val model = model(holdings = listOf(source, source.copy(token = unpriced)))
        val format = PortfolioFormat(NumbersFormatter(), "$")
        show { Column(Modifier.verticalScroll(rememberScrollState())) { PortfolioHero(model, format, TiltState(), actions) } }
        compose.onNodeWithContentDescription(text(R.string.portfolio_total_balance) + ", " + format.fiat(40.0), substring = true).assertExists()
        compose.onNodeWithText(text(R.string.portfolio_excluded_unpriced, 1), substring = true).assertExists()
        node(R.string.tx_outcome_quote_unavailable).assertDoesNotExist()
    }

    @Test fun aTotalValuedWithOldPricesSaysHowOldTheyAre() {
        val fresh = model(holdings = listOf(source))
        val old = fresh.copy(snapshot = fresh.snapshot.copy(pricesReadAtMillis = now - PortfolioFreshness.PRICES_OLD_AFTER_MS - 1))
        val format = PortfolioFormat(NumbersFormatter(), "$")
        show { Column(Modifier.verticalScroll(rememberScrollState())) { PortfolioHero(old, format, TiltState(), actions) } }
        val prefix = text(R.string.portfolio_prices_as_of, "")
        compose.onNodeWithText(prefix, substring = true).assertExists()
    }

    @Test fun testnetOnlyHoldingsHaveNoTotalAndSayTheyHaveNoValue() {
        val model = model(holdings = listOf(testnet), expanded = false)
        val format = PortfolioFormat(NumbersFormatter(), "$")
        show { Column(Modifier.verticalScroll(rememberScrollState())) {
            PortfolioHero(model, format, TiltState(), actions)
            PortfolioList(model, format, actions)
        } }
        node(R.string.portfolio_route_test).assertExists()
        node(R.string.network_tag_testnet).assertExists()
        // A dash over the amount on the row, as for every testnet holding.
        compose.onAllNodesWithText(PortfolioFormat.NO_VALUE).onFirst().assertExists()
        compose.onNodeWithText(format.amount(BigDecimal("1000"), "XOR")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$0", substring = true).assertDoesNotExist()
    }

    @Test fun aWalletThatIsStillBeingReadIsNotEmptyAndNotZero() {
        val model = model(holdings = emptyList(), sync = { WalletSync(null, unread = true) })
        assertFalse(model.empty)
        show { PortfolioHubScreen(rememberScrollState(), model, null, false, null, actions, actions, {}, {}) }
        node(R.string.portfolio_wallet_ready).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.portfolio_reading_balances), substring = true).assertExists()
        node(R.string.portfolio_reading_balances).performScrollTo().assertIsDisplayed()
        // No age to show yet, so no chip.
        node(R.string.portfolio_state_reading).assertDoesNotExist()
        compose.onNodeWithText("$0", substring = true).assertDoesNotExist()
    }

    @Test fun aWalletThatCouldNotBeReadSaysSoAndIsNotZero() {
        val model = model(holdings = emptyList(), sync = { WalletSync(null, unreachable = setOf(WalletNetworkId.SORA2), unread = true) })
        show { PortfolioHubScreen(rememberScrollState(), model, null, false, null, actions, actions, {}, {}) }
        compose.onAllNodesWithText(text(R.string.balance_unavailable)).assertCountEquals(2)
        node(R.string.portfolio_wallet_ready).assertDoesNotExist()
        compose.onNodeWithText("$0", substring = true).assertDoesNotExist()
    }

    @Test fun walletsSheetNeverShowsAZeroForAnUnknownTotal() {
        val model = model(holdings = listOf(source.copy(token = unpriced)))
        sheet(model, PortfolioSheet.Wallets)
        compose.onAllNodesWithText(PortfolioFormat.NO_VALUE).assertCountEquals(3)
        compose.onNodeWithText("$0", substring = true).assertDoesNotExist()
    }

    @Test fun withOneWalletTheWalletsSheetHasNoAllWalletsRowOrRefreshBanner() {
        val one = model(scope = PortfolioScope.SELECTED, wallets = listOf(PortfolioWallet("main", "Main wallet", true)), holdings = listOf(source))
        sheet(one, PortfolioSheet.Wallets)
        compose.onNodeWithText("Main wallet").assertExists()
        node(R.string.portfolio_all_wallets).assertDoesNotExist()
        node(R.string.portfolio_refresh_all).assertDoesNotExist()
        node(R.string.portfolio_wallets_note).assertDoesNotExist()
        click(R.string.portfolio_manage_wallets)
        click(R.string.portfolio_manage_assets)
        compose.runOnIdle { assertEquals(listOf("manage-wallets", "manage-assets"), actions.events) }
    }

    @Test fun assetsCardListsEveryTokenAndKeepsHiddenOnesOut() {
        val current = model(expanded = false)
        show { Column(Modifier.verticalScroll(rememberScrollState())) {
            PortfolioList(current, PortfolioFormat(NumbersFormatter(), "$"), actions)
        } }
        compose.onAllNodesWithText("HIDDEN", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Unpriced token").assertExists()
        // Several holdings: the row opens under itself. One holding: straight to "Where it lives".
        compose.onNodeWithText("SORA").performScrollTo().performClick()
        compose.onNodeWithText("Validator").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("toggle:${xor.id}", "token:${valToken.id}"), actions.events) }
    }

    @Test fun anOpenRowListsItsHoldingsAndEachOpensWhereItLives() {
        val current = model(holdings = listOf(source, secondary, testnet))
        val format = PortfolioFormat(NumbersFormatter(), "$")
        show { Column(Modifier.verticalScroll(rememberScrollState())) { PortfolioList(current, format, actions) } }
        compose.onNodeWithText("Savings wallet").performScrollTo().performClick()
        // The tapped holding goes with it, so the page opens on that wallet, not on the largest.
        compose.runOnIdle { assertEquals(listOf("token:${xor.id}@${secondary.key}"), actions.events) }
        // The testnet holding: a dash over its amount, never a value.
        compose.onNodeWithText(format.amount(BigDecimal("1000"), "XOR")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(PortfolioFormat.NO_VALUE).assertExists()
    }

    @Test fun walletsSheetPreservesSelectionAndProvidesRefreshAndManageActions() {
        sheet(model(scope = PortfolioScope.SELECTED), PortfolioSheet.Wallets)
        node(R.string.portfolio_all_wallets).performClick()
        compose.onNodeWithText("Savings wallet").performClick()
        click(R.string.portfolio_refresh_all)
        click(R.string.portfolio_manage_wallets)
        click(R.string.portfolio_manage_assets)
        compose.runOnIdle { assertEquals(listOf("all", "wallet:savings", "refresh", "manage-wallets", "manage-assets"), actions.events) }
    }

    @Test fun countedBalanceSheetExplainsHiddenUnpricedAndTestnetExclusions() {
        sheet(model(), PortfolioSheet.HowWeCount)
        node(R.string.portfolio_how_we_count).assertIsDisplayed()
        node(R.string.portfolio_hidden_by_you).assertExists()
        node(R.string.portfolio_without_price).assertExists()
        node(R.string.portfolio_testnets).assertExists()
        click(R.string.portfolio_manage_hidden)
        compose.runOnIdle { assertEquals(listOf("manage-assets"), actions.events) }
    }

    @Test fun everySyncStateShowsItsMeaningAndKeepsRetryAvailable() {
        val current = mutableStateOf(model())
        show { PortfolioSheetHost(current.value, PortfolioSheet.Sync, PortfolioFormat(NumbersFormatter(), "$"), false, actions) }
        for ((kind, resource) in listOf(FreshnessKind.LIVE to R.string.portfolio_sync_live,
            FreshnessKind.SAVED to R.string.portfolio_sync_saved, FreshnessKind.READING to R.string.portfolio_reading_balances,
            FreshnessKind.OFFLINE to R.string.portfolio_sync_offline)) {
            compose.runOnIdle { current.value = model().copy(freshness = WalletFreshness(kind, now - 60_000)) }
            node(resource).assertExists()
            click(R.string.portfolio_refresh_all)
        }
        compose.runOnIdle { current.value = model().copy(freshness = WalletFreshness(FreshnessKind.PARTIAL, now, setOf(WalletNetworkId.TAIRA))) }
        compose.onNodeWithText("could not be reached", substring = true).assertExists()
        click(R.string.portfolio_refresh_all)
        compose.runOnIdle { assertEquals(List(5) { "refresh" }, actions.events) }
    }

    @Test fun moveRejectsMalformedZeroAndOverBalanceAmounts() {
        sheet(model(), PortfolioSheet.Move(source.key))
        val amount = compose.onNode(hasSetTextAction())
        for (invalid in listOf("1..2", "0", "999")) {
            amount.performScrollTo().performTextReplacement(invalid)
            node(R.string.portfolio_review_in_send).performScrollTo().assertIsNotEnabled()
        }
        amount.performScrollTo().performTextReplacement("1.25")
        node(R.string.portfolio_review_in_send).performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertTrue(actions.events.isEmpty()) }
    }

    @Test fun maxMovePassesTheExactTransferableAmountToReview() {
        sheet(model(), PortfolioSheet.Move(source.key))
        click(R.string.portfolio_max)
        click(R.string.portfolio_review_in_send)
        compose.runOnIdle {
            assertEquals(source.key, actions.move?.first)
            assertEquals("savings", actions.move?.second)
            assertEquals(precise, actions.move?.third)
        }
    }

    @Test fun busyMoveCannotBeReviewedTwice() {
        sheet(model(), PortfolioSheet.Move(source.key), busy = true)
        node(R.string.portfolio_review_in_send).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertNull(actions.move) }
    }

    @Test fun unavailableNetworkOffersReceiveInsteadOfAnUnsupportedSwap() {
        sheet(model(), PortfolioSheet.Swap(testnet.key))
        node(R.string.portfolio_open_polkaswap).assertDoesNotExist()
        click(R.string.portfolio_receive_on_sora2)
        compose.runOnIdle { assertEquals(listOf("receive:main"), actions.events) }
    }

    @Test fun swapSelectionPassesChosenPairToExistingPolkaswapFlow() {
        sheet(model(), PortfolioSheet.Swap(source.key))
        compose.onNodeWithText("VAL").performScrollTo().performClick()
        click(R.string.portfolio_open_polkaswap)
        compose.runOnIdle { assertEquals(listOf("swap:${source.key}:${PortfolioSlots.VAL_ID}"), actions.events) }
    }

    @Test fun walletPickerNamesDestinationWalletForReceive() {
        sheet(model(), PortfolioSheet.PickWallet(WalletAction.RECEIVE))
        node(R.string.portfolio_receive_to).assertExists()
        compose.onNodeWithText("Savings wallet").performClick()
        compose.runOnIdle { assertEquals(listOf("pick:RECEIVE:savings"), actions.events) }
    }

    @Test fun walletPickerAsksWhichWalletToSendFrom() {
        sheet(model(), PortfolioSheet.PickWallet(WalletAction.SEND))
        node(R.string.portfolio_send_from).assertExists()
        compose.onNodeWithText("Main wallet").performClick()
        compose.runOnIdle { assertEquals(listOf("pick:SEND:main"), actions.events) }
    }

    @Test fun missingTokenOffersBackWithoutShowingStaleHoldings() {
        val model = model()
        show { WhereItLivesScreen(model, "missing", PortfolioFormat(NumbersFormatter(), "$"), TiltState(), rememberScrollState(), actions) }
        node(R.string.portfolio_token_gone).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        compose.runOnIdle { assertEquals(listOf("back"), actions.events) }
    }

    @Test fun tokenLocationsShowSourceAndNetworkBreakdownAndOpenAssetDetails() {
        val model = model()
        show { WhereItLivesScreen(model, xor.id, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), rememberScrollState(), actions) }
        node(R.string.portfolio_where_it_lives).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Main wallet").onFirst().assertExists()
        compose.onAllNodesWithText("Savings wallet").onFirst().assertExists()
        compose.onNode(hasText("SORA2") and hasText("Main wallet") and hasClickAction())
            .performScrollTo().performClick()
        compose.onAllNodesWithText(text(R.string.portfolio_asset_details)).onFirst().performScrollTo().performClick()
        compose.runOnIdle { assertTrue(actions.events.any { it.startsWith("details:") }) }
    }

    @Test fun aSwapReadyHoldingLeadsWithSwapThenSendAndMove() {
        val model = model(holdings = listOf(source))
        show { WhereItLivesScreen(model, xor.id, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), rememberScrollState(), actions) }
        click(R.string.portfolio_swap_on_polkaswap)
        click(R.string.common_send)
        click(R.string.portfolio_move)
        compose.runOnIdle {
            assertEquals(listOf("swap:${source.key}", "send:${source.key}", "move:${source.key}"), actions.events)
        }
    }

    @Test fun aHoldingOfAnotherNetworkLeadsWithSendAndSaysWhyItCannotSwap() {
        val model = model(holdings = listOf(testnet))
        show { WhereItLivesScreen(model, xor.id, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), rememberScrollState(), actions) }
        node(R.string.portfolio_swap_on_polkaswap).assertDoesNotExist()
        // The route card says only that a testnet asset has no value; there is no route to draw.
        node(R.string.portfolio_route_test).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.portfolio_send_unavailable, "Taira")).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.portfolio_send_on, "Taira")).performScrollTo().assertIsNotEnabled()
        click(R.string.portfolio_receive_on_sora2)
        compose.runOnIdle { assertEquals(listOf("receive:main"), actions.events) }
    }

    @Test fun aHoldingOutsideSora2SaysPolkaswapRunsOnSora2() {
        val model = model(holdings = listOf(secondary))
        show { WhereItLivesScreen(model, xor.id, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), rememberScrollState(), actions) }
        compose.onNodeWithText(text(R.string.portfolio_route_runs_on_sora2), substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun aTokenWithoutAPriceHasNoPriceNoteOnItsRowAndNoShareOfTheTotal() {
        val format = PortfolioFormat(NumbersFormatter(), "$")
        val model = model(holdings = listOf(source.copy(token = unpriced)))
        show { WhereItLivesScreen(model, unpriced.id, format, TiltState(), rememberScrollState(), actions) }
        node(R.string.tx_outcome_quote_unavailable).assertExists()
        compose.onNodeWithText(text(R.string.portfolio_share_of_all, format.share(0.0))).assertDoesNotExist()
        // The header's amount, the row's number, and the breakdown's total.
        compose.onAllNodesWithText(format.amount(source.total, unpriced.symbol)).assertCountEquals(3)
    }

    @Test fun aPricedTokenSaysItsShareOfTheTotal() {
        val format = PortfolioFormat(NumbersFormatter(), "$")
        val model = model(holdings = listOf(source))
        show { WhereItLivesScreen(model, xor.id, format, TiltState(), rememberScrollState(), actions) }
        compose.onNodeWithText(text(R.string.portfolio_share_of_all, format.share(1.0))).assertExists()
        compose.onAllNodesWithText(format.fiat(40.0)).assertCountEquals(2)
    }

    @Test fun aHoldingInAnotherWalletSaysItSwitchesFirst() {
        val other = source.copy(walletId = "savings", address = "savings-sora2")
        val model = model(holdings = listOf(other))
        show { WhereItLivesScreen(model, xor.id, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), rememberScrollState(), actions) }
        compose.onNodeWithText(text(R.string.portfolio_switch_note, "Savings wallet")).performScrollTo().assertIsDisplayed()
    }

    @Test fun aHoldingInTheSelectedWalletHasNoSwitchNote() {
        val model = model(holdings = listOf(source))
        show { WhereItLivesScreen(model, xor.id, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), rememberScrollState(), actions) }
        compose.onNodeWithText(text(R.string.portfolio_switch_note, "Main wallet")).assertDoesNotExist()
    }

    @Test fun tappingAnotherHoldingOpensItsNumbers() {
        val other = source.copy(walletId = "savings", address = "savings-sora2", total = BigDecimal("5"), transferable = BigDecimal("3"))
        val format = PortfolioFormat(NumbersFormatter(), "$")
        show { WhereItLivesScreen(model(holdings = listOf(source, other)), xor.id, format, TiltState(), rememberScrollState(), actions) }
        compose.onNodeWithText("Savings wallet").performScrollTo().performClick()
        compose.onNodeWithText(format.amount(BigDecimal("2"), "XOR")).performScrollTo().assertExists()
    }

    @Test fun polkaswapCardOpensTheSwapForTheTokensSora2Holding() {
        val model = model(holdings = listOf(source))
        show { WhereItLivesScreen(model, xor.id, PortfolioFormat(NumbersFormatter(), "$"), TiltState(), rememberScrollState(), actions) }
        click(R.string.portfolio_open_polkaswap)
        compose.runOnIdle { assertEquals(listOf("polkaswap:${xor.id}:${source.key}"), actions.events) }
    }

    @Test fun moveChooserUsesTheSelectedHoldingAndDoesNotSubmitFromTheList() {
        sheet(model(holdings = listOf(source)), PortfolioSheet.Move(null))
        compose.onNodeWithText(text(R.string.portfolio_choose_holding), ignoreCase = true).assertExists()
        compose.onNodeWithText("XOR · SORA2").performClick()
        compose.runOnIdle { assertEquals(listOf("sheet:${PortfolioSheet.Move(source.key)}"), actions.events); assertNull(actions.move) }
    }
    @Test fun swapChooserUsesTheSelectedHoldingAndDoesNotSubmitFromTheList() {
        sheet(model(holdings = listOf(source)), PortfolioSheet.Swap(null))
        compose.onNodeWithText(text(R.string.portfolio_choose_holding), ignoreCase = true).assertExists()
        compose.onNodeWithText("XOR · SORA2").performClick()
        compose.runOnIdle { assertEquals(listOf("sheet:${PortfolioSheet.Swap(source.key)}"), actions.events) }
    }
    @Test fun noTransferableHoldingsExplainWhyMoveCannotStart() {
        sheet(model(holdings = emptyList()), PortfolioSheet.Move(null))
        node(R.string.portfolio_nothing_to_move).assertExists()
        node(R.string.portfolio_review_in_send).assertDoesNotExist()
        compose.runOnIdle { assertNull(actions.move) }
    }
    @Test fun singleWalletMoveOffersAccountManagement() {
        val original = model(holdings = emptyList())
        sheet(original.copy(snapshot = original.snapshot.copy(wallets = original.snapshot.wallets.take(1))), PortfolioSheet.Move(null))
        node(R.string.portfolio_move_needs_wallets).assertExists()
        click(R.string.portfolio_manage_wallets)
        compose.runOnIdle { assertEquals(listOf("manage-wallets"), actions.events) }
    }
    @Test fun noSwapHoldingsOfferReceiveOnTheSelectedWallet() {
        sheet(model(holdings = emptyList()), PortfolioSheet.Swap(null))
        node(R.string.portfolio_nothing_to_swap).assertExists()
        click(R.string.portfolio_receive_on_sora2)
        compose.runOnIdle { assertEquals(listOf("receive:main"), actions.events) }
    }
    @Test fun nontransferableMoveExplainsTheBalanceWithoutOfferingReview() {
        val empty = source.copy(transferable = BigDecimal.ZERO)
        sheet(model(holdings = listOf(empty)), PortfolioSheet.Move(empty.key))
        node(R.string.portfolio_nothing_transferable).assertExists()
        node(R.string.portfolio_review_in_send).assertDoesNotExist()
    }
    @Test fun disabledNetworkMoveOffersReceiveOnSora2() {
        sheet(model(holdings = listOf(testnet)), PortfolioSheet.Move(testnet.key))
        compose.onNodeWithText(compose.activity.getString(R.string.portfolio_send_unavailable, "Taira")).assertExists()
        click(R.string.portfolio_receive_on_sora2)
        compose.runOnIdle { assertEquals(listOf("receive:main"), actions.events); assertNull(actions.move) }
    }
    @Test fun anotherWalletMoveNamesTheSwitchAndPassesItsExactHolding() {
        val other = source.copy(walletId = "savings", address = "savings-sora2")
        sheet(model(holdings = listOf(other)), PortfolioSheet.Move(other.key))
        compose.onNodeWithText(compose.activity.getString(R.string.portfolio_switch_and_review, "Savings wallet")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(other.key, actions.move!!.first); assertEquals("main", actions.move!!.second); assertNull(actions.move!!.third) }
    }
    @Test fun anotherWalletSwapNamesTheSwitchBeforeOpeningPolkaswap() {
        val other = source.copy(walletId = "savings", address = "savings-sora2")
        sheet(model(holdings = listOf(other)), PortfolioSheet.Swap(other.key))
        compose.onNodeWithText(compose.activity.getString(R.string.portfolio_switch_and_review, "Savings wallet")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("swap:${other.key}:${PortfolioSlots.VAL_ID}"), actions.events) }
    }
    @Test fun nontransferableSwapExplainsTheBalanceAndOffersReceive() {
        val empty = source.copy(transferable = BigDecimal.ZERO)
        sheet(model(holdings = listOf(empty)), PortfolioSheet.Swap(empty.key))
        node(R.string.portfolio_nothing_transferable).assertExists()
        node(R.string.portfolio_open_polkaswap).assertDoesNotExist()
    }

    private class Actions : PortfolioHomeActions, PortfolioSheetActions, WhereItLivesActions {
        val events = mutableListOf<String>()
        var move: Triple<String, String, BigDecimal?>? = null
        override fun onScopeChip() { events += "scope" }
        override fun onScan() { events += "scan" }
        override fun onFreshness() { events += "freshness" }
        override fun onCaption() { events += "caption" }
        override fun onLegend(key: String) { events += "legend:$key" }
        override fun onSend() { events += "send" }
        override fun onReceive() { events += "receive" }
        override fun onMove() { events += "move" }
        override fun onSwap() { events += "swap" }
        override fun onToggleRow(key: String) { events += "toggle:$key" }
        override fun onOpenToken(tokenId: String, holdingKey: String?) {
            events += "token:$tokenId" + holdingKey?.let { "@$it" }.orEmpty()
        }
        override fun onMoveHolding(holdingKey: String) { events += "move:$holdingKey" }
        override fun onSwapHolding(holdingKey: String) { events += "swap:$holdingKey" }
        override fun onDismiss() { events += "dismiss" }
        override fun onSelectAll() { events += "all" }
        override fun onSelectWallet(walletId: String) { events += "wallet:$walletId" }
        override fun onRefreshAll() { events += "refresh" }
        override fun onManageWallets() { events += "manage-wallets" }
        override fun onManageAssets() { events += "manage-assets" }
        override fun onPickWallet(action: WalletAction, walletId: String) { events += "pick:$action:$walletId" }
        override fun onReviewMove(holdingKey: String, destinationWalletId: String, amount: BigDecimal?) { move = Triple(holdingKey, destinationWalletId, amount) }
        override fun onOpenSwap(holdingKey: String, targetTokenId: String) { events += "swap:$holdingKey:$targetTokenId" }
        override fun onReceiveOnSora2(walletId: String) { events += "receive:$walletId" }
        override fun onShowSheet(sheet: PortfolioSheet) { events += "sheet:$sheet" }
        override fun onBack() { events += "back" }
        override fun onSwapSheet(holdingKey: String) { events += "swap:$holdingKey" }
        override fun onMoveSheet(holdingKey: String) { events += "move:$holdingKey" }
        override fun onSend(holdingKey: String) { events += "send:$holdingKey" }
        override fun onAssetDetails(holdingKey: String) { events += "details:$holdingKey" }
        override fun onOpenPolkaswap(tokenId: String, holdingKey: String?) { events += "polkaswap:$tokenId:$holdingKey" }
    }
}
