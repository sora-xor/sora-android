package jp.co.soramitsu.ui_core.lit

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The baked materials: where their light and shade fall, and that what must be read on them can be. */
class LitBakeTest {
    private val scale = 2f

    private fun alpha(argb: Int) = argb ushr 24

    private fun color(argb: Int) = Color(argb or (0xFF shl 24))

    private fun channel(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    /** The pixel at a point in dp. */
    private fun LitPixels.atDp(x: Float, y: Float) = at((x * scale).toInt().coerceIn(0, width - 1), (y * scale).toInt().coerceIn(0, height - 1))

    @Test
    fun `the hash is the one the iOS app uses`() {
        assertEquals(0f, LitBake.hash(0, 0), 0f)
        assertEquals(0.06993669688324314f, LitBake.hash(1, 2), 1e-6f)
        assertEquals(0.6350589773094044f, LitBake.hash(-5, 77), 1e-6f)
        assertEquals(0.13366938434060718f, LitBake.hash(12345, 678), 1e-6f)
        assertEquals(0.38749738857134514f, LitBake.hash(383, 191), 1e-6f)
    }

    @Test
    fun `glass casts a soft shadow down and right, spills light up and left by day, and nothing under itself`() {
        val radius = 18f
        val lift = 16f
        val day = LitBake.glassShadowPixels(radius, lift, scale, dark = false)
        val margin = 26f
        val side = 2f * (radius + margin) + 2f
        assertEquals((side * scale).toInt(), day.width)
        assertEquals(0, alpha(day.atDp(side / 2f, side / 2f)))
        val below = day.atDp(side / 2f, side - margin + 4f)
        val above = day.atDp(side / 2f, margin - 1.5f)
        assertTrue("shade falls below the sheet", alpha(below) > 20 && luminance(color(below)) < 0.2)
        assertTrue("light spills above it", alpha(above) > 20 && luminance(color(above)) > 0.8)
        assertEquals("the shadow has ended at the image's edge", 0, alpha(day.atDp(side / 2f, side - 0.3f)))

        val night = LitBake.glassShadowPixels(radius, lift, scale, dark = true)
        assertTrue("by night nothing spills light", alpha(night.atDp(side / 2f, margin - 1.5f)) == 0 || luminance(color(night.atDp(side / 2f, margin - 1.5f))) < 0.05)
        assertTrue(alpha(night.atDp(side / 2f, side - margin + 4f)) > alpha(below))
    }

    @Test
    fun `a glass edge is bright where it faces the light and dim where it turns away`() {
        val radius = 18f
        val edge = LitBake.glassEdgePixels(radius, scale, dark = false)
        val side = 2f * radius + 2f * LitBake.EDGE_INSET + 2f
        val top = edge.atDp(side / 2f, 0.3f)
        val bottom = edge.atDp(side / 2f, side - 0.6f)
        assertTrue("the near edge is a bright line", alpha(top) > 150 && luminance(color(top)) > 0.9)
        assertTrue("the far edge is a dim rim", alpha(bottom) < 60 && luminance(color(bottom)) < 0.4)
        assertEquals("the face is left alone", 0, alpha(edge.atDp(side / 2f, side / 2f)))
        val night = LitBake.glassEdgePixels(radius, scale, dark = true)
        assertTrue("by night the edge catches a tenth of the light", alpha(night.atDp(side / 2f, 0.3f)) < 50)
    }

    @Test
    fun `a pad is lit from the top left and shaded to the bottom right`() {
        val radius = 16f
        val lift = 10f
        val shadow = LitBake.padShadowPixels(radius, lift, scale, dark = false)
        val margin = 13f
        val side = 2f * (radius + margin) + 2f
        assertEquals(0, alpha(shadow.atDp(side / 2f, side / 2f)))
        val light = shadow.atDp(margin - 1.5f, margin - 1.5f + radius)
        val shade = shadow.atDp(side - margin + 2f, side - margin - radius + 2f)
        assertTrue("light spills to the left of it", alpha(light) > 60 && luminance(color(light)) > 0.8)
        assertTrue("shade falls to the right of it", alpha(shade) > 30 && luminance(color(shade)) < 0.25)
        val edge = LitBake.padEdgePixels(radius, scale, dark = false)
        val edgeSide = 2f * radius + 2f * LitBake.EDGE_INSET + 2f
        assertTrue(luminance(color(edge.atDp(edgeSide / 2f, 0.5f))) > luminance(color(edge.atDp(edgeSide / 2f, edgeSide - 0.5f))))
    }

    @Test
    fun `a ruled well keeps a boundary that does not depend on the light`() {
        for (lit in listOf(LitDay, LitNight)) {
            val radius = 12f
            val depth = 2f
            val plain = LitBake.wellPixels(radius, depth, scale, lit.wellFloor, null, lit.dark)
            val ruled = LitBake.wellPixels(radius, depth, scale, lit.wellFloor, lit.outline, lit.dark)
            val side = 2f * LitBake.wellCorner(radius, depth) + 2f / scale
            val middle = side / 2f
            assertEquals("the floor is the floor's colour", lit.wellFloor, color(plain.atDp(middle, middle)))
            assertEquals(255, alpha(plain.atDp(middle, middle)))
            // All the way round: under the near lip, where the light never helps, and under the far one. The ring
            // is told from the surface the well is let into at 3:1 or better (3.06:1 at the least, by day against
            // the page). Against the well's own floor the ring, laid at 92 %, is 2.86:1 at the least by day.
            for ((x, y) in listOf(middle to 0.5f, middle to side - 0.5f, 0.5f to middle, side - 0.5f to middle)) {
                val ring = color(ruled.atDp(x, y))
                assertTrue("${lit.dark} ring at $x,$y against the page is ${ratio(ring, lit.page)}", ratio(ring, lit.page) >= 3.0)
                assertTrue("${lit.dark} ring at $x,$y against the sheet is ${ratio(ring, lit.surface)}", ratio(ring, lit.surface) >= 3.0)
                assertTrue("${lit.dark} ring at $x,$y against its floor is ${ratio(ring, lit.wellFloor)}", ratio(ring, lit.wellFloor) >= 2.8)
            }
        }
    }

    @Test
    fun `cut glass is a red slab whose inlaid label reads on it, darker when set down`() {
        for (lit in listOf(LitDay, LitNight)) {
            val width = 34f
            val height = 56f
            val glass = LitBake.cutGlassPixels(width, height, 12f, lit.red, lit.inlay, false, null, scale, lit.dark)
            val m = LitBake.CUT_GLASS_MARGIN
            assertEquals(((width + 2f * m) * scale).toInt(), glass.width)
            val centre = glass.atDp(m + width / 2f, m + height / 2f)
            assertEquals(255, alpha(centre))
            assertTrue("the table is red", color(centre).red > 0.6f && color(centre).green < 0.35f)
            // The flat red and its inlay are 4.71:1 by day. The bake gathers light low in the glass, so the table's
            // mid-line, where a label sits, is a little brighter than the flat red: 4.45:1 by day, 4.87:1 by night.
            assertTrue("${lit.dark} label on the table is ${ratio(lit.inlay, color(centre))}", ratio(lit.inlay, color(centre)) >= 4.4)
            // The facet that faces the light is brighter than the one that turns away.
            assertTrue(luminance(color(glass.atDp(m + width / 2f, m + 0.9f))) > luminance(color(glass.atDp(m + width / 2f, m + height - 0.9f))))
            val pressed = LitBake.cutGlassPixels(width, height, 12f, lit.red, lit.inlay, true, null, scale, lit.dark)
            assertTrue(luminance(color(pressed.atDp(m + width / 2f, m + height / 2f))) < luminance(color(centre)))
            // Its shadow is tighter when it is set down.
            assertTrue(alpha(pressed.atDp(m + width / 2f, m + height + 4f)) < alpha(glass.atDp(m + width / 2f, m + height + 4f)))
        }
    }

    @Test
    fun `a glyph is inlaid in white, flush in the glass`() {
        val size = 56f
        val pixels = (size * scale).toInt()
        // A square glyph in the middle third of the piece.
        val mask = LitMask(pixels, pixels, scale, FloatArray(pixels * pixels) { index ->
            val x = index % pixels
            val y = index / pixels
            if (x in pixels / 3 until 2 * pixels / 3 && y in pixels / 3 until 2 * pixels / 3) 1f else 0f
        })
        val piece = LitBake.cutGlassPixels(size, size, size / 2f, LitDay.red, LitDay.inlay, false, mask, scale, dark = false)
        val m = LitBake.CUT_GLASS_MARGIN
        val inlay = color(piece.atDp(m + size / 2f, m + size / 2f))
        val glass = color(piece.atDp(m + size / 2f, m + size * 0.2f))
        assertTrue("the inlay is white", luminance(inlay) > 0.6)
        assertTrue("the glass round it is red", glass.red > 0.6f && glass.green < 0.35f)
        assertTrue("the mark is told from the glass", ratio(inlay, glass) >= 3.0)
        assertEquals("the corners outside the disc are clear of glass", true, alpha(piece.atDp(m + 1f, m + 1f)) < 255)
    }

    @Test
    fun `a bead is a filled red silhouette with one glint`() {
        val diameter = 12f
        val bead = LitBake.beadPixels(diameter, scale, LitDay.redDeep, LitDay.redBright, dark = false)
        val side = diameter + 12f
        val centre = color(bead.atDp(side / 2f, side / 2f))
        assertEquals(255, alpha(bead.atDp(side / 2f, side / 2f)))
        assertTrue(centre.red > 0.5f && centre.green < 0.4f)
        // A filled place is told from an empty one's floor without the light.
        assertTrue(ratio(centre, LitDay.wellFloor) >= 3.0)
        var brightest = 0.0
        for (y in 0 until bead.height) for (x in 0 until bead.width) {
            if (alpha(bead.at(x, y)) == 255) brightest = maxOf(brightest, luminance(color(bead.at(x, y))))
        }
        assertTrue("the glint", brightest > 0.5)
    }

    @Test
    fun `a blurred mask keeps its weight and softens its edge`() {
        val mask = LitMask(9, 9, 1f, FloatArray(81) { if (it == 40) 1f else 0f })
        val soft = mask.blurred(1)
        assertEquals(1f, soft.a.sum(), 1e-4f)
        assertTrue(soft.a[40] < 1f && soft.a[41] > 0f)
        assertEquals(1f, mask.at(4.5f, 4.5f), 1e-6f)
        assertEquals(0.5f, mask.at(5f, 4.5f), 1e-6f)
        assertEquals(0f, mask.at(-3f, 4.5f), 0f)
    }
}
