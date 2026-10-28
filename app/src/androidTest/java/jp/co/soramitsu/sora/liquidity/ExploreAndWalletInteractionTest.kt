@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.liquidity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.*
import jp.co.soramitsu.common_wallet.presentation.compose.components.PoolsList
import jp.co.soramitsu.common_wallet.presentation.compose.states.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.allpools.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.allcurrencies.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.alldemeter.*
import jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter.ClaimDemeterScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter.model.ClaimScreenState
import jp.co.soramitsu.feature_main_impl.presentation.inappupdate.InAppUpdateScreen
import jp.co.soramitsu.feature_wallet_impl.presentation.editcardshub.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExploreAndWalletInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val readOnly = mutableStateOf(false)
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) { content() } }
    }
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun pool(target: String) = BasicPoolListItemState("xor" to target, "1", "", "", "XOR/${target.uppercase()}", "$100 TVL", "12%")
    private fun asset(id: String) = AssetItemCardState("", id, "Token ${id.uppercase()}", id.uppercase(), "1 $id", "$2", "+3%")
    private fun farm(reward: String) = BasicFarmListItemState(Triple("xor", "val", reward), "1", "", "", "", reward.uppercase(), "Farm ${reward.uppercase()}", "$100 TVL", "12% APR")
    private fun showPools(state: MutableStateFlow<EcoSystemPoolsState>) {
        val vm = mockk<AllPoolsViewModel>(); every { vm.poolsState } returns state
        show { AllPoolsScreen({ events += "pool:${it.first}:${it.second}" }, { events += "create" }, vm) }
    }
    private fun showAssets(state: MutableStateFlow<EcoSystemTokensState>) {
        val vm = mockk<AllCurrenciesViewModel>(); every { vm.state } returns state
        show { AllCurrenciesScreen({ events += "asset:$it" }, vm) }
    }
    private fun showFarms(state: MutableLiveData<AllFarmsState>) {
        val vm = mockk<AllDemeterViewModel>(); every { vm.farmsLiveData } returns state
        show { AllDemeterScreen({ events += "farm:${it.first}:${it.second}:${it.third}" }, vm) }
    }
    @Test fun poolRowsRouteTheirOwnPairAndKeepDisplayedYield() {
        showPools(MutableStateFlow(EcoSystemPoolsState(listOf(pool("val"), pool("eth")))))
        compose.onNodeWithText("XOR/ETH").performClick()
        compose.onNodeWithText("XOR/VAL").performClick()
        compose.onAllNodesWithText("12% " + compose.activity.getString(R.string.polkaswap_apy)).assertCountEquals(2)
        compose.runOnIdle { assertEquals(listOf("pool:xor:eth", "pool:xor:val"), events) }
    }
    @Test fun poolLoadingAndEmptyStatesRemovePreviouslyVisibleRows() {
        val state = MutableStateFlow(EcoSystemPoolsState(listOf(pool("val"))))
        showPools(state)
        compose.runOnIdle { state.value = state.value.copy(loading = true) }
        compose.onNodeWithText("XOR/VAL").assertDoesNotExist()
        compose.runOnIdle { state.value = EcoSystemPoolsState(emptyList()) }
        node(R.string.common_nothing_available).assertIsDisplayed()
    }
    @Test fun creatingAPoolHasAnAccessibleNamedAction() {
        showPools(MutableStateFlow(EcoSystemPoolsState(emptyList())))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.explore_create_pool)).assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(listOf("create"), events) }
    }
    @Test fun assetRowsKeepTheirTokenIdentityAfterReordering() {
        val state = MutableStateFlow(EcoSystemTokensState(listOf("1" to asset("xor"), "2" to asset("val"))))
        showAssets(state)
        compose.onNodeWithText("Token VAL").performClick()
        compose.runOnIdle { state.value = EcoSystemTokensState(listOf("1" to asset("val"), "2" to asset("xor"))) }
        compose.onNodeWithText("Token XOR").performClick()
        compose.runOnIdle { assertEquals(listOf("asset:val", "asset:xor"), events) }
    }
    @Test fun assetLoadingAndEmptyStatesDoNotLeaveStalePriceRows() {
        val state = MutableStateFlow(EcoSystemTokensState(listOf("1" to asset("xor"))))
        showAssets(state)
        compose.runOnIdle { state.value = state.value.copy(loading = true) }
        compose.onNodeWithText("Token XOR").assertDoesNotExist()
        compose.runOnIdle { state.value = EcoSystemTokensState(emptyList()) }
        node(R.string.common_nothing_available).assertIsDisplayed()
    }
    @Test fun farmsWithTheSamePairRouteDifferentRewardTokens() {
        val state = MutableLiveData(AllFarmsState(listOf(farm("pswap"), farm("deo"))))
        showFarms(state)
        compose.onNodeWithText("Farm PSWAP").performClick()
        compose.onNodeWithText("Farm DEO").performClick()
        compose.runOnIdle { assertEquals(listOf("farm:xor:val:pswap", "farm:xor:val:deo"), events) }
    }
    @Test fun farmLoadingAndEmptyStatesRemoveOldRewardRows() {
        val state = MutableLiveData(AllFarmsState(listOf(farm("pswap")))); showFarms(state)
        compose.runOnIdle { state.value = AllFarmsState(listOf(farm("pswap")), loading = true) }
        compose.onNodeWithText("Farm PSWAP").assertDoesNotExist()
        compose.runOnIdle { state.value = AllFarmsState(emptyList()) }
        node(R.string.common_nothing_available).assertIsDisplayed()
    }
    @Test fun compactPoolListPreservesAmountsFiatRewardsAndPairRouting() {
        val first = PoolsListItemState("", "", "XOR", "VAL", "2 XOR - 4 VAL", "$12", "+3%", "xor" to "val", listOf("", ""))
        val state = mutableStateOf(PoolsListState(listOf(first, first.copy(poolToken2Symbol = "ETH", poolAmounts = "3 XOR - 6 ETH", fiat = "$24", tokenIds = "xor" to "eth"))))
        show { Column { PoolsList(state.value) { events += "${it.first}:${it.second}" } } }
        compose.onNodeWithText("$12").assertExists(); compose.onNodeWithText("$24").assertExists()
        compose.onNodeWithText("3 XOR - 6 ETH").performClick()
        compose.runOnIdle { assertEquals(listOf("xor:eth"), events); state.value = PoolsListState(emptyList()) }
        compose.onNodeWithText("2 XOR - 4 VAL").assertDoesNotExist()
    }
    private fun claim() = ClaimScreenState(Triple("xor", "val", "pswap"), "", "2.000000000000000001 PSWAP", "$4.00", "0.007 XOR", true, false, false)
    private fun showClaim(state: State<ClaimScreenState>) = show {
        Column(Modifier.verticalScroll(rememberScrollState())) { ClaimDemeterScreen(state.value) { events += "claim" } }
    }
    @Test fun claimShowsExactRewardAndFeeAndWaitsForExplicitConfirmation() {
        showClaim(mutableStateOf(claim()))
        compose.onNodeWithText("2.000000000000000001 PSWAP").assertExists()
        compose.onNodeWithText("$4.00").assertExists(); compose.onNodeWithText("0.007 XOR").assertExists()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        node(R.string.common_claim).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("claim"), events) }
    }
    @Test fun claimHonorsChangedReadinessAndReadOnlyAccount() {
        val state = mutableStateOf(claim().copy(isButtonActive = false)); showClaim(state)
        node(R.string.common_claim).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = claim(); readOnly.value = true }
        node(R.string.common_claim).assertIsNotEnabled()
    }
    @Test fun claimLoadingRemovesThePreviousConfirmationAction() {
        val state = mutableStateOf(claim()); showClaim(state)
        compose.runOnIdle { state.value = state.value.copy(isLoading = true) }
        node(R.string.common_claim).assertDoesNotExist()
        compose.onNodeWithText("2.000000000000000001 PSWAP").assertDoesNotExist()
    }
    @Test fun editWalletCardsRoutesTheIndexWithinTheCorrectSection() {
        val state = EditCardsHubScreenState(
            listOf(HubCardState("assets", HubCardVisibility.VISIBLE_AND_ENABLED, R.string.common_currencies), HubCardState("pools", HubCardVisibility.VISIBLE_AND_ENABLED, R.string.discovery_polkaswap_pools)),
            listOf(HubCardState("farms", HubCardVisibility.NOT_VISIBLE_ENABLED, R.string.explore_demeter_title)))
        show { EditCardsHubScreen(state, rememberScrollState(), { events += "enabled:$it" }, { events += "disabled:$it" }) }
        node(R.string.discovery_polkaswap_pools).performScrollTo().performClick()
        node(R.string.explore_demeter_title).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("enabled:1", "disabled:0"), events) }
    }
    @Test fun fixedWalletCardsCannotBeToggled() {
        val state = EditCardsHubScreenState(
            listOf(HubCardState("assets", HubCardVisibility.VISIBLE_AND_DISABLED, R.string.common_currencies)),
            listOf(HubCardState("farms", HubCardVisibility.NOT_VISIBLE_DISABLED, R.string.explore_demeter_title)))
        show { EditCardsHubScreen(state, rememberScrollState(), { events += "enabled:$it" }, { events += "disabled:$it" }) }
        node(R.string.common_currencies).performScrollTo().assertIsNotEnabled()
        node(R.string.explore_demeter_title).performScrollTo().assertIsNotEnabled()
    }
    @Test fun emptyWalletCardSectionsDisappearAfterAnUpdate() {
        val state = mutableStateOf(EditCardsHubScreenState(listOf(HubCardState("assets", HubCardVisibility.VISIBLE_AND_ENABLED, R.string.common_currencies)), emptyList()))
        show { EditCardsHubScreen(state.value, rememberScrollState(), {}, {}) }
        compose.onNodeWithText(compose.activity.getString(R.string.common_disabled).uppercase()).assertDoesNotExist()
        compose.runOnIdle { state.value = EditCardsHubScreenState(emptyList(), emptyList()) }
        node(R.string.common_currencies).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.common_enabled).uppercase()).assertDoesNotExist()
    }
    @Test fun updatePromptMakesRestartExplicitAndKeepsCancelSeparate() {
        show { InAppUpdateScreen({ events += "update" }, { events += "cancel" }) }
        node(R.string.restart_required).assertExists(); node(R.string.update_downloaded).assertExists()
        compose.onNode(hasText(compose.activity.getString(R.string.common_update)) and hasClickAction()).performClick()
        node(R.string.common_cancel).performClick()
        compose.runOnIdle { assertEquals(listOf("update", "cancel"), events) }
    }
}
