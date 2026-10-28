package jp.co.soramitsu.sora.settings

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.splash.presentation.WalletStartupActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reads the installed manifest without starting a wallet or using external services. */
@RunWith(AndroidJUnit4::class)
class WalletStartupComponentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.packageManager
    private val published = ComponentName(context.packageName, "jp.co.soramitsu.sora.splash.presentation.SplashActivity")
    private val implementation = ComponentName(context, WalletStartupActivity::class.java)

    @Test fun publishedLauncherNameTargetsTheStartupImplementation() {
        val alias = manager.getActivityInfo(published, 0)
        assertEquals(WalletStartupActivity::class.java.name, alias.targetActivity)
        assertTrue(alias.exported)
        assertTrue(alias.enabled)
    }

    @Test fun implementationIsInternalAndKeepsTheSystemSplashTheme() {
        val activity = manager.getActivityInfo(implementation, 0)
        assertFalse(activity.exported)
        assertTrue(activity.enabled)
        assertEquals(R.style.SplashTheme, activity.theme)
    }

    @Test fun packageLauncherIntentKeepsThePublishedComponent() {
        val intent = requireNotNull(manager.getLaunchIntentForPackage(context.packageName))
        assertEquals(published, intent.component)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
    }

    @Test fun publishedExplicitIntentStillResolves() {
        val resolved = requireNotNull(manager.resolveActivity(Intent().setComponent(published), PackageManager.MATCH_DEFAULT_ONLY))
        assertEquals(published.className, resolved.activityInfo.name)
        assertEquals(implementation.className, resolved.activityInfo.targetActivity)
    }
}
