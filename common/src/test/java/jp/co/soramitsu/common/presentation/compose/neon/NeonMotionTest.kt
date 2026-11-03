package jp.co.soramitsu.common.presentation.compose.neon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The motion tokens of design spec 4.7.1: durations in order, and easings that start and end in place. */
class NeonMotionTest {

    @Test
    fun `durations grow from press to hero`() {
        val durations = listOf(NeonMotion.PRESS, NeonMotion.FAST, NeonMotion.BASE, NeonMotion.SLOW, NeonMotion.HERO)
        assertEquals(durations.sorted(), durations)
    }

    @Test
    fun `every easing starts at zero, ends at one and never runs backwards`() {
        for (easing in listOf(NeonMotion.EaseStandard, NeonMotion.EaseDecel, NeonMotion.EaseExit, NeonMotion.EaseSine)) {
            assertEquals(0f, easing.transform(0f), 1e-4f)
            assertEquals(1f, easing.transform(1f), 1e-4f)
            val samples = (0..20).map { easing.transform(it / 20f) }
            assertTrue(samples.toString(), samples.zipWithNext().all { (a, b) -> b >= a - 1e-4f })
        }
    }
}
