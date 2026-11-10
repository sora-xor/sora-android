@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.portfolio

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
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
 * The hero sky on a test clock. The classic design runs no looping light: once the ring has drawn
 * itself in (at once on test devices, which run with animations off), the frame only changes with
 * the tilt.
 */
@RunWith(AndroidJUnit4::class)
class PortfolioSkyMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // XOR wraps round behind the planet; VAL is highlighted.
    private val fan = listOf(FanSlice("xor", 70.0, 70), FanSlice("val", 30.0, 30))
    private val motion = FxPolicy(FxTier.FULL, reduceMotion = false, opaqueGlass = false, highContrast = false)
    private val tilt = TiltState()

    private fun sky(fx: FxPolicy) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SoraAppTheme {
                CompositionLocalProvider(LocalFx provides fx) {
                    PortfolioSky(
                        fan = fan,
                        colorOf = { key -> if (key == "val") Color(0xFF3D7BFF) else Color(0xFFE2231A) },
                        highlighted = "val",
                        tilt = tilt,
                        onSliceTap = {},
                    ) {}
                }
            }
        }
    }

    private fun settle() = compose.mainClock.advanceTimeBy(SkyGeometry.INTRO_MS + 500L)

    private fun frame(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    @Test fun afterItsDrawInTheSkyHoldsStill() {
        sky(motion)
        settle()
        val before = frame()
        compose.mainClock.advanceTimeBy(5_000L)
        assertTrue(before.sameAs(frame()))
    }

    @Test fun theTiltMovesThePlanetAndItsRing() {
        sky(motion)
        settle()
        val before = frame()
        compose.runOnIdle {
            tilt.x = 0.8f
            tilt.y = -0.5f
        }
        compose.mainClock.advanceTimeBy(100L)
        assertFalse(before.sameAs(frame()))
    }

    @Test fun reduceMotionDrawsTheRingAtOnce() {
        sky(motion.copy(reduceMotion = true))
        compose.mainClock.advanceTimeBy(100L)
        val before = frame()
        settle()
        assertTrue(before.sameAs(frame()))
    }
}
