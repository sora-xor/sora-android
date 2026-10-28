package jp.co.soramitsu.sora.accessibility

import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.common.util.Notification as WalletNotification
import jp.co.soramitsu.feature_assets_impl.R as AssetsR
import jp.co.soramitsu.feature_polkaswap_impl.R as PoolsR

/** Real installed resources and row geometry; no image loaders, wallet data, or network. */
@RunWith(AndroidJUnit4::class)
class ResourceCompatibilityTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext

    @Test fun installedLauncherProvidesAnAdaptiveIcon() {
        val manager = target.packageManager
        val launch = requireNotNull(manager.getLaunchIntentForPackage(target.packageName))
        val entry = requireNotNull(manager.resolveActivity(launch, 0))
        val icon = entry.loadIcon(manager)
        assertTrue("The installed launcher must support launcher masks", icon is AdaptiveIconDrawable)
        assertTrue(icon.intrinsicWidth > 0 && icon.intrinsicHeight > 0)
    }

    @Test fun installedRoundIconAlsoSupportsLauncherMasks() {
        // Inspect the installed manifest instead of assuming a resource name for this flavor.
        val parser = target.assets.openXmlResourceParser("AndroidManifest.xml")
        var roundIcon = 0
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "application") {
                    roundIcon = parser.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android", "roundIcon", 0,
                    )
                    break
                }
            }
        } finally {
            parser.close()
        }
        assertTrue("The installed manifest must declare a round icon", roundIcon != 0)
        val icon = target.getDrawable(roundIcon)
        assertTrue("Round-icon launchers must receive the adaptive icon too", icon is AdaptiveIconDrawable)
    }

    @Test fun notificationBuildsWithABitmapLargeIconAndValidSmallIcon() {
        val notification = WalletNotification.getBuilder(target).build()
        val largeIcon = requireNotNull(notification.getLargeIcon())
        val drawable = largeIcon.loadDrawable(target)
        assertTrue("Notification large icons remain bitmap-backed", drawable is BitmapDrawable)
        val bitmap = (drawable as BitmapDrawable).bitmap
        assertFalse(bitmap.isRecycled)
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        assertNotNull(requireNotNull(notification.smallIcon).loadDrawable(target))
    }

    private fun checkRtlRow(
        layout: Int,
        handleId: Int,
        titleId: Int,
        amountId: Int,
        trailingIconId: Int,
        firstIconId: Int,
        favoriteId: Int,
        actionIds: List<Int>,
    ) {
        lateinit var row: ViewGroup
        activity.scenario.onActivity { host ->
            host.setTheme(CommonR.style.AppTheme)
            val config = Configuration(host.resources.configuration).apply {
                setLocale(Locale("ar"))
                setLayoutDirection(Locale("ar"))
                fontScale = 1f
            }
            val context = ContextThemeWrapper(host.createConfigurationContext(config), CommonR.style.AppTheme)
            val parent = FrameLayout(context).apply {
                // Keep the measured row clear of the edge-to-edge status bar on recent APIs.
                setPadding(dp(16), dp(48), dp(16), 0)
            }
            row = (LayoutInflater.from(context).inflate(layout, parent, false) as ViewGroup).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                findViewById<TextView>(titleId).text = "أصل للاختبار"
                findViewById<TextView>(amountId).text = "1 234 XOR"
            }
            parent.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            host.setContentView(parent)
        }
        instrumentation.waitForIdleSync()
        activity.scenario.onActivity {
            assertEquals(View.LAYOUT_DIRECTION_RTL, row.layoutDirection)
            val handle = row.findViewById<View>(handleId)
            assertTrue("Reordering must remain visible in RTL", handle.isShown)
            assertTrue("The reorder target must be at least 48dp wide", handle.width >= dp(48))
            assertTrue("The reorder target must be at least 48dp tall", handle.height >= dp(48))
            assertFalse(handle.contentDescription.isNullOrBlank())
            val visible = Rect()
            assertTrue(handle.getGlobalVisibleRect(visible))
            assertEquals("The reorder target must not be clipped horizontally", handle.width, visible.width())
            assertEquals("The reorder target must not be clipped vertically", handle.height, visible.height())
            assertEquals("End-aligned controls move to the left in RTL", 0, handle.left)

            val handleBounds = Rect().also(handle::getHitRect)
            for (id in listOf(titleId, amountId, trailingIconId, firstIconId, favoriteId)) {
                val content = row.findViewById<View>(id)
                assertTrue("Row content must retain usable width", content.width > 0)
                val bounds = Rect().also(content::getHitRect)
                assertFalse("Row content must not cover the reorder touch target", Rect.intersects(handleBounds, bounds))
            }
            val title = row.findViewById<View>(titleId)
            val icon = row.findViewById<View>(trailingIconId)
            assertTrue("The start margin must separate Arabic text from its icon", icon.left - title.right >= dp(8) - 1)
            val actionBounds = actionIds.map { id ->
                val action = row.findViewById<View>(id)
                assertTrue("Settings action must be at least 48dp wide", action.width >= dp(48))
                assertTrue("Settings action must be at least 48dp tall", action.height >= dp(48))
                assertFalse("Settings action must have a spoken label", action.contentDescription.isNullOrBlank())
                Rect().also(action::getHitRect)
            }
            for (i in actionBounds.indices) for (j in i + 1 until actionBounds.size) {
                assertFalse("Interactive targets must not overlap", Rect.intersects(actionBounds[i], actionBounds[j]))
            }
            val firstIcon = row.findViewById<View>(firstIconId)
            val favorite = row.findViewById<View>(favoriteId)
            assertTrue("The icon's start margin must mirror in RTL", favorite.left - firstIcon.right >= dp(16) - 1)
        }
    }

    private fun dp(value: Int): Int = (value * target.resources.displayMetrics.density).roundToInt()

    @Test fun assetSettingsRetainsAVisibleAccessibleReorderTargetInRtl() = checkRtlRow(
        AssetsR.layout.item_asset_in_settings, AssetsR.id.ivAssetSettingsItemDrag,
        AssetsR.id.tvAssetSettingsTokenName, AssetsR.id.tvAssetSettingsAmount,
        AssetsR.id.ivAssetSettingsIcon, AssetsR.id.ivAssetSettingsIcon, AssetsR.id.ivAssetSettingsFavorite,
        listOf(AssetsR.id.ivAssetSettingsFavoriteClickable, AssetsR.id.ivAssetSettingsVisibility, AssetsR.id.ivAssetSettingsItemDrag),
    )

    @Test fun poolSettingsRetainsAVisibleAccessibleReorderTargetInRtl() = checkRtlRow(
        PoolsR.layout.item_pool_in_settings, PoolsR.id.ivPoolSettingsItemDrag,
        PoolsR.id.tvPoolSettingsTokenName, PoolsR.id.tvPoolSettingsAmount,
        PoolsR.id.ivPool2SettingsIcon, PoolsR.id.ivPool1SettingsIcon, PoolsR.id.ivPoolSettingsFavorite,
        listOf(PoolsR.id.ivPoolSettingsFavoriteClickable, PoolsR.id.ivPoolSettingsItemDrag),
    )
}
