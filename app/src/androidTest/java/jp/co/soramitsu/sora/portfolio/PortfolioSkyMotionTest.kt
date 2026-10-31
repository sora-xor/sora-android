@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.portfolio

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxPolicy
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxTier
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FanSlice
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.PortfolioSky
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.SkyGeometry
import jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion.TiltState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The hero sky on a test clock. Test devices run with animations off, so the theme alone would
 * never start the looping light; these tests provide the full effect tier themselves. The light
 * laps the ring while the sky is on screen, and the frame stays still off screen and under
 * Reduce Motion.
 */
@RunWith(AndroidJUnit4::class)
class PortfolioSkyMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // XOR wraps round behind the planet; VAL is highlighted, so its arc gets the selected glow.
    private val fan = listOf(FanSlice("xor", 70.0, 70), FanSlice("val", 30.0, 30))
    private val motion = FxPolicy(FxTier.FULL, reduceMotion = false, opaqueGlass = false, highContrast = false)

    private fun sky(fx: FxPolicy, active: MutableState<Boolean> = mutableStateOf(true)) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SoraAppTheme {
                CompositionLocalProvider(LocalFx provides fx) {
                    PortfolioSky(
                        fan = fan,
                        colorOf = { key -> if (key == "val") Color(0xFF3D7BFF) else Color(0xFFE2231A) },
                        highlighted = "val",
                        tilt = TiltState(),
                        onSliceTap = {},
                        active = active.value,
                    ) {}
                }
            }
        }
        // The ring draws itself in and the highlight settles before frames are compared.
        compose.mainClock.advanceTimeBy(SkyGeometry.INTRO_MS + 400L)
    }

    private fun frame(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    @Test fun theLightLapsTheRingWhileTheSkyIsOnScreen() {
        sky(motion)
        val before = frame()
        compose.mainClock.advanceTimeBy(1_500L)
        assertFalse(before.sameAs(frame()))
    }

    @Test fun theSkyStandsStillOffScreenAndMovesAgainOnScreen() {
        val active = mutableStateOf(true)
        sky(motion, active)
        compose.runOnIdle { active.value = false }
        compose.mainClock.advanceTimeBy(100L)
        val parked = frame()
        compose.mainClock.advanceTimeBy(1_500L)
        assertTrue(parked.sameAs(frame()))

        compose.runOnIdle { active.value = true }
        compose.mainClock.advanceTimeBy(1_500L)
        assertFalse(parked.sameAs(frame()))
    }

    @Test fun reduceMotionDrawsAStillSky() {
        sky(motion.copy(reduceMotion = true))
        val before = frame()
        compose.mainClock.advanceTimeBy(1_500L)
        assertTrue(before.sameAs(frame()))
    }
}
