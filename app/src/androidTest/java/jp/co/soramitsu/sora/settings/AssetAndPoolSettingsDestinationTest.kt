@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.MutableLiveData
import androidx.navigation.compose.*
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.*
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_assets_impl.presentation.screens.fullassetsettings.*
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetSettingsState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.fullpoolsettings.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.PoolSettingsState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetAndPoolSettingsDestinationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(fragment: SoraBaseFragment<*>, vm: Any) {
        compose.runOnUiThread { compose.activity.setTheme(R.style.AppTheme) }
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        every { fragment.viewLifecycleOwner } returns compose.activity
        compose.setContent { SoraAppTheme {
            fragment.backgroundColorComposable()
            val nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
        } }
        assertEquals(R.attr.baseBackgroundSecond, fragment.backgroundColor())
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
    }
    private fun list() = descendants(compose.activity.window.decorView).filterIsInstance<RecyclerView>().single()
    private fun awaitRows(count: Int) {
        compose.waitUntil(5_000) { var matches = false; compose.runOnUiThread { matches = list().adapter?.itemCount == count && list().childCount == count }; matches }
    }
    @Test fun assetDestinationReflectsFilteringAndRoutesFavoriteVisibilityActions() {
        val token = Token("fixture", "Fixture asset", "FIX", 18, true, null, 1.0, 0.0, "$")
        val asset = AssetSettingsState(token, "25 FIX", "FIX", false, true, 25.0)
        val rows = MutableLiveData<List<AssetSettingsState>>(); val empty = MutableStateFlow(true); val sum = MutableStateFlow("$25.00")
        val dragging = MutableLiveData(true)
        val vm = mockk<FullAssetSettingsViewModel>(relaxed = true) {
            every { settingsState } returns rows; every { settingsStateEmpty } returns empty
            every { fiatSum } returns sum; every { dragList } returns dragging
        }
        show(spyk(FullAssetSettingsFragment()), vm)
        compose.waitForIdle()
        compose.runOnUiThread { list().itemAnimator = null; rows.value = listOf(asset); empty.value = false }
        awaitRows(1)
        compose.onNodeWithText("$25.00").assertIsDisplayed()
        compose.runOnUiThread {
            list().itemAnimator = null
            val row = list().getChildAt(0)
            row.findViewById<View>(jp.co.soramitsu.feature_assets_impl.R.id.ivAssetSettingsFavoriteClickable).performClick()
            row.findViewById<View>(jp.co.soramitsu.feature_assets_impl.R.id.ivAssetSettingsVisibility).performClick()
            verify(exactly = 1) { vm.onFavoriteClick(asset); vm.onVisibilityClick(asset) }
            rows.value = emptyList(); empty.value = true; sum.value = ""; dragging.value = false
        }
        awaitRows(0)
        compose.onNodeWithText(compose.activity.getString(R.string.search_empty_state)).assertIsDisplayed()
    }
    @Test fun poolDestinationReflectsReplacementReorderingAndFavoriteActions() {
        val pool = PoolSettingsState("a" to "b", "", "", "Alpha-Beta", "ALP-BET", "1 - 2", false, 3.0)
        val second = pool.copy(id = "c" to "d", tokenSymbol = "GAM-DEL")
        val rows = MutableLiveData<List<PoolSettingsState>>(); val empty = MutableStateFlow(true); val sum = MutableStateFlow("$3.00")
        val dragging = MutableLiveData(true); val positions = MutableLiveData<Pair<Int, Int>>()
        val vm = mockk<FullPoolSettingsViewModel>(relaxed = true) {
            every { settingsState } returns rows; every { poolsEmptyState } returns empty
            every { fiatSum } returns sum; every { dragList } returns dragging; every { assetPositions } returns positions
        }
        show(spyk(FullPoolSettingsFragment()), vm)
        compose.waitForIdle()
        compose.runOnUiThread { list().itemAnimator = null; rows.value = listOf(pool, second); empty.value = false }
        awaitRows(2)
        compose.onNodeWithText("$3.00").assertIsDisplayed()
        compose.runOnUiThread {
            list().getChildAt(0).findViewById<View>(jp.co.soramitsu.feature_polkaswap_impl.R.id.ivPoolSettingsFavoriteClickable).performClick()
            verify(exactly = 1) { vm.onFavoriteClick(pool) }
            list().itemAnimator = null
            rows.value = emptyList(); empty.value = true; sum.value = ""; dragging.value = false
        }
        awaitRows(0)
        compose.onNodeWithText(compose.activity.getString(R.string.common_nothing_available)).assertExists()
    }
}
