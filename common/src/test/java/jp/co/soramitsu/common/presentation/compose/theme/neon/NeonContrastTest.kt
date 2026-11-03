/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.presentation.compose.theme.neon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import java.io.File
import jp.co.soramitsu.common.presentation.compose.theme.soraDarkColorsHighContrast
import jp.co.soramitsu.common.presentation.compose.theme.soraLightColorsHighContrast
import jp.co.soramitsu.common.presentation.compose.theme.tokens.DayThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NightThemeColors
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classic SORA palettes (3.8.6 and earlier) as the app draws them: the Compose slots, the
 * classic Neon token sets and their XML mirrors. Primary text is readable (4.5:1) on every
 * surface and the accent meets 3:1 for controls. Secondary and tertiary text are the classic
 * lighter browns and purples, below 4.5:1 as they were in 3.8.6.
 */
class NeonContrastTest {

    private fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    /** Composite a translucent colour over an opaque one first. */
    private fun over(fg: Color, bg: Color): Color = Color(
        red = fg.red * fg.alpha + bg.red * (1 - fg.alpha),
        green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
        blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
    )

    private fun ratio(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun assertContrast(label: String, fg: Color, bg: Color, min: Double) {
        val r = ratio(fg, bg)
        assertTrue("$label is %.2f:1, needs %.1f:1".format(r, min), r >= min)
    }

    @Test
    fun `classic slots keep primary text readable and controls visible`() {
        val day = with(DayThemeColors) { listOf(BgPage, BgSurface, BgSurfaceVariant, FgPrimary, AccentPrimary) }
        val night = with(NightThemeColors) { listOf(BgPage, BgSurface, BgSurfaceVariant, FgPrimary, AccentPrimary) }
        for ((theme, colors) in listOf("day" to day, "night" to night)) {
            val (page, surface, variant, primary, accent) = colors
            for ((name, bg) in listOf("page" to page, "surface" to surface, "variant" to variant)) {
                assertContrast("$theme fgPrimary on $name", primary, bg, 4.5)
                assertContrast("$theme accentPrimary on $name", accent, bg, 3.0)
            }
            assertContrast("$theme label on accentPrimary", Color.White, accent, 3.0)
        }
    }

    @Test
    fun `classic tokens are the classic palettes`() {
        assertEquals(DayThemeColors.BgPage, NeonClassicLight.canvas)
        assertEquals(DayThemeColors.BgSurface, NeonClassicLight.surface)
        assertEquals(DayThemeColors.FgPrimary, NeonClassicLight.textPrimary)
        assertEquals(DayThemeColors.FgSecondary, NeonClassicLight.textSecondary)
        assertEquals(DayThemeColors.AccentPrimary, NeonClassicLight.brandRed)
        assertEquals(DayThemeColors.AccentPrimary, NeonClassicLight.cyan)
        assertEquals(NightThemeColors.BgPage, NeonClassicDark.canvas)
        assertEquals(NightThemeColors.BgSurface, NeonClassicDark.surface)
        assertEquals(NightThemeColors.FgPrimary, NeonClassicDark.textPrimary)
        assertEquals(NightThemeColors.AccentPrimary, NeonClassicDark.brandRed)
        for (n in listOf(NeonClassicLight, NeonClassicDark)) {
            // Solid surfaces: no aurora, grain, glow or neumorphic depth.
            assertEquals(Color.Transparent, n.auroraCyan)
            assertEquals(0f, n.grainAlpha)
            assertEquals(0f, n.glowPrimary.rest)
            assertEquals(0f, n.neuDark.alpha)
            assertEquals(n.surface, n.glassFill)
            assertEquals(SolidColor(n.brandRed), n.ctaPrimary)
        }
    }

    // --- XML mirrors (values[-night]/neon_colors.xml and the AppTheme attribute mapping) ---

    private val res = File("src/main/res")

    private fun xmlColors(dir: String): Map<String, Color> {
        val text = File(res, "$dir/neon_colors.xml").readText()
        return Regex("""<color name="([a-z0-9_]+)">#([0-9A-Fa-f]{6,8})</color>""").findAll(text)
            .associate { m ->
                val hex = m.groupValues[2]
                val argb = if (hex.length == 6) "FF$hex" else hex
                m.groupValues[1] to Color(argb.toLong(16))
            }
    }

    private fun themeAttr(name: String): String {
        val text = File(res, "values/styles.xml").readText()
        val m = Regex("""<item name="$name">@color/([a-z0-9_]+)</item>""").find(text)
        return requireNotNull(m) { "AppTheme has no @color mapping for $name" }.groupValues[1]
    }

    @Test
    fun `xml mirrors match the compose palette`() {
        val night = xmlColors("values-night")
        val day = xmlColors("values")
        assertEquals(night.keys, day.keys)
        assertEquals(NightThemeColors.BgPage, night["neon_canvas"])
        assertEquals(NightThemeColors.BgSurface, night["neon_surface"])
        assertEquals(NightThemeColors.FgPrimary, night["neon_text_primary"])
        assertEquals(NightThemeColors.FgSecondary, night["neon_text_secondary"])
        assertEquals(NightThemeColors.AccentPrimary, night["neon_brand_red"])
        assertEquals(DayThemeColors.BgPage, day["neon_canvas"])
        assertEquals(DayThemeColors.BgSurface, day["neon_surface"])
        assertEquals(DayThemeColors.FgPrimary, day["neon_text_primary"])
        assertEquals(DayThemeColors.FgSecondary, day["neon_text_secondary"])
        assertEquals(DayThemeColors.AccentPrimary, day["neon_brand_red"])
    }

    @Test
    fun `dialogs and the splash are readable in both themes`() {
        val text = themeAttr("contentPrimary")
        val page = themeAttr("baseBackground")
        for (dir in listOf("values", "values-night")) {
            val c = xmlColors(dir)
            fun col(name: String) = requireNotNull(c[name]) { "$dir has no $name" }
            assertContrast("$dir page text", col(text), col(page), 4.5)
            assertContrast("$dir dialog text", col("neon_text_primary"), col("neon_sheet"), 4.5)
            assertContrast("$dir dialog actions", col("neon_link"), col("neon_sheet"), 3.0)
            assertContrast("$dir Material primary button", col(themeAttr("colorOnPrimary")), col(themeAttr("colorPrimary")), 3.0)
        }
    }

    @Test
    fun `high contrast maps tertiary text to secondary and strengthens hairlines`() {
        for ((base, hc) in listOf(NeonClassicDark to NeonClassicDarkHighContrast, NeonClassicLight to NeonClassicLightHighContrast)) {
            assertEquals(base.textSecondary, hc.textTertiary)
            assertEquals((base.hairline.alpha * 2f).coerceAtMost(1f), hc.hairline.alpha, 0.001f)
            assertEquals(base.textPrimary, hc.textPrimary)
        }
        assertEquals(NightThemeColors.FgSecondary, soraDarkColorsHighContrast.fgTertiary)
        assertEquals(DayThemeColors.FgSecondary, soraLightColorsHighContrast.fgTertiary)
    }

    private fun layoutColorRef(file: String, attr: String): String {
        val text = File(res, file).readText()
        val m = Regex("""$attr="([@?][a-z]+/[A-Za-z0-9_]+)"""").find(text)
        return requireNotNull(m) { "$file has no $attr" }.groupValues[1]
    }

    @Test
    fun `transaction toast is readable in both themes`() {
        val text = layoutColorRef("layout/custom_toast.xml", "android:textColor")
        val fill = layoutColorRef("drawable/rounded_toast_bg.xml", "android:color")
        assertTrue("toast text must be a fixed colour, was $text", text.startsWith("@color/"))
        assertTrue("toast fill must be a theme attribute, was $fill", fill.startsWith("?attr/"))
        val fillColor = themeAttr(fill.substringAfter("/"))
        for (dir in listOf("values", "values-night")) {
            val c = xmlColors(dir)
            fun col(name: String) = requireNotNull(c[name]) { "$dir has no $name" }
            assertContrast("$dir toast text", col(text.substringAfter("/")), col(fillColor), 4.5)
        }
        assertEquals(NeonClassicDark.toast, xmlColors("values-night")["neon_toast"])
        assertEquals(NeonClassicLight.toast, xmlColors("values")["neon_toast"])
    }

    /** Exercise the actual style resolver without exposing a production test API. */
    private fun buttonStyle(variant: NeonVariant, tokens: NeonTokens, enabled: Boolean): Any =
        Class.forName("jp.co.soramitsu.common.presentation.compose.neon.NeonButtonKt")
            .getDeclaredMethod("styleFor", NeonVariant::class.java, NeonTokens::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(null, variant, tokens, enabled)

    private fun Any.styleField(name: String): Any? = javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(this)

    // Compose Color's JVM backing field is its packed ULong, not an ARGB Int.
    private fun Any.labelColor(): Color = Color((styleField("label") as Long).toULong())

    @Test
    fun `recovery button styles distinguish disabled safe secondary and tertiary actions`() {
        for (n in listOf(NeonClassicDark, NeonClassicLight)) {
            val safe = buttonStyle(NeonVariant.Safe, n, true)
            val disabledSafe = buttonStyle(NeonVariant.Safe, n, false)
            assertEquals(n.ctaSafe, safe.styleField("fill"))
            assertEquals(n.onAccent, safe.labelColor())
            assertEquals(SolidColor(n.ctaDisabled), disabledSafe.styleField("fill"))
            assertEquals(n.textDisabled, disabledSafe.labelColor())
            assertEquals(null, disabledSafe.styleField("edge"))
            assertEquals(null, disabledSafe.styleField("glow"))
            assertEquals(null, disabledSafe.styleField("pressedOverlay"))
            assertTrue("disabled safe fill differs from enabled", disabledSafe.styleField("fill") != safe.styleField("fill"))

            val secondary = buttonStyle(NeonVariant.Secondary, n, true)
            val disabledSecondary = buttonStyle(NeonVariant.Secondary, n, false)
            assertEquals(SolidColor(n.glassFill), secondary.styleField("fill"))
            assertEquals(SolidColor(n.glassFillPressed), secondary.styleField("pressedFill"))
            assertEquals(n.textPrimary, secondary.labelColor())
            assertEquals(n.textDisabled, disabledSecondary.labelColor())
            assertEquals(null, disabledSecondary.styleField("edge"))
            assertTrue("disabled secondary label differs from enabled", disabledSecondary.labelColor() != secondary.labelColor())

            val tertiary = buttonStyle(NeonVariant.Tertiary, n, true)
            val disabledTertiary = buttonStyle(NeonVariant.Tertiary, n, false)
            assertEquals(null, tertiary.styleField("fill"))
            assertEquals(n.link, tertiary.labelColor())
            assertEquals(n.textDisabled, disabledTertiary.labelColor())
            assertContrast("${n.isDark} tertiary action", tertiary.labelColor(), n.surface, 3.0)
            assertContrast("${n.isDark} actual secondary label", secondary.labelColor(), n.glassFill, 4.5)
        }
    }
}
