/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import jp.co.soramitsu.common.presentation.compose.theme.tokens.DayThemeColors
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
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphXS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textL
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textXS
import jp.co.soramitsu.ui_core.theme.AppTheme
import jp.co.soramitsu.ui_core.theme.CustomTypography
import jp.co.soramitsu.ui_core.theme.darkColors
import jp.co.soramitsu.ui_core.theme.defaultCustomTypography
import jp.co.soramitsu.ui_core.theme.lightColors

@Deprecated("use SoraAppTheme instead")
@Composable
fun SoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

private val LightColors = androidx.compose.material.lightColors(
    primary = ThemeColors.Primary,
    onPrimary = ThemeColors.OnPrimary,
    secondary = ThemeColors.Secondary,
    onSecondary = ThemeColors.OnSecondary,
    background = ThemeColors.Background,
    onBackground = ThemeColors.Background
)

private val DarkColors = androidx.compose.material.lightColors(
    primary = ThemeColorsDark.Primary,
    onPrimary = ThemeColorsDark.OnPrimary,
    secondary = ThemeColorsDark.Secondary,
    onSecondary = ThemeColorsDark.OnSecondary,
    background = ThemeColorsDark.Background,
    onBackground = ThemeColorsDark.Background
)

@Composable
fun SoraAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    AppTheme(
        darkTheme = darkTheme,
        lightColors = soraLightColors,
        darkColors = soraDarkColors,
        typography = soraTypography,
        content = content
    )
}

private val soraLightColors = lightColors(
    accentPrimary = DayThemeColors.AccentPrimary,
    accentPrimaryContainer = DayThemeColors.AccentPrimaryContainer,
    bgPage = DayThemeColors.BgPage,
    bgSurface = DayThemeColors.BgSurface,
    bgSurfaceVariant = DayThemeColors.BgSurfaceVariant,
    bgSurfaceInverted = DayThemeColors.BgSurfaceInverted,
    fgPrimary = DayThemeColors.FgPrimary,
    fgSecondary = DayThemeColors.FgSecondary,
    frTetriary = DayThemeColors.FgTetriary,
    fgInverted = DayThemeColors.FgInverted,
    fgOutline = DayThemeColors.FgOutline,
    statusSuccess = DayThemeColors.StatusSuccess,
    statusSuccessContainer = DayThemeColors.StatusSuccessContainer,
    statusWarning = DayThemeColors.StatusWarning,
    statusWarningContainer = DayThemeColors.StatusWarningContainer,
    statusError = DayThemeColors.StatusError,
    statusErrorContainer = DayThemeColors.StatusErrorContainer
)

private val soraDarkColors = darkColors(
    accentPrimary = NightThemeColors.AccentPrimary,
    accentPrimaryContainer = NightThemeColors.AccentPrimaryContainer,
    bgPage = NightThemeColors.BgPage,
    bgSurface = NightThemeColors.BgSurface,
    bgSurfaceVariant = NightThemeColors.BgSurfaceVariant,
    bgSurfaceInverted = NightThemeColors.BgSurfaceInverted,
    fgPrimary = NightThemeColors.FgPrimary,
    fgSecondary = NightThemeColors.FgSecondary,
    frTetriary = NightThemeColors.FgTetriary,
    fgInverted = NightThemeColors.FgInverted,
    fgOutline = NightThemeColors.FgOutline,
    statusSuccess = NightThemeColors.StatusSuccess,
    statusSuccessContainer = NightThemeColors.StatusSuccessContainer,
    statusWarning = NightThemeColors.StatusWarning,
    statusWarningContainer = NightThemeColors.StatusWarningContainer,
    statusError = NightThemeColors.StatusError,
    statusErrorContainer = NightThemeColors.StatusErrorContainer
)

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
    paragraphL = paragraphL,
    paragraphM = paragraphM,
    paragraphS = paragraphS,
    paragraphXS = paragraphXS,
    buttonM = buttonM
)
