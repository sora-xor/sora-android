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

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.shadow.Shadow as BoxShadow
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors
import jp.co.soramitsu.common.presentation.compose.theme.tokens.DayThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NightThemeColors

/**
 * A coloured glow (design spec 2.1 "Glow specs"). [blur] is already converted with
 * [designBlur]. Only the alpha of a glow layer is ever animated, never the shadow itself.
 */
@Immutable
data class GlowSpec(
    val color: Color,
    val rest: Float,
    val peak: Float,
    val pressed: Float,
    val blur: Dp,
    val offsetY: Dp,
)

/**
 * Every SORA Neon token that has no ui-core `CustomColors` slot (design spec 2.5).
 * Read with `MaterialTheme.neon` inside `SoraAppTheme`.
 */
@Immutable
data class NeonTokens(
    val isDark: Boolean,
    // surfaces
    val canvas: Color,
    val layer1: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val sheet: Color,
    val toast: Color,
    val scrim: Color,
    val scrimOverBlur: Color,
    // aurora + grain
    val auroraCyan: Color,
    val auroraMagenta: Color,
    val auroraRed: Color,
    val safeAuroraMint: Color,
    val safeAuroraCyan: Color,
    val grainAlpha: Float,
    // glass
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassFillPressed: Color,
    val glassSolid: Color,
    val chromeTint: Color,
    // lines
    val borderGlass: Brush,
    val borderSafe: Brush,
    val borderCtaSheen: Brush,
    val borderChromeTop: Brush,
    val hairline: Color,
    val divider: Color,
    val borderInput: Color,
    val focusRing: Color,
    // text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val onAccent: Color,
    val link: Color,
    // accents
    val brandRed: Color,
    val redText: Color,
    val cyan: Color,
    val steel: Color,
    val magenta: Color,
    val polkaswapBrand: Color,
    val polkaswapText: Color,
    val ctaPrimary: Brush,
    val ctaSwap: Brush,
    val ctaSafe: Brush,
    val ctaDisabled: Color,
    // status
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val warningFill: Color,
    val onWarningFill: Color,
    val error: Color,
    val errorContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val redContainer: Color,
    // glow
    val glowPrimary: GlowSpec,
    val glowSwap: GlowSpec,
    val glowSafe: GlowSpec,
    // neumorphic
    val neuBg: Color,
    val neuFace: Brush,
    val neuFacePressed: Color,
    val neuRim: Brush,
    val neuDark: BoxShadow,
    val neuLight: BoxShadow,
    val neuInnerDark: BoxShadow,
    val neuInnerLight: BoxShadow,
    // skeleton
    val skeletonBase: Color,
    val skeletonHighlight: Color,
)

/**
 * Provided by `SoraAppTheme`. The default (light) only keeps previews and isolated component
 * tests from crashing; every app host applies `SoraAppTheme`.
 */
val LocalNeon = staticCompositionLocalOf { NeonClassicLight }

val MaterialTheme.neon: NeonTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalNeon.current

/**
 * Design blur B (Figma/CSS, sigma = B/2) to the Compose `Shadow.radius` (design spec 4.1).
 * Calibrate this one factor on a device; never tune individual call sites.
 */
fun designBlur(b: Float): Dp = (b * DESIGN_BLUR_FACTOR).dp

internal const val DESIGN_BLUR_FACTOR = 0.87f

private fun c(argb: Long) = Color(argb)
private fun a(rgb: Long, alpha: Float) = Color(0xFF000000 or rgb).copy(alpha = alpha)

/** 135 degrees, top-start to bottom-end. Never mirrored in RTL (it follows the light source). */
private fun diag(vararg stops: Pair<Float, Color>) =
    Brush.linearGradient(*stops, start = Offset.Zero, end = Offset.Infinite)

private fun shadow(blur: Float, color: Color, x: Int, y: Int, alpha: Float) = BoxShadow(
    radius = designBlur(blur),
    color = color,
    offset = DpOffset(x.dp, y.dp),
    alpha = alpha,
)

private val noBrush: Brush = SolidColor(Color.Transparent)
private val noGlow = GlowSpec(Color.Transparent, 0f, 0f, 0f, 0.dp, 0.dp)
private val classicInfo = c(0xFF4394E6)
private val classicInfoContainer = c(0xFFE6F3FF)

/**
 * The classic SORA look (3.8.6 and earlier) in the Neon token slots, built from a classic palette:
 * its page, surface, text and accent colours, solid buttons, and no aurora, grain, grid, glass
 * sheen, gradient border, glow or neumorphic depth.
 */
