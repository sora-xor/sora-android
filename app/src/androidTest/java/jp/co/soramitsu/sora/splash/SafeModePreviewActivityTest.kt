package jp.co.soramitsu.sora.splash

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.sora.debug.SafeModePreviewActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeModePreviewActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun invalidStateFallsBackToRecoveryWithoutExposingTheSamplePhrase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, SafeModePreviewActivity::class.java).putExtra("state", "unrecognized-state")
        ActivityScenario.launch<SafeModePreviewActivity>(intent).use {
            compose.onNodeWithText(context.getString(R.string.safe_mode_keep_installed)).assertExists()
            compose.onAllNodesWithText("abandon").assertCountEquals(0)
        }
    }
    @Test fun lightPreviewUsesTheRequestedReadOnlyState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, SafeModePreviewActivity::class.java).putExtra("state", "U9_READ_ONLY_HOME").putExtra("dark", false)
        ActivityScenario.launch<SafeModePreviewActivity>(intent).use {
            compose.onNodeWithText(context.getString(R.string.safe_mode_read_only_title)).assertExists()
            compose.onAllNodesWithText("abandon").assertCountEquals(0)
        }
    }
}
