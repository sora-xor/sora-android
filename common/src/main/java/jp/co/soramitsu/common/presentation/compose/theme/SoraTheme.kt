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

package jp.co.soramitsu.common.presentation.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalNeon
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonDark
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonDarkHighContrast
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonEffectsPreference
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonLight
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonLightHighContrast
import jp.co.soramitsu.common.presentation.compose.theme.neon.rememberFxPolicy
import jp.co.soramitsu.common.presentation.compose.theme.tokens.DayThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NightThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.tokens.buttonM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.displayL
import jp.co.soramitsu.common.presentation.compose.theme.tokens.displayM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.displayS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.headline1
import jp.co.soramitsu.common.presentation.compose.theme.tokens.headline2
import jp.co.soramitsu.common.presentation.compose.theme.tokens.headline3
import jp.co.soramitsu.common.presentation.compose.theme.tokens.headline4
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphL
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphLBold
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphMBold
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphSBold
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphXS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphXSBold
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textL
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textLBold
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textMBold
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textSBold
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textXS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textXSBold
import jp.co.soramitsu.ui_core.theme.AppTheme
import jp.co.soramitsu.ui_core.theme.BorderRadius
import jp.co.soramitsu.ui_core.theme.CustomTypography
import jp.co.soramitsu.ui_core.theme.Opacity
import jp.co.soramitsu.ui_core.theme.borderRadiuses
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.darkColors
import jp.co.soramitsu.ui_core.theme.defaultCustomTypography
import jp.co.soramitsu.ui_core.theme.lightColors
import jp.co.soramitsu.ui_core.theme.opacities

/**
 * The one theme for every Compose host (SORA Neon, design spec 2-7).
 *
 * - ui-core slots ([MaterialTheme.customColors], typography, radii) carry the remapped palette,
 *   so existing screens recolour without call-site changes.
 * - `MaterialTheme.neon` ([LocalNeon]) carries the tokens that have no slot (glass, glow,
 *   aurora, neumorphic shadows).
 * - [LocalFx] carries the effect tier for this device and the "Reduce effects" setting.
 * - Under the system high-contrast setting (API 34+), tertiary text uses the secondary colour
 *   and hairlines are twice as strong, in the ui-core slots and the neon tokens alike.
 */
@Composable
fun SoraAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceEffects: Boolean = NeonEffectsPreference.reduceEffects.collectAsState().value,
    content: @Composable () -> Unit,
) {
    val fx = rememberFxPolicy(reduceEffects)
    val highContrast = fx.highContrast
    AppTheme(
        darkTheme = darkTheme,
        lightColors = if (highContrast) soraLightColorsHighContrast else soraLightColors,
        darkColors = if (highContrast) soraDarkColorsHighContrast else soraDarkColors,
        typography = soraTypography,
        opacity = soraOpacity,
        borderRadius = soraBorderRadius,
        content = {
            // Standard Material inputs/actions use the same wallet palette, type and shapes as
            // the custom components (no Roboto labels or 4 dp corners on Material controls).
            val palette = MaterialTheme.customColors
            MaterialTheme(
                colors = MaterialTheme.colors.copy(
                    primary = palette.accentPrimary, primaryVariant = palette.accentPrimary,
                    secondary = palette.accentSecondary, secondaryVariant = palette.accentSecondary,
                    background = palette.bgPage, surface = palette.bgSurface,
                    onPrimary = palette.fgInverted, onSecondary = palette.fgInverted,
                    onBackground = palette.fgPrimary, onSurface = palette.fgPrimary,
                    error = palette.statusError, onError = palette.fgInverted, isLight = !darkTheme,
                ),
                typography = soraMaterialTypography,
                shapes = soraMaterialShapes,
            ) {
                CompositionLocalProvider(
                    LocalNeon provides when {
                        darkTheme && highContrast -> NeonDarkHighContrast
                        darkTheme -> NeonDark
                        highContrast -> NeonLightHighContrast
                        else -> NeonLight
                    },
                    LocalFx provides fx,
                    LocalContentColor provides palette.fgPrimary,
                    content = content,
                )
            }
        },
    )
}