private fun classic(
    isDark: Boolean,
    page: Color, surface: Color, surfaceVariant: Color, inverted: Color,
    primary: Color, secondary: Color, tertiary: Color,
    accent: Color, accentContainer: Color,
    success: Color, successContainer: Color, warning: Color, warningContainer: Color,
    error: Color, errorContainer: Color,
) = NeonTokens(
    isDark = isDark,
    canvas = page, layer1 = page, surface = surface, surfaceVariant = surfaceVariant, sheet = surface,
    toast = inverted, scrim = Colors.Brown90.copy(alpha = .32f), scrimOverBlur = Colors.Brown90.copy(alpha = .24f),
    auroraCyan = Color.Transparent, auroraMagenta = Color.Transparent, auroraRed = Color.Transparent,
    safeAuroraMint = Color.Transparent, safeAuroraCyan = Color.Transparent,
    grainAlpha = 0f,
    glassFill = surface, glassFillStrong = surface, glassFillPressed = surfaceVariant,
    glassSolid = surface, chromeTint = surface,
    borderGlass = noBrush, borderSafe = noBrush, borderCtaSheen = noBrush,
    borderChromeTop = noBrush,
    hairline = if (isDark) a(0x120620, .28f) else a(0x6B4B40, .16f), divider = if (isDark) a(0x120620, .28f) else a(0x6B4B40, .16f),
    borderInput = tertiary, focusRing = secondary,
    textPrimary = primary, textSecondary = secondary, textTertiary = tertiary, textDisabled = tertiary,
    onAccent = Color.White, link = accent,
    brandRed = accent, redText = accent, cyan = accent, steel = secondary,
    magenta = Colors.AdditionalPolkaswap, polkaswapBrand = Colors.AdditionalPolkaswap,
    polkaswapText = Colors.AdditionalPolkaswap,
    ctaPrimary = SolidColor(accent), ctaSwap = SolidColor(Colors.AdditionalPolkaswap), ctaSafe = SolidColor(success),
    ctaDisabled = surfaceVariant,
    success = success, successContainer = successContainer, warning = warning, warningContainer = warningContainer,
    warningFill = warning, onWarningFill = Colors.Brown90, error = error, errorContainer = errorContainer,
    info = classicInfo, infoContainer = classicInfoContainer, redContainer = accentContainer,
    glowPrimary = noGlow, glowSwap = noGlow, glowSafe = noGlow,
    neuBg = page,
    neuFace = if (isDark) diag(0f to c(0xFF653A80), 1f to c(0xFF522A6A)) else diag(0f to c(0xFFFAF7F5), 1f to c(0xFFF0EBE7)),
    neuFacePressed = surfaceVariant,
    neuRim = if (isDark) diag(0f to a(0xFFFFFF, .14f), 1f to a(0xFFFFFF, 0f)) else diag(0f to a(0xFFFFFF, .9f), 1f to a(0xFFFFFF, 0f)),
    neuDark = if (isDark) shadow(14f, c(0xFF12061C), 5, 7, .70f) else shadow(14f, c(0xFF6B4A40), 5, 7, .34f),
    neuLight = if (isDark) shadow(10f, c(0xFFD9B6F0), -4, -5, .14f) else shadow(10f, Color.White, -4, -5, .95f),
    neuInnerDark = if (isDark) shadow(8f, c(0xFF12061C), 3, 4, .70f) else shadow(8f, c(0xFF6B4A40), 3, 4, .38f),
    neuInnerLight = if (isDark) shadow(6f, c(0xFFD9B6F0), -2, -3, .12f) else shadow(6f, Color.White, -2, -3, .85f),
    skeletonBase = surfaceVariant, skeletonHighlight = surface.copy(alpha = .70f),
)

/** The classic day palette: brown text on white cards over a warm light page. */
val NeonClassicLight = classic(
    isDark = false,
    page = DayThemeColors.BgPage, surface = DayThemeColors.BgSurface,
    surfaceVariant = DayThemeColors.BgSurfaceVariant, inverted = DayThemeColors.BgSurfaceInverted,
    primary = DayThemeColors.FgPrimary, secondary = DayThemeColors.FgSecondary, tertiary = DayThemeColors.FgTertiary,
    accent = DayThemeColors.AccentPrimary, accentContainer = DayThemeColors.AccentPrimaryContainer,
    success = DayThemeColors.StatusSuccess, successContainer = DayThemeColors.StatusSuccessContainer,
    warning = DayThemeColors.StatusWarning, warningContainer = DayThemeColors.StatusWarningContainer,
    error = DayThemeColors.StatusError, errorContainer = DayThemeColors.StatusErrorContainer,
)

/** The classic night palette, which the app follows the system into as 3.8.6 did. */
val NeonClassicDark = classic(
    isDark = true,
    page = NightThemeColors.BgPage, surface = NightThemeColors.BgSurface,
    surfaceVariant = NightThemeColors.BgSurfaceVariant, inverted = NightThemeColors.BgSurfaceInverted,
    primary = NightThemeColors.FgPrimary, secondary = NightThemeColors.FgSecondary,
    tertiary = NightThemeColors.FgTertiary,
    accent = NightThemeColors.AccentPrimary, accentContainer = NightThemeColors.AccentPrimaryContainer,
    success = NightThemeColors.StatusSuccess, successContainer = NightThemeColors.StatusSuccessContainer,
    warning = NightThemeColors.StatusWarning, warningContainer = NightThemeColors.StatusWarningContainer,
    error = NightThemeColors.StatusError, errorContainer = NightThemeColors.StatusErrorContainer,
)

/**
 * The tokens under the system high-contrast setting (design spec 4.0 `highContrast`): tertiary
 * text reads as secondary and 1 dp hairlines are twice as strong. [FxPolicy] covers the rest
 * (2 dp borders, no glow, opaque glass).
 */
fun NeonTokens.forHighContrast(): NeonTokens = copy(
    textTertiary = textSecondary,
    hairline = hairline.copy(alpha = (hairline.alpha * 2f).coerceAtMost(1f)),
)

val NeonClassicDarkHighContrast: NeonTokens = NeonClassicDark.forHighContrast()

val NeonClassicLightHighContrast: NeonTokens = NeonClassicLight.forHighContrast()
