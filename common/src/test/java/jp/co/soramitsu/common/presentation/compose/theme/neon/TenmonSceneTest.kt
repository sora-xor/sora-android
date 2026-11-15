/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.common.presentation.compose.theme.neon

import jp.co.soramitsu.ui_core.sacred.SacredGeometry
import jp.co.soramitsu.ui_core.sacred.TenmonScene
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launch flight's numbers. The iOS app has the same scene (SacredTenmonScene) and the same expectations
 * (SacredMotionTests), so the two apps fly the same way.
 */
class TenmonSceneTest {

    private fun scene(keepsPlanet: Boolean = false) = TenmonScene(
        width = 603f,
        height = 1311f,
        pixelsPerDp = 1.5f,
        landingX = 301.5f,
        landingY = 228f,
        landingRadius = 48f,
        pageRed = 0.91f,
        pageGreen = 0.886f,
        pageBlue = 0.87f,
        keepsPlanet = keepsPlanet,
    )

    @Test
    fun `the camera waits where the way through begins`() {
        val scene = scene()
        val waiting = scene.uniforms(TenmonScene.Phase.INTRO, 1f, 1f, 0f)
        val leaving = scene.uniforms(TenmonScene.Phase.OUTRO, 0f, 1f, 0f)
        assertArrayEquals(waiting.flight, leaving.flight, 0f)
        assertArrayEquals(waiting.lens, leaving.lens, 0f)
        assertEquals(TenmonScene.HOLD_Z, waiting.flight[0], 0f)
        val holding = scene.uniforms(TenmonScene.Phase.HOLD, 0f, 1f, 0f)
        assertArrayEquals(waiting.flight, holding.flight, 0f)
    }

    @Test
    fun `it rises out of the dark and day breaks only at the end`() {
        val scene = scene()
        assertEquals(0f, scene.uniforms(TenmonScene.Phase.INTRO, 0f, 0f, 0f).state[0], 0f)
        assertEquals(1f, scene.uniforms(TenmonScene.Phase.INTRO, 1f, 1f, 0f).state[0], 0f)
        assertEquals(0f, scene.uniforms(TenmonScene.Phase.OUTRO, 0.6f, 2f, 0f).state[3], 0f)
        val end = scene.uniforms(TenmonScene.Phase.OUTRO, 1f, 2f, 0f)
        assertEquals(1f, end.state[3], 0f)
        assertEquals("the armillary's rings have come to rest", 1f, end.state[1], 0f)
    }

    @Test
    fun `the planet lands on its place at its size, past the last gate`() {
        val scene = scene()
        val end = scene.uniforms(TenmonScene.Phase.OUTRO, 1f, 2f, 0f)
        assertEquals(scene.endZ, end.flight[0], 0.001f)
        assertEquals(301.5f, end.lens[0], 0.001f)
        assertEquals(228f, end.lens[1], 0.001f)
        val radiusOnScreen = scene.focal * TenmonScene.PLANET_RADIUS / (TenmonScene.PLANET_Z - end.flight[0])
        assertEquals(48f, radiusOnScreen, 0.01f)
        assertTrue(scene.endZ > 37f)
    }

    @Test
    fun `the page says whether the planet stays`() {
        assertEquals(1f, scene(keepsPlanet = false).uniforms(TenmonScene.Phase.HOLD, 0f, 0f, 0f).page[3], 0f)
        assertEquals(0f, scene(keepsPlanet = true).uniforms(TenmonScene.Phase.HOLD, 0f, 0f, 0f).page[3], 0f)
    }

    @Test
    fun `the board's plate runs on to its next mark and stops`() {
        val hour = (PI / 6).toFloat()
        val turn = TenmonScene.turn(2f)
        assertEquals(2f / TenmonScene.HOUR_SECONDS * hour, turn, 0.0001f)
        assertEquals(turn, TenmonScene.settledTurn(turn, 0f), 0.0001f)
        val settled = TenmonScene.settledTurn(turn, 1f)
        assertTrue("never backwards", settled >= turn)
        assertEquals("on a mark", (settled / hour).roundToInt().toFloat(), settled / hour, 0.0001f)
        assertEquals(0f, TenmonScene.settledTurn(0f, 1f), 0f)
    }

    @Test
    fun `the flight takes the same time as on the iPhone`() {
        assertEquals(1.0f, TenmonScene.INTRO_SECONDS, 0f)
        assertEquals(SacredGeometry.beatMillis(4) / 1000f, TenmonScene.OUTRO_SECONDS, 0.001f)
    }

    @Test
    fun `beats step by the golden ratio and the seed stands on a hexagon`() {
        assertEquals(110, SacredGeometry.beatMillis(0))
        assertEquals(178, SacredGeometry.beatMillis(1))
        assertEquals(1220, SacredGeometry.beatMillis(5))
        val first = SacredGeometry.seedCentre(0, 100f)
        assertEquals(0f, first.x, 0.001f)
        assertEquals("the first stands at the top", -50f, first.y, 0.001f)
        for (index in 0 until 6) {
            val centre = SacredGeometry.seedCentre(index, 100f)
            val next = SacredGeometry.seedCentre((index + 1) % 6, 100f)
            assertEquals(50f, hypot(centre.x, centre.y), 0.001f)
            assertEquals("a hexagon's side", 50f, hypot(next.x - centre.x, next.y - centre.y), 0.001f)
        }
    }
}
