package jp.co.soramitsu.sora.platform

import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.feature_main_impl.MainStarterImpl
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_multiaccount_impl.MultiaccountStarterImpl
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Captures Android launch requests at the Context boundary without launching or changing a wallet. */
@RunWith(AndroidJUnit4::class)
class WalletEntryIntentContractTest {
    private class CapturingContext : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        lateinit var started: Intent
        var options: Bundle? = null
        override fun startActivity(intent: Intent) { started = intent; options = null }
        override fun startActivity(intent: Intent, options: Bundle?) { started = intent; this.options = options }
    }
    private val context = CapturingContext()
    private val clearTask = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    @Test fun normalStartClearsTheOldTaskAndInvitationKeepsTheCurrentTask() {
        val starter = MainStarterImpl()
        starter.start(context)
        assertEquals(MainActivity::class.java.name, context.started.component!!.className)
        assertEquals(clearTask, context.started.flags); assertNull(context.started.action); assertNotNull(context.options)
        starter.startWithInvite(context)
        assertEquals(MainActivity::class.java.name, context.started.component!!.className)
        assertEquals(0, context.started.flags)
        assertEquals("jp.co.soramitsu.feature_main_impl.ACTION_INVITE", context.started.action)
        assertNotNull(context.options)
    }
    // A pinned task (screen pinning, lock task mode) can be neither left nor cleared: the wallet
    // opens in it on top of the startup screen, which closes. Otherwise the start stays where it is.
    @Test fun aPinnedTaskOpensTheWalletInItselfAndClosesTheScreenThatOpenedIt() {
        val startup = io.mockk.mockk<android.app.Activity>(relaxed = true)
        MainActivity.start(context, pinnedTask = startup)
        assertEquals(MainActivity::class.java.name, context.started.component!!.className)
        assertEquals(0, context.started.flags); assertNull(context.started.action); assertNotNull(context.options)
        io.mockk.verify(exactly = 1) { startup.finish() }
        // Not pinned (or not started from a screen): a task of its own, as on every start.
        MainActivity.start(context, pinnedTask = null)
        assertEquals(clearTask, context.started.flags)
    }
    @Test fun languageAndNewAccountRestartsCarryDistinctActionsInANewTask() {
        MainActivity.restartAfterLanguageChange(context)
        assertEquals(clearTask, context.started.flags)
        assertEquals("jp.co.soramitsu.feature_main_impl.ACTION_CHANGE_LANGUAGE", context.started.action)
        MainStarterImpl().restartAfterAddAccount(context)
        assertEquals(clearTask, context.started.flags)
        assertEquals("jp.co.soramitsu.feature_main_impl.ACTION_ACCOUNT_ADDED", context.started.action)
        assertEquals(MainActivity::class.java.name, context.started.component!!.className)
    }
    @Test fun everyOnboardingEntryKeepsItsExplicitSourceAcrossBothTaskModes() {
        for (entry in OnboardingActivity.Entry.entries) for (clear in listOf(false, true)) {
            OnboardingActivity.start(context, clear, entry)
            assertEquals(OnboardingActivity::class.java.name, context.started.component!!.className)
            assertEquals(if (clear) clearTask else 0, context.started.flags)
            assertEquals(entry.name, context.started.getStringExtra("jp.co.soramitsu.feature_multiaccount_impl.extra.ENTRY"))
            assertNotNull(context.options)
        }
    }
    @Test fun genericOnboardingStarterUsesWelcomeWithoutInventingAnImportSource() {
        MultiaccountStarterImpl().startOnboardingFlow(context, false)
        assertEquals("WELCOME", context.started.getStringExtra("jp.co.soramitsu.feature_multiaccount_impl.extra.ENTRY"))
        assertEquals(0, context.started.flags)
    }
}
