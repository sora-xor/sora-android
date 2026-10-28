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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.shadow.Shadow as BoxShadow

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
    // aurora + texture
    val auroraCyan: Color,
    val auroraMagenta: Color,
    val auroraRed: Color,
    val safeAuroraMint: Color,
    val safeAuroraCyan: Color,
    val grainAlpha: Float,
    val gridMinor: Color,
    val gridMajor: Color,
    // glass
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassFillPressed: Color,
    val glassHighlight: Brush,
    val glassSolid: Color,
    val chromeTint: Color,
    val cardShadow: BoxShadow?,
    // lines
    val borderGlass: Brush,
    val borderSpectrum: Brush,
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
    val glowFocus: GlowSpec,
    val glowDot: GlowSpec,
    val glowTab: GlowSpec,
    val glowSafe: GlowSpec,
    val glowSuccess: GlowSpec,
    val heroTextGlow: Color?,
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
val LocalNeon = staticCompositionLocalOf { NeonLight }

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

private fun horiz(vararg stops: Pair<Float, Color>) = Brush.horizontalGradient(*stops)

private fun vert(vararg stops: Pair<Float, Color>) = Brush.verticalGradient(*stops)

private fun shadow(blur: Float, color: Color, x: Int, y: Int, alpha: Float) = BoxShadow(
    radius = designBlur(blur),
    color = color,
    offset = DpOffset(x.dp, y.dp),
    alpha = alpha,
)

/** Dark: the default look for new installs (design spec 2.1). */
val NeonDark = NeonTokens(
    isDark = true,
    canvas = c(0xFF06060B),
    layer1 = c(0xFF0C0C15),
    surface = c(0xFF14141F),
    surfaceVariant = c(0xFF1C1C28),
    sheet = c(0xFF191926),
    toast = c(0xFF222233),
    scrim = a(0x000000, .55f),
    scrimOverBlur = a(0x000000, .40f),
    auroraCyan = a(0x00E5FF, .08f),
    auroraMagenta = a(0xFF2BD6, .08f),
    auroraRed = a(0xFF2244, .08f),
    safeAuroraMint = a(0x2EF2A4, .07f),
    safeAuroraCyan = a(0x00E5FF, .06f),
    grainAlpha = .04f,
    gridMinor = a(0x2EE6FF, .03f),
    gridMajor = a(0x2EE6FF, .05f),
    glassFill = a(0xFFFFFF, .05f),
    glassFillStrong = a(0xFFFFFF, .08f),
    glassFillPressed = a(0xFFFFFF, .10f),
    glassHighlight = vert(0f to a(0xFFFFFF, .07f), .5f to Color.Transparent),
    glassSolid = c(0xFF14141F),
    chromeTint = a(0x0C0C15, .94f),
    cardShadow = null,
    borderGlass = diag(
        0f to a(0xFFFFFF, .22f),
        .40f to a(0xFFFFFF, .06f),
        .75f to a(0xFFFFFF, .04f),
        1f to a(0x2EE6FF, .28f),
    ),
    borderSpectrum = diag(0f to a(0x00E5FF, .80f), .5f to a(0x8A5CFF, .45f), 1f to a(0xFF2A55, .80f)),
    borderSafe = diag(0f to a(0x2EF2A4, .60f), 1f to a(0x2EE6FF, .45f)),
    borderCtaSheen = vert(0f to a(0xFFFFFF, .40f), .5f to Color.Transparent),
    borderChromeTop = horiz(0f to Color.Transparent, .5f to a(0xFFFFFF, .12f), 1f to Color.Transparent),
    hairline = a(0xFFFFFF, .10f),
    divider = c(0xFF26263A),
    borderInput = c(0xFF6C6F80),
    focusRing = c(0xFF2EE6FF),
    textPrimary = c(0xFFF5F6FB),
    textSecondary = c(0xFFB3B7CB),
    textTertiary = c(0xFF969BB4),
    textDisabled = c(0xFF5E6277),
    onAccent = c(0xFF06060B),
    link = c(0xFF2EE6FF),
    brandRed = c(0xFFFF3355),
    redText = c(0xFFFF5A75),
    cyan = c(0xFF2EE6FF),
    steel = c(0xFF7CCFE0),
    magenta = c(0xFFFF5CE1),
    polkaswapBrand = c(0xFFFF0066),
    polkaswapText = c(0xFFFF5C9E),
    ctaPrimary = Brush.horizontalGradient(listOf(c(0xFFFF2A4A), c(0xFFFF3D7F))),
    ctaSwap = Brush.horizontalGradient(listOf(c(0xFFFF0066), c(0xFFFF3DDC))),
    ctaSafe = Brush.horizontalGradient(listOf(c(0xFF2EF2A4), c(0xFF2EE6FF))),
    ctaDisabled = a(0xFFFFFF, .08f),
    success = c(0xFF2EF2A4),
    successContainer = c(0xFF0B221D),
    warning = c(0xFFFFB547),
    warningContainer = c(0xFF241B12),
    warningFill = c(0xFFFFB547),
    onWarningFill = c(0xFF06060B),
    error = c(0xFFFF6B85),
    errorContainer = c(0xFF2A1119),
    info = c(0xFF2EE6FF),
    infoContainer = c(0xFF0B2128),
    redContainer = c(0xFF2A0C15),
    glowPrimary = GlowSpec(c(0xFFFF2A55), .45f, .70f, .85f, designBlur(24f), 6.dp),
    glowSwap = GlowSpec(c(0xFFFF0066), .45f, .65f, .80f, designBlur(20f), 4.dp),
    glowFocus = GlowSpec(c(0xFF00E5FF), .35f, .35f, .60f, designBlur(12f), 0.dp),
    glowDot = GlowSpec(c(0xFF00E5FF), .50f, .50f, .50f, designBlur(8f), 0.dp),
    glowTab = GlowSpec(c(0xFFFF3355), .60f, .60f, .60f, designBlur(8f), 0.dp),
    glowSafe = GlowSpec(c(0xFF2EF2A4), .25f, .30f, .25f, designBlur(20f), 0.dp),
    glowSuccess = GlowSpec(c(0xFF2EF2A4), .25f, .60f, .25f, designBlur(24f), 0.dp),
    heroTextGlow = a(0x00E5FF, .30f),
    neuBg = c(0xFF0F0F18),
    neuFace = diag(0f to c(0xFF191924), 1f to c(0xFF121219)),
    neuFacePressed = c(0xFF101019),
    neuRim = diag(0f to a(0xFFFFFF, .08f), 1f to Color.Transparent),
    neuDark = shadow(12f, Color.Black, 5, 5, .65f),
    neuLight = shadow(10f, Color.White, -4, -4, .07f),
    neuInnerDark = shadow(8f, Color.Black, 3, 3, .50f),
    neuInnerLight = shadow(6f, Color.White, -2, -2, .05f),
    skeletonBase = a(0xFFFFFF, .05f),
    skeletonHighlight = a(0xFFFFFF, .10f),
)