private val soraLightColors = lightColors(
    accentPrimary = DayThemeColors.AccentPrimary,
    accentPrimaryContainer = DayThemeColors.AccentPrimaryContainer,
    accentSecondary = DayThemeColors.AccentSecondary,
    accentSecondaryContainer = DayThemeColors.AccentSecondaryContainer,
    accentTertiary = DayThemeColors.AccentTertiary,
    accentTertiaryContainer = DayThemeColors.AccentTertiaryContainer,
    bgPage = DayThemeColors.BgPage,
    bgSurface = DayThemeColors.BgSurface,
    bgSurfaceVariant = DayThemeColors.BgSurfaceVariant,
    bgSurfaceInverted = DayThemeColors.BgSurfaceInverted,
    fgPrimary = DayThemeColors.FgPrimary,
    fgSecondary = DayThemeColors.FgSecondary,
    fgTertiary = DayThemeColors.FgTertiary,
    fgInverted = DayThemeColors.FgInverted,
    fgOutline = DayThemeColors.FgOutline,
    statusSuccess = DayThemeColors.StatusSuccess,
    statusSuccessContainer = DayThemeColors.StatusSuccessContainer,
    statusWarning = DayThemeColors.StatusWarning,
    statusWarningContainer = DayThemeColors.StatusWarningContainer,
    statusError = DayThemeColors.StatusError,
    statusErrorContainer = DayThemeColors.StatusErrorContainer,
    elevation = DayThemeColors.Elevation,
)

private val soraDarkColors = darkColors(
    accentPrimary = NightThemeColors.AccentPrimary,
    accentPrimaryContainer = NightThemeColors.AccentPrimaryContainer,
    accentSecondary = NightThemeColors.AccentSecondary,
    accentSecondaryContainer = NightThemeColors.AccentSecondaryContainer,
    accentTertiary = NightThemeColors.AccentTertiary,
    accentTertiaryContainer = NightThemeColors.AccentTertiaryContainer,
    bgPage = NightThemeColors.BgPage,
    bgSurface = NightThemeColors.BgSurface,
    bgSurfaceVariant = NightThemeColors.BgSurfaceVariant,
    bgSurfaceInverted = NightThemeColors.BgSurfaceInverted,
    fgPrimary = NightThemeColors.FgPrimary,
    fgSecondary = NightThemeColors.FgSecondary,
    fgTertiary = NightThemeColors.FgTertiary,
    fgInverted = NightThemeColors.FgInverted,
    fgOutline = NightThemeColors.FgOutline,
    statusSuccess = NightThemeColors.StatusSuccess,
    statusSuccessContainer = NightThemeColors.StatusSuccessContainer,
    statusWarning = NightThemeColors.StatusWarning,
    statusWarningContainer = NightThemeColors.StatusWarningContainer,
    statusError = NightThemeColors.StatusError,
    statusErrorContainer = NightThemeColors.StatusErrorContainer,
    elevation = NightThemeColors.Elevation,
)

/** Design spec 4.0 `highContrast`: `text.tertiary` → `text.secondary`. */
internal val soraLightColorsHighContrast = soraLightColors.copy(fgTertiary = soraLightColors.fgSecondary)

internal val soraDarkColorsHighContrast = soraDarkColors.copy(fgTertiary = soraDarkColors.fgSecondary)

private val soraTypography: CustomTypography = defaultCustomTypography(
    displayL = displayL,
    displayM = displayM,
    displayS = displayS,
    headline1 = headline1,
    headline2 = headline2,
    headline3 = headline3,
    headline4 = headline4,
    textL = textL,
    textM = textM,
    textS = textS,
    textXS = textXS,
    textLBold = textLBold,
    textMBold = textMBold,
    textSBold = textSBold,
    textXSBold = textXSBold,
    paragraphL = paragraphL,
    paragraphM = paragraphM,
    paragraphS = paragraphS,
    paragraphXS = paragraphXS,
    paragraphLBold = paragraphLBold,
    paragraphMBold = paragraphMBold,
    paragraphSBold = paragraphSBold,
    paragraphXSBold = paragraphXSBold,
    buttonM = buttonM,
)

/**
 * Disabled controls: a visible fill (8%) and a label near `text.disabled` (36%). The ui-core
 * defaults (4% / 16%) made disabled labels almost invisible on the dark canvas.
 */
private val soraOpacity: Opacity = opacities(
    actionBgDisabled = 0.08f,
    actionFgDisabled = 0.36f,
)

private val soraMaterialTypography = Typography(
    defaultFontFamily = Inter,
    h4 = displayL,
    h5 = headline1,
    h6 = headline2,
    subtitle1 = textM,
    subtitle2 = textSBold,
    body1 = textM,
    body2 = textS,
    button = buttonM,
    caption = textXS,
    overline = NeonType.eyebrow,
)

/** Text fields 16, cards and dialogs 24, sheets 28 (design spec 6). */
private val soraMaterialShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
)

private val soraBorderRadius: BorderRadius = borderRadiuses(
    s = jp.co.soramitsu.common.presentation.compose.theme.tokens.BorderRadius.S,
    m = jp.co.soramitsu.common.presentation.compose.theme.tokens.BorderRadius.M,
    ml = jp.co.soramitsu.common.presentation.compose.theme.tokens.BorderRadius.ML,
    xl = jp.co.soramitsu.common.presentation.compose.theme.tokens.BorderRadius.L
)
