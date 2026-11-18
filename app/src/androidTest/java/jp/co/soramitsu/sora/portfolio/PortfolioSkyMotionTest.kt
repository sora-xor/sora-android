@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.portfolio

import android.graphics.Bitmap
import android.os.Build
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
import jp.co.soramitsu.ui_core.lit.LitHeroRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The hero sky on a test clock. Where lit motion runs, the comet laps the ring for as long as the sky is on
 * screen. Without it (a phone under the full tier, power saving), once the ring has drawn itself in the frame
 * only changes with the tilt.
 */
@RunWith(AndroidJUnit4::class)
class PortfolioSkyMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // XOR wraps round behind the planet; VAL is highlighted.
    private val fan = listOf(FanSlice("xor", 70.0, 70), FanSlice("val", 30.0, 30))
    private val motion = FxPolicy(FxTier.FULL, reduceMotion = false, highContrast = false)
    private val still = FxPolicy(FxTier.REDUCED, reduceMotion = false, highContrast = false)
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

    @Test fun withoutLitMotionTheSkyHoldsStillAfterItsDrawIn() {
        sky(still)
        settle()
        val before = frame()
        compose.mainClock.advanceTimeBy(5_000L)
        assertTrue(before.sameAs(frame()))
    }

    @Test fun withLitMotionTheCometKeepsLapping() {
        // The comet belongs to the traced hero, which needs Android 13.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        sky(motion)
        settle()
        val before = frame()
        compose.mainClock.advanceTimeBy(1_500L)
        assertFalse(before.sameAs(frame()))
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

    @Test fun aPhoneWithoutTheShaderStillDrawsThePortfolioAndRespondsToTilt() {
        // A failed shader compilation is cached for the process. A capable emulator can exercise
        // the same fallback as an older phone by starting with that cached result.
        val renderer = LitHeroRenderer::class.java
        val lock = renderer.getDeclaredField("lock").apply { isAccessible = true }.get(null)!!
        val tried = renderer.getDeclaredField("tried").apply { isAccessible = true }
        val compiled = renderer.getDeclaredField("compiled").apply { isAccessible = true }
        val previous = synchronized(lock) {
            val saved = tried.getBoolean(null) to compiled.get(null)
            tried.setBoolean(null, true)
            compiled.set(null, null)
            saved
        }
        try {
            sky(motion)
            settle()
            val initial = frame()
            compose.mainClock.advanceTimeBy(1_500L)
            assertTrue("The fallback has no orbiting comet", initial.sameAs(frame()))
            compose.runOnIdle { tilt.x = 0.8f }
            compose.mainClock.advanceTimeBy(100L)
            assertFalse("The fallback planet and ring must respond to tilt", initial.sameAs(frame()))
        } finally {
            synchronized(lock) {
                compiled.set(null, previous.second)
                tried.setBoolean(null, previous.first)
            }
        }
    }

    @Test fun reduceMotionDrawsTheRingAtOnce() {
        sky(motion.copy(reduceMotion = true))
        compose.mainClock.advanceTimeBy(100L)
        val before = frame()
        settle()
        assertTrue(before.sameAs(frame()))
    }
}