/** Light ("Daylight", design spec 2.2): neon becomes tinted soft shadows, glass is frosted white. */
val NeonLight = NeonTokens(
    isDark = false,
    canvas = c(0xFFF2F3F8),
    layer1 = c(0xFFECEEF5),
    surface = c(0xFFFFFFFF),
    surfaceVariant = c(0xFFE9EBF2),
    sheet = c(0xFFFFFFFF),
    toast = c(0xFF14141F),
    scrim = a(0x0B0C14, .32f),
    scrimOverBlur = a(0x0B0C14, .24f),
    auroraCyan = a(0x00C8E6, .10f),
    auroraMagenta = a(0xFF2BD6, .07f),
    auroraRed = a(0xFF2244, .06f),
    safeAuroraMint = a(0x12B886, .08f),
    safeAuroraCyan = a(0x00C8E6, .08f),
    grainAlpha = .03f,
    gridMinor = a(0x00708A, .04f),
    gridMajor = a(0x00708A, .07f),
    glassFill = a(0xFFFFFF, .72f),
    glassFillStrong = a(0xFFFFFF, .85f),
    // glass.fill with a #0B0C14 @ 6% overlay, pre-composited so it can be used on its own.
    glassFillPressed = a(0xEBEBEC, .74f),
    glassHighlight = vert(0f to a(0xFFFFFF, .60f), .5f to a(0xFFFFFF, 0f)),
    glassSolid = c(0xFFFFFFFF),
    chromeTint = a(0xFFFFFF, .94f),
    cardShadow = shadow(24f, c(0xFF0B0C14), 0, 8, .06f),
    borderGlass = diag(
        0f to a(0xFFFFFF, 1f),
        .40f to a(0x0B0C14, .06f),
        .75f to a(0x0B0C14, .08f),
        1f to a(0x00A3C4, .30f),
    ),
    borderSpectrum = diag(0f to a(0x00A3C4, .70f), .5f to a(0x7B4DFF, .40f), 1f to a(0xC80E34, .70f)),
    borderSafe = diag(0f to a(0x08704D, .50f), 1f to a(0x00708A, .40f)),
    borderCtaSheen = vert(0f to a(0xFFFFFF, .30f), .5f to a(0xFFFFFF, 0f)),
    borderChromeTop = horiz(0f to a(0x0B0C14, 0f), .5f to a(0x0B0C14, .08f), 1f to a(0x0B0C14, 0f)),
    hairline = a(0x0B0C14, .08f),
    divider = c(0xFFDADCE5),
    borderInput = c(0xFF7E8296),
    focusRing = c(0xFF00708A),
    textPrimary = c(0xFF0B0C14),
    textSecondary = c(0xFF454A5E),
    textTertiary = c(0xFF5E6278),
    textDisabled = c(0xFFA4A8B9),
    onAccent = c(0xFFFFFFFF),
    link = c(0xFF00708A),
    brandRed = c(0xFFC80E34),
    redText = c(0xFFC80E34),
    cyan = c(0xFF00708A),
    steel = c(0xFF1F6B7C),
    magenta = c(0xFFA3127C),
    polkaswapBrand = c(0xFFD6005A),
    polkaswapText = c(0xFFB0004B),
    ctaPrimary = Brush.horizontalGradient(listOf(c(0xFFC80E34), c(0xFFA50D4A))),
    ctaSwap = Brush.horizontalGradient(listOf(c(0xFFD6005A), c(0xFFA3128A))),
    ctaSafe = Brush.horizontalGradient(listOf(c(0xFF08704D), c(0xFF00708A))),
    ctaDisabled = a(0x0B0C14, .06f),
    success = c(0xFF08704D),
    successContainer = c(0xFFE3F6F0),
    warning = c(0xFF8A4F00),
    warningContainer = c(0xFFFFF0DA),
    warningFill = c(0xFFFFB547),
    onWarningFill = c(0xFF0B0C14),
    error = c(0xFFBC0E2D),
    errorContainer = c(0xFFF9E7EA),
    info = c(0xFF00708A),
    infoContainer = c(0xFFE0F8FC),
    redContainer = c(0xFFFBE7EB),
    glowPrimary = GlowSpec(c(0xFFC80E34), .28f, .40f, .50f, designBlur(20f), 8.dp),
    glowSwap = GlowSpec(c(0xFFD6005A), .28f, .40f, .50f, designBlur(20f), 8.dp),
    glowFocus = GlowSpec(c(0xFF00A3C4), .28f, .40f, .50f, designBlur(20f), 8.dp),
    glowDot = GlowSpec(c(0xFF00A3C4), .30f, .30f, .30f, designBlur(8f), 0.dp),
    glowTab = GlowSpec(c(0xFFC80E34), .35f, .35f, .35f, designBlur(8f), 0.dp),
    glowSafe = GlowSpec(c(0xFF12B886), .28f, .40f, .28f, designBlur(20f), 8.dp),
    glowSuccess = GlowSpec(c(0xFF12B886), .28f, .40f, .28f, designBlur(20f), 0.dp),
    heroTextGlow = null,
    neuBg = c(0xFFECEEF5),
    neuFace = diag(0f to c(0xFFF4F6FB), 1f to c(0xFFE3E6EF)),
    neuFacePressed = c(0xFFE3E6EF),
    neuRim = diag(0f to a(0xFFFFFF, 1f), 1f to a(0xFFFFFF, 0f)),
    neuDark = shadow(14f, c(0xFFA3A9C0), 6, 6, .55f),
    neuLight = shadow(14f, Color.White, -6, -6, 1f),
    neuInnerDark = shadow(8f, c(0xFFA3A9C0), 3, 3, .50f),
    neuInnerLight = shadow(8f, Color.White, -3, -3, .90f),
    skeletonBase = a(0x0B0C14, .06f),
    skeletonHighlight = a(0xFFFFFF, .70f),
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

val NeonDarkHighContrast: NeonTokens = NeonDark.forHighContrast()

val NeonLightHighContrast: NeonTokens = NeonLight.forHighContrast()
