package jp.co.soramitsu.sora.irohaconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Invalid entry points must stop before any relay, account, or signing request is constructed. */
@RunWith(AndroidJUnit4::class)
class IrohaConnectEntryActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun launch() = ActivityScenario.launch<IrohaConnectActivity>(Intent(context, IrohaConnectActivity::class.java))
    @Test fun emptyEntryShowsAnErrorAndKeepsItsWindowSecureUntilClosed() {
        launch().use { scenario ->
            compose.onNodeWithText(context.getString(R.string.wallet_connect_empty)).assertExists()
            scenario.onActivity { assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0); assertNull(it.intent.data) }
            compose.onNodeWithText(context.getString(R.string.wallet_connect_done)).performClick()
            compose.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
    }
    @Test fun malformedEntryIsConsumedAndNeverOffersApproval() {
        launch().use { scenario ->
            scenario.onActivity { it.startActivity(Intent(it, IrohaConnectActivity::class.java)
                .setData(Uri.parse("irohaconnect://invalid-fixture")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.wallet_connect_cannot_open)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(context.getString(R.string.wallet_connect_cannot_open)).assertExists()
            compose.onNodeWithTag("approve_pairing").assertDoesNotExist()
            compose.onNodeWithTag("approve_signing").assertDoesNotExist()
            scenario.onActivity { assertNull(it.intent.data) }
            scenario.onActivity { assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        }
    }
    @Test fun aNewMalformedIntentReplacesTheOldErrorWithoutRetainingItsUri() {
        launch().use { scenario ->
            compose.onNodeWithText(context.getString(R.string.wallet_connect_empty)).assertExists()
            scenario.onActivity { it.startActivity(Intent(it, IrohaConnectActivity::class.java)
                .setData(Uri.parse("irohaconnect://invalid-next-fixture")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.wallet_connect_cannot_open)).fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { assertNull(it.intent.data) }
            androidx.test.espresso.Espresso.pressBackUnconditionally()
            compose.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
    }
}
