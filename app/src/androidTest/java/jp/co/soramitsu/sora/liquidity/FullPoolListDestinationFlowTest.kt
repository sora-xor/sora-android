@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.liquidity

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.states.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.fullpoollist.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual pool-list destination and scrolling, with navigation intercepted at the view model. */
@RunWith(AndroidJUnit4::class)
class FullPoolListDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun pool(id: String) = PoolsListItemState("", "", "XOR", id.uppercase(), "2 XOR - 4 ${id.uppercase()}", "$6", "", "xor" to id, emptyList())
    private val screenState = MutableStateFlow(FullPoolListState("$12", PoolsListState(listOf(pool("val"), pool("eth"))), false))
    private val vm = mockk<FullPoolListViewModel>(relaxed = true) { every { state } returns screenState }
    private lateinit var nav: NavHostController
    private fun show(fontScale: Float = 1f) {
        val fragment = FullPoolListFragment()
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.size(320.dp, 480.dp)) {
                    nav = rememberNavController(); val scroll = rememberScrollState()
                    NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
                }
            }
        } }
    }
    private fun emptyMessage() = compose.onNodeWithText(compose.activity.getString(R.string.common_nothing_available))
    @Test fun selectingPoolUsesExactPairAndDoesNotLeaveListDirectly() {
        show(); compose.onNodeWithText("2 XOR - 4 ETH").performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onPoolClick("xor" to "eth") }; assertEquals(theOnlyRoute, nav.currentDestination?.route) }
    }
    @Test fun summaryShowsTheCurrentPortfolioTotal() {
        show(); compose.onNodeWithText("$12").assertExists(); compose.runOnIdle { screenState.value = screenState.value.copy(fiatSum = "$24") }
        compose.onNodeWithText("$12").assertDoesNotExist(); compose.onNodeWithText("$24").assertExists()
    }
    @Test fun noResultsAppearOnlyAfterLoadingCompletes() {
        screenState.value = FullPoolListState("", PoolsListState(emptyList()), true); show(); emptyMessage().assertDoesNotExist()
        compose.runOnIdle { screenState.value = screenState.value.copy(loading = false) }; emptyMessage().assertIsDisplayed()
    }
    @Test fun newSnapshotRemovesOldPoolCallbacksAndAmounts() {
        show(); compose.runOnIdle { screenState.value = FullPoolListState("$6", PoolsListState(listOf(pool("dai"))), false) }
        compose.onNodeWithText("2 XOR - 4 VAL").assertDoesNotExist(); compose.onNodeWithText("2 XOR - 4 DAI").performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onPoolClick("xor" to "dai") }; verify(exactly = 0) { vm.onPoolClick("xor" to "val") } }
    }
    @Test fun emptySnapshotRemovesPreviouslyVisiblePoolRows() {
        show(); compose.runOnIdle { screenState.value = FullPoolListState("$0", PoolsListState(emptyList()), false) }
        compose.onNodeWithText("2 XOR - 4 VAL").assertDoesNotExist(); emptyMessage().assertIsDisplayed()
    }
    @Test fun matchingPoolReplacesTheNoResultsMessage() {
        screenState.value = FullPoolListState("$0", PoolsListState(emptyList()), false); show()
        compose.runOnIdle { screenState.value = FullPoolListState("$6", PoolsListState(listOf(pool("val"))), false) }
        emptyMessage().assertDoesNotExist(); compose.onNodeWithText("2 XOR - 4 VAL").assertIsDisplayed()
    }
    @Test fun finalPoolInALongListRemainsReachableAtLargeFontSize() {
        screenState.value = FullPoolListState("$120", PoolsListState((1..20).map { pool("token$it") }), false)
        show(1.5f); compose.onNodeWithText("2 XOR - 4 TOKEN20").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onPoolClick("xor" to "token20") } }
    }
    @Test fun balancesAreInformationalUntilAPoolIsExplicitlySelected() {
        show(); compose.runOnIdle { verify(exactly = 0) { vm.onPoolClick(any()) }; verify(exactly = 0) { vm.onAction() } }
    }
}
