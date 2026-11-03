package jp.co.soramitsu.sora.neon

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The button states the classic screens use: a loading button and a round icon button. */
@RunWith(AndroidJUnit4::class)
class NeonButtonStatesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aLoadingButtonSaysItIsLoadingAndIgnoresTaps() {
        var taps = 0
        compose.setContent {
            SoraAppTheme {
                NeonButton(size = 40.dp, variant = NeonVariant.Primary, text = "Send", loading = true, onClick = { taps++ })
            }
        }
        val loading = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.common_loading)

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, loading)).performClick()

        compose.runOnIdle { assertEquals(0, taps) }
    }

    @Test fun aRoundButtonWithATrailingIconIsAtLeastItsSizeAndTaps() {
        var taps = 0
        compose.setContent {
            SoraAppTheme {
                NeonButton(
                    size = 40.dp,
                    variant = NeonVariant.Secondary,
                    text = "Next",
                    rightIcon = painterResource(R.drawable.ic_arrow_left),
                    shape = CircleShape,
                    onClick = { taps++ },
                )
            }
        }

        compose.onNode(hasClickAction()).assertWidthIsAtLeast(40.dp).performClick()
        compose.onNodeWithText("Next").performClick()

        compose.runOnIdle { assertEquals(2, taps) }
    }
}
