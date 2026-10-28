package jp.co.soramitsu.sora.accessibility

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.view.CustomItemTouchHelperCallback
import jp.co.soramitsu.feature_assets_impl.presentation.components.classic.AssetsManagementAdapter
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetSettingsState
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.classic.PoolsManagementAdapter
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.PoolSettingsState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetSettingsAccessibilityTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun assetFavoriteAndVisibilityAcceptAccessibilityClicksWithoutPointerPressure() {
        activity.scenario.onActivity { host ->
            host.setTheme(R.style.AppTheme)
            val parent = FrameLayout(host)
            val state = AssetSettingsState(Token("fixture-token", "Fixture", "FIX", 18, true, null, null, null, null), "1", "FIX", false, true, null)
            var favorites = 0; var visibility = 0
            val adapter = AssetsManagementAdapter(ItemTouchHelper(CustomItemTouchHelperCallback { _, _ -> false }), { true }, { _, _ -> false },
                { assertEquals(state, it); favorites++ }, { assertEquals(state, it); visibility++ })
            adapter.submitList(listOf(state))
            val holder = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(holder, 0)
            host.setContentView(parent.apply { addView(holder.itemView) })
            for (id in listOf(jp.co.soramitsu.feature_assets_impl.R.id.ivAssetSettingsFavoriteClickable, jp.co.soramitsu.feature_assets_impl.R.id.ivAssetSettingsVisibility)) {
                val action = holder.itemView.findViewById<View>(id)
                assertFalse(action.isPressed)
                assertTrue(action.isFocusable)
                val favorite = id == jp.co.soramitsu.feature_assets_impl.R.id.ivAssetSettingsFavoriteClickable
                assertEquals(host.getString(if (favorite) R.string.asset_add_to_favorites else R.string.asset_hide, "Fixture"), action.contentDescription)
                assertEquals(host.getString(if (favorite) R.string.asset_not_favorite_state else R.string.asset_visible_state), ViewCompat.getStateDescription(action))
                assertTrue(action.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            }
            assertEquals(1, favorites); assertEquals(1, visibility)
        }
    }

    @Test fun protectedAssetCannotBeHiddenOrFavoritedThroughAccessibility() {
        activity.scenario.onActivity { host ->
            host.setTheme(R.style.AppTheme)
            val state = AssetSettingsState(Token("fixture-fixed", "Fixed", "FIX", 18, false, null, null, null, null), "1", "FIX", false, true, null)
            var calls = 0
            val adapter = AssetsManagementAdapter(ItemTouchHelper(CustomItemTouchHelperCallback { _, _ -> false }), { true }, { _, _ -> false }, { calls++ }, { calls++ })
            adapter.submitList(listOf(state))
            val holder = adapter.onCreateViewHolder(FrameLayout(host), 0)
            adapter.onBindViewHolder(holder, 0)
            for (id in listOf(jp.co.soramitsu.feature_assets_impl.R.id.ivAssetSettingsFavoriteClickable, jp.co.soramitsu.feature_assets_impl.R.id.ivAssetSettingsVisibility)) {
                val action = holder.itemView.findViewById<View>(id)
                assertFalse(action.isEnabled)
                assertFalse(action.isClickable)
                assertFalse(action.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                assertFalse(action.performClick())
            }
            assertEquals(0, calls)
        }
    }

    @Test fun poolFavoriteAcceptsAccessibilityActivation() {
        activity.scenario.onActivity { host ->
            host.setTheme(R.style.AppTheme)
            val state = PoolSettingsState("a" to "b", DEFAULT_ICON_URI, DEFAULT_ICON_URI, "Fixture pool", "A-B", "1", false, 0.0)
            var favorites = 0
            val adapter = PoolsManagementAdapter(ItemTouchHelper(CustomItemTouchHelperCallback { _, _ -> false }), { true }, { _, _ -> false }, { assertEquals(state, it); favorites++ })
            adapter.submitList(listOf(state))
            val parent = FrameLayout(host)
            val holder = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(holder, 0)
            host.setContentView(parent.apply { addView(holder.itemView) })
            val action = holder.itemView.findViewById<View>(jp.co.soramitsu.feature_polkaswap_impl.R.id.ivPoolSettingsFavoriteClickable)
            assertFalse(action.isPressed)
            assertTrue(action.isFocusable)
            assertEquals(host.getString(R.string.asset_add_to_favorites, state.tokenSymbol), action.contentDescription)
            assertEquals(host.getString(R.string.asset_not_favorite_state), ViewCompat.getStateDescription(action))
            assertTrue(action.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            assertEquals(1, favorites)
        }
    }

    @Test fun recycledAssetActionsRefreshLabelsStatesCallbacksAndEnabledState() {
        activity.scenario.onActivity { host ->
            host.setTheme(R.style.AppTheme)
            val original = AssetSettingsState(Token("fixture-token", "Original", "FIX", 18, true, null, null, null, null), "1", "FIX", false, true, null)
            val next = original.copy(token = original.token.copy(name = "Next"), favorite = true, visible = false)
            val fixed = next.copy(token = next.token.copy(isHidable = false))
            val favoriteCalls = mutableListOf<AssetSettingsState>()
            val visibilityCalls = mutableListOf<AssetSettingsState>()
            val adapter = AssetsManagementAdapter(ItemTouchHelper(CustomItemTouchHelperCallback { _, _ -> false }), { true }, { _, _ -> false }, { favoriteCalls.add(it) }, { visibilityCalls.add(it) })
            val holder = adapter.onCreateViewHolder(FrameLayout(host), 0)
            val favorite = holder.itemView.findViewById<View>(jp.co.soramitsu.feature_assets_impl.R.id.ivAssetSettingsFavoriteClickable)
            val visibility = holder.itemView.findViewById<View>(jp.co.soramitsu.feature_assets_impl.R.id.ivAssetSettingsVisibility)
            holder.bind(original)
            holder.bind(next)
            assertEquals(host.getString(R.string.asset_remove_from_favorites, "Next"), favorite.contentDescription)
            assertEquals(host.getString(R.string.asset_show, "Next"), visibility.contentDescription)
            assertEquals(host.getString(R.string.asset_favorite_state), ViewCompat.getStateDescription(favorite))
            assertEquals(host.getString(R.string.asset_hidden_state), ViewCompat.getStateDescription(visibility))
            assertTrue(favorite.performClick()); assertTrue(visibility.performClick())
            assertEquals(listOf(next), favoriteCalls); assertEquals(listOf(next), visibilityCalls)
            holder.bind(fixed)
            assertFalse(favorite.isEnabled); assertFalse(visibility.isEnabled)
            assertFalse(favorite.isClickable); assertFalse(visibility.isClickable)
            assertFalse(favorite.performClick()); assertFalse(visibility.performClick())
            holder.bind(original)
            assertTrue(favorite.isEnabled); assertTrue(visibility.isEnabled)
            assertTrue(favorite.isClickable); assertTrue(visibility.isClickable)
            assertTrue(favorite.performClick()); assertTrue(visibility.performClick())
            assertEquals(listOf(next, original), favoriteCalls); assertEquals(listOf(next, original), visibilityCalls)
        }
    }

    @Test fun recycledPoolFavoriteRefreshesActionAndState() {
        activity.scenario.onActivity { host ->
            host.setTheme(R.style.AppTheme)
            val original = PoolSettingsState("a" to "b", DEFAULT_ICON_URI, DEFAULT_ICON_URI, "Fixture pool", "A-B", "1", false, 0.0)
            val next = original.copy(tokenSymbol = "C-D", favorite = true)
            var received: PoolSettingsState? = null
            val adapter = PoolsManagementAdapter(ItemTouchHelper(CustomItemTouchHelperCallback { _, _ -> false }), { true }, { _, _ -> false }, { received = it })
            val holder = adapter.onCreateViewHolder(FrameLayout(host), 0)
            holder.bind(original)
            holder.bind(next)
            val action = holder.itemView.findViewById<View>(jp.co.soramitsu.feature_polkaswap_impl.R.id.ivPoolSettingsFavoriteClickable)
            assertEquals(host.getString(R.string.asset_remove_from_favorites, "C-D"), action.contentDescription)
            assertEquals(host.getString(R.string.asset_favorite_state), ViewCompat.getStateDescription(action))
            assertTrue(action.performClick())
            assertEquals(next, received)
        }
    }
}
