/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.presentation.compose.theme.neon

import androidx.compose.ui.graphics.Color
import java.io.File
import jp.co.soramitsu.common.presentation.compose.theme.tokens.DayThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NightThemeColors
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.x contrast for the SORA Neon tokens (design spec 3) and the XML mirrors that the splash,
 * recovery panel and dialogs use. Text pairs need 4.5:1, UI components 3:1.
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
    fun `dark slots keep text readable on every surface`() = with(NightThemeColors) {
        for ((name, bg) in listOf("page" to BgPage, "surface" to BgSurface, "variant" to BgSurfaceVariant)) {
            assertContrast("dark fgPrimary on $name", FgPrimary, bg, 4.5)
            assertContrast("dark fgSecondary on $name", FgSecondary, bg, 4.5)
            assertContrast("dark fgTertiary on $name", FgTertiary, bg, 4.5)
            assertContrast("dark accentTertiary on $name", AccentTertiary, bg, 4.5)
            assertContrast("dark statusSuccess on $name", StatusSuccess, bg, 4.5)
            assertContrast("dark statusWarning on $name", StatusWarning, bg, 4.5)
            assertContrast("dark statusError on $name", StatusError, bg, 4.5)
            assertContrast("dark accentPrimary (icons, fills) on $name", AccentPrimary, bg, 3.0)
        }
        assertContrast("dark label on accentPrimary", FgInverted, AccentPrimary, 4.5)
        assertContrast("dark label on accentSecondary", FgInverted, AccentSecondary, 4.5)
        assertContrast("dark text on inverted surface", FgInverted, BgSurfaceInverted, 4.5)
        assertContrast("dark success on container", StatusSuccess, StatusSuccessContainer, 4.5)
        assertContrast("dark warning on container", StatusWarning, StatusWarningContainer, 4.5)
        assertContrast("dark error on container", StatusError, StatusErrorContainer, 4.5)
        assertContrast("dark fgPrimary on accent container", FgPrimary, AccentPrimaryContainer, 4.5)
        assertContrast("dark fgPrimary on tertiary container", FgPrimary, AccentTertiaryContainer, 4.5)
    }

    @Test
    fun `light slots keep text readable on every surface`() = with(DayThemeColors) {
        for ((name, bg) in listOf("page" to BgPage, "surface" to BgSurface, "variant" to BgSurfaceVariant)) {
            assertContrast("light fgPrimary on $name", FgPrimary, bg, 4.5)
            assertContrast("light fgSecondary on $name", FgSecondary, bg, 4.5)
            assertContrast("light fgTertiary on $name", FgTertiary, bg, 4.5)
            assertContrast("light accentPrimary on $name", AccentPrimary, bg, 4.5)
            assertContrast("light accentTertiary on $name", AccentTertiary, bg, 4.5)
            assertContrast("light statusSuccess on $name", StatusSuccess, bg, 4.5)
            assertContrast("light statusWarning on $name", StatusWarning, bg, 4.5)
            assertContrast("light statusError on $name", StatusError, bg, 4.5)
        }
        assertContrast("light label on accentPrimary", FgInverted, AccentPrimary, 4.5)
        assertContrast("light label on accentSecondary", FgInverted, AccentSecondary, 4.5)
        assertContrast("light success on container", StatusSuccess, StatusSuccessContainer, 4.5)
        assertContrast("light warning on container", StatusWarning, StatusWarningContainer, 4.5)
        assertContrast("light error on container", StatusError, StatusErrorContainer, 4.5)
    }

    @Test
    fun `old light outline bug stays fixed`() {
        // fgOutline used to equal bgPage (1.0:1). Dividers are decorative, but must be visible.
        assertTrue(ratio(DayThemeColors.FgOutline, DayThemeColors.BgPage) > 1.1)
        assertTrue(ratio(DayThemeColors.FgOutline, DayThemeColors.BgSurface) > 1.1)
    }

    @Test
    fun `neon tokens meet the spec on glass and controls`() {
        for (n in listOf(NeonDark, NeonLight)) {
            val theme = if (n.isDark) "dark" else "light"
            val glass = over(n.glassFill, n.canvas)
            val pressed = over(n.glassFillPressed, n.canvas)
            for ((name, bg) in listOf("canvas" to n.canvas, "surface" to n.surface, "glass" to glass, "pressed glass" to pressed, "sheet" to n.sheet)) {
                assertContrast("$theme textPrimary on $name", n.textPrimary, bg, 4.5)
                assertContrast("$theme textSecondary on $name", n.textSecondary, bg, 4.5)
                assertContrast("$theme textTertiary on $name", n.textTertiary, bg, 4.5)
                assertContrast("$theme link on $name", n.link, bg, 4.5)
                assertContrast("$theme borderInput on $name", n.borderInput, bg, 3.0)
                assertContrast("$theme focusRing on $name", n.focusRing, bg, 3.0)
            }
            assertContrast("$theme onAccent on brand red", n.onAccent, n.brandRed, 4.5)
            assertContrast("$theme onAccent on error", n.onAccent, n.error, 4.5)
            assertContrast("$theme onWarningFill on warning fill", n.onWarningFill, n.warningFill, 4.5)
            assertContrast("$theme info on its container", n.info, n.infoContainer, 4.5)
            assertContrast("$theme red text on red container", n.redText, n.redContainer, 4.5)
            assertContrast("$theme toast text", if (n.isDark) n.textPrimary else Color.White, n.toast, 4.5)
            assertContrast("$theme magenta on canvas", n.magenta, n.canvas, 4.5)
            assertContrast("$theme polkaswap text on canvas", n.polkaswapText, n.canvas, 4.5)
        }
        // Ink on neon in dark: white on any dark-theme neon fails (spec 2.6-1).
        assertTrue(ratio(Color.White, NeonDark.brandRed) < 4.5)
    }

    @Test
    fun `slot palette and neon tokens agree`() {
        assertEquals(NightThemeColors.BgPage, NeonDark.canvas)
        assertEquals(NightThemeColors.BgSurface, NeonDark.surface)
        assertEquals(NightThemeColors.FgPrimary, NeonDark.textPrimary)
        assertEquals(NightThemeColors.FgSecondary, NeonDark.textSecondary)
        assertEquals(NightThemeColors.FgTertiary, NeonDark.textTertiary)
        assertEquals(NightThemeColors.FgInverted, NeonDark.onAccent)
        assertEquals(NightThemeColors.AccentPrimary, NeonDark.brandRed)
        assertEquals(DayThemeColors.BgPage, NeonLight.canvas)
        assertEquals(DayThemeColors.BgSurface, NeonLight.surface)
        assertEquals(DayThemeColors.FgPrimary, NeonLight.textPrimary)
        assertEquals(DayThemeColors.FgSecondary, NeonLight.textSecondary)
        assertEquals(DayThemeColors.FgTertiary, NeonLight.textTertiary)
        assertEquals(DayThemeColors.FgInverted, NeonLight.onAccent)
        assertEquals(DayThemeColors.AccentPrimary, NeonLight.brandRed)
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
    fun `splash recovery panel and dialogs are readable in both themes`() {
        // Before SORA Neon the night recovery panel drew #2D2926 on #26262D: 1.04:1.
        val text = themeAttr("contentPrimary")
        val body = themeAttr("contentSecondary")
        val panel = themeAttr("secondaryBackground")
        val page = themeAttr("baseBackground")
        for (dir in listOf("values", "values-night")) {
            val c = xmlColors(dir)
            fun col(name: String) = requireNotNull(c[name]) { "$dir has no $name" }
            assertContrast("$dir recovery title", col(text), col(panel), 4.5)
            assertContrast("$dir recovery body", col(body), col(panel), 4.5)
            assertContrast("$dir recovery body on the safe panel", col(body), col("neon_surface"), 4.5)
            assertContrast("$dir splash loading text", col(body), col(page), 4.5)
            assertContrast("$dir safe button label", col("neon_on_accent"), col("neon_safe_start"), 4.5)
            assertContrast("$dir secondary button label", col("neon_text_primary"), col("neon_button_secondary"), 4.5)
            assertContrast("$dir support link", col("neon_link"), col("neon_surface"), 4.5)
            assertContrast("$dir dialog text", col("neon_text_primary"), col("neon_sheet"), 4.5)
            assertContrast("$dir dialog actions", col("neon_link"), col("neon_sheet"), 4.5)
            assertContrast("$dir Material primary button", col(themeAttr("colorOnPrimary")), col(themeAttr("colorPrimary")), 4.5)
            assertContrast("$dir banner text on error", col(themeAttr("baseOnAccent")), col(themeAttr("statusError")), 4.5)
            assertContrast("$dir banner text on success", col(themeAttr("baseOnAccent")), col(themeAttr("statusSuccess")), 4.5)
            assertContrast(
                "$dir bottom navigation inactive label",
                col(themeAttr("bottomMenuInactiveColor")),
                over(col(themeAttr("pureWhiteOnDay")), col(page)),
                4.5,
            )
        }
    }
}
