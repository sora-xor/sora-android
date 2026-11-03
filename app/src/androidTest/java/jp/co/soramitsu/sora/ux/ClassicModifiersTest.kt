package jp.co.soramitsu.sora.ux

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.debounceClickable
import jp.co.soramitsu.common.util.ext.shake
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The wrong-PIN shake and the debounced tap the classic screens are built with. */
@RunWith(AndroidJUnit4::class)
class ClassicModifiersTest {
    @get:Rule val compose = createComposeRule()

    @After fun inspectorOff() { isDebugInspectorInfoEnabled = false }

    @Test fun aWrongPinShakeRunsAndSaysWhenItHasEnded() {
        isDebugInspectorInfoEnabled = true
        var ended = 0
        val wrongPin = mutableStateOf(false)
        compose.setContent { Box(Modifier.size(40.dp).shake(wrongPin.value) { ended++ }.testTag("pin-dots")) }

        compose.runOnIdle { wrongPin.value = true }
        compose.waitForIdle()

        compose.runOnIdle { assertEquals(1, ended) }
    }

    @Test fun aDoubleTapOnADebouncedControlActsOnce() {
        var taps = 0
        val handler = DebounceClickHandler()
        compose.setContent { Box(Modifier.size(48.dp).testTag("control").debounceClickable(handler) { taps++ }) }

        compose.onNodeWithTag("control").performClick().performClick()

        compose.runOnIdle { assertEquals(1, taps) }
    }
}
