@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.PercentBar
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.TxHistoryErrorScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.MnemonicWordsColumn
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryAndAmountComponentsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun mnemonicIndicesRemainPairedWithTheirWordsInBothLayoutDirections() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        compose.setContent { SoraAppTheme { CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
            MnemonicWordsColumn(10, listOf("abandon", "ability", "able"))
        } } }
        for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = layout }
            for ((index, word) in listOf("abandon", "ability", "able").withIndex()) {
                compose.onNodeWithText((10 + index).toString()).assertIsDisplayed()
                compose.onNodeWithText(word).assertIsDisplayed()
            }
        }
    }
    @Test fun amountShortcutsSubmitTheExactPercentage() {
        val selected = mutableListOf<Int>()
        compose.setContent { SoraAppTheme { PercentBar { selected += it } } }
        for (percent in listOf(100, 75, 50, 25)) compose.onNodeWithText("$percent%").performClick()
        compose.runOnIdle { assertEquals(listOf(100, 75, 50, 25), selected) }
    }
    @Test fun historyErrorExplainsTheProblemAndRetriesOnlyWhenRequested() {
        var retries = 0
        compose.setContent { SoraAppTheme { TxHistoryErrorScreen { retries++ } } }
        compose.onNodeWithText(compose.activity.getString(R.string.activity_error_title)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, retries) }
        compose.onNodeWithText(compose.activity.getString(R.string.common_refresh)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
