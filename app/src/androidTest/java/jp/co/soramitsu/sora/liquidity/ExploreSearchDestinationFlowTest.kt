@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.liquidity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.*
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.feature_ecosystem_impl.presentation.EcoSystemTokensState
import jp.co.soramitsu.feature_ecosystem_impl.presentation.explore.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real Explore search destination; asset/pool/farm navigation stays behind mocked handlers. */
@RunWith(AndroidJUnit4::class)
class ExploreSearchDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val screenState = MutableStateFlow(ExploreScreenState(isSearching = true))
    private fun pool(target: String) = BasicPoolListItemState("xor" to target, "1", "", "", "XOR/${target.uppercase()}", "$100 TVL", "12%")
    private fun asset(id: String) = AssetItemCardState("", id, "Token ${id.uppercase()}", id.uppercase(), "1 $id", "$2", "+3%")
    private fun farm(reward: String) = BasicFarmListItemState(Triple("xor", "val", reward), "1", "", "", "", reward.uppercase(), "Farm ${reward.uppercase()}", "$100 TVL", "12% APR")
    private val vm = mockk<ExploreViewModel>(relaxed = true) { every { state } returns this@ExploreSearchDestinationFlowTest.screenState }
    private fun show() {
        val fragment = ExploreFragment()
        ExploreFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            val nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
        } }
    }
    @Test fun emptySearchShowsNothingFoundWithoutActions() {
        show(); compose.onNodeWithText(compose.activity.getString(R.string.common_nothing_found)).assertIsDisplayed()
        compose.runOnIdle { verify(exactly = 0) { vm.onTokenClicked(any()); vm.onPoolClicked(any()); vm.onFarmClicked(any()) } }
    }
    @Test fun loadingSearchDoesNotClaimNothingFound() {
        screenState.value = screenState.value.copy(isLoading = true); show()
        compose.onNodeWithText(compose.activity.getString(R.string.common_nothing_found)).assertDoesNotExist()
    }
    @Test fun emptyLoadedSearchReplacesProgressWithEmptyResult() {
        screenState.value = screenState.value.copy(isLoading = true); show(); compose.runOnIdle { screenState.value = screenState.value.copy(isLoading = false) }
        compose.onNodeWithText(compose.activity.getString(R.string.common_nothing_found)).assertIsDisplayed()
    }
    @Test fun assetResultUsesExactAssetIdentifier() {
        screenState.value = screenState.value.copy(ecoSystemTokensState = EcoSystemTokensState(listOf("1" to asset("val")))); show()
        compose.onNodeWithText("Token VAL").performClick(); compose.runOnIdle { verify(exactly = 1) { vm.onTokenClicked("val") } }
    }
    @Test fun poolResultUsesExactOrderedPair() {
        screenState.value = screenState.value.copy(pools = listOf(pool("val"))); show()
        compose.onNodeWithText("XOR/VAL").performClick(); compose.runOnIdle { verify(exactly = 1) { vm.onPoolClicked("xor" to "val") } }
    }
    @Test fun farmResultIncludesRewardAssetInIdentity() {
        screenState.value = screenState.value.copy(farms = listOf(farm("pswap"))); show()
        compose.onNodeWithText("Farm PSWAP").performClick(); compose.runOnIdle { verify(exactly = 1) { vm.onFarmClicked(Triple("xor", "val", "pswap")) } }
    }
    @Test fun resultReplacementRemovesPreviousAssetAction() {
        screenState.value = screenState.value.copy(ecoSystemTokensState = EcoSystemTokensState(listOf("1" to asset("val")))); show()
        compose.runOnIdle { screenState.value = screenState.value.copy(ecoSystemTokensState = EcoSystemTokensState(listOf("1" to asset("pswap")))) }
        compose.onNodeWithText("Token VAL").assertDoesNotExist(); compose.onNodeWithText("Token PSWAP").performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onTokenClicked("pswap") }; verify(exactly = 0) { vm.onTokenClicked("val") } }
    }
    @Test fun removingLastPoolProducesEmptyResult() {
        screenState.value = screenState.value.copy(pools = listOf(pool("val"))); show(); compose.runOnIdle { screenState.value = screenState.value.copy(pools = emptyList()) }
        compose.onNodeWithText("XOR/VAL").assertDoesNotExist(); compose.onNodeWithText(compose.activity.getString(R.string.common_nothing_found)).assertIsDisplayed()
    }
    @Test fun emptyAssetGroupDoesNotHideMatchingFarm() {
        screenState.value = screenState.value.copy(ecoSystemTokensState = EcoSystemTokensState(emptyList()), farms = listOf(farm("pswap"))); show()
        compose.onNodeWithText(compose.activity.getString(R.string.common_nothing_found)).assertDoesNotExist()
        compose.onNodeWithText("Farm PSWAP").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onFarmClicked(Triple("xor", "val", "pswap")) } }
    }
    @Test fun mixedSearchKeepsEachDestinationHandlerSeparate() {
        screenState.value = screenState.value.copy(ecoSystemTokensState = EcoSystemTokensState(listOf("1" to asset("val"))), pools = listOf(pool("val")), farms = listOf(farm("pswap"))); show()
        compose.onNodeWithText("Token VAL").performScrollTo().performClick()
        compose.onNodeWithText("XOR/VAL").performScrollTo().performClick()
        compose.onNodeWithText("Farm PSWAP").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onTokenClicked("val"); vm.onPoolClicked("xor" to "val"); vm.onFarmClicked(Triple("xor", "val", "pswap")) } }
    }
}
