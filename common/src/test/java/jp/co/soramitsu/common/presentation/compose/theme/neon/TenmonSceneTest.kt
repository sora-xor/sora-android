/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.common.presentation.compose.theme.neon

import androidx.compose.ui.graphics.toArgb
import jp.co.soramitsu.ui_core.lit.LitDay
import jp.co.soramitsu.ui_core.sacred.SacredGeometry
import jp.co.soramitsu.ui_core.sacred.TenmonFlight
import jp.co.soramitsu.ui_core.sacred.TenmonScene
import jp.co.soramitsu.ui_core.sacred.TenmonTimeline
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launch scene's numbers. The iOS app has the same scene (SacredTenmonScene) and the same expectations
 * (SacredMotionTests, "The traced scene"), so the two apps open the same way.
 */
class TenmonSceneTest {

    private fun scene(keepsMark: Boolean = true) = TenmonScene(
        width = 603f,
        height = 1311f,
        pixelsPerDp = 1.5f,
        landingX = 301.5f,
        landingY = 228f,
        landingRadius = 48f,
        pageRed = 0.925f,
        pageGreen = 0.914f,
        pageBlue = 0.89f,
        keepsMark = keepsMark,
    )

    @Test
    fun `the mark comes down out of the lens and the rings spread from under it`() {
        val scene = scene()
        assertEquals("the mark is the logo's old share of the width", 0.155f * 603f, scene.rest, 0.001f)
        val first = scene.uniforms(TenmonScene.Phase.INTRO, 0f, 0f, 0f)
        assertEquals(301.5f, first.lens[0], 0f)
        assertEquals(655.5f, first.lens[1], 0f)
        assertEquals("large and out of focus at the lens", scene.rest * 3.6f, first.lens[2], 0.01f)
        assertEquals(scene.rest, first.lens[3], 0f)
        assertEquals("still at the lens: nothing of it is seen yet", 1f, first.state[2], 0f)
        assertEquals("no ring before it lands", 0f, first.state[0], 0f)
        assertEquals(0f, TenmonScene.markArriving(0f).seen, 0f)

        val landed = TenmonScene.markArriving(0.26f)
        assertEquals(0f, landed.lift, 0.0001f)
        assertEquals(1f, landed.seen, 0f)
        assertTrue("the page gives under it", TenmonScene.markArriving(0.36f).scale < 1f)
        assertEquals(1f, TenmonScene.markArriving(1f).scale, 0.0001f)
        assertTrue("the rings start as it lands", TenmonScene.bloom(0.2f) < 0.0001f)
        assertEquals(1f, TenmonScene.bloom(1f), 0f)

        // Seven rings, each wider than the last and all wider than the mark; the outermost leaves first, and every
        // one has arrived when the clock has run out.
        assertEquals(7, TenmonScene.RING_COUNT)
        for (index in 0 until TenmonScene.RING_COUNT) {
            assertEquals(0f, TenmonScene.ringSpread(index, 0f), 0.0001f)
            assertEquals(1f, TenmonScene.ringSpread(index, 1f), 0.0001f)
            assertTrue("a twentieth over at most", TenmonScene.ringSpread(index, 0.6f) <= 1.06f)
            val inside = if (index == 0) 1.4f else TenmonScene.ringRadius(index - 1)
            assertTrue(TenmonScene.ringRadius(index) > inside)
        }
        assertTrue(TenmonScene.ringSpread(6, 0.1f) > 0f)
        assertEquals(0f, TenmonScene.ringSpread(0, 0.1f), 0.0001f)
        // The outermost reaches the corners of a phone, so the rings fill the page.
        assertTrue(TenmonScene.ringRadius(6) * scene.rest > hypot(603f, 1311f) / 2f * 0.98f)
    }

    @Test
    fun `nothing jumps where the phases meet`() {
        val scene = scene()
        val spread = scene.uniforms(TenmonScene.Phase.INTRO, 1f, 1f, 0f)
        val waiting = scene.uniforms(TenmonScene.Phase.HOLD, 0f, 1f, 0f)
        assertArrayEquals(spread.flight, waiting.flight, 0f)
        assertArrayEquals(spread.lens, waiting.lens, 0f)
        assertArrayEquals(spread.state, waiting.state, 0f)
        assertEquals("the mark at rest in the middle", scene.rest, waiting.lens[2], 0f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f), waiting.state, 0f)

