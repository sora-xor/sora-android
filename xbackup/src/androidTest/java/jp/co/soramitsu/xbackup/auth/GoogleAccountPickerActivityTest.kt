/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.auth

import android.accounts.AccountManager
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The account picker on a device without the user's Google account. The system
 * account chooser is answered by an ActivityMonitor, so the picker's own launch,
 * result callback and token request run unchanged. Every answer that does not
 * end in a Drive token returns RESULT_CANCELED, which callers already handled
 * for the Play-services sign-in screen. With Play services installed, a Google
 * account that is not on the device gets Google's sign-in screen; leaving it must
 * still end the picker (Google does not report that refusal to AccountManager).
 */
@RunWith(AndroidJUnit4::class)
class GoogleAccountPickerActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val testPackage = instrumentation.targetContext.packageName
    private var chooser: Instrumentation.ActivityMonitor? = null

    @After
    fun removeChooserAnswer() {
        chooser?.let(instrumentation::removeMonitor)
    }

    /** The system chooser returns [resultCode] and [data] as soon as the picker opens it. */
    private fun chooserAnswers(resultCode: Int, data: Intent?) {
        val chooserClass = AccountManager.newChooseAccountIntent(null, null, arrayOf(GOOGLE_ACCOUNT_TYPE), null, null, null, null)
            .component!!.className
        chooser = Instrumentation.ActivityMonitor(chooserClass, Instrumentation.ActivityResult(resultCode, data), true)
            .also(instrumentation::addMonitor)
    }

    private fun chosen(name: String, type: String) = Intent()
        .putExtra(AccountManager.KEY_ACCOUNT_NAME, name)
        .putExtra(AccountManager.KEY_ACCOUNT_TYPE, type)

    private fun pickerResult(): Int {
        // ActivityScenario.getState() throws while Google's screens keep the picker paused,
        // so the picker's end is observed through the lifecycle monitor.
        val destroyed = CountDownLatch(1)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is GoogleAccountPickerActivity && stage == Stage.DESTROYED) destroyed.countDown()
        }
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback)
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val intent = GoogleAccountPickerActivity.intent(context)
            ActivityScenario.launchActivityForResult<GoogleAccountPickerActivity>(intent).use { scenario ->
                val deadline = System.currentTimeMillis() + 60_000
                while (!destroyed.await(500, TimeUnit.MILLISECONDS) && System.currentTimeMillis() < deadline) {
                    // Back out of any account screen Google puts over the picker.
                    if (device.currentPackageName != testPackage) device.pressBack()
                }
                assertEquals(1, chooser!!.hits)
                return scenario.result.resultCode
            }
        } finally {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback)
        }
    }

    private fun savedAccountName() = instrumentation.targetContext
        .getSharedPreferences("xbackup_google_account", Context.MODE_PRIVATE)
        .getString("account_name", null)

    @Test
    fun leavingTheChooserCancels() {
        chooserAnswers(Activity.RESULT_CANCELED, null)
        assertEquals(Activity.RESULT_CANCELED, pickerResult())
    }

    @Test
    fun aChooserAnswerWithoutAnAccountCancels() {
        chooserAnswers(Activity.RESULT_OK, null)
        assertEquals(Activity.RESULT_CANCELED, pickerResult())
    }

    @Test
    fun anAccountThatIsNotGoogleCancels() {
        chooserAnswers(Activity.RESULT_OK, chosen("user@example.com", "com.example"))
        assertEquals(Activity.RESULT_CANCELED, pickerResult())
    }

    @Test
    fun aGoogleAccountWithoutADriveTokenCancelsAndIsNotSaved() {
        chooserAnswers(Activity.RESULT_OK, chosen("not-on-this-device@example.com", GOOGLE_ACCOUNT_TYPE))
        assertEquals(Activity.RESULT_CANCELED, pickerResult())
        assertNull(savedAccountName())
    }
}
