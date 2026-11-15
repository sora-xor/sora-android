package jp.co.soramitsu.ui_core.lit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The materials, rendered per pixel once and cached (the iOS app's `LitImages`, number for number). Sizes are in
 * dp; [scale] is pixels per dp. Pixels are computed premultiplied, in sRGB.
 */
internal object LitBake {
    private val cache = LruCache<String, ImageBitmap>(160)

    /** The materials are also baked ahead on a background thread; asking for one being baked waits for it. */
    private val bakeLock = Any()

    private inline fun cached(key: String, make: () -> ImageBitmap): ImageBitmap =
        cache.get(key) ?: synchronized(bakeLock) { cache.get(key) ?: make().also { cache.put(key, it) } }

    private fun sat(v: Float) = v.coerceIn(0f, 1f)

    private fun smooth(a: Float, b: Float, v: Float): Float {
        val t = sat((v - a) / (b - a))
        return t * t * (3f - 2f * t)
    }

    /** An RGBA pixel, premultiplied. */
    private class Pixel {
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

        fun scale(k: Float) {
            r *= k
            g *= k
            b *= k
            a *= k
        }
    }

    private inline fun render(widthDp: Float, heightDp: Float, scale: Float, shade: (x: Float, y: Float, out: Pixel) -> Unit): ImageBitmap {
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
                val a = sat(pixel.a)
                if (a > 0f) {
                    // Bitmap.createBitmap takes straight colours.
                    val r = sat(pixel.r / a)
                    val g = sat(pixel.g / a)
                    val b = sat(pixel.b / a)
                    colors[row * width + column] = ((a * 255f + 0.5f).toInt() shl 24) or
                        ((r * 255f + 0.5f).toInt() shl 16) or
                        ((g * 255f + 0.5f).toInt() shl 8) or
                        (b * 255f + 0.5f).toInt()
                }
            }
        }
        return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Signed distance to a rounded square of side [side] (negative inside) and the outward direction. */
    private class Shape {
        @JvmField var distance = 0f
        @JvmField var nx = 0f
        @JvmField var ny = 0f

        fun at(x: Float, y: Float, width: Float, height: Float, radius: Float) {
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
        }
    }

    /** What a plate casts on the page. Empty where the plate sits. Stretches in its centre. */
    class Stretch(val image: ImageBitmap, val marginDp: Float, val cornerDp: Float, val sideDp: Float)

    fun plateShadow(radius: Float, spread: Float, halo: Float, scale: Float, palette: LitPalette): Stretch {
        val margin = ceil(spread * 3.2f)
        val side = 2f * (radius + margin) + 2f
        val key = "shadow|$radius|$spread|$halo|$scale|${palette.dark}"
        val image = cached(key) {
            val px = ceil(side * scale).toInt()
            val bitmap = createBitmap(px, px)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            val rect = RectF(margin, margin, side - margin, side - margin)
            val shape = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            // Only outside the shape: the plate's face covers the rest.
            canvas.clipOutPath(shape)
            // The caster stands off the image and only its shadow lands here, or it would show through the clip's
            // soft edge as a dark hairline round the plate.
            val away = side * 2f
            canvas.translate(-away, 0f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
            fun cast(color: Color, alpha: Float, dx: Float, dy: Float, blur: Float) {
                paint.setShadowLayer(max(blur, 0.1f), dx + away, dy, color.copy(alpha = alpha).toArgb())
                canvas.drawPath(shape, paint)
            }
            val s = palette.shadowStrength
            cast(palette.shadow, 0.13f * s, 0f, spread * 1.15f, spread * 2.3f)
            cast(palette.shadow, 0.34f * s, spread * 0.42f, spread * 0.6f, spread * 1.1f)
            cast(palette.contact, 0.20f * s, spread * 0.09f, spread * 0.15f, spread * 0.16f)
            cast(palette.halo, palette.haloAlpha * halo, -spread * 0.30f, -spread * 0.38f, spread * 0.7f)
            bitmap.asImageBitmap()
        }
        return Stretch(image, margin, radius + margin, side)
    }

    /** The plate's rounded edge: a quarter-round bullnose lit by the key, as an overlay on its face. */
    fun plateEdge(radius: Float, bevel: Float, scale: Float, palette: LitPalette): Stretch {
        val side = 2f * radius + 2f * bevel + 2f
        val key = "edge|$radius|$bevel|$scale|${palette.dark}"
        val image = cached(key) {
            val lx = -0.46f
            val ly = -0.62f
            val lz = 0.63f
            val shape = Shape()
            val lit = palette.edgeLight
            val shade = palette.edgeShade
            render(side, side, scale) { x, y, out ->
                shape.at(x, y, side, side, radius)
                val coverage = sat(0.5f - shape.distance * scale)
                if (coverage > 0f && shape.distance > -bevel) {
                    val t = (shape.distance + bevel) / bevel
                    val angle = t * t * (Math.PI.toFloat() / 2f) * 0.92f
                    val sinA = sin(angle)
                    val nx = shape.nx * sinA
                    val ny = shape.ny * sinA
                    val nz = cos(angle)
                    val relative = nx * lx + ny * ly + nz * lz - lz
                    if (relative > 0f) {
                        out.over(1f, 1f, 1f, min(relative * 1.9f, 0.95f) * lit)
                    } else {
                        out.over(palette.shadow.red, palette.shadow.green, palette.shadow.blue, min(-relative * 0.30f, 0.34f) * shade)
                    }
                    val hz = lz + 1f
                    val hl = sqrt(lx * lx + ly * ly + hz * hz)
                    val spec = sat((nx * lx + ny * ly + nz * hz) / hl).pow(42f) * 0.8f * smooth(0.15f, 0.6f, t) * lit
                    out.over(1f, 1f, 1f, spec)
                    out.scale(coverage)
                }
            }
        }
        val corner = radius + bevel
        return Stretch(image, 0f, corner, side)
    }

    /** A well pressed into a plate: darker floor, its top-left lip shadowing it, its bottom-right lip lit. */
    fun well(radius: Float, depth: Float, scale: Float, floor: Color, palette: LitPalette): Stretch {
        val margin = 2f
        val corner = max(radius, depth * 3f)
        val inner = 2f * corner + 2f / scale
        val side = inner + 2f * margin
        val key = "well|$radius|$depth|$scale|${floor.value}|${palette.dark}"
        val image = cached(key) {
            val shape = Shape()
            val keyX = LitPalette.KEY_X
            val keyY = LitPalette.KEY_Y
            val shadeR = if (palette.dark) 0f else 0.30f
            val shadeG = if (palette.dark) 0f else 0.18f
            val shadeB = if (palette.dark) 0f else 0.16f
            val aa = 0.5f / scale
            render(side, side, scale) { x, y, out ->
                shape.at(x - margin, y - margin, inner, inner, radius)
                val inside = smooth(aa, -aa, shape.distance)
                val facing = shape.nx * keyX + shape.ny * keyY
                if (shape.distance > -aa) {
                    val lipBand = smooth(1.6f, 0f, shape.distance)
                    out.over(palette.shadow.red, palette.shadow.green, palette.shadow.blue, sat(facing) * lipBand * 0.12f)
                    out.over(1f, 1f, 1f, sat(-facing) * lipBand * 0.95f * palette.edgeLight)
                    out.scale(1f - inside)
                }
                if (inside > 0f) {
                    val dist = -shape.distance
                    val cast = exp(-dist / (depth * 0.95f)) * sat(facing * 0.75f + 0.5f)
                    val occlusion = exp(-dist / (depth * 2.6f)) * 0.18f
                    val bounce = exp(-dist / (depth * 0.9f)) * sat(-facing) * 0.32f * palette.edgeLight
                    val darken = min(cast * 0.40f + occlusion, 0.85f)
                    val r0 = floor.red * (1f - darken) + shadeR * darken
                    val g0 = floor.green * (1f - darken) + shadeG * darken
                    val b0 = floor.blue * (1f - darken) + shadeB * darken
                    val keep = 1f - inside
                    out.r = (r0 + (1f - r0) * bounce) * inside + out.r * keep
                    out.g = (g0 + (1f - g0) * bounce) * inside + out.g * keep
                    out.b = (b0 + (1f - b0) * bounce) * inside + out.b * keep
                    out.a = inside + out.a * keep
                }
            }
        }
        return Stretch(image, margin, corner + margin, side)
    }

    /** A polished glass cabochon of a colour, with its shadow on the floor below. `marginDp` is what it adds round the dome. */
    fun cabochon(lit: Color, deep: Color, diameter: Float, scale: Float): Stretch {
        val margin = ceil(diameter * 0.46f)
        val side = diameter + 2f * margin
        val key = "dome|${lit.value}|${deep.value}|$diameter|$scale"
        val image = cached(key) {
            fun linear(v: Float) = v.pow(2.2f)
            val litR = linear(lit.red)
            val litG = linear(lit.green)
            val litB = linear(lit.blue)
            val deepR = linear(deep.red)
            val deepG = linear(deep.green)
            val deepB = linear(deep.blue)
            val glowR = litR.pow(0.45f)
            val glowG = litG.pow(0.45f)
            val glowB = litB.pow(0.45f)
            val radius = diameter / 2f
            val c = side / 2f
            val aa = 0.6f / scale
            render(side, side, scale) { x, y, out ->
                val dx = x - c
                val dy = y - c
                val dist = sqrt(dx * dx + dy * dy)
                val gx = dx - radius * 0.12f
                val gy = dy - radius * 0.28f
                out.over(glowR, glowG, glowB, 0.50f * smooth(radius * 1.44f, radius * 0.72f, sqrt(gx * gx + gy * gy)))
                val sx = dx - radius * 0.10f
                val sy = dy - radius * 0.16f
                out.over(0.20f, 0.11f, 0.10f, 0.46f * smooth(radius * 1.20f, radius * 0.72f, sqrt(sx * sx + sy * sy)))
                val coverage = smooth(radius + aa, radius - aa, dist)
                if (coverage > 0f) {
                    val u = min(dist / radius, 1f)
                    val turn = 0.78f * u + 0.22f * u.pow(6f)
                    val l = max(dist, 0.0001f)
                    val nx = dx / l * turn
                    val ny = dy / l * turn
                    val nz = sqrt(1f - min(turn * turn, 0.9995f))
                    val gather = sat(0.5f + 0.62f * (nx * 0.6f + ny * 0.8f) / max(turn, 0.001f) * u)
                    val bodyMix = gather.pow(1.4f)
                    val edgeDark = 1f - 0.52f * u.pow(4f)
                    val keyDiffuse = sat(0.5f + 0.5f * (-0.5f * nx - 0.68f * ny + 0.54f * nz))
                    val shade = edgeDark * (0.62f + 0.62f * keyDiffuse)
                    val cx = dx / radius - 0.20f
                    val cy = dy / radius - 0.40f
                    val caustic = exp(-(cx * cx * 3.2f + cy * cy * 5.5f)) * 0.75f
                    var bodyR = (deepR + (litR - deepR) * bodyMix) * shade + (litR * 0.9f + 0.12f) * caustic
                    var bodyG = (deepG + (litG - deepG) * bodyMix) * shade + (litG * 0.9f + 0.12f) * caustic
                    var bodyB = (deepB + (litB - deepB) * bodyMix) * shade + (litB * 0.9f + 0.12f) * caustic
                    val fresnel = 0.045f + 0.955f * (1f - nz).pow(5f)
                    val up = -2f * nz * ny
                    val room = 0.16f + 0.34f * smooth(-0.2f, 0.9f, up)
                    bodyR = bodyR * (1f - fresnel) + room * fresnel
                    bodyG = bodyG * (1f - fresnel) + room * fresnel
                    bodyB = bodyB * (1f - fresnel) + room * fresnel
                    fun film(v: Float) = sat(v / (1f + 0.22f * v) * 1.16f).pow(1f / 2.2f)
                    val domeR = film(bodyR)
                    val domeG = film(bodyG)
                    val domeB = film(bodyB)
                    // The window on the glass, a soft cap high on the lit side.
                    val qx = dx / radius + 0.20f
                    val qy = dy / radius + 0.47f
                    val ax = qx * 0.92f + qy * 0.39f
                    val ay = qx * -0.39f + qy * 0.92f
                    val cap = sqrt(ax * ax / (0.56f * 0.56f) + ay * ay / (0.29f * 0.29f))
                    val capAlpha = smooth(1f, 0.72f, cap) * (0.30f + 0.62f * smooth(0.28f, -0.26f, ay))
                    var rOut = domeR * (1f - capAlpha) + capAlpha
                    var gOut = domeG * (1f - capAlpha) + capAlpha
                    var bOut = domeB * (1f - capAlpha) + capAlpha
                    // A thin bright line on the rolled edge, on the side the light leaves through.
                    val ring = smooth(0.90f, 0.985f, u) * sat((dx * 0.6f + dy * 0.8f) / max(dist, 0.001f)) * 0.35f
                    rOut = rOut * (1f - ring) + ring
                    gOut = gOut * (1f - ring) + 0.92f * ring
                    bOut = bOut * (1f - ring) + 0.90f * ring
                    val keep = 1f - coverage
                    out.r = rOut * coverage + out.r * keep
                    out.g = gOut * coverage + out.g * keep
                    out.b = bOut * coverage + out.b * keep
                    out.a = coverage + out.a * keep
                }
            }
        }
        return Stretch(image, margin, 0f, side)
    }

    /** The light on a coin: over a flat round icon it becomes a domed, polished token with a turned rim. */
    fun coin(diameter: Float, scale: Float): ImageBitmap = cached("coin|$diameter|$scale") {
        val radius = diameter / 2f
        val aa = 0.6f / scale
        val keyX = LitPalette.KEY_X
        val keyY = LitPalette.KEY_Y
        render(diameter, diameter, scale) { x, y, out ->
            val dx = x - radius
            val dy = y - radius
            val dist = sqrt(dx * dx + dy * dy)
            val coverage = smooth(radius + aa, radius - aa, dist)
            if (coverage > 0f) {
                val u = min(dist / radius, 1f)
                val l = max(dist, 0.0001f)
                val facing = dx / l * keyX + dy / l * keyY
                val across = (dx * keyX + dy * keyY) / radius
                out.over(0.10f, 0.03f, 0.05f, sat(-across) * 0.26f * u.pow(1.5f))
                val rimWidth = max(1.3f, radius * 0.075f) / radius
                val rim = smooth(1f - rimWidth * 1.6f, 1f - rimWidth * 0.5f, u)
                if (facing > 0f) {
                    out.over(1f, 1f, 1f, rim * facing * 0.62f)
                } else {
                    out.over(0.08f, 0.02f, 0.04f, rim * -facing * 0.36f)
                }
                out.over(1f, 1f, 1f, rim * 0.10f)
                val wx = dx / radius + 0.34f
                val wy = dy / radius + 0.46f
                out.over(1f, 1f, 1f, smooth(0.62f, 0.18f, sqrt(wx * wx / 0.60f + wy * wy / 0.24f)) * 0.46f)
                val lx = dx / radius - 0.30f
                val ly = dy / radius - 0.44f
                out.over(1f, 1f, 1f, smooth(0.8f, 0.1f, sqrt(lx * lx + ly * ly)) * 0.14f)
                out.scale(coverage)
            }
        }
    }

    /** A coin's shadow on the plate, offset away from the light. */
    fun coinShadow(diameter: Float, scale: Float): Stretch {
        val margin = ceil(diameter * 0.26f)
        val side = diameter + 2f * margin
        val image = cached("coinShadow|$diameter|$scale") {
            val radius = diameter / 2f
            val c = side / 2f
            render(side, side, min(scale, 2f)) { x, y, out ->
                val dx = x - c - radius * 0.10f
                val dy = y - c - radius * 0.18f
                val d = sqrt(dx * dx + dy * dy)
                out.over(0.35f, 0.22f, 0.20f, 0.20f * smooth(radius * 1.5f, radius * 0.7f, d))
                out.over(0.25f, 0.14f, 0.12f, 0.40f * smooth(radius * 1.12f, radius * 0.82f, d))
            }
        }
        return Stretch(image, margin, 0f, side)
    }

    /** A small lit LED: a coloured dome with a hot core and a glow. */
    fun led(color: Color, diameter: Float, scale: Float): Stretch {
        val margin = ceil(diameter * 0.8f)
        val side = diameter + 2f * margin
        val image = cached("led|${color.value}|$diameter|$scale") {
            val radius = diameter / 2f
            val mid = side / 2f
            val aa = 0.6f / scale
            render(side, side, scale) { x, y, out ->
                val dx = x - mid
                val dy = y - mid
                val d = sqrt(dx * dx + dy * dy)
                out.over(color.red, color.green, color.blue, 0.42f * sat(1f - (d - radius) / (radius * 1.5f)).pow(2f))
                val coverage = smooth(radius + aa, radius - aa, d)
                if (coverage > 0f) {
                    val u = min(d / radius, 1f)
                    val shade = 1f - 0.34f * u * u
                    val hx = dx + radius * 0.2f
                    val hy = dy + radius * 0.3f
                    val hot = sat(1f - sqrt(hx * hx + hy * hy) / (radius * 0.75f))
                    val keep = 1f - coverage
                    out.r = min(color.red * shade + hot * 0.7f, 1f) * coverage + out.r * keep
                    out.g = min(color.green * shade + hot * 0.7f, 1f) * coverage + out.g * keep
                    out.b = min(color.blue * shade + hot * 0.7f, 1f) * coverage + out.b * keep
                    out.a = coverage + out.a * keep
                }
            }
        }
        return Stretch(image, margin, 0f, side)
    }

    /** Lacquer on a pill: the top's soft sheen, the light gathering at the bottom, a lit top lip. */
    fun lacquer(height: Float, radius: Float, scale: Float): Stretch {
        val width = 2f * radius + 2f
        val image = cached("lacquer|$height|$radius|$scale") {
            val shape = Shape()
            val aa = 0.6f / scale
            val keyX = LitPalette.KEY_X
            val keyY = LitPalette.KEY_Y
            render(width, height, scale) { x, y, out ->
                shape.at(x, y, width, height, radius)
                val coverage = smooth(aa, -aa, shape.distance)
                if (coverage > 0f) {
                    val v = y / height
                    val facing = shape.nx * keyX + shape.ny * keyY
                    out.over(1f, 1f, 1f, smooth(0.58f, 0.12f, v) * 0.30f)
                    out.over(1f, 0.78f, 0.72f, smooth(0.55f, 1f, v) * 0.22f)
                    out.over(0.18f, 0f, 0.03f, sat(-facing) * smooth(-4f, 0f, shape.distance) * 0.26f)
                    out.over(1f, 1f, 1f, smooth(-1.5f, -0.3f, shape.distance) * sat(facing) * 0.65f)
                    out.scale(coverage)
                }
            }
        }
        return Stretch(image, 0f, radius, width)
    }

    /** Grain below one code value, to keep the long gradients from banding. */
    val grain: ImageBitmap by lazy {
        var state = 0x2F6E2B1
        fun next(): Int {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            return state
        }
        render(96f, 96f, 1f) { _, _, out ->
            val v = (next() and 0xFF) / 255f
            if (v > 0.5f) out.over(1f, 1f, 1f, (v - 0.5f) * 0.030f) else out.over(0.25f, 0.15f, 0.12f, (0.5f - v) * 0.030f)
        }
    }
}
