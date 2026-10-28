@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_assets_impl.presentation.screens.txlist.*
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen.*
import jp.co.soramitsu.feature_main_impl.presentation.profile.debugmenu.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt.*
import jp.co.soramitsu.feature_wallet_impl.presentation.editcardshub.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryAndMarketDestinationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(fragment: SoraBaseFragment<*>, vm: Any) {
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            val nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
        } }
    }
    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun combinedHistoryDestinationRoutesRetryAndPaginationAsStateChanges() {
        val history = MutableStateFlow<HistoryState>(HistoryState.Error)
        val networks = MutableStateFlow(NetworkActivityState(loading = false))
        val vm = mockk<ActivitiesViewModel>(relaxed = true) {
            every { historyState } returns history; every { networkActivity } returns networks
        }
        show(ActivitiesFragment(), vm)
        compose.onNodeWithText(text(R.string.activity_error_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_refresh)).performClick()
        compose.runOnIdle { history.value = HistoryState.History(endReached = false, events = emptyList()) }
        compose.onNodeWithText("SORA2 · " + text(R.string.show_more)).performClick()
        compose.runOnIdle { history.value = HistoryState.NoData }
        compose.onNodeWithText(text(R.string.activity_empty_content_title)).assertIsDisplayed()
        compose.runOnIdle { verify(exactly = 1) { vm.refresh(); vm.onMoreHistoryEventsRequested() } }
    }

    @Test fun assetHistoryDestinationMovesFromErrorToEmptyAndIncompleteHistory() {
        val history = MutableStateFlow<HistoryState>(HistoryState.Error)
        val vm = mockk<TxListViewModel>(relaxed = true) { every { state } returns history }
        show(TxListFragment(), vm)
        compose.onNodeWithText(text(R.string.common_refresh)).performClick()
        compose.runOnIdle { history.value = HistoryState.NoData }
        compose.onNodeWithText(text(R.string.activity_empty_content_title)).assertIsDisplayed()
        compose.runOnIdle { history.value = HistoryState.History(endReached = true, events = emptyList(), hasErrorLoadingNew = true) }
        compose.onNodeWithText(text(R.string.activity_data_not_up_to_date_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_refresh)).performClick()
        compose.runOnIdle { verify(exactly = 2) { vm.refresh() } }
    }

    @Test fun marketDestinationForwardsSearchRefreshAndFilterReset() {
        val state = MutableStateFlow(PolkamarktScreenState(loading = false, search = "unknown", statusFilter = "closed", categoryFilter = "sports", mineOnly = true))
        val vm = mockk<PolkamarktViewModel>(relaxed = true) { every { this@mockk.state } returns state }
        show(PolkamarktFragment(), vm)
        compose.onNode(hasSetTextAction()).performTextReplacement("fixture")
        compose.onNodeWithText(text(R.string.wallet_market_refresh)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.wallet_market_reset)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.search("fixture"); vm.refresh(); vm.search(""); vm.filterStatus("all"); vm.filterCategory("all"); vm.toggleMineOnly() } }
    }

    @Test fun hubCustomizationDestinationUsesSeparateEnabledAndDisabledIndices() {
        val state = MutableStateFlow(EditCardsHubScreenState(
            listOf(HubCardState("enabled", HubCardVisibility.VISIBLE_AND_ENABLED, R.string.common_activity)),
            listOf(HubCardState("disabled", HubCardVisibility.NOT_VISIBLE_ENABLED, R.string.polkamarkt_title))))
        val vm = mockk<EditCardsHubViewModel>(relaxed = true) { every { this@mockk.state } returns state }
        show(EditCardsHubFragment(), vm)
        compose.onNodeWithText(text(R.string.common_activity)).performClick()
        compose.onNodeWithText(text(R.string.polkamarkt_title)).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onEnabledCardItemClick(0); vm.onDisabledCardItemClick(0) } }
    }

    @Test fun diagnosticsDestinationShowsDeviceValuesAndRoutesExplicitActions() {
        val vm = mockk<DebugMenuViewModel>(relaxed = true) { every { state } returns DebugMenuScreenState(emptyList()) }
        val activity = mockk<FragmentActivity> {
            every { resources } returns compose.activity.resources
            every { packageManager } returns compose.activity.packageManager
        }
        val fragment = spyk(DebugMenuFragment()) { every { getActivity() } returns activity }
        show(fragment, vm)
        compose.onNodeWithText("Density", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reset runtime").performClick()
        compose.onNodeWithText("Change google account").performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onResetRuntimeClick(); vm.onChangeGoogleAccount(any()) } }
        compose.onNodeWithText("SORA Neon components").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Reset runtime").assertIsDisplayed()
    }
}
