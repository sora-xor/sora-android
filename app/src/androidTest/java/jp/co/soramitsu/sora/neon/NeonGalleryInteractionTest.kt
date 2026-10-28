package jp.co.soramitsu.sora.neon

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.presentation.compose.neon.NeonGallery
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.theme.neon.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

/** The shipped design-review screen must remain navigable with large text and reduced effects. */
@RunWith(AndroidJUnit4::class)
class NeonGalleryInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var closed = 0
    private val dark = mutableStateOf(true)
    private fun show(policy: FxPolicy, fontScale: Float = 1f) {
        compose.setContent { SoraAppTheme(darkTheme = dark.value) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalFx provides policy) {
                Box(Modifier.size(320.dp, 520.dp)) { NeonGallery(onClose = { closed++ }) }
            }
        } }
    }
    @Test fun largeTextCanReachLastSectionAndReturnToClose() {
        show(FxPolicy(FxTier.MINIMAL, true, true, true), 2f)
        compose.onNodeWithText("mono cnUJ6X…Jfq7a14M").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }
    @Test fun disabledAndBusyExamplesDoNotExposeEnabledActions() {
        show(FxPolicy(FxTier.FULL, true, false, false))
        compose.onNodeWithText("Enter an amount").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Sending").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Technical details").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertEquals(0, closed) }
    }
    @Test fun themeChangeKeepsContentAndCloseActionAvailable() {
        show(FxPolicy(FxTier.REDUCED, true, false, false))
        compose.onNodeWithText("$12,480.52").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { dark.value = false }
        compose.onNodeWithText("$12,480.52").assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }
}
