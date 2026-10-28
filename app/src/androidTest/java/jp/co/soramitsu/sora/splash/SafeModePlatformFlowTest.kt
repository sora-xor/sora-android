package jp.co.soramitsu.sora.splash

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.presentation.compose.neon.safemode.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

/** Real Android intent/URI/preferences values with all activity launching intercepted. */
@RunWith(AndroidJUnit4::class)
class SafeModePlatformFlowTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val preferenceName = "safety-platform-fixture-${UUID.randomUUID()}"
    private val preferences = app.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val packages = mockk<PackageManager>()
    private val context = mockk<Context>()
    private val info = PackageInfo().apply { firstInstallTime = 100; lastUpdateTime = 200 }
    private val launched = mutableListOf<Intent>()
    @Before fun setup() {
        every { context.packageName } returns "fixture.wallet"
        every { context.packageManager } returns packages
        every { context.getSharedPreferences("sora_upgrade_welcome", Context.MODE_PRIVATE) } returns preferences
        every { packages.getPackageInfo("fixture.wallet", 0) } returns info
        every { context.startActivity(any()) } answers { launched += firstArg<Intent>(); Unit }
    }
    @After fun cleanup() { app.deleteSharedPreferences(preferenceName) }
    @Test fun freshInstallDoesNotShowUpgradeWelcome() {
        info.lastUpdateTime = info.firstInstallTime; assertFalse(UpgradeWelcome.isPending(context))
    }
    @Test fun updatedInstallShowsWelcomeUntilExplicitCompletion() {
        assertTrue(UpgradeWelcome.isPending(context)); assertTrue(UpgradeWelcome.isPending(context))
        assertTrue(preferences.all.isEmpty())
    }
    @Test fun markingWelcomeDonePersistsAndSkipsPackageInspection() {
        UpgradeWelcome.markDone(context); clearMocks(packages, answers = false)
        assertFalse(UpgradeWelcome.isPending(context)); verify(exactly = 0) { packages.getPackageInfo(any<String>(), any<Int>()) }
        assertEquals(mapOf("upgrade_welcome_3_9_done" to true), preferences.all)
    }
    @Test fun welcomeInspectionFailureDoesNotPreventWalletEntry() {
        every { packages.getPackageInfo("fixture.wallet", 0) } throws PackageManager.NameNotFoundException()
        assertFalse(UpgradeWelcome.isPending(context))
    }
    @Test fun unreadableWelcomePreferencesDoNotPreventWalletEntry() {
        every { context.getSharedPreferences(any(), any()) } throws SecurityException("preferences unavailable")
        assertFalse(UpgradeWelcome.isPending(context)); UpgradeWelcome.markDone(context)
    }
    @Test fun supportEmailEncodesSubjectAndBodyWithoutChangingTheirContents() {
        val subject = "Wallet & backup? 日本語"; val body = "public diagnostic\nvalue=1&another=two#end"
        assertTrue(SafeModeSupport.email(context, subject, body))
        val intent = launched.single(); assertEquals(Intent.ACTION_SENDTO, intent.action)
        val uri = intent.data!!; assertEquals("mailto", uri.scheme)
        assertTrue(uri.schemeSpecificPart.startsWith(SafeModeSupport.EMAIL + "?"))
        val query = android.net.Uri.parse("https://example.test/?" + uri.encodedSchemeSpecificPart.substringAfter('?'))
        assertEquals(subject, query.getQueryParameter("subject")); assertEquals(body, query.getQueryParameter("body"))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    @Test fun noEmailApplicationReturnsFailureWithoutCrashing() {
        every { context.startActivity(any()) } throws ActivityNotFoundException()
        assertFalse(SafeModeSupport.email(context, "fixture", "public diagnostic"))
    }
    @Test fun restrictedEmailLaunchReturnsFailureWithoutCrashing() {
        every { context.startActivity(any()) } throws SecurityException("restricted")
        assertFalse(SafeModeSupport.email(context, "fixture", "public diagnostic"))
    }
    @Test fun retryWithNoLauncherDoesNothing() {
        every { packages.getLaunchIntentForPackage("fixture.wallet") } returns null
        WalletUpgradeRetry.launch(context); assertTrue(launched.isEmpty())
    }
    @Test fun retryStartsSameAppInFreshTaskWithExplicitUpgradeRequest() {
        val launch = Intent("fixture.launch").setPackage("fixture.wallet")
        every { packages.getLaunchIntentForPackage("fixture.wallet") } returns launch
        WalletUpgradeRetry.launch(context)
        val result = launched.single(); assertSame(launch, result); assertEquals("fixture.wallet", result.`package`)
        assertTrue(result.getBooleanExtra(WalletUpgradeRetry.EXTRA_RETRY_UPGRADE, false))
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(flags, result.flags and flags)
    }
}
