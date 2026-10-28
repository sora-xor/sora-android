@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.liquidity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.EditFarmScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.model.EditFarmScreenState
import jp.co.soramitsu.common_wallet.presentation.compose.BasicFarmListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.BasicUserFarmListItemState
import jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails.FarmDetailsScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails.model.FarmDetailsState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class LiquidityAndFarmingInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val readOnly = mutableStateOf(false)
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) {
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        } }
    }
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun input(symbol: String, value: String) = AssetAmountInputState(
        Token(symbol, symbol, symbol, 18, true, null, 1.0, 0.0, "$"), "100 $symbol", BigDecimal(value), "$value", true)
    private fun add() = LiquidityAddState(ButtonState("Review supply", true), input("XOR", "2.5"), input("VAL", "5"),
        LiquidityAddEstimatedState("XOR", "2.5", "VAL", "5", "10%"),
        LiquidityAddPricesState("XOR/VAL", "2", "VAL/XOR", "0.5", "12%", "0.01 XOR"),
        LiquidityAddConfirmState("Review the exact amounts", null, ButtonState("Confirm supply", true)),
        pairNotExist = true, hintVisible = true, shouldTransactionReminderInsufficientWarningBeShown = false)
    private fun remove() = LiquidityRemoveState(ButtonState("Review removal", true), 0.5, input("XOR", "2.5"), input("VAL", "5"),
        LiquidityRemoveEstimatedState("XOR", "2.5", "VAL", "5", "10%"),
        LiquidityRemovePricesState("XOR/VAL", "2", "VAL/XOR", "0.5", "12%", "0.01 XOR"),
        LiquidityRemoveConfirmState("Review the exact withdrawal", null, ButtonState("Confirm removal", true)),
        hintVisible = true, shouldTransactionReminderInsufficientWarningBeShown = false, poolInFarming = false)
    private fun swap() = SwapMainState(input("XOR", "2.5"), input("VAL", "5"), 0.5, Market.XYK, null,
        defaultSwapDetailsState().copy(transactionFee = "0.01 XOR", minmaxValue = "4.95 VAL", route = "XOR → VAL"),
        ButtonState("Review swap", true), ButtonState("Confirm swap", true), AnnotatedString("Review the exact swap"), null)
    private fun farm() = FarmDetailsState(Triple("xor", "val", "pswap"), "XOR / VAL", "TVL $100", "12%", "", "", "PSWAP", "", "0.01 XOR",
        hasSupplyInPool = true, poolIds = "xor" to "val", poolTitle = "XOR / VAL pool", apyText = "12%")

    @Test fun supplyReviewShowsAmountsAndHandsOffOnlyWhenEnabled() {
        val current = mutableStateOf(add())
        show { LiquidityAddScreen(current.value, 0.5, {}, {}, {}, {}, { events += "slippage" }, { events += "review" }, {}, {}) }
        compose.onNodeWithText("Review supply").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("review"), events); current.value = current.value.copy(btnState = ButtonState("Review supply", false)) }
        compose.onNodeWithText("Review supply").assertIsNotEnabled()
    }

    @Test fun readOnlyAccountCannotReviewSupplyDespiteAStaleEnabledState() {
        show { LiquidityAddScreen(add(), 0.5, {}, {}, {}, {}, {}, { events += "review" }, {}, {}) }
        compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText("Review supply").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun supplyConfirmationRequiresAnotherExplicitAction() {
        show { LiquidityAddConfirmScreen(add(), 0.5) { events += "supply" } }
        compose.onNodeWithText("Review the exact amounts").assertExists()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        compose.onNodeWithText("Confirm supply").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("supply"), events) }
    }

    @Test fun switchingToReadOnlyWhileReviewingSupplyDisablesConfirmation() {
        show { LiquidityAddConfirmScreen(add(), 0.5) { events += "supply" } }
        compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText("Confirm supply").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun removalConfirmationShowsWithdrawalAndForwardsOneConfirmation() {
        show { LiquidityRemoveConfirmScreen(remove()) { events += "remove" } }
        compose.onNodeWithText("Review the exact withdrawal").assertExists()
        compose.onNodeWithText("Confirm removal").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("remove"), events) }
    }

    @Test fun switchingToReadOnlyWhileReviewingRemovalDisablesConfirmation() {
        show { LiquidityRemoveConfirmScreen(remove()) { events += "remove" } }
        compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText("Confirm removal").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun swapConfirmationDisplaysMinimumAndFeeBeforeHandingOff() {
        show { SwapConfirmScreen(swap()) { events += "swap" } }
        compose.onNodeWithText("4.95 VAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.01 XOR").assertExists()
        compose.onNodeWithText("Confirm swap").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("swap"), events) }
    }

    @Test fun switchingToReadOnlyWhileReviewingSwapDisablesConfirmation() {
        show { SwapConfirmScreen(swap()) { events += "swap" } }
        compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText("Confirm swap").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun farmRequiresPoolSupplyBeforeStaking() {
        val current = mutableStateOf(farm().copy(hasSupplyInPool = false))
        show { FarmDetailsScreen(current.value, { events += "stake" }, { events += "supply" }, { events += "claim" }) }
        node(R.string.start_staking).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { current.value = current.value.copy(hasSupplyInPool = true) }
        node(R.string.start_staking).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("stake"), events) }
    }

    @Test fun farmRewardsMustBeAvailableBeforeClaiming() {
        val current = mutableStateOf(farm().copy(poolShareStacked = 25f, poolShareStackedText = "25%", userRewardsAmount = "0 PSWAP"))
        show { FarmDetailsScreen(current.value, { events += "edit" }, {}, { events += "claim" }) }
        node(R.string.claim_rewards).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { current.value = current.value.copy(hasRewardsAvailable = true, userRewardsAmount = "2 PSWAP") }
        compose.onNodeWithText("2 PSWAP").performScrollTo().assertIsDisplayed()
        node(R.string.claim_rewards).performScrollTo().performClick()
        node(R.string.edit_farm).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("claim", "edit"), events) }
    }

    private val farmIds = Triple("xor", "val", "pswap")
    private fun pool() = PoolDetailsState("", "", "", "PSWAP", "XOR", "VAL", "12%", "2.5 XOR", "5 VAL",
        "TVL $100", true, true, "10%", emptyList(), emptyList(), false)
    private fun edit() = EditFarmScreenState(farmIds, "25%", 0.25f, "10%", "12.5%", "0.1 XOR", "0.01 XOR",
        isButtonActive = true, isCardLoading = false)

    @Test fun poolDetailsShowBothBalancesAndExplicitSupplyAndRemoveActions() {
        show { PoolDetailsScreen(pool(), { events += "supply" }, { events += "remove" }, {}) }
        compose.onNodeWithText("2.5 XOR").assertExists()
        compose.onNodeWithText("5 VAL").assertExists()
        compose.onNodeWithText("10%").assertExists()
        node(R.string.common_supply).performScrollTo().performClick()
        node(R.string.common_remove).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("supply", "remove"), events) }
    }

    @Test fun poolWithoutPositionHidesPersonalBalancesAndDisablesRemoval() {
        show { PoolDetailsScreen(pool().copy(pooled1 = null, pooled2 = null, userPoolSharePercent = null, removeEnabled = false), {}, {}, {}) }
        compose.onNodeWithText("2.5 XOR").assertDoesNotExist()
        compose.onNodeWithText("10%").assertDoesNotExist()
        node(R.string.common_remove).performScrollTo().assertIsNotEnabled()
    }

    @Test fun fullyFarmedPoolExplainsWhyRemovalIsUnavailable() {
        show { PoolDetailsScreen(pool().copy(demeter100Percent = true, removeEnabled = false, addEnabled = false), {}, {}, {}) }
        node(R.string.polkaswap_farming_unstake_to_remove).performScrollTo().assertIsDisplayed()
        node(R.string.common_remove).performScrollTo().assertIsNotEnabled()
        node(R.string.common_supply).performScrollTo().assertIsNotEnabled()
    }

    @Test fun poolActiveAndAvailableFarmsRouteTheirOwnIdentifiersWithoutDuplicates() {
        val otherIds = Triple("xor", "val", "deo")
        val active = BasicUserFarmListItemState(farmIds, "", "", "", "Active PSWAP farm", "Rewards 2 PSWAP", "12% APR", "25% staked")
        val duplicate = BasicFarmListItemState(farmIds, "1", "", "", "", "PSWAP", "Duplicate active farm", "$100", "12% APR")
        val available = duplicate.copy(ids = otherIds, rewardTokenSymbol = "DEO", text1 = "Available DEO farm")
        val selected = mutableListOf<Triple<String, String, String>>()
        show { PoolDetailsScreen(pool().copy(demeterPools = listOf(active), availableDemeterFarms = listOf(duplicate, available)), {}, {}, selected::add) }
        compose.onNodeWithText("Duplicate active farm").assertDoesNotExist()
        compose.onNodeWithText("Active PSWAP farm").performScrollTo().performClick()
        compose.onNodeWithText("Available DEO farm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(farmIds, otherIds), selected) }
    }

    @Test fun removalReviewExplainsFarmingAndRoutesSlippageAndReviewSeparately() {
        show { LiquidityRemoveScreen(remove().copy(poolInFarming = true, shouldTransactionReminderInsufficientWarningBeShown = true), {}, {}, {}, {},
            { events += "slippage" }, { events += "review" }) }
        node(R.string.polkaswap_farming_pool_in_farming_hint).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.5%").performScrollTo().assertHasClickAction().performClick()
        compose.onNodeWithText("Review removal").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("slippage", "review"), events) }
    }

    @Test fun readOnlyAccountCannotReviewWithdrawal() {
        show { LiquidityRemoveScreen(remove(), {}, {}, {}, {}, {}, { events += "review" }) }
        compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText("Review removal").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun farmEditMaxAndConfirmationAreSeparateExplicitActions() {
        val changes = mutableListOf<Double>()
        show { EditFarmScreen(edit(), changes::add) { events += "confirm" } }
        compose.onNodeWithText("12.5%").assertExists()
        compose.onNodeWithText("0.01 XOR").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.common_max).uppercase()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(1.0), changes); assertTrue(events.isEmpty()) }
        node(R.string.common_confirm).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("confirm"), events) }
    }

    @Test fun farmEditRequiresReadyStateAndWritableAccount() {
        val state = mutableStateOf(edit().copy(isCardLoading = true))
        show { EditFarmScreen(state.value, {}) { events += "confirm" } }
        node(R.string.common_confirm).assertDoesNotExist()
        compose.runOnIdle { state.value = edit().copy(isButtonActive = false) }
        node(R.string.common_confirm).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = edit(); readOnly.value = true }
        node(R.string.common_confirm).assertIsNotEnabled()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test fun slippagePreservesAnUneditedValue() {
        val result = mutableListOf<Double>()
        show { SwapSlippageScreen(0.5, result::add) }
        node(R.string.common_done).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(0.5), result) }
    }

    @Test fun slippageClampsZeroToMinimumAndExplainsFailureRisk() {
        val result = mutableListOf<Double>()
        show { SwapSlippageScreen(0.5, result::add) }
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        node(R.string.polkaswap_slippage_mayfail).assertExists()
        node(R.string.common_done).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(0.01), result) }
    }

    @Test fun slippageCapsExcessiveInputAndExplainsFrontRunningRisk() {
        val result = mutableListOf<Double>()
        show { SwapSlippageScreen(0.5, result::add) }
        compose.onNode(hasSetTextAction()).performTextReplacement("12")
        node(R.string.polkaswap_slippage_frontrun).assertExists()
        node(R.string.common_done).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(10.0), result) }
    }

    @Test fun slippageClearsWarningAfterReturningToNormalRange() {
        val result = mutableListOf<Double>()
        show { SwapSlippageScreen(0.5, result::add) }
        compose.onNode(hasSetTextAction()).performTextReplacement("6")
        node(R.string.polkaswap_slippage_frontrun).assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("0.75")
        node(R.string.polkaswap_slippage_frontrun).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(listOf(0.75), result) }
    }

    @Test fun marketsForwardExactSelectedMarket() {
        val selected = mutableListOf<Market>()
        show { SwapMarketsScreen(Market.XYK, listOf(Market.SMART, Market.TBC, Market.XYK), selected::add) }
        node(Market.TBC.titleResource).performClick()
        node(Market.SMART.titleResource).performClick()
        compose.runOnIdle { assertEquals(listOf(Market.TBC, Market.SMART), selected) }
    }

    @Test fun marketExplanationCanBeOpenedAndDismissedWithoutChangingSelection() {
        show { SwapMarketsScreen(Market.XYK, listOf(Market.XYK), { events += "select" }) }
        compose.onNodeWithTag("jp.co.soramitsu.sora.develop:id/MarketHintIcon").performClick()
        node(Market.XYK.descriptionResource).assertIsDisplayed()
        node(R.string.common_ok).performClick()
        node(Market.XYK.descriptionResource).assertDoesNotExist()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
}
