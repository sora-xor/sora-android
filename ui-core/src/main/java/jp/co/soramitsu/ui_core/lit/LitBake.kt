package jp.co.soramitsu.ui_core.lit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A rendered image: straight (not premultiplied) ARGB pixels, row 0 at the top. */
internal class LitPixels(val width: Int, val height: Int, val argb: IntArray) {
    fun toImage(): ImageBitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

    fun at(x: Int, y: Int): Int = argb[y * width + x]
}

/** A coverage mask, row 0 at the top. Blurred, its slope is the relief of an inlay. */
internal class LitMask(val width: Int, val height: Int, val scale: Float, val a: FloatArray) {
    private fun sample(x: Int, y: Int): Float = if (x < 0 || y < 0 || x >= width || y >= height) 0f else a[y * width + x]

    /** The coverage at a point, in dp. */
    fun at(x: Float, y: Float): Float {
        val fx = x * scale - 0.5f
        val fy = y * scale - 0.5f
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        val top = sample(x0, y0) + (sample(x0 + 1, y0) - sample(x0, y0)) * tx
        val bottom = sample(x0, y0 + 1) + (sample(x0 + 1, y0 + 1) - sample(x0, y0 + 1)) * tx
        return top + (bottom - top) * ty
    }

    fun blurred(radius: Int): LitMask {
        if (radius <= 0) return this
        val out = a.copyOf()
        val scratch = FloatArray(a.size)
        val window = (2 * radius + 1).toFloat()
        repeat(2) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var sum = 0f
                    for (k in -radius..radius) sum += out[y * width + (x + k).coerceIn(0, width - 1)]
                    scratch[y * width + x] = sum / window
                }
            }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var sum = 0f
                    for (k in -radius..radius) sum += scratch[(y + k).coerceIn(0, height - 1) * width + x]
                    out[y * width + x] = sum / window
                }
            }
        }
        return LitMask(width, height, scale, out)
    }
}

/**
 * The materials, rendered per pixel once and cached (the iOS app's `LitImages`, number for number). Sizes are in
 * dp; `scale` is pixels per dp. Pixels are computed premultiplied, in sRGB. By day the light spills white round
 * raised things; by night nothing glows: edges catch a tenth of the light and shadows are black.
 */
internal object LitBake {
    private val cache = LruCache<String, Any>(200)

    /**
     * The materials are also baked ahead on a background thread. Asking for one that is being baked waits for it;
     * asking for any other does not: each image has a lock of its own, so a screen's first frame never queues
     * behind the page or a pressed button being baked for later. A lock is kept for as long as the app runs (one
     * small object per image ever asked for), so the same image is never baked twice at once.
     */
    private val bakeLocks = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private inline fun <T : Any> cached(key: String, make: () -> T): T {
        cache.get(key)?.let { return it as T }
        return synchronized(bakeLocks.getOrPut(key) { Any() }) {
            cache.get(key) ?: make().also { cache.put(key, it) }
        } as T
    }

    private fun sat(v: Float) = v.coerceIn(0f, 1f)

