package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import androidx.compose.ui.geometry.Offset
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FanSlice
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hero's ring is the chart: every ray's stretch of band leads back to its own slice. */
class PortfolioRingTest {

    private val fan = listOf(FanSlice("first", 40.0, 40), FanSlice("second", 60.0, 60))
    private val center = Offset(540f, 456f)
    private val radius = 384f
    private val band = 39f
    private val slop = 36f
    private val planet = 138f

    private fun pose(x: Float = 0f, y: Float = 0f) = RingPose.at(center, radius, x, y)

    private fun tap(pose: RingPose, p: Offset, fan: List<FanSlice> = this.fan) =
        ringSliceAt(fan, pose, p, band, slop, planet)?.key

    @Test
    fun `every ray maps to its slice at rest and tilted, unless the planet hides it`() {
        listOf(0f to 0f, 1f to -1f, -0.6f to 0.8f).forEach { (x, y) ->
            val pose = pose(x, y)
            for (ray in 0 until SkyGeometry.RAYS) {
                val deg = ringStart(fan) + (ray + 0.5f) * SkyGeometry.DEG_PER_RAY
                val p = pose.point(deg)
                val hidden = sin(deg * PI / 180) < 0 && (p - center).getDistance() < planet
                val expected = if (hidden) null else if (ray < 40) "first" else "second"
                assertEquals("ray $ray at ($x, $y)", expected, tap(pose, p))
            }
        }
    }

    @Test
    fun `the largest slice is centred behind the planet and the slices close the ring`() {
        val arcs = ringArcs(fan)
        assertEquals(270f, arcs[1].start + arcs[1].sweep / 2f, 1e-3f)
        assertEquals(arcs[0].start + arcs[0].sweep, arcs[1].start, 1e-4f)
        assertEquals(360f, arcs.sumOf { it.sweep.toDouble() }.toFloat(), 1e-3f)
    }

    @Test
    fun `a small slice comes round to the front instead of hiding behind the planet`() {
        val lopsided = listOf(FanSlice("main", 94.0, 94), FanSlice("small", 6.0, 6))
        listOf(0f to 1f, 0f to -1f, 1f to 0f).forEach { (x, y) ->
            val pose = pose(x, y)
            assertEquals("small", tap(pose, pose.point(90f), lopsided))
            for (ray in 94 until SkyGeometry.RAYS) {
                val p = pose.point(ringStart(lopsided) + (ray + 0.5f) * SkyGeometry.DEG_PER_RAY)
                assertEquals("ray $ray at ($x, $y)", "small", tap(pose, p, lopsided))
            }
        }
    }

    @Test
    fun `the plane round-trips through the screen`() {
        val pose = pose(0.4f, -0.3f)
        listOf(0f, 37f, 90f, 181f, 270f, 333f).forEach { deg ->
            val q = pose.toPlane(pose.point(deg))
            val t = deg * PI.toFloat() / 180f
            assertEquals(radius * cos(t), q.x, 1e-2f)
            assertEquals(radius * sin(t), q.y, 1e-2f)
        }
    }

    @Test
    fun `the planet, the sky round the ring and its hidden back take no taps`() {
        val pose = pose()
        assertNull(tap(pose, center))
        assertNull(tap(pose, pose.point(0f, radius * 1.5f)))
        assertNull(tap(pose, pose.point(0f, radius * 0.6f)))
        assertNull(tap(pose, pose.point(270f)))
        assertNull(tap(pose, pose.point(90f), emptyList()))
    }

    @Test
    fun `the thin front of the band stays tappable over the planet and a little beyond`() {
        val pose = pose()
        val front = pose.point(90f)
        assertTrue((front - center).getDistance() < planet)
        assertEquals("first", tap(pose, front))
        assertEquals("first", tap(pose, front + Offset(0f, slop * 0.8f)))
    }
}