        // Whenever the wait ends, the way out begins exactly where the wait stood.
        for (waited in floatArrayOf(0f, 0.4f, 1.3f, 2.75f, 9f)) {
            val held = scene.uniforms(TenmonScene.Phase.HOLD, 0f, 3f, waited, 0.3f, -0.2f)
            val leaving = scene.uniforms(TenmonScene.Phase.OUTRO, 0f, 3f, waited, 0.3f, -0.2f)
            assertArrayEquals(held.flight, leaving.flight, 0f)
            assertArrayEquals(held.lens, leaving.lens, 0f)
            assertArrayEquals(held.state, leaving.state, 0f)
        }
    }

    @Test
    fun `the mark sets down where the next screen shows it and the rings are gone`() {
        val end = scene().uniforms(TenmonScene.Phase.OUTRO, 1f, 2f, 1.7f)
        assertEquals(301.5f, end.lens[0], 0.001f)
        assertEquals(228f, end.lens[1], 0.001f)
        assertEquals("the size the PIN screen draws it", 48f, end.lens[2], 0.001f)
        assertEquals("set down", 0f, end.state[2], 0.0001f)
        assertEquals("the rings have sunk into the page", 1f, end.state[3], 0f)
        assertArrayEquals(
            "the mark stays for the PIN screen to take over",
            floatArrayOf(0.925f, 0.914f, 0.89f, 0f), end.page, 0f,
        )
        assertEquals(1f, scene(keepsMark = false).uniforms(TenmonScene.Phase.OUTRO, 1f, 2f, 0f).page[3], 0f)
        // Halfway it is nearest the lens, and the rings are already going.
        val halfway = scene().uniforms(TenmonScene.Phase.OUTRO, 0.5f, 2f, 0f)
        assertTrue(halfway.state[2] > 0.09f)
        assertTrue(halfway.state[3] > 0.5f)
        assertTrue(halfway.state[3] < 1f)
        // A landing of no size is still a mark.
        assertEquals(1f, TenmonScene(603f, 1311f, 1.5f, 301.5f, 228f, 0f, 1f, 1f, 1f, true).landingRadius, 0f)
    }

    @Test
    fun `the pulse leaves the mark and passes the rings in turn`() {
        fun crest(place: Int): Float =
            (0 until 190).map { it * 0.01f + TenmonScene.PULSE_PERIOD }.maxByOrNull { TenmonScene.pulse(place, it) } ?: 0f
        assertEquals("nothing stirs the moment the rings come to rest", 0f, TenmonScene.pulse(0, 0f), 0f)
        assertEquals("nor before", 0f, TenmonScene.pulse(0, -1f), 0f)
        var last = crest(0)
        for (place in 1..TenmonScene.RING_COUNT) {
            val here = crest(place)
            assertTrue("ring ${place - 1} swells after the one inside it", here > last)
            assertEquals(1f, TenmonScene.pulse(place, here), 0.02f)
            last = here
        }
        assertEquals("once each period", TenmonScene.pulse(2, 3f), TenmonScene.pulse(2, 3f + TenmonScene.PULSE_PERIOD), 0.0001f)
        assertEquals(1.9f, TenmonScene.PULSE_PERIOD, 0f)
        assertEquals(0.10f, TenmonScene.RING_STEP, 0f)
    }

    @Test
    fun `turning the phone leans the stack and moves the lamp`() {
        val scene = scene()
        val straight = scene.uniforms(TenmonScene.Phase.HOLD, 0f, 1f, 0f)
        val turned = scene.uniforms(TenmonScene.Phase.HOLD, 0f, 1f, 0f, 0.5f, -1f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), straight.flight, 0f)
        assertArrayEquals(floatArrayOf(0.45f, -0.9f, 0.25f, -0.5f), turned.flight, 0.00001f)
        assertArrayEquals("the mark stays where it is", straight.lens, turned.lens, 0f)
        // Left alone the lamp still wanders a little, so the shell is never quite still.
        val later = scene.uniforms(TenmonScene.Phase.HOLD, 0f, 3f, 2f)
        assertNotEquals(0f, later.flight[2], 0f)
        assertTrue(abs(later.flight[2]) < 0.11f)
        assertEquals(0f, later.flight[0], 0f)
        // While the rings spread the camera comes over the stack from one side.
        val coming = scene.uniforms(TenmonScene.Phase.INTRO, 0f, 0f, 0f)
        assertEquals(0.22f, coming.flight[0], 0.0001f)
        assertEquals(-1.15f, coming.flight[1], 0.0001f)
    }

    @Test
    fun `a wide window measures the rings by its height`() {
        assertEquals(0.155f * 603f, TenmonScene.restRadius(603f, 1311f), 0.001f)
        assertEquals("an unfolded screen", 0.155f * 0.6f * 1000f, TenmonScene.restRadius(1200f, 1000f), 0.001f)
    }

    @Test
    fun `the scene takes the same time as on the iPhone`() {
        assertEquals(1.0f, TenmonScene.INTRO_SECONDS, 0f)
        assertEquals(0.6f, TenmonScene.OUTRO_SECONDS, 0f)
    }

    // The clock both screens read.

    private val second = 1_000_000_000L

    @Test
    fun `the clock runs the rings out, waits, and goes on when the first screen is ready`() {
        val timeline = TenmonTimeline()
        assertFalse(timeline.isStarted)
        val idle = timeline.at(5 * second)
        assertEquals(TenmonScene.Phase.INTRO, idle.phase)
        assertEquals("nothing moves before the first frame is drawn", 0f, idle.progress, 0f)
        assertFalse(idle.over)

        val start = 7 * second
        assertTrue(timeline.start(start))
        assertFalse("the second screen joins the first's clock", timeline.start(start + second))
        assertTrue(timeline.isStarted)
        val coming = timeline.at(start + second / 2)
        assertEquals(TenmonScene.Phase.INTRO, coming.phase)
        assertEquals(0.5f, coming.progress, 0.0001f)
        assertEquals(0.5f, coming.clock, 0.0001f)
        assertEquals(0f, coming.waited, 0f)
        val spread = timeline.at(start + second)
        assertEquals(TenmonScene.Phase.HOLD, spread.phase)
        assertEquals(0f, spread.waited, 0f)
        val waiting = timeline.at(start + 5 * second / 2)
        assertEquals(TenmonScene.Phase.HOLD, waiting.phase)
        assertEquals(1.5f, waiting.waited, 0.0001f)
        assertFalse(timeline.isLeaving)

        // The way out begins where the wait stood, and the pulse runs on through it.
        timeline.leave(start + 5 * second / 2)
        assertTrue(timeline.isLeaving)
        val leaving = timeline.at(start + 5 * second / 2)
        assertEquals(TenmonScene.Phase.OUTRO, leaving.phase)
        assertEquals(0f, leaving.progress, 0f)
        assertEquals(1.5f, leaving.waited, 0.0001f)
        val halfway = timeline.at(start + 28 * second / 10)
        assertEquals(0.5f, halfway.progress, 0.0001f)
        assertEquals(1.8f, halfway.waited, 0.0001f)
        assertFalse(halfway.over)
        val landed = timeline.at(start + 32 * second / 10)
        assertEquals(1f, landed.progress, 0f)
        assertTrue(landed.over)
        // A second word changes nothing, and the end stays the end.
        timeline.leave(start + 10 * second)
        val after = timeline.at(start + 4 * second)
        assertEquals(TenmonScene.Phase.OUTRO, after.phase)
        assertEquals(1f, after.progress, 0f)
        assertTrue(after.over)
    }

    @Test
    fun `a first screen ready early waits for the rings to lie spread`() {
        val during = TenmonTimeline()
        during.start(second)
        during.leave(second + 3 * second / 10)
        assertEquals(TenmonScene.Phase.INTRO, during.at(second + 6 * second / 10).phase)
        val turn = during.at(2 * second)
        assertEquals(TenmonScene.Phase.OUTRO, turn.phase)
        assertEquals(0f, turn.progress, 0f)
        assertEquals(0f, turn.waited, 0f)

        // Ready before a frame was drawn at all: the rings still spread first.
        val before = TenmonTimeline()
        before.leave(second / 2)
        assertTrue(before.isLeaving)
        assertEquals(TenmonScene.Phase.INTRO, before.at(second).phase)
        assertTrue(before.start(3 * second))
        assertEquals(TenmonScene.Phase.INTRO, before.at(3 * second + second / 2).phase)
        assertEquals(TenmonScene.Phase.OUTRO, before.at(4 * second).phase)
        assertTrue(before.at(4 * second + 7 * second / 10).over)
    }

    // Which phones play it.

    @Test
    fun `the traced scene needs Android 13, the motion tier and a cool phone`() {
        assertEquals(TenmonFlight.Mode.TRACED, TenmonFlight.mode(motion = true, sdkInt = 33, hot = false))
        assertEquals(TenmonFlight.Mode.TRACED, TenmonFlight.mode(motion = true, sdkInt = 36, hot = false))
        assertEquals("no shader language before 13", TenmonFlight.Mode.STILL, TenmonFlight.mode(motion = true, sdkInt = 32, hot = false))
        assertEquals(
            "power saving, or animations removed",
            TenmonFlight.Mode.STILL, TenmonFlight.mode(motion = false, sdkInt = 36, hot = false),
        )
        assertEquals(TenmonFlight.Mode.STILL, TenmonFlight.mode(motion = true, sdkInt = 36, hot = true))
        assertFalse("nothing to carry on before a scene has begun", TenmonFlight.isPlaying)
    }

    @Test
    fun `the scene plays on the light page`() {
        assertEquals(LitDay.page.toArgb(), TenmonFlight.PAGE)
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
