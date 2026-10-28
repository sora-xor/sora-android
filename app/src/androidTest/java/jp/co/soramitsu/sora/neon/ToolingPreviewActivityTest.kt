package jp.co.soramitsu.sora.neon

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.tooling.PreviewActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real tooling activity and its public Android Studio intent contract. */
@RunWith(AndroidJUnit4::class)
class ToolingPreviewActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<PreviewActivity>? = null

    @After fun closePreview() { scenario?.close() }

    private fun launch(parameterized: Boolean = false, index: Int? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val method = if (parameterized) "ParameterizedWalletToolingPreview" else "PlainWalletToolingPreview"
        val intent = Intent(context, PreviewActivity::class.java)
            .putExtra("composable", "jp.co.soramitsu.sora.debug.ToolingPreviewFixturesKt.$method")
        if (parameterized) {
            intent.putExtra("parameterProviderClassName", "jp.co.soramitsu.sora.debug.WalletToolingPreviewProvider")
        }
        index?.let { intent.putExtra("parameterProviderIndex", it) }
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    private fun assertState(state: String) {
        compose.onAllNodesWithTag("tooling-preview-content").assertCountEquals(1)
        compose.onNodeWithTag("tooling-preview-content")
            .assertIsDisplayed().assertTextEquals("Wallet state: $state")
    }

    @Test fun noParameterPreviewUsesTheAppThemeWithoutProviderControls() {
        launch()
        compose.onNodeWithTag("tooling-preview-content")
            .assertIsDisplayed().assertTextEquals("Wallet preview ready")
        compose.onNodeWithText("Next").assertDoesNotExist()
    }

    @Test fun nextCyclesEveryProviderValueAndWrapsToTheFirst() {
        launch(parameterized = true)
        assertState("Ready")
        compose.onNodeWithText("Next").assertIsDisplayed().performClick()
        assertState("Review")
        compose.onNodeWithText("Next").performClick()
        assertState("Complete")
        compose.onNodeWithText("Next").performClick()
        assertState("Ready")
    }

    @Test fun explicitProviderIndexRendersOnlyThatValueWithoutNext() {
        launch(parameterized = true, index = 2)
        assertState("Complete")
        compose.onNodeWithText("Wallet state: Ready").assertDoesNotExist()
        compose.onNodeWithText("Wallet state: Review").assertDoesNotExist()
        compose.onNodeWithText("Next").assertDoesNotExist()
    }
}
