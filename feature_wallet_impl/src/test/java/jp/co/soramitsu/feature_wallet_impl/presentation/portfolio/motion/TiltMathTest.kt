package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio.motion

import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs [TiltMath] against the shared golden vectors (a copy of the portfolio dashboard's
 * reference/parallax-vectors.json, version 1). Every port must reproduce them within 1e-6.
 */
class TiltMathTest {

    private val root: JsonObject = Json.parseToJsonElement(
        requireNotNull(javaClass.classLoader?.getResource("portfolio/parallax-vectors.json")) {
            "parallax-vectors.json missing from test resources"
        }.readText()
    ).jsonObject

    private var checks = 0
    private val failures = mutableListOf<String>()

    private fun near(actual: Double, expected: Double, id: String, index: Int) {
        checks++
        if (abs(actual - expected) > 1e-6) failures += "$id[$index]: got $actual, expected $expected"
    }

    private fun nums(element: JsonElement): List<Double> =
        element.jsonArray.map { it.jsonPrimitive.double }

    @Test
    fun `constants match the shared vectors`() {
        val constants = root.getValue("constants").jsonObject
        assertEquals(constants.number("tiltRangeDeg"), TiltParallaxConstants.TILT_RANGE_DEG, 0.0)
        assertEquals(constants.number("baselineTauMs"), TiltParallaxConstants.BASELINE_TAU_MS, 0.0)
        assertEquals(constants.number("deadzone"), TiltParallaxConstants.DEADZONE, 0.0)
        assertEquals(constants.number("phoneSmoothTauMs"), TiltParallaxConstants.PHONE_SMOOTH_TAU_MS, 0.0)
        assertEquals(constants.number("settleEps"), TiltParallaxConstants.SETTLE_EPS, 0.0)
    }

    @Test
    fun `every golden vector case reproduces within 1e-6`() {
        val cases = root.getValue("cases").jsonArray.map { it.jsonObject }
        for (case in cases) {
            val id = case.getValue("id").jsonPrimitive.content
            val expect = case.getValue("expect").jsonArray
            when (val kind = case.getValue("kind").jsonPrimitive.content) {
                "baseline" -> {
                    val baseline = TiltBaseline(case.number("tauMs"))
                    case.getValue("samples").jsonArray.forEachIndexed { i, sample ->
                        val v = nums(sample)
                        val out = baseline.normalise(v[1], v[2], v[0])
                        val e = nums(expect[i])
                        near(out.x, e[0], id, i)
                        near(out.y, e[1], id, i)
                    }
                }
                "smooth" -> {
                    val smooth = Smooth2D(case.number("tauMs"))
                    val start = nums(case.getValue("start"))
                    smooth.x = start[0]
                    smooth.y = start[1]
                    smooth.tx = start[0]
                    smooth.ty = start[1]
                    case.getValue("steps").jsonArray.forEachIndexed { i, step ->
                        val v = nums(step)
                        smooth.tx = v[1]
                        smooth.ty = v[2]
                        val settled = smooth.step(v[0])
                        val e = nums(expect[i])
                        near(smooth.x, e[0], id, i)
                        near(smooth.y, e[1], id, i)
                        checks++
                        if ((if (settled) 1.0 else 0.0) != e[2]) failures += "$id[$i] settled"
                    }
                }
                "offset" -> case.getValue("inputs").jsonArray.forEachIndexed { i, input ->
                    val v = nums(input)
                    val out = layerOffset(v[0], v[1], v[2], v[3], v[4])
                    val e = nums(expect[i])
                    near(out.dx, e[0], id, i)
                    near(out.dy, e[1], id, i)
                }
                "viewer-tilt" -> case.getValue("inputs").jsonArray.forEachIndexed { i, input ->
                    val (a, b) = input.jsonArray.map { nums(it) }
                    val out = viewerTilt(Quat(a[0], a[1], a[2], a[3]), Quat(b[0], b[1], b[2], b[3]))
                    val e = nums(expect[i])
                    near(out.first, e[0], id, i)
                    near(out.second, e[1], id, i)
                }
                "gate" -> case.getValue("inputs").jsonArray.forEachIndexed { i, input ->
                    val g = input.jsonArray
                    val open = ParallaxGate(
                        fullEffects = g[0].jsonPrimitive.content == "full",
                        reduceMotion = g[1].jsonPrimitive.boolean,
                        lowPower = g[2].jsonPrimitive.boolean,
                        hidden = g[3].jsonPrimitive.boolean,
                        heroVisible = g[4].jsonPrimitive.boolean,
                    ).isOpen
                    checks++
                    if ((if (open) 1.0 else 0.0) != expect[i].jsonPrimitive.double) failures += "$id[$i]"
                }
                else -> failures += "unknown kind $kind"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        // The reference harness reports 13 cases and 923 checks for vector version 1 (5 of them
        // are the constants, checked separately above).
        assertEquals(13, cases.size)
        assertEquals(918, checks)
    }

    private fun JsonObject.number(key: String): Double = getValue(key).jsonPrimitive.double
}
