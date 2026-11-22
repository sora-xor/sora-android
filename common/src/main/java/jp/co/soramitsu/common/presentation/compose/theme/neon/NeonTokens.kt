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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import jp.co.soramitsu.common.presentation.compose.theme.tokens.DayThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NightThemeColors
import jp.co.soramitsu.ui_core.lit.LitDay
import jp.co.soramitsu.ui_core.lit.LitNight
import jp.co.soramitsu.ui_core.lit.LitPalette

/**
 * Every colour token that has no ui-core `CustomColors` slot (design spec 2.5).
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
    // glass
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassFillPressed: Color,
    val glassSolid: Color,
    val chromeTint: Color,
    // lines
    val borderSafe: Brush,
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

private fun c(argb: Long) = Color(argb)

private val noBrush: Brush = SolidColor(Color.Transparent)
private val classicInfo = c(0xFF4394E6)
private val classicInfoContainer = c(0xFFE6F3FF)

/**
 * The classic SORA layout's slots in white and SORA red. Glass, pads, wells and cut glass come from the lit kit
 * ([LitPalette]); these are the colours of what sits on them and of the surfaces that have no sheet of their own.
 */
private fun classic(
    lit: LitPalette,
    accent: Color, accentContainer: Color,
    success: Color, successContainer: Color, warning: Color, warningContainer: Color, warningFill: Color,
    error: Color, errorContainer: Color,
) = NeonTokens(
    isDark = lit.dark,
    canvas = lit.page, layer1 = lit.page, surface = lit.surface, surfaceVariant = lit.wellFloor, sheet = lit.surface,
    toast = lit.ink, scrim = LitDay.ink.copy(alpha = .32f), scrimOverBlur = LitDay.ink.copy(alpha = .24f),
    glassFill = lit.surface, glassFillStrong = lit.surface, glassFillPressed = lit.wellFloor,
    glassSolid = lit.surface, chromeTint = lit.surface,
    borderSafe = noBrush,
    hairline = lit.rule, divider = lit.rule,
    borderInput = lit.outline, focusRing = if (lit.dark) lit.redText else lit.redDeep,
    textPrimary = lit.ink, textSecondary = lit.inkSecondary, textTertiary = lit.inkTertiary, textDisabled = lit.inkTertiary,
    onAccent = lit.inlay, link = lit.redText,
    brandRed = accent, redText = lit.redText, cyan = accent, steel = lit.inkSecondary,
    magenta = accent, polkaswapBrand = accent, polkaswapText = lit.redText,
    ctaPrimary = SolidColor(lit.red), ctaSwap = SolidColor(lit.red), ctaSafe = SolidColor(if (lit.dark) c(0xFF2C6E50) else success),
    ctaDisabled = lit.wellFloor,
    success = success, successContainer = successContainer, warning = warning, warningContainer = warningContainer,
    warningFill = warningFill, onWarningFill = LitDay.ink, error = error, errorContainer = errorContainer,
    info = classicInfo, infoContainer = if (lit.dark) c(0xFF1F3246) else classicInfoContainer, redContainer = accentContainer,
    skeletonBase = lit.wellFloor, skeletonHighlight = lit.surface.copy(alpha = .70f),
)

/** By day: ink on white glass over a washi page. */
val NeonClassicLight = classic(
    lit = LitDay,
    accent = DayThemeColors.AccentPrimary, accentContainer = DayThemeColors.AccentPrimaryContainer,
    success = DayThemeColors.StatusSuccess, successContainer = DayThemeColors.StatusSuccessContainer,
    warning = DayThemeColors.StatusWarning, warningContainer = DayThemeColors.StatusWarningContainer,
    warningFill = DayThemeColors.StatusWarningFill,
    error = DayThemeColors.StatusError, errorContainer = DayThemeColors.StatusErrorContainer,
)

/** By night, which the app follows the system into: ivory on warm charcoal. */
val NeonClassicDark = classic(
    lit = LitNight,
    accent = NightThemeColors.AccentPrimary, accentContainer = NightThemeColors.AccentPrimaryContainer,
    success = NightThemeColors.StatusSuccess, successContainer = NightThemeColors.StatusSuccessContainer,
    warning = NightThemeColors.StatusWarning, warningContainer = NightThemeColors.StatusWarningContainer,
    warningFill = NightThemeColors.StatusWarningFill,
    error = NightThemeColors.StatusError, errorContainer = NightThemeColors.StatusErrorContainer,
)

/**
 * The tokens under the system high-contrast setting (design spec 4.0 `highContrast`): tertiary
 * text reads as secondary and 1 dp hairlines are twice as strong. [FxPolicy] covers the rest
 * (2 dp borders).
 */
fun NeonTokens.forHighContrast(): NeonTokens = copy(
    textTertiary = textSecondary,
    hairline = hairline.copy(alpha = (hairline.alpha * 2f).coerceAtMost(1f)),
)

val NeonClassicDarkHighContrast: NeonTokens = NeonClassicDark.forHighContrast()

val NeonClassicLightHighContrast: NeonTokens = NeonClassicLight.forHighContrast()
