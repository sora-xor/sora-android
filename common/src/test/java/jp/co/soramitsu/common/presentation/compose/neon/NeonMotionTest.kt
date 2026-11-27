package jp.co.soramitsu.common.presentation.compose.neon

import org.junit.Assert.assertTrue
import org.junit.Test

/** The motion tokens of design spec 4.7.1. */
class NeonMotionTest {

    @Test
    fun `a press is quicker than its release`() {
        assertTrue(NeonMotion.PRESS < NeonMotion.FAST)
    }
}