    fun smooth(a: Float, b: Float, v: Float): Float {
        val t = sat((v - a) / (b - a))
        return t * t * (3f - 2f * t)
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun linear(v: Float) = v.pow(2.2f)

    private fun srgb(v: Float) = sat(v).pow(1f / 2.2f)

    /** A gentle shoulder, so highlights roll off instead of clipping. */
    private fun film(v: Float) = v / (1f + 0.18f * v) * 1.14f

    /** An integer hash, the same on both apps (wrapping 32-bit arithmetic), in 0...1. */
    fun hash(x: Int, y: Int): Float {
        var h = x * 374_761_393 + y * 668_265_263
        h = (h xor (h ushr 13)) * 1_274_126_177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16_777_215f
    }

    /** Value noise in 0...1 that repeats every `period` cells. */
    private fun noise(x: Float, y: Float, period: Int, seed: Int): Float {
        val xf = floor(x)
        val yf = floor(y)
        val fx = x - xf
        val fy = y - yf
        val xi = xf.toInt()
        val yi = yf.toInt()
        fun wrap(v: Int) = ((v % period) + period) % period
        val a = hash(wrap(xi) + seed, wrap(yi))
        val b = hash(wrap(xi + 1) + seed, wrap(yi))
        val c = hash(wrap(xi) + seed, wrap(yi + 1))
        val d = hash(wrap(xi + 1) + seed, wrap(yi + 1))
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        return mix(mix(a, b, ux), mix(c, d, ux), uy)
    }

    /** Towards the window, with z towards the viewer; and halfway between it and the eye. */
    private const val KEY_LENGTH = 0.99644367f
    const val KEY_X = -0.46f / KEY_LENGTH
    const val KEY_Y = -0.62f / KEY_LENGTH
    const val KEY_Z = 0.63f / KEY_LENGTH
    private val halfLength = sqrt(KEY_X * KEY_X + KEY_Y * KEY_Y + (KEY_Z + 1f) * (KEY_Z + 1f))
    private val HALF_X = KEY_X / halfLength
    private val HALF_Y = KEY_Y / halfLength
    private val HALF_Z = (KEY_Z + 1f) / halfLength

    /** An RGBA pixel, premultiplied. */
    class Pixel {
        @JvmField var r = 0f
        @JvmField var g = 0f
        @JvmField var b = 0f
        @JvmField var a = 0f

        fun clear() {
            r = 0f
            g = 0f
            b = 0f
            a = 0f
        }

        /** Lays a straight colour at an opacity over this pixel. */
        fun over(cr: Float, cg: Float, cb: Float, alpha: Float) {
            val keep = 1f - alpha
            r = cr * alpha + r * keep
            g = cg * alpha + g * keep
            b = cb * alpha + b * keep
            a = alpha + a * keep
        }

        /** Lays a straight colour at an opacity under this pixel. */
        fun under(cr: Float, cg: Float, cb: Float, alpha: Float) {
            val keep = 1f - a
            r += cr * alpha * keep
            g += cg * alpha * keep
            b += cb * alpha * keep
            a += alpha * keep
        }

        fun scale(k: Float) {
            r *= k
            g *= k
            b *= k
            a *= k
        }
    }

    inline fun render(widthDp: Float, heightDp: Float, scale: Float, shade: (x: Float, y: Float, out: Pixel) -> Unit): LitPixels {
        val width = ceil(widthDp * scale).toInt().coerceAtLeast(1)
        val height = ceil(heightDp * scale).toInt().coerceAtLeast(1)
        val colors = IntArray(width * height)
        val step = 1f / scale
        val pixel = Pixel()
        for (row in 0 until height) {
            val y = (row + 0.5f) * step
            for (column in 0 until width) {
                val x = (column + 0.5f) * step
                pixel.clear()
                shade(x, y, pixel)
                val a = pixel.a.coerceIn(0f, 1f)
                if (a > 0f) {
                    // Bitmap.createBitmap takes straight colours.
                    val r = (pixel.r / a).coerceIn(0f, 1f)
                    val g = (pixel.g / a).coerceIn(0f, 1f)
                    val b = (pixel.b / a).coerceIn(0f, 1f)
                    colors[row * width + column] = ((a * 255f + 0.5f).toInt() shl 24) or
                        ((r * 255f + 0.5f).toInt() shl 16) or
                        ((g * 255f + 0.5f).toInt() shl 8) or
                        (b * 255f + 0.5f).toInt()
                }
            }
        }
        return LitPixels(width, height, colors)
    }

    /** Signed distance to a rounded rectangle (negative inside) and the outward direction. */
    class Shape {
        @JvmField var distance = 0f
        @JvmField var nx = 0f
        @JvmField var ny = 0f

        fun at(x: Float, y: Float, width: Float, height: Float, radius: Float): Float {
            val hx = width / 2f
            val hy = height / 2f
            val px = x - hx
            val py = y - hy
            val qx = abs(px) - hx + radius
            val qy = abs(py) - hy + radius
            val ox = max(qx, 0f)
            val oy = max(qy, 0f)
            val outside = sqrt(ox * ox + oy * oy)
            distance = outside + min(max(qx, qy), 0f) - radius
            when {
                outside > 0.0001f -> {
                    nx = ox / outside * (if (px < 0f) -1f else 1f)
                    ny = oy / outside * (if (py < 0f) -1f else 1f)
                }
                qx > qy -> {
                    nx = if (px < 0f) -1f else 1f
                    ny = 0f
                }
                else -> {
                    nx = 0f
                    ny = if (py < 0f) -1f else 1f
                }
            }
            return distance
        }
    }

    /** An image that stretches in its centre: [cornerDp] of each side stays as baked. */
    class Stretch(val image: ImageBitmap, val marginDp: Float, val cornerDp: Float, val sideDp: Float)

    /** An image of a fixed size that adds [marginDp] round the thing it shows. */
    class Piece(val image: ImageBitmap, val marginDp: Float)

    // Glass

    private fun glassShadowMargin(lift: Float) = ceil(22f * lift / 16f) + 4f

    /**
     * What a sheet of frosted glass casts on the page. Glass lets light through, so its shadow is wide, soft and
     * lighter than a solid thing's, with a line of contact; along its near edges a little light spills onto the
     * page. Empty where the sheet lies.
     */
    fun glassShadowPixels(radius: Float, lift: Float, scale: Float, dark: Boolean): LitPixels {
        val margin = glassShadowMargin(lift)
        val inner = 2f * radius + 2f
        val side = inner + 2f * margin
        val k = lift / 16f
        val aa = 0.6f / scale
        val shape = Shape()
        return render(side, side, scale) { x, y, out ->
            val px = x - margin
            val py = y - margin
            val coverage = smooth(aa, -aa, shape.at(px, py, inner, inner, radius))
            if (coverage < 1f) {
                val far = shape.at(px - 2.5f * k, py - 7f * k, inner, inner, radius)
                val near = shape.at(px - 0.6f, py - 1.3f, inner, inner, radius)
                val spill = shape.at(px + 1.2f, py + 1.6f, inner, inner, radius)
                if (dark) {
                    out.over(0f, 0f, 0f, 0.30f * smooth(20f * k, -6f * k, far))
                    out.over(0f, 0f, 0f, 0.22f * smooth(2.0f, -0.6f, near))
                } else {
                    out.over(0.30f, 0.24f, 0.18f, 0.17f * smooth(20f * k, -6f * k, far))
                    out.over(0.26f, 0.20f, 0.15f, 0.16f * smooth(2.0f, -0.6f, near))
                    out.over(1f, 1f, 1f, 0.55f * smooth(3.0f, -0.5f, spill))
                }
                out.scale(1f - coverage)
            }
        }
    }

    fun glassShadow(radius: Float, lift: Float, scale: Float, dark: Boolean): Stretch {
        val margin = glassShadowMargin(lift)
        val bakeScale = min(scale, 2f)
        val image: ImageBitmap = cached("glassShadow|$radius|$lift|$bakeScale|$dark") {
            glassShadowPixels(radius, lift, bakeScale, dark).toImage()
        }
        return Stretch(image, margin, radius + margin, 2f * (radius + margin) + 2f)
    }

    /** How far an edge overlay reaches into a face. */
    const val EDGE_INSET = 4f

    /**
     * The polished edge of a glass sheet, as an overlay on its face: a bright line where it faces the light and
     * just inside it the dark hairline of the glass's own thickness; on the far sides a dim rim with a line of
     * light leaving the glass.
     */
    fun glassEdgePixels(radius: Float, scale: Float, dark: Boolean): LitPixels {
        val side = 2f * radius + 2f * EDGE_INSET + 2f
        val aa = 0.6f / scale
        val shape = Shape()
        val lit = if (dark) 0.14f else 1f
        val leaving = if (dark) 0.06f else 0.60f
        return render(side, side, scale) { x, y, out ->
            val d = shape.at(x, y, side, side, radius)
            val coverage = smooth(aa, -aa, d)
            if (coverage > 0f && d > -3.4f) {
                val facing = shape.nx * KEY_X + shape.ny * KEY_Y
                if (facing > 0f) {
                    out.over(1f, 1f, 1f, smooth(-1.6f, -0.2f, d) * (0.70f + 0.30f * facing) * lit)
                    out.over(0.35f, 0.30f, 0.26f, smooth(-3.0f, -2.3f, d) * smooth(-1.5f, -2.2f, d) * 0.16f * facing)
                } else {
                    if (dark) {
                        out.over(0f, 0f, 0f, smooth(-1.2f, -0.1f, d) * 0.22f * -facing)
                    } else {
                        out.over(0.42f, 0.36f, 0.30f, smooth(-1.2f, -0.1f, d) * 0.16f * -facing)
                    }
                    out.over(1f, 1f, 1f, smooth(-2.4f, -1.8f, d) * smooth(-1.1f, -1.7f, d) * leaving * -facing)
                }
                out.scale(coverage)
            }
        }
    }

    fun glassEdge(radius: Float, scale: Float, dark: Boolean): Stretch {
        val image: ImageBitmap = cached("glassEdge|$radius|$scale|$dark") { glassEdgePixels(radius, scale, dark).toImage() }
        return Stretch(image, 0f, radius + EDGE_INSET, 2f * radius + 2f * EDGE_INSET + 2f)
    }

    // Soft pads

    private fun padShadowMargin(lift: Float) = ceil(lift) + 3f

    /**
     * What a soft raised pad does to the surface round it: light spills up and left of it, shade falls down and
     * right, and it touches the surface along its far edge. Empty where the pad is.
     */
    fun padShadowPixels(radius: Float, lift: Float, scale: Float, dark: Boolean): LitPixels {
        val margin = padShadowMargin(lift)
        val inner = 2f * radius + 2f
        val side = inner + 2f * margin
        val k = lift / 14f
        val aa = 0.6f / scale
        val shape = Shape()
        return render(side, side, scale) { x, y, out ->
            val px = x - margin
            val py = y - margin
            val coverage = smooth(aa, -aa, shape.at(px, py, inner, inner, radius))
            if (coverage < 1f) {
                val light = shape.at(px + 2.5f * k, py + 3.0f * k, inner, inner, radius)
                val shade = shape.at(px - 3.5f * k, py - 4.5f * k, inner, inner, radius)
                val contact = shape.at(px - 0.6f, py - 1.0f, inner, inner, radius)
                if (dark) {
                    out.over(0f, 0f, 0f, 0.34f * smooth(12f * k, -2f, shade))
                    out.over(0f, 0f, 0f, 0.22f * smooth(1.6f, -0.5f, contact))
                } else {
                    out.over(1f, 1f, 1f, 0.92f * smooth(8f * k, -1f, light))
                    out.over(0.40f, 0.34f, 0.28f, 0.27f * smooth(12f * k, -2f, shade))
                    out.over(0.30f, 0.24f, 0.18f, 0.20f * smooth(1.6f, -0.5f, contact))
                }
                out.scale(1f - coverage)
            }
        }
    }

    fun padShadow(radius: Float, lift: Float, scale: Float, dark: Boolean): Stretch {
        val margin = padShadowMargin(lift)
        val image: ImageBitmap = cached("padShadow|$radius|$lift|$scale|$dark") {
            padShadowPixels(radius, lift, scale, dark).toImage()
        }
        return Stretch(image, margin, radius + margin, 2f * (radius + margin) + 2f)
    }

    /** A raised pad's soft quarter-round edge, as an overlay on its face. */
    fun padEdgePixels(radius: Float, scale: Float, dark: Boolean): LitPixels {
        val side = 2f * radius + 2f * EDGE_INSET + 2f
        val aa = 0.6f / scale
        val shape = Shape()
        return render(side, side, scale) { x, y, out ->
            val d = shape.at(x, y, side, side, radius)
            val coverage = smooth(aa, -aa, d)
            if (coverage > 0f && d > -3.4f) {
                val facing = shape.nx * KEY_X + shape.ny * KEY_Y
                val edge = smooth(-3.2f, 0f, d)
                when {
                    facing > 0f -> out.over(1f, 1f, 1f, edge * facing * (if (dark) 0.10f else 0.9f))
                    dark -> out.over(0f, 0f, 0f, edge * -facing * 0.22f)
                    else -> out.over(0.62f, 0.57f, 0.52f, edge * -facing * 0.20f)
                }
                out.scale(coverage)
            }
        }
    }

    fun padEdge(radius: Float, scale: Float, dark: Boolean): Stretch {
        val image: ImageBitmap = cached("padEdge|$radius|$scale|$dark") { padEdgePixels(radius, scale, dark).toImage() }
        return Stretch(image, 0f, radius + EDGE_INSET, 2f * radius + 2f * EDGE_INSET + 2f)
    }

    /** The corner a well's image stretches from. */
    fun wellCorner(radius: Float, depth: Float): Float = max(radius, depth * 4f)

    /**
     * A pad let into the surface: a floor a shade darker, shaded under its near lip, its far lip lit. [outline] adds
     * a solid boundary, for a well that takes input or counts something: it does not depend on the light.
     */
    fun wellPixels(radius: Float, depth: Float, scale: Float, floor: Color, outline: Color?, dark: Boolean): LitPixels {
        val side = 2f * wellCorner(radius, depth) + 2f / scale
        val aa = 0.6f / scale
        val shape = Shape()
        val castTone = if (dark) floatArrayOf(0f, 0f, 0f) else floatArrayOf(0.45f, 0.39f, 0.33f)
        val castStrength = if (dark) 0.45f else 0.38f
        val lipStrength = if (dark) 0.08f else 0.75f
        return render(side, side, scale) { x, y, out ->
            val d = shape.at(x, y, side, side, radius)
            val coverage = smooth(aa, -aa, d)
            if (coverage > 0f) {
                val facing = shape.nx * KEY_X + shape.ny * KEY_Y
                val inward = -d
                val cast = exp(-inward / (depth * 1.3f)) * sat(facing * 0.8f + 0.35f) * castStrength
                val lip = exp(-inward / (depth * 0.65f)) * sat(-facing) * lipStrength
                var r = mix(mix(floor.red, castTone[0], cast), 1f, lip)
                var g = mix(mix(floor.green, castTone[1], cast), 1f, lip)
                var b = mix(mix(floor.blue, castTone[2], cast), 1f, lip)
                if (outline != null) {
                    val ring = smooth(-1.25f, -0.85f, d) * 0.92f
                    r = mix(r, outline.red, ring)
                    g = mix(g, outline.green, ring)
                    b = mix(b, outline.blue, ring)
                }
                out.r = r * coverage
                out.g = g * coverage
                out.b = b * coverage
                out.a = coverage
            }
        }
    }

    fun well(radius: Float, depth: Float, scale: Float, floor: Color, outline: Color?, dark: Boolean): Stretch {
        val corner = wellCorner(radius, depth)
        val image: ImageBitmap = cached("well|$radius|$depth|$scale|${floor.value}|${outline?.value}|$dark") {
            wellPixels(radius, depth, scale, floor, outline, dark).toImage()
        }
        return Stretch(image, 0f, corner, 2f * corner + 2f / scale)
    }

    // Cut glass

    /** What a cut-glass image adds round the slab. */
    const val CUT_GLASS_MARGIN = 16f

    /** How far in from a button's ends its cut-glass image stops varying, beyond its corner. */
    const val CUT_GLASS_CAP = 4f

    /**
     * Cut glass of a colour: a slab with four polished facets. The table is deep where the light enters and bright
     * where it gathers low in the glass; each facet is flat, so it has one brightness, set by which way it faces; and
     * by day the light that went through comes out as a pool of the glass's colour on the surface beyond its far
     * edge. [mask] is inlaid in [inlay], centred. The image adds [CUT_GLASS_MARGIN] round the slab.
     */
    fun cutGlassPixels(
        width: Float,
        height: Float,
        radius: Float,
        base: Color,
        inlay: Color,
        pressed: Boolean,
        mask: LitMask?,
        scale: Float,
        dark: Boolean,
    ): LitPixels {
        val m = CUT_GLASS_MARGIN
        val aa = 0.6f / scale
        val bevel = min(1.9f, min(width, height) * 0.08f)
        val red = floatArrayOf(linear(base.red), linear(base.green), linear(base.blue))
        val white = floatArrayOf(linear(inlay.red), linear(inlay.green), linear(inlay.blue))
        val facetTint = floatArrayOf(1f, 0.84f, 0.76f)
        val arrisTint = floatArrayOf(1f, 0.92f, 0.86f)
        val pool = FloatArray(3) { srgb(mix(red[it] * 1.34f + 0.022f, 1f, 0.10f)) }
        val bed = mask?.blurred(max(1, (scale * 0.4f).roundToInt()))
        val lift = if (pressed) 0.4f else 1f
        val shape = Shape()
        val c = FloatArray(3)
        val face = Pixel()
        return render(width + 2f * m, height + 2f * m, scale) { x, y, out ->
            val px = x - m
            val py = y - m
            val near = shape.at(px - 0.7f * lift, py - 1.4f * lift, width, height, radius)
            val far = shape.at(px - 1.6f * lift, py - 4.2f * lift, width, height, radius)
            if (dark) {
                out.over(0f, 0f, 0f, 0.40f * smooth(1.8f, -0.7f, near))
                out.over(0f, 0f, 0f, 0.24f * lift * smooth(7f, -2.5f, far))
            } else {
                out.over(0.20f, 0.08f, 0.05f, 0.32f * smooth(1.8f, -0.7f, near))
                out.over(0.28f, 0.10f, 0.06f, 0.15f * lift * smooth(7f, -2.5f, far))
                val spread = shape.at(px - 3f - 2.2f * lift, py - 2f - 5.5f * lift, width - 6f, height - 4f, max(radius - 2f, 1f))
                out.under(pool[0], pool[1], pool[2], 0.15f * lift * smooth(8f, -1f, spread))
            }
            val d = shape.at(px, py, width, height, radius)
            val coverage = smooth(aa, -aa, d)
            if (coverage > 0f) {
                val facing = shape.nx * KEY_X + shape.ny * KEY_Y
                if (d > -bevel) {
                    val turn = smooth(0.15f, 0.95f, facing * 0.5f + 0.5f)
                    // The arris between facet and table glints on the lit sides.
                    val arris = smooth(-bevel - 0.45f, -bevel + 0.1f, d) * smooth(-bevel + 0.75f, -bevel + 0.15f, d)
                    for (i in 0..2) {
                        val bright = red[i] * 1.34f + 0.022f
                        var v = mix(red[i] * 0.47f * 0.55f, mix(bright, facetTint[i], 0.30f), turn)
                        v += arrisTint[i] * (arris * sat(facing) * 0.55f)
                        v *= 1f - arris * sat(-facing) * 0.35f
                        c[i] = v
                    }
                } else {
                    val down = smooth(0f, 1f, py / height)
                    val glow = exp(-(-d - bevel) / 3.5f) * sat(-facing * 0.9f + 0.1f) * 0.8f
                    for (i in 0..2) {
                        val bright = red[i] * 1.34f + 0.022f
                        c[i] = mix(red[i] * 0.90f, red[i] * 1.14f + 0.006f, down) + bright * 0.30f * glow
                    }
                }
                if (pressed) for (i in 0..2) c[i] *= 0.86f
                face.r = srgb(film(c[0] * 1.02f))
                face.g = srgb(film(c[1] * 1.02f))
                face.b = srgb(film(c[2] * 1.02f))
                face.a = 1f
                if (mask != null && bed != null) inlay(mask, bed, px, py, white, 0.10f, face)
                val keep = 1f - coverage
                out.r = face.r * coverage + out.r * keep
                out.g = face.g * coverage + out.g * keep
                out.b = face.b * coverage + out.b * keep
                out.a = coverage + out.a * keep
            }
        }
    }

    /**
     * A white inlay set flush in coloured glass, laid over [pixel]: soft-edged, its far side shaded, with the faint
     * shade it casts beside itself. [x], [y] are in the mask's dp.
     */
    private fun inlay(crisp: LitMask, bed: LitMask, x: Float, y: Float, base: FloatArray, shade: Float, pixel: Pixel) {
        val a = crisp.at(x, y)
        val cast = bed.at(x - 0.35f, y - 0.55f) * shade
        pixel.over(0.16f, 0.03f, 0.02f, cast * (1f - a))
        if (a <= 0.002f) return
        val e = 1f / crisp.scale
        val gx = (bed.at(x + e, y) - bed.at(x - e, y)) / (2f * e)
        val gy = (bed.at(x, y + e) - bed.at(x, y - e)) / (2f * e)
        val length = max(sqrt(gx * gx * 2.25f + gy * gy * 2.25f + 1f), 1e-6f)
        val nx = -gx * 1.5f / length
        val ny = -gy * 1.5f / length
        val nz = 1f / length
        val lit = 0.80f + 0.26f * (nx * KEY_X + ny * KEY_Y + nz * KEY_Z - KEY_Z)
        val grain = 0.985f + 0.03f * hash((x * crisp.scale).toInt(), (y * crisp.scale).toInt())
        pixel.over(srgb(base[0] * lit * grain), srgb(base[1] * lit * grain), srgb(base[2] * lit * grain), a)
    }

    private fun barKey(height: Float, radius: Float, base: Color, pressed: Boolean, scale: Float, dark: Boolean) =
        "cutBar|$height|$radius|${base.value}|$pressed|$scale|$dark"

    /** A filled button's face in cut glass, stretched in its centre along its length. */
    fun cutGlassBar(height: Float, radius: Float, base: Color, inlay: Color, pressed: Boolean, scale: Float, dark: Boolean): Stretch {
        val width = 2f * (radius + CUT_GLASS_CAP) + 2f
        val image: ImageBitmap = cached(barKey(height, radius, base, pressed, scale, dark)) {
            cutGlassPixels(width, height, radius, base, inlay, pressed, null, scale, dark).toImage()
        }
        return Stretch(image, CUT_GLASS_MARGIN, CUT_GLASS_MARGIN + radius + CUT_GLASS_CAP, width + 2f * CUT_GLASS_MARGIN)
    }

    private val warming = HashSet<String>()
    private val warmer by lazy {
        Executors.newSingleThreadExecutor { job ->
            Thread(job, "lit-warm").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }

    /** Bakes a button's pressed face on a background thread, so its first press does not wait for it. */
    fun warmPressedBar(height: Float, radius: Float, base: Color, inlay: Color, scale: Float, dark: Boolean) {
        val key = barKey(height, radius, base, true, scale, dark)
        if (cache.get(key) != null) return
        synchronized(warming) { if (!warming.add(key)) return }
        warmer.execute { cutGlassBar(height, radius, base, inlay, true, scale, dark) }
    }

    /** A cut-glass piece of a fixed size with a glyph inlaid in white. [mask] is asked for only when it is baked. */
    fun cutGlassPiece(
        width: Float,
        height: Float,
        radius: Float,
        base: Color,
        inlay: Color,
        glyphKey: String,
        pressed: Boolean,
        scale: Float,
        dark: Boolean,
        mask: () -> LitMask?,
    ): Piece {
        val image: ImageBitmap = cached("cutPiece|$width|$height|$radius|${base.value}|$glyphKey|$pressed|$scale|$dark") {
            cutGlassPixels(width, height, radius, base, inlay, pressed, mask(), scale, dark).toImage()
        }
        return Piece(image, CUT_GLASS_MARGIN)
    }

    private const val BEAD_MARGIN = 6f

    /**
     * A bead of red glass: dark toward the light, bright where the light comes out low on the far side, with its
     * small shadow and, by day, the red it throws. For a PIN place that has been filled.
     */
    fun beadPixels(diameter: Float, scale: Float, deepColor: Color, brightColor: Color, dark: Boolean): LitPixels {
        val side = diameter + 2f * BEAD_MARGIN
        val radius = diameter / 2f
        val c = side / 2f
        val aa = 0.6f / scale
        val deep = floatArrayOf(linear(deepColor.red), linear(deepColor.green), linear(deepColor.blue))
        val bright = floatArrayOf(linear(brightColor.red), linear(brightColor.green), linear(brightColor.blue))
        return render(side, side, scale) { x, y, out ->
            val dx = x - c
            val dy = y - c
            val dist = sqrt(dx * dx + dy * dy)
            val sx = dx - radius * 0.16f
            val sy = dy - radius * 0.30f
            val shadow = smooth(radius * 1.25f, radius * 0.70f, sqrt(sx * sx + sy * sy))
            if (dark) {
                out.over(0f, 0f, 0f, 0.44f * shadow)
            } else {
                out.over(0.25f, 0.08f, 0.05f, 0.36f * shadow)
                val gx = dx - radius * 0.30f
                val gy = dy - radius * 0.55f
                out.under(0.96f, 0.30f, 0.18f, 0.34f * smooth(radius * 1.5f, radius * 0.5f, sqrt(gx * gx + gy * gy)))
            }
            val coverage = smooth(radius + aa, radius - aa, dist)
            if (coverage > 0f) {
                val q = dist / radius
                val nz = sqrt(max(1f - q * q, 0f))
                val nx = dx / radius
                val ny = dy / radius
                val gather = sat(0.5f + 0.55f * (nx * 0.6f + ny * 0.8f)).pow(1.3f)
                val edge = 1f - 0.45f * q.pow(4f)
                val fresnel = 0.10f * (0.05f + 0.95f * (1f - nz).pow(5f))
                val spec = sat(nx * HALF_X + ny * HALF_Y + nz * HALF_Z).pow(48f) * 0.85f
                val keep = 1f - coverage
                out.r = srgb(film(mix(deep[0], bright[0], gather) * edge + spec + fresnel)) * coverage + out.r * keep
                out.g = srgb(film(mix(deep[1], bright[1], gather) * edge + 0.97f * spec + fresnel)) * coverage + out.g * keep
                out.b = srgb(film(mix(deep[2], bright[2], gather) * edge + 0.94f * spec + fresnel)) * coverage + out.b * keep
                out.a = coverage + out.a * keep
            }
        }
    }

    fun bead(diameter: Float, scale: Float, deep: Color, bright: Color, dark: Boolean): Piece {
        val image: ImageBitmap = cached("bead|$diameter|$scale|${deep.value}|${bright.value}|$dark") {
            beadPixels(diameter, scale, deep, bright, dark).toImage()
        }
        return Piece(image, BEAD_MARGIN)
    }

    // Marks

    /** The die-cut edge of a round printed mark: lit on the near side, shaded on the far side. Flat otherwise. */
    fun coin(diameter: Float, scale: Float, dark: Boolean): ImageBitmap = cached("coin|$diameter|$scale|$dark") {
        val radius = diameter / 2f
        val aa = 0.6f / scale
        val lit = if (dark) 0.14f else 0.55f
        render(diameter, diameter, scale) { x, y, out ->
            val dx = x - radius
            val dy = y - radius
            val dist = sqrt(dx * dx + dy * dy)
            val coverage = smooth(radius + aa, radius - aa, dist)
            if (coverage > 0f) {
                val l = max(dist, 0.0001f)
                val facing = dx / l * KEY_X + dy / l * KEY_Y
                val rim = smooth(radius - 1.1f, radius - 0.2f, dist)
                if (facing > 0f) out.over(1f, 1f, 1f, rim * facing * lit) else out.over(0.16f, 0.10f, 0.06f, rim * -facing * 0.26f)
                out.scale(coverage)
            }
        }.toImage()
    }

    private const val COIN_SHADOW_MARGIN = 3f

    /** A printed mark's contact shadow on the sheet. */
    fun coinShadow(diameter: Float, scale: Float, dark: Boolean): Piece {
        val side = diameter + 2f * COIN_SHADOW_MARGIN
        val image: ImageBitmap = cached("coinShadow|$diameter|$scale|$dark") {
            val radius = diameter / 2f
            val c = side / 2f
            render(side, side, scale) { x, y, out ->
                val dx = x - c - 0.35f
                val dy = y - c - 0.8f
                val shade = smooth(radius + 1.3f, radius - 0.9f, sqrt(dx * dx + dy * dy))
                if (dark) out.over(0f, 0f, 0f, 0.34f * shade) else out.over(0.20f, 0.14f, 0.09f, 0.26f * shade)
            }.toImage()
        }
        return Piece(image, COIN_SHADOW_MARGIN)
    }

    private const val DOT_MARGIN = 1f

    /** A dot of ink on paper: flat, a touch fuller at its edge where the ink pools. For status and legend marks. */
    fun dot(color: Color, diameter: Float, scale: Float): Piece {
        val side = diameter + 2f * DOT_MARGIN
        val image: ImageBitmap = cached("dot|${color.value}|$diameter|$scale") {
            val radius = diameter / 2f
            val mid = side / 2f
            val aa = 0.6f / scale
            render(side, side, scale) { x, y, out ->
                val dx = x - mid
                val dy = y - mid
                val d = sqrt(dx * dx + dy * dy)
                val coverage = smooth(radius + aa, radius - aa, d)
                if (coverage > 0f) {
                    val pooled = 1f - 0.10f * smooth(radius * 0.55f, radius, d)
                    out.r = color.red * pooled * coverage
                    out.g = color.green * pooled * coverage
                    out.b = color.blue * pooled * coverage
                    out.a = coverage
                }
            }.toImage()
        }
        return Piece(image, DOT_MARGIN)
    }

    // Paper

    /** A tile of paper repeats every this many dp. */
    const val PAPER_TILE = 192f

    /**
     * A tile of handmade paper: clouded pulp, long kōzo fibres, a few flecks of bark. [scale] is the tile's own
     * pixels per dp.
     */
    fun washi(base: Color, fibres: Int, flecks: Int, strength: Float, seed: Int, scale: Float): ImageBitmap =
        cached("washi|${base.value}|$fibres|$flecks|$strength|$seed|$scale") {
            val tile = PAPER_TILE
            val cells = tile.toInt()
            val pulp = render(tile, tile, scale) { x, y, out ->
                // Pulp gathers unevenly as the sheet is formed.
                val cloud = noise(x / 32f, y / 32f, cells / 32, seed) * 0.6f + noise(x / 8f, y / 8f, cells / 8, seed + 7) * 0.4f
                val grain = hash((x * scale).toInt() + seed, (y * scale).toInt())
                val v = 1f + ((cloud - 0.5f) * 0.030f + (grain - 0.5f) * 0.016f) * strength
                out.r = sat(base.red * v)
                out.g = sat(base.green * v)
                out.b = sat(base.blue * v)
                out.a = 1f
            }
            val bitmap = createBitmap(pulp.width, pulp.height)
            bitmap.setPixels(pulp.argb, 0, pulp.width, 0, 0, pulp.width, pulp.height)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            var state = seed + -0x61c88647
            fun next(): Float {
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                return (state and 0xFFFFFF) / 16_777_215f
            }
            val offsets = floatArrayOf(-tile, 0f, tile)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
            val path = Path()
            for (index in 0 until fibres) {
                val x0 = next() * tile
                val y0 = next() * tile
                val angle = next() * Math.PI.toFloat() * 2f
                val length = 20f + next() * next() * 120f
                val bend = (next() - 0.5f) * length * 0.14f
                val width = 0.20f + next() * 0.35f
                val light = index % 5 != 0
                val alpha = (if (light) 0.12f + next() * 0.20f else 0.025f + next() * 0.035f) * strength
                val dx = cos(angle) * length
                val dy = sin(angle) * length
                val cx = x0 + dx / 2f - sin(angle) * bend
                val cy = y0 + dy / 2f + cos(angle) * bend
                stroke.color = (if (light) Color(1f, 0.995f, 0.97f, alpha) else Color(0.42f, 0.33f, 0.22f, alpha)).toArgb()
                stroke.strokeWidth = width
                for (ox in offsets) {
                    for (oy in offsets) {
                        path.rewind()
                        path.moveTo(x0 + ox, y0 + oy)
                        path.quadTo(cx + ox, cy + oy, x0 + dx + ox, y0 + dy + oy)
                        canvas.drawPath(path, stroke)
                    }
                }
            }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(flecks) {
                val x0 = next() * tile
                val y0 = next() * tile
                val w = 0.5f + next() * 1.1f
                val h = 0.3f + next() * 0.5f
                val angle = next() * 180f
                fill.color = Color(0.36f, 0.27f, 0.17f, (0.10f + next() * 0.16f) * strength).toArgb()
                for (ox in offsets) {
                    for (oy in offsets) {
                        canvas.withTranslation(x0 + ox, y0 + oy) {
                            rotate(angle)
                            drawOval(-w / 2f, -h / 2f, w / 2f, h / 2f, fill)
                        }
                    }
                }
            }
            bitmap.asImageBitmap()
        }

    /**
     * Shippō, interlocked circles, as a print in mica: a lit line and, a hair down and right of it, a shaded one.
     * Clear between the lines; repeats every [PAPER_TILE] dp.
     */
    fun shippo(scale: Float, dark: Boolean): ImageBitmap = cached("shippo|$scale|$dark") {
        val tile = PAPER_TILE
        val cell = 32f
        val side = ceil(tile * scale).toInt()
        val bitmap = createBitmap(side, side)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val count = (tile / cell).toInt()
        for (pass in 0 until 2) {
            paint.color = when {
                pass == 0 && dark -> Color(0f, 0f, 0f, 0.16f)
                pass == 0 -> Color(0.35f, 0.30f, 0.24f, 0.045f)
                dark -> Color(1f, 1f, 1f, 0.05f)
                else -> Color(1f, 1f, 1f, 0.62f)
            }.toArgb()
            paint.strokeWidth = if (pass == 0) 0.7f else 0.6f
            val shift = if (pass == 0) 0.45f else 0f
            for (i in -1..count + 1) {
                for (j in -1..count + 1) {
                    if ((i + j) % 2 != 0) continue
                    canvas.drawCircle(i * cell + shift, j * cell + shift, cell, paint)
                }
            }
        }
        bitmap.asImageBitmap()
    }
}
