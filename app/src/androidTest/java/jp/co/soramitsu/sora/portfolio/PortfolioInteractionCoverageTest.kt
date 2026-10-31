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
        lens: PortfolioLens = PortfolioLens.TOKENS,
        holdings: List<PortfolioHolding> = listOf(source, secondary, testnet,
            source.copy(token = valToken, total = BigDecimal("4")),
            source.copy(token = unpriced), source.copy(token = valToken.copy(id = "hidden", symbol = "HIDDEN"), hidden = true)),
        expanded: Boolean = true,
    ): PortfolioModel {
        val wallets = listOf(PortfolioWallet("main", "Main wallet", true), PortfolioWallet("savings", "Savings wallet", false))
        val snapshot = PortfolioSnapshot(wallets, holdings,
            wallets.associate { it.id to WalletSync(now, live = it.selected) }, true,
            wallets.associate { wallet -> wallet.id to WalletNetworkId.entries.associateWith { "${wallet.id}-${it.wireId}" } }, "$")
        val ids = PortfolioCalculator.scopeWalletIds(snapshot, scope)
        val groups = PortfolioCalculator.groups(snapshot, ids, lens)
        return PortfolioModel(snapshot, false, now, scope, ids, lens, PortfolioCalculator.availableLenses(scope),
            PortfolioCalculator.scopeTotals(snapshot, ids), PortfolioCalculator.worstFreshness(snapshot, ids, now), groups,
            PortfolioCalculator.fan(groups), groups.mapIndexed { index, group -> group.key to index }.toMap(),
            mapOf("main" to 0, "savings" to 1), PortfolioCalculator.walletSummaries(snapshot, now),
            PortfolioCalculator.scopeTotals(snapshot, wallets.map { it.id }), PortfolioCalculator.worstFreshness(snapshot, wallets.map { it.id }, now),
            null, if (expanded) groups.map { it.key }.toSet() else emptySet(), false)
    }

    private fun text(id: Int) = compose.activity.getString(id)
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

    @Test fun lensesExposeTokenNetworkAndWalletRowsAndKeepHiddenAssetsOut() {
        val current = mutableStateOf(model(lens = PortfolioLens.TOKENS))
        show { Column(Modifier.verticalScroll(rememberScrollState())) {
            LensControl(PortfolioLens.entries, current.value.lens, actions::onLens)
            PortfolioList(current.value, PortfolioFormat(NumbersFormatter(), "$"), actions)
        } }
        compose.onAllNodesWithText("HIDDEN").assertCountEquals(0)
        compose.onNodeWithText("UNKNOWN", substring = true).assertExists()
        node(R.string.portfolio_lens_networks).performClick()
        compose.runOnIdle { assertEquals("lens:networks", actions.events.last()); current.value = model(lens = PortfolioLens.NETWORKS) }
        compose.onAllNodesWithText("SORA2", substring = true).onFirst().assertExists()
        node(R.string.portfolio_lens_wallets).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("lens:wallets", actions.events.last()); current.value = model(lens = PortfolioLens.WALLETS) }
        compose.onNodeWithText("Main wallet").assertExists()
        compose.onNodeWithText("Savings wallet").assertExists()
    }

    @Test fun walletsSheetPreservesSelectionAndProvidesRefreshAndManageActions() {
        sheet(model(scope = PortfolioScope.SELECTED), PortfolioSheet.Wallets)
        node(R.string.portfolio_all_wallets).performClick()
        compose.onNodeWithText("Savings wallet").performClick()
        click(R.string.portfolio_refresh_all)
        click(R.string.portfolio_manage_wallets)
        compose.runOnIdle { assertEquals(listOf("all", "wallet:savings", "refresh", "manage-wallets"), actions.events) }
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
            FreshnessKind.SAVED to R.string.portfolio_sync_saved, FreshnessKind.OFFLINE to R.string.portfolio_sync_offline)) {
            compose.runOnIdle { current.value = model().copy(freshness = WalletFreshness(kind, now - 60_000)) }
            node(resource).assertExists()
            click(R.string.portfolio_refresh_all)
        }
        compose.runOnIdle { current.value = model().copy(freshness = WalletFreshness(FreshnessKind.PARTIAL, now, setOf(WalletNetworkId.TAIRA))) }
        compose.onNodeWithText("could not be reached", substring = true).assertExists()
        click(R.string.portfolio_refresh_all)
        compose.runOnIdle { assertEquals(List(4) { "refresh" }, actions.events) }
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
        compose.onNodeWithText("Savings wallet").performClick()
        compose.runOnIdle { assertEquals(listOf("pick:RECEIVE:savings"), actions.events) }
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
        override fun onLens(lens: PortfolioLens) { events += "lens:${lens.key}" }
        override fun onToggleRow(key: String) { events += "toggle:$key" }
        override fun onOpenToken(tokenId: String) { events += "token:$tokenId" }
        override fun onOpenWalletAlone(walletId: String) { events += "wallet:$walletId" }
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
